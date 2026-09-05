import Foundation
import SwiftUI

/// 앱 전역 객체 조립. 안드로이드 App(Application) 클래스에 해당한다.
@MainActor
final class AppEnvironment: ObservableObject {
    let config: AppConfig
    let tokenStore: TokenStore
    let apiClient: APIClient
    let services: BackendServices
    let socket: ChatSocketManager
    let chat: ChatRepository
    let session: SessionStore
    let friends: FriendsStore
    let images: ImageLoader

    init() {
        let config = AppConfig.fromBundle()
        let tokenStore = KeychainTokenStore()
        let apiClient = APIClient(baseURL: config.apiBaseURL, tokenStore: tokenStore)
        let services = BackendServices(client: apiClient)
        let identity = IdentityHolder()
        let socket = ChatSocketManager(url: config.socketURL, credentials: .init(
            userId: { identity.userId },
            accessToken: { tokenStore.accessToken }))
        let chat = ChatRepository(api: services, socket: socket, identity: identity)

        self.config = config
        self.tokenStore = tokenStore
        self.apiClient = apiClient
        self.services = services
        self.socket = socket
        self.chat = chat
        self.session = SessionStore(config: config, api: services, tokenStore: tokenStore, chat: chat,
                                    google: GoogleSignInProvider(config: config))
        self.friends = FriendsStore(api: services)
        self.images = ImageLoader(config: config, tokenStore: tokenStore)
    }
}
