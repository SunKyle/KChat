import SwiftUI

struct InputArea: View {
    @Binding var text: String
    var onSend: () -> Void
    var isStreaming: Bool
    
    var body: some View {
        VStack(spacing: 8) {
            Divider()
            
            HStack(spacing: 8) {
                Button(action: {}) {
                    Image(systemName: "plus")
                        .frame(width: 32, height: 32)
                        .foregroundColor(.secondary)
                        .background(.gray.opacity(0.2))
                        .clipShape(Circle())
                }
                .disabled(isStreaming)
                
                ZStack(alignment: .leading) {
                    if text.isEmpty {
                        Text("输入消息...")
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 16)
                    }
                    
                    TextEditor(text: $text)
                        .font(.body)
                        .frame(height: 44)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 4)
                        .background(.gray.opacity(0.2))
                        .cornerRadius(16)
                        .disabled(isStreaming)
                }
                .frame(minHeight: 44, maxHeight: 120)
                
                Button(action: onSend) {
                    Image(systemName: "arrow.up")
                        .frame(width: 32, height: 32)
                        .foregroundColor(.white)
                        .background(text.isEmpty || isStreaming ? .gray : .blue)
                        .clipShape(Circle())
                }
                .disabled(text.isEmpty || isStreaming)
                .keyboardShortcut(.return, modifiers: [.command])
            }
            
            HStack(spacing: 16) {
                Text("支持 Markdown 格式")
                    .font(.caption)
                    .foregroundColor(.secondary)
                
                Spacer()
                
                Text("Cmd + Enter 发送")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            .padding(.horizontal, 8)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 16)
    }
}