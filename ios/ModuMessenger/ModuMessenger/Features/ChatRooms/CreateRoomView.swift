import SwiftUI

/// 친구를 골라 채팅방을 만든다. 안드로이드 CreateRoomActivity.
struct CreateRoomView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var friends: FriendsStore
    @EnvironmentObject private var chat: ChatRepository
    @Environment(\.dismiss) private var dismiss
    let onCreated: (String) -> Void
    @State private var picked = Set<Int64>()
    @State private var error: String?
    @State private var working = false

    var body: some View {
        NavigationStack {
            List(friends.friends) { friend in
                let id = friend.id ?? -1
                Button {
                    if picked.contains(id) { picked.remove(id) } else { picked.insert(id) }
                } label: {
                    HStack {
                        MemberRow(member: friend, subtitle: friend.statusMessage)
                        Image(systemName: picked.contains(id) ? "checkmark.circle.fill" : "circle")
                            .foregroundStyle(picked.contains(id) ? Color.brand : Color.secondary)
                    }
                }
                .foregroundStyle(.primary)
            }
            .overlay {
                if friends.friends.isEmpty { ContentUnavailableView("친구가 없어요", systemImage: "person.2", description: Text("먼저 친구를 추가하세요.")) }
            }
            .navigationTitle("채팅방 만들기")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("취소") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(working ? "만드는 중…" : "만들기 (\(picked.count))") { Task { await create() } }
                        .disabled(picked.isEmpty || working)
                }
            }
            .task { await friends.load(userId: session.myUserId) }
            .alert("채팅방을 만들지 못했습니다", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
                Button("확인") {}
            } message: { Text(error ?? "") }
        }
    }

    private func create() async {
        guard let me = session.member?.id else { return }
        working = true
        defer { working = false }
        do {
            let room = try await chat.createRoom(memberIds: [me] + picked.sorted())
            if let roomId = room.roomId { onCreated(roomId) } else { dismiss() }
        } catch {
            self.error = error.localizedDescription
        }
    }
}
