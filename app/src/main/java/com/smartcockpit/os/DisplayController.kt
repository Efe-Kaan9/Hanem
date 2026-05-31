package com.smartcockpit.os

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.Window
import android.view.WindowManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DisplayController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val devicePolicyManager = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, AdminReceiver::class.java)

    fun setBrightness(level: Int) {
        // level 0-255
        try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                level
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun enterDeepSleep() {
        if (devicePolicyManager.isAdminActive(adminComponent)) {
            devicePolicyManager.lockNow()
        }
    }
}
