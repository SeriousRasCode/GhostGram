package com.example.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import com.example.data.model.MainTab
import coil.compose.AsyncImage

@Composable
fun GhostBottomNavBar(
    currentTab: MainTab,
    showPersonal: Boolean,
    onTabSelected: (MainTab) -> Unit,
    musicCover: Any? = null,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        windowInsets = WindowInsets.navigationBars,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = modifier.testTag("main_bottom_nav")
    ) {
        // Tab 1: Feed (New things as feed)
        NavigationBarItem(
            selected = currentTab == MainTab.FEED,
            onClick = { onTabSelected(MainTab.FEED) },
            icon = {
                Icon(
                    imageVector = if (currentTab == MainTab.FEED) Icons.Filled.DynamicFeed else Icons.Outlined.DynamicFeed,
                    contentDescription = "Feed"
                )
            },
            label = {
                Text(
                    text = "Feed",
                    fontWeight = if (currentTab == MainTab.FEED) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("nav_tab_feed")
        )

        // Tab 2: Groups (Groups list user added in settings)
        NavigationBarItem(
            selected = currentTab == MainTab.GROUPS,
            onClick = { onTabSelected(MainTab.GROUPS) },
            icon = {
                Icon(
                    imageVector = if (currentTab == MainTab.GROUPS) Icons.Filled.Groups else Icons.Outlined.Groups,
                    contentDescription = "Groups"
                )
            },
            label = {
                Text(
                    text = "Groups",
                    fontWeight = if (currentTab == MainTab.GROUPS) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("nav_tab_groups")
        )

        NavigationBarItem(
            selected = currentTab == MainTab.MUSIC,
            onClick = { onTabSelected(MainTab.MUSIC) },
            icon = {
                Box(
                    modifier = Modifier.size(if (currentTab == MainTab.MUSIC) 50.dp else 42.dp)
                        .clip(CircleShape)
                        .background(
                            if (currentTab == MainTab.MUSIC) Brush.linearGradient(
                                listOf(Color(0xFF6A5AE0), Color(0xFFDA5C9B), Color(0xFFFFA866))
                            ) else Brush.linearGradient(
                                listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh)
                            )
                        )
                        .border(2.dp, Brush.sweepGradient(listOf(Color(0xFF8B7CFF), Color(0xFFFF79B7), Color(0xFFFFC36C), Color(0xFF8B7CFF))), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (musicCover != null) {
                        AsyncImage(model = musicCover, contentDescription = "Currently playing album cover",
                            modifier = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Icon(
                            imageVector = Icons.Filled.LibraryMusic,
                            contentDescription = "Music",
                            tint = if (currentTab == MainTab.MUSIC) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(if (currentTab == MainTab.MUSIC) 27.dp else 23.dp)
                        )
                    }
                }
            },
            label = {
                Text(
                    text = "Music",
                    fontWeight = if (currentTab == MainTab.MUSIC) FontWeight.Bold else FontWeight.Medium
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = Color.Transparent,
                selectedIconColor = Color.White,
                selectedTextColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("nav_tab_music")
        )

        // Tab 3: Channels (Channels list user added in settings)
        NavigationBarItem(
            selected = currentTab == MainTab.CHANNELS,
            onClick = { onTabSelected(MainTab.CHANNELS) },
            icon = {
                Icon(
                    imageVector = if (currentTab == MainTab.CHANNELS) Icons.Filled.Campaign else Icons.Outlined.Campaign,
                    contentDescription = "Channels"
                )
            },
            label = {
                Text(
                    text = "Channels",
                    fontWeight = if (currentTab == MainTab.CHANNELS) FontWeight.Bold else FontWeight.Normal
                )
            },
            colors = NavigationBarItemDefaults.colors(
                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.testTag("nav_tab_channels")
        )

        // Tab 4: Personal (Optional 1-on-1 chats)
        if (showPersonal) {
            NavigationBarItem(
                selected = currentTab == MainTab.PERSONAL,
                onClick = { onTabSelected(MainTab.PERSONAL) },
                icon = {
                    Icon(
                        imageVector = if (currentTab == MainTab.PERSONAL) Icons.Filled.Person else Icons.Outlined.Person,
                        contentDescription = "Personal"
                    )
                },
                label = {
                    Text(
                        text = "Personal",
                        fontWeight = if (currentTab == MainTab.PERSONAL) FontWeight.Bold else FontWeight.Normal
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("nav_tab_personal")
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}
