import SwiftUI

extension View {
    /// Fades and rises into place a moment after the screen opens, one piece after another
    /// (`order` 0 first). Flip `hasAppeared` to true in `onAppear`. With Reduce Motion on,
    /// everything is simply there.
    func appearing(order: Int, hasAppeared: Bool, reduceMotion: Bool) -> some View {
        opacity(hasAppeared || reduceMotion ? 1 : 0)
            .offset(y: hasAppeared || reduceMotion ? 0 : 16)
            .animation(
                reduceMotion ? nil : .spring(duration: 0.6, bounce: 0.12).delay(0.06 * Double(order)),
                value: hasAppeared
            )
    }
}
