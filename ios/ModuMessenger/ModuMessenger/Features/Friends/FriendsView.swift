import SwiftUI

struct FriendsView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var friends: FriendsStore
    @EnvironmentObject private var chat: ChatRepository
    @State private var showFind = false
    @State private var selected: MemberDto?
    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
            List {
                if let me = session.member {
                    Section("내 프로필") {
                        NavigationLink { ProfileEditView() } label: { MemberRow(member: me, subtitle: me.statusMessage) }
                    }
                }
                Section("친구 \(friends.friends.count)") {
                    if friends.friends.isEmpty && !friends.isLoading {
                        Text("아직 친구가 없어요. 오른쪽 위 + 로 이메일을 검색해 추가하세요.")
                            .font(.footnote).foregroundStyle(.secondary)
                    }
                    ForEach(friends.friends) { friend in
                        Button { selected = friend } label: { MemberRow(member: friend, subtitle: friend.statusMessage) }
                            .foregroundStyle(.primary)
                    }
                }
            }
            .listStyle(.insetGrouped)
            .navigationTitle("친구")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showFind = true } label: { Image(systemName: "person.badge.plus") }
                }
            }
            .refreshable { await friends.load(userId: session.myUserId) }
            .task { await friends.load(userId: session.myUserId) }
            .sheet(isPresented: $showFind) { FindFriendView() }
            .sheet(item: $selected) { friend in
                FriendProfileSheet(friend: friend) { roomId in
                    selected = nil
                    path.append(roomId)
                }
            }
            .navigationDestination(for: String.self) { roomId in ChatRoomView(roomId: roomId) }
        }
    }
}

struct MemberRow: View {
    let member: MemberDto
    var subtitle: String?

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(fileName: member.profileImage)
            VStack(alignment: .leading, spacing: 2) {
                Text(member.displayName).font(.body.weight(.medium)).lineLimit(1)
                if let subtitle, !subtitle.isEmpty {
                    Text(subtitle).font(.footnote).foregroundStyle(.secondary).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 2)
    }
}

/// 친구를 눌렀을 때. 안드로이드 ProfileActivity 의 축약판.
struct FriendProfileSheet: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var chat: ChatRepository
    @Environment(\.dismiss) private var dismiss
    let friend: MemberDto
    let onOpenRoom: (String) -> Void
    @State private var error: String?
    @State private var working = false

    var body: some View {
        VStack(spacing: 16) {
            Capsule().fill(.tertiary).frame(width: 36, height: 5).padding(.top, 8)
            Spacer()
            AvatarView(fileName: friend.profileImage, size: 110)
            Text(friend.displayName).font(.title2.bold())
            if let status = friend.statusMessage, !status.isEmpty { Text(status).foregroundStyle(.secondary) }
            Text(friend.email ?? "").font(.footnote).foregroundStyle(.tertiary)
            Spacer()
            Button {
                Task { await openRoom() }
            } label: {
                Label("1:1 채팅", systemImage: "bubble.left.fill").frame(maxWidth: .infinity).padding(.vertical, 8)
            }
            .buttonStyle(.borderedProminent).disabled(working)
            if let error { Text(error).font(.footnote).foregroundStyle(.red) }
        }
        .padding(24)
        .presentationDetents([.medium])
    }

    private func openRoom() async {
        guard let me = session.member?.id, let other = friend.id else { return }
        working = true
        defer { working = false }
        do {
            let room = try await chat.createRoom(memberIds: [me, other])
            if let roomId = room.roomId { onOpenRoom(roomId) } else { dismiss() }
        } catch {
            self.error = error.localizedDescription
        }
    }
}
