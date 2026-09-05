import SwiftUI

/// 이메일로 회원을 찾아 친구로 추가한다. 안드로이드 FindFriendsActivity.
struct FindFriendView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var friends: FriendsStore
    @Environment(\.dismiss) private var dismiss
    @State private var email = ""
    @State private var results: [MemberDto] = []
    @State private var message: String?
    @State private var searching = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack {
                        TextField("친구 이메일", text: $email)
                            .keyboardType(.emailAddress).textInputAutocapitalization(.never).autocorrectionDisabled()
                            .onSubmit { Task { await search() } }
                        Button("검색") { Task { await search() } }.disabled(email.isEmpty || searching)
                    }
                }
                if let message { Section { Text(message).font(.footnote).foregroundStyle(.secondary) } }
                Section {
                    ForEach(results) { member in
                        HStack {
                            MemberRow(member: member, subtitle: member.email)
                            if isFriend(member) || member.userId == session.myUserId {
                                Text(member.userId == session.myUserId ? "나" : "친구").font(.footnote).foregroundStyle(.secondary)
                            } else {
                                Button("추가") { Task { await add(member) } }.buttonStyle(.borderedProminent).controlSize(.small)
                            }
                        }
                    }
                }
            }
            .navigationTitle("친구 찾기")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("닫기") { dismiss() } } }
        }
    }

    private func isFriend(_ member: MemberDto) -> Bool { friends.friends.contains { $0.id == member.id } }

    private func search() async {
        searching = true
        defer { searching = false }
        message = nil
        do {
            results = try await friends.search(email: email.trimmingCharacters(in: .whitespaces))
            if results.isEmpty { message = "일치하는 회원이 없습니다." }
        } catch {
            results = []
            message = "찾지 못했습니다: \(error.localizedDescription)"
        }
    }

    private func add(_ member: MemberDto) async {
        guard let target = member.email else { return }
        do {
            try await friends.add(userId: session.myUserId, email: target)
            message = "\(member.displayName) 님을 친구로 추가했습니다."
        } catch {
            message = "추가 실패: \(error.localizedDescription)"
        }
    }
}
