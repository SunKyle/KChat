import SwiftData
import Foundation

enum ProviderType: String, Codable {
    case openAI = "OPENAI"
    case openAICompatible = "OPENAI_COMPATIBLE"
    case anthropic = "ANTHROPIC"
    case google = "GOOGLE"
    case ollama = "OLLAMA"
    case azure = "AZURE"
    case custom = "CUSTOM"
    
    var displayName: String {
        switch self {
        case .openAI: return "OpenAI"
        case .openAICompatible: return "OpenAI 兼容"
        case .anthropic: return "Anthropic"
        case .google: return "Google"
        case .ollama: return "Ollama"
        case .azure: return "Azure OpenAI"
        case .custom: return "自定义"
        }
    }
    
    var icon: String {
        switch self {
        case .openAI: return "brain"
        case .openAICompatible: return "cpu"
        case .anthropic: return "sparkles"
        case .google: return "globe"
        case .ollama: return "lama"
        case .azure: return "cloud"
        case .custom: return "wrench"
        }
    }
}

enum ModelCategory: String, Codable {
    case text = "TEXT"
    case image = "IMAGE"
    case video = "VIDEO"
    
    var displayName: String {
        switch self {
        case .text: return "文本"
        case .image: return "图像"
        case .video: return "视频"
        }
    }
}

@Model
final class ModelConfig {
    @Attribute(.unique) var id: String
    var name: String
    var modelId: String
    var baseUrl: String
    var apiKey: String
    var type: String
    var category: String
    var enabled: Bool
    var createdAt: Date
    
    init(id: String, name: String, modelId: String, baseUrl: String,
         apiKey: String, type: ProviderType, category: ModelCategory) {
        self.id = id
        self.name = name
        self.modelId = modelId
        self.baseUrl = baseUrl
        self.apiKey = apiKey
        self.type = type.rawValue
        self.category = category.rawValue
        self.enabled = true
        self.createdAt = Date()
    }
    
    var providerType: ProviderType {
        ProviderType(rawValue: type) ?? .custom
    }
    
    var modelCategory: ModelCategory {
        ModelCategory(rawValue: category) ?? .text
    }
}