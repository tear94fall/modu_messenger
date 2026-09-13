import XCTest
@testable import ModuMessenger

/// 경로가 안드로이드 Retrofit 인터페이스와 글자 단위로 같아야 한다.
final class EndpointTests: XCTestCase {
    func testAuthPaths() {
        XCTAssertEqual(Endpoint.login(userId: "u", email: "e").path, "auth-service/api-public/login")
        XCTAssertEqual(Endpoint.reissue.path, "auth-service/api-public/auth/reissue")
        XCTAssertEqual(Endpoint.logout.path, "auth-service/api-public/auth/logout")
    }

    func testMemberPaths() {
        XCTAssertEqual(Endpoint.signup(idToken: "t").path, "member-service/api-public/member/signup")
        XCTAssertEqual(Endpoint.memberByEmail("a@b.com").path, "member-service/api-public/member/a@b.com")
        XCTAssertEqual(Endpoint.friends(userId: "u1").path, "member-service/api-public/member/u1/friends")
        XCTAssertEqual(Endpoint.searchFriend(email: "x@y.com").path, "member-service/api-public/member/friends/x@y.com")
        XCTAssertEqual(Endpoint.addFriend(userId: "u1", email: "x").path, "member-service/api-public/member/u1/friends")
        XCTAssertEqual(Endpoint.addFriend(userId: "u1", email: "x").method, "POST")
        XCTAssertEqual(Endpoint.updateProfile(userId: "u1", .init(username: "n", statusMessage: "s", profileImage: "", wallpaperImage: "")).path,
                       "member-service/api-public/member/u1")
    }

    func testChatPaths() {
        XCTAssertEqual(Endpoint.chatRooms(memberId: "3").path, "chat-service/api-public/chat/3/rooms")
        XCTAssertEqual(Endpoint.createChatRoom(memberIds: [1, 2]).path, "chat-service/api-public/chat/chat/room")
        XCTAssertEqual(Endpoint.exitChatRoom(roomId: "r", userId: "u").method, "DELETE")
        XCTAssertEqual(Endpoint.exitChatRoom(roomId: "r", userId: "u").path, "chat-service/api-public/chat/r/member/u")
        XCTAssertEqual(Endpoint.unreadCounts(userId: "u").path, "chat-service/api-public/chat/unread/u")
        XCTAssertEqual(Endpoint.readCursors(roomId: "r").path, "chat-service/api-public/chat/read/r")
        XCTAssertEqual(Endpoint.updateLastRead(roomId: "r", userId: "u").path, "chat-service/api-public/chat/read/r/u")
        XCTAssertEqual(Endpoint.chatPage(roomId: "r", size: 50).path, "chat-service/api-public/chat/r/page/50")
        XCTAssertEqual(Endpoint.prevChats(roomId: "r", beforeChatId: 12, size: 50).path, "chat-service/api-public/chat/r/12/50")
    }

    func testStoragePaths() {
        XCTAssertEqual(Endpoint.upload(fileName: "a.jpg", data: Data(), mimeType: "image/jpeg").path, "storage-service/api-public/upload")
        XCTAssertEqual(AppConfig(apiBaseURL: URL(string: "http://h:8000/")!, wsBaseURL: URL(string: "ws://h:8000/")!, googleClientID: "", googleServerClientID: "")
                        .imageURL(fileName: "pic.png")?.absoluteString,
                       "http://h:8000/storage-service/api-public/view/pic.png")
    }

    func testCreateRoomBodyIsPlainIdArray() throws {
        let body = try XCTUnwrap(Endpoint.createChatRoom(memberIds: [1, 2]).body)
        XCTAssertEqual(String(data: body, encoding: .utf8), "[1,2]")
    }

    func testMultipartBodyContainsFilePart() throws {
        let ep = Endpoint.upload(fileName: "a.jpg", data: Data("abc".utf8), mimeType: "image/jpeg")
        let body = try XCTUnwrap(ep.body)
        let text = String(decoding: body, as: UTF8.self)
        XCTAssertTrue(text.contains("name=\"file\"; filename=\"a.jpg\""))
        XCTAssertTrue(text.contains("Content-Type: image/jpeg"))
        XCTAssertTrue(ep.contentType?.hasPrefix("multipart/form-data; boundary=") == true)
    }
}
