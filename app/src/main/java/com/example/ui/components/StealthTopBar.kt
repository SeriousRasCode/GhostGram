package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.FocusFilter
import com.example.data.model.MainTab
import com.example.ui.theme.GhostSky
import com.example.ui.theme.GhostTeal

@Composable
fun StealthTopBar(
    currentTab: MainTab,
    filter: FocusFilter,
    searchQuery: String,
    onFilterSelect: (FocusFilter) -> Unit,
    onSearchChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAudit: () -> Unit,
    userSession: com.example.data.model.UserAccountSession? = null,
    modifier: Modifier = Modifier
) {
    var isSearchExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        // Compact Single Row: Brand + Stealth Indicator & 2 focused controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Profile (Far Left) + Brand & Stealth Dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Profile Avatar (Clickable to open Audit/Status)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            if (userSession?.isLoggedIn == true) GhostSky.copy(alpha = 0.15f) 
                            else MaterialTheme.colorScheme.surfaceVariant, 
                            CircleShape
                        )
                        .clip(CircleShape)
                        .clickable { onOpenAudit() }
                        .testTag("top_bar_profile_icon"),
                    contentAlignment = Alignment.Center
                ) {
                    if (userSession?.isLoggedIn == true && !userSession.profilePicPath.isNullOrBlank()) {
                        AsyncImage(
                            model = userSession.profilePicPath,
                            contentDescription = "My Profile",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                        )
                    } else {
                        val initial = userSession?.userName?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (userSession?.isLoggedIn == true) GhostSky else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Brand Title + Stealth Indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onOpenAudit() }
                ) {
                    Text(
                        text = "GhostGram",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.3).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(GhostTeal, CircleShape)
                    )
                }
            }

            // Exactly 2 Focused Actions: Search and Settings
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) onSearchChange("")
                    },
                    modifier = Modifier
                        .size(48.dp) // Accessibility: Standard touch target
                        .testTag("toggle_search_button")
                ) {
                    Icon(
                        imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(48.dp) // Accessibility: Standard touch target
                        .testTag("settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Settings and About",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Expandable Search Field (smooth animation)
        AnimatedVisibility(
            visible = isSearchExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = {
                    Text(
                        "Search in your focus feeds...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .testTag("search_text_field"),
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                )
            )
        }

        // Slim filter chips (Only shown on Feed tab)
        if (currentTab == MainTab.FEED) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CompactFilterChip(
                    selected = filter == FocusFilter.ALL,
                    label = "All",
                    onClick = { onFilterSelect(FocusFilter.ALL) },
                    tag = "filter_all"
                )
                CompactFilterChip(
                    selected = filter == FocusFilter.URGENT,
                    label = "⚡ Radar",
                    onClick = { onFilterSelect(FocusFilter.URGENT) },
                    tag = "filter_urgent"
                )
                CompactFilterChip(
                    selected = filter == FocusFilter.PINNED,
                    label = "📌 Pinned",
                    onClick = { onFilterSelect(FocusFilter.PINNED) },
                    tag = "filter_pinned"
                )
                CompactFilterChip(
                    selected = filter == FocusFilter.BOOKMARKS,
                    label = "⭐ Saved",
                    onClick = { onFilterSelect(FocusFilter.BOOKMARKS) },
                    tag = "filter_saved"
                )
            }
        }
    }
}

@Composable
private fun CompactFilterChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    tag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                )
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            selectedLabelColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.testTag(tag)
    )
}
