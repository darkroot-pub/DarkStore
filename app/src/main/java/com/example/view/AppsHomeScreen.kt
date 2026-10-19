package com.example.view

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.EmptyCatalogStateCard
import com.example.AppItemCardView
import com.example.DiscoveryFeedState
import com.example.data.AppEntity
import com.example.data.BannerEntity
import com.example.data.CollectionEntity

/**
 * The Apps (home) tab, rebuilt to be cheap to scroll.
 *
 * Design: category chips -> banners -> collections -> three short horizontal shelves -> one
 * paged "All apps" list. It reuses the existing components, so the look of the cards, banners
 * and collections is unchanged; what is new is the structure:
 *
 *  - ONE lazy list. Every row has a stable key and a content type so rows are recycled.
 *  - Shelves are derived once per data change (remember), not on every recomposition.
 *  - Per-row work is a map lookup; row click lambdas are remembered per app id.
 *  - The long list is paged (40 at a time) so the first frame composes a few rows, not the catalog.
 *  - No infinite animations, no per-item entry animations, no item-placement animations.
 *  - While searching or a category filter is active the shelves are hidden and only the
 *    (already filtered) result list shows, like the old Apps tab behaved.
 *
 * Search and category filtering already happen in StoreViewModel.apps (off the main thread),
 * so [feedState].apps arrives filtered.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsHomeScreen(
    isDarkMode: Boolean,
    feedState: DiscoveryFeedState,
    searchQuery: String,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    accent: Color,
    textPrimary: Color,
    textSecondary: Color,
    cardBgColor: Color,
    cardBorderColor: Color,
    onAppClick: (AppEntity) -> Unit,
    onActionClick: (AppEntity) -> Unit,
    onBuyClick: (AppEntity) -> Unit,
    onRegisterClick: (AppEntity) -> Unit,
    onCollectionClick: (CollectionEntity) -> Unit,
    onBannerClick: (BannerEntity) -> Unit
) {
    val categories = remember { listOf("All", "Utilities", "Games", "Tools", "Entertainment") }

    // Latest callbacks behind stable State reads, so remembered per-row lambdas never go stale.
    val latestAppClick = rememberUpdatedState(onAppClick)
    val latestAction = rememberUpdatedState(onActionClick)
    val latestBuy = rememberUpdatedState(onBuyClick)
    val latestRegister = rememberUpdatedState(onRegisterClick)

    // De-duplicated once per data change: duplicate ids would crash a keyed LazyColumn.
    val apps = remember(feedState.apps) { feedState.apps.distinctBy { it.id } }
    val standard = remember(apps) { apps.filter { !it.isUpcoming } }
    val isBrowsing = searchQuery.isBlank() && selectedCategory == "All"

    // Shelves only exist when browsing the full catalog.
    val recommended = remember(standard, isBrowsing) {
        if (!isBrowsing) emptyList() else standard.filter { it.isFeatured }.ifEmpty { standard }.take(12)
    }
    val popular = remember(standard, isBrowsing) {
        if (!isBrowsing) emptyList() else
            standard.filter { it.isPopular }.ifEmpty { standard }
                .sortedByDescending { it.rating.toFloatOrNull() ?: 0f }.take(12)
    }
    val topFree = remember(standard, isBrowsing) {
        if (!isBrowsing) emptyList() else
            standard.filter { !it.isPremium }.sortedByDescending { it.rating.toFloatOrNull() ?: 0f }.take(12)
    }
    fun moreOf(title: String, list: List<AppEntity>) = CollectionEntity(
        id = "more_$title", title = title, color = "#10B981", appIds = list.map { it.id }
    )

    val downloadsById = remember(feedState.downloads) { feedState.downloads.associateBy { it.id } }

    // Paging for the long list: compose a screenful or two first, grow as the user nears the end.
    var shownCount by rememberSaveable(selectedCategory, searchQuery) { mutableStateOf(PAGE) }
    val listState = rememberLazyListState()
    val visibleApps = remember(standard, shownCount) { standard.take(shownCount) }
    val nearEnd by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, standard.size) {
        if (nearEnd && shownCount < standard.size) shownCount += PAGE
    }

    val pullState = rememberPullToRefreshState()
    LaunchedEffect(pullState.isRefreshing) {
        if (pullState.isRefreshing) {
            onRefresh()
            // Fail-safe, same as the old feed: stop the spinner if no refresh state arrives (offline/cached).
            kotlinx.coroutines.delay(1200)
            if (!isRefreshing) pullState.endRefresh()
        }
    }
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) pullState.startRefresh() else pullState.endRefresh()
    }

    Box(Modifier.fillMaxSize().nestedScroll(pullState.nestedScrollConnection)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. Category chips
            item(key = "chips", contentType = "chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories, key = { it }, contentType = { "chip" }) { cat ->
                        val selected = cat == selectedCategory
                        val bg = if (selected) accent.copy(alpha = 0.18f) else textSecondary.copy(alpha = 0.08f)
                        Text(
                            text = cat,
                            color = if (selected) accent else textSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50.dp))
                                .background(bg)
                                .clickable { onCategorySelect(cat) }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            if (isBrowsing && feedState.banners.isNotEmpty()) {
                item(key = "banners", contentType = "banners") {
                    BannerCarousel(
                        banners = feedState.banners,
                        isDark = isDarkMode,
                        accent = accent,
                        onBannerClick = onBannerClick
                    )
                }
            }

            if (isBrowsing && feedState.collections.isNotEmpty()) {
                item(key = "collections", contentType = "collections") {
                    CollectionsRow(
                        collections = feedState.collections,
                        apps = apps,
                        textPrimary = textPrimary,
                        onClick = onCollectionClick
                    )
                }
            }

            if (standard.isEmpty()) {
                item(key = "empty", contentType = "empty") {
                    EmptyCatalogStateCard(
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accentGreen = accent,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor
                    )
                }
            } else {
                if (recommended.isNotEmpty()) {
                    item(key = "t_rec", contentType = "title") {
                        SectionTitleRow(tr("sec_recommended"), accent, textPrimary) {
                            onCollectionClick(moreOf("Recommended For You", standard))
                        }
                    }
                    item(key = "r_rec", contentType = "shelf") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(recommended, key = { "rec_" + it.id }, contentType = { "tile" }) { app ->
                                val click = remember(app.id) { { latestAppClick.value(app) } }
                                SquareAppTile(app, isDarkMode, accent, textPrimary, textSecondary, click)
                            }
                        }
                    }
                }
                if (popular.isNotEmpty()) {
                    item(key = "t_pop", contentType = "title") {
                        SectionTitleRow(tr("sec_popular"), accent, textPrimary) {
                            onCollectionClick(moreOf("Most Popular", popular))
                        }
                    }
                    item(key = "r_pop", contentType = "shelf") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(popular, key = { "pop_" + it.id }, contentType = { "tile" }) { app ->
                                val click = remember(app.id) { { latestAppClick.value(app) } }
                                val rating = feedState.localAppRatings[app.id]?.first ?: app.rating
                                PopularAppCard(app, rating, isDarkMode, accent, textPrimary, textSecondary, click)
                            }
                        }
                    }
                }
                if (topFree.isNotEmpty()) {
                    item(key = "t_free", contentType = "title") {
                        SectionTitleRow(tr("sec_free"), accent, textPrimary) {
                            onCollectionClick(moreOf("Top Free", topFree))
                        }
                    }
                    item(key = "r_free", contentType = "shelf") {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                            items(topFree, key = { "free_" + it.id }, contentType = { "tile" }) { app ->
                                val click = remember(app.id) { { latestAppClick.value(app) } }
                                val rating = feedState.localAppRatings[app.id]?.first ?: app.rating
                                CompactAppCard(app, rating, isDarkMode, accent, textPrimary, textSecondary, click)
                            }
                        }
                    }
                }

                // 2. The full list, paged.
                item(key = "t_all", contentType = "title") {
                    SectionTitleRow(if (isBrowsing) "All apps" else "Results", accent, textPrimary, null)
                }
                items(visibleApps, key = { it.id }, contentType = { "app_row" }) { app ->
                    val click = remember(app.id) { { latestAppClick.value(app) } }
                    val action = remember(app.id) { { latestAction.value(app) } }
                    val buy = remember(app.id) { { latestBuy.value(app) } }
                    val register = remember(app.id) { { latestRegister.value(app) } }
                    AppItemCardView(
                        app = app,
                        downloadState = downloadsById[app.id],
                        installedInfo = feedState.installedAppsInfo[app.packageName],
                        overriddenRating = feedState.localAppRatings[app.id]?.first ?: app.rating,
                        accentGreen = accent,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        cardBgColor = cardBgColor,
                        cardBorderColor = cardBorderColor,
                        purchased = app.id in feedState.purchasedAppIds,
                        registered = app.id in feedState.preRegisteredAppIds,
                        onBuyClick = buy,
                        onRegisterClick = register,
                        onClick = click,
                        onActionClick = action
                    )
                }
                if (visibleApps.size >= standard.size) {
                    item(key = "end", contentType = "end") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                            Text("You're all caught up.", color = textSecondary.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                } else {
                    item(key = "more", contentType = "end") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }

        if (pullState.verticalOffset > 0f || pullState.isRefreshing) {
            PullToRefreshContainer(
                state = pullState,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = cardBgColor,
                contentColor = accent
            )
        }
    }
}

private const val PAGE = 40
