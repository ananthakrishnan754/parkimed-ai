package com.parkimed.ai.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.parkimed.ai.R
import com.parkimed.ai.data.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** History of local assessments (offline). */
class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        val store = LocalStore(this)
        val list = findViewById<ListView>(R.id.historyList)
        val fmt = SimpleDateFormat("dd MMM HH:mm", Locale.US)

        CoroutineScope(Dispatchers.Main).launch {
            val records = withContext(Dispatchers.IO) { store.history() }
            list.adapter = ArrayAdapter(
                this@HistoryActivity,
                android.R.layout.simple_list_item_1,
                records.map {
                    "${fmt.format(Date(it.timestamp))}  •  ${it.onOff}  •  " +
                            "sev ${"%.1f".format(it.severity)}  •  ${it.sourcesUsed}"
                }
            )
        }
    }
}