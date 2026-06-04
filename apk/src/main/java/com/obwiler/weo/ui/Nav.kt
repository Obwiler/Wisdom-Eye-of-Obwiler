package com.obwiler.weo.ui

sealed class Screen {
    data object Home : Screen()
    data object Camera : Screen()
    data class Result(val answer: String, val steps: List<String>, val photoPath: String?) : Screen()
    data object Settings : Screen()
    data object About : Screen()
    data object Gallery : Screen()
    data object Wifi : Screen()
}
