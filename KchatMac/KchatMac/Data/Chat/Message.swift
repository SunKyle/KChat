import SwiftData
import Foundation

enum MessageRole: String, Codable {
    case user = "user"
    case assistant = "assistant"
}

@Model
final class Message {
    @Attribute(.unique) var id: String
    var content: String
    var role: String
    var timestamp: Date
    var images: [String]
    
    @Relationship(inverse: \Conversation.messages) var conversation: Conversation?
    
    init(id: String, content: String, role: MessageRole) {
        self.id = id
        self.content = content
        self.role = role.rawValue
        self.timestamp = Date()
        self.images = []
    }
    
    var messageRole: MessageRole {
        MessageRole(rawValue: role) ?? .user
    }
}