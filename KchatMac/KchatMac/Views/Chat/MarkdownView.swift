import SwiftUI

struct MarkdownView: View {
    let text: String
    
    var body: some View {
        Text(text)
            .font(.body)
            .textSelection(.enabled)
            .multilineTextAlignment(.leading)
            .frame(maxWidth: .infinity, alignment: .leading)
    }
}