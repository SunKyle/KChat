import Foundation

class SSEParser {
    private var buffer = ""
    
    func parse(_ bytes: [UInt8]) -> SSEEvent? {
        if let string = String(bytes: bytes, encoding: .utf8) {
            buffer += string
            return processBuffer()
        }
        return nil
    }
    
    private func processBuffer() -> SSEEvent? {
        while let doubleNewlineIndex = buffer.range(of: "\n\n") {
            let eventBlock = String(buffer[..<doubleNewlineIndex.lowerBound])
            buffer = String(buffer[doubleNewlineIndex.upperBound...])
            
            if let event = parseEventBlock(eventBlock) {
                return event
            }
        }
        return nil
    }
    
    private func parseEventBlock(_ block: String) -> SSEEvent? {
        var eventType = "message"
        var data = ""
        
        let lines = block.split(separator: "\n")
        for line in lines {
            let trimmedLine = line.trimmingCharacters(in: .whitespaces)
            
            if trimmedLine.hasPrefix("event:") {
                eventType = String(trimmedLine.dropFirst(6)).trimmingCharacters(in: .whitespaces)
            } else if trimmedLine.hasPrefix("data:") {
                data += String(trimmedLine.dropFirst(5))
            }
        }
        
        if !data.isEmpty {
            return SSEEvent(event: eventType, data: data)
        }
        
        return nil
    }
}