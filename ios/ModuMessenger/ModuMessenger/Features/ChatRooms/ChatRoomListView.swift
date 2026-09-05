import SwiftUI

struct ChatRoomListView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var chat: ChatRepository
    @State private var showCreate = false
    @State private var path: [String] = []
    @State private var exitTarget: ChatRoomDto?

    var body: some View {
        NavigationStack(path: $path) {
            List {
                if chat.connectionState == .reconnecting || chat.connectionState == .connecting {
                    Label(chat.connectionState == .connecting ? "연결 중…" : "다시 연결하는 중…", systemImage: "wifi.exclamationmark")
                        .font(.footnote).foregroundStyle(.secondary)
                }
                if chat.rooms.isEmpty {
                    Text("아직 대화가 없어요. 오른쪽 위 + 로 채팅방을 만드세요.").font(.footnote).foregroundStyle(.secondary)
                }
                ForEach(chat.rooms) { room in
                    NavigationLink(value: room.roomId ?? "") {
                        ChatRoomRow(room: room, myUserId: session.myUserId, unread: chat.unreadCount(roomId: room.roomId ?? ""))
                    }
                    .swipeActions {
                        Button(role: .destructive) { exitTarget = room } label: { Label("나가기", systemImage: "rectangle.portrait.and.arrow.right") }
                    }
                }
            }
            .listStyle(.plain)
            .navigationTitle("채팅")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button { showCreate = true } label: { Image(systemName: "plus.bubble") }
                }
            }
            .refreshable { await chat.refreshRooms() }
            .task { await chat.refreshRooms() }
            .sheet(isPresented: $showCreate) {
                CreateRoomView { roomId in
                    showCreate = false
                    path.append(roomId)
                }
            }
            .navigationDestination(for: String.self) { roomId in ChatRoomView(roomId: roomId) }
            .confirmationDialog("이 채팅방에서 나갈까요?", isPresented: Binding(get: { exitTarget != nil }, set: { if !$0 { exitTarget = nil } }), titleVisibility: .visible) {
                Button("나가기", role: .destructive) {
                    if let roomId = exitTarget?.roomId { Task { try? await chat.exitRoom(roomId: roomId) } }
                    exitTarget = nil
                }
                Button("취소", role: .cancel) { exitTarget = nil }
            }
        }
    }
}

struct ChatRoomRow: View {
    let room: ChatRoomDto
    let myUserId: String
    let unread: Int64

    private var image: String? {
        if let img = room.roomImage, !img.isEmpty { return img }
        return room.members?.first { $0.userId != myUserId }?.profileImage
    }

    var body: some View {
        HStack(spacing: 12) {
            AvatarView(fileName: image, size: 52)
            VStack(alignment: .leading, spacing: 3) {
                HStack {
                    Text(room.displayName(myUserId: myUserId)).font(.body.weight(.semibold)).lineLimit(1)
                    if let count = room.members?.count, count > 2 {
                        Text("\(count)").font(.footnote).foregroundStyle(.secondary)
                    }
                    Spacer()
                    Text(ChatTime.listLabel(room.lastChatTime)).font(.caption).foregroundStyle(.secondary)
                }
                HStack(alignment: .top) {
                    Text(room.lastChatMsg ?? "").font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                    Spacer()
                    if unread > 0 {
                        Text(unread > 99 ? "99+" : "\(unread)")
                            .font(.caption2.bold()).foregroundStyle(.white)
                            .padding(.horizontal, 7).padding(.vertical, 3)
                            .background(Color.brand, in: Capsule())
                    }
                }
            }
        }
        .padding(.vertical, 4)
    }
}
