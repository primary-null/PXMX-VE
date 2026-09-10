package com.pxmx.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pxmx.app.ProxmoxApp
import com.pxmx.app.data.model.GuestType
import com.pxmx.app.data.model.LoginOutcome
import com.pxmx.app.ui.guest.GuestDetailScreen
import com.pxmx.app.ui.guest.GuestDetailViewModel
import com.pxmx.app.ui.home.HomeScreen
import com.pxmx.app.ui.home.HomeViewModel
import com.pxmx.app.ui.login.LoginScreen
import com.pxmx.app.ui.login.LoginViewModel
import com.pxmx.app.ui.splash.SplashScreen
import com.pxmx.app.ui.console.ConsoleScreen
import com.pxmx.app.ui.console.ConsoleViewModel
import com.pxmx.app.ui.servers.ServersScreen
import com.pxmx.app.ui.servers.ServersViewModel
import com.pxmx.app.ui.node.NodeDetailScreen
import com.pxmx.app.ui.node.NodeDetailViewModel
import com.pxmx.app.ui.settings.FirewallScreen
import com.pxmx.app.ui.settings.FirewallViewModel
import com.pxmx.app.ui.settings.NetworkScreen
import com.pxmx.app.ui.settings.NetworkViewModel
import com.pxmx.app.ui.settings.SdnScreen
import com.pxmx.app.ui.settings.SdnViewModel
import com.pxmx.app.ui.settings.SettingsScreen
import com.pxmx.app.ui.settings.SettingsViewModel
import com.pxmx.app.ui.settings.UpdatesScreen
import com.pxmx.app.ui.settings.UpdatesViewModel
import com.pxmx.app.ui.permissions.PermissionsScreen
import com.pxmx.app.ui.storage.StorageDetailScreen
import com.pxmx.app.ui.storage.StorageDetailViewModel
import com.pxmx.app.ui.tasks.TasksScreen
import com.pxmx.app.ui.tasks.TasksViewModel
import com.pxmx.app.ui.log.LogScreen
import com.pxmx.app.ui.log.LogViewModel
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pxmx.app.ui.components.TechPlate
import com.pxmx.app.ui.components.TechColors
import com.pxmx.app.ui.adaptive.isOperatorTwoPane
import com.pxmx.app.ui.adaptive.isTabletop
import com.pxmx.app.ui.adaptive.SettingsPaneSelection
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.collectFoldingFeaturesAsState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"
    const val HOME = "home"
    const val TASKS = "tasks"
    const val SETTINGS = "settings"
    const val SERVERS = "servers"
    const val NETWORK = "settings/network"
    const val SDN = "settings/sdn"
    const val FIREWALL = "settings/firewall"
    const val UPDATES = "settings/updates"
    const val PERMISSIONS = "settings/permissions"
    const val LOG = "log"
    const val NODE = "node/{node}"

    fun node(node: String): String = "node/${Uri.encode(node)}"
    const val GUEST = "guest/{node}/{type}/{vmid}/{name}"
    const val STORAGE = "storage/{node}/{storage}"
    const val CONSOLE = "console/{node}/{type}/{vmid}/{name}?cmd={cmd}"

    fun guest(node: String, type: String, vmid: Long, name: String): String {
        val safeNode = Uri.encode(node)
        val safeType = Uri.encode(type)
        val safeName = Uri.encode(name)
        return "guest/$safeNode/$safeType/$vmid/$safeName"
    }

    fun storage(node: String, storage: String): String {
        val safeNode = Uri.encode(node)
        val safeStorage = Uri.encode(storage)
        return "storage/$safeNode/$safeStorage"
    }

    fun console(node: String, type: String, vmid: Long, name: String, cmd: String? = null): String {
        val safeNode = Uri.encode(node)
        val safeType = Uri.encode(type)
        val safeName = Uri.encode(name)
        val base = "console/$safeNode/$safeType/$vmid/$safeName"
        return if (cmd != null) {
            val safeCmd = Uri.encode(cmd)
            "$base?cmd=$safeCmd"
        } else base
    }
}

sealed interface DetailPaneSelection {
    data class Guest(val node: String, val type: String, val vmid: Long, val name: String) : DetailPaneSelection
    data class Node(val node: String) : DetailPaneSelection
    data class Storage(val node: String, val storage: String) : DetailPaneSelection
}

val DetailPaneSelectionSaver: Saver<DetailPaneSelection?, Any> = Saver(
    save = { sel ->
        when (sel) {
            is DetailPaneSelection.Guest -> mapOf(
                "kind" to "guest",
                "node" to sel.node,
                "type" to sel.type,
                "vmid" to sel.vmid,
                "name" to sel.name,
            )
            is DetailPaneSelection.Node -> mapOf(
                "kind" to "node",
                "node" to sel.node,
            )
            is DetailPaneSelection.Storage -> mapOf(
                "kind" to "storage",
                "node" to sel.node,
                "storage" to sel.storage,
            )
            null -> emptyMap<String, Any>()
        }
    },
    restore = { value ->
        val map = value as? Map<*, *> ?: return@Saver null
        when (map["kind"] as? String) {
            "guest" -> DetailPaneSelection.Guest(
                node = map["node"] as String,
                type = map["type"] as String,
                vmid = (map["vmid"] as Number).toLong(),
                name = map["name"] as String,
            )
            "node" -> DetailPaneSelection.Node(
                node = map["node"] as String,
            )
            "storage" -> DetailPaneSelection.Storage(
                node = map["node"] as String,
                storage = map["storage"] as String,
            )
            else -> null
        }
    }
)

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ProxmoxNavGraph() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as ProxmoxApp
    var splashStatus by remember { mutableStateOf("Starting…") }

    // Adaptive window info and posture
    val adaptiveInfo = currentWindowAdaptiveInfoV2()
    val configuration = LocalConfiguration.current
    val isTwoPane = isOperatorTwoPane(configuration.screenWidthDp)
    val foldingFeatures = collectFoldingFeaturesAsState().value
    val isTabletop = foldingFeatures.any { isTabletop(it.state.toString(), it.orientation.toString()) }

    var selectedDetail by rememberSaveable(stateSaver = DetailPaneSelectionSaver) {
        mutableStateOf<DetailPaneSelection?>(null)
    }

    var selectedSettingsPane by rememberSaveable {
        mutableStateOf<SettingsPaneSelection?>(null)
    }

    // Back handler on two-pane: clear the pane selection first before popping Home
    BackHandler(enabled = isTwoPane && selectedDetail != null) {
        selectedDetail = null
    }

    // Back handler on two-pane Settings: clear tool selection first before popping Settings
    BackHandler(enabled = isTwoPane && selectedSettingsPane != null && navController.currentDestination?.route == Routes.SETTINGS) {
        selectedSettingsPane = null
    }

    // If folding back down to compact while a detail was selected in two-pane, push it onto nav stack
    LaunchedEffect(isTwoPane) {
        if (!isTwoPane && selectedDetail != null && navController.currentDestination?.route == Routes.HOME) {
            val sel = selectedDetail
            selectedDetail = null
            when (sel) {
                is DetailPaneSelection.Guest -> navController.navigate(Routes.guest(sel.node, sel.type, sel.vmid, sel.name))
                is DetailPaneSelection.Node -> navController.navigate(Routes.node(sel.node))
                is DetailPaneSelection.Storage -> navController.navigate(Routes.storage(sel.node, sel.storage))
                null -> Unit
            }
        }
        if (!isTwoPane && selectedSettingsPane != null && navController.currentDestination?.route == Routes.SETTINGS) {
            val pane = selectedSettingsPane
            selectedSettingsPane = null
            when (pane) {
                SettingsPaneSelection.NETWORK -> navController.navigate(Routes.NETWORK)
                SettingsPaneSelection.SDN -> navController.navigate(Routes.SDN)
                SettingsPaneSelection.FIREWALL -> navController.navigate(Routes.FIREWALL)
                SettingsPaneSelection.UPDATES -> navController.navigate(Routes.UPDATES)
                SettingsPaneSelection.LOG -> navController.navigate(Routes.LOG)
                null -> Unit
            }
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.SPLASH,
        enterTransition = {
            fadeIn(tween(220)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(280),
            )
        },
        exitTransition = {
            fadeOut(tween(180))
        },
        popEnterTransition = {
            fadeIn(tween(220)) + slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                tween(280),
            )
        },
        popExitTransition = {
            fadeOut(tween(180)) + slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                tween(240),
            )
        },
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(
                statusText = splashStatus,
                bootstrap = {
                    splashStatus = "Checking saved connection…"
                    app.repository.tryAutoConnect().isSuccess
                },
                onFinished = { autoConnected ->
                    val dest = if (autoConnected) Routes.HOME else Routes.LOGIN
                    navController.navigate(dest) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.LOGIN) {
            val vm: LoginViewModel = viewModel(
                factory = LoginViewModel.Factory(app.repository, app.sessionStore),
            )
            LoginScreen(
                viewModel = vm,
                onLoggedIn = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            val vm: HomeViewModel = viewModel(
                factory = HomeViewModel.Factory(app.repository, app.sessionStore),
            )
            if (isTwoPane) {
                Row(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        viewModel = vm,
                        onOpenGuest = { node, type, vmid, name ->
                            selectedDetail = DetailPaneSelection.Guest(node, type, vmid, name)
                        },
                        onOpenStorage = { node, storage ->
                            selectedDetail = DetailPaneSelection.Storage(node, storage)
                        },
                        onOpenNode = { node ->
                            selectedDetail = DetailPaneSelection.Node(node)
                        },
                        onOpenSettings = {
                            navController.navigate(Routes.SETTINGS)
                        },
                        onOpenTasks = {
                            navController.navigate(Routes.TASKS)
                        },
                        onOpenLogs = {
                            navController.navigate(Routes.LOG)
                        },
                        onOpenServers = {
                            navController.navigate(Routes.SERVERS)
                        },
                        onOpenUpdates = {
                            navController.navigate(Routes.UPDATES)
                        },
                        onLogout = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onSwitchAccount = {
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        canOfferNodeShell = isTabletop,
                        onOpenNodeShell = { node ->
                            navController.navigate(
                                Routes.console(node, GuestType.NODE.path, 0L, node, "shell"),
                            )
                        },
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(TechColors.Edge),
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        when (val sel = selectedDetail) {
                            is DetailPaneSelection.Guest -> {
                                val guestType = remember(sel.type) {
                                    GuestType.fromResourceType(sel.type) ?: GuestType.QEMU
                                }
                                val detailVm: GuestDetailViewModel = viewModel(
                                    key = "pane-guest-${sel.node}-${sel.type}-${sel.vmid}",
                                    factory = GuestDetailViewModel.Factory(
                                        app.repository,
                                        sel.node,
                                        guestType,
                                        sel.vmid,
                                        sel.name,
                                    ),
                                )
                                GuestDetailScreen(
                                    viewModel = detailVm,
                                    onBack = { selectedDetail = null },
                                    onOpenConsole = {
                                        navController.navigate(
                                            Routes.console(sel.node, sel.type, sel.vmid, sel.name),
                                        )
                                    },
                                    onOpenLogs = {
                                        navController.navigate(Routes.LOG)
                                    },
                                )
                            }
                            is DetailPaneSelection.Node -> {
                                val detailVm: NodeDetailViewModel = viewModel(
                                    key = "pane-node-${sel.node}",
                                    factory = NodeDetailViewModel.Factory(app.repository, sel.node),
                                )
                                NodeDetailScreen(
                                    viewModel = detailVm,
                                    onBack = { selectedDetail = null },
                                    onOpenConsole = { cmd ->
                                        navController.navigate(
                                            Routes.console(sel.node, GuestType.NODE.path, 0L, sel.node, cmd ?: "shell"),
                                        )
                                    },
                                )
                            }
                            is DetailPaneSelection.Storage -> {
                                val detailVm: StorageDetailViewModel = viewModel(
                                    key = "pane-storage-${sel.node}-${sel.storage}",
                                    factory = StorageDetailViewModel.Factory(app.repository, sel.node, sel.storage),
                                )
                                StorageDetailScreen(
                                    viewModel = detailVm,
                                    onBack = { selectedDetail = null },
                                )
                            }
                            null -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(TechColors.Hull)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TechPlate(
                                        railColor = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = "OPERATOR PANE",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = "Select a guest, node, or storage to view details.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                HomeScreen(
                    viewModel = vm,
                    onOpenGuest = { node, type, vmid, name ->
                        navController.navigate(Routes.guest(node, type, vmid, name))
                    },
                    onOpenStorage = { node, storage ->
                        navController.navigate(Routes.storage(node, storage))
                    },
                    onOpenNode = { node ->
                        navController.navigate(Routes.node(node))
                    },
                    onOpenSettings = {
                        navController.navigate(Routes.SETTINGS)
                    },
                    onOpenTasks = {
                        navController.navigate(Routes.TASKS)
                    },
                    onOpenLogs = {
                        navController.navigate(Routes.LOG)
                    },
                    onOpenServers = {
                        navController.navigate(Routes.SERVERS)
                    },
                    onOpenUpdates = {
                        navController.navigate(Routes.UPDATES)
                    },
                    onLogout = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onSwitchAccount = {
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    canOfferNodeShell = false,
                    onOpenNodeShell = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composable(Routes.SETTINGS) {
            val vm: SettingsViewModel = viewModel(
                factory = SettingsViewModel.Factory(app.repository),
            )
            val uiState by vm.ui.collectAsStateWithLifecycle()
            val session by app.sessionStore.session.collectAsStateWithLifecycle()
            val themeMode by app.sessionStore.themeMode.collectAsStateWithLifecycle()

            if (isTwoPane) {
                Row(modifier = Modifier.fillMaxSize()) {
                    SettingsScreen(
                        hostDisplay = session?.config?.displayHost ?: "—",
                        versionDisplay = session?.version?.display ?: "—",
                        themeMode = themeMode,
                        uiState = uiState,
                        onBack = {
                            if (selectedSettingsPane != null) selectedSettingsPane = null
                            else navController.popBackStack()
                        },
                        onOpenNetwork = { selectedSettingsPane = SettingsPaneSelection.NETWORK },
                        onOpenSdn = { selectedSettingsPane = SettingsPaneSelection.SDN },
                        onOpenFirewall = { selectedSettingsPane = SettingsPaneSelection.FIREWALL },
                        onOpenUpdates = { selectedSettingsPane = SettingsPaneSelection.UPDATES },
                        onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                        onOpenLogs = { selectedSettingsPane = SettingsPaneSelection.LOG },
                        onThemeMode = { app.sessionStore.setThemeMode(it) },
                        onSwitchAccount = {
                            app.repository.logout(rememberAsPrevious = true)
                            navController.navigate(Routes.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onCleanSlate = {
                            app.sessionStore.purgeAll(app)
                            android.os.Process.killProcess(android.os.Process.myPid())
                        },
                        modifier = Modifier.width(360.dp).fillMaxHeight(),
                    )

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(TechColors.Edge),
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        when (selectedSettingsPane) {
                            SettingsPaneSelection.NETWORK -> {
                                val netVm: NetworkViewModel = viewModel(
                                    key = "pane-network",
                                    factory = NetworkViewModel.Factory(app.repository),
                                )
                                NetworkScreen(
                                    viewModel = netVm,
                                    onBack = { selectedSettingsPane = null },
                                )
                            }
                            SettingsPaneSelection.SDN -> {
                                val sdnVm: SdnViewModel = viewModel(
                                    key = "pane-sdn",
                                    factory = SdnViewModel.Factory(app.repository),
                                )
                                SdnScreen(
                                    viewModel = sdnVm,
                                    onBack = { selectedSettingsPane = null },
                                )
                            }
                            SettingsPaneSelection.FIREWALL -> {
                                val fwVm: FirewallViewModel = viewModel(
                                    key = "pane-firewall",
                                    factory = FirewallViewModel.Factory(app.repository),
                                )
                                FirewallScreen(
                                    viewModel = fwVm,
                                    onBack = { selectedSettingsPane = null },
                                )
                            }
                            SettingsPaneSelection.UPDATES -> {
                                val updatesVm: UpdatesViewModel = viewModel(
                                    key = "pane-updates",
                                    factory = UpdatesViewModel.Factory(app.repository),
                                )
                                UpdatesScreen(
                                    viewModel = updatesVm,
                                    onBack = { selectedSettingsPane = null },
                                    onOpenNode = { node ->
                                        navController.navigate(Routes.node(node))
                                    },
                                    onOpenNodeShell = { node ->
                                        navController.navigate(
                                            Routes.console(node, GuestType.NODE.path, 0L, node, "login"),
                                        )
                                    },
                                )
                            }
                            SettingsPaneSelection.LOG -> {
                                val logVm: LogViewModel = viewModel(
                                    key = "pane-log",
                                    factory = LogViewModel.Factory(app.repository),
                                )
                                LogScreen(
                                    viewModel = logVm,
                                    onBack = { selectedSettingsPane = null },
                                )
                            }
                            null -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(TechColors.Hull)
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    TechPlate(
                                        railColor = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Text(
                                                text = "CLUSTER TOOLS",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = "Select a cluster tool to configure.",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontFamily = FontFamily.Monospace,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                SettingsScreen(
                    hostDisplay = session?.config?.displayHost ?: "—",
                    versionDisplay = session?.version?.display ?: "—",
                    themeMode = themeMode,
                    uiState = uiState,
                    onBack = { navController.popBackStack() },
                    onOpenNetwork = { navController.navigate(Routes.NETWORK) },
                    onOpenSdn = { navController.navigate(Routes.SDN) },
                    onOpenFirewall = { navController.navigate(Routes.FIREWALL) },
                    onOpenUpdates = { navController.navigate(Routes.UPDATES) },
                    onOpenPermissions = { navController.navigate(Routes.PERMISSIONS) },
                    onOpenLogs = { navController.navigate(Routes.LOG) },
                    onThemeMode = { app.sessionStore.setThemeMode(it) },
                    onSwitchAccount = {
                        app.repository.logout(rememberAsPrevious = true)
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onCleanSlate = {
                        app.sessionStore.purgeAll(app)
                        android.os.Process.killProcess(android.os.Process.myPid())
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        composable(Routes.LOG) {
            LaunchedEffect(isTwoPane) {
                if (isTwoPane) {
                    val popped = navController.popBackStack(Routes.SETTINGS, inclusive = false)
                    if (popped) {
                        selectedSettingsPane = SettingsPaneSelection.LOG
                    }
                }
            }
            if (isTwoPane && selectedSettingsPane == SettingsPaneSelection.LOG) return@composable

            val vm: LogViewModel = viewModel(
                factory = LogViewModel.Factory(app.repository),
            )
            LogScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.TASKS) {
            val vm: TasksViewModel = viewModel(
                factory = TasksViewModel.Factory(app.repository),
            )
            TasksScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SERVERS) {
            val vm: ServersViewModel = viewModel(
                factory = ServersViewModel.Factory(app.repository, app.sessionStore),
            )
            val scope = rememberCoroutineScope()
            ServersScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onServerSelected = { profileId ->
                    scope.launch {
                        when (app.repository.loginWithProfile(profileId)) {
                            is LoginOutcome.Success -> {
                                navController.navigate(Routes.HOME) {
                                    popUpTo(Routes.HOME) { inclusive = true }
                                }
                            }
                            is LoginOutcome.NeedsTfa -> {
                                app.sessionStore.touchProfile(profileId)
                                navController.navigate(Routes.LOGIN) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                            is LoginOutcome.Failed -> Unit
                        }
                    }
                },
                onLoginPrefilled = { profileId ->
                    app.sessionStore.touchProfile(profileId)
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.NETWORK) {
            LaunchedEffect(isTwoPane) {
                if (isTwoPane) {
                    selectedSettingsPane = SettingsPaneSelection.NETWORK
                    val popped = navController.popBackStack(Routes.SETTINGS, inclusive = false)
                    if (!popped) {
                        navController.navigate(Routes.SETTINGS)
                    }
                }
            }
            if (isTwoPane) return@composable

            val vm: NetworkViewModel = viewModel(
                factory = NetworkViewModel.Factory(app.repository),
            )
            NetworkScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SDN) {
            LaunchedEffect(isTwoPane) {
                if (isTwoPane) {
                    selectedSettingsPane = SettingsPaneSelection.SDN
                    val popped = navController.popBackStack(Routes.SETTINGS, inclusive = false)
                    if (!popped) {
                        navController.navigate(Routes.SETTINGS)
                    }
                }
            }
            if (isTwoPane) return@composable

            val vm: SdnViewModel = viewModel(
                factory = SdnViewModel.Factory(app.repository),
            )
            SdnScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.FIREWALL) {
            LaunchedEffect(isTwoPane) {
                if (isTwoPane) {
                    selectedSettingsPane = SettingsPaneSelection.FIREWALL
                    val popped = navController.popBackStack(Routes.SETTINGS, inclusive = false)
                    if (!popped) {
                        navController.navigate(Routes.SETTINGS)
                    }
                }
            }
            if (isTwoPane) return@composable

            val vm: FirewallViewModel = viewModel(
                factory = FirewallViewModel.Factory(app.repository),
            )
            FirewallScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.UPDATES) {
            LaunchedEffect(isTwoPane) {
                if (isTwoPane) {
                    val popped = navController.popBackStack(Routes.SETTINGS, inclusive = false)
                    if (popped) {
                        selectedSettingsPane = SettingsPaneSelection.UPDATES
                    }
                }
            }
            if (isTwoPane && selectedSettingsPane == SettingsPaneSelection.UPDATES) return@composable

            val vm: UpdatesViewModel = viewModel(
                factory = UpdatesViewModel.Factory(app.repository),
            )
            UpdatesScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenNode = { node ->
                    navController.navigate(Routes.node(node))
                },
                onOpenNodeShell = { node ->
                    navController.navigate(
                        Routes.console(node, GuestType.NODE.path, 0L, node, "login"),
                    )
                },
            )
        }

        composable(Routes.PERMISSIONS) {
            PermissionsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.NODE,
            arguments = listOf(
                navArgument("node") { type = NavType.StringType },
            ),
        ) { entry ->
            val node = entry.arguments?.getString("node") ?: return@composable
            LaunchedEffect(isTwoPane, node) {
                if (isTwoPane) {
                    selectedDetail = DetailPaneSelection.Node(node)
                    val popped = navController.popBackStack(Routes.HOME, inclusive = false)
                    if (!popped) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
            if (isTwoPane) return@composable

            val vm: NodeDetailViewModel = viewModel(
                factory = NodeDetailViewModel.Factory(app.repository, node),
            )
            NodeDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenConsole = { cmd ->
                    navController.navigate(
                        Routes.console(node, GuestType.NODE.path, 0L, node, cmd ?: "shell"),
                    )
                },
            )
        }

        composable(
            route = Routes.STORAGE,
            arguments = listOf(
                navArgument("node") { type = NavType.StringType },
                navArgument("storage") { type = NavType.StringType },
            ),
        ) { entry ->
            val node = entry.arguments?.getString("node") ?: return@composable
            val storage = entry.arguments?.getString("storage") ?: return@composable
            LaunchedEffect(isTwoPane, node, storage) {
                if (isTwoPane) {
                    selectedDetail = DetailPaneSelection.Storage(node, storage)
                    val popped = navController.popBackStack(Routes.HOME, inclusive = false)
                    if (!popped) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
            if (isTwoPane) return@composable

            val vm: StorageDetailViewModel = viewModel(
                factory = StorageDetailViewModel.Factory(app.repository, node, storage),
            )
            StorageDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.GUEST,
            arguments = listOf(
                navArgument("node") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
                navArgument("vmid") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType },
            ),
        ) { entry ->
            val node = entry.arguments?.getString("node") ?: return@composable
            val type = entry.arguments?.getString("type") ?: return@composable
            val vmid = entry.arguments?.getLong("vmid") ?: return@composable
            val name = entry.arguments?.getString("name").orEmpty()
            LaunchedEffect(isTwoPane, node, type, vmid, name) {
                if (isTwoPane) {
                    selectedDetail = DetailPaneSelection.Guest(node, type, vmid, name)
                    val popped = navController.popBackStack(Routes.HOME, inclusive = false)
                    if (!popped) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }
            }
            if (isTwoPane) return@composable

            val guestType = remember(type) {
                GuestType.fromResourceType(type) ?: GuestType.QEMU
            }
            val vm: GuestDetailViewModel = viewModel(
                factory = GuestDetailViewModel.Factory(
                    app.repository,
                    node,
                    guestType,
                    vmid,
                    name,
                ),
            )
            GuestDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenConsole = {
                    navController.navigate(
                        Routes.console(node, type, vmid, name),
                    )
                },
                onOpenLogs = {
                    navController.navigate(Routes.LOG)
                },
            )
        }

        composable(
            route = Routes.CONSOLE,
            arguments = listOf(
                navArgument("node") { type = NavType.StringType },
                navArgument("type") { type = NavType.StringType },
                navArgument("vmid") { type = NavType.LongType },
                navArgument("name") { type = NavType.StringType },
                navArgument("cmd") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            val node = entry.arguments?.getString("node") ?: return@composable
            val type = entry.arguments?.getString("type") ?: return@composable
            val vmid = entry.arguments?.getLong("vmid") ?: return@composable
            val name = entry.arguments?.getString("name").orEmpty()
            val cmd = entry.arguments?.getString("cmd")
            val guestType = remember(type) {
                GuestType.fromResourceType(type) ?: GuestType.QEMU
            }
            val vm: ConsoleViewModel = viewModel(
                factory = ConsoleViewModel.Factory(
                    app.repository,
                    app.sessionStore,
                    node,
                    guestType,
                    vmid,
                    name,
                    cmd,
                ),
            )
            val state by vm.ui.collectAsStateWithLifecycle()
            BackHandler {
                navController.popBackStack()
            }
            when {
                state.loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.session != null -> {
                    ConsoleScreen(
                        session = state.session!!,
                        trustSelfSigned = state.trustSelfSigned,
                        expectedCertPin = state.certPin,
                        onBack = { navController.popBackStack() },
                        isTabletop = isTabletop,
                    )
                }
                else -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TechPlate(railColor = MaterialTheme.colorScheme.error) {
                            Column(
                                Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = (state.error ?: "Console unavailable").uppercase(),
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { vm.open() },
                                        shape = RoundedCornerShape(2.dp),
                                    ) {
                                        Text("RETRY", fontFamily = FontFamily.Monospace)
                                    }
                                    OutlinedButton(
                                        onClick = { navController.popBackStack() },
                                        shape = RoundedCornerShape(2.dp),
                                    ) {
                                        Text("BACK", fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
