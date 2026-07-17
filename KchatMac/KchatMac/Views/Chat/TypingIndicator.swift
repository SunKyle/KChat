import SwiftUI

struct TypingIndicator: View {
    var body: some View {
        HStack(alignment: .top, spacing: 12) {
            Image(systemName: "bot")
                .resizable()
                .frame(width: 36, height: 36)
                .foregroundColor(.purple)
                .background(.gray.opacity(0.2))
                .clipShape(Circle())
            
            VStack(alignment: .leading, spacing: 4) {
                ZStack {
                    RoundedRectangle(cornerRadius: 16)
                        .fill(.gray.opacity(0.2))
                    
                    VStack(alignment: .leading, spacing: 8) {
                        Text("AI")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .padding(.leading, 12)
                            .padding(.top, 8)
                        
                        HStack(spacing: 4) {
                            Circle()
                                .frame(width: 6, height: 6)
                                .foregroundColor(.secondary)
                                .opacity(0.6)
                                .animation(Animation.easeInOut(duration: 0.6).repeatForever(), value: UUID())
                            
                            Circle()
                                .frame(width: 6, height: 6)
                                .foregroundColor(.secondary)
                                .opacity(0.6)
                                .animation(Animation.easeInOut(duration: 0.6).repeatForever().delay(0.2), value: UUID())
                            
                            Circle()
                                .frame(width: 6, height: 6)
                                .foregroundColor(.secondary)
                                .opacity(0.6)
                                .animation(Animation.easeInOut(duration: 0.6).repeatForever().delay(0.4), value: UUID())
                        }
                        .padding(.horizontal, 12)
                        .padding(.bottom, 8)
                    }
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 8)
    }
}