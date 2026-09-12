package com.example.modumessenger.feature.friends

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.modumessenger.R
import com.example.modumessenger.core.ui.components.ModuTopBar
import com.example.modumessenger.core.ui.components.rememberComingSoon

/** 친구 설정(부록 A §20). 세 항목 모두 데이터가 없어 `"준비 중입니다"` 만 띄운다. */
@Composable
fun SetFriendsScreen(onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    val comingSoon = rememberComingSoon(snackbarHostState)

    Scaffold(
        topBar = { ModuTopBar(title = stringResource(R.string.set_friends_title), onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SetFriendsRow(R.drawable.ic_baseline_person_24, R.string.set_friends_favorite, comingSoon)
            SetFriendsRow(R.drawable.ic_baseline_person_outline_24, R.string.set_friends_hidden, comingSoon)
            SetFriendsRow(R.drawable.ic_baseline_person_off_24, R.string.set_friends_blocked, comingSoon)
        }
    }
}

@Composable
private fun SetFriendsRow(iconRes: Int, labelRes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyLarge)
    }
}
