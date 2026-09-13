import Foundation

/// Config/AppConfig.xcconfig → Info.plist 로 들어온 설정값.
struct AppConfig {
    let apiBaseURL: URL
    let wsBaseURL: URL
    let googleClientID: String
    let googleServerClientID: String

    static let wsPath = "ws-service/modu-chat"

    var socketURL: URL { wsBaseURL.appendingPathComponent(Self.wsPath) }

    /// 프로필/채팅 이미지 조회 주소. Authorization 헤더가 있어야 열린다.
    func imageURL(fileName: String?) -> URL? {
        guard let fileName, !fileName.isEmpty else { return nil }
        if fileName.hasPrefix("http") { return URL(string: fileName) }
        return URL(string: "storage-service/api-public/view/\(fileName)", relativeTo: apiBaseURL)?.absoluteURL
    }

    /// iOS 클라이언트 ID 를 xcconfig 에 채웠는지.
    var isGoogleConfigured: Bool {
        !googleClientID.isEmpty && !googleClientID.hasPrefix("REPLACE_ME")
    }

    static func fromBundle(_ bundle: Bundle = .main) -> AppConfig {
        func string(_ key: String) -> String { (bundle.object(forInfoDictionaryKey: key) as? String) ?? "" }
        let api = URL(string: string("APIBaseURL")) ?? URL(string: "http://localhost:8000/")!
        let ws = URL(string: string("WSBaseURL")) ?? URL(string: "ws://localhost:8000/")!
        return AppConfig(apiBaseURL: api, wsBaseURL: ws,
                         googleClientID: string("GIDClientID"),
                         googleServerClientID: string("GIDServerClientID"))
    }
}
