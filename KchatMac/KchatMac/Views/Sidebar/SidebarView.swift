import SwiftUI
import SwiftData

struct SidebarView: View {
    @StateObject var viewModel: SidebarViewModel
    @Binding var selectedConversation: Conversation?
    var onCreateConversation: () -> Void
    
    var body: some View {
        VStack(spacing: 0) {
            headerView
            
            searchView
            
            Divider()
            
            ScrollView {
                LazyVStack(spacing: 0) {
                    if viewModel.isLoading {
                        loadingView
                    } else {
                        ForEach(viewModel.filteredConversations) { conversation in
                            ConversationItem(
                                conversation: conversation,
                                isSelected: selectedConversation?.id == conversation.id,
                                onSelect: {
                                    viewModel.selectConversation(conversation)
                                    selectedConversation = conversation
                                },
                                onDelete: {
                                    Task {
                                        await viewModel.deleteConversation(conversation)
                                    }
                                }
                            )
                        }
                        
                        if viewModel.filteredConversations.isEmpty {
                            emptyStateView
                        }
                    }
                }
            }
            
            Divider()
            
            bottomActionsView
        }
        .frame(width: 280)
        .background(.gray.opacity(0.1))
        .toast(message: viewModel.error, isPresented: viewModel.error != nil) {
            viewModel.clearError()
        }
    }
    
    private var loadingView: some View {
        VStack(spacing: 12) {
            ProgressView()
                .scaleEffect(1.5)
            
            Text("加载对话中...")
                .font(.body)
                .foregroundColor(.secondary)
        }
        .padding(40)
    }
    
    private var headerView: some View {
        HStack(spacing: 8) {
            Image(systemName: "message.circle")
                .font(.title)
                .foregroundColor(.blue)
            
            Text("KChat")
                .font(.title)
                .fontWeight(.bold)
            
            Spacer()
            
            Button(action: onCreateConversation) {
                Image(systemName: "plus")
                    .frame(width: 24, height: 24)
                    .foregroundColor(.white)
                    .background(.blue)
                    .clipShape(Circle())
            }
            .keyboardShortcut("N", modifiers: .command)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
    
    private var searchView: some View {
        HStack(spacing: 8) {
            Image(systemName: "search")
                .frame(width: 16, height: 16)
                .foregroundColor(.secondary)
            
            TextField("搜索对话...", text: $viewModel.searchText)
                .font(.body)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(.background)
        .cornerRadius(8)
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .keyboardShortcut("K", modifiers: .command)
    }
    
    private var emptyStateView: some View {
        VStack(spacing: 12) {
            Image(systemName: "message.circle")
                .font(.largeTitle)
                .foregroundColor(.secondary)
            
            Text("暂无对话")
                .font(.body)
                .foregroundColor(.secondary)
            
            Text("点击右上角 + 创建新对话")
                .font(.caption)
                .foregroundColor(.secondary)
        }
        .padding(40)
    }
    
    private var bottomActionsView: some View {
        VStack(spacing: 4) {
            Button(action: {}) {
                HStack(spacing: 8) {
                    Image(systemName: "notebook")
                        .frame(width: 16, height: 16)
                        .foregroundColor(.secondary)
                    
                    Text("笔记")
                        .font(.body)
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .cornerRadius(8)
            }
            
            Button(action: {}) {
                HStack(spacing: 8) {
                    Image(systemName: "checklist")
                        .frame(width: 16, height: 16)
                        .foregroundColor(.secondary)
                    
                    Text("待办")
                        .font(.body)
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .cornerRadius(8)
            }
            
            Button(action: {}) {
                HStack(spacing: 8) {
                    Image(systemName: "settings")
                        .frame(width: 16, height: 16)
                        .foregroundColor(.secondary)
                    
                    Text("设置")
                        .font(.body)
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .cornerRadius(8)
            }
            .keyboardShortcut(",", modifiers: .command)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
    }
}