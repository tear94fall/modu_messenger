import XCTest
@testable import ModuMessenger

/// 실제 네트워크 없이 요청 헤더/재발급 흐름을 검증한다.
final class APIClientTests: XCTestCase {
    private var transport: MockTransport!
    private var tokens: InMemoryTokenStore!
    private var client: APIClient!

    override func setUp() {
        super.setUp()
        transport = MockTransport()
        tokens = InMemoryTokenStore()
        client = APIClient(baseURL: URL(string: "http://h:8000/")!, tokenStore: tokens, transport: transport)
    }

    func testBuildsAbsoluteURLAndAuthorizationHeader() async throws {
        tokens.accessToken = "Bearer abc"
        transport.enqueue(status: 200, body: #"{"id":1,"userId":"u1"}"#)

        let member: MemberDto = try await client.send(.memberByEmail("u1"))

        XCTAssertEqual(member.id, 1)
        let request = try XCTUnwrap(transport.requests.first)
        XCTAssertEqual(request.url?.absoluteString, "http://h:8000/member-service/api-public/member/u1")
        XCTAssertEqual(request.value(forHTTPHeaderField: "Authorization"), "Bearer abc")
        XCTAssertEqual(request.httpMethod, "GET")
    }

    func testLoginStoresTokensFromHeadersWithBearerPrefix() async throws {
        transport.enqueue(status: 200, body: "", headers: ["access-token": "A1", "refresh-token": "R1"])

        try await client.login(userId: "u1", email: "u1@x.com")

        XCTAssertEqual(tokens.accessToken, "Bearer A1")
        XCTAssertEqual(tokens.refreshToken, "Bearer R1")
        let sent = try XCTUnwrap(transport.requests.first?.httpBody)
        let object = try XCTUnwrap(JSONSerialization.jsonObject(with: sent) as? [String: String])
        XCTAssertEqual(object, ["userId": "u1", "email": "u1@x.com"])
    }

    func testUnauthorizedTriggersReissueAndRetriesOnce() async throws {
        tokens.accessToken = "Bearer old"
        tokens.refreshToken = "Bearer refresh"
        transport.enqueue(status: 401, body: "")
        transport.enqueue(status: 200, body: #"{"accessToken":"new","refreshToken":"newR"}"#)
        transport.enqueue(status: 200, body: #"[]"#)

        let rooms: [ChatRoomDto] = try await client.send(.chatRooms(memberId: "1"))

        XCTAssertTrue(rooms.isEmpty)
        XCTAssertEqual(transport.requests.count, 3)
        XCTAssertEqual(transport.requests[1].url?.path, "/auth-service/api-public/auth/reissue")
        XCTAssertEqual(transport.requests[1].value(forHTTPHeaderField: "refresh-token"), "Bearer refresh")
        XCTAssertEqual(transport.requests[2].value(forHTTPHeaderField: "Authorization"), "Bearer new")
        XCTAssertEqual(tokens.refreshToken, "Bearer newR")
    }

    func testUnauthorizedWithoutRefreshTokenFails() async {
        transport.enqueue(status: 401, body: "")
        do {
            let _: [ChatRoomDto] = try await client.send(.chatRooms(memberId: "1"))
            XCTFail("expected failure")
        } catch let error as APIError {
            guard case .unauthorized = error else { return XCTFail("unexpected \(error)") }
        } catch {
            XCTFail("unexpected \(error)")
        }
        XCTAssertEqual(transport.requests.count, 1)
    }

    func testServerErrorSurfacesStatusCode() async {
        transport.enqueue(status: 500, body: "boom")
        do {
            let _: [ChatRoomDto] = try await client.send(.chatRooms(memberId: "1"))
            XCTFail("expected failure")
        } catch let error as APIError {
            XCTAssertEqual(error, .http(status: 500, body: "boom"))
        } catch {
            XCTFail("unexpected \(error)")
        }
    }

    func testUploadReturnsPlainStringFileName() async throws {
        transport.enqueue(status: 200, body: "stored-name.jpg")
        let name = try await client.sendString(.upload(fileName: "a.jpg", data: Data("x".utf8), mimeType: "image/jpeg"))
        XCTAssertEqual(name, "stored-name.jpg")
    }
}

final class MockTransport: HTTPTransport {
    struct Canned { let status: Int; let body: String; let headers: [String: String] }
    private var queue: [Canned] = []
    private(set) var requests: [URLRequest] = []

    func enqueue(status: Int, body: String, headers: [String: String] = [:]) {
        queue.append(Canned(status: status, body: body, headers: headers))
    }

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        requests.append(request)
        guard !queue.isEmpty else { throw URLError(.badServerResponse) }
        let canned = queue.removeFirst()
        let response = HTTPURLResponse(url: request.url!, statusCode: canned.status, httpVersion: nil, headerFields: canned.headers)!
        return (Data(canned.body.utf8), response)
    }
}
