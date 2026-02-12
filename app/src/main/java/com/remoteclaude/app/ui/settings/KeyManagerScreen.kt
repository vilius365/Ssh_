package com.remoteclaude.app.ui.settings

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remoteclaude.app.R
import com.remoteclaude.app.ssh.SshKeyInfo

/**
 * SSH key management screen. Lists stored keys with metadata, and provides
 * options to import or generate new keys.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: KeyManagerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var keyToDelete by remember { mutableStateOf<SshKeyInfo?>(null) }

    val context = LocalContext.current

    // File picker for key import
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@rememberLauncherForActivityResult
            // Extract filename from URI for pre-filling the name field
            val fileName = uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.substringBeforeLast('.')
                ?: "imported-key"
            viewModel.onImportFileSelected(fileName, bytes)
        } catch (_: Exception) {
            // File read failed silently; user can retry
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ssh_keys_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showGenerateDialog() },
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.generate_key),
                )
            }
        },
    ) { padding ->
        if (uiState.keys.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.no_ssh_keys),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { importFileLauncher.launch(arrayOf("*/*")) },
                        ) {
                            Text(stringResource(R.string.import_key))
                        }
                        OutlinedButton(onClick = { viewModel.showGenerateDialog() }) {
                            Text(stringResource(R.string.generate_key))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = uiState.keys,
                    key = { it.id },
                ) { keyInfo ->
                    KeyCard(
                        keyInfo = keyInfo,
                        onDelete = { keyToDelete = keyInfo },
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { importFileLauncher.launch(arrayOf("*/*")) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.import_key))
                        }
                        OutlinedButton(
                            onClick = { viewModel.showGenerateDialog() },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.generate_key))
                        }
                    }
                }

                // Bottom spacer for FAB clearance
                item { Spacer(modifier = Modifier.height(72.dp)) }
            }
        }
    }

    // Delete confirmation dialog
    keyToDelete?.let { keyInfo ->
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = { Text(stringResource(R.string.delete_key_title)) },
            text = { Text(stringResource(R.string.delete_key_message, keyInfo.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteKey(keyInfo.id)
                        keyToDelete = null
                    },
                ) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { keyToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Generate key dialog
    if (uiState.showGenerateDialog) {
        GenerateKeyDialog(
            isLoading = uiState.isOperationInProgress,
            error = uiState.error,
            onGenerate = { name, algorithm -> viewModel.generateKey(name, algorithm) },
            onDismiss = { viewModel.hideGenerateDialog() },
        )
    }

    // Import key dialog (appears after file is selected)
    if (uiState.showImportDialog) {
        ImportKeyDialog(
            defaultName = uiState.importFileName,
            isLoading = uiState.isOperationInProgress,
            error = uiState.error,
            onImport = { name, passphrase -> viewModel.performImport(name, passphrase) },
            onDismiss = { viewModel.hideImportDialog() },
        )
    }
}

/**
 * Dialog for generating a new SSH key with name and algorithm selection.
 */
@Composable
private fun GenerateKeyDialog(
    isLoading: Boolean,
    error: String?,
    onGenerate: (name: String, algorithm: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyName by remember { mutableStateOf("") }
    var selectedAlgorithm by remember { mutableStateOf("ed25519") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.generate_key_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyName,
                    onValueChange = { keyName = it },
                    label = { Text(stringResource(R.string.key_name)) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.key_algorithm),
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Column(modifier = Modifier.selectableGroup()) {
                    AlgorithmOption(
                        label = "Ed25519",
                        description = stringResource(R.string.ed25519_description),
                        selected = selectedAlgorithm == "ed25519",
                        enabled = !isLoading,
                        onClick = { selectedAlgorithm = "ed25519" },
                    )
                    AlgorithmOption(
                        label = "RSA-4096",
                        description = stringResource(R.string.rsa_description),
                        selected = selectedAlgorithm == "rsa-4096",
                        enabled = !isLoading,
                        onClick = { selectedAlgorithm = "rsa-4096" },
                    )
                }
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(
                    onClick = { onGenerate(keyName.ifBlank { "My SSH Key" }, selectedAlgorithm) },
                ) {
                    Text(stringResource(R.string.generate_key))
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

/**
 * Single algorithm radio option row.
 */
@Composable
private fun AlgorithmOption(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Dialog for importing an SSH key after file selection.
 * Shows name field (pre-filled from filename) and optional passphrase.
 */
@Composable
private fun ImportKeyDialog(
    defaultName: String,
    isLoading: Boolean,
    error: String?,
    onImport: (name: String, passphrase: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var keyName by remember { mutableStateOf(defaultName) }
    var passphrase by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text(stringResource(R.string.import_key_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = keyName,
                    onValueChange = { keyName = it },
                    label = { Text(stringResource(R.string.key_name)) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.passphrase_optional)) },
                    singleLine = true,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(
                    onClick = {
                        onImport(
                            keyName.ifBlank { "imported-key" },
                            passphrase.ifBlank { null },
                        )
                    },
                ) {
                    Text(stringResource(R.string.import_action))
                }
            }
        },
        dismissButton = {
            if (!isLoading) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

/**
 * Card displaying SSH key metadata: name, algorithm, fingerprint, and creation date.
 */
@Composable
private fun KeyCard(
    keyInfo: SshKeyInfo,
    onDelete: () -> Unit,
) {
    Card(
        colors = CardDefaults.outlinedCardColors(),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = keyInfo.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = keyInfo.algorithm,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = keyInfo.fingerprintSha256,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
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
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
