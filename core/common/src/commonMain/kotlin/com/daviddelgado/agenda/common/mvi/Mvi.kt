package com.daviddelgado.agenda.common.mvi

/** Marca un estado inmutable de pantalla (el "Model" de MVI). */
interface UiState

/** Marca una intención disparada por el usuario (el "Intent" de MVI). */
interface UiIntent

/** Marca un efecto de un solo uso: navegacion, snackbar, etc. */
interface UiEffect
