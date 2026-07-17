import SwiftUI
import SwiftData
import Combine

class ChatViewModel: ObservableObject {
    @Published var messages: [Message] = []
    @Published var isStreaming: Bool = false
    @Published var currentContent: String = ""
    @Published var error: String?
    @Published var selectedConversation: Conversation?
    
    private let chatService = ChatService()
    private var cancellables = Set<AnyCancellable>()
    private var modelContext: ModelContext
    
    init(modelContext: ModelContext) {
        self.modelContext = modelContext
    }
    
    func loadConversation(_ conversation: Conversation) async {
        selectedConversation = conversation
        do {
            let (_, messagesDTO) = try await chatService.getConversation(id: conversation.id)
            self.messages = messagesDTO.map { dto in
                Message(id: dto.id, content: dto.content, role: MessageRole(rawValue: dto.role) ?? .user)
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func sendMessage(_ text: String, model: String? = nil) async {
        guard !text.isEmpty, let conversation = selectedConversation else { return }
        
        let userMessage = Message(id: UUID().uuidString, content: text, role: .user)
        messages.append(userMessage)
        conversation.addMessage(userMessage)
        
        isStreaming = true
        currentContent = ""
        error = nil
        
        let requestData = ChatService.ChatRequest(
            conversationId: conversation.id,
            message: text,
            model: model,
            imageUrls: nil,
            userId: "default",
            webSearch: nil
        )
        
        do {
            let stream = try await chatService.streamMessage(requestData: requestData)
            
            for try await event in stream {
                if event.event == "message" {
                    if let dataData = event.data.data(using: .utf8),
                       let data = try? JSONSerialization.jsonObject(with: dataData) as? [String: Any],
                       let content = data["content"] as? String {
                        currentContent += content
                    }
                } else if event.event == "done" {
                    if let dataData = event.data.data(using: .utf8),
                       let data = try? JSONSerialization.jsonObject(with: dataData) as? [String: Any],
                       let messageId = data["messageId"] as? String,
                       let title = data["title"] as? String {
                        
                        let assistantMessage = Message(id: messageId, content: currentContent, role: .assistant)
                        messages.append(assistantMessage)
                        conversation.addMessage(assistantMessage)
                        
                        conversation.updateTitle(title)
                        
                        do {
                            try modelContext.save()
                        } catch {
                            print("Failed to save context: \(error)")
                        }
                    }
                    isStreaming = false
                    currentContent = ""
                    break
                }
            }
        } catch {
            self.error = error.localizedDescription
            isStreaming = false
            currentContent = ""
        }
    }
    
    func createNewConversation() async {
        do {
            let dto = try await chatService.createConversation()
            let conversation = Conversation(id: dto.id, title: dto.title)
            modelContext.insert(conversation)
            try modelContext.save()
            selectedConversation = conversation
            messages = []
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func deleteConversation() async {
        guard let conversation = selectedConversation else { return }
        
        do {
            try await chatService.deleteConversation(id: conversation.id)
            modelContext.delete(conversation)
            try modelContext.save()
            selectedConversation = nil
            messages = []
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func clearError() {
        error = nil
    }
}