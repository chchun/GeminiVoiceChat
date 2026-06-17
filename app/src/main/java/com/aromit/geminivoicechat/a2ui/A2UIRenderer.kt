package com.aromit.geminivoicechat.a2ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun A2UISurfaceView(
    surface: A2UISurface,
    onData: (path: String, value: Any?) -> Unit,
    onAction: (eventName: String, context: Map<String, Any?>) -> Unit,
    modifier: Modifier = Modifier,
) {
    A2UINode(
        componentId = surface.rootId,
        surface = surface,
        onData = onData,
        onAction = onAction,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun A2UINode(
    componentId: String,
    surface: A2UISurface,
    onData: (path: String, value: Any?) -> Unit,
    onAction: (eventName: String, context: Map<String, Any?>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val component = surface.components[componentId] ?: return
    val model = surface.dataModel
    val primary = surface.primaryColor

    when (component) {
        is A2UIComponent.Card -> {
            Card(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    A2UINode(component.child, surface, onData, onAction)
                }
            }
        }

        is A2UIComponent.Column -> {
            Column(
                modifier = modifier,
                verticalArrangement = when (component.justify) {
                    "spaceBetween" -> Arrangement.SpaceBetween
                    "spaceAround" -> Arrangement.SpaceAround
                    "end" -> Arrangement.Bottom
                    "center" -> Arrangement.Center
                    else -> Arrangement.spacedBy(10.dp)
                },
                horizontalAlignment = when (component.align) {
                    "center" -> Alignment.CenterHorizontally
                    "end" -> Alignment.End
                    else -> Alignment.Start
                },
            ) {
                component.children.forEach { childId ->
                    A2UINode(childId, surface, onData, onAction)
                }
            }
        }

        is A2UIComponent.Row -> {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = when (component.justify) {
                    "spaceBetween" -> Arrangement.SpaceBetween
                    "spaceAround" -> Arrangement.SpaceAround
                    "end" -> Arrangement.End
                    "center" -> Arrangement.Center
                    else -> Arrangement.spacedBy(8.dp)
                },
                verticalAlignment = when (component.align) {
                    "start" -> Alignment.Top
                    "end" -> Alignment.Bottom
                    else -> Alignment.CenterVertically
                },
            ) {
                component.children.forEach { childId ->
                    A2UINode(childId, surface, onData, onAction)
                }
            }
        }

        is A2UIComponent.TextComp -> {
            val text = resolveValue(component.text, model)
            when (component.variant) {
                "h1" -> Text(text,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
                "h2" -> Text(text,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
                "caption" -> Text(text,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }

        is A2UIComponent.IconComp -> {
            val imageVector = when (component.name) {
                "alert", "warning" -> Icons.Filled.Warning
                "activity", "chart", "show_chart" -> Icons.Filled.ShowChart
                "check", "check_circle" -> Icons.Filled.CheckCircle
                "settings" -> Icons.Filled.Settings
                "search" -> Icons.Filled.Search
                else -> Icons.Filled.Info
            }
            Icon(
                imageVector = imageVector,
                contentDescription = component.name,
                modifier = Modifier.size(component.size.dp),
                tint = primary,
            )
        }

        is A2UIComponent.Divider -> {
            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
        }

        is A2UIComponent.ButtonComp -> {
            val label = resolveValue(component.label, model)
            val enabled = checksPass(component.checks, model)
            val errorMsg = if (!enabled) firstFailMessage(component.checks, model) else null
            Column(modifier = modifier) {
                when (component.variant) {
                    "primary" -> Button(
                        onClick = {
                            component.action?.let { a ->
                                onAction(a.eventName, resolveContext(a.contextRefs, a.extraLiterals, model))
                            }
                        },
                        enabled = enabled,
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }

                    "secondary" -> OutlinedButton(
                        onClick = {
                            component.action?.let { a ->
                                onAction(a.eventName, resolveContext(a.contextRefs, a.extraLiterals, model))
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }

                    "borderless" -> TextButton(
                        onClick = {
                            component.action?.let { a ->
                                onAction(a.eventName, resolveContext(a.contextRefs, a.extraLiterals, model))
                            }
                        },
                    ) { Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant) }

                    else -> Button(
                        onClick = {
                            component.action?.let { a ->
                                onAction(a.eventName, resolveContext(a.contextRefs, a.extraLiterals, model))
                            }
                        },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(label) }
                }
                if (errorMsg != null) {
                    Text(
                        text = errorMsg,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            }
        }

        is A2UIComponent.TextFieldComp -> {
            val value = getAtPath(model, component.valuePath)?.toString() ?: ""
            OutlinedTextField(
                value = value,
                onValueChange = { onData(component.valuePath, it) },
                label = { Text(component.label) },
                placeholder = { Text(component.placeholder) },
                maxLines = if (component.variant == "longText") 5 else 1,
                minLines = if (component.variant == "longText") 3 else 1,
                modifier = modifier.fillMaxWidth(),
            )
        }

        is A2UIComponent.CheckBoxComp -> {
            val checked = getAtPath(model, component.valuePath) as? Boolean ?: false
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = modifier
                    .fillMaxWidth()
                    .clickable { onData(component.valuePath, !checked) },
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { onData(component.valuePath, it) },
                    colors = CheckboxDefaults.colors(checkedColor = primary),
                )
                Spacer(Modifier.width(4.dp))
                Text(component.label, style = MaterialTheme.typography.bodyMedium)
            }
        }

        is A2UIComponent.ChoicePickerComp -> {
            val isMultiple = component.variant == "multiple"
            @Suppress("UNCHECKED_CAST")
            val selectedList: List<String> = if (isMultiple) {
                (getAtPath(model, component.valuePath) as? List<String>) ?: emptyList()
            } else emptyList()
            val singleSelected = if (!isMultiple) getAtPath(model, component.valuePath)?.toString() else null

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = modifier,
            ) {
                component.options.forEach { option ->
                    val selected = if (isMultiple) selectedList.contains(option.value)
                                   else singleSelected == option.value
                    FilterChip(
                        selected = selected,
                        onClick = {
                            if (isMultiple) {
                                val newList = if (selected) selectedList - option.value
                                             else selectedList + option.value
                                onData(component.valuePath, newList.toMutableList())
                            } else {
                                onData(component.valuePath, option.value)
                            }
                        },
                        label = { Text(option.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = primary.copy(alpha = 0.15f),
                            selectedLabelColor = primary,
                        ),
                    )
                }
            }
        }

        is A2UIComponent.SliderComp -> {
            val value = (getAtPath(model, component.valuePath) as? Number)?.toFloat() ?: component.min
            Column(modifier = modifier) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(component.label, style = MaterialTheme.typography.labelMedium)
                    Text("${value.toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = primary,
                        fontWeight = FontWeight.SemiBold)
                }
                Slider(
                    value = value,
                    onValueChange = { onData(component.valuePath, it.toInt()) },
                    valueRange = component.min..component.max,
                    steps = ((component.max - component.min) / component.step).toInt() - 1,
                    colors = SliderDefaults.colors(thumbColor = primary, activeTrackColor = primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is A2UIComponent.SwitchComp -> {
            val on = getAtPath(model, component.valuePath) as? Boolean ?: false
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(component.label, style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = on,
                    onCheckedChange = { onData(component.valuePath, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primary,
                    ),
                )
            }
        }

        is A2UIComponent.TagInputComp -> {
            @Suppress("UNCHECKED_CAST")
            val tags: List<String> = (getAtPath(model, component.valuePath) as? List<String>) ?: emptyList()
            var inputText by remember { mutableStateOf("") }
            Column(modifier = modifier) {
                Text(component.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                // Suggestion chips (unselected suggestions only)
                val unselected = component.suggestions.filter { !tags.contains(it) }
                if (unselected.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        unselected.forEach { s ->
                            SuggestionChip(
                                onClick = { onData(component.valuePath, (tags + s).toMutableList()) },
                                label = { Text(s, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // Selected tags
                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { onData(component.valuePath, tags.filterNot { it == tag }.toMutableList()) },
                                label = { Text(tag) },
                                trailingIcon = {
                                    Icon(Icons.Filled.Close, contentDescription = "제거",
                                        modifier = Modifier.size(14.dp))
                                },
                                colors = InputChipDefaults.inputChipColors(
                                    selectedContainerColor = primary.copy(alpha = 0.15f),
                                    selectedLabelColor = primary,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                // Custom text input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text(component.placeholder) },
                    trailingIcon = {
                        if (inputText.isNotBlank()) {
                            IconButton(onClick = {
                                val trimmed = inputText.trim()
                                if (trimmed.isNotEmpty() && !tags.contains(trimmed)) {
                                    onData(component.valuePath, (tags + trimmed).toMutableList())
                                }
                                inputText = ""
                            }) {
                                Icon(Icons.Filled.Add, contentDescription = "추가")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        is A2UIComponent.SelectComp -> {
            var expanded by remember { mutableStateOf(false) }
            val current = getAtPath(model, component.valuePath)?.toString() ?: ""
            val currentLabel = component.options.firstOrNull { it.value == current }?.label
                ?: component.placeholder.ifEmpty { "선택" }
            Column(modifier = modifier) {
                Text(component.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                ) {
                    OutlinedTextField(
                        value = currentLabel,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        component.options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    onData(component.valuePath, option.value)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
            }
        }

        is A2UIComponent.RiskCardList -> {
            @Suppress("UNCHECKED_CAST")
            val rows = (getAtPath(model, component.rowsPath) as? List<Map<String, Any?>>) ?: emptyList()
            Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { idx, row ->
                    RiskRowCard(index = idx + 1, row = row)
                }
            }
        }
    }
}

@Composable
private fun RiskRowCard(index: Int, row: Map<String, Any?>) {
    val risk = row["risk"]?.toString() ?: "하"
    val riskColor = when (risk) {
        "상" -> Color(0xFFE5484D)
        "중" -> Color(0xFFDD6B20)
        else -> Color(0xFF1F8A5B)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "$index. ${row["trade"] ?: ""}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Box(
                    modifier = Modifier
                        .background(riskColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("위험 $risk",
                        color = riskColor,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold)
                }
            }
            Text(row["task"]?.toString() ?: "",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium)
            Text(row["cause"]?.toString() ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            val improve = row["improve"]?.toString()
            if (!improve.isNullOrBlank()) {
                Text("개선: $improve",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF0E7C66),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
