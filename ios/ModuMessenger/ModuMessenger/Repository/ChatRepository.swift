import Foundation
import os

/// 화면에 올릴 채팅 한 건. 서버 왕복 전의 낙관적 에코는 음수 임시 id 를 가진다.
struct ChatMessage: Identifiable, Equatable {
    var dto: ChatDto
    var isPending: Bool = false
    var isFailed: Bool = false
    var id: Int64 { dto.id ?? 0 }
}

/// 방 목록·안읽음·채팅·읽음 커서를 한곳에서 들고 소켓 이벤트를 반영한다. 안드로이드 ChatRepository 와 같은 역할.
@MainActor
final class ChatRepository: ObservableObject, ChatSocketListener {
    static let pageSize = 50

    @Published private(set) var rooms: [ChatRoomDto] = []
    @Published private(set) var connectionState: ConnectionState = .disconnected
    @Published private(set) var authFailed = false
    @Published private var unread: [String: Int64] = [:]
    @Published private var chatsByRoom: [String: [ChatMessage]] = [:]
    @Published private var cursors: [String: [String: Int64]] = [:]
    @Published private(set) var activeRoomId: String?

    private let api: ChatAPI
    private let socket: ChatSocket
    private let now: () -> String
    private let log = Logger(subsystem: "com.example.modumessenger", category: "chat")

    /// 소켓이 연결 시점마다 읽는 신원. 메인 액터 밖에서도 읽을 수 있게 락으로 보호한다.
    nonisolated let identity: IdentityHolder
    var myUserId: String { identity.userId }
    var myMemberId: String? { identity.memberId }
    private var nextTempId: Int64 = -1
    private var pendingReadRooms = Set<String>()

    init(api: ChatAPI, socket: ChatSocket, identity: IdentityHolder = IdentityHolder(),
         now: @escaping () -> String = { ChatTime.now() }) {
        self.api = api
        self.socket = socket
        self.identity = identity
        self.now = now
        socket.listener = self
    }

    // MARK: identity / lifecycle

    func setIdentity(userId: String, memberId: String?) {
        identity.set(userId: userId, memberId: memberId)
    }

    func connect() { socket.connect() }

    /// 로그아웃. 신원을 먼저 비워 늦게 오는 프레임이 상태를 되살리지 못하게 한다.
    func clear() {
        identity.set(userId: "", memberId: nil)
        socket.disconnect()
        rooms = []
        unread = [:]
        chatsByRoom = [:]
        cursors = [:]
        activeRoomId = nil
        pendingReadRooms = []
        authFailed = false
    }

    // MARK: read accessors

    func chats(roomId: String) -> [ChatMessage] { chatsByRoom[roomId] ?? [] }
    func unreadCount(roomId: String) -> Int64 { unread[roomId] ?? 0 }
    func readCursor(roomId: String, userId: String) -> Int64 { cursors[roomId]?[userId] ?? 0 }
    func room(roomId: String) -> ChatRoomDto? { rooms.first { $0.roomId == roomId } }

    /// chatId 를 아직 읽지 않은 멤버 수 (나 제외). 말풍선 옆 숫자.
    func unreadMemberCount(roomId: String, chatId: Int64, memberIds: [String]) -> Int {
        memberIds.filter { $0 != myUserId && readCursor(roomId: roomId, userId: $0) < chatId }.count
    }

    // MARK: rooms

    func refreshRooms() async {
        guard let memberId = myMemberId else { return }
        do {
            let fetched = try await api.chatRooms(memberId: memberId)
            rooms = Self.sorted(fetched)
            await refreshUnread()
        } catch {
            log.error("rooms: \(error.localizedDescription)")
        }
    }

    private func refreshUnread() async {
        guard !myUserId.isEmpty else { return }
        do {
            let counts = try await api.unreadCounts(userId: myUserId)
            var next: [String: Int64] = [:]
            for c in counts { if let id = c.roomId { next[id] = c.unreadChatCount ?? 0 } }
            if let active = activeRoomId { next[active] = 0 }
            unread = next
        } catch {
            log.error("unread: \(error.localizedDescription)")
        }
    }

    func createRoom(memberIds: [Int64]) async throws -> ChatRoomDto {
        let room = try await api.createChatRoom(memberIds: memberIds)
        await refreshRooms()
        return room
    }

    func exitRoom(roomId: String) async throws {
        try await api.exitChatRoom(roomId: roomId, userId: myUserId)
        rooms.removeAll { $0.roomId == roomId }
        chatsByRoom[roomId] = nil
        unread[roomId] = nil
        if activeRoomId == roomId { activeRoomId = nil }
    }

    // MARK: chats

    /// 방에 들어간다: 첫 페이지와 읽음 커서를 받고, 안읽음이 있으면 읽음 처리한다.
    func enterRoom(_ roomId: String) async {
        activeRoomId = roomId
        await loadInitial(roomId)
        await loadCursors(roomId)
        if unreadCount(roomId: roomId) > 0 { markRead(roomId) }
        unread[roomId] = 0
    }

    func leaveRoom() { activeRoomId = nil }

    private func loadInitial(_ roomId: String) async {
        do {
            let page = try await api.chatPage(roomId: roomId, size: Self.pageSize)
            merge(roomId: roomId, incoming: page)
        } catch {
            log.error("page: \(error.localizedDescription)")
        }
    }

    func loadPrevious(_ roomId: String) async {
        guard let oldest = chats(roomId: roomId).first(where: { $0.id > 0 })?.id else { return }
        do {
            let page = try await api.prevChats(roomId: roomId, beforeChatId: oldest, size: Self.pageSize)
            merge(roomId: roomId, incoming: page)
        } catch {
            log.error("prev: \(error.localizedDescription)")
        }
    }

    private func loadCursors(_ roomId: String) async {
        do {
            let list = try await api.readCursors(roomId: roomId)
            var map = cursors[roomId] ?? [:]
            for c in list { if let u = c.userId { map[u] = c.lastReadChatId ?? 0 } }
            cursors[roomId] = map
        } catch {
            log.error("cursors: \(error.localizedDescription)")
        }
    }

    /// id 기준으로 합치고 오름차순 정렬. 임시(음수) 항목은 그대로 둔다.
    private func merge(roomId: String, incoming: [ChatDto]) {
        var current = chatsByRoom[roomId] ?? []
        var known = Set(current.compactMap { $0.id > 0 ? $0.id : nil })
        for dto in incoming {
            guard let id = dto.id, !known.contains(id) else { continue }
            known.insert(id)
            current.append(ChatMessage(dto: dto))
        }
        chatsByRoom[roomId] = Self.sortedChats(current)
    }

    // MARK: send

    @discardableResult
    func sendText(roomId: String, text: String) -> Bool {
        send(roomId: roomId, message: text, kind: .text)
    }

    /// 사진을 storage-service 에 올린 뒤 파일 이름을 이미지 채팅으로 보낸다.
    func sendImage(roomId: String, data: Data, fileName: String) async throws {
        let stored = try await api.upload(fileName: fileName, data: data, mimeType: "image/jpeg")
        send(roomId: roomId, message: stored, kind: .image)
    }

    @discardableResult
    private func send(roomId: String, message: String, kind: ChatType) -> Bool {
        var dto = ChatDto(id: nil, chatType: kind.rawValue, roomId: roomId, sender: myUserId, message: message, chatTime: now())
        let payload = (try? JSONEncoder().encode(dto)).flatMap { String(data: $0, encoding: .utf8) } ?? ""
        let sent = socket.send(payload)

        dto.id = nextTempId
        nextTempId -= 1
        var list = chatsByRoom[roomId] ?? []
        list.append(ChatMessage(dto: dto, isPending: sent, isFailed: !sent))
        chatsByRoom[roomId] = list
        return sent
    }

    /// 실패한 말풍선을 다시 보낸다.
    func retry(roomId: String, tempId: Int64) {
        guard var list = chatsByRoom[roomId], let index = list.firstIndex(where: { $0.id == tempId }) else { return }
        let failed = list.remove(at: index)
        chatsByRoom[roomId] = list
        send(roomId: roomId, message: failed.dto.message ?? "", kind: failed.dto.kind)
    }

    // MARK: read receipts

    private func markRead(_ roomId: String) {
        unread[roomId] = 0
        guard !myUserId.isEmpty else { return }
        if let payload = try? ChatSocketFrame.readReceiptPayload(roomId: roomId, sender: myUserId),
           let text = String(data: payload, encoding: .utf8), socket.send(text) {
            pendingReadRooms.remove(roomId)
        } else {
            pendingReadRooms.insert(roomId)
        }
        let userId = myUserId
        Task { [api] in
            do { try await api.updateLastRead(roomId: roomId, userId: userId) }
            catch { self.log.error("updateLastRead: \(error.localizedDescription)") }
        }
    }

    // MARK: ChatSocketListener

    func socketDidReceive(_ frame: ChatSocketFrame) {
        guard !myUserId.isEmpty else { return }
        switch frame {
        case .chat(let dto): handleChat(dto)
        case .read(let roomId, let userId, let cursor):
            var map = cursors[roomId] ?? [:]
            map[userId] = max(map[userId] ?? 0, cursor)
            cursors[roomId] = map
        case .roomCreated:
            Task { await refreshRooms() }
        }
    }

    func socketDidChange(state: ConnectionState) { connectionState = state }

    func socketDidReconnect() {
        Task {
            for roomId in pendingReadRooms { markRead(roomId) }
            await refreshRooms()
            if let active = activeRoomId { await loadInitial(active) }
        }
    }

    func socketDidFailAuth() { authFailed = true }

    private func handleChat(_ dto: ChatDto) {
        guard let roomId = dto.roomId, let id = dto.id else { return }
        var list = chatsByRoom[roomId] ?? []

        if dto.sender == myUserId,
           let index = list.firstIndex(where: { $0.isPending && $0.dto.message == dto.message && $0.dto.chatType == dto.chatType }) {
            list[index] = ChatMessage(dto: dto)
        } else if !list.contains(where: { $0.id == id }) {
            list.append(ChatMessage(dto: dto))
        }
        chatsByRoom[roomId] = Self.sortedChats(list)

        updateRoomPreview(roomId: roomId, dto: dto)

        if activeRoomId == roomId {
            markRead(roomId)
        } else if dto.sender != myUserId {
            unread[roomId] = (unread[roomId] ?? 0) + 1
        }
    }

    private func updateRoomPreview(roomId: String, dto: ChatDto) {
        guard let index = rooms.firstIndex(where: { $0.roomId == roomId }) else {
            Task { await refreshRooms() }   // 모르는 방이면 목록을 다시 받는다
            return
        }
        var room = rooms[index]
        room.lastChatMsg = dto.kind == .image ? "사진" : dto.message
        room.lastChatId = dto.id.map(String.init)
        room.lastChatTime = dto.chatTime
        rooms[index] = room
        rooms = Self.sorted(rooms)
    }

    // MARK: sorting

    private static func sorted(_ rooms: [ChatRoomDto]) -> [ChatRoomDto] {
        rooms.sorted { ($0.lastChatTime ?? "") > ($1.lastChatTime ?? "") }
    }

    /// 서버 id 오름차순, 임시(음수) 항목은 맨 뒤에 보낸 순서대로.
    private static func sortedChats(_ list: [ChatMessage]) -> [ChatMessage] {
        let real = list.filter { $0.id > 0 }.sorted { $0.id < $1.id }
        let temp = list.filter { $0.id <= 0 }.sorted { $0.id > $1.id }
        return real + temp
    }
}

/// 로그인한 사용자의 userId/memberId. 소켓 스레드에서도 읽으므로 락으로 보호한다.
final class IdentityHolder: @unchecked Sendable {
    private let lock = NSLock()
    private var _userId = ""
    private var _memberId: String?

    var userId: String { lock.lock(); defer { lock.unlock() }; return _userId }
    var memberId: String? { lock.lock(); defer { lock.unlock() }; return _memberId }

    func set(userId: String, memberId: String?) {
        lock.lock(); defer { lock.unlock() }
        _userId = userId
        _memberId = memberId
    }
}
