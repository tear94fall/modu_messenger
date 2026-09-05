import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var session: SessionStore
    @EnvironmentObject private var friends: FriendsStore
    @State private var confirmLogout = false

    var body: some View {
        NavigationStack {
            List {
                if let me = session.member {
                    Section {
                        NavigationLink { ProfileEditView() } label: {
                            HStack(spacing: 14) {
                                AvatarView(fileName: me.profileImage, size: 64)
                                VStack(alignment: .leading, spacing: 4) {
                                    Text(me.displayName).font(.title3.weight(.semibold))
                                    Text(me.statusMessage?.isEmpty == false ? me.statusMessage! : "상태 메시지를 설정해 보세요")
                                        .font(.footnote).foregroundStyle(.secondary)
                                    Text(me.email ?? "").font(.caption).foregroundStyle(.tertiary)
                                }
                            }
                            .padding(.vertical, 6)
                        }
                    }
                }
                Section("앱") {
                    NavigationLink("앱 정보") { AppInfoView() }
                }
                Section {
                    Button("로그아웃", role: .destructive) { confirmLogout = true }
                }
            }
            .navigationTitle("설정")
            .confirmationDialog("로그아웃 할까요?", isPresented: $confirmLogout, titleVisibility: .visible) {
                Button("로그아웃", role: .destructive) {
                    Task { friends.clear(); await session.logout() }
                }
            }
        }
    }
}

struct AppInfoView: View {
    @EnvironmentObject private var env: AppEnvironment

    private var version: String {
        let short = Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "-"
        let build = Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String ?? "-"
        return "\(short) (\(build))"
    }

    var body: some View {
        List {
            Section("모두의 채팅") {
                LabeledContent("버전", value: version)
                LabeledContent("플랫폼", value: "iPhone")
            }
            Section("서버") {
                LabeledContent("API", value: env.config.apiBaseURL.absoluteString)
                LabeledContent("소켓", value: env.config.socketURL.absoluteString)
                LabeledContent("Google 로그인", value: env.config.isGoogleConfigured ? "설정됨" : "미설정")
            }
        }
        .navigationTitle("앱 정보")
        .navigationBarTitleDisplayMode(.inline)
    }
}
