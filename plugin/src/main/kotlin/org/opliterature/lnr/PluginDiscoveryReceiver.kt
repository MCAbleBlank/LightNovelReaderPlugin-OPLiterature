package org.opliterature.lnr

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 宿主通过发送 PLUGIN_DISCOVERY 广播来发现插件，这里只需存在该接收器即可
 */
class PluginDiscoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {}
}
