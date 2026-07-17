import SwiftUI

struct MessageBubble: View {
    let message: Message
    let isStreaming: Bool
    let streamingContent: String
    
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            if message.messageRole == .assistant {
                avatarView
            }
            
            VStack(alignment: message.messageRole == .user ? .trailing : .leading, spacing: 4) {
                contentView
                timeView
            }
            
            if message.messageRole == .user {
                avatarView
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
        .transition(.opacity.combined(with: .move(edge: .bottom)))
    }
    
    private var avatarView: some View {
        Image(systemName: message.messageRole == .user ? "person" : "bot")
            .resizable()
            .frame(width: 36, height: 36)
            .foregroundColor(message.messageRole == .user ? .blue : .purple)
            .background(.gray.opacity(0.2))
            .clipShape(Circle())
    }
    
    private var contentView: some View {
        let content = isStreaming && message.messageRole == .assistant ? streamingContent : message.content
        
        return ZStack {
            RoundedRectangle(cornerRadius: 16)
                .fill(message.messageRole == .user ? .blue : .gray.opacity(0.2))
            
            VStack(alignment: .leading, spacing: 4) {
                if message.messageRole == .assistant {
                    Text("AI")
                        .font(.caption)
                        .foregroundColor(.secondary)
                        .padding(.leading, 12)
                        .padding(.top, 8)
                }
                
                ScrollView {
                    MarkdownView(text: content)
                        .padding(message.messageRole == .user ? [.leading, .trailing] : [])
                        .padding(.bottom, 8)
                }
                .frame(maxWidth: 500)
                .padding(message.messageRole == .user ? [.leading, .trailing] : [])
            }
        }
        .padding(.horizontal, message.messageRole == .user ? 8 : 0)
    }
    
    private var timeView: some View {
        Text(formatDate(message.timestamp))
            .font(.caption2)
            .foregroundColor(.secondary)
            .padding(.horizontal, 4)
    }
    
    private func formatDate(_ date: Date) -> String {
        let formatter = DateFormatter()
        formatter.dateStyle = .none
        formatter.timeStyle = .short
        return formatter.string(from: date)
    }
}