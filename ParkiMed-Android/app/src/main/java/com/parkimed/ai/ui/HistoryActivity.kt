package com.parkimed.ai.ui

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.parkimed.ai.R
import com.parkimed.ai.data.AssessmentRecord
import com.parkimed.ai.data.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Card-based history list with state colour-coding and severity bars. */
class HistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        val list = findViewById<ListView>(R.id.historyList)
        val empty = findViewById<TextView>(R.id.txtEmpty)
        val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

        CoroutineScope(Dispatchers.Main).launch {
            val records = withContext(Dispatchers.IO) { store.history() }
            if (records.isEmpty()) {
                empty.visibility = View.VISIBLE
            } else {
                empty.visibility = View.GONE
                list.adapter = object : BaseAdapter() {
                    override fun getCount() = records.size
                    override fun getItem(position: Int) = records[position]
                    override fun getItemId(position: Int) = records[position].id
                    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                        val v = convertView ?: layoutInflater.inflate(R.layout.item_history, parent, false)
                        val r = records[position]
                        val isOn = r.onOff == "ON"
                        val onC = androidx.core.content.ContextCompat.getColor(this@HistoryActivity, R.color.state_on)
                        val offC = androidx.core.content.ContextCompat.getColor(this@HistoryActivity, R.color.state_off)

                        v.findViewById<View>(R.id.dotState).background.setTint(if (isOn) onC else offC)
                        v.findViewById<TextView>(R.id.txtWhen).text = fmt.format(Date(r.timestamp))
                        v.findViewById<TextView>(R.id.txtSource).text = r.sourcesUsed
                        v.findViewById<TextView>(R.id.txtState).text = r.onOff
                        v.findViewById<TextView>(R.id.txtState).setTextColor(if (isOn) onC else offC)
                        v.findViewById<TextView>(R.id.txtSev).text = "sev %.1f".format(r.severity)
                        v.findViewById<ProgressBar>(R.id.sevBar).progress = (r.severity * 10).toInt()
                        return v
                    }
                }
            }
        }
    }

    private val store: LocalStore by lazy { LocalStore(this) }
}
