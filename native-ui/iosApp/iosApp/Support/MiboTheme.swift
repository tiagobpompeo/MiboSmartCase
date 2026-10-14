import SwiftUI
import Shared

/// Paleta medida nas capturas do app Mibo Smart (mesma do projeto da raiz). Visual simples: List, NavigationStack, toolbar.
enum MiboColors {
    static let green = Color(hex: 0x00A237)         // verde Intelbras: tint, chips, botões
    static let pageBg = Color(hex: 0xF8F8F8)        // fundo das telas
    static let textSecondary = Color(hex: 0x8A8F94)
    static let onlineDot = Color(hex: 0x16C59C)     // bolinha "online"
    static let amber = Color(hex: 0xFFB300)         // bateria baixa / atualização disponível
    static let videoBg = Color(hex: 0x484848)       // fundo do vídeo antes do 1.º quadro
}

extension Color {
    /// `Color(hex: 0x00A237)`: o mesmo valor que o Compose usa em `Color(0xFF00A237)`.
    init(hex: UInt32) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}

/// Texto usado onde a API não informou o dado: nunca inventamos estado.
let textPlaceholder = "—"

/// "Online"/"Offline"; `nil` = a GDI não informou (não é "offline").
func onlineLabel(_ online: Bool?) -> String {
    switch online {
    case .some(true): return "Online"
    case .some(false): return "Offline"
    case .none: return textPlaceholder
    }
}

/// 3140 ms -> "3,1 s" (formato brasileiro, truncado), igual ao `formatSeconds` do Compose.
func formatSeconds(_ ms: Int64) -> String { "\(ms / 1000),\((ms % 1000) / 100) s" }

// MARK: - Rótulos derivados do Device (a UI não conhece a GDI; só lê o modelo de domínio)

extension Device {
    /// `Boolean?` do Kotlin chega ao Swift como `KotlinBoolean?` (classe boxed): aqui vira `Bool?` de verdade.
    var isOnline: Bool? { online?.boolValue }

    /// Rótulos do item da lista: "via hub" para subdispositivo e "compartilhado" quando a origem é SHARED
    /// (vinculado é o caso comum e não ganha rótulo).
    var tags: [String] {
        var tags: [String] = []
        if subdevice { tags.append("via hub") }
        if origin == DeviceOrigin.shared { tags.append("compartilhado") }
        return tags
    }

    /// Subtítulo da linha: "MFR 2030 • via hub • compartilhado".
    var subtitle: String { ([model ?? kind.label] + tags).joined(separator: " • ") }

    /// A GDI não informa canais; a linha "Dual" (iM4 Dual) tem duas lentes (móvel + fixa).
    /// O nome também conta porque o modelo pode vir genérico e o nome de fábrica traz o sufixo.
    var hasDualLens: Bool {
        [model, name].compactMap { $0 }.contains { $0.range(of: "dual", options: .caseInsensitive) != nil }
    }

    /// Ícone SF Symbols por tipo. Enum Kotlin vira classe com membros estáticos: comparação com `==`, não `switch`.
    var symbolName: String {
        if kind == DeviceKind.camera { return "video.fill" }
        if kind == DeviceKind.lock { return "lock.fill" }
        if kind == DeviceKind.hub { return "wifi.router.fill" }
        if kind == DeviceKind.sensor { return "sensor.fill" }
        if kind == DeviceKind.lamp { return "lightbulb.fill" }
        return "cpu"
    }
}

extension Firmware {
    /// `nil` = a API não informou: nada de afirmar "Atualizado" sem dado.
    var statusLabel: String? {
        switch updateAvailable?.boolValue {
        case .some(true): return "Atualização disponível"
        case .some(false): return "Atualizado"
        case .none: return nil
        }
    }
}
