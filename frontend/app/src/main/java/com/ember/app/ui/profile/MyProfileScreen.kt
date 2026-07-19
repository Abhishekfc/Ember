package com.ember.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ember.app.ui.components.cssAngleGradient
import com.ember.app.ui.theme.EmberTheme
import com.ember.app.ui.theme.PublicSansFontFamily
import java.io.File
import java.io.FileOutputStream

@Composable
fun MyProfileScreen(
    viewModel: MyProfileViewModel,
) {
    val colors = EmberTheme.colors
    val typography = EmberTheme.typography
    var screenSize by remember { mutableStateOf(Size.Zero) }
    val context = LocalContext.current
    val fieldShape = RoundedCornerShape(14.dp)
    val pillShape = RoundedCornerShape(16.dp)

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val file = File(context.cacheDir, "ember_profile_photo_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output -> input.copyTo(output) }
            }
            viewModel.uploadPhoto(file)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = Size(it.width.toFloat(), it.height.toFloat()) }
            .background(colors.background.asBrush(screenSize))
            .padding(top = 60.dp, start = 20.dp, end = 20.dp, bottom = 30.dp),
    ) {
        Text(
            text = "Profile",
            fontFamily = typography.display,
            fontSize = 22.sp,
            color = colors.cream,
            modifier = Modifier.padding(top = 16.dp),
        )

        Box(modifier = Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 8.dp), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(120.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colors.panel)
                        .border(1.dp, colors.border, CircleShape)
                        .clickable {
                            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val photoUrl = viewModel.profile?.profilePhotoUrl
                    if (photoUrl != null) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Your profile photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(
                            text = viewModel.profile?.displayName?.firstOrNull()?.uppercase() ?: "•",
                            fontFamily = typography.display,
                            fontSize = 38.sp,
                            color = colors.cream,
                        )
                    }
                    if (viewModel.isUploadingPhoto) {
                        Box(
                            modifier = Modifier.fillMaxSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = colors.glow, strokeWidth = 2.5.dp)
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colors.glow)
                        .border(2.dp, colors.panel, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = "Change photo", tint = colors.accentText, modifier = Modifier.size(15.dp))
                }
            }
        }

        Text(
            text = "Change photo",
            fontFamily = PublicSansFontFamily,
            fontSize = 12.5.sp,
            color = colors.glow,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp)
                .clickable {
                    galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
        )

        FieldLabel("NAME")
        ProfileTextField(value = viewModel.displayNameInput, onValueChange = viewModel::onDisplayNameChange)

        FieldLabel("USERNAME", topPadding = 14.dp)
        ProfileTextField(
            value = viewModel.usernameInput,
            onValueChange = viewModel::onUsernameChange,
            prefix = "@",
        )

        FieldLabel("EMAIL", topPadding = 14.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(fieldShape)
                .background(colors.panel.copy(alpha = 0.6f))
                .border(1.dp, colors.border, fieldShape)
                .padding(horizontal = 14.dp, vertical = 13.dp),
        ) {
            Text(
                text = viewModel.profile?.email ?: "",
                fontFamily = PublicSansFontFamily,
                fontSize = 13.sp,
                color = colors.mutedDim,
            )
        }

        if (viewModel.errorMessage != null) {
            Text(
                text = viewModel.errorMessage.orEmpty(),
                fontFamily = PublicSansFontFamily,
                fontSize = 11.5.sp,
                color = colors.glow2,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        Box(modifier = Modifier.weight(1f))

        val buttonSizePx = Size(300f, 52f)
        val canSave = viewModel.hasChanges && !viewModel.isSaving &&
            viewModel.displayNameInput.isNotBlank() && viewModel.usernameInput.isNotBlank()
        val buttonBackground = if (canSave) {
            cssAngleGradient(160f, listOf(colors.glow, colors.glow2), buttonSizePx)
        } else {
            Brush.linearGradient(listOf(colors.panel, colors.panel))
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(buttonBackground, pillShape)
                .clickable(enabled = canSave, onClick = viewModel::save)
                .padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (viewModel.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = colors.accentText, strokeWidth = 2.dp)
            } else {
                Text(
                    text = "Save changes",
                    fontFamily = PublicSansFontFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSave) colors.accentText else colors.mutedDim,
                )
            }
        }
    }
}

@Composable
private fun FieldLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val colors = EmberTheme.colors
    Text(
        text = text,
        fontFamily = PublicSansFontFamily,
        fontSize = 11.5.sp,
        color = colors.mutedDim,
        modifier = Modifier.padding(top = topPadding, bottom = 6.dp),
    )
}

@Composable
private fun ProfileTextField(value: String, onValueChange: (String) -> Unit, prefix: String? = null) {
    val colors = EmberTheme.colors
    val shape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.panel)
            .border(1.dp, colors.border, shape)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (prefix != null) {
            Text(text = prefix, fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.mutedDim)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontFamily = PublicSansFontFamily, fontSize = 13.sp, color = colors.cream),
            cursorBrush = SolidColor(colors.glow),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
