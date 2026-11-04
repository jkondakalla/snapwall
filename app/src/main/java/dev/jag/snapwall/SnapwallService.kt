package dev.jag.snapwall

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.io.File

class SnapwallService : AccessibilityService() {

    private var lastKick = 0L
    private var lastScan = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != SNAPCHAT) return

        // Content-changed events fire constantly during scrolling; throttle the tree walk.
        val now = SystemClock.uptimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastScan < 250) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != SNAPCHAT) return

        if (Rules.dumpNext(this)) {
            Rules.setDumpNext(this, false)
            dump(root)
            toast("Snapwall saved this screen. Open Snapwall to view it.")
            return
        }

        if (!Rules.enabled(this)) return
        val rules = Rules.parse(Rules.raw(this))
        val hit = rules.firstOrNull { matches(root, it) } ?: return

        if (now - lastKick < 1000) return
        lastKick = now
        performGlobalAction(GLOBAL_ACTION_HOME)
        toast("Snapwall: ${hit.value} is blocked")
    }

    override fun onInterrupt() {}

    private fun matches(root: AccessibilityNodeInfo, rule: Rule): Boolean {
        var found = false
        walk(root) { node ->
            if (found || !node.isVisibleToUser) return@walk
            found = when (rule.kind) {
                "id" -> node.viewIdResourceName.equals(rule.value, ignoreCase = true)
                "text" -> label(node).equals(rule.value, ignoreCase = true)
                "selected" -> label(node).equals(rule.value, ignoreCase = true) && selectedSelfOrAncestor(node)
                else -> false
            }
        }
        return found
    }

    private fun label(n: AccessibilityNodeInfo): String =
        (n.text ?: n.contentDescription ?: "").toString().trim()

    private fun selectedSelfOrAncestor(n: AccessibilityNodeInfo): Boolean {
        var cur: AccessibilityNodeInfo? = n
        var depth = 0
        while (cur != null && depth < 4) {
            if (cur.isSelected) return true
            cur = cur.parent
            depth++
        }
        return false
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int = 0, visit: (AccessibilityNodeInfo) -> Unit) {
        if (depth > 60) return
        visit(node)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { walk(it, depth + 1, visit) }
        }
    }

    /** Writes the visible element tree so rules can be tuned when Snapchat changes its layout. */
    private fun dump(root: AccessibilityNodeInfo) {
        val sb = StringBuilder()
        fun rec(n: AccessibilityNodeInfo, d: Int) {
            if (d > 60) return
            if (n.isVisibleToUser) {
                val l = label(n)
                val id = n.viewIdResourceName ?: ""
                if (l.isNotEmpty() || id.isNotEmpty() || n.isSelected) {
                    sb.append("  ".repeat(minOf(d, 20)))
                    if (l.isNotEmpty()) sb.append("\"").append(l.take(60)).append("\" ")
                    if (id.isNotEmpty()) sb.append("id=").append(id).append(' ')
                    if (n.isSelected) sb.append("[SELECTED]")
                    sb.append('\n')
                }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { rec(it, d + 1) }
        }
        rec(root, 0)
        File(filesDir, DUMP_FILE).writeText(sb.toString())
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        const val SNAPCHAT = "com.snapchat.android"
        const val DUMP_FILE = "last_screen.txt"
    }
}
