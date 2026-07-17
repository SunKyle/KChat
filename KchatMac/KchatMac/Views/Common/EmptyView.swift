import SwiftUI

struct EmptyView: View {
    let title: String
    let subtitle: String
    let icon: String
    
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 64))
                .foregroundColor(.secondary)
            
            Text(title)
                .font(.largeTitle)
                .foregroundColor(.secondary)
            
            Text(subtitle)
                .font(.body)
                .foregroundColor(.secondary)
                .multilineTextAlignment(.center)
        }
        .padding(40)
    }
}