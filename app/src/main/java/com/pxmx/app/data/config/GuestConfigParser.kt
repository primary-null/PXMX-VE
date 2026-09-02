package com.pxmx.app.data.config

import com.pxmx.app.data.model.ConfigDisk
import com.pxmx.app.data.model.ConfigNet
import com.pxmx.app.data.model.ConfigPci
import com.pxmx.app.data.model.ConfigUsb
import com.pxmx.app.data.model.HostUsbDevice
import com.pxmx.app.data.model.ParsedGuestConfig

object GuestConfigParser {

    private val diskKey = Regex(
        "^(ide|sata|scsi|virtio|nvme|efidisk|tpmstate|unused|mp|rootfs)\\d*$",
    )
    private val netKey = Regex("^net\\d+$")
    private val usbKey = Regex("^usb\\d+$")
    private val pciKey = Regex("^hostpci\\d+$")

    /** Keys whose values must never be shown in the UI (raw config, options, etc.). */
    private val secretKeys = setOf(
        "cipassword",
        "password",
        "sshkeys",
    )

    private val knownScalar = setOf(
        "name", "cores", "sockets", "cpu", "memory", "balloon", "ostype",
        "machine", "bios", "scsihw", "boot", "onboot", "agent", "tags",
        "description", "numa", "digest", "smbios1", "vmgenid", "meta",
        "parent", "lock", "template", "hotplug", "tablet", "vga", "serial0",
        "serial1", "serial2", "serial3", "keyboard", "args", "affinity",
        "cpuunits", "cpulimit", "shares", "startup", "protection", "hookscript",
        "cicustom", "cipassword", "citype", "ciuser", "ipconfig0", "nameserver",
        "searchdomain", "sshkeys", "hostname", "arch", "cmode", "console",
        "features", "force", "swap", "lxc", "unprivileged", "timezone",
    )

    fun parse(
        rawAny: Map<String, Any?>,
        hostUsbs: List<HostUsbDevice> = emptyList(),
    ): ParsedGuestConfig {
        val rawPlain = rawAny.mapNotNull { (k, v) ->
            if (v == null) null else k to pveString(v)
        }.toMap()
        // Display map only — secrets redacted so raw config UI cannot leak them.
        val raw = rawPlain.mapValues { (key, value) -> redactIfSecret(key, value) }

        val disks = mutableListOf<ConfigDisk>()
        val nets = mutableListOf<ConfigNet>()
        val usbs = mutableListOf<ConfigUsb>()
        val pcis = mutableListOf<ConfigPci>()
        val other = mutableListOf<Pair<String, String>>()

        for ((key, value) in raw.entries.sortedBy { it.key }) {
            when {
                diskKey.matches(key) -> disks += parseDisk(key, value)
                netKey.matches(key) -> nets += parseNet(key, value)
                usbKey.matches(key) -> usbs += parseUsb(key, value, hostUsbs)
                pciKey.matches(key) -> pcis += ConfigPci(key, value)
                key in knownScalar -> { /* handled below */ }
                else -> other += key to value
            }
        }

        return ParsedGuestConfig(
            raw = raw,
            name = raw["name"],
            cores = raw["cores"],
            sockets = raw["sockets"],
            cpu = raw["cpu"],
            memory = raw["memory"],
            balloon = raw["balloon"],
            ostype = raw["ostype"],
            machine = raw["machine"],
            bios = raw["bios"],
            scsihw = raw["scsihw"],
            boot = raw["boot"],
            onboot = raw["onboot"]?.let { it == "1" || it.equals("true", true) },
            agent = raw["agent"],
            tags = raw["tags"],
            description = raw["description"],
            numa = raw["numa"],
            digest = raw["digest"],
            disks = disks,
            nets = nets,
            usbs = usbs,
            pcis = pcis,
            other = other,
        )
    }

    private fun parseKv(value: String): Pair<String, Map<String, String>> {
        val parts = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return "" to emptyMap()
        val head = parts.first()
        val map = linkedMapOf<String, String>()
        for (p in parts.drop(1)) {
            val eq = p.indexOf('=')
            if (eq == -1) {
                if (p.isNotBlank()) map[p] = "1"
            } else if (eq >= 0) {
                val k = p.substring(0, eq).trim()
                if (k.isNotEmpty()) {
                    map[k] = p.substring(eq + 1)
                }
            }
        }
        // also allow key=value in head for some cases, but NOT for LXC name=
        if (head.contains('=') && !head.contains(':') && !head.contains('/') && !head.startsWith("name=")) {
            val eq = head.indexOf('=')
            if (eq != -1) {
                val k = head.substring(0, eq).trim()
                if (k.isNotEmpty()) {
                    map[k] = head.substring(eq + 1)
                    return "" to map
                }
            }
        }
        return head to map
    }

    private fun parseDisk(key: String, value: String): ConfigDisk {
        val (volume, opts) = parseKv(value)
        val storage = volume.substringBefore(':', missingDelimiterValue = "").ifBlank { null }
        return ConfigDisk(
            key = key,
            raw = value,
            storage = storage,
            volume = volume.ifBlank { null },
            size = opts["size"],
            media = opts["media"],
            cache = opts["cache"],
            iothread = opts["iothread"] == "1",
            discard = opts["discard"] == "on" || opts["discard"] == "1",
            ssd = opts["ssd"] == "1",
            backup = opts["backup"]?.let { it != "0" },
            extra = opts,
        )
    }

    private fun parseNet(key: String, value: String): ConfigNet {
        val (head, opts) = parseKv(value)

        if (head.startsWith("name=")) {
            // LXC: name=eth0,... type=veth hwaddr=xx
            return ConfigNet(
                key = key, raw = value,
                model = opts["type"] ?: "veth",
                mac = opts["hwaddr"] ?: opts["macaddr"] ?: opts["mac"],
                bridge = opts["bridge"], tag = opts["tag"],
                firewall = opts["firewall"] == "1", rate = opts["rate"],
                extra = opts,
            )
        }

        // head is often model=mac
        var model: String? = null
        var mac: String? = null
        if (head.contains('=')) {
            val eq = head.indexOf('=')
            model = head.substring(0, eq)
            mac = head.substring(eq + 1)
        } else if (head.isNotBlank()) {
            model = head
        }
        model = model ?: opts.keys.firstOrNull {
            it in setOf("virtio", "e1000", "e1000e", "rtl8139", "vmxnet3", "veth")
        }
        if (mac == null && model != null) {
            mac = opts[model] ?: opts["macaddr"]
        }
        return ConfigNet(
            key = key,
            raw = value,
            model = model,
            mac = mac,
            bridge = opts["bridge"],
            tag = opts["tag"],
            firewall = opts["firewall"] == "1",
            rate = opts["rate"],
            extra = opts,
        )
    }

    private fun parseUsb(
        key: String,
        value: String,
        hostUsbs: List<HostUsbDevice>,
    ): ConfigUsb {
        val (_, opts) = parseKv(value)
        val host = opts["host"]
            ?: value.removePrefix("host=").substringBefore(',').takeIf {
                value.startsWith("host=") || it.contains(':')
            }
        val resolved = host?.let { h ->
            val id = h.lowercase()
            hostUsbs.firstOrNull { dev ->
                dev.hostId?.lowercase() == id ||
                    "${dev.vendid}:${dev.prodid}".equals(h, true)
            }?.display
        }
        return ConfigUsb(
            key = key,
            raw = value,
            host = host,
            usb3 = opts["usb3"] == "1",
            resolvedName = resolved,
        )
    }

    /**
     * Gson often deserializes JSON numbers as Double. Proxmox wants "1" not "1.0".
     */
    private fun pveString(value: Any): String = when (value) {
        is Double -> if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
        is Float -> if (value % 1f == 0f) value.toLong().toString() else value.toString()
        is Number -> value.toString()
        else -> value.toString()
    }

    private fun redactIfSecret(key: String, value: String): String {
        val k = key.lowercase()
        if (k in secretKeys || k.contains("password") || k.contains("secret") || k.contains("token")) {
            return if (value.isBlank()) value else "••••••••"
        }
        return value
    }
}
