import CoreGraphics

/// Spacing scale (points). Use these instead of loose numbers so layouts stay consistent.
enum Spacing {
    static let xxs: CGFloat = 4
    static let xs: CGFloat = 8
    static let s: CGFloat = 12
    static let m: CGFloat = 16
    static let l: CGFloat = 20
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    static let huge: CGFloat = 48
}

/// Corner radii. All used with `.continuous` corners, the smooth curve iOS uses everywhere.
enum Radius {
    static let field: CGFloat = 14
    static let largeField: CGFloat = 18
    static let button: CGFloat = 16
    static let card: CGFloat = 28
    static let thumbnail: CGFloat = 10
}

/// Fixed control sizes.
enum Size {
    static let buttonHeight: CGFloat = 50
    static let fieldHeight: CGFloat = 50
    static let largeFieldHeight: CGFloat = 60
    static let tabBarHeight: CGFloat = 66
    static let cameraButton: CGFloat = 52
    static let avatarSmall: CGFloat = 36
    /// The friend row on Home: the selected friend is a touch larger than the rest.
    static let avatarActive: CGFloat = 76
    static let avatarInactive: CGFloat = 72
    /// Height of that whole row, avatars plus the first names under them.
    static let friendRowHeight: CGFloat = 104
    /// Home's photo card, same as Android: 18-point side margins, width : height = 4 : 5.
    static let cardSidePadding: CGFloat = 18
    static let cardAspectRatio: CGFloat = 0.8
}
