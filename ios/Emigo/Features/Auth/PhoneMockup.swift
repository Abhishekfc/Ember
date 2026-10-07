import SwiftUI

/// A drawn iPhone with an Emigo widget on its home screen: the picture on the welcome screen that
/// says what the app is, "a friend's photo, right where you already look".
///
/// Everything is drawn in code from the phone's width, so it stays sharp at any size and needs no
/// artwork except the sample photo. The apps around the widget are plain placeholders, so it
/// doesn't advertise anyone else's brand.
struct PhoneMockup: View {
    /// The phone's width in points; its height follows from the shape of a real iPhone.
    let width: CGFloat

    @Environment(\.theme) private var theme

    // MARK: - Proportions (an iPhone 16 Pro, scaled)

    private var height: CGFloat { width * 2.17 }
    private var bodyRadius: CGFloat { width * 0.17 }
    /// The metal rim, then the black glass edge, between the outside and the picture.
    private var rim: CGFloat { width * 0.012 }
    private var inset: CGFloat { width * 0.034 }
    private var screenWidth: CGFloat { width - 2 * inset }
    private var screenHeight: CGFloat { height - 2 * inset }
    private var screenRadius: CGFloat { bodyRadius - inset }
    /// One hundredth of the screen's width: every size on the screen is a multiple of this.
    private var unit: CGFloat { screenWidth / 100 }

    // MARK: - Home screen grid

    private let columns = 4
    private let rows = 6
    private var sidePadding: CGFloat { 7 * unit }
    private var columnGap: CGFloat { 4.6 * unit }
    private var cell: CGFloat { (100 * unit - 2 * sidePadding - 3 * columnGap) / 4 }
    private var labelHeight: CGFloat { 2.2 * unit }
    private var rowGap: CGFloat { 3.6 * unit }
    private var rowPitch: CGFloat { cell + 1.6 * unit + labelHeight + rowGap }
    private var gridTop: CGFloat { 17 * unit }

    var body: some View {
        ZStack {
            sideButtons
            phoneBody
            screen
        }
        .frame(width: width, height: height)
        .accessibilityHidden(true)
    }

    // MARK: - The device

    private var phoneBody: some View {
        ZStack {
            RoundedRectangle(cornerRadius: bodyRadius, style: .continuous)
                .fill(LinearGradient(
                    colors: [Color(white: 0.42), Color(white: 0.16), Color(white: 0.30)],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                ))
            RoundedRectangle(cornerRadius: bodyRadius, style: .continuous)
                .strokeBorder(
                    LinearGradient(colors: [.white.opacity(0.35), .white.opacity(0.04), .white.opacity(0.18)], startPoint: .top, endPoint: .bottom),
                    lineWidth: max(0.75, width * 0.004)
                )
            RoundedRectangle(cornerRadius: bodyRadius - rim, style: .continuous)
                .fill(Color.black)
                .padding(rim)
        }
    }

    /// The buttons on the edges: Action and volume on the left, power on the right.
    private var sideButtons: some View {
        let thickness = width * 0.014
        let protrusion = thickness * 0.55
        func button(height: CGFloat, y: CGFloat, leading: Bool) -> some View {
            RoundedRectangle(cornerRadius: thickness / 2, style: .continuous)
                .fill(LinearGradient(colors: [Color(white: 0.34), Color(white: 0.14)], startPoint: .leading, endPoint: .trailing))
                .frame(width: thickness, height: height)
                .offset(x: leading ? -width / 2 + thickness / 2 - protrusion : width / 2 - thickness / 2 + protrusion,
                        y: -self.height / 2 + y + height / 2)
        }
        return ZStack {
            button(height: self.height * 0.035, y: self.height * 0.145, leading: true)
            button(height: self.height * 0.065, y: self.height * 0.21, leading: true)
            button(height: self.height * 0.065, y: self.height * 0.29, leading: true)
            button(height: self.height * 0.10, y: self.height * 0.24, leading: false)
        }
    }

    // MARK: - The screen

    private var screen: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(
                colors: [Color(red: 0.19, green: 0.17, blue: 0.10), Color(white: 0.07), Color(white: 0.03)],
                startPoint: .top,
                endPoint: .bottom
            )

            statusBar
            homeGrid
            widget
            pageDots
            dock
            homeIndicator
            dynamicIsland
        }
        .frame(width: screenWidth, height: screenHeight, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: screenRadius, style: .continuous))
    }

    private var dynamicIsland: some View {
        Capsule()
            .fill(Color.black)
            .frame(width: 30 * unit, height: 8.8 * unit)
            .frame(width: screenWidth)
            .offset(y: 2.6 * unit)
    }

    private var statusBar: some View {
        HStack {
            Text(verbatim: "9:41")
                .font(.system(size: 5.4 * unit, weight: .semibold))
            Spacer()
            HStack(spacing: 1.6 * unit) {
                Image(systemName: "cellularbars")
                Image(systemName: "wifi")
                Image(systemName: "battery.100percent")
            }
            .font(.system(size: 4.4 * unit, weight: .semibold))
        }
        .foregroundStyle(.white)
        .padding(.horizontal, 8.5 * unit)
        .frame(width: screenWidth, height: 8.8 * unit)
        .offset(y: 2.6 * unit)
    }

    private var homeGrid: some View {
        VStack(alignment: .leading, spacing: rowGap) {
            ForEach(0..<rows, id: \.self) { row in
                HStack(spacing: columnGap) {
                    ForEach(0..<columns, id: \.self) { column in
                        if isUnderWidget(row: row, column: column) {
                            Color.clear.frame(width: cell, height: cell + 1.6 * unit + labelHeight)
                        } else {
                            appIcon(index: row * columns + column)
                        }
                    }
                }
            }
        }
        .padding(.horizontal, sidePadding)
        .offset(y: gridTop)
    }

    /// The widget covers the two rows right of the second column; those slots are left empty.
    private func isUnderWidget(row: Int, column: Int) -> Bool {
        (1...2).contains(row) && column >= 2
    }

    private func appIcon(index: Int) -> some View {
        let tone = Self.iconTones[index % Self.iconTones.count]
        return VStack(spacing: 1.6 * unit) {
            RoundedRectangle(cornerRadius: cell * 0.225, style: .continuous)
                .fill(LinearGradient(colors: [Color(hex: tone.top), Color(hex: tone.bottom)], startPoint: .top, endPoint: .bottom))
                .frame(width: cell, height: cell)
            Capsule()
                .fill(Color.white.opacity(0.28))
                .frame(width: cell * 0.62, height: labelHeight * 0.8)
                .frame(height: labelHeight)
        }
    }

    /// Soft, mostly neutral tones with a few cool and warm ones, so the apps read as a varied
    /// home screen without being anyone's real icons.
    private static let iconTones: [(top: UInt32, bottom: UInt32)] = [
        (0x4A4A4E, 0x2C2C2E), (0x3A3F4A, 0x22252C), (0x55504A, 0x302D29), (0x2E3A36, 0x1B2320),
        (0x58585C, 0x38383B), (0x40404A, 0x25252C), (0x4A4440, 0x2A2624), (0x363A40, 0x1F2226),
        (0x505052, 0x303032), (0x3E4A44, 0x242C28), (0x4C4A56, 0x2B2A32), (0x44403A, 0x28251F),
    ]

    /// The Emigo widget: a friend's photo, with the app's yellow around it. Just the photo, so it
    /// reads as their picture living there and not as an advert.
    private var widget: some View {
        let widgetWidth = 2 * cell + columnGap
        let widgetHeight = 2 * rowPitch - rowGap
        let shape = RoundedRectangle(cornerRadius: widgetWidth * 0.2, style: .continuous)
        return Image("Onboarding-photo")
            .resizable()
            .scaledToFill()
            .frame(width: widgetWidth, height: widgetHeight)
            .clipShape(shape)
            .overlay { shape.strokeBorder(theme.colors.accent, lineWidth: max(1.2, 0.7 * unit)) }
            .offset(x: sidePadding + 2 * (cell + columnGap), y: gridTop + rowPitch)
    }

    private var pageDots: some View {
        HStack(spacing: 1.8 * unit) {
            ForEach(0..<3, id: \.self) { index in
                Circle()
                    .fill(Color.white.opacity(index == 0 ? 0.85 : 0.35))
                    .frame(width: 1.7 * unit, height: 1.7 * unit)
            }
        }
        .frame(width: screenWidth)
        .offset(y: screenHeight - 40 * unit)
    }

    private var dock: some View {
        let dockHeight = 22 * unit
        return RoundedRectangle(cornerRadius: 7.5 * unit, style: .continuous)
            .fill(Color.white.opacity(0.13))
            .frame(width: 100 * unit - 2 * 4.5 * unit, height: dockHeight)
            .overlay {
                HStack(spacing: columnGap) {
                    ForEach(0..<4, id: \.self) { index in
                        let tone = Self.iconTones[(index * 3 + 1) % Self.iconTones.count]
                        RoundedRectangle(cornerRadius: cell * 0.225, style: .continuous)
                            .fill(LinearGradient(colors: [Color(hex: tone.top), Color(hex: tone.bottom)], startPoint: .top, endPoint: .bottom))
                            .frame(width: cell, height: cell)
                    }
                }
            }
            .frame(width: screenWidth)
            .offset(y: screenHeight - dockHeight - 8 * unit)
    }

    private var homeIndicator: some View {
        Capsule()
            .fill(Color.white.opacity(0.85))
            .frame(width: 34 * unit, height: 1.3 * unit)
            .frame(width: screenWidth)
            .offset(y: screenHeight - 3.6 * unit)
    }
}

private extension Color {
    init(hex: UInt32) {
        self.init(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255, opacity: 1)
    }
}
