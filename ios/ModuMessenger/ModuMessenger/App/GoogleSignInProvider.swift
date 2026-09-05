import Foundation
import GoogleSignIn
import UIKit

/// GoogleSignIn SDK 래퍼. serverClientID 를 주면 ID 토큰의 audience 가 백엔드 웹 클라이언트 ID 가 되어
/// member-service 의 GoogleIdTokenValidator 를 통과한다.
struct GoogleSignInProvider: GoogleIDTokenProvider {
    let config: AppConfig

    @MainActor
    func fetchIDToken() async throws -> String {
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: config.googleClientID,
                                                                  serverClientID: config.googleServerClientID)
        guard let presenter = Self.topViewController() else {
            throw NSError(domain: "GoogleSignIn", code: -1, userInfo: [NSLocalizedDescriptionKey: "로그인 화면을 띄울 수 없습니다."])
        }
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        guard let token = result.user.idToken?.tokenString else {
            throw NSError(domain: "GoogleSignIn", code: -2, userInfo: [NSLocalizedDescriptionKey: "Google 이 ID 토큰을 주지 않았습니다."])
        }
        return token
    }

    func signOut() { GIDSignIn.sharedInstance.signOut() }

    @MainActor
    private static func topViewController() -> UIViewController? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        let window = scenes.flatMap(\.windows).first { $0.isKeyWindow } ?? scenes.first?.windows.first
        var top = window?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
