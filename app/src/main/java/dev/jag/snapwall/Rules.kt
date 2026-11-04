package dev.jag.snapwall

import android.content.Context

/**
 * One rule per line:
 *   text:Discover        a visible element whose text or label is exactly "Discover"
 *   selected:Spotlight   the nav tab labeled "Spotlight" is the selected one
 *   id:<view id>         a visible element with this resource id
 * Lines starting with # are ignored. Matching ignores case.
 */
data class Rule(val kind: String, val value: String)

object Rules {
    private const val PREFS = "snapwall"
    private const val KEY_RULES = "rules"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DUMP_NEXT = "dump_next"

    val DEFAULT = """
        # Discover section heading (lives under the Stories tab)
        text:Discover
        # Spotlight tab in the bottom bar
        selected:Spotlight
    """.trimIndent()

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun raw(c: Context): String = prefs(c).getString(KEY_RULES, DEFAULT) ?: DEFAULT
    fun save(c: Context, text: String) = prefs(c).edit().putString(KEY_RULES, text).apply()

    fun enabled(c: Context) = prefs(c).getBoolean(KEY_ENABLED, true)
    fun setEnabled(c: Context, on: Boolean) = prefs(c).edit().putBoolean(KEY_ENABLED, on).apply()

    fun dumpNext(c: Context) = prefs(c).getBoolean(KEY_DUMP_NEXT, false)
    fun setDumpNext(c: Context, on: Boolean) = prefs(c).edit().putBoolean(KEY_DUMP_NEXT, on).apply()

    fun parse(text: String): List<Rule> = text.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains(":") }
        .map {
            val i = it.indexOf(':')
            Rule(it.substring(0, i).trim().lowercase(), it.substring(i + 1).trim())
        }
        .filter { it.value.isNotEmpty() && it.kind in setOf("text", "selected", "id") }
}
