package com.daviddelgado.agenda.feature.tasks.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * "Tombstone" de un borrado hecho sin red: se guarda el id de la tarea borrada localmente
 * hasta que el borrado se confirme en el servidor. Sin esto, una tarea borrada offline
 * podia reaparecer en la siguiente sincronizacion porque el `GET /tasks` seguia devolviendola
 * (limitacion documentada en ESTADO_PROYECTO.md antes de esta sesion).
 */
@Entity(tableName = "pending_deletions")
data class PendingDeletionEntity(
    @PrimaryKey val id: String,
)
