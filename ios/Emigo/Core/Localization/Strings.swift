import Foundation

/// Every piece of user-visible text, by key. The English wording lives in
/// `Resources/Localizable.xcstrings`; nothing in the app types a sentence directly into a view.
/// In a view: `Text(Strings.Welcome.tagline)`. Where a plain `String` is needed (view models,
/// accessibility labels): `String(localized: Strings.Welcome.tagline)`.
enum Strings {
    enum Common {
        static let `continue` = LocalizedStringResource("common.continue")
        static let cancel = LocalizedStringResource("common.cancel")
        static let close = LocalizedStringResource("common.close")
        static let save = LocalizedStringResource("common.save")
        static let delete = LocalizedStringResource("common.delete")
        static let moreOptions = LocalizedStringResource("common.moreOptions")
        static let back = LocalizedStringResource("common.back")
        static let done = LocalizedStringResource("common.done")
    }

    enum Failure {
        static let somethingWentWrong = LocalizedStringResource("failure.somethingWentWrong")
        static let sessionExpired = LocalizedStringResource("failure.sessionExpired")
        static let noConnection = LocalizedStringResource("failure.noConnection")
        static let notConfigured = LocalizedStringResource("failure.notConfigured")
        static let createAccount = LocalizedStringResource("failure.createAccount")
        static let checkEmail = LocalizedStringResource("failure.checkEmail")
        static let checkUsername = LocalizedStringResource("failure.checkUsername")
        static let saveName = LocalizedStringResource("failure.saveName")
        static let saveUsername = LocalizedStringResource("failure.saveUsername")
        static let changePassword = LocalizedStringResource("failure.changePassword")
        static let updatePhoto = LocalizedStringResource("failure.updatePhoto")
        static let search = LocalizedStringResource("failure.search")
        static let sendRequest = LocalizedStringResource("failure.sendRequest")
        static let acceptRequest = LocalizedStringResource("failure.acceptRequest")
        static let declineRequest = LocalizedStringResource("failure.declineRequest")
        static let unblock = LocalizedStringResource("failure.unblock")
        static let loadFriends = LocalizedStringResource("failure.loadFriends")
        static let loadSent = LocalizedStringResource("failure.loadSent")
        static let unsend = LocalizedStringResource("failure.unsend")
        static let saveList = LocalizedStringResource("failure.saveList")
        static let deleteList = LocalizedStringResource("failure.deleteList")
        static let pin = LocalizedStringResource("failure.pin")
        static let removeFriend = LocalizedStringResource("failure.removeFriend")
        static let cancelRequest = LocalizedStringResource("failure.cancelRequest")
        static let block = LocalizedStringResource("failure.block")
        static let report = LocalizedStringResource("failure.report")
    }

    enum Identity {
        static let noAccount = LocalizedStringResource("identity.noAccount")
        static let emailInUse = LocalizedStringResource("identity.emailInUse")
        static let weakPassword = LocalizedStringResource("identity.weakPassword")
        static let badCredentials = LocalizedStringResource("identity.badCredentials")
        static let recentLoginRequired = LocalizedStringResource("identity.recentLoginRequired")
        static let tooManyRequests = LocalizedStringResource("identity.tooManyRequests")
    }

    enum Welcome {
        static let tagline = LocalizedStringResource("welcome.tagline")
        static let createAccount = LocalizedStringResource("welcome.createAccount")
        static let signIn = LocalizedStringResource("welcome.signIn")
    }

    enum Invite {
        static let title = LocalizedStringResource("invite.title")
        static let subtitle = LocalizedStringResource("invite.subtitle")
        static let sectionFrom = LocalizedStringResource("invite.sectionFrom")
        static let sectionLink = LocalizedStringResource("invite.sectionLink")
        static let copyTitle = LocalizedStringResource("invite.copyTitle")
        static let copySubtitle = LocalizedStringResource("invite.copySubtitle")
        static let copied = LocalizedStringResource("invite.copied")
        static let whatsappSubtitle = LocalizedStringResource("invite.whatsappSubtitle")
        static let instagramDMTitle = LocalizedStringResource("invite.instagramDMTitle")
        static let instagramDMSubtitle = LocalizedStringResource("invite.instagramDMSubtitle")
        static let instagramStoryTitle = LocalizedStringResource("invite.instagramStoryTitle")
        static let instagramStorySubtitle = LocalizedStringResource("invite.instagramStorySubtitle")
        static let messagesTitle = LocalizedStringResource("invite.messagesTitle")
        static let messagesSubtitle = LocalizedStringResource("invite.messagesSubtitle")
        static let more = LocalizedStringResource("invite.more")
        static let skip = LocalizedStringResource("invite.skip")
        static let tagline = LocalizedStringResource("invite.tagline")
    }

    enum Login {
        static let title = LocalizedStringResource("login.title")
        static let identifierHint = LocalizedStringResource("login.identifierHint")
        static let passwordHint = LocalizedStringResource("login.passwordHint")
        static let forgotPassword = LocalizedStringResource("login.forgotPassword")
        static let button = LocalizedStringResource("login.button")
        static let errorEmailTaken = LocalizedStringResource("login.errorEmailTaken")
        static let errorFillAll = LocalizedStringResource("login.errorFillAll")
        static let errorInvalidEmail = LocalizedStringResource("login.errorInvalidEmail")
        static let errorBadCredentials = LocalizedStringResource("login.errorBadCredentials")
        static let errorCheckDetails = LocalizedStringResource("login.errorCheckDetails")
        static let errorFirstName = LocalizedStringResource("login.errorFirstName")
    }

    enum Reset {
        static let title = LocalizedStringResource("reset.title")
        static let description = LocalizedStringResource("reset.description")
        static let emailHint = LocalizedStringResource("reset.emailHint")
        static let sent = LocalizedStringResource("reset.sent")
        static let sendButton = LocalizedStringResource("reset.sendButton")
    }

    enum Register {
        static let emailTitle = LocalizedStringResource("register.emailTitle")
        static let emailHint = LocalizedStringResource("register.emailHint")
        static let legalPrefix = LocalizedStringResource("register.legalPrefix")
        static let legalTerms = LocalizedStringResource("register.legalTerms")
        static let legalAnd = LocalizedStringResource("register.legalAnd")
        static let legalPrivacy = LocalizedStringResource("register.legalPrivacy")
        static let passwordTitle = LocalizedStringResource("register.passwordTitle")
        static let passwordRule = LocalizedStringResource("register.passwordRule")
        static let createAccountButton = LocalizedStringResource("register.createAccountButton")
        static let nameTitle = LocalizedStringResource("register.nameTitle")
        static let firstNameHint = LocalizedStringResource("register.firstNameHint")
        static let lastNameHint = LocalizedStringResource("register.lastNameHint")
        static let usernameTitle = LocalizedStringResource("register.usernameTitle")
        static let usernameHint = LocalizedStringResource("register.usernameHint")
        static let usernameChecking = LocalizedStringResource("register.usernameChecking")
        static let usernameAvailable = LocalizedStringResource("register.usernameAvailable")
        static let usernameTaken = LocalizedStringResource("register.usernameTaken")
        static let usernameTooShort = LocalizedStringResource("register.usernameTooShort")
        static let usernamePickAvailable = LocalizedStringResource("register.usernamePickAvailable")
    }

    enum Password {
        static let show = LocalizedStringResource("password.show")
        static let hide = LocalizedStringResource("password.hide")
    }

    enum Verify {
        static let title = LocalizedStringResource("verify.title")
        static let failedTitle = LocalizedStringResource("verify.failedTitle")
        static let sentDetail = LocalizedStringResource("verify.sentDetail")
        static let expiresIn = LocalizedStringResource("verify.expiresIn")
        static let expiredTitle = LocalizedStringResource("verify.expiredTitle")
        static let expiredDetail = LocalizedStringResource("verify.expiredDetail")
        static let doneButton = LocalizedStringResource("verify.doneButton")
        static let startOver = LocalizedStringResource("verify.startOver")
        static let resend = LocalizedStringResource("verify.resend")
        static let resendIn = LocalizedStringResource("verify.resendIn")
        static let resent = LocalizedStringResource("verify.resent")
        static let resendFailed = LocalizedStringResource("verify.resendFailed")
        static let stillUnverified = LocalizedStringResource("verify.stillUnverified")
    }

    enum Time {
        static let justNow = LocalizedStringResource("time.justNow")
        static let yesterday = LocalizedStringResource("time.yesterday")
        static let minutesAgo = LocalizedStringResource("time.minutesAgo")
        static let hoursAgo = LocalizedStringResource("time.hoursAgo")
        static let daysAgo = LocalizedStringResource("time.daysAgo")
        static let minutesLeft = LocalizedStringResource("time.minutesLeft")
        static let hoursLeft = LocalizedStringResource("time.hoursLeft")
        static let expiring = LocalizedStringResource("time.expiring")
    }

    enum Tab {
        static let memories = LocalizedStringResource("tab.memories")
        static let home = LocalizedStringResource("tab.home")
        static let camera = LocalizedStringResource("tab.camera")
        static let friends = LocalizedStringResource("tab.friends")
        static let settings = LocalizedStringResource("tab.settings")
    }

    enum Home {
        static let modeHome = LocalizedStringResource("home.modeHome")
        static let modeMoments = LocalizedStringResource("home.modeMoments")
        static let connectError = LocalizedStringResource("home.connectError")
        static let emptyWithFriends = LocalizedStringResource("home.emptyWithFriends")
        static let emptyNoFriends = LocalizedStringResource("home.emptyNoFriends")
        static let cardForEveryFriend = LocalizedStringResource("home.cardForEveryFriend")
        static let addFriendsPrompt = LocalizedStringResource("home.addFriendsPrompt")
        static let activity = LocalizedStringResource("home.activity")
        static let profile = LocalizedStringResource("home.profile")
        static let addFriend = LocalizedStringResource("home.addFriend")
        static let addShort = LocalizedStringResource("home.addShort")
        static let streakDays = LocalizedStringResource("home.streakDays")
    }

    enum Friends {
        static let title = LocalizedStringResource("friends.title")
        static let findPeople = LocalizedStringResource("friends.findPeople")
        static let searchHint = LocalizedStringResource("friends.searchHint")
        static let noMatch = LocalizedStringResource("friends.noMatch")
        static let sectionYourEmigo = LocalizedStringResource("friends.sectionYourEmigo")
        static let pinnedPartner = LocalizedStringResource("friends.pinnedPartner")
        static let sectionRequests = LocalizedStringResource("friends.sectionRequests")
        static let accept = LocalizedStringResource("friends.accept")
        static let decline = LocalizedStringResource("friends.decline")
        static let sectionMyFriends = LocalizedStringResource("friends.sectionMyFriends")
        static let empty = LocalizedStringResource("friends.empty")
        static let sentToYou = LocalizedStringResource("friends.sentToYou")
        static let youSent = LocalizedStringResource("friends.youSent")
        static let noPhotosYet = LocalizedStringResource("friends.noPhotosYet")
    }

    enum Activity {
        static let title = LocalizedStringResource("activity.title")
        static let today = LocalizedStringResource("activity.today")
        static let yesterday = LocalizedStringResource("activity.yesterday")
        static let emptyTitle = LocalizedStringResource("activity.emptyTitle")
        static let emptyDetail = LocalizedStringResource("activity.emptyDetail")
    }

    enum Profile {
        static let sectionAccount = LocalizedStringResource("profile.sectionAccount")
        static let rowName = LocalizedStringResource("profile.rowName")
        static let rowUsername = LocalizedStringResource("profile.rowUsername")
        static let rowPassword = LocalizedStringResource("profile.rowPassword")
        static let rowPicture = LocalizedStringResource("profile.rowPicture")
        static let nameTitle = LocalizedStringResource("profile.nameTitle")
        static let nameSubtitle = LocalizedStringResource("profile.nameSubtitle")
        static let usernameTitle = LocalizedStringResource("profile.usernameTitle")
        static let usernameSubtitle = LocalizedStringResource("profile.usernameSubtitle")
        static let usernameAvailable = LocalizedStringResource("profile.usernameAvailable")
        static let usernameTaken = LocalizedStringResource("profile.usernameTaken")
        static let passwordSubtitle = LocalizedStringResource("profile.passwordSubtitle")
        static let currentPasswordHint = LocalizedStringResource("profile.currentPasswordHint")
        static let newPasswordHint = LocalizedStringResource("profile.newPasswordHint")
        static let confirmPasswordHint = LocalizedStringResource("profile.confirmPasswordHint")
        static let passwordChanged = LocalizedStringResource("profile.passwordChanged")
        static let errorNameEmpty = LocalizedStringResource("profile.errorNameEmpty")
        static let errorCurrentPassword = LocalizedStringResource("profile.errorCurrentPassword")
        static let errorNewPasswordShort = LocalizedStringResource("profile.errorNewPasswordShort")
        static let errorPasswordMismatch = LocalizedStringResource("profile.errorPasswordMismatch")
    }

    enum FindPeople {
        static let searchHint = LocalizedStringResource("findPeople.searchHint")
        static let intro = LocalizedStringResource("findPeople.intro")
        static let invitePrompt = LocalizedStringResource("findPeople.invitePrompt")
        static let add = LocalizedStringResource("findPeople.add")
        static let requested = LocalizedStringResource("findPeople.requested")
        static let more = LocalizedStringResource("findPeople.more")
        static let inviteMessage = LocalizedStringResource("findPeople.inviteMessage")
        static let inviteMessageWithUsername = LocalizedStringResource("findPeople.inviteMessageWithUsername")
    }

    enum Blocked {
        static let title = LocalizedStringResource("blocked.title")
        static let subtitle = LocalizedStringResource("blocked.subtitle")
        static let empty = LocalizedStringResource("blocked.empty")
        static let unblock = LocalizedStringResource("blocked.unblock")
    }

    enum FriendProfile {
        static let sendPhoto = LocalizedStringResource("friendProfile.sendPhoto")
        static let pinAsPartner = LocalizedStringResource("friendProfile.pinAsPartner")
        static let pinnedAsPartner = LocalizedStringResource("friendProfile.pinnedAsPartner")
        static let cancelRequest = LocalizedStringResource("friendProfile.cancelRequest")
        static let unfriend = LocalizedStringResource("friendProfile.unfriend")
        static let unfriendTitle = LocalizedStringResource("friendProfile.unfriendTitle")
        static let unfriendWarning = LocalizedStringResource("friendProfile.unfriendWarning")
        static let block = LocalizedStringResource("friendProfile.block")
        static let blockTitle = LocalizedStringResource("friendProfile.blockTitle")
        static let blockWarning = LocalizedStringResource("friendProfile.blockWarning")
        static let report = LocalizedStringResource("friendProfile.report")
    }

    enum Report {
        static let title = LocalizedStringResource("report.title")
        static let submittedTitle = LocalizedStringResource("report.submittedTitle")
        static let thanks = LocalizedStringResource("report.thanks")
        static let question = LocalizedStringResource("report.question")
        static let submit = LocalizedStringResource("report.submit")
        static let reasonSpam = LocalizedStringResource("report.reasonSpam")
        static let reasonHarassment = LocalizedStringResource("report.reasonHarassment")
        static let reasonInappropriate = LocalizedStringResource("report.reasonInappropriate")
        static let reasonFakeAccount = LocalizedStringResource("report.reasonFakeAccount")
        static let reasonOther = LocalizedStringResource("report.reasonOther")
    }

    enum Memories {
        static let title = LocalizedStringResource("memories.title")
        static let empty = LocalizedStringResource("memories.empty")
        static let emptyDetail = LocalizedStringResource("memories.emptyDetail")
        static let photoDescription = LocalizedStringResource("memories.photoDescription")
    }

    enum Camera {
        static let takePhoto = LocalizedStringResource("camera.takePhoto")
        static let send = LocalizedStringResource("camera.send")
        static let retake = LocalizedStringResource("camera.retake")
        static let pickFromGallery = LocalizedStringResource("camera.pickFromGallery")
        static let flip = LocalizedStringResource("camera.flip")
        static let sentPhotos = LocalizedStringResource("camera.sentPhotos")
        static let chooseRecipients = LocalizedStringResource("camera.chooseRecipients")
        static let permissionNeeded = LocalizedStringResource("camera.permissionNeeded")
        static let noCamera = LocalizedStringResource("camera.noCamera")
        static let openSettings = LocalizedStringResource("camera.openSettings")
        static let flashOn = LocalizedStringResource("camera.flashOn")
        static let flashOff = LocalizedStringResource("camera.flashOff")
        static let addText = LocalizedStringResource("camera.addText")
        static let saveToMemories = LocalizedStringResource("camera.saveToMemories")
        static let savedToMemories = LocalizedStringResource("camera.savedToMemories")
        static let captureFailed = LocalizedStringResource("camera.captureFailed")
        static let errorSelectFriend = LocalizedStringResource("camera.errorSelectFriend")
        static let errorQueue = LocalizedStringResource("camera.errorQueue")
        static let errorSave = LocalizedStringResource("camera.errorSave")
        static let goldTitle = LocalizedStringResource("camera.goldTitle")
        static let goldPerk = LocalizedStringResource("camera.goldPerk")
        static let goldNotOnIPhone = LocalizedStringResource("camera.goldNotOnIPhone")
        static let maybeLater = LocalizedStringResource("camera.maybeLater")
    }

    enum Recipients {
        static let title = LocalizedStringResource("recipients.title")
        static let badgeRecent = LocalizedStringResource("recipients.badgeRecent")
        static let badgeEveryone = LocalizedStringResource("recipients.badgeEveryone")
        static let createList = LocalizedStringResource("recipients.createList")
        static let nameListHint = LocalizedStringResource("recipients.nameListHint")
        static let deleteListTitle = LocalizedStringResource("recipients.deleteListTitle")
        static let addFriendsFirst = LocalizedStringResource("recipients.addFriendsFirst")
        static let findFriends = LocalizedStringResource("recipients.findFriends")
        static let emptyList = LocalizedStringResource("recipients.emptyList")
    }

    enum Sent {
        static let title = LocalizedStringResource("sent.title")
        static let intro = LocalizedStringResource("sent.intro")
        static let empty = LocalizedStringResource("sent.empty")
        static let timeDescription = LocalizedStringResource("sent.timeDescription")
        static let remainingToUnsend = LocalizedStringResource("sent.remainingToUnsend")
        static let unsend = LocalizedStringResource("sent.unsend")
        static let unsendTitle = LocalizedStringResource("sent.unsendTitle")
        static let unsendWarning = LocalizedStringResource("sent.unsendWarning")
    }

    enum Settings {
        static let title = LocalizedStringResource("settings.title")
        static let defaultAccountName = LocalizedStringResource("settings.defaultAccountName")
        static let sectionPrivacy = LocalizedStringResource("settings.sectionPrivacy")
        static let blockedAccounts = LocalizedStringResource("settings.blockedAccounts")
        static let privacyPolicy = LocalizedStringResource("settings.privacyPolicy")
        static let sectionSupport = LocalizedStringResource("settings.sectionSupport")
        static let helpSupport = LocalizedStringResource("settings.helpSupport")
        static let sendFeedback = LocalizedStringResource("settings.sendFeedback")
        static let aboutEmigo = LocalizedStringResource("settings.aboutEmigo")
        static let supportEmailSubject = LocalizedStringResource("settings.supportEmailSubject")
        static let feedbackEmailSubject = LocalizedStringResource("settings.feedbackEmailSubject")
        static let logOut = LocalizedStringResource("settings.logOut")
        static let logOutConfirmTitle = LocalizedStringResource("settings.logOutConfirmTitle")
        static let gold = LocalizedStringResource("settings.gold")
        static let badgeGold = LocalizedStringResource("settings.badgeGold")
        static let badgeFree = LocalizedStringResource("settings.badgeFree")
        static let sectionPreferences = LocalizedStringResource("settings.sectionPreferences")
        static let notifications = LocalizedStringResource("settings.notifications")
        static let notificationsDeniedTitle = LocalizedStringResource("settings.notificationsDeniedTitle")
        static let notificationsDeniedMessage = LocalizedStringResource("settings.notificationsDeniedMessage")
        static let openSystemSettings = LocalizedStringResource("settings.openSystemSettings")
        static let appearance = LocalizedStringResource("settings.appearance")
        static let other = LocalizedStringResource("settings.other")
    }

    enum Theme {
        static let title = LocalizedStringResource("theme.title")
        static let subtitle = LocalizedStringResource("theme.subtitle")
        static let apply = LocalizedStringResource("theme.apply")
        static let applied = LocalizedStringResource("theme.applied")
        static let getGold = LocalizedStringResource("theme.getGold")
        static let badgeDefault = LocalizedStringResource("theme.badgeDefault")
        static let nameCream = LocalizedStringResource("theme.nameCream")
        static let nameDusk = LocalizedStringResource("theme.nameDusk")
        static let nameBlaze = LocalizedStringResource("theme.nameBlaze")
        static let nameNoir = LocalizedStringResource("theme.nameNoir")
        static let nameAurora = LocalizedStringResource("theme.nameAurora")
        static let nameCyber = LocalizedStringResource("theme.nameCyber")
        static let nameBotanica = LocalizedStringResource("theme.nameBotanica")
        static let nameEmber = LocalizedStringResource("theme.nameEmber")
        static let nameFrost = LocalizedStringResource("theme.nameFrost")
    }

    enum Gold {
        static let title = LocalizedStringResource("gold.title")
        static let subtitleMember = LocalizedStringResource("gold.subtitleMember")
        static let subtitleVisitor = LocalizedStringResource("gold.subtitleVisitor")
        static let perkRestoreStreakTitle = LocalizedStringResource("gold.perkRestoreStreakTitle")
        static let perkRestoreStreakDetail = LocalizedStringResource("gold.perkRestoreStreakDetail")
        static let perkThemesTitle = LocalizedStringResource("gold.perkThemesTitle")
        static let perkThemesDetail = LocalizedStringResource("gold.perkThemesDetail")
        static let perkGalleryTitle = LocalizedStringResource("gold.perkGalleryTitle")
        static let perkGalleryDetail = LocalizedStringResource("gold.perkGalleryDetail")
        static let perkWidgetTitle = LocalizedStringResource("gold.perkWidgetTitle")
        static let perkWidgetDetail = LocalizedStringResource("gold.perkWidgetDetail")
        static let memberLabel = LocalizedStringResource("gold.memberLabel")
        static let memberStatus = LocalizedStringResource("gold.memberStatus")
        static let manage = LocalizedStringResource("gold.manage")
        static let captionCancelAnytime = LocalizedStringResource("gold.captionCancelAnytime")
        static let unavailable = LocalizedStringResource("gold.unavailable")
        static let alreadyMember = LocalizedStringResource("gold.alreadyMember")
    }

    enum DeleteAccount {
        static let row = LocalizedStringResource("deleteAccount.row")
        static let title = LocalizedStringResource("deleteAccount.title")
        static let warning = LocalizedStringResource("deleteAccount.warning")
        static let confirmPrompt = LocalizedStringResource("deleteAccount.confirmPrompt")
        static let failed = LocalizedStringResource("deleteAccount.failed")
    }
}
