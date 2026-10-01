package com.example.sendit.ui.screens

import android.app.DatePickerDialog
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
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
import androidx.compose.runtime.produceState
import androidx.compose.material3.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import com.example.sendit.data.RouteEntity
import com.example.sendit.domain.AttemptDetails
import com.example.sendit.ui.theme.SendItSpacing
import com.example.sendit.ui.theme.SendItTheme


private const val VIDEO_MIME_TYPE = "video/*"

@Composable
fun AttemptFormScreen(
    modifier: Modifier = Modifier,
    selectedVideo: Uri?,
    onVideoSelected: (Uri) -> Unit,
    processing: Boolean = false,
    analysisProgress: Int? = null,
    errorMessage: String? = null,
    existingRoutes: List<RouteEntity> = emptyList(),
    onSubmit: (AttemptDetails) -> Unit = {},
    onCancel: () -> Unit = {}
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
    var selectedRouteId by rememberSaveable { mutableStateOf<String?>(null) }
    var showNewRouteDialog by rememberSaveable { mutableStateOf(false) }
    var grade by rememberSaveable { mutableStateOf("VB") }
    var location by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var outcome by rememberSaveable { mutableStateOf("Fall") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }

    // An existing route supplies its own name, grade and location; otherwise routeName holds a new route's name.
    val selectedRoute = existingRoutes.firstOrNull { it.id == selectedRouteId }
    val hasRoute = selectedRoute != null || routeName.isNotBlank()
    val formEnabled = !processing // The form is locked while an upload is being processed.

    val context = LocalContext.current // Needed for the date picker dialogue
    val focusManager = LocalFocusManager.current
    // Ask Android for the document name once per selection, away from the UI thread.
    val videoName by produceState<String?>(null, selectedVideo) {
        value = null
        val video = selectedVideo ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.query(video, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                    ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull()?.takeIf { it.isNotBlank() }
                ?: video.lastPathSegment ?: context.getString(R.string.video_selected)
        }
    }

    if (showNewRouteDialog) {
        NewRouteDialog(
            onAdd = { name -> selectedRouteId = null; routeName = name; showNewRouteDialog = false },
            onDismiss = { showNewRouteDialog = false }
        )
    }

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
        RouteDropdown(
            routes = existingRoutes,
            selectedName = selectedRoute?.name ?: routeName.ifBlank { null },
            enabled = formEnabled,
            onRouteSelected = { selectedRouteId = it.id; routeName = "" },
            onNewRoute = { showNewRouteDialog = true }
        )
        Spacer(Modifier.height(SendItSpacing.large))
        
        // Video entry point.
        SectionLabel(stringResource(R.string.attempt_video)) // Video section heading
        Spacer(Modifier.height(SendItSpacing.extraSmall))
        VideoPlaceholder(videoName = if (selectedVideo == null) null else videoName ?: stringResource(R.string.loading_video_name)) {
            if (!processing) {
                focusManager.clearFocus()
                videoPicker.launch(arrayOf(VIDEO_MIME_TYPE))
            }
        }
        Spacer(Modifier.height(SendItSpacing.extraLarge))

        // Further attempt details (grade, location, date, outcome, notes).
        if (selectedRoute == null) {
            GradeDropdown(grade, formEnabled) { grade = it } // Grade dropdown picker
            Spacer(Modifier.height(SendItSpacing.large))
            EditableField( // Location text field
                stringResource(R.string.location), 
                location, 
                { location = it }, 
                routeLabel = true, 
                placeholder = stringResource(R.string.location_placeholder),
                enabled = formEnabled
            )
        }
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
            enabled = formEnabled,
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
            OutcomeOption(stringResource(R.string.sent), selected = outcome == "Sent", onClick = { outcome = "Sent" }, enabled = formEnabled, modifier = Modifier.weight(1f))
            OutcomeOption(stringResource(R.string.fall), selected = outcome == "Fall", onClick = { outcome = "Fall" }, enabled = formEnabled, modifier = Modifier.weight(1f))
            OutcomeOption(stringResource(R.string.flash), selected = outcome == "Flash", onClick = { outcome = "Flash" }, enabled = formEnabled, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(SendItSpacing.extraLarge))

        EditableField( // Notes text field.
            label = stringResource(R.string.notes),
            value = notes,
            onValueChange = { notes = it },
            placeholder = stringResource(R.string.notes_placeholder),
            multiline = true,
            enabled = formEnabled
        )
        Spacer(Modifier.height(40.dp))
        if (errorMessage != null) {
            Text(errorMessage, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(SendItSpacing.medium))
        }
        if (processing) {
            // The percentage covers pose analysis; copying and saving have their own status text.
            Text(when (analysisProgress) {
                null -> stringResource(R.string.preparing_video)
                100 -> stringResource(R.string.saving_attempt)
                else -> stringResource(R.string.analysing_video_progress, analysisProgress)
            })
            Spacer(Modifier.height(SendItSpacing.small))
            if (analysisProgress == null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { analysisProgress / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(SendItSpacing.medium))
        }
        // Cancel only appears once the form has been submitted, and returns the form to an editable state.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SendItSpacing.section),
            horizontalArrangement = Arrangement.spacedBy(SendItSpacing.small)
        ) {
            Surface( // Form submission (upload and analysis button).
                onClick = {
                    focusManager.clearFocus()
                    onSubmit(AttemptDetails(
                        selectedRoute?.name ?: routeName, selectedRoute?.grade ?: grade, selectedRoute?.location ?: location,
                        LocalDate.parse(date).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        outcome, notes, selectedRoute?.id
                    ))
                },
                enabled = selectedVideo != null && hasRoute && !processing, // Video and route are required.
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Box(
                    modifier = Modifier.heightIn(min = 48.dp).padding(SendItSpacing.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (processing) "Processing video…" else stringResource(R.string.upload_and_analyse),
                        style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center
                    )
                }
            }
            if (processing) {
                Surface(
                    onClick = onCancel,
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = MaterialTheme.colorScheme.secondary
                ) {
                    Box(
                        modifier = Modifier.heightIn(min = 48.dp).padding(SendItSpacing.medium),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelLarge)
                    }
                }
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
    multiline: Boolean = false,
    enabled: Boolean = true
) {
    Column(verticalArrangement = Arrangement.spacedBy(SendItSpacing.extraSmall)) {
        FieldLabel(label, routeLabel)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
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

// Dropdown of saved routes, with "+ New Route" last. The parent owns the selection.
@Composable
private fun RouteDropdown(
    routes: List<RouteEntity>,
    selectedName: String?,
    enabled: Boolean,
    onRouteSelected: (RouteEntity) -> Unit,
    onNewRoute: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val selectorDescription = stringResource(R.string.route_selector)
    Column(verticalArrangement = Arrangement.spacedBy(SendItSpacing.extraSmall)) {
        FieldLabel(stringResource(R.string.route_name), routeLabel = true)
        Box {
            Surface(
                onClick = { focusManager.clearFocus(); expanded = true },
                enabled = enabled,
                modifier = Modifier.semantics { contentDescription = selectorDescription },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(SendItSpacing.large),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedName ?: stringResource(R.string.select_route), modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge)
                    Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null,
                        modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 280.dp)) {
                routes.forEach { route ->
                    DropdownMenuItem(
                        text = { Text(route.name) },
                        onClick = { onRouteSelected(route); expanded = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.new_route_option)) },
                    onClick = { onNewRoute(); expanded = false }
                )
            }
        }
    }
}

// Modal for typing the name of a new route. Add stays disabled until a name is entered.
@Composable
private fun NewRouteDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    val nameDescription = stringResource(R.string.new_route_name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_route)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.route_name_placeholder)) },
                modifier = Modifier.semantics { contentDescription = nameDescription }
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.add)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
    )
}

// Dropdown menu for grade route selection. Final selected output is owned by parent.
@Composable
private fun GradeDropdown(grade: String, enabled: Boolean, onGradeChange: (String) -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    Column(verticalArrangement = Arrangement.spacedBy(SendItSpacing.extraSmall)) {
        FieldLabel(stringResource(R.string.grade), routeLabel = true)
        Box {
            Surface(
                onClick = { focusManager.clearFocus(); expanded = true },
                enabled = enabled,
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
private fun VideoPlaceholder(videoName: String?, onClick: () -> Unit) {
    val hasSelectedVideo = videoName != null
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
                videoName ?: stringResource(R.string.choose_attempt_video),
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
private fun OutcomeOption(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Surface(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
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
