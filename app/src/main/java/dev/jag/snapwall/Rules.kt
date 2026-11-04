package dev.jag.snapwall

import android.content.Context

/**
 * One rule per line:
 *   section:Discover     opening anything listed under the "Discover" heading
 *   selected:Spotlight   the nav tab labeled "Spotlight" is the selected one
 *   text:Some label      a visible element whose text or label is exactly this
 *   id:<view id>         a visible element with this resource id
 * Lines starting with # are ignored. Matching ignores case.
 */
data class Rule(val kind: String, val value: String)

object Rules {
    private const val PREFS = "snapwall"
    // v2: the v1 default (text:Discover) blocked the whole Stories tab, so saved v1 rules are dropped.
    private const val KEY_RULES = "rules_v2"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CAPTURE_UNTIL = "capture_until"

    val KINDS = setOf("section", "selected", "text", "id")

    val DEFAULT = """
        # Opening a story or short from the Discover section
        section:Discover
        # Spotlight tab (it starts playing shorts as soon as it opens)
        selected:Spotlight
    """.trimIndent()

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun raw(c: Context): String = prefs(c).getString(KEY_RULES, DEFAULT) ?: DEFAULT
    fun save(c: Context, text: String) = prefs(c).edit().putString(KEY_RULES, text).apply()

    fun enabled(c: Context) = prefs(c).getBoolean(KEY_ENABLED, true)
    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean(KEY_ENABLED, on).apply()

    /** Wall-clock ms until which the service records screens instead of blocking. */
    fun captureUntil(c: Context) = prefs(c).getLong(KEY_CAPTURE_UNTIL, 0L)
    fun setCaptureUntil(c: Context, t: Long) = prefs(c).edit().putLong(KEY_CAPTURE_UNTIL, t).apply()

    fun parse(text: String): List<Rule> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains(":") }
        .map {
            val i = it.indexOf(':')
            Rule(it.substring(0, i).trim().lowercase(), it.substring(i + 1).trim())
        }
        .filter { it.value.isNotEmpty() && it.kind in KINDS }
}
