package com.parag.safetydesk

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * On-device storage. Settings live in SharedPreferences; registers (findings, incidents,
 * saved documents) live in one JSON file in the app's private storage.
 */
class Store(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val file = File(ctx.filesDir, "records.json")
    private val records: MutableList<JSONObject> = load()

    // ---------- settings ----------
    fun pref(key: String, def: String = ""): String = prefs.getString(key, def) ?: def
    fun setPrefs(values: Map<String, String>) { prefs.edit().apply { values.forEach { (k, v) -> putString(k, v) } }.apply() }

    val apiKey get() = pref("apiKey")
    val geminiKey get() = pref("geminiKey")
    val model get() = pref("model", MODELS.first())

    // ---------- records ----------
    private fun load(): MutableList<JSONObject> = try {
        val a = JSONArray(file.readText()); MutableList(a.length()) { a.getJSONObject(it) }
    } catch (e: Exception) { mutableListOf() }

    private fun persist() {
        val a = JSONArray(); records.forEach { a.put(it) }
        val tmp = File(ctx.filesDir, "records.json.tmp"); tmp.writeText(a.toString()); tmp.renameTo(file)
    }

    fun list(kind: String): List<JSONObject> = records.filter { it.optString("kind") == kind }
        .sortedWith(compareByDescending<JSONObject> { it.optString("date") }.thenByDescending { it.optLong("created") })

    fun get(id: String): JSONObject? = records.firstOrNull { it.optString("id") == id }

    fun save(r: JSONObject): JSONObject {
        if (r.optString("id").isEmpty()) r.put("id", nextId(r.optString("kind")))
        if (!r.has("created")) r.put("created", System.currentTimeMillis())
        r.put("updated", System.currentTimeMillis())
        records.removeAll { it.optString("id") == r.optString("id") }
        records.add(r); persist(); return r
    }

    fun delete(id: String) { records.removeAll { it.optString("id") == id }; persist() }

    private fun nextId(kind: String): String {
        val prefix = when (kind) { "finding" -> "F"; "incident" -> "INC"; "doc" -> "DOC"; else -> "R" }
        val n = records.count { it.optString("kind") == kind } + 1
        var id: String; var k = n
        do { id = "%s-%04d".format(prefix, k++) } while (get(id) != null)
        return id
    }

    fun exportJson(): String { val a = JSONArray(); records.forEach { a.put(it) }; return a.toString(1) }

    // ---------- derived ----------
    fun openFindings() = list("finding").filter { !it.optString("status").startsWith("Closed") }

    /** Items that need management attention, by the escalation rules in the instructions file. */
    fun escalations(): List<Pair<String, JSONObject>> {
        val t = today(); val out = mutableListOf<Pair<String, JSONObject>>()
        for (f in openFindings()) {
            val band = riskBand(f.optInt("L", 3) * f.optInt("S", 3))
            val target = f.optString("target")
            when {
                band == "Critical" -> out += "Critical risk open: stop work and isolate" to f
                target.isNotEmpty() && target < t -> out += "Corrective action overdue (target ${fmt(target)})" to f
                band == "High" && daysBetween(f.optString("date"), t) >= 2 -> out += "High risk open ${daysBetween(f.optString("date"), t)} days" to f
            }
        }
        for (i in list("incident").filter { it.optString("status") != "Closed" }) {
            if (i.optString("type") in SERIOUS_INCIDENTS) out += "${i.optString("type")} – notify management" to i
        }
        return out
    }

    companion object {
        val MODELS = listOf("gemini-flash-latest", "gemini-flash-lite-latest", "gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-pro-latest", "claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")
        val CATS = listOf("Work at height", "Scaffolding & formwork", "Tower crane & hoist", "Lifting", "Excavation", "Electrical",
            "Hot work & fire", "PPE", "Housekeeping & storage", "Openings & edges", "Vehicles & plant", "Welfare & sanitation",
            "Dust, noise & environment", "Permit & documentation", "Other")
        val STATUSES = listOf("Open", "Action taken – awaiting verification", "Closed – verified")
        val INCIDENT_TYPES = listOf("Near miss", "First aid case", "Medical treatment case", "Lost time injury", "Dangerous occurrence", "Fatal", "Property damage", "Fire", "Environmental")
        val SERIOUS_INCIDENTS = setOf("Lost time injury", "Dangerous occurrence", "Fatal", "Fire")

        private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        fun today(): String = iso.format(Date())
        fun addDays(d: String, n: Int): String = try {
            val c = Calendar.getInstance(); c.time = iso.parse(d)!!; c.add(Calendar.DAY_OF_YEAR, n); iso.format(c.time)
        } catch (e: Exception) { d }
        fun daysBetween(a: String, b: String): Int = try { ((iso.parse(b)!!.time - iso.parse(a)!!.time) / 86_400_000L).toInt() } catch (e: Exception) { 0 }
        /** DD-MM-YYYY, as the instructions require. */
        fun fmt(d: String): String = if (d.length >= 10) d.substring(8, 10) + "-" + d.substring(5, 7) + "-" + d.substring(0, 4) else d
        fun riskBand(r: Int) = when { r >= 16 -> "Critical"; r >= 10 -> "High"; r >= 5 -> "Medium"; else -> "Low" }
    }
}
