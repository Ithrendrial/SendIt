package com.example.sendit.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.sendit.R
import com.example.sendit.ui.theme.SendItSpacing
import com.example.sendit.ui.theme.SendItTheme


private const val VIDEO_MIME_TYPE = "video/*"

@Composable
fun AttemptFormScreen(
    modifier: Modifier = Modifier,
    selectedVideo: Uri?,
    onVideoSelected: (Uri) -> Unit
) {
    // Register once with Compose. The picker grants access to the document the user chooses.
    // Null = cancellation, in which case keep previous selection.
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onVideoSelected(uri)
    }

    // Attempt form variables:
    // Changing these values in the State will cause Compose to redraw the UI.
    // rememberSaveable stores this info and persists it across config changes, but isn't stored in the Room.
    var routeName by rememberSaveable { mutableStateOf("") }
    var grade by rememberSaveable { mutableStateOf("VB") }
    var location by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var outcome by rememberSaveable { mutableStateOf("Fall") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    val context = LocalContext.current // Needed for the date picker dialogue
    val focusManager = LocalFocusManager.current

    // Column layout places children vertically and incorperates scrolling.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SendItSpacing.screenPadding)
            .padding(top = SendItSpacing.large, bottom = SendItSpacing.extraLarge)
    ) {
        Text(stringResource(R.string.new_attempt), style = MaterialTheme.typography.headlineSmall) // Landing page heading
        Spacer(Modifier.height(SendItSpacing.section))
        SectionLabel(stringResource(R.string.route_details)) // Route details heading
        Spacer(Modifier.height(SendItSpacing.extraSmall))
        EditableField( // Route name text input with label and placeholder.
            stringResource(R.string.route_name),
            routeName,
            { routeName = it },
            routeLabel = true,
            placeholder = stringResource(R.string.route_name_placeholder)
        )
        Spacer(Modifier.height(SendItSpacing.large))
        
        // Video entry point.
        SectionLabel(stringResource(R.string.attempt_video)) // Video section heading
        Spacer(Modifier.height(SendItSpacing.extraSmall))
        VideoPlaceholder(hasSelectedVideo = selectedVideo != null) {
            focusManager.clearFocus()
            videoPicker.launch(arrayOf(VIDEO_MIME_TYPE))
        }
        Spacer(Modifier.height(SendItSpacing.extraLarge))

        // Further attempt details (grade, location, date, outcome, notes).
        GradeDropdown(grade) { grade = it } // Grade dropdown picker
        Spacer(Modifier.height(SendItSpacing.large))
        EditableField( // Location text field
            stringResource(R.string.location), 
            location, 
            { location = it }, 
            routeLabel = true, 
            placeholder = stringResource(R.string.location_placeholder)
        )
        Spacer(Modifier.height(SendItSpacing.extraLarge))
        // Tapping the date opens the native Android calendar dialog.
        SectionLabel(stringResource(R.string.date)) // Date label
        Spacer(Modifier.height(SendItSpacing.extraSmall))
        Surface(
            onClick = {
                focusManager.clearFocus()
                val selectedDate = LocalDate.parse(date)
                // DatePickerDialog counts months from 0; LocalDate counts them from 1.
                DatePickerDialog(
                    context,
                    android.R.style.Theme_DeviceDefault_Dialog,
                    { _, year, month, day -> date = LocalDate.of(year, month + 1, day).toString() },
                    selectedDate.year, selectedDate.monthValue - 1, selectedDate.dayOfMonth
                ).show()
            },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                LocalDate.parse(date).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
                modifier = Modifier.padding(SendItSpacing.large),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Spacer(Modifier.height(SendItSpacing.extraLarge))
        SectionLabel(stringResource(R.string.outcome))
        Spacer(Modifier.height(SendItSpacing.extraSmall))
        // Outcome options (only one can be selected at a time), arranged in a row.
        Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(SendItSpacing.small)) {
            OutcomeOption(stringResource(R.string.sent), selected = outcome == "Sent", onClick = { outcome = "Sent" }, modifier = Modifier.weight(1f))
            OutcomeOption(stringResource(R.string.fall), selected = outcome == "Fall", onClick = { outcome = "Fall" }, modifier = Modifier.weight(1f))
            OutcomeOption(stringResource(R.string.flash), selected = outcome == "Flash", onClick = { outcome = "Flash" }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(SendItSpacing.extraLarge))

        EditableField( // Notes text field.
            label = stringResource(R.string.notes),
            value = notes,
            onValueChange = { notes = it },
            placeholder = stringResource(R.string.notes_placeholder),
            multiline = true
        )
        Spacer(Modifier.height(40.dp))
        Surface( // Form submission (upload and analysis button).
            onClick = {
                focusManager.clearFocus()
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = SendItSpacing.section),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Box(
                modifier = Modifier.heightIn(min = 48.dp).padding(SendItSpacing.medium),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.upload_and_analyse), style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
            }
        }
    }
}

// Reusable heading component.
@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
}


// Reusable input for route name, location, and notes.
//The parent owns the value (onValueChange sends edits back to it).
@Composable
private fun EditableField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    routeLabel: Boolean = false,
    placeholder: String = "",
    multiline: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(SendItSpacing.extraSmall)) {
        FieldLabel(label, routeLabel)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = !multiline,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.secondary),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = label }
                .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                .heightIn(min = if (multiline) 100.dp else 56.dp)
                .padding(SendItSpacing.large),
            // Show the hint only when empty, and always render the actual editable content.
            decorationBox = { innerTextField ->
                Box(contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f))
                    }
                    innerTextField()
                }
            }
        )
    }
}

// Sub-subheading labels for text fields.
@Composable
private fun FieldLabel(label: String, routeLabel: Boolean) {
    Text(
        label,
        style = if (routeLabel) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
        color = if (routeLabel) MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onBackground
    )
}

// Dropdown menu for grade route selection. Final selected output is owned by parent.
@Composable
private fun GradeDropdown(grade: String, onGradeChange: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(SendItSpacing.extraSmall)) {
        FieldLabel(stringResource(R.string.grade), routeLabel = true)
        Box {
            Surface(
                onClick = { focusManager.clearFocus(); expanded = true },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(SendItSpacing.large),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(grade, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Limit menu height so the full grade list can scroll on smaller screens.
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                (listOf("VB") + (0..17).map { "V$it" }).forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = { onGradeChange(option); expanded = false }
                    )
                }
            }
        }
    }
}

// Styled video entry area.
@Composable
private fun VideoPlaceholder(hasSelectedVideo: Boolean, onClick: () -> Unit) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().drawWithCache {
            // Draws rounded, dashed border around video placeholder.
            val strokeWidth = 2.dp.toPx()
            val stroke = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())))
            onDrawWithContent {
                drawContent()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = stroke
                )
            }
        }
    ) {
        Column(
            modifier = Modifier.heightIn(min = 200.dp).padding(SendItSpacing.extraLarge),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.size(60.dp).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_upload_cloud),
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(Modifier.height(SendItSpacing.large))
            Text(
                stringResource(if (hasSelectedVideo) R.string.video_selected else R.string.choose_attempt_video),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(SendItSpacing.small))
            Text(
                stringResource(if (hasSelectedVideo) R.string.replace_video_hint else R.string.video_picker_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
        }
    }
}

// Selectable outcome tiles (Sent, Fall, Flash).
@Composable
private fun OutcomeOption(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.secondary) else null
    ) {
        Box(Modifier.heightIn(min = 48.dp).padding(SendItSpacing.small), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}
