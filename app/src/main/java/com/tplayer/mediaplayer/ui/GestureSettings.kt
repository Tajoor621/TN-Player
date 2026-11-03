package com.tplayer.mediaplayer.ui

import android.content.Context
import android.widget.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.tplayer.mediaplayer.data.Settings
import com.tplayer.mediaplayer.R

/** Shared native Views settings panel; no new Activity/toolkit and no second player. */
object GestureSettings {
    fun show(context: Context, changed: () -> Unit = {}) {
        val settings = Settings(context)
        val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        box.addView(MaterialSwitch(context).apply {
            text = context.getString(R.string.gesture_enable); textOn = ""; textOff = ""; showText = false; isChecked = settings.gestures
            setOnCheckedChangeListener { _, value -> settings.gestures = value; changed() }
        })
        box.addView(TextView(context).apply { text = context.getString(R.string.gesture_settings_guide) })
        val refreshers = mutableListOf<() -> Unit>()
        for (slot in GestureSlot.values()) {
            val button = Button(context).apply { minHeight = (48 * resources.displayMetrics.density).toInt(); isAllCaps = false }
            fun refresh() { button.text = context.getString(R.string.gesture_assignment, slot.label, settings.gestureAction(slot).label) }
            refresh(); refreshers.add(::refresh)
            button.setOnClickListener {
                val values = GestureAction.values()
                MaterialAlertDialogBuilder(context).setTitle(slot.label)
                    .setSingleChoiceItems(values.map { it.label }.toTypedArray(), values.indexOf(settings.gestureAction(slot))) { dialog, index ->
                        settings.setGestureAction(slot, values[index]); refresh(); changed(); dialog.dismiss()
                    }.setNegativeButton("Cancel", null).show()
            }
            box.addView(button)
        }
        box.addView(Button(context).apply { text = context.getString(R.string.gesture_reset); setOnClickListener {
            GestureSlot.values().forEach { settings.setGestureAction(it, GesturePolicy.default(it)) }; changed()
            refreshers.forEach { it() }
        } })
        MaterialAlertDialogBuilder(context).setTitle("Gesture controls").setView(ScrollView(context).apply { addView(box) })
            .setPositiveButton("Done", null).show()
    }
}
