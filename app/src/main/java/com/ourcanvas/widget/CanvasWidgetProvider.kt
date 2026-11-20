package com.ourcanvas.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import android.widget.RemoteViews
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

class CanvasWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        scheduleRefresh(context)
        refreshAll(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            refreshAll(context, manager, manager.getAppWidgetIds(
                android.content.ComponentName(context, CanvasWidgetProvider::class.java)
            ))
        }
    }

    private fun scheduleRefresh(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CanvasWidgetProvider::class.java).setAction(ACTION_REFRESH)
        val pending = PendingIntent.getBroadcast(
            context, 7102, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + REFRESH_INTERVAL,
            REFRESH_INTERVAL,
            pending
        )
    }

    private fun refreshAll(
        context: Context,
        manager: AppWidgetManager,
        widgetIds: IntArray
    ) {
        if (widgetIds.isEmpty()) return

        val prefs = context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val roomId = prefs.getString(MainActivity.KEY_ROOM_ID, null)

        widgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_canvas)
            val openIntent = if (!roomId.isNullOrBlank()) {
                Intent(Intent.ACTION_VIEW, Uri.parse("$WEBSITE_URL/?room=$roomId"))
            } else {
                Intent(context, MainActivity::class.java)
            }
            val openPending = PendingIntent.getActivity(
                context, id, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetCanvasImage, openPending)

            val refreshIntent = Intent(context, CanvasWidgetProvider::class.java)
                .setAction(ACTION_REFRESH)
            val refreshPending = PendingIntent.getBroadcast(
                context, id + 8000, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRefresh, refreshPending)

            if (roomId.isNullOrBlank()) {
                views.setImageViewBitmap(
                    R.id.widgetCanvasImage,
                    renderMessage("Connect your canvas in the app")
                )
                manager.updateAppWidget(id, views)
            } else {
                views.setImageViewBitmap(
                    R.id.widgetCanvasImage,
                    renderMessage("Loading your canvas…")
                )
                manager.updateAppWidget(id, views)
            }
        }

        if (!roomId.isNullOrBlank()) {
            Thread {
                val bitmap = try {
                    fetchAndRender(roomId)
                } catch (_: Exception) {
                    renderMessage("Couldn't refresh. Tap ↻ to try again.")
                }
                widgetIds.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_canvas)
                    val openPending = PendingIntent.getActivity(
                        context, id,
                        Intent(Intent.ACTION_VIEW, Uri.parse("$WEBSITE_URL/?room=$roomId")),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val refreshPending = PendingIntent.getBroadcast(
                        context, id + 8000,
                        Intent(context, CanvasWidgetProvider::class.java).setAction(ACTION_REFRESH),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widgetCanvasImage, openPending)
                    views.setOnClickPendingIntent(R.id.widgetRefresh, refreshPending)
                    views.setImageViewBitmap(R.id.widgetCanvasImage, bitmap)
                    manager.updateAppWidget(id, views)
                }
            }.start()
        }
    }

    private fun fetchAndRender(roomId: String): Bitmap {
        val endpoint = "$DATABASE_URL/rooms/$roomId/strokes.json"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("Firebase returned HTTP $status")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            renderStrokes(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun renderStrokes(json: String): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(23, 19, 34))

        if (json == "null" || json.isBlank()) {
            drawCenteredText(canvas, "A little space for the two of us.")
            return bitmap
        }

        val root = JSONObject(json)
        val iterator = root.keys()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }

        while (iterator.hasNext()) {
            val key = iterator.next()
            val stroke = root.optJSONObject(key) ?: continue
            val points = stroke.optJSONArray("points") ?: continue
            if (points.length() == 0) continue

            paint.color = parseColor(stroke.optString("color", "#FFFFFF"))
            val rawWidth = stroke.optDouble("width", 3.0).toFloat()
            paint.strokeWidth = (rawWidth * BITMAP_SIZE / 400f).coerceIn(1.5f, 28f)

            val first = points.optJSONObject(0) ?: continue
            val path = Path()
            path.moveTo(pointX(first), pointY(first))
            for (i in 1 until points.length()) {
                val point = points.optJSONObject(i) ?: continue
                path.lineTo(pointX(point), pointY(point))
            }
            if (points.length() == 1) {
                val x = pointX(first)
                val y = pointY(first)
                canvas.drawCircle(x, y, max(1.5f, paint.strokeWidth / 2f), paint.apply {
                    style = Paint.Style.FILL
                })
                paint.style = Paint.Style.STROKE
            } else {
                canvas.drawPath(path, paint)
            }
        }
        return bitmap
    }

    private fun pointX(point: JSONObject): Float =
        (point.optDouble("x", 0.0) * BITMAP_SIZE).toFloat().coerceIn(0f, BITMAP_SIZE.toFloat())

    private fun pointY(point: JSONObject): Float =
        (point.optDouble("y", 0.0) * BITMAP_SIZE).toFloat().coerceIn(0f, BITMAP_SIZE.toFloat())

    private fun parseColor(value: String): Int {
        return try {
            Color.parseColor(value)
        } catch (_: IllegalArgumentException) {
            Color.WHITE
        }
    }

    private fun renderMessage(message: String): Bitmap {
        val bitmap = Bitmap.createBitmap(BITMAP_SIZE, BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(23, 19, 34))
        drawCenteredText(canvas, message)
        return bitmap
    }

    private fun drawCenteredText(canvas: Canvas, message: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(217, 207, 255)
            textSize = 27f
            textAlign = Paint.Align.CENTER
        }
        val lines = if (message.length > 28) {
            listOf(message.take(28), message.drop(28))
        } else {
            listOf(message)
        }
        val centerY = BITMAP_SIZE / 2f - (lines.size - 1) * 18f
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, BITMAP_SIZE / 2f, centerY + index * 38f, paint)
        }
    }

    companion object {
        private const val ACTION_REFRESH = "com.ourcanvas.widget.ACTION_REFRESH"
        private const val DATABASE_URL = "https://ourcanvas-529d2-default-rtdb.firebaseio.com"
        private const val WEBSITE_URL = "https://ourcanvas-529d2.web.app"
        private const val BITMAP_SIZE = 600
        private const val REFRESH_INTERVAL = 15 * 60 * 1000L
    }
}
