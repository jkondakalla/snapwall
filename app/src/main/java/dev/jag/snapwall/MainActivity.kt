package dev.jag.snapwall

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var rulesBox: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }

        col.addView(TextView(this).apply { text = "Snapwall"; textSize = 26f })
        status = TextView(this).apply { textSize = 15f; setPadding(0, pad / 2, 0, pad) }
        col.addView(status)

        col.addView(button("Open accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        toggle = button("") { onToggle() }
        col.addView(toggle)

        col.addView(TextView(this).apply {
            text = "Rules (one per line: text:, selected:, or id:)"
            setPadding(0, pad, 0, 0)
        })
        rulesBox = EditText(this).apply {
            setText(Rules.raw(this@MainActivity))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 5
            textSize = 14f
        }
        col.addView(rulesBox, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        col.addView(button("Save rules") {
            Rules.save(this, rulesBox.text.toString())
            toast("Saved")
        })
        col.addView(button("Reset rules to default") {
            rulesBox.setText(Rules.DEFAULT)
            Rules.save(this, Rules.DEFAULT)
        })

        col.addView(TextView(this).apply {
            text = "If something slips through or gets blocked by mistake: tap Record, then in Snapchat open the Stories tab, a friend's story, a Discover story, and Spotlight. Come back, tap View capture, and copy it. Blocking is off while recording."
            setPadding(0, pad, 0, 0)
        })
        col.addView(button("Record Snapchat for 60 seconds") {
            Rules.setCaptureUntil(this, System.currentTimeMillis() + 60_000)
            toast("Recording. Open Snapchat now.")
        })
        col.addView(button("View capture") { showDump() })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val serviceOn = isServiceEnabled()
        val blocking = Rules.enabled(this)
        status.text = when {
            !serviceOn -> "Not active. Turn on Snapwall in accessibility settings."
            System.currentTimeMillis() < Rules.captureUntil(this) -> "Recording. Blocking is off until it finishes."
            blocking -> "Active. Opening Discover or Spotlight content is blocked. Friends' stories work."
            else -> "Paused. The service is on but not blocking."
        }
        toggle.text = if (blocking) "Pause blocking" else "Resume blocking"
    }

    /** Pausing takes deliberate effort; resuming takes one tap. */
    private fun onToggle() {
        if (!Rules.enabled(this)) {
            Rules.setEnabled(this, true); refresh(); return
        }
        val phrase = "I am choosing to doomscroll"
        val input = EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Type to pause")
            .setMessage("Type exactly:\n\n$phrase")
            .setView(input)
            .setPositiveButton("Pause") { _, _ ->
                if (input.text.toString().trim() == phrase) {
                    Rules.setEnabled(this, false); refresh()
                } else toast("Didn't match. Still blocking.")
            }
            .setNegativeButton("Keep blocking", null)
            .show()
    }

    private fun showDump() {
        val f = File(filesDir, SnapwallService.DUMP_FILE)
        val text = if (f.exists()) f.readText() else "No capture yet."
        AlertDialog.Builder(this)
            .setTitle("Captured screen")
            .setMessage(text.take(20000))
            .setPositiveButton("Copy") { _, _ ->
                val cm = getSystemService(ClipboardManager::class.java)
                cm.setPrimaryClip(ClipData.newPlainText("snapwall", text))
                toast("Copied")
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun isServiceEnabled(): Boolean {
        val list = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return list.split(':').any { it.startsWith("$packageName/") }
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
