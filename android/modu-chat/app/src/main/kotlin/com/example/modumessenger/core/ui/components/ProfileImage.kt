package com.example.modumessenger.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.modumessenger.R
import com.example.modumessenger.core.network.ApiConfig

/**
 * 프로필/방 이미지. 파일 이름이 비어 있으면 기본 이미지를 쓴다.
 * 이미지 주소는 인증이 필요하고, Hilt 가 제공하는 Coil `ImageLoader` 가 토큰을 붙인다.
 */
@Composable
fun ProfileImage(
    fileName: String?,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val placeholder = painterResource(R.drawable.basic_profile_image)
    AsyncImage(
        model = fileName?.takeIf { it.isNotBlank() }?.let { ApiConfig.imageUrl(it) },
        contentDescription = null,
        modifier = modifier.size(size).clip(CircleShape),
        contentScale = ContentScale.Crop,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
    )
}
