package com.looknote.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoFilter
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("recipes", "Công thức", Icons.Filled.Palette),
    Tab("analyze", "Đọc ảnh", Icons.Filled.Image),
    Tab("frame", "Khung ảnh", Icons.Filled.PhotoFilter),
    Tab("camera", "Máy ảnh", Icons.Filled.Usb),
)

@Composable
fun LookNoteRoot() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val showBar = tabs.any { it.route == route }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    tabs.forEach { t ->
                        NavigationBarItem(
                            selected = route == t.route,
                            onClick = {
                                nav.navigate(t.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = "recipes",
            modifier = Modifier.padding(bottom = inner.calculateBottomPadding()),
        ) {
            composable("recipes") {
                RecipesScreen(
                    onOpen = { id -> nav.navigate("recipe/$id") },
                    onNew = { nav.navigate("edit/new") },
                )
            }
            composable(
                "recipe/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e ->
                val id = e.arguments?.getString("id").orEmpty()
                RecipeDetailScreen(
                    id = id,
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate("edit/$id") },
                    onDuplicate = { nav.navigate("edit/new") },
                )
            }
            composable(
                "edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { e ->
                val id = e.arguments?.getString("id").orEmpty()
                RecipeEditScreen(
                    id = id,
                    onDone = { savedId ->
                        nav.popBackStack()
                        if (id == "new" && savedId != null) nav.navigate("recipe/$savedId")
                    },
                )
            }
            composable("analyze") {
                AnalyzeScreen(onCreateRecipe = { nav.navigate("edit/new") })
            }
            composable("frame") { FrameScreen() }
            composable("camera") { CameraScreen() }
        }
    }
}
