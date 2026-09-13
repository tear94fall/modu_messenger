import SwiftUI

struct LoginView: View {
    @EnvironmentObject private var session: SessionStore

    var body: some View {
        VStack(spacing: 24) {
            Spacer()
            Image("AppLogo").resizable().scaledToFit().frame(width: 120, height: 120)
                .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
            Text("모두의 채팅").font(.largeTitle.bold())
            Text("친구와 가볍게 이야기하세요").foregroundStyle(.secondary)
            Spacer()

            if session.state == .loggingIn {
                ProgressView("로그인 중…")
            } else {
                Button {
                    Task { await session.signInWithGoogle() }
                } label: {
                    HStack(spacing: 10) {
                        Text("G").font(.title3.bold()).foregroundStyle(Color.brand)
                            .frame(width: 28, height: 28).background(Color.white, in: Circle())
                        Text("Google 계정으로 로그인").fontWeight(.semibold)
                    }
                    .frame(maxWidth: .infinity).padding(.vertical, 12)
                }
                .buttonStyle(.borderedProminent)
                .padding(.horizontal, 32)
            }

            if let message = session.errorMessage {
                Text(message).font(.footnote).foregroundStyle(.red)
                    .multilineTextAlignment(.center).padding(.horizontal, 32)
            }
            Spacer().frame(height: 40)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }
}
