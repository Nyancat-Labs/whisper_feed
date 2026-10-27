package com.saulhdev.feeder.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/*
 * Three tones, as Material has them: positive in the accent colour, neutral
 * (Reset, Dismiss) quieter, destructive (Delete, Sign out, Forget) in the
 * error colour. "Not positive" used to mean the tertiary colour, a pink that
 * said nothing about what the button did (owner, 25 October).
 */

@Composable
fun ActionButton(
    modifier: Modifier = Modifier,
    text: String,
    positive: Boolean = true,
    /** Removes or forgets something: drawn in the error colour. */
    destructive: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    // The main action is filled primary, as Expressive draws it; it was the
    // light primary container, the same lavender as a selected chip, so the
    // one thing to press looked like a choice already made (review 16, 21).
    FilledTonalButton(
        modifier = modifier,
        colors = ButtonDefaults.filledTonalButtonColors(
            contentColor = when {
                destructive -> MaterialTheme.colorScheme.onErrorContainer
                positive -> MaterialTheme.colorScheme.onPrimary
                else     -> MaterialTheme.colorScheme.onSecondaryContainer
            },
            containerColor = when {
                destructive -> MaterialTheme.colorScheme.errorContainer
                positive -> MaterialTheme.colorScheme.primary
                else     -> MaterialTheme.colorScheme.secondaryContainer
            }
        ),
        enabled = enabled,
        onClick = onClick,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = text)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
fun OutlinedActionButton(
    modifier: Modifier = Modifier,
    text: String,
    positive: Boolean = true,
    /** Removes or forgets something: drawn in the error colour. */
    destructive: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = when {
                destructive -> MaterialTheme.colorScheme.error
                else     -> MaterialTheme.colorScheme.primary
            },
        ),
        border = BorderStroke(
            width = 1.dp,
            color = when {
                destructive -> MaterialTheme.colorScheme.error
                positive -> MaterialTheme.colorScheme.primary
                else     -> MaterialTheme.colorScheme.outline
            },
        ),
        enabled = enabled,
        onClick = onClick,
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = text)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
fun SingleChoiceSegmentedButtonRowScope.SegmentedTabButton(
    text: String,
    icon: ImageVector,
    index: Int,
    count: Int,
    selected: () -> Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    SegmentedButton(
        modifier = modifier,
        selected = selected(),
        onClick = onClick,
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainerHighest),
        colors = SegmentedButtonDefaults.colors(
            activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
            activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            inactiveContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            inactiveContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = itemShape(index, count, selected),
        icon = {
            Icon(imageVector = icon, contentDescription = text)
        }
    ) {
        Text(text = text)
    }
}

@Composable
fun itemShape(index: Int, count: Int, selected: () -> Boolean): Shape {
    if (count == 1 || selected()) return MaterialTheme.shapes.extraLarge

    return when (index) {
        0 -> MaterialTheme.shapes.extraLarge.copy(
            topEnd = CornerSize(4.dp),
            bottomEnd = CornerSize(4.dp)
        )

        count - 1 -> MaterialTheme.shapes.extraLarge.copy(
            topStart = CornerSize(4.dp),
            bottomStart = CornerSize(4.dp)
        )

        else -> MaterialTheme.shapes.extraSmall
    }
}