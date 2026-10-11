package com.pxmx.app.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ClusterMember(
    val host: String,
    val nodeName: String? = null,
)

@Serializable
data class KnownCluster(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val members: List<ClusterMember> = emptyList(),
    val lastSeenEpochMs: Long = System.currentTimeMillis(),
)
