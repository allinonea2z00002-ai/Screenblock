package com.example.screenguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color

// নোটিফিকেশন (লোগোসহ)
object Notif {
    private const val CH = "sg_main"

    fun build(c: Context, text: String): Notification {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CH) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "ScreenGuard", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(c, CH)
            .setSmallIcon(R.drawable.ic_shield)
            .setColor(Color.parseColor("#6366F1"))
            .setContentTitle("ScreenGuard")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    fun update(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!Store.enabled(c)) {
            nm.cancel(1)
            return
        }
        val t = "সুরক্ষা চালু • আজ ব্লক ${bn(Store.todayCount(c))} • ${bn(Store.streak(c))} দিন পরিষ্কার"
        nm.notify(1, build(c, t))
    }

    fun cancel(c: Context) {
        c.getSystemService(NotificationManager::class.java).cancel(1)
    }
}
