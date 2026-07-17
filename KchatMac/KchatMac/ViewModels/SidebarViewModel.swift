import SwiftUI
import SwiftData

class SidebarViewModel: ObservableObject {
    @Published var conversations: [Conversation] = []
    @Published var searchText: String = ""
    @Published var selectedConversationId: String?
    @Published var isLoading: Bool = false
    @Published var error: String?
    
    private let chatService = ChatService()
    let modelContext: ModelContext
    
    init(modelContext: ModelContext) {
        self.modelContext = modelContext
        loadConversations()
    }
    
    func loadConversations() {
        isLoading = true
        Task {
            do {
                let dtos = try await chatService.getConversations()
                
                for dto in dtos {
                    let existing = conversations.first { $0.id == dto.id }
                    if existing == nil {
                        let conversation = Conversation(id: dto.id, title: dto.title)
                        conversation.pinned = dto.pinned ?? false
                        conversation.isSummaryNote = dto.isSummaryNote ?? false
                        if let createdAt = parseDate(dto.createdAt) {
                            conversation.createdAt = createdAt
                        }
                        if let updatedAtStr = dto.updatedAt, let updatedAt = parseDate(updatedAtStr) {
                            conversation.updatedAt = updatedAt
                        }
                        modelContext.insert(conversation)
                    }
                }
                
                try modelContext.save()
                
                let descriptor = FetchDescriptor<Conversation>(
                    sortBy: [SortDescriptor(\.updatedAt, order: .reverse)]
                )
                conversations = try modelContext.fetch(descriptor)
                
                if selectedConversationId == nil, !conversations.isEmpty {
                    selectedConversationId = conversations.first?.id
                }
            } catch {
                self.error = error.localizedDescription
            }
            isLoading = false
        }
    }
    
    private func parseDate(_ dateString: String) -> Date? {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter.date(from: dateString)
    }
    
    func selectConversation(_ conversation: Conversation) {
        selectedConversationId = conversation.id
    }
    
    func createNewConversation() async {
        do {
            let dto = try await chatService.createConversation()
            let conversation = Conversation(id: dto.id, title: dto.title)
            modelContext.insert(conversation)
            try modelContext.save()
            
            let descriptor = FetchDescriptor<Conversation>(
                sortBy: [SortDescriptor(\.updatedAt, order: .reverse)]
            )
            conversations = try modelContext.fetch(descriptor)
            
            selectedConversationId = conversation.id
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func deleteConversation(_ conversation: Conversation) async {
        do {
            try await chatService.deleteConversation(id: conversation.id)
            modelContext.delete(conversation)
            try modelContext.save()
            
            let descriptor = FetchDescriptor<Conversation>(
                sortBy: [SortDescriptor(\.updatedAt, order: .reverse)]
            )
            conversations = try modelContext.fetch(descriptor)
            
            if selectedConversationId == conversation.id {
                selectedConversationId = conversations.first?.id
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    var filteredConversations: [Conversation] {
        if searchText.isEmpty {
            return conversations
        }
        let searchLower = searchText.lowercased()
        return conversations.filter {
            $0.title.lowercased().contains(searchLower)
        }
    }
    
    func clearError() {
        error = nil
    }
}