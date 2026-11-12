package africa.fundaza.customer.ui.navigation

import africa.fundaza.customer.core.security.SessionState
import africa.fundaza.customer.ui.AppViewModel
import africa.fundaza.customer.ui.components.MessageState
import africa.fundaza.customer.ui.feature.auth.LoginScreen
import africa.fundaza.customer.ui.feature.home.HomeScreen
import africa.fundaza.customer.ui.feature.profile.ProfileScreen
import africa.fundaza.customer.ui.feature.recipients.AddRecipientScreen
import africa.fundaza.customer.ui.feature.recipients.RecipientDetailScreen
import africa.fundaza.customer.ui.feature.recipients.RecipientsScreen
import africa.fundaza.customer.ui.feature.send.SendScreen
import africa.fundaza.customer.ui.feature.transfers.TransferDetailScreen
import africa.fundaza.customer.ui.feature.transfers.TransfersScreen
import africa.fundaza.customer.ui.feature.wallet.WalletScreen
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlin.reflect.KClass

private data class Tab(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

private val tabs = listOf(
    Tab(HomeRoute, HomeRoute::class, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(TransfersRoute, TransfersRoute::class, "Transfers", Icons.Outlined.SwapHoriz, Icons.Filled.SwapHoriz),
    Tab(RecipientsRoute, RecipientsRoute::class, "Recipients", Icons.Outlined.People, Icons.Filled.People),
    Tab(ProfileRoute, ProfileRoute::class, "Profile", Icons.Outlined.Person, Icons.Filled.Person),
)

@Composable
fun FundazaApp(appViewModel: AppViewModel) {
    val session by appViewModel.sessionState.collectAsStateWithLifecycle()

    when (val s = session) {
        // The splash screen covers this.
        SessionState.Restoring -> Box(Modifier.fillMaxSize())
        is SessionState.Unreachable -> MessageState(
            icon = Icons.Outlined.CloudOff,
            title = "Can't reach Fundaza",
            body = s.message,
            actionLabel = "Try again",
            onAction = appViewModel::restore,
        )
        else -> SignedInOrOut(s, appViewModel)
    }
}

@Composable
private fun SignedInOrOut(session: SessionState, appViewModel: AppViewModel) {
    val nav = rememberNavController()
    val signedIn = session is SessionState.SignedIn

    if (signedIn) AskForNotifications()

    // Follow the session wherever it changes: sign-in, sign-out, or a token the server stopped accepting.
    LaunchedEffect(signedIn) {
        val onLogin = nav.currentDestination?.hasRoute(LoginRoute::class) == true
        if (signedIn && onLogin) {
            nav.navigate(HomeRoute) { popUpTo(0) { inclusive = true } }
        } else if (!signedIn && !onLogin && nav.currentDestination != null) {
            nav.navigate(LoginRoute) { popUpTo(0) { inclusive = true } }
        }
    }

    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBottomBar = tabs.any { tab -> destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                    tabs.forEach { tab ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(tab.routeClass) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.secondary,
                                selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        FundazaNavHost(
            nav = nav,
            startDestination = if (signedIn) HomeRoute else LoginRoute,
            notice = (session as? SessionState.SignedOut)?.notice,
            bottomPadding = padding,
            onSignOut = appViewModel::signOut,
        )
    }
}

private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun FundazaNavHost(
    nav: NavHostController,
    startDestination: Any,
    notice: String?,
    bottomPadding: PaddingValues,
    onSignOut: () -> Unit,
) {
    // Tab screens sit above the bottom bar; pushed screens own the whole window.
    val tabPadding = Modifier.padding(bottom = bottomPadding.calculateBottomPadding())
    NavHost(
        navController = nav,
        startDestination = startDestination,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable<LoginRoute> { LoginScreen(notice = notice) }

        composable<HomeRoute>(enterTransition = { fadeIn() }) {
            HomeScreen(
                modifier = tabPadding,
                onSend = { nav.navigate(SendRoute) },
                onWallet = { nav.navigate(WalletRoute) },
                onTransfer = { nav.navigate(TransferDetailRoute(it)) },
                onAllTransfers = { nav.switchTab(TransfersRoute) },
                onProfile = { nav.switchTab(ProfileRoute) },
            )
        }
        composable<TransfersRoute>(enterTransition = { fadeIn() }) {
            TransfersScreen(
                modifier = tabPadding,
                onTransfer = { nav.navigate(TransferDetailRoute(it)) },
                onSend = { nav.navigate(SendRoute) },
            )
        }
        composable<RecipientsRoute>(enterTransition = { fadeIn() }) {
            RecipientsScreen(
                modifier = tabPadding,
                onRecipient = { nav.navigate(RecipientDetailRoute(it)) },
                onAdd = { nav.navigate(AddRecipientRoute()) },
            )
        }
        composable<ProfileRoute>(enterTransition = { fadeIn() }) {
            ProfileScreen(modifier = tabPadding, onSignOut = onSignOut)
        }

        composable<SendRoute> { entry ->
            val created by entry.savedStateHandle
                .getStateFlow<Long?>(RESULT_BENEFICIARY_ID, null)
                .collectAsStateWithLifecycle()
            SendScreen(
                onBack = { nav.popBackStack() },
                createdBeneficiaryId = created,
                onCreatedBeneficiaryConsumed = { entry.savedStateHandle[RESULT_BENEFICIARY_ID] = null },
                onAddRecipient = { route -> nav.navigate(route) },
                onSent = { reference ->
                    nav.navigate(TransferDetailRoute(reference, justSent = true)) {
                        popUpTo(HomeRoute)
                    }
                },
            )
        }
        composable<WalletRoute> {
            WalletScreen(onBack = { nav.popBackStack() }, onTransfer = { nav.navigate(TransferDetailRoute(it)) })
        }
        composable<TransferDetailRoute> { entry ->
            val route = entry.toRoute<TransferDetailRoute>()
            TransferDetailScreen(
                justSent = route.justSent,
                onBack = { nav.popBackStack() },
                onRecipient = { nav.navigate(RecipientDetailRoute(it)) },
            )
        }
        composable<RecipientDetailRoute> {
            RecipientDetailScreen(
                onBack = { nav.popBackStack() },
                onAddWay = { nav.navigate(it) },
            )
        }
        composable<AddRecipientRoute> {
            AddRecipientScreen(
                onBack = { nav.popBackStack() },
                onSaved = { id ->
                    nav.previousBackStackEntry?.savedStateHandle?.set(RESULT_BENEFICIARY_ID, id)
                    nav.popBackStack()
                },
            )
        }
    }
}

/**
 * Android 13+ asks before an app may notify. Asked once signed in, when there is
 * something to be told about; Android itself stops asking after a second refusal.
 */
@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
