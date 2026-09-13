import Foundation

/// ws-service 가 보내는 프레임 세 종류. READ / ROOM_CREATED 는 ChatDto 모양이 아니라 먼저 갈라낸다.
enum ChatSocketFrame: Equatable {
    case chat(ChatDto)
    case read(roomId: String, userId: String, lastReadChatId: Int64)
    case roomCreated(roomId: String)

    static func parse(_ text: String) -> ChatSocketFrame? {
        guard let data = text.data(using: .utf8),
              let object = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] else { return nil }

        if let type = object["type"] as? String {
            switch type {
            case "READ":
                guard let roomId = object["roomId"] as? String,
                      let userId = object["userId"] as? String,
                      let cursor = parseCursor(object["lastReadChatId"]) else { return nil }
                return .read(roomId: roomId, userId: userId, lastReadChatId: cursor)
            case "ROOM_CREATED":
                guard let roomId = object["roomId"] as? String else { return nil }
                return .roomCreated(roomId: roomId)
            default:
                return nil
            }
        }

        guard object["chatType"] != nil, let dto = try? JSONDecoder().decode(ChatDto.self, from: data) else { return nil }
        return .chat(dto)
    }

    /// 서버는 커서를 숫자로도, 문자열로도 보낸다. 빈 방은 "0".
    private static func parseCursor(_ value: Any?) -> Int64? {
        if let n = value as? NSNumber { return n.int64Value }
        if let s = value as? String { return Int64(s) }
        return nil
    }

    /// "여기까지 읽었다" 프레임. 서버가 커서를 방의 lastChatId 로 올리고 방 인원에게 브로드캐스트한다.
    static func readReceiptPayload(roomId: String, sender: String) throws -> Data {
        try JSONSerialization.data(withJSONObject: ["type": "READ", "roomId": roomId, "sender": sender])
    }
}
