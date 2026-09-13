import Foundation

/// 채팅 저장소가 필요로 하는 REST 호출. 테스트에서 목으로 바꾼다.
protocol ChatAPI: AnyObject {
    func chatRooms(memberId: String) async throws -> [ChatRoomDto]
    func unreadCounts(userId: String) async throws -> [ChatRoomUnreadDto]
    func readCursors(roomId: String) async throws -> [ChatReadCursorDto]
    func updateLastRead(roomId: String, userId: String) async throws
    func chatPage(roomId: String, size: Int) async throws -> [ChatDto]
    func prevChats(roomId: String, beforeChatId: Int64, size: Int) async throws -> [ChatDto]
    func createChatRoom(memberIds: [Int64]) async throws -> ChatRoomDto
    func exitChatRoom(roomId: String, userId: String) async throws
    func upload(fileName: String, data: Data, mimeType: String) async throws -> String
}

/// 회원/친구/프로필 호출.
protocol MemberAPI: AnyObject {
    func signup(idToken: String) async throws -> SignUpDto
    func login(userId: String, email: String) async throws
    func logout() async throws
    func member(email: String) async throws -> MemberDto
    func member(id: Int64) async throws -> MemberDto
    func friends(userId: String) async throws -> [MemberDto]
    func searchFriend(email: String) async throws -> [MemberDto]
    func addFriend(userId: String, email: String) async throws -> MemberDto
    func updateProfile(userId: String, _ dto: UpdateProfileDto) async throws -> MemberDto
    func upload(fileName: String, data: Data, mimeType: String) async throws -> String
}

/// APIClient 위의 얇은 어댑터. 두 프로토콜을 모두 구현한다.
final class BackendServices: ChatAPI, MemberAPI {
    let client: APIClient
    init(client: APIClient) { self.client = client }

    func chatRooms(memberId: String) async throws -> [ChatRoomDto] { try await client.send(.chatRooms(memberId: memberId)) }
    func unreadCounts(userId: String) async throws -> [ChatRoomUnreadDto] { try await client.send(.unreadCounts(userId: userId)) }
    func readCursors(roomId: String) async throws -> [ChatReadCursorDto] { try await client.send(.readCursors(roomId: roomId)) }
    func updateLastRead(roomId: String, userId: String) async throws { try await client.sendVoid(.updateLastRead(roomId: roomId, userId: userId)) }
    func chatPage(roomId: String, size: Int) async throws -> [ChatDto] { try await client.send(.chatPage(roomId: roomId, size: size)) }
    func prevChats(roomId: String, beforeChatId: Int64, size: Int) async throws -> [ChatDto] {
        try await client.send(.prevChats(roomId: roomId, beforeChatId: beforeChatId, size: size))
    }
    func createChatRoom(memberIds: [Int64]) async throws -> ChatRoomDto { try await client.send(.createChatRoom(memberIds: memberIds)) }
    func exitChatRoom(roomId: String, userId: String) async throws { try await client.sendVoid(.exitChatRoom(roomId: roomId, userId: userId)) }
    func upload(fileName: String, data: Data, mimeType: String) async throws -> String {
        try await client.sendString(.upload(fileName: fileName, data: data, mimeType: mimeType))
    }

    func signup(idToken: String) async throws -> SignUpDto { try await client.send(.signup(idToken: idToken)) }
    func login(userId: String, email: String) async throws { try await client.login(userId: userId, email: email) }
    func logout() async throws { try await client.sendVoid(.logout) }
    func member(email: String) async throws -> MemberDto { try await client.send(.memberByEmail(email)) }
    func member(id: Int64) async throws -> MemberDto { try await client.send(.memberById(id)) }
    func friends(userId: String) async throws -> [MemberDto] { try await client.send(.friends(userId: userId)) }
    func searchFriend(email: String) async throws -> [MemberDto] { try await client.send(.searchFriend(email: email)) }
    func addFriend(userId: String, email: String) async throws -> MemberDto { try await client.send(.addFriend(userId: userId, email: email)) }
    func updateProfile(userId: String, _ dto: UpdateProfileDto) async throws -> MemberDto { try await client.send(.updateProfile(userId: userId, dto)) }
}
