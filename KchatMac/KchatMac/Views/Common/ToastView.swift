import SwiftUI

struct ToastView: View {
    let message: String
    let type: ToastType
    
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: type.icon)
                .frame(width: 20, height: 20)
                .foregroundColor(type.color)
            
            Text(message)
                .font(.body)
            
            Spacer()
            
            Button(action: {}) {
                Image(systemName: "xmark")
                    .frame(width: 16, height: 16)
                    .foregroundColor(.secondary)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(.background)
        .cornerRadius(12)
        .shadow(radius: 4)
        .padding(.horizontal, 32)
    }
}

enum ToastType {
    case success
    case error
    case info
    case warning
    
    var icon: String {
        switch self {
        case .success: return "checkmark.circle"
        case .error: return "xmark.circle"
        case .info: return "info.circle"
        case .warning: return "exclamationmark.circle"
        }
    }
    
    var color: Color {
        switch self {
        case .success: return .green
        case .error: return .red
        case .info: return .blue
        case .warning: return .yellow
        }
    }
}

struct ToastModifier: ViewModifier {
    let message: String?
    let isPresented: Bool
    let type: ToastType
    let onDismiss: () -> Void
    
    func body(content: Content) -> some View {
        content
            .overlay(
                Group {
                    if isPresented, let message = message {
                        VStack {
                            Spacer()
                            ToastView(message: message, type: type)
                                .transition(.move(edge: .bottom).combined(with: .opacity))
                        }
                        .animation(.easeInOut(duration: 0.3), value: isPresented)
                        .onTapGesture {
                            onDismiss()
                        }
                    }
                }
            )
    }
}

extension View {
    func toast(message: String?, isPresented: Bool, type: ToastType = .error, onDismiss: @escaping () -> Void) -> some View {
        self.modifier(ToastModifier(message: message, isPresented: isPresented, type: type, onDismiss: onDismiss))
    }
}