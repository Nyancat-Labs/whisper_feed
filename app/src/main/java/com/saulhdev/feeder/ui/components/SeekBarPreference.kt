package com.saulhdev.feeder.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.data.content.FloatPref

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeekBarPreference(
    modifier: Modifier = Modifier,
    pref: FloatPref,
    index: Int = 1,
    groupSize: Int = 1,
    isEnabled: Boolean = true,
    onValueChange: ((Float) -> Unit) = {},
) {
    var currentValue by remember(pref) { mutableFloatStateOf(pref.peekOrDefault()) }

    BasePreference(
        modifier = modifier,
        titleId = pref.titleId,
        summaryId = pref.summaryId,
        index = index,
        groupSize = groupSize,
        isEnabled = isEnabled,
        startWidget = {
            PreferenceIcon(
                icon = pref.icon,
                contentDescription = stringResource(id = pref.titleId),
            )
        },
        bottomWidget = {
            // Three quarters slider, one quarter value, and the value centred
            // in its quarter rather than pushed against either edge. Material's
            // slider puts the value in a fixed column beside the track so the
            // track does not change length as the number does — a slider that
            // grows and shrinks while being dragged is very hard to aim.
            //
            // It used to be weight(1f) against a text with a minimum width and
            // no maximum, so a long value simply ate the slider: at its worst
            // the track was a few pixels wide and the value wrapped onto two
            // lines underneath it.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Slider(
                    value = currentValue,
                    valueRange = pref.minValue..pref.maxValue,
                    steps = pref.steps,
                    onValueChange = { currentValue = it },
                    onValueChangeFinished = {
                        // Was a blocking DataStore write on the main thread,
                        // called the moment a finger left the slider.
                        pref.set(currentValue)
                        onValueChange(currentValue)
                    },
                    enabled = isEnabled,
                    // "6 seconds", not "60 percent". A slider announces a
                    // percentage of its range by default, which for a range
                    // measured in seconds is a number with no meaning.
                    modifier = Modifier
                        .requiredHeight(24.dp)
                        .weight(SLIDER_WIDTH)
                        .semantics { stateDescription = pref.specialOutputs(currentValue) },
                    // No tick marks. Material draws one per step, which was
                    // never a deliberate difference between these two sliders
                    // — the opacity one has a hundred steps, so its ticks are
                    // too dense to resolve and it reads as a solid bar, while
                    // ten steps read as a row of dots. Same control, same
                    // look, whatever the range happens to be.
                    track = { sliderState ->
                        SliderDefaults.Track(
                            sliderState = sliderState,
                            enabled = isEnabled,
                            drawTick = { _, _ -> },
                        )
                    },
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f - SLIDER_WIDTH),
                ) {
                    // Every FloatPref already carried a specialOutputs lambda
                    // saying how to write its value down, and nothing read it:
                    // the sliders were a bare track with no number anywhere.
                    Text(
                        text = pref.specialOutputs(currentValue),
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    )
}

/**
 * How much of the row the track gets.
 *
 * The value needs enough room for the longest thing it will ever say and no
 * more; everything left over is worth giving to the track, because the track is
 * the part being aimed at.
 */
private const val SLIDER_WIDTH = 0.75f
