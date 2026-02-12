package com.pocketssh.app.ui.terminal

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pocketssh.app.R
import com.pocketssh.app.data.model.TmuxSession

/**
 * Modal bottom sheet for managing tmux sessions on the connected server.
 * Lists sessions with attach/detach actions, swipe-to-kill, and new session creation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionManagerSheet(
    sessions: List<TmuxSession>,
    currentSessionName: String?,
    serverName: String,
    onAttach: (String) -> Unit,
    onDetach: () -> Unit,
    onKill: (String) -> Unit,
    onNewSession: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sessionToKill by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = if (serverName.isNotEmpty()) {
                    stringResource(R.string.sessions_on_server, serverName)
                } else {
                    stringResource(R.string.sessions_title)
                },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            if (sessions.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_active_sessions),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    items(
                        items = sessions,
                        key = { it.name },
                    ) { session ->
                        val isActive = session.name == currentSessionName

                        SwipeableSessionCard(
                            session = session,
                            isActive = isActive,
                            onAction = {
                                if (isActive) onDetach() else onAttach(session.name)
                            },
                            onKill = { sessionToKill = session.name },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            TextButton(
                onClick = onNewSession,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
            ) {
                Text(stringResource(R.string.new_session))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Kill confirmation dialog
    sessionToKill?.let { name ->
        AlertDialog(
            onDismissRequest = { sessionToKill = null },
            title = { Text(stringResource(R.string.kill_session_title)) },
            text = { Text(stringResource(R.string.kill_session_message, name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onKill(name)
                        sessionToKill = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.kill_session),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToKill = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/**
 * Session card with swipe-left-to-kill action and primary color accent for active session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableSessionCard(
    session: TmuxSession,
    isActive: Boolean,
    onAction: () -> Unit,
    onKill: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onKill()
            }
            false
        },
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.dismissDirection) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surface
                },
                label = "sessionSwipeBg",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color, MaterialTheme.shapes.medium)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.kill_session),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        SessionCard(
            session = session,
            isActive = isActive,
            onAction = onAction,
        )
    }
}

/**
 * Individual session card showing name, window count, activity, and attach/detach action.
 */
@Composable
private fun SessionCard(
    session: TmuxSession,
    isActive: Boolean,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.outlinedCardColors(),
        border = if (isActive) {
            CardDefaults.outlinedCardBorder().copy(
                width = 2.dp,
            )
        } else {
            CardDefaults.outlinedCardBorder()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isActive) {
                        Modifier
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                            )
                            // Primary left border accent (4dp)
                            .padding(start = 4.dp)
                    } else Modifier
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Text(
                            text = "* ",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = session.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(
                            if (session.windowCount == 1) "1 window"
                            else "${session.windowCount} windows"
                        )
                        append(" | ")
                        val timeText = DateUtils.getRelativeTimeSpanString(
                            session.lastActivity,
                            System.currentTimeMillis(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ).toString()
                        append(
                            if (session.isAttached) "attached $timeText"
                            else "detached $timeText"
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onAction) {
                Text(
                    if (isActive) stringResource(R.string.detach)
                    else stringResource(R.string.attach)
                )
            }
        }
    }
}
