package io.github.legandy.volumemixerpanel.setup

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.legandy.volumemixerpanel.R
import io.github.legandy.volumemixerpanel.setup.SetupViewModel.OnboardingStep
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect



@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SetupScreen(
    currentStep: OnboardingStep,
    onNavigateTo: (OnboardingStep) -> Unit,
    onOnboardingComplete: () -> Unit,
    hasShizukuPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    hasNotificationPolicyAccess: Boolean,
    hasOverlayPermission: Boolean,
    onGrantShizukuClick: () -> Unit,
    onOpenAccessibilityClick: () -> Unit,
    onOpenNotificationPolicyAccessClick: () -> Unit,
    onOpenOverlayPermissionClick: () -> Unit,
    onGrantAllPermissionsClick: () -> Unit,
    canGoNext: Boolean // New parameter for controlling "Next" button state
) {
    val onboardingSteps = remember { OnboardingStep.entries.toTypedArray() }
    val pagerState = rememberPagerState(
        initialPage = onboardingSteps.indexOf(currentStep),
        pageCount = { onboardingSteps.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // Synchronize pager state with ViewModel's currentStep
    LaunchedEffect(currentStep) {
        val index = onboardingSteps.indexOf(currentStep)
        if (index != -1 && index != pagerState.currentPage) {
            pagerState.animateScrollToPage(index)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.onboarding_setup_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false, // Disable user swipe to ensure guided navigation
                modifier = Modifier
                    .weight(1f) // Occupy remaining space
                    .padding(horizontal = 24.dp)
            ) { page ->
                val step = onboardingSteps[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (step) {
                        OnboardingStep.Welcome -> WelcomeScreen() // Removed onGetStartedClick
                        OnboardingStep.Shizuku -> ShizukuPermissionScreen(
                            hasShizuku = hasShizukuPermission,
                            onGrantShizukuClick = onGrantShizukuClick,
                            onGrantAllPermissionsClick = onGrantAllPermissionsClick
                        )
                        OnboardingStep.Accessibility -> AccessibilityPermissionScreen(
                            hasAccessibility = isAccessibilityEnabled,
                            onOpenAccessibilityClick = onOpenAccessibilityClick
                        )
                        OnboardingStep.NotificationPolicyAccess -> NotificationPolicyAccessScreen(
                            hasNotificationPolicyAccess = hasNotificationPolicyAccess,
                            onOpenNotificationPolicyAccessClick = onOpenNotificationPolicyAccessClick
                        )
                        OnboardingStep.OverlayPermission -> OverlayPermissionScreen(
                            hasOverlayPermission = hasOverlayPermission,
                            onOpenOverlayPermissionClick = onOpenOverlayPermissionClick
                        )
                        OnboardingStep.Complete -> OnboardingCompleteScreen() // Modified to remove onGoToAppClick
                    }
                }
            }

            // Bottom Navigation Section
            BottomNavigationSection(
                currentPage = pagerState.currentPage,
                pageCount = onboardingSteps.size,
                onPreviousClick = {
                    coroutineScope.launch {
                        val previousPageIndex = pagerState.currentPage - 1
                        if (previousPageIndex >= 0) {
                            pagerState.animateScrollToPage(previousPageIndex)
                            onNavigateTo(onboardingSteps[previousPageIndex])
                        }
                    }
                },
                onNextClick = {
                    val nextPageIndex = pagerState.currentPage + 1
                    if (nextPageIndex < onboardingSteps.size) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(nextPageIndex)
                            onNavigateTo(onboardingSteps[nextPageIndex])
                        }
                    } else if (nextPageIndex == onboardingSteps.size) { // This means we are on the last page and attempting to go to 'Complete'
                        onOnboardingComplete()
                    }
                },
                canGoNext = canGoNext,
                isLastPage = (pagerState.currentPage == onboardingSteps.size - 1)
            )
        }
    }
}

@Composable
fun WelcomeScreen() { // Removed onGetStartedClick parameter
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            Icons.Filled.Headphones, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp) // Fixed height to prevent jumping
        )
        Spacer(modifier = Modifier.height(32.dp))
        // Removed "Get Started" button
    }
}

@Composable
fun ShizukuPermissionScreen(
    hasShizuku: Boolean,
    onGrantShizukuClick: () -> Unit,
    onGrantAllPermissionsClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize() // Fill available space
    ) {
        Icon(
            Icons.Filled.Key, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.shizuku_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.shizuku_permission_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp) // Fixed height to prevent jumping
        )
        StatusCard(
            title = stringResource(R.string.shizuku_card_title),
            description = stringResource(R.string.shizuku_card_description),
            granted = hasShizuku
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onGrantShizukuClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasShizuku
        ) {
            Text(stringResource(R.string.shizuku_grant_button))
        }
        Button(
            onClick = onGrantAllPermissionsClick,
            modifier = Modifier.fillMaxWidth(),
            enabled = hasShizuku // Only enabled if Shizuku is granted
        ) {
            Text(stringResource(R.string.grant_all_permissions_button))
        }
    }
}

@Composable
fun AccessibilityPermissionScreen(
    hasAccessibility: Boolean,
    onOpenAccessibilityClick: () -> Unit
) {
    val context = LocalContext.current
    val intent = remember { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize() // Fill available space
    ) {
        Icon(
            Icons.Filled.Accessibility, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.accessibility_service_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.accessibility_service_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp) // Fixed height
        )
        StatusCard(
            title = stringResource(R.string.accessibility_card_title),
            description = stringResource(R.string.accessibility_card_description),
            granted = hasAccessibility
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onOpenAccessibilityClick()
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasAccessibility
        ) {
            Text(stringResource(R.string.accessibility_enable_button))
        }
    }
}

@Composable
fun NotificationPolicyAccessScreen(
    hasNotificationPolicyAccess: Boolean,
    onOpenNotificationPolicyAccessClick: () -> Unit
) {
    val context = LocalContext.current
    val intent = remember { Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize() // Fill available space
    ) {
        Icon(
            Icons.Filled.Notifications, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.notification_policy_access_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.notification_policy_access_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp) // Fixed height
        )
        StatusCard(
            title = stringResource(R.string.notification_policy_access_card_title),
            description = stringResource(R.string.notification_policy_access_card_description),
            granted = hasNotificationPolicyAccess
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onOpenNotificationPolicyAccessClick()
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasNotificationPolicyAccess
        ) {
            Text(stringResource(R.string.notification_policy_access_grant_button))
        }
    }
}

@Composable
fun OverlayPermissionScreen(
    hasOverlayPermission: Boolean,
    onOpenOverlayPermissionClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize() // Fill available space
    ) {
        Icon(
            Icons.Filled.Widgets, // Icon for overlay permission
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(R.string.overlay_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            stringResource(R.string.overlay_permission_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp) // Fixed height
        )
        StatusCard(
            title = stringResource(R.string.overlay_permission_card_title),
            description = stringResource(R.string.overlay_permission_card_description),
            granted = hasOverlayPermission
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                onOpenOverlayPermissionClick()
                // The activity will handle launching the intent via launcher
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !hasOverlayPermission
        ) {
            Text(stringResource(R.string.overlay_permission_grant_button))
        }
    }
}

@Composable
fun OnboardingCompleteScreen() { // Removed onGoToAppClick parameter
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize()
    ) {
        Icon(
            Icons.Filled.DoneAll, // Example icon
            contentDescription = null, // Decorative icon
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            stringResource(R.string.onboarding_complete_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.onboarding_complete_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .height(80.dp) // Fixed height
        )
        Spacer(modifier = Modifier.height(32.dp))
        // Removed "Go to app" button
    }
}

@Composable
fun StatusCard(title: String, description: String, granted: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = if (granted) stringResource(R.string.status_icon_granted) else stringResource(R.string.status_icon_not_granted),
                tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun WaitingForShizukuScreen() { /* TODO: Implement actual waiting screen */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.shizuku_waiting_title),
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.shizuku_waiting_message),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomNavigationSection(
    currentPage: Int,
    pageCount: Int,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    canGoNext: Boolean,
    isLastPage: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Previous Button
        IconButton(
            onClick = onPreviousClick,
            enabled = currentPage > 0,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.previous_button_description))
        }

        // Page Indicators
        Row(
            modifier = Modifier.weight(2f),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(pageCount) { iteration ->
                val color = if (currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                Icon(
                    Icons.Filled.CheckCircle, // Using a simple circle icon for indicator
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(8.dp)
                )
            }
        }

        // Next/Complete Button
        IconButton(
            onClick = onNextClick,
            enabled = canGoNext || isLastPage,
            modifier = Modifier.weight(1f)
        ) {
            val icon = if (isLastPage) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.ArrowForward
            val contentDescription = if (isLastPage) stringResource(R.string.onboarding_complete_go_to_app_button) else stringResource(R.string.next_button_text)
            Icon(icon, contentDescription = contentDescription)
        }
    }
}
