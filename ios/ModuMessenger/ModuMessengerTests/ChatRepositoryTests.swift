import XCTest
@testable import ModuMessenger

@MainActor
final class ChatRepositoryTests: XCTestCase {
    private var api: MockChatAPI!
    private var socket: MockSocket!
    private var repo: ChatRepository!

    override func setUp() async throws {
        api = MockChatAPI()
        socket = MockSocket()
        repo = ChatRepository(api: api, socket: socket, now: { "2026-09-05 12:00:00" })
        repo.setIdentity(userId: "me", memberId: "1")
    }

    func testRefreshRoomsLoadsRoomsAndUnread() async {
        api.rooms = [room("r1", lastChatTime: "2026-09-05 10:00:00"), room("r2", lastChatTime: "2026-09-05 11:00:00")]
        api.unread = [ChatRoomUnreadDto(roomId: "r1", lastSendChatId: 5, lastReadChatId: 2, unreadChatCount: 3)]

        await repo.refreshRooms()

        XCTAssertEqual(repo.rooms.map(\.roomId), ["r2", "r1"], "최근 대화가 위로")
        XCTAssertEqual(repo.unreadCount(roomId: "r1"), 3)
        XCTAssertEqual(repo.unreadCount(roomId: "r2"), 0)
    }

    func testSendTextShowsOptimisticEchoThenBroadcastReplacesIt() async throws {
        socket.connected = true
        repo.sendText(roomId: "r1", text: "hello")

        XCTAssertEqual(repo.chats(roomId: "r1").count, 1)
        XCTAssertTrue(repo.chats(roomId: "r1")[0].isPending)
        let sentJSON = try XCTUnwrap(socket.sent.first)
        let sent = try JSONDecoder().decode(ChatDto.self, from: Data(sentJSON.utf8))
        XCTAssertEqual(sent.sender, "me")
        XCTAssertEqual(sent.message, "hello")
        XCTAssertEqual(sent.chatTime, "2026-09-05 12:00:00")

        repo.socketDidReceive(.chat(ChatDto(id: 77, chatType: 1, roomId: "r1", sender: "me", message: "hello", chatTime: "2026-09-05 12:00:01")))

        let chats = repo.chats(roomId: "r1")
        XCTAssertEqual(chats.count, 1)
        XCTAssertEqual(chats[0].id, 77)
        XCTAssertFalse(chats[0].isPending)
    }

    func testSendWhileDisconnectedMarksFailed() {
        socket.connected = false
        repo.sendText(roomId: "r1", text: "x")
        XCTAssertTrue(repo.chats(roomId: "r1")[0].isFailed)
        XCTAssertTrue(socket.sent.isEmpty)
    }

    func testIncomingChatForOtherRoomBumpsUnreadAndRoomPreview() async {
        api.rooms = [room("r1", lastChatTime: "2026-09-05 10:00:00")]
        await repo.refreshRooms()

        repo.socketDidReceive(.chat(ChatDto(id: 9, chatType: 1, roomId: "r1", sender: "other", message: "yo", chatTime: "2026-09-05 12:30:00")))

        XCTAssertEqual(repo.unreadCount(roomId: "r1"), 1)
        XCTAssertEqual(repo.rooms[0].lastChatMsg, "yo")
        XCTAssertEqual(repo.rooms[0].lastChatId, "9")
    }

    func testIncomingChatForActiveRoomSendsReadReceiptAndKeepsUnreadZero() async {
        socket.connected = true
        api.rooms = [room("r1", lastChatTime: "2026-09-05 10:00:00")]
        await repo.refreshRooms()
        await repo.enterRoom("r1")
        socket.sent.removeAll()

        repo.socketDidReceive(.chat(ChatDto(id: 9, chatType: 1, roomId: "r1", sender: "other", message: "yo", chatTime: "2026-09-05 12:30:00")))

        XCTAssertEqual(repo.unreadCount(roomId: "r1"), 0)
        XCTAssertTrue(socket.sent.contains { $0.contains("\"READ\"") && $0.contains("\"r1\"") })
        try? await Task.sleep(nanoseconds: 50_000_000)   // REST 커서 갱신은 별도 Task 에서 돈다
        XCTAssertEqual(api.lastReadCalls, ["r1"], "REST 커서도 함께 올린다")
    }

    func testReadFrameUpdatesCursorSoOwnMessagesShowAsRead() {
        repo.socketDidReceive(.read(roomId: "r1", userId: "other", lastReadChatId: 40))
        XCTAssertEqual(repo.readCursor(roomId: "r1", userId: "other"), 40)
        XCTAssertEqual(repo.unreadMemberCount(roomId: "r1", chatId: 40, memberIds: ["me", "other"]), 0)
        XCTAssertEqual(repo.unreadMemberCount(roomId: "r1", chatId: 41, memberIds: ["me", "other"]), 1)
    }

    func testRoomCreatedFrameRefreshesRooms() async {
        api.rooms = [room("r9", lastChatTime: "")]
        repo.socketDidReceive(.roomCreated(roomId: "r9"))
        try? await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertEqual(repo.rooms.map(\.roomId), ["r9"])
    }

    func testEnterRoomLoadsHistorySortedAscending() async {
        api.page = [chat(3), chat(1), chat(2)]
        await repo.enterRoom("r1")
        XCTAssertEqual(repo.chats(roomId: "r1").map(\.id), [1, 2, 3])
    }

    func testLoadPreviousPrependsOlderPage() async {
        api.page = [chat(10), chat(11)]
        await repo.enterRoom("r1")
        api.prev = [chat(8), chat(9)]
        await repo.loadPrevious("r1")
        XCTAssertEqual(repo.chats(roomId: "r1").map(\.id), [8, 9, 10, 11])
        XCTAssertEqual(api.prevCalls, [10])
    }

    func testReconnectRecoversGapForActiveRoom() async {
        api.page = [chat(1)]
        await repo.enterRoom("r1")
        api.page = [chat(1), chat(2)]
        repo.socketDidReconnect()
        try? await Task.sleep(nanoseconds: 50_000_000)
        XCTAssertEqual(repo.chats(roomId: "r1").map(\.id), [1, 2])
    }

    func testLogoutClearsEverything() async {
        api.rooms = [room("r1", lastChatTime: "")]
        await repo.refreshRooms()
        repo.clear()
        XCTAssertTrue(repo.rooms.isEmpty)
        XCTAssertTrue(socket.disconnected)
    }

    // MARK: helpers
    private func room(_ id: String, lastChatTime: String) -> ChatRoomDto {
        ChatRoomDto(roomId: id, roomName: id, roomImage: nil, lastChatMsg: "", lastChatId: "", lastChatTime: lastChatTime,
                    members: [MemberDto(id: 1, userId: "me"), MemberDto(id: 2, userId: "other")])
    }
    private func chat(_ id: Int64) -> ChatDto {
        ChatDto(id: id, chatType: 1, roomId: "r1", sender: "other", message: "m\(id)", chatTime: "2026-09-05 10:00:00")
    }
}

final class MockChatAPI: ChatAPI {
    var rooms: [ChatRoomDto] = []
    var unread: [ChatRoomUnreadDto] = []
    var cursors: [ChatReadCursorDto] = []
    var page: [ChatDto] = []
    var prev: [ChatDto] = []
    var prevCalls: [Int64] = []
    var lastReadCalls: [String] = []

    func chatRooms(memberId: String) async throws -> [ChatRoomDto] { rooms }
    func unreadCounts(userId: String) async throws -> [ChatRoomUnreadDto] { unread }
    func readCursors(roomId: String) async throws -> [ChatReadCursorDto] { cursors }
    func updateLastRead(roomId: String, userId: String) async throws { lastReadCalls.append(roomId) }
    func chatPage(roomId: String, size: Int) async throws -> [ChatDto] { page }
    func prevChats(roomId: String, beforeChatId: Int64, size: Int) async throws -> [ChatDto] { prevCalls.append(beforeChatId); return prev }
    func createChatRoom(memberIds: [Int64]) async throws -> ChatRoomDto { rooms[0] }
    func exitChatRoom(roomId: String, userId: String) async throws {}
    func upload(fileName: String, data: Data, mimeType: String) async throws -> String { fileName }
}

final class MockSocket: ChatSocket {
    var connected = false
    var sent: [String] = []
    var disconnected = false
    weak var listener: ChatSocketListener?
    var state: ConnectionState { connected ? .connected : .disconnected }
    func connect() { connected = true }
    func disconnect() { connected = false; disconnected = true }
    func send(_ text: String) -> Bool { guard connected else { return false }; sent.append(text); return true }
}
