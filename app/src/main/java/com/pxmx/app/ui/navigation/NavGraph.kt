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

@Composable
fun ProxmoxNavGraph() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as ProxmoxApp
    var splashStatus by remember { mutableStateOf("Starting…") }

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
            )
        }

        composable(Routes.SETTINGS) {
            val session by app.sessionStore.session.collectAsStateWithLifecycle()
            val themeMode by app.sessionStore.themeMode.collectAsStateWithLifecycle()
            SettingsScreen(
                hostDisplay = session?.config?.displayHost ?: "—",
                versionDisplay = session?.version?.display ?: "—",
                themeMode = themeMode,
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
            )
        }

        composable(Routes.LOG) {
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
            val vm: NetworkViewModel = viewModel(
                factory = NetworkViewModel.Factory(app.repository),
            )
            NetworkScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.SDN) {
            val vm: SdnViewModel = viewModel(
                factory = SdnViewModel.Factory(app.repository),
            )
            SdnScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.FIREWALL) {
            val vm: FirewallViewModel = viewModel(
                factory = FirewallViewModel.Factory(app.repository),
            )
            FirewallScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(Routes.UPDATES) {
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
