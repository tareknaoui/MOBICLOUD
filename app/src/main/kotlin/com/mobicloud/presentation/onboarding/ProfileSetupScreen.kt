package com.mobicloud.presentation.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.serialization.Serializable

@Serializable
object ProfileSetupRoute

private val IosBlue   = Color(0xFF0A84FF)
private val Amber     = Color(0xFFFFB300)
private val LightBg   = Color(0xFFFFFFFF)
private val LightSurface = Color(0xFFF2F2F7)
private val TextHint  = Color(0xFF8E8E93)
private val TextPrimary = Color(0xFF1C1C1E)

@Composable
fun ProfileSetupScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileSetupViewModel = hiltViewModel()
) {
    var name by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> avatarUri = uri }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(LightBg)
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "MobiCloud",
            color = IosBlue,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Create your profile",
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Your name will be visible to peers in your group. Photo is optional.",
            color = TextHint,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )

        Spacer(Modifier.height(36.dp))

        // Avatar picker
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(LightSurface)
                .border(2.dp, IosBlue.copy(alpha = 0.4f), CircleShape)
                .clickable { photoPickerLauncher.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (avatarUri != null) {
                AsyncImage(
                    model = avatarUri,
                    contentDescription = "Profile photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null,
                        tint = IosBlue,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = "Add photo",
                        color = IosBlue,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // Name field
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 30) name = it },
            label = { Text("Your name") },
            placeholder = { Text("e.g. Anis", color = TextHint.copy(alpha = 0.6f)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                focusedBorderColor = IosBlue,
                unfocusedBorderColor = TextHint,
                cursorColor = IosBlue,
                focusedLabelColor = IosBlue
            ),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = {
                viewModel.saveProfile(
                    displayName = name,
                    avatarUri = avatarUri?.toString()
                )
                onContinue()
            },
            enabled = name.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = IosBlue,
                disabledContainerColor = TextHint
            )
        ) {
            Text(
                text = "Continue",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(Modifier.height(16.dp))

        TextButton(onClick = onContinue) {
            Text(
                text = "Skip for now",
                color = TextHint,
                fontSize = 14.sp
            )
        }
    }
}
