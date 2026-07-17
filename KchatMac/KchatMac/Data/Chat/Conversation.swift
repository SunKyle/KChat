import SwiftData
import Foundation

@Model
final class Conversation {
    @Attribute(.unique) var id: String
    var title: String
    var createdAt: Date
    var updatedAt: Date
    var pinned: Bool
    var isSummaryNote: Bool
    
    @Relationship(deleteRule: .cascade) var messages: [Message] = []
    
    init(id: String, title: String) {
        self.id = id
        self.title = title
        self.createdAt = Date()
        self.updatedAt = Date()
        self.pinned = false
        self.isSummaryNote = false
    }
    
    func updateTitle(_ newTitle: String) {
        self.title = newTitle
        self.updatedAt = Date()
    }
    
    func addMessage(_ message: Message) {
        messages.append(message)
        self.updatedAt = Date()
    }
}