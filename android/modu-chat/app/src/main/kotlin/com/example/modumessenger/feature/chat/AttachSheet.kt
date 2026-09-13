package com.example.modumessenger.feature.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.modumessenger.R
import com.example.modumessenger.core.ui.components.rememberSnackbarThen

/**
 * 첨부 시트(부록 A §9). 4열 격자로 앨범/카메라/파일/음성.
 * 고른 파일은 [onPicked] 로 넘기고 시트는 곧바로 닫힌다 — 업로드·전송은 ViewModel 이 한다.
 *
 * 한 칸을 누르면 기존 앱처럼 그 이름을 먼저 알린다. 스낵바는 채팅방 화면의 것을 그대로 쓴다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    snackbarHostState: SnackbarHostState,
    onDismiss: () -> Unit,
    onPicked: (Uri, AttachKind) -> Unit,
    takePictureUri: () -> Uri,
) {
    val sheetState = rememberModalBottomSheetState()
    val announce = rememberSnackbarThen(snackbarHostState)

    // 카메라는 결과 Uri 를 돌려주지 않는다. 미리 만들어 둔 자리(FileProvider)를 기억해 둔다.
    val cameraUri = remember { mutableStateOf<Uri?>(null) }

    val albumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onPicked(uri, AttachKind.IMAGE)
        onDismiss()
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { saved ->
        val uri = cameraUri.value
        if (saved && uri != null) onPicked(uri, AttachKind.IMAGE)
        onDismiss()
    }

    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) onPicked(uri, AttachKind.FILE)
        onDismiss()
    }

    val audioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) onPicked(uri, AttachKind.AUDIO)
        onDismiss()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            AttachItem(
                icon = painterResource(R.drawable.ic_baseline_image_24),
                label = stringResource(R.string.attach_album),
                announce = announce,
                modifier = Modifier.weight(1f),
            ) {
                albumLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }
            AttachItem(
                icon = painterResource(R.drawable.ic_baseline_photo_camera_24),
                label = stringResource(R.string.attach_camera),
                announce = announce,
                modifier = Modifier.weight(1f),
            ) {
                val uri = takePictureUri()
                cameraUri.value = uri
                cameraLauncher.launch(uri)
            }
            AttachItem(
                icon = painterResource(R.drawable.ic_baseline_attach_file_24),
                label = stringResource(R.string.attach_file),
                announce = announce,
                modifier = Modifier.weight(1f),
            ) {
                fileLauncher.launch(MIME_ANY)
            }
            AttachItem(
                icon = painterResource(R.drawable.ic_baseline_audio_file_24),
                label = stringResource(R.string.attach_audio),
                announce = announce,
                modifier = Modifier.weight(1f),
            ) {
                audioLauncher.launch(MIME_AUDIO)
            }
        }
    }
}

@Composable
private fun AttachItem(
    icon: Painter,
    label: String,
    announce: (String, () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable { announce(label, onClick) }.padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(text = label, style = MaterialTheme.typography.labelMedium)
    }
}

private const val MIME_ANY = "*/*"
private const val MIME_AUDIO = "audio/*"
