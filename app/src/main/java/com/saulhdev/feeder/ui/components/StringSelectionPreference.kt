package com.saulhdev.feeder.ui.components

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