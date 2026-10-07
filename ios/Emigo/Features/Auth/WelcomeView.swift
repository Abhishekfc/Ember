import SwiftUI

/// The first screen a signed-out person sees: an iPhone with a friend's photo on its home screen,
/// the name, and the two ways in. The phone runs down behind the text and fades into the black,
/// so the picture and the words feel like one piece.
struct WelcomeView: View {
    let model: AuthFlowViewModel

    @Environment(\.theme) private var theme
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var hasAppeared = false
    @State private var isFloating = false

    var body: some View {
        GeometryReader { proxy in
            let phoneWidth = min(proxy.size.width * 0.66, 300)
            ZStack(alignment: .bottom) {
                phone(width: phoneWidth)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                    .padding(.top, Spacing.s)

                bottomPanel
            }
        }
        .background(theme.colors.background.ignoresSafeArea())
        .navigationBarHidden(true)
        .onAppear {
            hasAppeared = true
            guard !reduceMotion else { return }
            withAnimation(.easeInOut(duration: 3.4).repeatForever(autoreverses: true)) { isFloating = true }
        }
    }

    // MARK: - Pieces

    /// Tilted a little, like a phone set down on a table, drifting up and down very slowly, and
    /// fading out toward the bottom where the words begin.
    private func phone(width: CGFloat) -> some View {
        PhoneMockup(width: width)
            .mask(
                LinearGradient(
                    stops: [
                        .init(color: .black, location: 0),
                        .init(color: .black, location: 0.55),
                        .init(color: .clear, location: 0.93),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .rotationEffect(.degrees(-4))
            .offset(y: isFloating ? 5 : -5)
            .scaleEffect(hasAppeared || reduceMotion ? 1 : 0.94)
            .offset(y: hasAppeared || reduceMotion ? 0 : 36)
            .opacity(hasAppeared || reduceMotion ? 1 : 0)
            .animation(reduceMotion ? nil : .spring(duration: 0.9, bounce: 0.2), value: hasAppeared)
    }

    private var bottomPanel: some View {
        VStack(spacing: 0) {
            Text(verbatim: "Emigo")
                .font(EmigoFont.wordmark(size: 46))
                .foregroundStyle(theme.colors.cream)
                .appearing(order: 2, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

            Text(Strings.Welcome.tagline)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(theme.colors.muted)
                .multilineTextAlignment(.center)
                .padding(.top, Spacing.xs)
                .appearing(order: 3, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

            AuthPrimaryButton(title: Strings.Welcome.createAccount) {
                model.startRegistration()
            }
            .padding(.top, Spacing.xl)
            .appearing(order: 4, hasAppeared: hasAppeared, reduceMotion: reduceMotion)

            Button(action: model.startSignIn) {
                Text(Strings.Welcome.signIn)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(theme.colors.cream)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .contentShape(Rectangle())
            }
            .buttonStyle(AuthPressStyle())
            .padding(.top, Spacing.xxs)
            .appearing(order: 5, hasAppeared: hasAppeared, reduceMotion: reduceMotion)
        }
        .padding(.horizontal, Spacing.xl)
        .padding(.bottom, Spacing.xs)
    }
}
