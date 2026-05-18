package com.verdantgem.ledger.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry

object AppTransitions {
    private val mainRoutes = listOf("dashboard", "statistics", "about")

    fun enterTransition(initialState: NavBackStackEntry, targetState: NavBackStackEntry): EnterTransition {
        val initialRoute = initialState.destination.route
        val targetRoute = targetState.destination.route
        
        val initialIndex = mainRoutes.indexOf(initialRoute)
        val targetIndex = mainRoutes.indexOf(targetRoute)

        return if (initialIndex != -1 && targetIndex != -1) {
            // 主标签页切换
            if (targetIndex > initialIndex) {
                slideInHorizontally(animationSpec = tween(400)) { it } + fadeIn()
            } else {
                slideInHorizontally(animationSpec = tween(400)) { -it } + fadeIn()
            }
        } else {
            // 进入子页面：统一从右滑入
            slideInHorizontally(animationSpec = tween(400)) { it } + fadeIn()
        }
    }

    fun exitTransition(initialState: NavBackStackEntry, targetState: NavBackStackEntry): ExitTransition {
        val initialRoute = initialState.destination.route
        val targetRoute = targetState.destination.route
        
        val initialIndex = mainRoutes.indexOf(initialRoute)
        val targetIndex = mainRoutes.indexOf(targetRoute)

        return if (initialIndex != -1 && targetIndex != -1) {
            // 主标签页切换
            if (targetIndex > initialIndex) {
                slideOutHorizontally(animationSpec = tween(400)) { -it } + fadeOut()
            } else {
                slideOutHorizontally(animationSpec = tween(400)) { it } + fadeOut()
            }
        } else {
            // 离开主页进入子页：主页向左偏移退出
            slideOutHorizontally(animationSpec = tween(400)) { -it } + fadeOut()
        }
    }

    fun popEnterTransition(): EnterTransition {
        // 返回时：从左滑入
        return slideInHorizontally(animationSpec = tween(400)) { -it } + fadeIn()
    }

    fun popExitTransition(): ExitTransition {
        // 退出子页时：向右滑出
        return slideOutHorizontally(animationSpec = tween(400)) { it } + fadeOut()
    }
}
