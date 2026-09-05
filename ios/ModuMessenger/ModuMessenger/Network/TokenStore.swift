import Foundation
import Security

/// 액세스/리프레시 토큰 보관. 값은 "Bearer xxx" 형태로 통째로 저장한다 (안드로이드 DataStore 와 같음).
protocol TokenStore: AnyObject {
    var accessToken: String? { get set }
    var refreshToken: String? { get set }
    func clear()
}

final class InMemoryTokenStore: TokenStore {
    var accessToken: String?
    var refreshToken: String?
    func clear() { accessToken = nil; refreshToken = nil }
}

final class KeychainTokenStore: TokenStore {
    private let service = "com.example.modumessenger.tokens"
    private let lock = NSLock()

    var accessToken: String? {
        get { read("access-token") }
        set { write("access-token", newValue) }
    }
    var refreshToken: String? {
        get { read("refresh-token") }
        set { write("refresh-token", newValue) }
    }

    func clear() { accessToken = nil; refreshToken = nil }

    private func query(_ account: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword,
         kSecAttrService as String: service,
         kSecAttrAccount as String: account]
    }

    private func read(_ account: String) -> String? {
        lock.lock(); defer { lock.unlock() }
        var q = query(account)
        q[kSecReturnData as String] = true
        q[kSecMatchLimit as String] = kSecMatchLimitOne
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func write(_ account: String, _ value: String?) {
        lock.lock(); defer { lock.unlock() }
        SecItemDelete(query(account) as CFDictionary)
        guard let value, let data = value.data(using: .utf8) else { return }
        var q = query(account)
        q[kSecValueData as String] = data
        q[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(q as CFDictionary, nil)
    }
}
