import SwiftUI
import SwiftData

enum NoteTodoMode {
    case note
    case todo
}

class NoteTodoViewModel: ObservableObject {
    @Published var notes: [Note] = []
    @Published var todos: [Todo] = []
    @Published var mode: NoteTodoMode = .note
    @Published var selectedNote: Note?
    @Published var selectedTodo: Todo?
    @Published var error: String?
    
    private var modelContext: ModelContext
    
    init(modelContext: ModelContext) {
        self.modelContext = modelContext
        loadNotes()
        loadTodos()
    }
    
    func loadNotes() {
        Task {
            do {
                let descriptor = FetchDescriptor<Note>()
                var fetchedNotes = try modelContext.fetch(descriptor)
                fetchedNotes.sort {
                    if $0.pinned != $1.pinned {
                        return $0.pinned
                    }
                    return $0.updatedAt > $1.updatedAt
                }
                notes = fetchedNotes
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
    
    func loadTodos() {
        Task {
            do {
                let descriptor = FetchDescriptor<Todo>()
                var fetchedTodos = try modelContext.fetch(descriptor)
                fetchedTodos.sort {
                    if $0.status != $1.status {
                        return $0.status < $1.status
                    }
                    if $0.priority != $1.priority {
                        return $0.priority > $1.priority
                    }
                    return $0.createdAt < $1.createdAt
                }
                todos = fetchedTodos
            } catch {
                self.error = error.localizedDescription
            }
        }
    }
    
    func addNote(title: String, content: String) {
        let note = Note(id: UUID().uuidString, title: title, content: content)
        modelContext.insert(note)
        do {
            try modelContext.save()
            notes.insert(note, at: 0)
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func updateNote(_ note: Note, title: String? = nil, content: String? = nil, 
                    category: String? = nil, tags: [String]? = nil, pinned: Bool? = nil) {
        note.update(title: title, content: content, category: category, tags: tags, pinned: pinned)
        do {
            try modelContext.save()
            if let index = notes.firstIndex(where: { $0.id == note.id }) {
                notes[index] = note
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func deleteNote(_ note: Note) {
        modelContext.delete(note)
        do {
            try modelContext.save()
            if let index = notes.firstIndex(where: { $0.id == note.id }) {
                notes.remove(at: index)
            }
            if selectedNote?.id == note.id {
                selectedNote = nil
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func addTodo(title: String, note: String = "", priority: TodoPriority = .medium) {
        let todo = Todo(id: UUID().uuidString, title: title)
        todo.update(note: note, priority: priority)
        modelContext.insert(todo)
        do {
            try modelContext.save()
            todos.insert(todo, at: 0)
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func toggleTodo(_ todo: Todo) {
        todo.toggleStatus()
        do {
            try modelContext.save()
            if let index = todos.firstIndex(where: { $0.id == todo.id }) {
                todos[index] = todo
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func updateTodo(_ todo: Todo, title: String? = nil, note: String? = nil, 
                    status: TodoStatus? = nil, priority: TodoPriority? = nil, 
                    dueDate: Date? = nil, category: String? = nil) {
        todo.update(title: title, note: note, status: status, 
                    priority: priority, dueDate: dueDate, category: category)
        do {
            try modelContext.save()
            if let index = todos.firstIndex(where: { $0.id == todo.id }) {
                todos[index] = todo
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func deleteTodo(_ todo: Todo) {
        modelContext.delete(todo)
        do {
            try modelContext.save()
            if let index = todos.firstIndex(where: { $0.id == todo.id }) {
                todos.remove(at: index)
            }
            if selectedTodo?.id == todo.id {
                selectedTodo = nil
            }
        } catch {
            self.error = error.localizedDescription
        }
    }
    
    func selectNote(_ note: Note?) {
        selectedNote = note
        selectedTodo = nil
    }
    
    func selectTodo(_ todo: Todo?) {
        selectedTodo = todo
        selectedNote = nil
    }
    
    func clearError() {
        error = nil
    }
}