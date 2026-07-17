import SwiftUI
import SwiftData

@main
struct KchatMacApp: App {
    @StateObject private var themeService = ThemeService()
    private var modelContainer: ModelContainer
    
    init() {
        self.modelContainer = KChatContainer.create()
    }
    
    var body: some Scene {
        WindowGroup {
            MainView()
                .modelContainer(modelContainer)
                .environmentObject(themeService)
                .preferredColorScheme(themeService.colorScheme)
        }
        .windowToolbarStyle(.unifiedCompact)
        .windowStyle(.titleBar)
    }
}

struct MainView: View {
    @Environment(\.modelContext) private var modelContext
    @EnvironmentObject private var themeService: ThemeService
    
    @StateObject private var sidebarViewModel: SidebarViewModel
    @State private var selectedConversation: Conversation?
    @State private var showSettings = false
    @State private var showNoteTodo = false
    
    init() {
        let modelContext = ModelContext(KChatContainer.create())
        _sidebarViewModel = StateObject(wrappedValue: SidebarViewModel(modelContext: modelContext))
    }
    
    var body: some View {
        HSplitView {
            SidebarView(
                viewModel: sidebarViewModel,
                selectedConversation: $selectedConversation,
                onCreateConversation: createNewConversation
            )
            
            if let conversation = selectedConversation {
                ChatView(
                    viewModel: ChatViewModel(modelContext: sidebarViewModel.modelContext),
                    selectedConversation: $selectedConversation
                )
            } else {
                emptyChatView
            }
        }
        .toolbar {
            MainToolbar(
                onCreateConversation: createNewConversation,
                onSettings: { showSettings.toggle() },
                onNoteTodo: { showNoteTodo.toggle() }
            )
        }
        .sheet(isPresented: $showSettings) {
            SettingsView(viewModel: SettingsViewModel(modelContext: sidebarViewModel.modelContext))
                .frame(width: 500, height: 600)
        }
        .sheet(isPresented: $showNoteTodo) {
            NoteTodoView(viewModel: NoteTodoViewModel(modelContext: sidebarViewModel.modelContext))
                .frame(width: 700, height: 600)
        }
        .onChange(of: themeService.currentTheme) {
            NSApp.windows.forEach { window in
                window.invalidateRestorableState()
            }
        }
    }
    
    private var emptyChatView: some View {
        VStack(spacing: 16) {
            Image(systemName: "message.circle")
                .font(.system(size: 64))
                .foregroundColor(.secondary)
            
            Text("选择或创建对话")
                .font(.largeTitle)
                .foregroundColor(.secondary)
            
            Text("在左侧选择一个对话，或创建新对话开始聊天")
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(40)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(.background)
    }
    
    private func createNewConversation() {
        Task {
            await sidebarViewModel.createNewConversation()
            if let newConversation = sidebarViewModel.conversations.first {
                selectedConversation = newConversation
            }
        }
    }
}

struct MainToolbar: ToolbarContent {
    var onCreateConversation: () -> Void
    var onSettings: () -> Void
    var onNoteTodo: () -> Void
    
    var body: some ToolbarContent {
        ToolbarItem(placement: .navigation) {
            Button(action: onCreateConversation) {
                Label("新建对话", systemImage: "plus")
            }
            .keyboardShortcut("N", modifiers: .command)
        }
        
        ToolbarItem(placement: .primaryAction) {
            Button(action: onNoteTodo) {
                Label("笔记/待办", systemImage: "notebook")
            }
        }
        
        ToolbarItem(placement: .primaryAction) {
            Button(action: onSettings) {
                Label("设置", systemImage: "gear")
            }
            .keyboardShortcut(",", modifiers: .command)
        }
        
        ToolbarItem(placement: .status) {
            ConnectionStatusView()
        }
    }
}

struct ConnectionStatusView: View {
    @State private var isConnected = false
    
    var body: some View {
        HStack(spacing: 8) {
            Circle()
                .frame(width: 8, height: 8)
                .foregroundColor(isConnected ? .green : .red)
            
            Text(isConnected ? "已连接" : "未连接")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .onAppear {
            checkConnection()
        }
    }
    
    private func checkConnection() {
        guard let url = URL(string: "http://localhost:8080/api/conversations") else { return }
        
        URLSession.shared.dataTask(with: url) { _, response, _ in
            if let httpResponse = response as? HTTPURLResponse, (200...299).contains(httpResponse.statusCode) {
                isConnected = true
            }
        }.resume()
    }
}