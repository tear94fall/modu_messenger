import XCTest
@testable import ModuMessenger

/// 백엔드(Jackson/Gson)가 내려주는 JSON 모양 그대로 디코딩되는지 확인한다.
/// null 필드가 흔하므로 전부 옵셔널로 받아야 한다.
final class ModelDecodingTests: XCTestCase {
    private let decoder = JSONDecoder()

    func testMemberDtoDecodesWithNulls() throws {
        let json = """
        {"id":7,"userId":"a@b.com","email":"a@b.com","auth":"google","role":"ROLE_USER",
         "username":"준섭","statusMessage":null,"profileImage":"","wallpaperImage":null,"profiles":null}
        """.data(using: .utf8)!
        let dto = try decoder.decode(MemberDto.self, from: json)
        XCTAssertEqual(dto.id, 7)
        XCTAssertEqual(dto.userId, "a@b.com")
        XCTAssertEqual(dto.username, "준섭")
        XCTAssertNil(dto.statusMessage)
        XCTAssertEqual(dto.profileImage, "")
        XCTAssertNil(dto.profiles)
    }

    func testChatRoomDtoDecodesMembers() throws {
        let json = """
        {"roomId":"r1","roomName":"방","roomImage":null,"lastChatMsg":"hi","lastChatId":"12",
         "lastChatTime":"2026-09-05 10:11:12","members":[{"id":1,"userId":"u1"},{"id":2,"userId":"u2"}]}
        """.data(using: .utf8)!
        let dto = try decoder.decode(ChatRoomDto.self, from: json)
        XCTAssertEqual(dto.roomId, "r1")
        XCTAssertEqual(dto.members?.count, 2)
        XCTAssertEqual(dto.lastChatId, "12")
    }

    func testChatDtoRoundTrip() throws {
        let json = """
        {"id":99,"chatType":2,"roomId":"r1","sender":"u1","message":"pic.png","chatTime":"2026-09-05 10:11:12"}
        """.data(using: .utf8)!
        let dto = try decoder.decode(ChatDto.self, from: json)
        XCTAssertEqual(dto.id, 99)
        XCTAssertEqual(dto.kind, .image)

        let encoded = try JSONEncoder().encode(dto)
        let back = try decoder.decode(ChatDto.self, from: encoded)
        XCTAssertEqual(back, dto)
    }

    func testOutgoingChatDtoOmitsNilId() throws {
        let dto = ChatDto(id: nil, chatType: ChatType.text.rawValue, roomId: "r1", sender: "u1", message: "hi", chatTime: "2026-09-05 10:11:12")
        let encoded = try JSONEncoder().encode(dto)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        XCTAssertNil(object["id"])
        XCTAssertEqual(object["chatType"] as? Int, 1)
    }

    func testUnreadAndReadCursorDtos() throws {
        let unread = try decoder.decode([ChatRoomUnreadDto].self, from: """
        [{"roomId":"r1","lastSendChatId":10,"lastReadChatId":7,"unreadChatCount":3}]
        """.data(using: .utf8)!)
        XCTAssertEqual(unread.first?.unreadChatCount, 3)

        let cursors = try decoder.decode([ChatReadCursorDto].self, from: """
        [{"userId":"u1","lastReadChatId":10},{"userId":"u2","lastReadChatId":null}]
        """.data(using: .utf8)!)
        XCTAssertEqual(cursors[0].lastReadChatId, 10)
        XCTAssertNil(cursors[1].lastReadChatId)
    }

    func testTokenResponseDto() throws {
        let dto = try decoder.decode(TokenResponseDto.self, from: """
        {"accessToken":"a","refreshToken":"r"}
        """.data(using: .utf8)!)
        XCTAssertEqual(dto.accessToken, "a")
        XCTAssertEqual(dto.refreshToken, "r")
    }
}
