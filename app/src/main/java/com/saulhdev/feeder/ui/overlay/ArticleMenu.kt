/*
 * This file is part of Whisper
 * Copyright (c) 2026   Whisper contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.saulhdev.feeder.ui.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.DotsThreeVertical
import com.saulhdev.feeder.ui.icons.phosphor.Asterisk
import com.saulhdev.feeder.ui.icons.phosphor.EyeSlash
import com.saulhdev.feeder.ui.icons.phosphor.Info
import com.saulhdev.feeder.ui.icons.phosphor.Prohibit
import com.saulhdev.feeder.ui.icons.phosphor.ShareNetwork
import com.saulhdev.feeder.ui.icons.phosphor.Sparkle

/**
 * What can be done with one article beyond opening it.
 *
 * Every entry acts immediately and none of them opens a dialog: this menu is
 * reached mid-scroll, often one-handed, and a confirmation step for "show me
 * less of this" would cost more than getting it wrong does.
 *
 * More and Less record a preference the ranking does not read yet. That is
 * deliberate rather than a stub — a signal is only useful with history behind
 * it, so collecting from the day the menu appears means the first release that
 * ranks has something to rank with. Hide source and Share work fully today.
 *
 * @param tint set on the hero shape, where the menu sits over a photograph and
 *   the theme's content colours cannot be relied on to be visible.
 */
@Composable
fun ArticleOverflowMenu(
    onMoreLikeThis: () -> Unit,
    onLessLikeThis: () -> Unit,
    onHideSource: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    reasons: List<WeightReason> = emptyList(),
    pinned: Boolean = false,
    onPin: (Boolean) -> Unit = {},
    /**
     * Whether this card is leading a breaking story, and so has a promotion
     * to give back. Every other card has nothing to dismiss, and an entry
     * that does nothing on five cards out of six is worse than no entry.
     */
    breaking: Boolean = false,
    onDismissStory: () -> Unit = {},
) {
    var expanded by remember { mutableStateOf(false) }
    var explain by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // The same clear OverflowMenu does, for the same reason: Compose hands
    // focus back to whatever held it when this closes, and on a feed with the
    // search field in use that means the keyboard returns over the article the
    // menu was opened about. This menu is hand-rolled rather than built on
    // OverflowMenu — it carries its own explain state — so it does not get the
    // fix for free.
    IconButton(
        onClick = {
            focusManager.clearFocus()
            expanded = true
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = Phosphor.DotsThreeVertical,
            contentDescription = stringResource(R.string.more_options),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }

    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        MenuEntry(R.string.more_like_this, Phosphor.Sparkle) {
            expanded = false
            onMoreLikeThis()
        }
        MenuEntry(R.string.less_like_this, Phosphor.Prohibit) {
            expanded = false
            onLessLikeThis()
        }
        MenuEntry(
            if (pinned) R.string.unpin_article else R.string.pin_article,
            Phosphor.Asterisk,
        ) {
            expanded = false
            onPin(!pinned)
        }
        if (breaking) {
            // Only on the card that carries the promotion. Dismissing is not
            // hiding: the article keeps its place in the feed and loses the
            // treatment breaking news gets, which is the thing the reader is
            // actually objecting to when they have scrolled past it twice.
            MenuEntry(R.string.dismiss_story, Phosphor.Prohibit) {
                expanded = false
                onDismissStory()
            }
        }
        MenuEntry(R.string.hide_source, Phosphor.EyeSlash) {
            expanded = false
            onHideSource()
        }
        MenuEntry(R.string.share, Phosphor.ShareNetwork) {
            expanded = false
            onShare()
        }
        MenuEntry(R.string.why_this_size, Phosphor.Info) {
            expanded = false
            explain = true
        }
    }

    if (explain) WhyThisSizeDialog(reasons = reasons) { explain = false }
}

/**
 * The reasons an article is the size it is, at the article.
 *
 * A ranking nobody can see is one nobody can correct, and the settings screen
 * answers the general question — what the app has learned — while this answers
 * the specific one that actually gets asked: why *this* article, here, now. By
 * the time someone has walked to Settings they have stopped wondering about
 * the card that prompted it.
 *
 * Reasons are ordered by how much they moved the result, so the biggest one is
 * first rather than buried among the small ones.
 */
@Composable
private fun WhyThisSizeDialog(
    reasons: List<WeightReason>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.why_this_size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { reason ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(reason.labelId),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        if (reason.amount != 0f) {
                            Text(
                                text = "%+.2f".format(reason.amount),
                                style = MaterialTheme.typography.bodyMedium,
                                // Green and red would be the obvious choice and
                                // the wrong one: a negative signal is not a
                                // fault, it is the app saying something smaller
                                // is right for this article.
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(R.string.why_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
        },
    )
}

@Composable
private fun MenuEntry(
    textId: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(stringResource(textId)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
