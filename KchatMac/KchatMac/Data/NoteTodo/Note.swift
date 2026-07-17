import SwiftData
import Foundation

@Model
final class Note {
    @Attribute(.unique) var id: String
    var userId: String
    var title: String
    var content: String
    var category: String
    var tags: [String]
    var pinned: Bool
    var createdAt: Date
    var updatedAt: Date
    
    init(id: String, title: String, content: String) {
        self.id = id
        self.userId = "default"
        self.title = title
        self.content = content
        self.category = "default"
        self.tags = []
        self.pinned = false
        self.createdAt = Date()
        self.updatedAt = Date()
    }
    
    func update(title: String? = nil, content: String? = nil, category: String? = nil, 
                tags: [String]? = nil, pinned: Bool? = nil) {
        if let title { self.title = title }
        if let content { self.content = content }
        if let category { self.category = category }
        if let tags { self.tags = tags }
        if let pinned { self.pinned = pinned }
        self.updatedAt = Date()
    }
}