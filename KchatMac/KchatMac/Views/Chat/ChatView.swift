import SwiftUI
import SwiftData

struct ChatView: View {
    @StateObject var viewModel: ChatViewModel
    @Binding var selectedConversation: Conversation?
    @State private var inputText = ""
    
    var body: some View {
        VStack(spacing: 0) {
            if let conversation = selectedConversation {
                headerView(conversation)
            }
            
            ScrollViewReader { proxy in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(viewModel.messages, id: \.id) { message in
                            MessageBubble(
                                message: message,
                                isStreaming: viewModel.isStreaming && message.messageRole == .assistant,
                                streamingContent: viewModel.currentContent
                            )
                        }
                        
                        if viewModel.isStreaming {
                            TypingIndicator()
                        }
                    }
                }
                .onChange(of: viewModel.messages.count) {
                    if !viewModel.messages.isEmpty {
                        withAnimation {
                            proxy.scrollTo(viewModel.messages.last?.id, anchor: .bottom)
                        }
                    }
                }
                .onChange(of: viewModel.currentContent) {
                    if viewModel.isStreaming {
                        withAnimation {
                            proxy.scrollTo(viewModel.messages.last?.id ?? "streaming", anchor: .bottom)
                        }
                    }
                }
            }
            
            InputArea(
                text: $inputText,
                onSend: sendMessage,
                isStreaming: viewModel.isStreaming
            )
        }
        .onAppear {
            if let conversation = selectedConversation {
                Task {
                    await viewModel.loadConversation(conversation)
                }
            }
        }
        .onChange(of: selectedConversation) { newConversation in
            if let conversation = newConversation {
                Task {
                    await viewModel.loadConversation(conversation)
                }
            } else {
                viewModel.messages = []
            }
        }
        .alert("错误", isPresented: .constant(viewModel.error != nil), actions: {
            Button("确定") {
                viewModel.clearError()
            }
        }, message: {
            if let error = viewModel.error {
                Text(error)
            }
        })
    }
    
    private func headerView(_ conversation: Conversation) -> some View {
        HStack(spacing: 12) {
            Button(action: {}) {
                Image(systemName: "chevron.left")
                    .foregroundColor(.secondary)
            }
            
            VStack(alignment: .leading) {
                Text(conversation.title)
                    .font(.headline)
                
                Text("\(conversation.messages.count) 条消息")
                    .font(.caption)
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Button(action: {}) {
                Image(systemName: "ellipsis")
                    .foregroundColor(.secondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.background)
    }
    
    private func sendMessage() {
        guard !inputText.isEmpty else { return }
        
        let text = inputText
        inputText = ""
        
        Task {
            await viewModel.sendMessage(text)
        }
    }
}