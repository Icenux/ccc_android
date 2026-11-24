package io.github.icenux.ccc.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.Pairing
import io.github.icenux.ccc.ui.theme.CccColors

/**
 * The phone is paired with a ccc that doesn't answer (#72) — typically the computer was reinstalled and
 * the old address is gone. Instead of loading for ever the person decides: try the same server again, or
 * connect to another computer (the connection screen with the QR scan). The old pairing is kept until a
 * new one succeeds.
 */
@Composable
fun ServerUnreachable(
    server: String,
    onRetry: () -> Unit,
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.unreachable_title),
            style = MaterialTheme.typography.titleMedium,
            color = CccColors.Amber,
            textAlign = TextAlign.Center,
        )
        Text(
            stringResource(R.string.unreachable_text, Pairing.origin(server) ?: server),
            Modifier.padding(top = 12.dp, bottom = 24.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = CccColors.Muted,
            textAlign = TextAlign.Center,
        )
        CccButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.unreachable_retry))
        }
        CccButton(kind = ButtonKind.SECONDARY, onClick = onChangeServer, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(stringResource(R.string.unreachable_change))
        }
    }
}
