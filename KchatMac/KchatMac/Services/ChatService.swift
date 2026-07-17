import Foundation
import SwiftData

class ChatService {
    private let apiClient = APIClient()
    
    struct ChatRequest: Encodable {
        let conversationId: String?
        let message: String
        let model: String?
        let imageUrls: [String]?
        let userId: String?
        let webSearch: Bool?
    }
    
    struct ChatResponse: Decodable {
        let messageId: String
        let content: String
        let role: String
        let conversationId: String
    }
    
    struct ConversationDTO: Decodable {
        let id: String
        let title: String
        let createdAt: String
        let updatedAt: String?
        let pinned: Bool?
        let isSummaryNote: Bool?
    }
    
    struct MessageDTO: Decodable {
        let id: String
        let conversationId: String
        let content: String
        let role: String
        let timestamp: String
        let images: [String]?
    }
    
    func getConversations() async throws -> [ConversationDTO] {
        return try await apiClient.request("/conversations")
    }
    
    func getConversation(id: String) async throws -> (conversation: ConversationDTO, messages: [MessageDTO]) {
        struct Response: Decodable {
            let conversation: ConversationDTO
            let messages: [MessageDTO]
        }
        let response: Response = try await apiClient.request("/conversations/\(id)")
        return (response.conversation, response.messages)
    }
    
    func createConversation(title: String? = nil) async throws -> ConversationDTO {
        if let title {
            let body = try JSONEncoder().encode(["title": title])
            return try await apiClient.request("/conversations", method: "POST", body: body)
        }
        return try await apiClient.request("/conversations", method: "POST")
    }
    
    func updateConversation(id: String, updates: [String: Any]) async throws -> ConversationDTO {
        let body = try JSONSerialization.data(withJSONObject: updates)
        return try await apiClient.request("/conversations/\(id)", method: "PUT", body: body)
    }
    
    func deleteConversation(id: String) async throws {
        struct EmptyResponse: Decodable {}
        _ = try await apiClient.request("/conversations/\(id)", method: "DELETE") as EmptyResponse
    }
    
    func sendMessage(requestData: ChatRequest) async throws -> ChatResponse {
        let body = try JSONEncoder().encode(requestData)
        return try await apiClient.request("/chat", method: "POST", body: body)
    }
    
    func streamMessage(requestData: ChatRequest) async throws -> AsyncThrowingStream<SSEEvent, Error> {
        let body = try JSONEncoder().encode(requestData)
        return try await apiClient.requestStream("/chat/stream", method: "POST", body: body)
    }
    
    func summarize(content: String, model: String) async throws -> (title: String, summary: String) {
        struct Response: Decodable {
            let title: String
            let summary: String
        }
        let body = try JSONEncoder().encode(["content": content, "model": model, "userId": "default"])
        let response: Response = try await apiClient.request("/chat/summarize", method: "POST", body: body)
        return (response.title, response.summary)
    }
    
    func uploadImage(fileData: Data, fileName: String) async throws -> String {
        let result = try await apiClient.uploadFile("/images/upload", fileData: fileData, fileName: fileName)
        return result["url"] ?? ""
    }
}