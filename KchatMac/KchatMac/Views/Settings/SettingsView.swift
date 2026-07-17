import SwiftUI
import SwiftData

struct SettingsView: View {
    @StateObject var viewModel: SettingsViewModel
    @Environment(\.dismiss) var dismiss
    
    var body: some View {
        NavigationStack {
            List {
                Section("外观") {
                    themePicker
                }
                
                Section("模型配置") {
                    ForEach(viewModel.modelConfigs) { config in
                        modelConfigRow(config)
                    }
                    
                    Button(action: addModelConfig) {
                        HStack(spacing: 8) {
                            Image(systemName: "plus")
                                .foregroundColor(.blue)
                            
                            Text("添加模型")
                        }
                    }
                }
                
                Section("API 密钥") {
                    ForEach(viewModel.apiKeys) { key in
                        apiKeyRow(key)
                    }
                    
                    Button(action: addAPIKey) {
                        HStack(spacing: 8) {
                            Image(systemName: "plus")
                                .foregroundColor(.blue)
                            
                            Text("添加密钥")
                        }
                    }
                }
                
                Section("关于") {
                    HStack(spacing: 12) {
                        Image(systemName: "message.circle")
                            .font(.largeTitle)
                            .foregroundColor(.blue)
                        
                        VStack(alignment: .leading) {
                            Text("KChat")
                                .font(.headline)
                            
                            Text("版本 1.0.0")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("设置")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") {
                        dismiss()
                    }
                }
            }
        }
    }
    
    private var themePicker: some View {
        Picker("主题", selection: $viewModel.selectedTheme) {
            ForEach(ThemeType.allCases, id: \.self) { theme in
                Text(theme.displayName)
            }
        }
        .onChange(of: viewModel.selectedTheme) { theme in
            viewModel.setTheme(theme)
        }
    }
    
    private func modelConfigRow(_ config: ModelConfig) -> some View {
        HStack(spacing: 12) {
            Image(systemName: config.providerType.icon)
                .frame(width: 32, height: 32)
                .foregroundColor(.purple)
                .background(.gray.opacity(0.2))
                .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 2) {
                Text(config.name)
                    .font(.body)
                
                Text(config.modelId)
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Button(action: { viewModel.deleteModelConfig(config) }) {
                Image(systemName: "trash")
                    .foregroundColor(.red)
            }
        }
    }
    
    private func apiKeyRow(_ key: APIKey) -> some View {
        HStack(spacing: 12) {
            Image(systemName: "key")
                .frame(width: 32, height: 32)
                .foregroundColor(.orange)
                .background(.gray.opacity(0.2))
                .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 2) {
                Text(key.name)
                    .font(.body)
                
                Text(maskKey(key.key))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Button(action: { viewModel.deleteAPIKey(key) }) {
                Image(systemName: "trash")
                    .foregroundColor(.red)
            }
        }
    }
    
    private func maskKey(_ key: String) -> String {
        if key.count <= 8 {
            return key
        }
        let prefix = key.prefix(4)
        let suffix = key.suffix(4)
        return "\(prefix)••••••••\(suffix)"
    }
    
    private func addModelConfig() {
        let newConfig = ModelConfig(
            id: UUID().uuidString,
            name: "新模型",
            modelId: "model-id",
            baseUrl: "https://api.example.com",
            apiKey: "",
            type: .custom,
            category: .text
        )
        viewModel.addModelConfig(newConfig)
    }
    
    private func addAPIKey() {
        viewModel.addAPIKey(APIKey(name: "新密钥", key: ""))
    }
}