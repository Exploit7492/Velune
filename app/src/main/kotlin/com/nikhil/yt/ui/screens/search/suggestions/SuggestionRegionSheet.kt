package com.nikhil.yt.ui.screens.search.suggestions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nikhil.yt.constants.SuggestionRegionSlugToName

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestionRegionSheet(
    currentRegionSlug: String,
    onRegionSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        LazyColumn {
            items(SuggestionRegionSlugToName.entries.toList()) { (slug, name) ->
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (slug == currentRegionSlug) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onRegionSelected(slug)
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }
}
