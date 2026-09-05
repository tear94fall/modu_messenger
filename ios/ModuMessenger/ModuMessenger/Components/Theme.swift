import SwiftUI

/// 안드로이드 res/values/colors.xml 의 브랜드 색.
extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
    static let brand = Color(hex: 0x786BFC)
    static let brandDark = Color(hex: 0x5B4FD6)
    static let bubbleMine = Color(hex: 0xC9C2FE)
    static let onBubble = Color(hex: 0x1F2430)
    static let bubbleOther = Color(.secondarySystemBackground)
}
