import Foundation

/// 친구 목록·검색·추가. 안드로이드 FragmentFriends / FindFriendsActivity 에 해당한다.
@MainActor
final class FriendsStore: ObservableObject {
    @Published private(set) var friends: [MemberDto] = []
    @Published private(set) var isLoading = false
    @Published var errorMessage: String?

    private let api: MemberAPI

    init(api: MemberAPI) { self.api = api }

    func load(userId: String) async {
        guard !userId.isEmpty else { return }
        isLoading = true
        defer { isLoading = false }
        do {
            friends = try await api.friends(userId: userId).sorted { $0.displayName < $1.displayName }
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func search(email: String) async throws -> [MemberDto] {
        try await api.searchFriend(email: email)
    }

    func add(userId: String, email: String) async throws {
        _ = try await api.addFriend(userId: userId, email: email)
        await load(userId: userId)
    }

    func clear() { friends = [] }
}
