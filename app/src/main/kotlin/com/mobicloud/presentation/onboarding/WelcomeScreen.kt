package com.mobicloud.presentation.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
object WelcomeRoute

private val IosBlue  = Color(0xFF0A84FF)
private val IosGreen = Color(0xFF34C759)
private val IosText1 = Color(0xFF1C1C1E)
private val IosText2 = Color(0xFF8E8E93)

private data class WelcomePage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val accentColor: Color,
)

private val pages = listOf(
    WelcomePage(
        icon = Icons.Outlined.Cloud,
        title = "Welcome to MobiCloud",
        subtitle = "Your files are protected by the phones around you — no server, no cloud subscription needed.",
        accentColor = IosBlue,
    ),
    WelcomePage(
        icon = Icons.Outlined.Hub,
        title = "Join a group automatically",
        subtitle = "MobiCloud finds nearby devices and forms a secure cluster. Your files are replicated across the group.",
        accentColor = IosGreen,
    ),
    WelcomePage(
        icon = Icons.Outlined.Lock,
        title = "Your data stays private",
        subtitle = "Everything is end-to-end encrypted. Only you can access your files — not even group members can read them.",
        accentColor = IosBlue,
    ),
)

@Composable
fun WelcomeScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PermissionsViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    val permissionsToRequest = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.markOnboardingCompleted()
        onFinish()
    }

    fun complete() {
        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            viewModel.markOnboardingCompleted()
            onFinish()
        }
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f),
            ) { pageIndex ->
                PageContent(page = pages[pageIndex])
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                PagerDots(
                    pageCount = pages.size,
                    currentPage = pagerState.currentPage,
                )

                Button(
                    onClick = {
                        if (isLastPage) {
                            complete()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = IosBlue),
                ) {
                    Text(
                        text = if (isLastPage) "Get started" else "Next",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                }

                if (!isLastPage) {
                    TextButton(onClick = { complete() }) {
                        Text(
                            text = "Skip",
                            color = IosText2,
                            fontSize = 14.sp,
                        )
                    }
                } else {
                    Spacer(Modifier.height(36.dp))
                }
            }
        }
    }
}

@Composable
private fun PageContent(page: WelcomePage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(page.accentColor.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = page.accentColor,
                modifier = Modifier.size(56.dp),
            )
        }

        Spacer(Modifier.height(44.dp))

        Text(
            text = page.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = IosText1,
            textAlign = TextAlign.Center,
            lineHeight = 30.sp,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = page.subtitle,
            fontSize = 15.sp,
            color = IosText2,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
        )
    }
}

@Composable
private fun PagerDots(pageCount: Int, currentPage: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val dotColor by animateColorAsState(
                targetValue = if (isActive) IosBlue else Color(0xFFE5E5EA),
                animationSpec = tween(300),
                label = "dot$index",
            )
            val dotWidth: Dp by animateDpAsState(
                targetValue = if (isActive) 20.dp else 8.dp,
                animationSpec = tween(300),
                label = "dotWidth$index",
            )
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(dotColor)
                    .height(8.dp)
                    .width(dotWidth),
            )
        }
    }
}
