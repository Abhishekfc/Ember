package com.emigo.app.widget

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.emigo.app.MainActivity
import com.emigo.app.R
import com.google.firebase.auth.FirebaseAuth
import java.io.File

/** The home-screen widget: shows the single most recent photo any friend has sent, matching the
 * "latest photo, glowing on your home screen" pitch from the login screen. Purely a renderer over
 * whatever [WidgetPhotoSync] last cached — it does no fetching of its own. */
class EmberWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Nobody signed in means no photo, whatever is still saved: a second safety on top of
        // WidgetSession clearing the saved copy, so a friend's private photo can never be drawn
        // for an account that is gone, even if some path forgot to clear it.
        val state = if (FirebaseAuth.getInstance().currentUser == null) null else WidgetPhotoStore(context).current()
        val bitmap = state
            ?.let { File(it.localFilePath).takeIf { file -> file.exists() } }
            ?.let { BitmapFactory.decodeFile(it.absolutePath) }
        val snapshot = if (state != null && bitmap != null) state to bitmap else null

        // Same effective-filter logic WidgetPhotoSync uses (cached Gold status, never a live
        // check here either) — purely to pick the right empty-state copy below; an empty widget
        // because a Gold subscriber's chosen friends haven't sent anything yet is a different,
        // more specific situation than a brand new account that's never opened the app.
        val preferenceStore = WidgetPreferenceStore(context)
        val hasFeaturedFriends = preferenceStore.cachedIsGoldMember() && preferenceStore.currentFeaturedFriendIds().isNotEmpty()

        provideContent {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(24.dp)
                    .background(Color(0xFF17181B))
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                if (snapshot != null) {
                    val (state, bitmap) = snapshot
                    Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = state.senderName,
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.fillMaxSize(),
                        )
                        // No scrim behind this any more, and no timestamp — just the sender's
                        // name sitting directly on the photo.
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(12.dp)) {
                            Text(
                                text = state.senderName,
                                style = TextStyle(
                                    color = ColorProvider(Color.White),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                ),
                            )
                        }
                    }
                } else {
                    EmptyState(hasFeaturedFriends = hasFeaturedFriends)
                }
            }
        }
    }

    @Composable
    private fun EmptyState(hasFeaturedFriends: Boolean) {
        Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    provider = ImageProvider(R.drawable.widget_empty_state),
                    contentDescription = null,
                    modifier = GlanceModifier.padding(bottom = 8.dp),
                )
                Text(
                    text = LocalContext.current.getString(
                        if (hasFeaturedFriends) R.string.widget_waiting_for_photo else R.string.widget_open_app,
                    ),
                    style = TextStyle(color = ColorProvider(Color(0xFFB9B2C9)), fontSize = 11.sp),
                )
            }
        }
    }
}
