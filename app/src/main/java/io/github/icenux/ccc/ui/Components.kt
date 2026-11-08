package io.github.icenux.ccc.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.icenux.ccc.R
import io.github.icenux.ccc.data.ConversationStatus
import io.github.icenux.ccc.data.Permission
import io.github.icenux.ccc.data.Usage
import io.github.icenux.ccc.data.UsageWindow
import io.github.icenux.ccc.ui.theme.CccColors
import io.github.icenux.ccc.ui.theme.CccFonts
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

fun statusColor(status: ConversationStatus, needsApproval: Boolean = false): Color = when {
    needsApproval -> CccColors.Approval
    status == ConversationStatus.RUNNING -> CccColors.Running
    status == ConversationStatus.WAITING_RETRY -> CccColors.Blue
    status == ConversationStatus.PAUSED_LIMIT -> CccColors.Amber
    status == ConversationStatus.BLOCKED_ERROR -> CccColors.Red
    else -> CccColors.Dim
}

@Composable
fun statusLabel(status: ConversationStatus): String = stringResource(
    when (status) {
        ConversationStatus.IDLE -> R.string.status_idle
        ConversationStatus.RUNNING -> R.string.status_running
        ConversationStatus.WAITING_RETRY -> R.string.status_waiting_retry
        ConversationStatus.BLOCKED_ERROR -> R.string.status_blocked_error
        ConversationStatus.PAUSED_LIMIT -> R.string.status_paused_limit
        ConversationStatus.UNKNOWN -> R.string.status_unknown
    },
)

@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier, pulse: Pulse = Pulse.NONE) {
    GlowDot(color, modifier, size = 8.dp, pulse = pulse)
}

/** "14:05" today, else "21. 10. 14:05" — in the phone's time zone. */
fun shortTime(iso: String?): String? {
    iso ?: return null
    val time = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()) }
        .recoverCatching { java.time.LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()) }
        .getOrNull() ?: return null
    val today = time.toLocalDate() == java.time.LocalDate.now()
    return time.format(DateTimeFormatter.ofPattern(if (today) "HH:mm" else "d. M. HH:mm"))
}

private val pretty = Json { prettyPrint = true }

fun prettyInput(input: JsonObject): String = pretty.encodeToString(JsonObject.serializer(), input)

/** Claude asks to use a tool: what and with which input, Allow / Always / Deny. It pops in on a spring and folds away once decided. */
@Composable
fun ApprovalCard(
    permission: Permission,
    conversationName: String?,
    onDecide: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var decided by androidx.compose.runtime.remember(permission.id) { androidx.compose.runtime.mutableStateOf(false) }
    var shown by androidx.compose.runtime.remember(permission.id) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(permission.id) { shown = true }
    val decide = { decision: String ->
        decided = true
        haptic.performHapticFeedback(
            if (decision == "deny") androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject
            else androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm,
        )
        onDecide(decision)
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = shown && !decided,
        enter = androidx.compose.animation.scaleIn(
            initialScale = 0.9f,
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
            ),
        ) + androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut(),
        modifier = modifier,
    ) {
        HudPanel(accent = CccColors.Amber, brackets = false, glow = true, contentPadding = PaddingValues(14.dp)) {
            Text(
                stringResource(R.string.approval_title, conversationName ?: stringResource(R.string.claude), permission.tool),
                style = MaterialTheme.typography.titleSmall,
                color = CccColors.Amber,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp)
                    .background(CccColors.Background, RoundedCornerShape(6.dp))
                    .border(1.dp, CccColors.Line, RoundedCornerShape(6.dp))
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp),
            ) {
                Text(
                    prettyInput(permission.input),
                    fontFamily = CccFonts.Mono,
                    style = MaterialTheme.typography.bodySmall,
                    color = CccColors.Cyan,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CccButton(onClick = { decide("allow") }) { Text(stringResource(R.string.allow)) }
                CccButton(onClick = { decide("always") }, kind = ButtonKind.SECONDARY) { Text(stringResource(R.string.always_allow)) }
                Spacer(Modifier.weight(1f))
                CccButton(onClick = { decide("deny") }, kind = ButtonKind.GHOST, color = CccColors.Red) { Text(stringResource(R.string.deny)) }
            }
        }
    }
}

/** The usage limits: 5-hour and 7-day windows. */
@Composable
fun UsageCard(usage: Usage, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        usage.fiveHour?.let { UsageRow(stringResource(R.string.usage_five_hour), it.percent) }
        usage.sevenDay?.let { UsageRow(stringResource(R.string.usage_seven_day), it.percent) }
    }
}

/**
 * For a Scaffold's bottomBar: the bar clears the gesture/navigation bar and the
 * keyboard. (A bottomBar gets no insets from the Scaffold, so with edge-to-edge
 * a text field in it sat half under the system bar.)
 */
@Composable
fun Modifier.bottomBarInsets(): Modifier =
    windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
