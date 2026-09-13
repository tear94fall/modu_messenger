import Foundation

/// 요청 하나의 설명. 경로는 안드로이드 Retrofit 인터페이스와 글자 단위로 같다.
struct Endpoint {
    var method: String
    var path: String
    var body: Data?
    var contentType: String?
    var extraHeaders: [String: String] = [:]

    private static let encoder = JSONEncoder()

    private static func json<T: Encodable>(_ value: T) -> Data { (try? encoder.encode(value)) ?? Data() }

    private static func get(_ path: String) -> Endpoint { Endpoint(method: "GET", path: path, body: nil, contentType: nil) }
    private static func post<T: Encodable>(_ path: String, _ body: T) -> Endpoint {
        Endpoint(method: "POST", path: path, body: json(body), contentType: "application/json")
    }

    // MARK: auth-service
    static func login(userId: String, email: String) -> Endpoint {
        post("auth-service/api-public/login", RequestLoginDto(userId: userId, email: email))
    }
    static var reissue: Endpoint { Endpoint(method: "POST", path: "auth-service/api-public/auth/reissue", body: nil, contentType: nil) }
    static var logout: Endpoint { Endpoint(method: "POST", path: "auth-service/api-public/auth/logout", body: nil, contentType: nil) }

    // MARK: member-service
    static func signup(idToken: String) -> Endpoint {
        post("member-service/api-public/member/signup", GoogleLoginRequest(authType: "google", idToken: idToken))
    }
    static func memberByEmail(_ email: String) -> Endpoint { get("member-service/api-public/member/\(email)") }
    static func memberById(_ id: Int64) -> Endpoint { get("member-service/api-public/member/member/\(id)") }
    static func friends(userId: String) -> Endpoint { get("member-service/api-public/member/\(userId)/friends") }
    static func searchFriend(email: String) -> Endpoint { get("member-service/api-public/member/friends/\(email)") }
    static func addFriend(userId: String, email: String) -> Endpoint {
        post("member-service/api-public/member/\(userId)/friends", AddFriendDto(email: email))
    }
    static func updateProfile(userId: String, _ dto: UpdateProfileDto) -> Endpoint {
        post("member-service/api-public/member/\(userId)", dto)
    }

    // MARK: chat-service
    static func chatRooms(memberId: String) -> Endpoint { get("chat-service/api-public/chat/\(memberId)/rooms") }
    static func chatRoom(roomId: String) -> Endpoint { get("chat-service/api-public/chat/\(roomId)/room") }
    static func createChatRoom(memberIds: [Int64]) -> Endpoint { post("chat-service/api-public/chat/chat/room", memberIds) }
    static func exitChatRoom(roomId: String, userId: String) -> Endpoint {
        Endpoint(method: "DELETE", path: "chat-service/api-public/chat/\(roomId)/member/\(userId)", body: nil, contentType: nil)
    }
    static func unreadCounts(userId: String) -> Endpoint { get("chat-service/api-public/chat/unread/\(userId)") }
    static func readCursors(roomId: String) -> Endpoint { get("chat-service/api-public/chat/read/\(roomId)") }
    static func updateLastRead(roomId: String, userId: String) -> Endpoint {
        Endpoint(method: "POST", path: "chat-service/api-public/chat/read/\(roomId)/\(userId)", body: nil, contentType: nil)
    }
    static func chatPage(roomId: String, size: Int) -> Endpoint { get("chat-service/api-public/chat/\(roomId)/page/\(size)") }
    static func prevChats(roomId: String, beforeChatId: Int64, size: Int) -> Endpoint {
        get("chat-service/api-public/chat/\(roomId)/\(beforeChatId)/\(size)")
    }

    // MARK: storage-service
    static func upload(fileName: String, data: Data, mimeType: String) -> Endpoint {
        let boundary = "ModuBoundary-\(UUID().uuidString)"
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(data)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        return Endpoint(method: "POST", path: "storage-service/api-public/upload", body: body,
                        contentType: "multipart/form-data; boundary=\(boundary)")
    }
}
