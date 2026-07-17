import Foundation

struct APIClient {
    private let session: URLSession
    private let baseURL: URL
    
    init(baseURL: String = "http://localhost:8080/api") {
        self.session = URLSession.shared
        guard let url = URL(string: baseURL) else {
            fatalError("Invalid base URL: \(baseURL)")
        }
        self.baseURL = url
    }
    
    func request<T: Decodable>(_ endpoint: String, method: String = "GET", 
                               body: Data? = nil, headers: [String: String]? = nil) async throws -> T {
        guard let url = URL(string: endpoint, relativeTo: baseURL) else {
            throw APIError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        
        if let token = TokenService.shared.getToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        headers?.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        request.httpBody = body
        
        let (data, response) = try await session.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        
        guard (200...299).contains(httpResponse.statusCode) else {
            throw APIError.httpError(statusCode: httpResponse.statusCode, data: data)
        }
        
        do {
            return try JSONDecoder().decode(T.self, from: data)
        } catch {
            throw APIError.decodingError(error)
        }
    }
    
    func requestStream(_ endpoint: String, method: String = "POST",
                       body: Data? = nil, headers: [String: String]? = nil) async throws -> AsyncThrowingStream<SSEEvent, Error> {
        guard let url = URL(string: endpoint, relativeTo: baseURL) else {
            throw APIError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("text/event-stream", forHTTPHeaderField: "Accept")
        request.setValue("no-cache", forHTTPHeaderField: "Cache-Control")
        request.setValue("keep-alive", forHTTPHeaderField: "Connection")
        
        if let token = TokenService.shared.getToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        headers?.forEach { request.setValue($0.value, forHTTPHeaderField: $0.key) }
        request.httpBody = body
        
        let (stream, response) = try await session.bytes(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        
        guard (200...299).contains(httpResponse.statusCode) else {
            var data = Data()
            do {
                for try await byte in stream {
                    data.append(byte)
                    if data.count > 1024 * 1024 { break }
                }
            } catch {}
            throw APIError.httpError(statusCode: httpResponse.statusCode, data: data)
        }
        
        return AsyncThrowingStream { continuation in
            Task {
                let parser = SSEParser()
                var buffer: [UInt8] = []
                
                do {
                    for try await byte in stream {
                        buffer.append(byte)
                        if let event = parser.parse(buffer) {
                            buffer.removeAll()
                            continuation.yield(event)
                        }
                    }
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }
    
    func uploadFile(_ endpoint: String, fileData: Data, fileName: String) async throws -> [String: String] {
        guard let url = URL(string: endpoint, relativeTo: baseURL) else {
            throw APIError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        
        if let token = TokenService.shared.getToken() {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        let boundary = "Boundary-\(UUID().uuidString)"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"image\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: application/octet-stream\r\n\r\n".data(using: .utf8)!)
        body.append(fileData)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)
        
        request.httpBody = body
        
        let (data, response) = try await session.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw APIError.invalidResponse
        }
        
        guard (200...299).contains(httpResponse.statusCode) else {
            throw APIError.httpError(statusCode: httpResponse.statusCode, data: data)
        }
        
        do {
            return try JSONDecoder().decode([String: String].self, from: data)
        } catch {
            throw APIError.decodingError(error)
        }
    }
}

enum APIError: Error, LocalizedError {
    case invalidURL
    case invalidResponse
    case httpError(statusCode: Int, data: Data)
    case decodingError(Error)
    case networkError(Error)
    case timeout
    
    var errorDescription: String? {
        switch self {
        case .invalidURL: return "无效的 URL"
        case .invalidResponse: return "无效的响应"
        case .httpError(let statusCode, _): return "HTTP 错误: \(statusCode)"
        case .decodingError(let error): return "解码错误: \(error.localizedDescription)"
        case .networkError(let error): return "网络错误: \(error.localizedDescription)"
        case .timeout: return "请求超时"
        }
    }
}

struct SSEEvent: Decodable {
    let event: String
    let data: String
}

struct ChatCompletionChunk: Decodable {
    let content: String?
    let messageId: String?
    let title: String?
}