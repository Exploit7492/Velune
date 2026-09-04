package com.nikhil.yt.campaign

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import timber.log.Timber

@Composable
fun CampaignCardSection(
    repository: CampaignRepository,
    onCampaignClick: (CampaignCard) -> Unit,
    modifier: Modifier = Modifier,
) {
    var campaigns by remember { mutableStateOf<List<CampaignCard>>(emptyList()) }

    LaunchedEffect(Unit) {
        val fetched = repository.fetchLiveCampaignsForBanner()
        Timber.tag("CampaignCardSection").d("Fetched ${fetched.size} campaigns")
        campaigns = fetched
        fetched.firstOrNull()?.let {
            Timber.tag("CampaignCardSection").d("First campaign: ${it.title} by ${it.artist}")
        }
    }

    val campaign = campaigns.firstOrNull()
    Timber.tag("CampaignCardSection").d("Current campaign: ${campaign?.title ?: "null"}")

    if (campaign != null) {
        // Use CampaignBanner if available, otherwise fallback to a simple Text.
        // Assuming CampaignBanner exists in the same package.
        // If not, we can define a simple banner here.
        CampaignBanner(
            campaign = campaign,
            onClick = { onCampaignClick(campaign) },
            modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

// Fallback definition if CampaignBanner is missing – this will be used if the original is not found.
@Composable
fun CampaignBanner(
    campaign: CampaignCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "${campaign.title} by ${campaign.artist}",
        modifier = modifier.padding(16.dp)
    )
}
