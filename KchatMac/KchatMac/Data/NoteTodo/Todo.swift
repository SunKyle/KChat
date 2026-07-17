import SwiftData
import Foundation
import SwiftUI

enum TodoStatus: String, Codable {
    case pending = "pending"
    case completed = "completed"
}

enum TodoPriority: String, Codable {
    case high = "high"
    case medium = "medium"
    case low = "low"
    
    var displayName: String {
        switch self {
        case .high: return "高"
        case .medium: return "中"
        case .low: return "低"
        }
    }
    
    var color: Color {
        switch self {
        case .high: return .red
        case .medium: return .orange
        case .low: return .green
        }
    }
}

@Model
final class Todo {
    @Attribute(.unique) var id: String
    var userId: String
    var title: String
    var note: String
    var status: String
    var priority: String
    var dueDate: Date?
    var category: String
    var createdAt: Date
    var completedAt: Date?
    
    init(id: String, title: String) {
        self.id = id
        self.userId = "default"
        self.title = title
        self.note = ""
        self.status = TodoStatus.pending.rawValue
        self.priority = TodoPriority.medium.rawValue
        self.category = "default"
        self.createdAt = Date()
    }
    
    var todoStatus: TodoStatus {
        TodoStatus(rawValue: status) ?? .pending
    }
    
    var todoPriority: TodoPriority {
        TodoPriority(rawValue: priority) ?? .medium
    }
    
    func toggleStatus() {
        if status == TodoStatus.pending.rawValue {
            status = TodoStatus.completed.rawValue
            completedAt = Date()
        } else {
            status = TodoStatus.pending.rawValue
            completedAt = nil
        }
    }
    
    func update(title: String? = nil, note: String? = nil, status: TodoStatus? = nil,
                priority: TodoPriority? = nil, dueDate: Date? = nil, category: String? = nil) {
        if let title { self.title = title }
        if let note { self.note = note }
        if let status { self.status = status.rawValue }
        if let priority { self.priority = priority.rawValue }
        if let dueDate { self.dueDate = dueDate }
        if let category { self.category = category }
    }
}