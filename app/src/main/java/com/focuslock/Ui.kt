package com.focuslock

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun Context.label(text: CharSequence, sp: Float = 15f, bold: Boolean = false, color: Int = 0xFF222222.toInt()): TextView =
    TextView(this).apply {
        this.text = text; textSize = sp; setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

fun Context.button(text: CharSequence, onClick: () -> Unit): Button =
    Button(this).apply { this.text = text; isAllCaps = false; setOnClickListener { onClick() } }

fun LinearLayout.add(v: View, top: Int = 8): View {
    addView(v, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        .apply { topMargin = context.dp(top) })
    return v
}

fun Context.screen(): Pair<ScrollView, LinearLayout> {
    val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(24)) }
    val sv = ScrollView(this).apply { setBackgroundColor(Color.WHITE); addView(col) }
    return sv to col
}

object Apps {
    fun label(pm: PackageManager, pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { pkg }
    fun icon(pm: PackageManager, pkg: String): Drawable? = try { pm.getApplicationIcon(pkg) } catch (e: Exception) { null }
}
