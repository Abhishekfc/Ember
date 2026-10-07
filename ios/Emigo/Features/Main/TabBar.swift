import SwiftUI

/// The dock along the bottom: a floating Liquid Glass pill that photos and lists scroll beneath.
///
/// It works like Apple's own tab bar. Tap a tab to go there. Or touch and hold: the bubble under
/// the chosen tab swells (taller and wider than the dock itself), follows your finger as you slide
/// across the tabs with a tick for each one you pass, and when you let go it settles on the tab
/// it's over.
struct TabBar: View {
    @Binding var selection: AppTab

    @Environment(\.theme) private var theme
    /// Where the finger is, across the dock's width, while it's down. Nil when it isn't.
    @State private var fingerX: CGFloat?
    @State private var dockWidth: CGFloat = 0

    /// Space around the tabs, inside the dock.
    private let inset: CGFloat = 6
    private var tabs: [AppTab] { AppTab.allCases }
    private var isPressing: Bool { fingerX != nil }
    private var slotWidth: CGFloat { max(0, dockWidth - 2 * inset) / CGFloat(tabs.count) }

    /// The bubble, filling a tab's slot. It keeps this size whether or not the dock is held.
    private var lensSize: CGSize {
        CGSize(width: max(0, slotWidth - 4), height: Size.tabBarHeight - 12)
    }

    /// How much bigger the whole dock gets while a finger is on it.
    private let heldDockScale: CGFloat = 1.07


    /// The tab under the finger while pressing, else the chosen one.
    private var highlightedTab: AppTab {
        guard let fingerX else { return selection }
        return tab(atX: fingerX)
    }

    var body: some View {
        ZStack(alignment: .leading) {
            lens

            HStack(spacing: 0) {
                ForEach(tabs) { tab in
                    icon(for: tab)
                        .frame(maxWidth: .infinity)
                        .frame(height: Size.tabBarHeight - 2 * inset)
                        .accessibilityElement()
                        .accessibilityLabel(Text(tab.title))
                        .accessibilityAddTraits(.isButton)
                        .accessibilityAddTraits(selection == tab ? .isSelected : [])
                        .accessibilityAction { selection = tab }
                }
            }
            .padding(.horizontal, inset)
        }
        .frame(height: Size.tabBarHeight)
        .background {
            GeometryReader { proxy in
                Color.clear
                    .onAppear { dockWidth = proxy.size.width }
                    .onChange(of: proxy.size.width) { _, width in dockWidth = width }
            }
        }
        // The dock's glass sits behind everything above, so the icons stay sharp.
        .liquidGlass(in: Capsule())
        .contentShape(Capsule())
        // While a finger is on it the whole dock swells, then settles back when you let go.
        .scaleEffect(isPressing ? heldDockScale : 1)
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in fingerX = value.location.x }
                .onEnded { value in
                    let target = tab(atX: value.location.x)
                    fingerX = nil
                    withAnimation(.spring(duration: 0.5, bounce: 0.35)) { selection = target }
                }
        )
        .animation(.spring(duration: 0.4, bounce: 0.35), value: isPressing)
        .padding(.horizontal, Spacing.xl)
        // Negative on purpose: sits the dock down into the bottom safe area, about 22 points above
        // the screen edge, so it hugs the bottom like Android's instead of floating high.
        .padding(.bottom, -Spacing.s)
        .sensoryFeedback(.selection, trigger: highlightedTab)
        .sensoryFeedback(.impact(weight: .medium), trigger: isPressing) { _, pressing in pressing }
        #if DEBUG
        // `-EmigoDockHold 3` starts with a finger already held on the 4th tab, so the held look
        // can be checked without touching the screen.
        .task(id: dockWidth) {
            if dockWidth > 0, let held = UserDefaults.standard.string(forKey: "EmigoDockHold").flatMap({ Int($0) }) {
                fingerX = inset + slotWidth * (CGFloat(held) + 0.5)
            }
        }
        #endif
    }

    // MARK: - The bubble

    private var lens: some View {
        let pressed = isPressing
        let size = lensSize
        // The camera is its own bright button; the bubble only shows there while it's being dragged.
        let isHidden = !pressed && selection == .camera
        let motion: Animation = pressed
            ? .interactiveSpring(response: 0.2, dampingFraction: 0.78)
            : .spring(duration: 0.5, bounce: 0.35)

        return LensShape(isHeld: pressed)
            .frame(width: size.width, height: size.height)
            .opacity(isHidden ? 0 : 1)
            .offset(x: inset + lensCenterX - size.width / 2)
            .animation(motion, value: lensCenterX)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }

    /// Where the bubble is centred, measured from the left of the tab area: behind the chosen tab
    /// at rest, under the finger while pressed (kept inside the dock).
    private var lensCenterX: CGFloat {
        let restingX = slotWidth * (CGFloat(selection.rawValue) + 0.5)
        guard let fingerX else { return restingX }
        let half = lensSize.width / 2
        let furthest = max(half, slotWidth * CGFloat(tabs.count) - half)
        return min(max(fingerX - inset, half), furthest)
    }

    // MARK: - Icons

    @ViewBuilder
    private func icon(for tab: AppTab) -> some View {
        let isHighlighted = highlightedTab == tab
        if tab == .camera {
            Image(systemName: "camera")
                .font(.system(size: 19, weight: .semibold))
                .foregroundStyle(theme.colors.accentText)
                .frame(width: Size.cameraButton, height: Size.cameraButton)
                .liquidGlass(in: Circle(), tint: theme.colors.accent)
        } else {
            Image(systemName: isHighlighted ? tab.selectedSymbol : tab.symbol)
                .font(.system(size: 20, weight: isHighlighted ? .semibold : .regular))
                // White whether chosen or not, like Instagram's: the chosen tab shows as a filled
                // icon (and the bubble), the others as outlines. Grey icons made the bar look dull.
                .foregroundStyle(theme.colors.cream)
                .animation(.easeOut(duration: 0.15), value: isHighlighted)
        }
    }

    // MARK: - Hit testing

    /// The tab whose slot contains `x` (measured across the whole dock, padding included).
    private func tab(atX x: CGFloat) -> AppTab {
        guard slotWidth > 0 else { return selection }
        let index = Int(((x - inset) / slotWidth).rounded(.down))
        return tabs[min(max(index, 0), tabs.count - 1)]
    }
}

/// The bubble that marks the chosen tab. A soft light glass pill, a little lighter than the dock so
/// it shows, with a thin light edge: real Liquid Glass on iOS 26 and later (the picture behind it
/// bends through it), brighter while held; a light translucent capsule before that.
private struct LensShape: View {
    let isHeld: Bool

    var body: some View {
        if #available(iOS 26.0, *) {
            Capsule().fill(Color.white.opacity(isHeld ? 0.20 : 0.15))
                .liquidGlass(in: Capsule(), tint: Color.white.opacity(isHeld ? 0.10 : 0.06))
                .overlay(Capsule().strokeBorder(Color.white.opacity(isHeld ? 0.34 : 0.24), lineWidth: 0.75))
        } else {
            Capsule()
                .fill(Color.white.opacity(isHeld ? 0.30 : 0.20))
                .overlay(Capsule().strokeBorder(Color.white.opacity(isHeld ? 0.42 : 0.26), lineWidth: 0.75))
        }
    }
}
