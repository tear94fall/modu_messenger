import Foundation

struct ProfileDto: Codable, Equatable, Hashable {
    var id: Int64?
    var memberId: Int64?
    var profileType: String?
    var value: String?
    var createdDate: String?
    var updatedDate: String?
}

/// member-service 의 회원 응답. 서버가 null 을 자주 내려 필드 전부 옵셔널이다.
struct MemberDto: Codable, Equatable, Hashable, Identifiable {
    var id: Int64?
    var userId: String?
    var email: String?
    var auth: String?
    var role: String?
    var username: String?
    var statusMessage: String?
    var profileImage: String?
    var wallpaperImage: String?
    var profiles: [ProfileDto]?

    init(id: Int64? = nil, userId: String? = nil, email: String? = nil, auth: String? = nil, role: String? = nil,
         username: String? = nil, statusMessage: String? = nil, profileImage: String? = nil,
         wallpaperImage: String? = nil, profiles: [ProfileDto]? = nil) {
        self.id = id; self.userId = userId; self.email = email; self.auth = auth; self.role = role
        self.username = username; self.statusMessage = statusMessage; self.profileImage = profileImage
        self.wallpaperImage = wallpaperImage; self.profiles = profiles
    }

    var displayName: String { (username?.isEmpty == false ? username : email) ?? userId ?? "" }
}

struct SignUpDto: Codable {
    var userId: String?
    var email: String?
    var auth: String?
    var username: String?
    var statusMessage: String?
    var profileImage: String?
    var wallpaperImage: String?
}

struct GoogleLoginRequest: Codable {
    var authType: String
    var idToken: String
}

struct RequestLoginDto: Codable {
    var userId: String
    var email: String
}

struct TokenResponseDto: Codable {
    var accessToken: String?
    var refreshToken: String?
}

struct AddFriendDto: Codable {
    var email: String
}

struct UpdateProfileDto: Codable {
    var username: String?
    var statusMessage: String?
    var profileImage: String?
    var wallpaperImage: String?
}
