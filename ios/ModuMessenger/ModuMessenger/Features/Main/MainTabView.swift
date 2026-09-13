import SwiftUI

struct MainTabView: View {
    @EnvironmentObject private var chat: ChatRepository

    private var totalUnread: Int {
        chat.rooms.reduce(0) { $0 + Int(chat.unreadCount(roomId: $1.roomId ?? "")) }
    }

    var body: some View {
        TabView {
            FriendsView()
                .tabItem { Label("친구", systemImage: "person.2.fill") }
            ChatRoomListView()
                .tabItem { Label("채팅", systemImage: "bubble.left.and.bubble.right.fill") }
                .badge(totalUnread)
            SettingsView()
                .tabItem { Label("설정", systemImage: "gearshape.fill") }
        }
    }
}
