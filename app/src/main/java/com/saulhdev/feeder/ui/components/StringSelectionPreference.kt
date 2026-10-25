package com.saulhdev.feeder.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.saulhdev.feeder.data.content.StringSelectionPref
import com.saulhdev.feeder.data.content.asState

@Composable
fun StringSelectionPreference(
    modifier: Modifier = Modifier,
    pref: StringSelectionPref,
    index: Int = 1,
    groupSize: Int = 1,
    isEnabled: Boolean = true,
    onClick: (() -> Unit) = {},
) {
    // Collected, not read once: derivedStateOf over a blocking getValue() never
    // recomposes, so the row went on showing the previous choice after a change.
    val current by pref.asState()
    val buttons = pref.buttons
    if (buttons != null) {
        // A few short choices: shown, not hidden behind a dialog.
        BasePreference(
            modifier = modifier,
            titleId = pref.titleId,
            summaryId = pref.summaryId,
            index = index,
            groupSize = groupSize,
            startWidget = {
                PreferenceIcon(
                    icon = pref.icon,
                    contentDescription = stringResource(id = pref.titleId),
                )
            },
            isEnabled = isEnabled,
            bottomWidget = {
                ChoiceButtons(
                    options = buttons.mapValues { stringResource(it.value) },
                    selected = current,
                    enabled = isEnabled,
                    onSelect = { pref.set(it) },
                )
            },
        )
        return
    }
    val summary = pref.entries[current]
    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.summaryId,
        summary = summary,
        index = index,
        groupSize = groupSize,
        startWidget = {
            PreferenceIcon(
                icon = pref.icon,
                contentDescription = stringResource(id = pref.titleId),
            )
        },
        isEnabled = isEnabled,
        onClick = onClick
    )
}

/**
 * Expressive's connected button group: one joined row, exactly one chosen,
 * the chosen one changing shape. For a choice of two to four short options.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChoiceButtons(
    options: Map<String, String>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val last = options.size - 1
        options.entries.forEachIndexed { i, (value, label) ->
            ToggleButton(
                checked = value == selected,
                onCheckedChange = { if (it) onSelect(value) },
                enabled = enabled,
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    last -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
            ) {
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
