package com.example.modumessenger.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.modumessenger.MainActivity
import com.example.modumessenger.R
import com.example.modumessenger.core.model.ChatType
import com.example.modumessenger.core.session.FriendNames
import com.example.modumessenger.core.util.NotificationText
import com.example.modumessenger.data.dto.FcmMessageDto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 채팅 알림 하나를 띄운다(스펙 §7).
 *
 * 기존 앱은 알림 id 를 상수 `9999` 로 써서 방이 달라도 서로 덮어썼다. 여기서는 방마다 id 를 나눈다.
 */
@Singleton
class ChatNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val friendNames: FriendNames,
) {

    fun notify(data: FcmMessageDto) {
        val roomId = data.roomId.orEmpty()
        // 서버가 숫자가 아닌 값을 보내도 죽지 않는다(기존 앱은 parseInt 로 터졌다).
        val chatType = data.type?.toIntOrNull() ?: ChatType.TEXT

        val (title, body) = NotificationText.build(
            names = friendNames.names.value,
            roomName = data.title,
            senderUserId = data.sender,
            senderName = data.senderName,
            memberCount = data.memberCount,
            message = data.message,
            isImage = chatType == ChatType.IMAGE,
        )

        ensureChannel()

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_ROOM_ID, roomId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId(roomId),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_VIBRATE)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        if (!canNotify()) return
        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId(roomId), notification)
        }
    }

    /** 방마다 알림 하나. 같은 방의 새 메시지는 기존 알림을 갱신한다. */
    private fun notificationId(roomId: String): Int = roomId.hashCode()

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = CHANNEL_DESCRIPTION
            enableLights(true)
            enableVibration(true)
            setShowBadge(false)
            vibrationPattern = VIBRATION_PATTERN
        }
        manager.createNotificationChannel(channel)
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val CHANNEL_ID = "modu-chat"
        const val CHANNEL_NAME = "modu-chat-channel"
        const val CHANNEL_DESCRIPTION = "modu-chat-messaging-channel"
        val VIBRATION_PATTERN = longArrayOf(100, 200, 100, 200)
    }
}
