package dev.jag.snapwall

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.io.File

class SnapwallService : AccessibilityService() {

    /** One visible element, flattened out of the accessibility tree. */
    private data class V(val label: String, val id: String, val selected: Boolean, val rect: Rect)

    /**
     * What we've learned about one section heading (e.g. "Discover") from watching the Stories list.
     * [above]: labels seen above the heading (friends' stories). [below]: labels seen under it.
     * A player screen that shows a [below] label that never appeared [above] is Discover content.
     */
    private class Section {
        val above = BoundedSet(400)
        val below = BoundedSet(400)
        var lastHeaderTop: Int? = null
    }

    private val sections = HashMap<String, Section>()
    private var lastKick = 0L
    private var lastScan = 0L
    private var captureStarted = 0L
    private var lastCaptured = ""

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString() != SNAPCHAT) return

        val now = SystemClock.uptimeMillis()
        val isClick = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED
        // Content-changed events fire constantly during scrolling; throttle the tree walk.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && now - lastScan < 250) return
        lastScan = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != SNAPCHAT) return
        val nodes = collect(root)

        if (System.currentTimeMillis() < Rules.captureUntil(this)) {
            record(nodes, event)
            return
        }
        captureStarted = 0L

        if (!Rules.enabled(this)) return
        val clickRect = if (isClick) event.source?.let { Rect().also(it::getBoundsInScreen) } else null

        for (rule in Rules.parse(Rules.raw(this))) {
            val hit = when (rule.kind) {
                "section" -> sectionHit(rule.value, nodes, clickRect)
                "selected" -> nodes.any { it.selected && it.label.equals(rule.value, true) }
                "text" -> nodes.any { it.label.equals(rule.value, true) }
                "id" -> nodes.any { it.id.equals(rule.value, true) }
                else -> false
            }
            if (hit) {
                kick(rule.value)
                return
            }
        }
    }

    override fun onInterrupt() {}

    private fun sectionHit(heading: String, nodes: List<V>, click: Rect?): Boolean {
        val st = sections.getOrPut(heading.lowercase()) { Section() }
        val navCount = nodes.count { it.label.lowercase() in NAV }
        val inList = navCount >= 3 // bottom bar visible: browsing a tab, not watching a story
        val storiesSelected = nodes.any { it.selected && it.label.equals("Stories", true) }
        val header = nodes.firstOrNull { it.label.equals(heading, true) }

        if (inList) {
            if (header != null) {
                for (n in nodes) {
                    if (!usable(n.label, heading)) continue
                    when {
                        n.rect.top >= header.rect.bottom -> st.below.add(n.label)
                        n.rect.bottom <= header.rect.top -> st.above.add(n.label)
                    }
                }
                st.lastHeaderTop = header.rect.top
                // A tap on something under the heading opens Discover content.
                if (click != null && click.top >= header.rect.bottom) return true
                return false
            }
            // Heading just scrolled off the top of the Stories list: everything on screen is Discover.
            val top = st.lastHeaderTop
            val scrolledPast = storiesSelected && top != null &&
                top < resources.displayMetrics.heightPixels / 2
            if (scrolledPast) {
                nodes.filter { usable(it.label, heading) && !st.above.contains(it.label) }
                    .forEach { st.below.add(it.label) }
                if (click != null) return true
            } else if (!storiesSelected) {
                st.lastHeaderTop = null
            }
            return false
        }

        // Full-screen player. Block if it shows a label we only ever saw under the heading.
        return nodes.any { usable(it.label, heading) && st.below.contains(it.label) && !st.above.contains(it.label) }
    }

    private fun usable(label: String, heading: String): Boolean {
        if (label.length < 3 || label.equals(heading, true)) return false
        val l = label.lowercase()
        if (l in NAV) return false
        if (TIMESTAMP.matches(l)) return false
        return true
    }

    /** First hit backs out of the story into the list; a repeat within 3s goes home. */
    private fun kick(what: String) {
        val now = SystemClock.uptimeMillis()
        if (now - lastKick < 700) return
        val repeat = now - lastKick < 3000
        lastKick = now
        performGlobalAction(if (repeat) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
        toast("Snapwall: $what is blocked")
    }

    private fun collect(root: AccessibilityNodeInfo): List<V> {
        val out = ArrayList<V>()
        fun rec(n: AccessibilityNodeInfo, depth: Int, ancestorsSelected: List<Boolean>) {
            if (depth > 60) return
            val sel = n.isSelected || ancestorsSelected.takeLast(3).any { it }
            if (n.isVisibleToUser) {
                val label = (n.text ?: n.contentDescription ?: "").toString().trim()
                val id = n.viewIdResourceName ?: ""
                if (label.isNotEmpty() || id.isNotEmpty()) {
                    out.add(V(label, id, sel && label.isNotEmpty(), Rect().also(n::getBoundsInScreen)))
                }
            }
            val next = ancestorsSelected + n.isSelected
            for (i in 0 until n.childCount) n.getChild(i)?.let { rec(it, depth + 1, next) }
        }
        rec(root, 0, emptyList())
        return out
    }

    /** Appends each distinct Snapchat screen to the capture file while recording is on. */
    private fun record(nodes: List<V>, event: AccessibilityEvent) {
        val f = File(filesDir, DUMP_FILE)
        if (captureStarted == 0L) {
            captureStarted = SystemClock.uptimeMillis()
            lastCaptured = ""
            f.writeText("")
            toast("Snapwall is recording. Blocking is off until it stops.")
        }
        val body = buildString {
            for (n in nodes) {
                if (n.label.isNotEmpty()) append('"').append(n.label.take(60)).append("\" ")
                if (n.id.isNotEmpty()) append("id=").append(n.id.substringAfter(":id/")).append(' ')
                if (n.selected) append("[SELECTED] ")
                append("y=").append(n.rect.top).append('\n')
            }
        }
        if (body == lastCaptured || f.length() > 300_000) return
        lastCaptured = body
        val secs = (SystemClock.uptimeMillis() - captureStarted) / 1000
        val type = AccessibilityEvent.eventTypeToString(event.eventType).removePrefix("TYPE_")
        f.appendText("=== +${secs}s $type ===\n$body\n")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private class BoundedSet(private val max: Int) {
        private val items = LinkedHashSet<String>()
        fun add(s: String) {
            items.remove(s)
            items.add(s)
            if (items.size > max) items.remove(items.first())
        }
        fun contains(s: String) = s in items
    }

    companion object {
        const val SNAPCHAT = "com.snapchat.android"
        const val DUMP_FILE = "capture.txt"
        private val NAV = setOf("map", "chat", "camera", "stories", "spotlight")
        private val TIMESTAMP = Regex("""^(\d+\s*[smhdw]|\d+\s*(sec|min|hr|hour|day|week)s?( ago)?|just now|yesterday)$""")
    }
}
