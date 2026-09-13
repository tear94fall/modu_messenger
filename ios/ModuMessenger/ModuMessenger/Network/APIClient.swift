import Foundation

enum APIError: Error, Equatable, LocalizedError {
    case unauthorized
    case http(status: Int, body: String)
    case decoding(String)
    case transport(String)

    var errorDescription: String? {
        switch self {
        case .unauthorized: return "인증이 만료되었습니다. 다시 로그인해 주세요."
        case .http(let status, _): return "서버 응답 오류 (코드 \(status))"
        case .decoding(let detail): return "응답을 읽지 못했습니다: \(detail)"
        case .transport(let detail): return "연결이 원활하지 않습니다: \(detail)"
        }
    }
}

/// URLSession 을 감싸 테스트에서 바꿔 끼울 수 있게 한다.
protocol HTTPTransport {
    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse)
}

struct URLSessionTransport: HTTPTransport {
    let session: URLSession

    init(timeout: TimeInterval = 10) {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = timeout
        config.waitsForConnectivity = false
        session = URLSession(configuration: config)
    }

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse else { throw APIError.transport("HTTP 응답이 아닙니다") }
            return (data, http)
        } catch let error as APIError {
            throw error
        } catch {
            throw APIError.transport(error.localizedDescription)
        }
    }
}

/// 게이트웨이 호출. Authorization 헤더를 붙이고, 401 이면 리프레시 토큰으로 재발급한 뒤 한 번 재시도한다.
final class APIClient {
    let baseURL: URL
    let tokenStore: TokenStore
    private let transport: HTTPTransport
    private let decoder = JSONDecoder()

    init(baseURL: URL, tokenStore: TokenStore, transport: HTTPTransport = URLSessionTransport()) {
        self.baseURL = baseURL
        self.tokenStore = tokenStore
        self.transport = transport
    }

    // MARK: public

    func send<T: Decodable>(_ endpoint: Endpoint) async throws -> T {
        let (data, _) = try await perform(endpoint)
        do {
            return try decoder.decode(T.self, from: data)
        } catch {
            throw APIError.decoding("\(T.self): \(error)")
        }
    }

    /// 본문이 없거나 무시해도 되는 요청.
    func sendVoid(_ endpoint: Endpoint) async throws {
        _ = try await perform(endpoint)
    }

    /// 서버가 JSON 이 아니라 문자열 한 줄(업로드된 파일 이름 등)을 돌려주는 요청.
    func sendString(_ endpoint: Endpoint) async throws -> String {
        let (data, _) = try await perform(endpoint)
        var text = String(decoding: data, as: UTF8.self)
        if text.hasPrefix("\""), text.hasSuffix("\""), text.count >= 2 { text = String(text.dropFirst().dropLast()) }
        return text
    }

    /// 로그인은 본문 없이 헤더로 토큰을 준다. "Bearer " 를 붙여 보관한다.
    func login(userId: String, email: String) async throws {
        let (_, response) = try await perform(.login(userId: userId, email: email), allowReissue: false)
        guard let access = response.value(forHTTPHeaderField: "access-token"),
              let refresh = response.value(forHTTPHeaderField: "refresh-token") else {
            throw APIError.decoding("로그인 응답에 토큰 헤더가 없습니다")
        }
        tokenStore.accessToken = "Bearer \(access)"
        tokenStore.refreshToken = "Bearer \(refresh)"
    }

    // MARK: internals

    private func perform(_ endpoint: Endpoint, allowReissue: Bool = true) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await transport.data(for: request(for: endpoint))
        switch response.statusCode {
        case 200..<400:
            return (data, response)
        case 401:
            guard allowReissue, try await reissue() else { throw APIError.unauthorized }
            let (data2, response2) = try await transport.data(for: request(for: endpoint))
            guard (200..<400).contains(response2.statusCode) else {
                throw response2.statusCode == 401 ? APIError.unauthorized
                    : APIError.http(status: response2.statusCode, body: String(decoding: data2, as: UTF8.self))
            }
            return (data2, response2)
        default:
            throw APIError.http(status: response.statusCode, body: String(decoding: data, as: UTF8.self))
        }
    }

    private func request(for endpoint: Endpoint) -> URLRequest {
        var request = URLRequest(url: URL(string: endpoint.path, relativeTo: baseURL)!.absoluteURL)
        request.httpMethod = endpoint.method
        request.httpBody = endpoint.body
        if let contentType = endpoint.contentType { request.setValue(contentType, forHTTPHeaderField: "Content-Type") }
        if let token = tokenStore.accessToken { request.setValue(token, forHTTPHeaderField: "Authorization") }
        for (key, value) in endpoint.extraHeaders { request.setValue(value, forHTTPHeaderField: key) }
        return request
    }

    /// 리프레시 토큰으로 재발급. 성공하면 true. 리프레시 토큰이 없거나 거절되면 false.
    private func reissue() async throws -> Bool {
        guard let refresh = tokenStore.refreshToken else { return false }
        var endpoint = Endpoint.reissue
        endpoint.extraHeaders["refresh-token"] = refresh
        let (data, response) = try await transport.data(for: request(for: endpoint))
        guard (200..<400).contains(response.statusCode),
              let dto = try? decoder.decode(TokenResponseDto.self, from: data),
              let access = dto.accessToken else { return false }
        tokenStore.accessToken = access.hasPrefix("Bearer ") ? access : "Bearer \(access)"
        if let newRefresh = dto.refreshToken {
            tokenStore.refreshToken = newRefresh.hasPrefix("Bearer ") ? newRefresh : "Bearer \(newRefresh)"
        }
        return true
    }
}
