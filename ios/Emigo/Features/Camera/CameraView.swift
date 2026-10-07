import PhotosUI
import SwiftUI

/// The Camera tab: the live camera in a card the same size as Home's, with the gallery, shutter
/// and flip below it. After a photo is taken the card shows it (with room for a caption) and the
/// controls become Send and Retake. Same layout as Android's.
struct CameraView: View {
    @Bindable var model: CameraViewModel
    /// True while the Camera tab is the one showing; the live preview only runs then.
    let isActive: Bool
    let onOpenRecipients: () -> Void
    let onOpenSentPhotos: () -> Void

    @Environment(\.theme) private var theme
    @Environment(\.scenePhase) private var scenePhase
    @State private var pickerItem: PhotosPickerItem?
    @State private var showPicker = false

    var body: some View {
        VStack(spacing: 0) {
            header
            // The card starts at the same height as Home's, so switching tabs doesn't make it jump.
            Color.clear.frame(height: Spacing.m + 31 + Spacing.l)
            cardAndControls
        }
        .photosPicker(isPresented: $showPicker, selection: $pickerItem, matching: .images)
        .onChange(of: pickerItem) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self) {
                    await model.didCapture(CapturedPhoto(jpeg: data, isFrontCamera: false))
                }
                pickerItem = nil
            }
        }
        .sheet(isPresented: $model.showGoldUpsell) { GoldUpsellSheet() }
        .alert(model.errorMessage ?? "", isPresented: Binding(
            get: { model.errorMessage != nil },
            set: { if !$0 { model.dismissError() } }
        )) {
            Button(role: .cancel) {} label: { Text(Strings.Common.close) }
        }
        .task { await model.refresh() }
        .task { await model.engine.requestAccessIfNeeded() }
        .onChange(of: isActive, initial: true) { _, active in updateSession(active: active) }
        .onChange(of: scenePhase) { _, _ in updateSession(active: isActive) }
        .onChange(of: model.engine.availability) { _, _ in updateSession(active: isActive) }
        .ignoresSafeArea(.keyboard)
    }

    /// Runs the camera only while the Camera tab is showing, so its light goes off when you leave
    /// it. It keeps running while a photo is being looked at, so Retake is instant and nothing
    /// goes black in between.
    private func updateSession(active: Bool) {
        if active && scenePhase == .active {
            model.engine.start()
        } else {
            model.engine.stop()
        }
    }

    // MARK: - Header

    private var header: some View {
        ZStack {
            RecipientChip(friends: model.selectedFriends, hasPinned: model.hasPinnedSelected, action: onOpenRecipients)
            HStack {
                OutboxButton(state: model.sendAnimState, lastSentURL: model.lastSentPhotoURL, action: onOpenSentPhotos)
                Spacer()
                if model.isReviewing { saveButton }
            }
        }
        .padding(.horizontal, Spacing.m)
        .frame(height: 36)
    }

    private var saveButton: some View {
        Button {
            Task { await model.saveToMemories() }
        } label: {
            ZStack {
                Image(systemName: model.isSaved ? "bookmark.fill" : "bookmark")
                    .font(.system(size: 22, weight: .regular))
                    .foregroundStyle(.white)
                    .contentTransition(.symbolEffect(.replace))
                if model.isSavingToMemories { ProgressView().tint(.white).controlSize(.small) }
            }
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(model.isSaved || model.isSavingToMemories)
        .accessibilityLabel(Text(model.isSaved ? Strings.Camera.savedToMemories : Strings.Camera.saveToMemories))
    }

    // MARK: - Card and controls

    private var cardAndControls: some View {
        GeometryReader { proxy in
            let controlsHeight: CGFloat = 84 + Spacing.l
            let fullWidth = proxy.size.width - 2 * Size.cardSidePadding
            let height = max(min(fullWidth / Size.cardAspectRatio, proxy.size.height - controlsHeight), 0)
            let width = height * Size.cardAspectRatio

            VStack(spacing: Spacing.l) {
                CameraCard(model: model, isActive: isActive)
                    .frame(width: width, height: height)
                controls
                    .frame(height: 84)
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity)
        }
    }

    @ViewBuilder
    private var controls: some View {
        if model.isReviewing {
            HStack(spacing: 40) {
                Color.clear.frame(width: 56, height: 56)
                SendPhotoButton(canSend: model.hasRecipients, isSending: model.isQueuingSend) {
                    Task { await model.sendCaptured() }
                }
                CameraGlyphButton(symbol: "arrow.counterclockwise", label: Strings.Camera.retake, caption: Strings.Camera.retake) {
                    model.discardCapture()
                }
                .disabled(model.isQueuingSend)
            }
            .transition(.opacity)
        } else {
            HStack(spacing: 40) {
                CameraGlyphButton(symbol: "photo", label: Strings.Camera.pickFromGallery) {
                    model.galleryTapped { showPicker = true }
                }
                ShutterButton(isEnabled: model.engine.availability == .authorized || model.engine.availability == .unavailable) {
                    Task { await takePhoto() }
                }
                CameraGlyphButton(symbol: "arrow.triangle.2.circlepath.camera", label: Strings.Camera.flip) {
                    model.engine.flip()
                }
            }
            .transition(.opacity)
        }
    }

    private func takePhoto() async {
        guard !model.isTakingPhoto else { return }
        model.isTakingPhoto = true
        defer { model.isTakingPhoto = false }
        do {
            let photo = try await model.engine.capture()
            await model.didCapture(photo)
        } catch {
            model.captureFailed()
        }
    }
}

/// The card itself: live camera, or the photo just taken with a caption on it.
private struct CameraCard: View {
    @Bindable var model: CameraViewModel
    let isActive: Bool

    @Environment(\.theme) private var theme
    @State private var isEditingCaption = false
    @State private var focusPoint: CGPoint?
    @State private var showsZoom = false
    @FocusState private var captionFocused: Bool

    var body: some View {
        GeometryReader { proxy in
            ZStack {
                Color.black
                if let image = model.previewImage {
                    review(image: image, cardWidth: proxy.size.width, cardHeight: proxy.size.height)
                } else {
                    liveCamera
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 30, style: .continuous))
        }
    }

    // MARK: Live camera

    @ViewBuilder
    private var liveCamera: some View {
        switch model.engine.availability {
        case .authorized:
            ZStack {
                CameraPreview(
                    session: model.engine.session,
                    isFrozen: model.isTakingPhoto,
                    onPinchBegan: { model.engine.beginPinch(); showZoom() },
                    onPinchChanged: { model.engine.updatePinch(scale: $0); showZoom() },
                    onTap: { devicePoint, viewPoint in
                        model.engine.focus(atDevicePoint: devicePoint)
                        showFocus(at: viewPoint)
                    }
                )
                // Black until the camera has settled, then a soft fade to the picture, instead of
                // the dark-or-washed-out first frames showing as a blink.
                Color.black
                    .opacity(model.engine.isPreviewLive ? 0 : 1)
                    .animation(.easeOut(duration: 0.2), value: model.engine.isPreviewLive)
                    .allowsHitTesting(false)
                if let focusPoint {
                    FocusRing().position(focusPoint).transition(.scale(scale: 1.4).combined(with: .opacity))
                }
                VStack {
                    HStack {
                        if model.engine.canUseFlash { flashButton }
                        Spacer()
                    }
                    Spacer()
                }
                .padding(Spacing.s)
                if showsZoom {
                    VStack { ZoomBadge(factor: model.engine.zoomFactor).padding(.top, Spacing.s); Spacer() }
                        .transition(.opacity)
                }
            }
        case .unknown:
            Color.clear
        case .denied:
            CameraUnavailableView(reason: .denied)
        case .unavailable:
            #if DEBUG
            SimulatedPreview()
            #else
            CameraUnavailableView(reason: .noCamera)
            #endif
        }
    }

    private var flashButton: some View {
        Button {
            model.engine.toggleFlash()
        } label: {
            Image(systemName: model.engine.isFlashOn ? "bolt.fill" : "bolt.slash.fill")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(model.engine.isFlashOn ? theme.colors.accent : .white)
                .frame(width: 36, height: 36)
                .liquidGlass(in: Circle(), interactive: true)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(model.engine.isFlashOn ? Strings.Camera.flashOff : Strings.Camera.flashOn))
    }

    private func showFocus(at point: CGPoint) {
        withAnimation(.spring(duration: 0.25, bounce: 0.3)) { focusPoint = point }
        Task {
            try? await Task.sleep(for: .milliseconds(900))
            withAnimation(.easeOut(duration: 0.3)) { focusPoint = nil }
        }
    }

    private func showZoom() {
        withAnimation(.easeOut(duration: 0.15)) { showsZoom = true }
        Task {
            try? await Task.sleep(for: .milliseconds(900))
            withAnimation(.easeOut(duration: 0.3)) { showsZoom = false }
        }
    }

    // MARK: Review

    private func review(image: UIImage, cardWidth: CGFloat, cardHeight: CGFloat) -> some View {
        // The caption is sized from the card's width, the way it's sized from the photo's width
        // when it is drawn into the picture, so what you see is what gets sent.
        let fontSize = cardWidth * 0.045
        return ZStack {
            Image(uiImage: image)
                .resizable()
                .scaledToFill()
                .frame(width: cardWidth, height: cardHeight)
                .clipped()

            captionView(fontSize: fontSize, cardWidth: cardWidth, cardHeight: cardHeight)

            if !isEditingCaption {
                VStack {
                    HStack {
                        Spacer()
                        Button { beginCaption() } label: {
                            Image(systemName: "textformat")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 40, height: 40)
                                .liquidGlass(in: Circle(), interactive: true)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text(Strings.Camera.addText))
                    }
                    Spacer()
                }
                .padding(Spacing.s)
            }
        }
        .transition(.opacity)
    }

    /// The caption bar: full width, dark, centred 72% of the way down, like the one drawn into the
    /// sent picture. Tap it to edit.
    @ViewBuilder
    private func captionView(fontSize: CGFloat, cardWidth: CGFloat, cardHeight: CGFloat) -> some View {
        let center = CGPoint(x: cardWidth / 2, y: cardHeight * PhotoBaker.captionCenterFraction)
        if isEditingCaption {
            TextField("", text: $model.captionText, axis: .vertical)
                .focused($captionFocused)
                .submitLabel(.done)
                .onSubmit { isEditingCaption = false }
                .font(.system(size: fontSize, weight: .medium))
                .multilineTextAlignment(.center)
                .foregroundStyle(.white)
                .tint(theme.colors.accent)
                .padding(.horizontal, 18)
                .padding(.vertical, 10)
                .frame(width: cardWidth)
                .background(Color.black.opacity(0.59))
                .position(center)
        } else if !model.captionText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            Text(model.captionText)
                .font(.system(size: fontSize, weight: .medium))
                .multilineTextAlignment(.center)
                .foregroundStyle(.white)
                .padding(.horizontal, 18)
                .padding(.vertical, 10)
                .frame(width: cardWidth)
                .background(Color.black.opacity(0.59))
                .onTapGesture { beginCaption() }
                .position(center)
        }
    }

    private func beginCaption() {
        isEditingCaption = true
        captionFocused = true
    }
}

#if DEBUG
/// What the card shows where there is no camera (the simulator).
private struct SimulatedPreview: View {
    @Environment(\.theme) private var theme

    var body: some View {
        ZStack {
            LinearGradient(colors: [Color(white: 0.16), Color(white: 0.06)], startPoint: .top, endPoint: .bottom)
            Text(verbatim: "Simulated camera")
                .font(.system(size: 13))
                .foregroundStyle(theme.colors.mutedDim)
        }
    }
}
#endif
