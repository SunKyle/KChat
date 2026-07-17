import SwiftUI

struct ErrorCard: View {
    let error: String
    var onRetry: () -> Void
    
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: "exclamationmark.triangle")
                .font(.largeTitle)
                .foregroundColor(.orange)
            
            Text(error)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
            
            Button(action: onRetry) {
                Text("重试")
                    .font(.body)
                    .foregroundColor(.white)
                    .padding(.horizontal, 24)
                    .padding(.vertical, 8)
                    .background(.blue)
                    .cornerRadius(8)
            }
        }
        .padding(32)
        .background(.gray.opacity(0.2))
        .cornerRadius(16)
    }
}