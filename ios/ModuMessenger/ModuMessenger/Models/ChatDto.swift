import Foundation

/// 소켓과 REST 양쪽에서 쓰는 채팅 한 건. 보낼 때는 id 를 비워 두고 서버가 채운다.
struct ChatDto: Codable, Equatable, Hashable, Identifiable {
    var id: Int64?
    var chatType: Int
    var roomId: String?
    var sender: String?
    var message: String?
    var chatTime: String?

    private enum CodingKeys: String, CodingKey { case id, chatType, roomId, sender, message, chatTime }

    init(id: Int64?, chatType: Int, roomId: String?, sender: String?, message: String?, chatTime: String?) {
        self.id = id; self.chatType = chatType; self.roomId = roomId
        self.sender = sender; self.message = message; self.chatTime = chatTime
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(Int64.self, forKey: .id)
        chatType = try c.decodeIfPresent(Int.self, forKey: .chatType) ?? ChatType.invalid.rawValue
        roomId = try c.decodeIfPresent(String.self, forKey: .roomId)
        sender = try c.decodeIfPresent(String.self, forKey: .sender)
        message = try c.decodeIfPresent(String.self, forKey: .message)
        chatTime = try c.decodeIfPresent(String.self, forKey: .chatTime)
    }

    func encode(to encoder: Encoder) throws {
        var c = encoder.container(keyedBy: CodingKeys.self)
        try c.encodeIfPresent(id, forKey: .id)
        try c.encode(chatType, forKey: .chatType)
        try c.encodeIfPresent(roomId, forKey: .roomId)
        try c.encodeIfPresent(sender, forKey: .sender)
        try c.encodeIfPresent(message, forKey: .message)
        try c.encodeIfPresent(chatTime, forKey: .chatTime)
    }

    var kind: ChatType { ChatType(rawValue: chatType) ?? .invalid }
}

struct ChatRoomDto: Codable, Equatable, Hashable, Identifiable {
    var roomId: String?
    var roomName: String?
    var roomImage: String?
    var lastChatMsg: String?
    var lastChatId: String?
    var lastChatTime: String?
    var members: [MemberDto]?

    var id: String { roomId ?? "" }

    init(roomId: String?, roomName: String?, roomImage: String?, lastChatMsg: String?, lastChatId: String?,
         lastChatTime: String?, members: [MemberDto]?) {
        self.roomId = roomId; self.roomName = roomName; self.roomImage = roomImage; self.lastChatMsg = lastChatMsg
        self.lastChatId = lastChatId; self.lastChatTime = lastChatTime; self.members = members
    }

    /// 방 이름이 비어 있으면 나를 뺀 멤버 이름을 이어 붙인다 (안드로이드와 같은 규칙).
    func displayName(myUserId: String) -> String {
        if let name = roomName, !name.isEmpty { return name }
        let others = (members ?? []).filter { $0.userId != myUserId }.map(\.displayName).filter { !$0.isEmpty }
        return others.isEmpty ? "대화상대 없음" : others.joined(separator: ", ")
    }

    func member(userId: String) -> MemberDto? { members?.first { $0.userId == userId } }
}

/// GET chat/unread/{userId} 응답 한 건.
struct ChatRoomUnreadDto: Codable, Equatable {
    var roomId: String?
    var lastSendChatId: Int64?
    var lastReadChatId: Int64?
    var unreadChatCount: Int64?
}

/// GET chat/read/{roomId} 응답 한 건. 방 멤버 한 명의 읽음 커서.
struct ChatReadCursorDto: Codable, Equatable {
    var userId: String?
    var lastReadChatId: Int64?
}
