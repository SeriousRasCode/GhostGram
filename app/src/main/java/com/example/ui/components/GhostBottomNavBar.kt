package com.example.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DynamicFeed
import androidx.compose.material.icons.filled.Groups
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
import androidx.compose.ui.text.font.FontWeight
import com.example.data.model.MainTab

@Composable
fun GhostBottomNavBar(
    currentTab: MainTab,
    showPersonal: Boolean,
    onTabSelected: (MainTab) -> Unit,
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
        }
    }
}
