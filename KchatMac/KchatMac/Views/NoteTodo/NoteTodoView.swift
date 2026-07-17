import SwiftUI
import SwiftData

struct NoteTodoView: View {
    @StateObject var viewModel: NoteTodoViewModel
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if viewModel.mode == .note {
                    notesView
                } else {
                    todosView
                }
                
                TabBarView(selectedMode: $viewModel.mode)
            }
            .navigationTitle(viewModel.mode == .note ? "笔记" : "待办")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button(action: addItem) {
                        Image(systemName: "plus")
                    }
                }
            }
        }
    }
    
    private var notesView: some View {
        ScrollView {
            LazyVStack(spacing: 12) {
                ForEach(viewModel.notes) { note in
                    NoteCard(note: note, onSelect: { viewModel.selectNote(note) })
                }
                
                if viewModel.notes.isEmpty {
                    emptyStateView(title: "暂无笔记", subtitle: "点击右上角 + 创建笔记")
                }
            }
            .padding(16)
        }
    }
    
    private var todosView: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
                ForEach(viewModel.todos) { todo in
                    TodoRow(todo: todo, onToggle: { viewModel.toggleTodo(todo) })
                }
                
                if viewModel.todos.isEmpty {
                    emptyStateView(title: "暂无待办", subtitle: "点击右上角 + 创建待办")
                }
            }
            .padding(16)
        }
    }
    
    private func emptyStateView(title: String, subtitle: String) -> some View {
        VStack(spacing: 12) {
            Image(systemName: viewModel.mode == .note ? "notebook" : "checklist")
                .font(.largeTitle)
                .foregroundColor(.secondary)
            
            Text(title)
                .font(.body)
                .foregroundColor(.secondary)
            
            Text(subtitle)
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(40)
    }
    
    private func addItem() {
        if viewModel.mode == .note {
            viewModel.addNote(title: "新建笔记", content: "")
        } else {
            viewModel.addTodo(title: "新建待办")
        }
    }
}

struct TabBarView: View {
    @Binding var selectedMode: NoteTodoMode
    
    var body: some View {
        HStack(spacing: 0) {
            Button(action: { selectedMode = .note }) {
                VStack(spacing: 4) {
                    Image(systemName: "notebook")
                        .foregroundColor(selectedMode == .note ? .blue : .secondary)
                    
                    Text("笔记")
                        .font(.caption)
                        .foregroundColor(selectedMode == .note ? .blue : .secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
            }
            
            Divider()
            
            Button(action: { selectedMode = .todo }) {
                VStack(spacing: 4) {
                    Image(systemName: "checklist")
                        .foregroundColor(selectedMode == .todo ? .blue : .secondary)
                    
                    Text("待办")
                        .font(.caption)
                        .foregroundColor(selectedMode == .todo ? .blue : .secondary)
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
            }
        }
        .background(.background)
    }
}

struct NoteCard: View {
    let note: Note
    var onSelect: () -> Void
    
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text(note.title)
                    .font(.headline)
                
                if note.pinned {
                    Image(systemName: "pin")
                        .frame(width: 16, height: 16)
                        .foregroundColor(.yellow)
                }
            }
            
            Text(note.content)
                .font(.body)
                .foregroundColor(.secondary)
                .lineLimit(3)
            
            HStack(spacing: 12) {
                Text(formatDate(note.updatedAt))
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                if !note.tags.isEmpty {
                    Text(note.tags.first!)
                        .font(.caption)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 2)
                        .background(.gray.opacity(0.2))
                        .cornerRadius(4)
                }
            }
        }
        .padding(16)
        .background(.background)
        .cornerRadius(12)
        .shadow(radius: 2)
        .onTapGesture {
            onSelect()
        }
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        return formatter.string(from: date)
    }
}

struct TodoRow: View {
    let todo: Todo
    var onToggle: () -> Void
    
    var body: some View {
        HStack(spacing: 12) {
            Button(action: onToggle) {
                Image(systemName: todo.todoStatus == .completed ? "checkmark.circle.fill" : "circle")
                    .frame(width: 20, height: 20)
                    .foregroundColor(todo.todoStatus == .completed ? .green : .secondary)
            }
            
            VStack(alignment: .leading, spacing: 4) {
                Text(todo.title)
                    .font(.body)
                    .strikethrough(todo.todoStatus == .completed)
                    .foregroundColor(todo.todoStatus == .completed ? .secondary : .primary)
                
                if !todo.note.isEmpty {
                    Text(todo.note)
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .lineLimit(1)
                }
            }
            
            Spacer()
            
            if todo.dueDate != nil {
                Text(formatDate(todo.dueDate!))
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Circle()
                .frame(width: 8, height: 8)
                .foregroundColor(todo.todoPriority.color)
        }
        .padding(12)
        .background(.background)
        .cornerRadius(8)
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .short
        return formatter.string(from: date)
    }
}