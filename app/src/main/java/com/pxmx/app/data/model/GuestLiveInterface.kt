package com.pxmx.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class GuestLiveAddress(
    val ip: String,
    val type: String, // "ipv4" or "ipv6"
    val prefix: Int? = null,
)

@Serializable
data class GuestLiveInterface(
    val name: String,
    val mac: String? = null,
    val addresses: List<GuestLiveAddress> = emptyList(),
)
