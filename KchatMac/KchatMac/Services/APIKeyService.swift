import Foundation
import Security

class APIKeyService {
    static let shared = APIKeyService()
    
    private let service = "com.kchat.api_keys"
    
    private init() {}
    
    func saveKeys(_ keys: [APIKey]) throws {
        let encoder = JSONEncoder()
        let data = try encoder.encode(keys)
        
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: "api_keys",
            kSecValueData: data
        ]
        
        SecItemDelete(query as CFDictionary)
        
        let status = SecItemAdd(query as CFDictionary, nil)
        guard status == errSecSuccess else {
            throw KeychainError.saveFailed(status: status)
        }
    }
    
    func getKeys() -> [APIKey] {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: "api_keys",
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]
        
        var data: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &data)
        
        guard status == errSecSuccess, let keyData = data as? Data else {
            return []
        }
        
        let decoder = JSONDecoder()
        do {
            return try decoder.decode([APIKey].self, from: keyData)
        } catch {
            return []
        }
    }
    
    func deleteKeys() throws {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: "api_keys"
        ]
        
        let status = SecItemDelete(query as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else {
            throw KeychainError.deleteFailed(status: status)
        }
    }
}

enum KeychainError: Error, LocalizedError {
    case saveFailed(status: OSStatus)
    case deleteFailed(status: OSStatus)
    
    var errorDescription: String? {
        switch self {
        case .saveFailed(let status): return "保存密钥失败: \(status)"
        case .deleteFailed(let status): return "删除密钥失败: \(status)"
        }
    }
}