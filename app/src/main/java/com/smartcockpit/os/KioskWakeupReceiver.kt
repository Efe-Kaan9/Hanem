package com.smartcockpit.os

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartcockpit.ui.MainActivity

class KioskWakeupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(launchIntent)
    }
}
