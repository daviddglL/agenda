package com.daviddelgado.agenda.shared

import androidx.compose.ui.window.ComposeUIViewController
import com.daviddelgado.agenda.shared.di.initKoin
import platform.UIKit.UIViewController

/** Llamado desde Swift (AppDelegate/App.swift) antes de mostrar la UI. */
fun doInitKoinIos() {
    initKoin()
}

fun mainViewController(): UIViewController = ComposeUIViewController { App() }
