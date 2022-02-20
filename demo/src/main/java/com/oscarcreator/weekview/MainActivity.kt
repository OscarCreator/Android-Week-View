package com.oscarcreator.weekview

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import java.util.*

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val weekView = findViewById<WeekView>(R.id.week_view)
        weekView.addEvents(listOf(
            WeekView.WeekViewEvent(Calendar.getInstance(), 50f, "Title of event 1"),
            WeekView.WeekViewEvent(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1) }, 120f, "Title of event 2"),
            WeekView.WeekViewEvent(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }, 75f, "Title of event 3"),
            WeekView.WeekViewEvent(Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -4) }, 30f, "Title of event 4")
        ))

    }
}