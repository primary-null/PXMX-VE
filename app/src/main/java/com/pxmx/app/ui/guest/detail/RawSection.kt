package com.pxmx.app.ui.guest.detail

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pxmx.app.data.model.ParsedGuestConfig

@Composable
fun RawBody(cfg: ParsedGuestConfig?) {
    if (cfg == null) {
        Text("No config")
        return
    }
    cfg.raw.entries.sortedBy { it.key }.forEach { (k, v) ->
        Text(
            "$k: $v",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(vertical = 2.dp),
        )
    }
}
