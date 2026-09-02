package com.pxmx.app.data.api

import com.pxmx.app.data.model.GuestType
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Interactive shell simulator for demo mode.
 * Provides canned command outputs and interactive HTML/JS terminal for WebViews.
 */
object DemoShell {

    val CANNED_COMMANDS: Map<String, (node: String) -> String> = mapOf(
        "help" to { _ ->
            """
            Available demo shell commands:
              help                           - Show this help message
              uptime                         - Show system uptime and load average
              ls                             - List directory contents
              pveversion                     - Show Proxmox VE version
              date                           - Show current system date and time
              whoami                         - Show current user
              apt list --upgradable          - List packages available for upgrade
              systemctl status pve-cluster   - Show cluster filesystem service status
            """.trimIndent()
        },
        "uptime" to { _ ->
            " 19:45:00 up 14 days,  3:22,  1 user,  load average: 0.12, 0.08, 0.05 (demo)"
        },
        "ls" to { _ ->
            "backup  cluster-config.yaml  pve-firewall.conf  templates"
        },
        "pveversion" to { _ ->
            "pve-manager/8.3.0/e33d45e5a (running kernel: 6.8.12-2-pve) (demo)"
        },
        "date" to { _ ->
            "Tue Sep  1 19:45:00 UTC 2026 (demo)"
        },
        "whoami" to { _ ->
            "root"
        },
        "apt list --upgradable" to { _ ->
            """
            Listing... Done
            corosync/stable 3.1.7-pve3 amd64 [upgradable from: 3.1.7-pve1]
            libpve-common-perl/stable 8.2.8 amd64 [upgradable from: 8.2.4]
            proxmox-widget-toolkit/stable 4.2.3 all [upgradable from: 4.2.1]
            pve-manager/stable 8.3.0 amd64 [upgradable from: 8.2.9]
            """.trimIndent()
        },
        "systemctl status pve-cluster" to { _ ->
            """
            ● pve-cluster.service - The Proxmox VE cluster filesystem
                 Loaded: loaded (/lib/systemd/system/pve-cluster.service; enabled; preset: enabled)
                 Active: active (running) since Tue 2026-08-18 15:20:00 UTC; 14 days ago
                Process: 1234 ExecStart=/usr/bin/pmxcfs (code=exited, status=0/SUCCESS)
               Main PID: 1235 (pmxcfs)
                  Tasks: 6 (limit: 76865)
                 Memory: 42.1M
                    CPU: 1min 12.345s
                 CGroup: /system.slice/pve-cluster.service
                         └─1235 /usr/bin/pmxcfs
            """.trimIndent()
        },
    )

    fun execute(command: String, node: String = "alpha"): String {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return ""

        val handler = CANNED_COMMANDS[trimmed.lowercase()]
            ?: CANNED_COMMANDS.entries.firstOrNull { it.key.equals(trimmed, ignoreCase = true) }?.value

        return if (handler != null) {
            handler(node)
        } else {
            "$trimmed: command not found (demo shell)"
        }
    }

    fun generateHtml(
        node: String,
        guestType: GuestType,
        vmid: Long,
        name: String,
    ): String {
        val titleText = if (guestType == GuestType.NODE) {
            "Linux $node 6.8.12-2-pve (demo shell)"
        } else {
            "$name ($vmid) console (demo)"
        }
        val promptUser = if (guestType == GuestType.NODE) "root@$node:~#" else "root@$name:~#"

        val jsCommandsObject = buildString {
            append("{\n")
            CANNED_COMMANDS.forEach { (cmd, func) ->
                val out = func(node).replace("\\", "\\\\").replace("`", "\\`").replace("$", "\\$")
                append("  \"${cmd.lowercase()}\": `")
                append(out)
                append("`,\n")
            }
            append("}")
        }

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
            <style>
            * { box-sizing: border-box; }
            html, body {
                background: #0c0d0e;
                color: #d1d5db;
                font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
                font-size: 13px;
                line-height: 1.4;
                padding: 14px;
                margin: 0;
                min-height: 100vh;
                cursor: text;
            }
            .banner { color: #9ca3af; margin-bottom: 3px; }
            .banner-hint { color: #6b7280; margin-top: 4px; margin-bottom: 12px; }
            .prompt { color: #64ffda; font-weight: bold; white-space: nowrap; }
            .cmd-line { margin: 3px 0; }
            .cmd-input-text { color: #ffffff; }
            .cmd-output { white-space: pre-wrap; margin: 3px 0 8px 0; color: #d1d5db; }
            .input-row { display: flex; align-items: center; margin-top: 4px; }
            #term-form { display: flex; flex: 1; margin: 0; padding: 0; }
            #term-input {
                flex: 1;
                background: transparent;
                border: none;
                outline: none;
                color: #ffffff;
                font-family: inherit;
                font-size: inherit;
                line-height: inherit;
                padding: 0 0 0 6px;
                margin: 0;
                caret-color: #64ffda;
            }
            </style>
            </head>
            <body>
            <div class="banner">$titleText</div>
            <div class="banner">Welcome to Proxmox VE Terminal (Demo Session)</div>
            <div class="banner">Last login: Tue Sep 1 18:24:00 2026 on pts/0</div>
            <div class="banner-hint">Type 'help' for available demo commands.</div>

            <div id="history"></div>

            <div class="input-row">
                <span class="prompt">$promptUser</span>
                <form id="term-form" onsubmit="return handleFormSubmit(event);">
                    <input id="term-input" type="text" autocomplete="off" autocorrect="off" autocapitalize="off" spellcheck="false" autofocus />
                </form>
            </div>

            <script>
            var commands = $jsCommandsObject;
            var promptStr = "$promptUser";

            function escapeHtml(text) {
                var div = document.createElement('div');
                div.textContent = text;
                return div.innerHTML;
            }

            function handleFormSubmit(e) {
                if (e) e.preventDefault();
                var input = document.getElementById('term-input');
                var val = input.value;
                var trimmed = val.trim();

                var history = document.getElementById('history');

                var lineDiv = document.createElement('div');
                lineDiv.className = 'cmd-line';
                lineDiv.innerHTML = '<span class="prompt">' + escapeHtml(promptStr) + '</span> <span class="cmd-input-text">' + escapeHtml(val) + '</span>';
                history.appendChild(lineDiv);

                if (trimmed.length > 0) {
                    var lower = trimmed.toLowerCase();
                    var output = commands[lower];
                    if (output === undefined) {
                        output = trimmed + ': command not found (demo shell)';
                    }
                    if (output.length > 0) {
                        var outDiv = document.createElement('div');
                        outDiv.className = 'cmd-output';
                        outDiv.innerHTML = escapeHtml(output);
                        history.appendChild(outDiv);
                    }
                }

                input.value = '';
                window.scrollTo(0, document.body.scrollHeight);
                return false;
            }

            document.addEventListener('click', function(e) {
                var input = document.getElementById('term-input');
                if (input && document.activeElement !== input) {
                    input.focus();
                }
            });

            window.addEventListener('load', function() {
                var input = document.getElementById('term-input');
                if (input) {
                    input.focus();
                }
            });
            </script>
            </body>
            </html>
        """.trimIndent()

        return "data:text/html;charset=utf-8," + URLEncoder.encode(html, StandardCharsets.UTF_8.toString())
    }
}
