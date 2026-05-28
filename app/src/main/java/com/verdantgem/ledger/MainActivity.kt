package com.verdantgem.ledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.verdantgem.ledger.ui.screens.budget.BudgetEditScreen
import com.verdantgem.ledger.ui.screens.dashboard.DashboardScreen
import com.verdantgem.ledger.ui.screens.record.AddRecordScreen
import com.verdantgem.ledger.ui.screens.record.CategoryEditDetailScreen
import com.verdantgem.ledger.ui.screens.record.CategoryEditScreen
import com.verdantgem.ledger.ui.screens.record.RecordDetailScreen
import com.verdantgem.ledger.ui.screens.record.CategoryViewModel
import com.verdantgem.ledger.ui.screens.settings.SettingsViewModel
import com.verdantgem.ledger.ui.screens.settings.ThemeSettingScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.verdantgem.ledger.ui.theme.LedgerTheme
import com.verdantgem.ledger.ui.theme.ThemeMode
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.verdantgem.ledger.ui.AppTransitions
import com.verdantgem.ledger.ui.screens.statistics.CategoryRecordsScreen
import java.net.URLEncoder

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val categoryViewModel: CategoryViewModel = hiltViewModel()
            val themeMode by settingsViewModel.themeMode
            val categories by categoryViewModel.allCategories.collectAsState()
            
            LedgerTheme(themeMode = themeMode) {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    bottomBar = {
                        val showBottomBar = currentRoute in listOf("dashboard", "statistics", "about")
                        if (showBottomBar) {
                            NavigationBar(containerColor = Color.Transparent) {
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.Home, contentDescription = "首页") },
                                    label = { Text("总览") },
                                    selected = currentRoute == "dashboard",
                                    onClick = { 
                                        navController.navigate("dashboard") {
                                            popUpTo("dashboard") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.DateRange, contentDescription = "统计") },
                                    label = { Text("统计") },
                                    selected = currentRoute == "statistics",
                                    onClick = { 
                                        navController.navigate("statistics") {
                                            popUpTo("dashboard") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                                NavigationBarItem(
                                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = "关于") },
                                    label = { Text("关于") },
                                    selected = currentRoute == "about",
                                    onClick = { 
                                        navController.navigate("about") {
                                            popUpTo("dashboard") { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        enterTransition = { AppTransitions.enterTransition(initialState, targetState) },
                        exitTransition = { AppTransitions.exitTransition(initialState, targetState) },
                        popEnterTransition = { AppTransitions.popEnterTransition() },
                        popExitTransition = { AppTransitions.popExitTransition() }
                    ) {
                        composable("dashboard") { 
                            DashboardScreen(
                                onNavigateToAddRecord = { navController.navigate("add_record") },
                                onNavigateToRecordDetail = { recordId -> navController.navigate("record_detail/$recordId") },
                                onNavigateToBudget = { navController.navigate("budget_edit") },
                                innerPadding = innerPadding
                            ) 
                        }
                        composable(
                            "record_detail/{recordId}",
                            arguments = listOf(navArgument("recordId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val recordId = backStackEntry.arguments?.getLong("recordId") ?: 0L
                            RecordDetailScreen(
                                recordId = recordId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("add_record") {
                            AddRecordScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToCategoryEdit = { navController.navigate("category_edit") }
                            )
                        }
                        composable("category_edit") {
                            CategoryEditScreen(
                                onBack = { navController.popBackStack() },
                                categories = categories,
                                onAdd = { name, parent, income -> categoryViewModel.addCategory(name, parent, income) },
                                onNavigateToDetail = { id -> navController.navigate("category_edit_detail/$id") },
                                onDelete = { categoryViewModel.deleteCategory(it) },
                                onReset = { categoryViewModel.resetToDefault() }
                            )
                        }
                        composable(
                            "category_edit_detail/{categoryId}",
                            arguments = listOf(navArgument("categoryId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val categoryId = backStackEntry.arguments?.getLong("categoryId") ?: return@composable
                            CategoryEditDetailScreen(
                                categoryId = categoryId,
                                viewModel = categoryViewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("statistics") { 
                            Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                                com.verdantgem.ledger.ui.screens.statistics.StatisticsScreen(
                                    onNavigateToCategoryRecords = { name, isParent, start, end, isIncome ->
                                        val encodedName = URLEncoder.encode(name, "UTF-8")
                                        navController.navigate("category_records/$encodedName/$isParent/$start/$end/$isIncome")
                                    }
                                )
                            }
                        }
                        composable("about") {
                            Box(modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())) {
                                com.verdantgem.ledger.ui.screens.about.AboutScreen(
                                    onNavigateToSettings = { navController.navigate("settings") }
                                )
                            }
                        }
                        composable("settings") {
                            com.verdantgem.ledger.ui.screens.settings.SettingsScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToWebDav = { navController.navigate("webdav_config") },
                                onNavigateToTheme = { navController.navigate("theme_setting") },
                                onNavigateToImportExport = { navController.navigate("import_export") },
                                themeMode = themeMode
                            )
                        }
                        composable("theme_setting") {
                            ThemeSettingScreen(
                                onBack = { navController.popBackStack() },
                                currentMode = themeMode,
                                onModeChange = { settingsViewModel.setThemeMode(it) }
                            )
                        }
                        composable("webdav_config") {
                            com.verdantgem.ledger.ui.screens.settings.WebDavConfigScreen(
                                onBack = { navController.popBackStack() },
                                settingsViewModel = settingsViewModel
                            )
                        }
                        composable("import_export") {
                            com.verdantgem.ledger.ui.screens.settings.ImportExportScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("budget_edit") {
                            BudgetEditScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            "category_records/{categoryName}/{isParent}/{startTime}/{endTime}/{isIncome}",
                            arguments = listOf(
                                navArgument("categoryName") { type = NavType.StringType },
                                navArgument("isParent") { type = NavType.BoolType },
                                navArgument("startTime") { type = NavType.LongType },
                                navArgument("endTime") { type = NavType.LongType },
                                navArgument("isIncome") { type = NavType.BoolType }
                            )
                        ) {
                            CategoryRecordsScreen(
                                onBack = { navController.popBackStack() },
                                onNavigateToRecordDetail = { recordId ->
                                    navController.navigate("record_detail/$recordId")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
