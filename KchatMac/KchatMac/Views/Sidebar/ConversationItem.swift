import SwiftUI

struct ConversationItem: View {
    let conversation: Conversation
    let isSelected: Bool
    var onSelect: () -> Void
    var onDelete: () -> Void
    
    var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "message")
                .frame(width: 20, height: 20)
                .foregroundColor(isSelected ? .blue : .secondary)
            
            VStack(alignment: .leading, spacing: 2) {
                Text(conversation.title)
                    .font(.body)
                    .foregroundColor(isSelected ? .primary : .secondary)
                    .lineLimit(1)
                
                Text(formatDate(conversation.updatedAt))
                    .font(.caption2)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Button(action: onDelete) {
                Image(systemName: "trash")
                    .frame(width: 16, height: 16)
                    .foregroundColor(.red)
            }
            .opacity(0.6)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(isSelected ? .blue.opacity(0.2) : .clear)
        .cornerRadius(8)
        .onTapGesture {
            onSelect()
        }
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        let now = Date()
        
        if Calendar.current.isDateInToday(date) {
            formatter.dateStyle = .none
            formatter.timeStyle = .short
        } else if Calendar.current.isDateInYesterday(date) {
            return "昨天"
        } else {
            formatter.dateStyle = .short
            formatter.timeStyle = .none
        }
        
        return formatter.string(from: date)
    }
}