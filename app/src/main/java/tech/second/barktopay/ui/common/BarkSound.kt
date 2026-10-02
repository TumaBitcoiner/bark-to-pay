package tech.second.barktopay.ui.common

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import tech.second.barktopay.R

/**
 * Plays the bark once when a payment goes through. Uses the notification audio
 * stream (respects silent/DND). Fire-and-forget: any audio failure is swallowed
 * so it can never break a payment screen.
 */
fun playBark(context: Context) {
    runCatching {
        val res = context.applicationContext.resources
        val afd = res.openRawResourceFd(R.raw.bark) ?: return@runCatching
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
        afd.close()
        player.setOnCompletionListener { it.release() }
        player.prepare()
        player.start()
    }
}
