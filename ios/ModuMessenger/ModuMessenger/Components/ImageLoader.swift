import Foundation
import UIKit

/// storage-service 이미지 조회. Authorization 헤더가 필요해 AsyncImage 를 못 쓴다. 메모리 캐시를 둔다.
final class ImageLoader {
    private let config: AppConfig
    private let tokenStore: TokenStore
    private let cache = NSCache<NSString, UIImage>()
    private let session = URLSession(configuration: .default)

    init(config: AppConfig, tokenStore: TokenStore) {
        self.config = config
        self.tokenStore = tokenStore
        cache.countLimit = 300
    }

    func cached(_ fileName: String) -> UIImage? { cache.object(forKey: fileName as NSString) }

    func image(for fileName: String?) async -> UIImage? {
        guard let fileName, !fileName.isEmpty else { return nil }
        if let hit = cached(fileName) { return hit }
        guard let url = config.imageURL(fileName: fileName) else { return nil }
        var request = URLRequest(url: url)
        if let token = tokenStore.accessToken { request.setValue(token, forHTTPHeaderField: "Authorization") }
        guard let (data, response) = try? await session.data(for: request),
              (response as? HTTPURLResponse).map({ (200..<300).contains($0.statusCode) }) ?? false,
              let image = UIImage(data: data) else { return nil }
        cache.setObject(image, forKey: fileName as NSString)
        return image
    }
}

/// 사진 보내기 전 크기를 줄인다. 긴 변 1280px, JPEG 80%.
enum ImageDownscaler {
    static func jpegData(from data: Data, maxDimension: CGFloat = 1280) -> Data? {
        guard let image = UIImage(data: data) else { return nil }
        let longest = max(image.size.width, image.size.height)
        let scale = longest > maxDimension ? maxDimension / longest : 1
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        let renderer = UIGraphicsImageRenderer(size: size, format: { let f = UIGraphicsImageRendererFormat(); f.scale = 1; return f }())
        let resized = renderer.image { _ in image.draw(in: CGRect(origin: .zero, size: size)) }
        return resized.jpegData(compressionQuality: 0.8)
    }
}
