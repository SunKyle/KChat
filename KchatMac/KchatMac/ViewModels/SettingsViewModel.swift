import SwiftUI
import SwiftData
import Combine

class SettingsViewModel: ObservableObject {
    @Published var selectedTheme: ThemeType = .system
    @Published var apiKeys: [APIKey] = []
    @Published var modelConfigs: [ModelConfig] = []
    @Published var selectedModelId: String?
    @Published var isLoading: Bool = false
    @Published var error: String?
    
    private let themeService = ThemeService()
    private let apiKeyService = APIKeyService.shared
    private var modelContext: ModelContext
    private var cancellables = Set<AnyCancellable>()
    
    init(modelContext: ModelContext) {
        self.modelContext = modelContext
        self.selectedTheme = themeService.currentTheme
        
        themeService.$currentTheme
            .assign(to: \.selectedTheme, on: self)
            .store(in: &cancellables)
        
        loadModelConfigs()
        loadAPIKeys()
    }
    
    func setTheme(_ theme: ThemeType) {
        themeService.setTheme(theme)
    }
    
    func loadModelConfigs() {
        Task {
            do {
                let descriptor = FetchDescriptor<ModelConfig>(
                    sortBy: [SortDescriptor(\.createdAt)]
                )
                modelConfigs = try modelContext.fetch(descriptor)
                
                if modelConfigs.isEmpty {
                    let defaultConfig = ModelConfig(
                        id: UUID().uuidString,
                        name: "默认模型",
                        modelId: "gpt-4o",
                        baseUrl: "https://api.openai.com",
                        apiKey: "",
                        type: .openAI,
                        category: .text
                    )
                    modelContext.insert(defaultConfig)
                    try modelContext.save()
                    modelConfigs = [defaultConfig]
                }
                
                if selectedModelId == nil {
                    selectedModelId = modelConfigs.first?.id
                }
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
    
    func addModelConfig(_ config: ModelConfig) {
        modelContext.insert(config)
        do {
            try modelContext.save()
            modelConfigs.append(config)
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func updateModelConfig(_ config: ModelConfig) {
        do {
            try modelContext.save()
            if let index = modelConfigs.firstIndex(where: { $0.id == config.id }) {
                modelConfigs[index] = config
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func deleteModelConfig(_ config: ModelConfig) {
        modelContext.delete(config)
        do {
            try modelContext.save()
            if let index = modelConfigs.firstIndex(where: { $0.id == config.id }) {
                modelConfigs.remove(at: index)
            }
            if selectedModelId == config.id {
                selectedModelId = modelConfigs.first?.id
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func loadAPIKeys() {
        apiKeys = apiKeyService.getKeys()
    }
    
    func addAPIKey(_ key: APIKey) {
        apiKeys.append(key)
        saveAPIKeys()
    }
    
    func updateAPIKey(_ key: APIKey) {
        if let index = apiKeys.firstIndex(where: { $0.id == key.id }) {
            apiKeys[index] = key
            saveAPIKeys()
        }
    }
    
    func deleteAPIKey(_ key: APIKey) {
        if let index = apiKeys.firstIndex(where: { $0.id == key.id }) {
            apiKeys.remove(at: index)
            saveAPIKeys()
        }
    }
    
    private func saveAPIKeys() {
        do {
            try apiKeyService.saveKeys(apiKeys)
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func clearError() {
        error = nil
    }
}

struct APIKey: Identifiable, Codable {
    let id: String
    var name: String
    var key: String
    
    init(id: String = UUID().uuidString, name: String, key: String) {
        self.id = id
        self.name = name
        self.key = key
    }
}