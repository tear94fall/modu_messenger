import SwiftUI
import GoogleSignIn

@main
struct ModuMessengerApp: App {
    @StateObject private var env = AppEnvironment()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(env)
                .environmentObject(env.session)
                .environmentObject(env.chat)
                .environmentObject(env.friends)
                .tint(.brand)
                .task { await env.session.restore() }
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
                .onChange(of: scenePhase) { _, phase in
                    guard phase == .active, env.session.state == .loggedIn else { return }
                    env.chat.connect()
                    Task { await env.chat.refreshRooms() }
                }
        }
    }
}
