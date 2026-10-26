package com.focuslock

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class AppSelectionActivity : Activity() {
    private data class Entry(val pkg: String, val label: String)
    private lateinit var store: Store
    private lateinit var rm: RestrictionManager
    private val icons = HashMap<String, Drawable?>()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Choose apps to limit"
        store = Store(this); rm = RestrictionManager(this, store)
        val lv = ListView(this)
        setContentView(lv)
        Thread {
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val list = packageManager.queryIntentActivities(i, 0)
                .map { Entry(it.activityInfo.packageName, it.loadLabel(packageManager).toString()) }
                .filter { it.pkg != packageName }.distinctBy { it.pkg }.sortedBy { it.label.lowercase() }
            val loaded = HashMap<String, Drawable?>()
            for (e in list) loaded[e.pkg] = Apps.icon(packageManager, e.pkg)
            runOnUiThread {
                icons.putAll(loaded)
                val ad = Adapter(list)
                lv.adapter = ad
                lv.setOnItemClickListener { _, _, pos, _ ->
                    val e = list[pos]
                    val sel = store.isSelected(e.pkg)
                    if (sel && rm.isRestricted(e.pkg))
                        Toast.makeText(this, "${e.label} is blocked (${formatHms(rm.remainingMs(e.pkg))} left). You can deselect it after the restriction ends.", Toast.LENGTH_LONG).show()
                    else store.setSelected(e.pkg, !sel)
                    ad.notifyDataSetChanged()
                }
            }
        }.start()
    }

    private inner class Adapter(val items: List<Entry>) : BaseAdapter() {
        override fun getCount() = items.size
        override fun getItem(p: Int) = items[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, c: View?, parent: ViewGroup?): View {
            val e = items[p]
            val ctx = this@AppSelectionActivity
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
            row.addView(ImageView(ctx).apply { setImageDrawable(icons[e.pkg]) }, LinearLayout.LayoutParams(dp(40), dp(40)))
            row.addView(TextView(ctx).apply { text = e.label; textSize = 16f; setPadding(dp(12), 0, 0, 0) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(CheckBox(ctx).apply { isChecked = store.isSelected(e.pkg); isClickable = false; isFocusable = false })
            return row
        }
    }
}
