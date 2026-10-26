package com.focuslock

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.widget.CheckBox
import android.widget.EditText
import android.widget.Toast

class SettingsActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        title = "Settings"
        val store = Store(this)
        val (sv, col) = screen()
        col.add(label("Settings", 22f, true))
        col.add(label("Continuous usage limit (minutes, 1-120)", 14f), 16)
        val limit = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(store.limitMin.toString()) }; col.add(limit, 2)
        col.add(label("Restriction duration (minutes, 1-1440)", 14f), 12)
        val block = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText(store.blockMin.toString()) }; col.add(block, 2)
        val warn = CheckBox(this).apply { text = "Notify me if the accessibility service stops"; isChecked = store.warnOnDisable }; col.add(warn, 12)
        col.add(label("Changes apply to NEW sessions/restrictions only. A restriction already running keeps its original expiry.", 12f, false, 0xFF777777.toInt()), 8)
        col.add(button("Save") {
            val l = limit.text.toString().toIntOrNull(); val d = block.text.toString().toIntOrNull()
            if (l == null || l !in 1..120) { limit.error = "Enter 1-120"; return@button }
            if (d == null || d !in 1..1440) { block.error = "Enter 1-1440"; return@button }
            store.limitMin = l; store.blockMin = d; store.warnOnDisable = warn.isChecked
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show(); finish()
        }, 16)
        setContentView(sv)
    }
}
