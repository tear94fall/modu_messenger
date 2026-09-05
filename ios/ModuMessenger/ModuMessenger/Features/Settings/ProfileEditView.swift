import SwiftUI
import PhotosUI

/// 이름·상태 메시지·프로필 사진 수정. 안드로이드 ProfileEditActivity.
struct ProfileEditView: View {
    @EnvironmentObject private var session: SessionStore
    @Environment(\.dismiss) private var dismiss
    @State private var username = ""
    @State private var statusMessage = ""
    @State private var profileImage: String?
    @State private var photo: PhotosPickerItem?
    @State private var saving = false
    @State private var error: String?

    var body: some View {
        Form {
            Section {
                HStack {
                    Spacer()
                    PhotosPicker(selection: $photo, matching: .images) {
                        ZStack(alignment: .bottomTrailing) {
                            AvatarView(fileName: profileImage, size: 110)
                            Image(systemName: "camera.circle.fill").font(.title).foregroundStyle(Color.brand)
                                .background(Color(.systemBackground), in: Circle())
                        }
                    }
                    Spacer()
                }
                .listRowBackground(Color.clear)
            }
            Section("이름") { TextField("이름", text: $username) }
            Section("상태 메시지") { TextField("상태 메시지", text: $statusMessage, axis: .vertical).lineLimit(1...3) }
            if let error { Section { Text(error).font(.footnote).foregroundStyle(.red) } }
        }
        .navigationTitle("프로필 수정")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(saving ? "저장 중…" : "저장") { Task { await save() } }
                    .disabled(saving || username.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .onAppear {
            username = session.member?.username ?? ""
            statusMessage = session.member?.statusMessage ?? ""
            profileImage = session.member?.profileImage
        }
        .onChange(of: photo) { _, item in
            guard let item else { return }
            Task { await upload(item) }
        }
    }

    private func upload(_ item: PhotosPickerItem) async {
        defer { photo = nil }
        guard let raw = try? await item.loadTransferable(type: Data.self),
              let jpeg = ImageDownscaler.jpegData(from: raw, maxDimension: 800) else { return }
        do { profileImage = try await session.uploadProfileImage(data: jpeg) }
        catch { self.error = "사진 업로드 실패: \(error.localizedDescription)" }
    }

    private func save() async {
        saving = true
        defer { saving = false }
        do {
            try await session.updateProfile(username: username.trimmingCharacters(in: .whitespaces),
                                            statusMessage: statusMessage, profileImage: profileImage)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
    }
}
