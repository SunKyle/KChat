import SwiftUI

enum ThemeType: String, Codable, CaseIterable {
    case light = "light"
    case dark = "dark"
    case system = "system"
    
    var displayName: String {
        switch self {
        case .light: return "浅色"
        case .dark: return "深色"
        case .system: return "跟随系统"
        }
    }
}

class ThemeService: ObservableObject {
    @Published var currentTheme: ThemeType = .system
    
    init() {
        if let saved = UserDefaults.standard.string(forKey: "kchat_theme") {
            currentTheme = ThemeType(rawValue: saved) ?? .system
        }
    }
    
    func setTheme(_ theme: ThemeType) {
        currentTheme = theme
        UserDefaults.standard.set(theme.rawValue, forKey: "kchat_theme")
    }
    
    var colorScheme: ColorScheme? {
        switch currentTheme {
        case .light: return .light
        case .dark: return .dark
        case .system: return nil
        }
    }
}

extension EnvironmentValues {
    private struct ThemeKey: EnvironmentKey {
        static var defaultValue: ThemeService = ThemeService()
    }
    
    var themeService: ThemeService {
        get { self[ThemeKey.self] }
        set { self[ThemeKey.self] = newValue }
    }
}