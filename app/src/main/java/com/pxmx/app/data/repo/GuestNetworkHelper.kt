package com.pxmx.app.data.repo

import com.pxmx.app.data.model.GuestLiveAddress
import com.pxmx.app.data.model.GuestLiveInterface
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object GuestNetworkHelper {

    suspend fun fetchLiveInterfaces(
        pveClient: PveClient,
        node: String,
        isQemu: Boolean,
        vmid: Long,
    ): List<GuestLiveInterface> {
        return try {
            val response = pveClient.apiCall { api ->
                if (isQemu) {
                    api.qemuAgentNetworkInterfaces(node, vmid)
                } else {
                    api.lxcNetworkInterfaces(node, vmid)
                }
            }.getOrNull() ?: return emptyList()

            if (isQemu) {
                parseAgentInterfaces(response.data)
            } else {
                parseLxcInterfaces(response.data)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseAgentInterfaces(data: JsonElement?): List<GuestLiveInterface> {
        if (data == null) return emptyList()

        val array: JsonArray = when (data) {
            is JsonObject -> data["result"] as? JsonArray ?: return emptyList()
            is JsonArray -> data
            else -> return emptyList()
        }

        val result = mutableListOf<GuestLiveInterface>()
        for (item in array) {
            val obj = item as? JsonObject ?: continue
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isBlank() || name.equals("lo", ignoreCase = true) || name.startsWith("lo:")) {
                continue
            }
            val mac = obj["hardware-address"]?.jsonPrimitive?.contentOrNull?.trim()
            val addrsJson = obj["ip-addresses"] as? JsonArray ?: JsonArray(emptyList())
            val addresses = mutableListOf<GuestLiveAddress>()
            for (addrElem in addrsJson) {
                val addrObj = addrElem as? JsonObject ?: continue
                val ip = addrObj["ip-address"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (ip.isBlank() || ip == "127.0.0.1" || ip == "::1") continue
                val type = addrObj["ip-address-type"]?.jsonPrimitive?.contentOrNull?.trim() ?: "ipv4"
                val prefix = addrObj["prefix"]?.jsonPrimitive?.intOrNull
                addresses.add(GuestLiveAddress(ip = ip, type = type, prefix = prefix))
            }
            result.add(GuestLiveInterface(name = name, mac = mac, addresses = addresses))
        }
        return result
    }

    fun parseLxcInterfaces(data: JsonElement?): List<GuestLiveInterface> {
        if (data == null) return emptyList()

        val array: JsonArray = when (data) {
            is JsonObject -> data["result"] as? JsonArray ?: return emptyList()
            is JsonArray -> data
            else -> return emptyList()
        }

        val result = mutableListOf<GuestLiveInterface>()
        for (item in array) {
            val obj = item as? JsonObject ?: continue
            val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
            if (name.isBlank() || name.equals("lo", ignoreCase = true) || name.startsWith("lo:")) {
                continue
            }
            val mac = obj["hwaddr"]?.jsonPrimitive?.contentOrNull?.trim()
            val addresses = mutableListOf<GuestLiveAddress>()

            val inet = obj["inet"]?.jsonPrimitive?.contentOrNull?.trim()
            if (!inet.isNullOrBlank()) {
                val ip = inet.substringBefore('/')
                if (ip != "127.0.0.1") {
                    val prefix = inet.substringAfter('/', "").toIntOrNull()
                    addresses.add(GuestLiveAddress(ip = ip, type = "ipv4", prefix = prefix))
                }
            }

            val inet6 = obj["inet6"]?.jsonPrimitive?.contentOrNull?.trim()
            if (!inet6.isNullOrBlank()) {
                val ip = inet6.substringBefore('/')
                if (ip != "::1") {
                    val prefix = inet6.substringAfter('/', "").toIntOrNull()
                    addresses.add(GuestLiveAddress(ip = ip, type = "ipv6", prefix = prefix))
                }
            }

            result.add(GuestLiveInterface(name = name, mac = mac, addresses = addresses))
        }
        return result
    }
}

