package com.lumen.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "nodes", indices = [Index("subscriptionId")])
data class NodeEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val protocol: String,
    val server: String,
    val port: Int,
    val link: String,
    val outboundJson: String = "",
    val pingMs: Int? = null,
    val subscriptionId: String? = null,
    val isAutoNode: Boolean = false,
    // Favourited by the user. Favourites are never removed by subscription refresh, no-ping cleanup or auto-delete.
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false
)
