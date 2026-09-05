import XCTest
@testable import ModuMessenger

final class ChatSocketFrameTests: XCTestCase {
    func testParsesChatFrame() throws {
        let frame = ChatSocketFrame.parse("""
        {"id":5,"chatType":1,"roomId":"r1","sender":"u1","message":"hi","chatTime":"2026-09-05 10:00:00"}
        """)
        guard case .chat(let dto)? = frame else { return XCTFail("expected chat, got \(String(describing: frame))") }
        XCTAssertEqual(dto.id, 5)
        XCTAssertEqual(dto.message, "hi")
    }

    func testParsesReadFrameWithStringCursor() throws {
        let frame = ChatSocketFrame.parse(#"{"type":"READ","roomId":"r1","userId":"u2","lastReadChatId":"42"}"#)
        XCTAssertEqual(frame, .read(roomId: "r1", userId: "u2", lastReadChatId: 42))
    }

    func testParsesReadFrameWithNumericCursor() throws {
        let frame = ChatSocketFrame.parse(#"{"type":"READ","roomId":"r1","userId":"u2","lastReadChatId":42}"#)
        XCTAssertEqual(frame, .read(roomId: "r1", userId: "u2", lastReadChatId: 42))
    }

    func testParsesRoomCreatedFrame() throws {
        XCTAssertEqual(ChatSocketFrame.parse(#"{"type":"ROOM_CREATED","roomId":"r9"}"#), .roomCreated(roomId: "r9"))
    }

    func testMalformedFramesAreDropped() {
        XCTAssertNil(ChatSocketFrame.parse("not json"))
        XCTAssertNil(ChatSocketFrame.parse(#"{"type":"READ","roomId":"r1"}"#))
        XCTAssertNil(ChatSocketFrame.parse(#"{"type":"UNKNOWN"}"#))
        // chatType 이 없는 채팅 프레임은 채팅으로 볼 수 없다.
        XCTAssertNil(ChatSocketFrame.parse(#"{"roomId":"r1","sender":"u1"}"#))
    }

    func testOutgoingReadFrameShape() throws {
        let data = try ChatSocketFrame.readReceiptPayload(roomId: "r1", sender: "me")
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: String])
        XCTAssertEqual(object, ["type": "READ", "roomId": "r1", "sender": "me"])
    }
}
