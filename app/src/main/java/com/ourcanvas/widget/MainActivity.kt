package com.ourcanvas.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val input = findViewById<EditText>(R.id.inviteLinkInput)
        val status = findViewById<TextView>(R.id.statusText)
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        input.setText(prefs.getString(KEY_ROOM_ID, ""))

        findViewById<Button>(R.id.connectButton).setOnClickListener {
            val raw = input.text.toString().trim()
            val roomId = extractRoomId(raw)
            if (roomId == null) {
                status.text = "That link doesn't look right. Paste the full OurCanvas invite link."
                return@setOnClickListener
            }

            prefs.edit().putString(KEY_ROOM_ID, roomId).apply()
            status.text = "Canvas connected. Add the OurCanvas widget to your home screen."
            sendBroadcast(
                Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                    .setComponent(ComponentName(this, CanvasWidgetProvider::class.java))
            )
        }
    }

    private fun extractRoomId(value: String): String? {
        val candidate = if (value.matches(Regex("^[a-fA-F0-9]{32}$"))) {
            value
        } else {
            try {
                Uri.parse(value).getQueryParameter("room")
            } catch (_: Exception) {
                null
            }
        } ?: return null

        return candidate.takeIf { it.matches(Regex("^[a-fA-F0-9]{32}$")) }
    }

    companion object {
        const val PREFS_NAME = "ourcanvas_preferences"
        const val KEY_ROOM_ID = "room_id"
    }
}
