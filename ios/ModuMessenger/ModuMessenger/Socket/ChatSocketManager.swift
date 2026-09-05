import Foundation
import os

enum ConnectionState: Equatable {
    case disconnected, connecting, connected, reconnecting
}

/// 저장소가 소켓에서 받는 이벤트. 안드로이드 ChatSocketListener 와 같다.
@MainActor
protocol ChatSocketListener: AnyObject {
    func socketDidReceive(_ frame: ChatSocketFrame)
    func socketDidChange(state: ConnectionState)
    /// 최초 연결이 아니라 끊겼다 다시 붙은 경우. 갭 복구 트리거.
    func socketDidReconnect()
    func socketDidFailAuth()
}

protocol ChatSocket: AnyObject {
    var listener: ChatSocketListener? { get set }
    var state: ConnectionState { get }
    func connect()
    func disconnect()
    func send(_ text: String) -> Bool
}

/// URLSessionWebSocketTask 기반 연결 관리. 20초 ping, 지수 백오프 재연결, 옛 소켓 콜백은 세대 번호로 걸러낸다.
final class ChatSocketManager: NSObject, ChatSocket {
    struct Credentials {
        var userId: () -> String?
        var accessToken: () -> String?
    }

    private static let pingInterval: TimeInterval = 20
    private let log = Logger(subsystem: "com.example.modumessenger", category: "socket")

    private let url: URL
    private let credentials: Credentials
    private var policy = ReconnectPolicy()
    private lazy var session = URLSession(configuration: .default, delegate: self, delegateQueue: nil)

    private let lock = NSLock()
    private var task: URLSessionWebSocketTask?
    private var generation = 0
    private var intentionallyClosed = true
    private var everConnected = false
    private var reconnectWork: DispatchWorkItem?
    private var pingTimer: DispatchSourceTimer?

    private(set) var state: ConnectionState = .disconnected
    weak var listener: ChatSocketListener?

    init(url: URL, credentials: Credentials) {
        self.url = url
        self.credentials = credentials
    }

    // MARK: ChatSocket

    func connect() {
        lock.lock()
        if state == .connecting || state == .connected { lock.unlock(); return }
        intentionallyClosed = false
        cancelReconnectLocked()
        lock.unlock()
        update(.connecting)
        open()
    }

    func disconnect() {
        lock.lock()
        intentionallyClosed = true
        generation += 1
        cancelReconnectLocked()
        stopPingLocked()
        policy.reset()
        let old = task
        task = nil
        lock.unlock()
        old?.cancel(with: .normalClosure, reason: nil)
        update(.disconnected)
    }

    func send(_ text: String) -> Bool {
        lock.lock()
        guard let task, state == .connected else { lock.unlock(); return false }
        lock.unlock()
        task.send(.string(text)) { [weak self] error in
            if let error { self?.log.error("send failed: \(error.localizedDescription)") }
        }
        return true
    }

    // MARK: internals

    private func open() {
        lock.lock()
        guard !intentionallyClosed else { lock.unlock(); return }
        generation += 1
        let myGeneration = generation
        var request = URLRequest(url: url)
        request.setValue(credentials.userId() ?? "", forHTTPHeaderField: "userId")
        request.setValue(credentials.accessToken() ?? "", forHTTPHeaderField: "Authorization")
        let newTask = session.webSocketTask(with: request)
        task = newTask
        lock.unlock()
        newTask.resume()
        receiveLoop(newTask, generation: myGeneration)
    }

    private func receiveLoop(_ task: URLSessionWebSocketTask, generation: Int) {
        task.receive { [weak self] result in
            guard let self, self.isCurrent(generation) else { return }
            switch result {
            case .success(let message):
                if case .string(let text) = message, let frame = ChatSocketFrame.parse(text) {
                    Task { @MainActor in self.listener?.socketDidReceive(frame) }
                }
                self.receiveLoop(task, generation: generation)
            case .failure(let error):
                self.log.warning("receive failed: \(error.localizedDescription)")
                self.handleDrop(generation: generation, httpStatus: nil)
            }
        }
    }

    private func isCurrent(_ generation: Int) -> Bool {
        lock.lock(); defer { lock.unlock() }
        return generation == self.generation
    }

    private func handleDrop(generation: Int, httpStatus: Int?) {
        lock.lock()
        guard generation == self.generation, !intentionallyClosed else { lock.unlock(); return }
        stopPingLocked()
        task = nil
        lock.unlock()

        if httpStatus == 401 {
            Task { @MainActor in self.listener?.socketDidFailAuth() }
        }
        scheduleReconnect()
    }

    private func scheduleReconnect() {
        lock.lock()
        guard !intentionallyClosed else { lock.unlock(); return }
        let delay = policy.nextDelay()
        let work = DispatchWorkItem { [weak self] in self?.open() }
        reconnectWork = work
        lock.unlock()
        update(.reconnecting)
        log.info("reconnect in \(delay, format: .fixed(precision: 1))s")
        DispatchQueue.global().asyncAfter(deadline: .now() + delay, execute: work)
    }

    private func cancelReconnectLocked() {
        reconnectWork?.cancel()
        reconnectWork = nil
    }

    private func startPingLocked(for task: URLSessionWebSocketTask, generation: Int) {
        stopPingLocked()
        let timer = DispatchSource.makeTimerSource(queue: .global())
        timer.schedule(deadline: .now() + Self.pingInterval, repeating: Self.pingInterval)
        timer.setEventHandler { [weak self] in
            task.sendPing { error in
                guard let self, let error else { return }
                self.log.warning("ping failed: \(error.localizedDescription)")
                self.handleDrop(generation: generation, httpStatus: nil)
            }
        }
        timer.resume()
        pingTimer = timer
    }

    private func stopPingLocked() {
        pingTimer?.cancel()
        pingTimer = nil
    }

    private func update(_ next: ConnectionState) {
        lock.lock()
        state = next
        lock.unlock()
        Task { @MainActor in self.listener?.socketDidChange(state: next) }
    }
}

extension ChatSocketManager: URLSessionWebSocketDelegate {
    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask, didOpenWithProtocol protocol: String?) {
        lock.lock()
        guard webSocketTask === task else { lock.unlock(); return }
        let myGeneration = generation
        cancelReconnectLocked()
        let isReconnect = everConnected
        everConnected = true
        policy.reset()
        startPingLocked(for: webSocketTask, generation: myGeneration)
        lock.unlock()
        update(.connected)
        if isReconnect { Task { @MainActor in self.listener?.socketDidReconnect() } }
    }

    func urlSession(_ session: URLSession, webSocketTask: URLSessionWebSocketTask,
                    didCloseWith closeCode: URLSessionWebSocketTask.CloseCode, reason: Data?) {
        lock.lock()
        let isMine = webSocketTask === task
        let myGeneration = generation
        lock.unlock()
        guard isMine else { return }
        handleDrop(generation: myGeneration, httpStatus: nil)
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        lock.lock()
        let isMine = task === self.task
        let myGeneration = generation
        lock.unlock()
        guard isMine else { return }
        let status = (task.response as? HTTPURLResponse)?.statusCode
        handleDrop(generation: myGeneration, httpStatus: status)
    }
}
