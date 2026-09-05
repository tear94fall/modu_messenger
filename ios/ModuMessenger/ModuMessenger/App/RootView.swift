import SwiftUI

struct RootView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        switch session.state {
        case .restoring:
            VStack(spacing: 16) {
                Image("AppLogo").resizable().scaledToFit().frame(width: 96, height: 96)
                ProgressView()
            }
        case .loggedOut, .loggingIn:
            LoginView()
        case .loggedIn:
            MainTabView()
        }
    }
}
