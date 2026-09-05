import Foundation
import os

/// 구글 ID 토큰을 얻는 쪽. 실제 구현은 GoogleSignIn SDK, 테스트는 목.
protocol GoogleIDTokenProvider {
    func fetchIDToken() async throws -> String
    func signOut()
}

enum SessionError: LocalizedError {
    case googleNotConfigured
    case missingIdentity

    var errorDescription: String? {
        switch self {
        case .googleNotConfigured:
            return "Google 로그인이 설정되지 않았습니다. Config/AppConfig.xcconfig 의 GOOGLE_IOS_CLIENT_ID 를 채워 주세요."
        case .missingIdentity:
            return "회원가입 응답에 아이디가 없습니다."
        }
    }
}

/// 로그인 상태 머신. 안드로이드 LoginActivity + App.onLoggedIn 에 해당한다.
@MainActor
final class SessionStore: ObservableObject {
    enum State: Equatable { case restoring, loggedOut, loggingIn, loggedIn }

    @Published private(set) var state: State = .restoring
    @Published private(set) var member: MemberDto?
    @Published var errorMessage: String?

    private let config: AppConfig
    private let api: MemberAPI
    private let tokenStore: TokenStore
    private let chat: ChatRepository
    private let google: GoogleIDTokenProvider
    private let defaults: UserDefaults
    private let log = Logger(subsystem: "com.example.modumessenger", category: "session")

    private enum Key {
        static let userId = "login.userId"
        static let email = "login.email"
        static let member = "member"
    }

    init(config: AppConfig, api: MemberAPI, tokenStore: TokenStore, chat: ChatRepository,
         google: GoogleIDTokenProvider, defaults: UserDefaults = .standard) {
        self.config = config
        self.api = api
        self.tokenStore = tokenStore
        self.chat = chat
        self.google = google
        self.defaults = defaults
    }

    var myUserId: String { member?.userId ?? "" }

    // MARK: flows

    /// 앱 시작. 저장된 userId/email 이 있으면 조용히 다시 로그인한다.
    func restore() async {
        guard let userId = defaults.string(forKey: Key.userId), let email = defaults.string(forKey: Key.email) else {
            state = .loggedOut
            return
        }
        if let data = defaults.data(forKey: Key.member), let cached = try? JSONDecoder().decode(MemberDto.self, from: data) {
            member = cached
        }
        await login(userId: userId, email: email)
    }

    func signInWithGoogle() async {
        guard config.isGoogleConfigured else {
            errorMessage = SessionError.googleNotConfigured.errorDescription
            return
        }
        state = .loggingIn
        errorMessage = nil
        do {
            let idToken = try await google.fetchIDToken()
            let signup = try await api.signup(idToken: idToken)
            guard let userId = signup.userId, let email = signup.email else { throw SessionError.missingIdentity }
            await login(userId: userId, email: email)
        } catch {
            log.error("google sign-in: \(error.localizedDescription)")
            errorMessage = error.localizedDescription
            google.signOut()
            state = .loggedOut
        }
    }

    private func login(userId: String, email: String) async {
        state = .loggingIn
        do {
            try await api.login(userId: userId, email: email)
            let me = try await api.member(email: email)
            defaults.set(userId, forKey: Key.userId)
            defaults.set(email, forKey: Key.email)
            apply(member: me)
            state = .loggedIn
            chat.setIdentity(userId: me.userId ?? userId, memberId: me.id.map(String.init))
            chat.connect()
            await chat.refreshRooms()
        } catch {
            log.error("login: \(error.localizedDescription)")
            errorMessage = error.localizedDescription
            // 캐시된 회원 정보가 있으면 오프라인으로라도 들어간다 (안드로이드와 같은 동작).
            if member != nil, case APIError.transport = error {
                state = .loggedIn
                chat.setIdentity(userId: member?.userId ?? userId, memberId: member?.id.map(String.init))
                chat.connect()
            } else {
                state = .loggedOut
            }
        }
    }

    func logout() async {
        try? await api.logout()
        google.signOut()
        chat.clear()
        tokenStore.clear()
        defaults.removeObject(forKey: Key.userId)
        defaults.removeObject(forKey: Key.email)
        defaults.removeObject(forKey: Key.member)
        member = nil
        state = .loggedOut
    }

    func refreshMember() async {
        guard let email = member?.email else { return }
        if let me = try? await api.member(email: email) { apply(member: me) }
    }

    func updateProfile(username: String, statusMessage: String, profileImage: String?) async throws {
        guard let userId = member?.userId else { return }
        let dto = UpdateProfileDto(username: username, statusMessage: statusMessage,
                                   profileImage: profileImage ?? member?.profileImage ?? "",
                                   wallpaperImage: member?.wallpaperImage ?? "")
        let updated = try await api.updateProfile(userId: userId, dto)
        apply(member: updated)
    }

    func uploadProfileImage(data: Data) async throws -> String {
        try await api.upload(fileName: "profile-\(Int(Date().timeIntervalSince1970)).jpg", data: data, mimeType: "image/jpeg")
    }

    private func apply(member me: MemberDto) {
        member = me
        if let data = try? JSONEncoder().encode(me) { defaults.set(data, forKey: Key.member) }
    }
}
