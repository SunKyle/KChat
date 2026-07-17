import SwiftData

struct KChatContainer {
    static func create() -> ModelContainer {
        let schema = Schema([
            Conversation.self,
            Message.self,
            ModelConfig.self,
            Note.self,
            Todo.self
        ])
        
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
        
        do {
            return try ModelContainer(for: schema, configurations: config)
        } catch {
            fatalError("Failed to create ModelContainer: \(error)")
        }
    }
    
    static func createInMemory() -> ModelContainer {
        let schema = Schema([
            Conversation.self,
            Message.self,
            ModelConfig.self,
            Note.self,
            Todo.self
        ])
        
        let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
        
        do {
            return try ModelContainer(for: schema, configurations: config)
        } catch {
            fatalError("Failed to create in-memory ModelContainer: \(error)")
        }
    }
}