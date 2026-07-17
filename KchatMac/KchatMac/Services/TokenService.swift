import Foundation
import Security

class TokenService {
    static let shared = TokenService()
    
    private let service = "com.kchat.token"
    private let account = "auth_token"
    
    private init() {}
    
    func saveToken(_ token: String) throws {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
            kSecValueData: token.data(using: .utf8)!
        ]
        
        SecItemDelete(query as CFDictionary)
        
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw TokenError.saveFailed(status: status)
        }
    }
    
    func getToken() -> String? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]
        
        var data: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &data)
        
        guard status == errSecSuccess, let tokenData = data as? Data else {
            return nil
        }
        
        return String(data: tokenData, encoding: .utf8)
    }
    
    func deleteToken() throws {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw TokenError.deleteFailed(status: status)
        }
    }
}

enum TokenError: Error, LocalizedError {
    case saveFailed(status: OSStatus)
    case deleteFailed(status: OSStatus)
    
    var errorDescription: String? {
        switch self {
        case .saveFailed(let status): return "保存令牌失败: \(status)"
        case .deleteFailed(let status): return "删除令牌失败: \(status)"
        }
    }
}