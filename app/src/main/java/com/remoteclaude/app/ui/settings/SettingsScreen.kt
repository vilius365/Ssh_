package com.remoteclaude.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.remoteclaude.app.R
import com.remoteclaude.app.ui.theme.DarkSurface
import com.remoteclaude.app.ui.theme.JetBrainsMono

/**
 * App settings screen with appearance, terminal, key management, and about sections.
 * All settings changes are applied immediately via DataStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToKeyManager: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showFontPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // APPEARANCE section
            SectionHeader(stringResource(R.string.appearance_section))

            // Theme selector (segmented button)
            Text(
                text = stringResource(R.string.theme),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ThemeMode.entries.size,
                        ),
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
                                ThemeMode.DARK -> stringResource(R.string.theme_dark)
                                ThemeMode.LIGHT -> stringResource(R.string.theme_light)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Font family picker
            SettingsRow(
                label = stringResource(R.string.font_family),
                value = uiState.fontFamily,
                onClick = { showFontPicker = true },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Font size slider
            Text(
                text = stringResource(R.string.font_size),
                style = MaterialTheme.typography.bodyLarge,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "8",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Slider(
                    value = uiState.fontSize.toFloat(),
                    onValueChange = { viewModel.setFontSize(it.toInt()) },
                    valueRange = 8f..24f,
                    steps = 15, // 16 values from 8 to 24
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                Text(
                    text = "24",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Live font preview
            FontPreview(
                fontFamily = getFontFamilyForName(uiState.fontFamily),
                fontSize = uiState.fontSize,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // TERMINAL section
            SectionHeader(stringResource(R.string.terminal_section))

            SettingsRow(
                label = stringResource(R.string.scrollback_lines),
                value = "%,d".format(uiState.scrollbackLines),
                onClick = { /* Not editable for now */ },
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bell mode (segmented button)
            Text(
                text = stringResource(R.string.bell),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                BellMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = uiState.bellMode == mode,
                        onClick = { viewModel.setBellMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = BellMode.entries.size,
                        ),
                    ) {
                        Text(
                            when (mode) {
                                BellMode.VIBRATE -> stringResource(R.string.bell_vibrate)
                                BellMode.SOUND -> stringResource(R.string.bell_sound)
                                BellMode.NONE -> stringResource(R.string.bell_none)
                            }
                        )
                    }
                }
            }

            // KEYS section
            SectionHeader(stringResource(R.string.keys_section))

            SettingsRow(
                label = stringResource(R.string.manage_ssh_keys),
                value = "",
                onClick = onNavigateToKeyManager,
            )

            // ABOUT section
            SectionHeader(stringResource(R.string.about_section))

            Text(
                text = stringResource(R.string.version_format, "1.0.0"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            SettingsRow(
                label = stringResource(R.string.open_source_licenses),
                value = "",
                onClick = { /* TODO: Open licenses screen */ },
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Font picker bottom sheet
    if (showFontPicker) {
        FontPickerSheet(
            selectedFont = uiState.fontFamily,
            onSelect = { fontName ->
                viewModel.setFontFamily(fontName)
                showFontPicker = false
            },
            onDismiss = { showFontPicker = false },
        )
    }
}

/**
 * Section header with primary color label.
 */
@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

/**
 * Clickable settings row with label, value, and right arrow.
 */
@Composable
private fun SettingsRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Terminal-styled live preview box showing sample text with the selected font and size.
 */
@Composable
private fun FontPreview(
    fontFamily: FontFamily,
    fontSize: Int,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                DarkSurface,
                MaterialTheme.shapes.medium,
            )
            .padding(12.dp),
    ) {
        Column {
            Text(
                text = stringResource(R.string.font_preview_line1),
                fontFamily = fontFamily,
                fontSize = fontSize.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.font_preview_line2),
                fontFamily = fontFamily,
                fontSize = fontSize.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                text = stringResource(R.string.font_preview_line3),
                fontFamily = fontFamily,
                fontSize = fontSize.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/**
 * Bottom sheet for selecting a terminal font family.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FontPickerSheet(
    selectedFont: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val fonts = listOf(
        "JetBrains Mono",
        "Fira Code",
        "Hack",
        "Inconsolata",
    )

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
                text = stringResource(R.string.font_family),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            fonts.forEach { fontName ->
                TextButton(
                    onClick = { onSelect(fontName) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = fontName,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = getFontFamilyForName(fontName),
                        ),
                        color = if (fontName == selectedFont) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Maps a font family name string to the corresponding [FontFamily] instance.
 */
private fun getFontFamilyForName(name: String): FontFamily {
    return when (name) {
        "JetBrains Mono" -> JetBrainsMono
        // Other fonts would be loaded similarly if bundled
        else -> FontFamily.Monospace
    }
}
