package com.saulhdev.feeder.ui.pages


import androidx.core.net.toUri
import com.saulhdev.feeder.utils.usableImageUrl
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.saulhdev.feeder.ui.overlay.TrackArticleReading
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.rememberNavController
import com.saulhdev.feeder.R
import com.saulhdev.feeder.ui.overlay.CARD_MARGIN
import com.saulhdev.feeder.ui.components.HeaderAction
import com.saulhdev.feeder.ui.components.RoundButton
import com.saulhdev.feeder.ui.components.SaveButton
import com.saulhdev.feeder.ui.overlay.articlePlaceholder
import com.saulhdev.feeder.ui.components.ViewWithActionBar
import com.saulhdev.feeder.ui.components.WithBidiDeterminedLayoutDirection
import com.saulhdev.feeder.ui.icons.Phosphor
import com.saulhdev.feeder.ui.icons.phosphor.ArrowSquareOut
import com.saulhdev.feeder.ui.icons.phosphor.ShareNetwork
import com.saulhdev.feeder.utils.blobFile
import com.saulhdev.feeder.utils.blobFullFile
import com.saulhdev.feeder.utils.blobFullInputStream
import com.saulhdev.feeder.utils.blobInputStream
import com.saulhdev.feeder.utils.extensions.koinNeoViewModel
import com.saulhdev.feeder.utils.extensions.launchView
import com.saulhdev.feeder.utils.extensions.shareIntent
import com.saulhdev.feeder.utils.readerBody
import com.saulhdev.feeder.utils.readerBodyText
import com.saulhdev.feeder.utils.formatArticleAge
import com.saulhdev.feeder.ui.overlay.ArticleMeta
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.saulhdev.feeder.utils.unicodeWrap
import com.saulhdev.feeder.viewmodels.ArticleViewModel
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toLocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalTime::class)
@Composable
fun ArticlePage(
    articleId: String,
    viewModel: ArticleViewModel = koinNeoViewModel(),
    onDismiss: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current

    val state by viewModel.articleState.collectAsState(initial = null)

    // Times the read. Counts forward only while this screen is resumed, so
    // closing the app or locking the phone mid-article stops it rather than
    // leaving a start time for a later resume to turn into a three-day read.
    TrackArticleReading(articleId = articleId, onRead = viewModel::addReading)

    LaunchedEffect(articleId) {
        viewModel.setArticleId(articleId)
    }

    // The readable article, fetched when this screen opens unless it is
    // already stored. It used to be shown only when the *feed* had "fetch full
    // articles" set, which is off by default — so opening an article normally
    // showed the paragraph the feed sent rather than the article, which is
    // what sent people to the browser instead.
    val fullText by viewModel.fullText.collectAsState()
    LaunchedEffect(articleId, state?.article?.uuid, state?.article?.link) {
        val article = state?.article

        // The id and the link must belong to the same article, and for a
        // moment after every tap they do not. `articleId` is the navigation
        // argument and changes instantly; `state` is fed by a database flow
        // and still holds the article that was open before. This effect used
        // to key on the id and the link alone, so it fired on the pair
        // (new id, previous article's link) — which has never been a real
        // article — and fetched the previous page into the new one's file.
        //
        // Permanently, because loadFullText returns early when the file
        // already exists. Every later opening of that article then showed the
        // one read before it: the right headline in the bar, somebody else's
        // article underneath. It survived being closed and reopened, which is
        // what made it look like the reader rather than the fetch.
        if (article == null || article.uuid != articleId) return@LaunchedEffect

        val link = article.link
        if (!link.isNullOrBlank()) {
            viewModel.loadFullText(articleId, link, context.filesDir)
        }
    }
    DisposableEffect(articleId) { onDispose { viewModel.resetFullText(articleId) } }

    // Both halves, and the id is the half that matters. One view model serves
    // every article the reader opens, so a Ready left behind by the article
    // read before this one would otherwise send this page looking for a file
    // that was never fetched for it.
    val showFullArticle = fullText.articleId == articleId &&
            fullText.state == ArticleViewModel.FullText.Ready

    val appName = stringResource(R.string.app_name)
    // Both of these fell back to the literal "Neo Feed", which is the wrong
    // app and, in the second case, not an address either — it was handed
    // straight to the open-in-browser and share actions. An untitled article
    // now falls back to this app's own name, and a linkless one to nothing at
    // all, which is what it has.
    val title by remember(appName) { derivedStateOf { state?.article?.title ?: appName } }
    val currentUrl by remember { derivedStateOf { state?.article?.link.orEmpty() } }
    val subTitle by remember {
        derivedStateOf {
            currentUrl.toUri().host
                ?: state?.source?.title
                ?: appName
        }
    }
    val feedTitle by remember(appName) { derivedStateOf { state?.source?.title ?: appName } }

    val navController = rememberNavController()
    BackHandler(onDismiss == null) {
        if (navController.currentBackStackEntry?.destination?.route == null) {
            activity?.finish()
        } else {
            navController.popBackStack()
        }
    }

    // From the configuration rather than from Locale.getDefault(), which
    // Compose cannot observe: change the phone's language and every date on
    // this screen keeps the old one until something unrelated happens to
    // recompose it.
    val locale = LocalConfiguration.current.locales[0]
    val dateTimeFormat: DateTimeFormatter =
        remember(locale) {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL, FormatStyle.SHORT)
                .withLocale(locale)
        }

    val authorDate = when {
        state?.article?.author == null && state?.article?.pubDate != null && (state?.article?.pubDate
            ?: 0L) > 0L ->
            stringResource(
                R.string.on_date,
                Instant.fromEpochMilliseconds(state?.article?.pubDate ?: 0L)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .toJavaLocalDateTime()
                    .format(dateTimeFormat)
            )

        state?.article?.author != null && (state?.article?.pubDate ?: 0L) > 0L ->
            stringResource(
                R.string.by_author_on_date,
                // Must wrap author in unicode marks to ensure it formats
                // correctly in RTL
                context.unicodeWrap(state?.article?.author ?: ""),
                Instant.fromEpochMilliseconds(state?.article?.pubDate ?: 0L)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .toJavaLocalDateTime()
                    .format(dateTimeFormat)
            )

        else -> null
    }

    // Read here rather than at the call sites: those are inside LazyListScope,
    // which is not a composable context.
    val placeholder = articlePlaceholder()

    ViewWithActionBar(
        title = title,
        titleSize = 16.sp,
        subTitle = subTitle,
        showBackButton = true,
        onBackAction = onDismiss,
        actions = {
            // Hidden rather than disabled when there is nothing to open: an
            // article without a link is rare enough that a permanently dead
            // button would read as a bug in the app rather than a gap in the
            // feed. The same goes for sharing it.
            if (currentUrl.isNotBlank()) {
                RoundButton(
                    icon = Phosphor.ArrowSquareOut,
                    description = stringResource(id = R.string.article_open_original),
                ) {
                    context.launchView(currentUrl)
                }
            }
            // The reader kept upstream's heart while the feed moved to the
            // Whisper save mark, so the same action had two different icons
            // depending on which screen it was pressed from — and this one was
            // labelled "Share" for screen readers, which it has never done.
            SaveButton(
                saved = state?.article?.bookmarked ?: false,
                onSavedChange = { viewModel.bookmarkArticle(articleId, it) },
            )
            // Share, as a button rather than the first item of a menu. The
            // menu held two things and the second was a third-party summary
            // service nobody here uses, so it existed to hide one action
            // behind two taps.
            if (currentUrl.isNotBlank()) {
                HeaderAction(
                    icon = Phosphor.ShareNetwork,
                    description = stringResource(id = R.string.share),
                    onClick = { context.shareIntent(currentUrl, title) },
                )
            }
        }
    ) { paddingValues ->
        SelectionContainer {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = paddingValues.calculateTopPadding(),
                        // The margin the feed uses. Both of these screens sat
                        // at four points, so an article opened from a card
                        // that started sixteen points in began four points in,
                        // and the two screens read as different apps.
                        start = CARD_MARGIN,
                        end = CARD_MARGIN,
                        bottom = paddingValues.calculateBottomPadding() + 8.dp
                    ),
            ) {
                // Read once, before anything is drawn: the article's opening
                // picture is drawn above the headline, and has to come out of
                // the body first so it is not drawn twice. See readerBody.
                // Also matched on the id: a Ready left by the article read
                // before this one is no full text for this one.
                val baseUrl = state?.article?.link ?: ""
                val cardPicture = usableImageUrl(state?.article?.imageUrl)
                val article = when {
                    showFullArticle && blobFullFile(articleId, context.filesDir).isFile ->
                        blobFullInputStream(articleId, context.filesDir).use {
                            readerBody(it, baseUrl, articleTitle = title, leadImageUrl = cardPicture, author = state?.article?.author)
                        }
                    !showFullArticle && blobFile(articleId, context.filesDir).isFile ->
                        blobInputStream(articleId, context.filesDir).use {
                            readerBody(it, baseUrl, articleTitle = title, leadImageUrl = cardPicture, author = state?.article?.author)
                        }
                    else -> null
                }

                // The picture first and edge to edge, then the headline, as a
                // card has them, so an article opens looking like the card it
                // was opened from. It sat between the byline and the text,
                // inset by the margins, and the two screens read as two apps.
                article?.leadImage?.let { picture ->
                    item {
                        AsyncImage(
                            model = picture,
                            contentDescription = null,
                            placeholder = painterResource(placeholder),
                            error = painterResource(placeholder),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .bleed(CARD_MARGIN)
                                .aspectRatio(16f / 9f),
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                item {
                    WithBidiDeterminedLayoutDirection(paragraph = title) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineMedium,
                            // Medium, as the cards' headlines are.
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .fillMaxWidth()
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    // The card's byline, the same line in the same type: the
                    // source's mark, its category, the publication's name and
                    // the article's age. The name was a line of its own at
                    // headline size, in full - "GSMArena.com - Latest
                    // articles" - and louder than anything but the title.
                    ArticleMeta(
                        category = state?.source?.tags?.firstOrNull().orEmpty(),
                        source = feedTitle,
                        age = state?.article?.let {
                            formatArticleAge(context, it.primarySortTime.toEpochMilliseconds())
                        }.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        iconUrl = usableImageUrl(state?.source?.feedImage?.toString()),
                    )

                    if (authorDate != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        WithBidiDeterminedLayoutDirection(paragraph = authorDate) {
                            Text(
                                text = authorDate,
                                // Quiet: the card's byline above already says
                                // how long ago; this is for who and exactly when.
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Also matched on the id: a progress bar for the article
                // read before this one is a progress bar that never finishes.
                if (fullText.articleId == articleId &&
                    fullText.state == ArticleViewModel.FullText.Loading
                ) {
                    item {
                        // The feed's own excerpt stays on screen while the full
                        // article is fetched, so the screen has something to
                        // read immediately rather than a spinner over nothing.
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                if (article != null) {
                    readerBodyText(
                        article = article,
                        baseUrl = baseUrl,
                        imagePlaceholder = placeholder,
                        onLinkClick = context::launchView,
                    )
                } else {
                    item {
                        NotFoundView()
                    }
                }
            }
        }
    }
}

/**
 * Out through the list's side margins to the screen's edges: the reader's
 * text keeps the feed's margin, and its lead picture runs the full width as a
 * card's does.
 */
private fun Modifier.bleed(margin: Dp): Modifier = layout { measurable, constraints ->
    val edge = margin.roundToPx()
    val width = constraints.maxWidth + 2 * edge
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-edge, 0) }
}

@Composable
fun NotFoundView() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = stringResource(id = R.string.article_not_found))
    }
}