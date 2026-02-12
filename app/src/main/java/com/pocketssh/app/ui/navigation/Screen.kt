package com.pocketssh.app.ui.navigation

sealed class Screen(val route: String) {

    data object ConnectionList : Screen("connection_list")

    data object ConnectionEditor : Screen("connection_editor/{profileId}") {
        fun createRoute(profileId: Long? = null): String =
            "connection_editor/${profileId ?: -1}"
    }

    data object Terminal : Screen("terminal/{profileId}") {
        fun createRoute(profileId: Long): String =
            "terminal/$profileId"
    }

    data object Settings : Screen("settings")

    data object KeyManager : Screen("key_manager")
}
