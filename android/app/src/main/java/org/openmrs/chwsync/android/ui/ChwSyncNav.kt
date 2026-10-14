package org.openmrs.chwsync.android.ui

import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.openmrs.chwsync.android.R

/** All navigation routes in one place. */
object Routes {
    const val PATIENTS = "patients"
    const val PATIENT_FORM = "patientForm?uuid={uuid}"
    const val PATIENT_DETAIL = "patientDetail/{uuid}"
    const val VISIT_FORM = "visitForm/{patientUuid}"
    const val ENCOUNTER_FORM = "encounterForm/{patientUuid}"
    const val HOUSEHOLDS = "households"
    const val APPOINTMENTS = "appointments"
    const val REFERRALS = "referrals"
    const val SYNC = "sync"

    fun patientForm(uuid: String?): String =
        if (uuid.isNullOrBlank()) "patientForm" else "patientForm?uuid=$uuid"

    fun patientDetail(uuid: String) = "patientDetail/$uuid"
    fun visitForm(patientUuid: String) = "visitForm/$patientUuid"
    fun encounterForm(patientUuid: String) = "encounterForm/$patientUuid"
}

private data class TopLevelDestination(
    val route: String,
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.PATIENTS, R.string.nav_patients, Icons.Filled.People),
    TopLevelDestination(Routes.HOUSEHOLDS, R.string.nav_households, Icons.Filled.Home),
    TopLevelDestination(Routes.APPOINTMENTS, R.string.nav_appointments, Icons.Filled.Event),
    TopLevelDestination(Routes.REFERRALS, R.string.nav_referrals, Icons.AutoMirrored.Filled.Send),
    TopLevelDestination(Routes.SYNC, R.string.nav_sync, Icons.Filled.Sync),
)

/**
 * Root switch between the login gate and the main app. Auth state is `null`
 * until DataStore has been read, so we show a blank loading frame rather than
 * flashing the login screen.
 */
@Composable
fun ChwSyncAppRoot(appViewModel: AppViewModel) {
    val auth by appViewModel.authState.collectAsStateWithLifecycle()
    when {
        auth == null -> LoadingScreen()
        !auth!!.isLoggedIn -> LoginScreen(appViewModel)
        else -> ChwSyncScaffold(appViewModel)
    }
}

@Composable
private fun LoadingScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChwSyncScaffold(appViewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute }
    val online by appViewModel.online.collectAsStateWithLifecycle()
    val pending by appViewModel.pendingCount.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    Icon(
                        imageVector = if (online) Icons.Filled.Wifi else Icons.Filled.WifiOff,
                        contentDescription = null,
                        tint = if (online) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    PendingBadge(pending)
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val selected = backStackEntry?.destination?.hierarchy
                            ?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(destination.route) {
                                        popUpTo(Routes.PATIENTS) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(stringResource(destination.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.PATIENTS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.PATIENTS) {
                PatientsScreen(appViewModel, navController)
            }
            composable(Routes.HOUSEHOLDS) {
                HouseholdsScreen(navController)
            }
            composable(Routes.APPOINTMENTS) {
                AppointmentsScreen(navController)
            }
            composable(Routes.REFERRALS) {
                ReferralsScreen(navController)
            }
            composable(Routes.SYNC) {
                SyncScreen(appViewModel)
            }
            composable(
                route = Routes.PATIENT_FORM,
                arguments = listOf(
                    navArgument("uuid") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
            ) { entry ->
                PatientFormScreen(
                    appViewModel = appViewModel,
                    patientUuid = entry.arguments?.getString("uuid"),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.PATIENT_DETAIL,
                arguments = listOf(navArgument("uuid") { type = NavType.StringType }),
            ) { entry ->
                PatientDetailScreen(
                    patientUuid = entry.arguments?.getString("uuid").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onEdit = { uuid -> navController.navigate(Routes.patientForm(uuid)) },
                    onAddVisit = { uuid -> navController.navigate(Routes.visitForm(uuid)) },
                    onAddEncounter = { uuid -> navController.navigate(Routes.encounterForm(uuid)) },
                )
            }
            composable(
                route = Routes.VISIT_FORM,
                arguments = listOf(navArgument("patientUuid") { type = NavType.StringType }),
            ) { entry ->
                VisitFormScreen(
                    appViewModel = appViewModel,
                    patientUuid = entry.arguments?.getString("patientUuid").orEmpty(),
                    onDone = { navController.popBackStack() },
                )
            }
            composable(
                route = Routes.ENCOUNTER_FORM,
                arguments = listOf(navArgument("patientUuid") { type = NavType.StringType }),
            ) { entry ->
                EncounterFormScreen(
                    appViewModel = appViewModel,
                    patientUuid = entry.arguments?.getString("patientUuid").orEmpty(),
                    onDone = { navController.popBackStack() },
                )
            }
        }
    }
}

@Composable
private fun PendingBadge(count: Int) {
    if (count <= 0) return
    BadgedBox(badge = { Badge { Text(count.toString()) } }) {
        Icon(Icons.Filled.CloudUpload, contentDescription = null)
    }
}
