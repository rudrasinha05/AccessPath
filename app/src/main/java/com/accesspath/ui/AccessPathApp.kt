package com.accesspath.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.accesspath.R
import com.accesspath.ui.theme.AccessPathTheme

private enum class Tab(val label: Int, val icon: ImageVector) {
    NAVIGATE(R.string.tab_navigate, Icons.Filled.Place),
    REPORTS(R.string.tab_reports, Icons.Filled.Warning),
    MANAGE(R.string.tab_manage, Icons.Filled.Build),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings)
}

@Composable
fun AccessPathApp(vm: AccessViewModel) {
    AccessPathTheme {
        val ui by vm.ui.collectAsStateWithLifecycle()
        val context = LocalContext.current
        val snackbar = remember { SnackbarHostState() }
        var tab by rememberSaveable { mutableStateOf(Tab.NAVIGATE) }

        LaunchedEffect(ui.message) {
            ui.message?.let {
                vm.message(null)
                snackbar.showSnackbar(it.text(context), withDismissAction = true)
            }
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t, onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(stringResource(t.label)) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
                when {
                    ui.loadError -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Banner(stringResource(R.string.error_generic), warning = true)
                    }
                    ui.snapshot == null -> Column(
                        Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.loading))
                    }
                    else -> when (tab) {
                        Tab.NAVIGATE -> NavigateScreen(ui, vm)
                        Tab.REPORTS -> ReportsScreen(ui, vm)
                        Tab.MANAGE -> ManageScreen(ui, vm)
                        Tab.SETTINGS -> SettingsScreen(ui, vm)
                    }
                }
            }
        }
    }
}
