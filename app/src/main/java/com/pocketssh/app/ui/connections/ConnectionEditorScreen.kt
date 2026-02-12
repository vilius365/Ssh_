package com.pocketssh.app.ui.connections

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pocketssh.app.R
import com.pocketssh.app.ssh.SshKeyInfo
import com.pocketssh.app.ui.theme.TerminalGreen

/**
 * Form screen for creating or editing an SSH connection profile.
 * Includes field validation, SSH key picker, and test connection functionality.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionEditorScreen(
    onNavigateBack: () -> Unit,
    viewModel: ConnectionEditorViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (uiState.isEditing) stringResource(R.string.edit_connection_title)
                        else stringResource(R.string.new_connection)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (uiState.isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 4.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        TextButton(
                            onClick = { viewModel.testConnection() },
                            enabled = uiState.isValid,
                        ) {
                            Text(stringResource(R.string.test_connection))
                        }
                    }
                    Button(
                        onClick = { viewModel.save(onNavigateBack) },
                        enabled = uiState.isValid && !uiState.isSaving,
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.nickname,
                onValueChange = viewModel::updateNickname,
                label = { Text(stringResource(R.string.nickname)) },
                singleLine = true,
                isError = uiState.nicknameError != null,
                supportingText = uiState.nicknameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.hostname,
                onValueChange = viewModel::updateHostname,
                label = { Text(stringResource(R.string.hostname)) },
                singleLine = true,
                isError = uiState.hostnameError != null,
                supportingText = uiState.hostnameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.port,
                onValueChange = viewModel::updatePort,
                label = { Text(stringResource(R.string.port)) },
                singleLine = true,
                isError = uiState.portError != null,
                supportingText = uiState.portError?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = uiState.username,
                onValueChange = viewModel::updateUsername,
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                isError = uiState.usernameError != null,
                supportingText = uiState.usernameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(16.dp))

            // SSH Key selector (read-only field that opens picker)
            // Wrap in Box with clickable overlay so clicks are never swallowed
            // by the TextField's internal focus handler
            Box {
                OutlinedTextField(
                    value = uiState.sshKeyName ?: stringResource(R.string.no_key_selected),
                    onValueChange = {},
                    label = { Text(stringResource(R.string.ssh_key)) },
                    readOnly = true,
                    singleLine = true,
                    enabled = false,
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.select_ssh_key),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Invisible overlay to capture clicks reliably
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { viewModel.showKeyPicker() },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Test connection result
            uiState.testResult?.let { result ->
                TestResultIndicator(result = result)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // SSH Key picker bottom sheet
    if (uiState.showKeyPicker) {
        SshKeyPickerSheet(
            keys = uiState.availableKeys,
            selectedKeyId = uiState.sshKeyId,
            onSelect = viewModel::selectSshKey,
            onDismiss = viewModel::hideKeyPicker,
        )
    }
}

/**
 * Displays the test connection result inline with an icon and text.
 */
@Composable
private fun TestResultIndicator(result: TestConnectionResult) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when (result) {
            is TestConnectionResult.Success -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = TerminalGreen,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.test_success),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TerminalGreen,
                )
            }
            is TestConnectionResult.Failure -> {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.test_failed, result.message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Bottom sheet for selecting an SSH key from stored keys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SshKeyPickerSheet(
    keys: List<SshKeyInfo>,
    selectedKeyId: String?,
    onSelect: (SshKeyInfo?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.select_ssh_key),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            // "None" option
            TextButton(
                onClick = { onSelect(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = stringResource(R.string.no_key_selected),
                    modifier = Modifier.weight(1f),
                )
                if (selectedKeyId == null) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            keys.forEach { keyInfo ->
                TextButton(
                    onClick = { onSelect(keyInfo) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = keyInfo.name,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = "${keyInfo.algorithm} - ${keyInfo.fingerprintSha256.take(16)}...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = DateUtils.getRelativeTimeSpanString(
                                keyInfo.createdAt,
                                System.currentTimeMillis(),
                                DateUtils.DAY_IN_MILLIS,
                            ).toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (keyInfo.id == selectedKeyId) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
