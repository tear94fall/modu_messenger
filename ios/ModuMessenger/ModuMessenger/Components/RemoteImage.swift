import SwiftUI

/// 서버 파일 이름으로 이미지를 그린다. 없거나 실패하면 placeholder.
struct RemoteImage<Placeholder: View>: View {
    @EnvironmentObject private var env: AppEnvironment
    let fileName: String?
    let contentMode: ContentMode
    @ViewBuilder let placeholder: () -> Placeholder
    @State private var image: UIImage?

    init(fileName: String?, contentMode: ContentMode = .fill, @ViewBuilder placeholder: @escaping () -> Placeholder) {
        self.fileName = fileName
        self.contentMode = contentMode
        self.placeholder = placeholder
    }

    var body: some View {
        Group {
            if let image {
                Image(uiImage: image).resizable().aspectRatio(contentMode: contentMode)
            } else {
                placeholder()
            }
        }
        .task(id: fileName) {
            image = env.images.cached(fileName ?? "")
            if image == nil { image = await env.images.image(for: fileName) }
        }
    }
}

/// 둥근 프로필 사진. 안드로이드 basic_profile_image 대신 SF Symbol 을 쓴다.
struct AvatarView: View {
    let fileName: String?
    var size: CGFloat = 44

    var body: some View {
        RemoteImage(fileName: fileName) {
            ZStack {
                Color.brand.opacity(0.15)
                Image(systemName: "person.fill")
                    .resizable().scaledToFit()
                    .foregroundStyle(Color.brand)
                    .padding(size * 0.22)
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: size * 0.36, style: .continuous))
    }
}
