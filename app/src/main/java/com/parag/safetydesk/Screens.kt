package com.parag.safetydesk

import android.app.AlertDialog
import android.graphics.BitmapFactory
import android.text.Html
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

// ======================= Home =======================
class HomeScreen(a: MainActivity) : Screen(a) {
    override val title get() = a.store.pref("project").ifBlank { "Safety Desk" }
    override val icon = "⛑️"
    private var tip = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR) % Icons.TIPS.size

    override fun build(): View = with(a) {
        val s = store
        val open = s.openFindings()
        val hi = open.count { Store.riskBand(it.optInt("L", 3) * it.optInt("S", 3)) in setOf("High", "Critical") }
        val overdue = open.count { it.optString("target").let { t -> t.isNotEmpty() && t < Store.today() } }
        val esc = s.escalations()
        val trkOverdue = Trackers.overdue(s)
        val root = col(16)
        var n = 0

        // Hero: greeting, site status
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greet = when { hour < 12 -> "Good morning"; hour < 17 -> "Good afternoon"; else -> "Good evening" }
        val name = s.pref("manager").substringBefore(' ').ifBlank { "" }
        val alerts = esc.size + trkOverdue
        val status = text(if (alerts == 0) "  ✅  Site status: no open alerts  " else "  ⚠️  $alerts item${if (alerts == 1) "" else "s"} need attention  ", 12.5f,
            if (alerts == 0) 0xFF0B5D2A.toInt() else 0xFF7A1010.toInt(), true).apply {
            background = rounded(if (alerts == 0) 0xFFC8F2D4.toInt() else 0xFFFFD6D6.toInt(), dp(14).toFloat()); setPadding(dp(4), dp(5), dp(4), dp(5))
            if (alerts > 0) setOnClickListener { show(if (esc.isNotEmpty()) EscalationScreen(this@with) else TrackersScreen(this@with)) }
        }
        val hero = col(18,
            row(col(0, text(if (name.isEmpty()) "$greet 👷" else "$greet, $name 👷", 20f, 0xFFFFFFFF.toInt(), true),
                text(listOf(s.pref("project"), s.pref("location")).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Your construction safety assistant" }, 13f, 0xFFC9D6E8.toInt()),
                text("📅 " + Store.fmt(Store.today()) + (s.pref("manhours").takeIf { it.isNotBlank() }?.let { "   ·   🛡️ $it safe man-hours" } ?: ""), 12.5f, 0xFFC9D6E8.toInt()).apply { setPadding(0, dp(4), 0, 0) },
            ).margins(this, bottom = 0, weight = 1f), text("🦺", 40f)),
            status.margins(this, top = 12, bottom = 0, width = ViewGroup.LayoutParams.WRAP_CONTENT),
        ).apply { background = gradient(C.NAVY, C.NAVY2, dp(20).toFloat()); elevation = dp(4).toFloat() }
        root.addView(hero.margins(this, bottom = 12).enter(n++))

        // KPIs
        root.addView(row(
            kpi("📋", "Open findings", open.size, C.BLUE, false) { show(RegisterScreen(this)) }.margins(this, end = 8, weight = 1f),
            kpi("🔥", "High / critical", hi, C.HIGH, hi > 0) { show(RegisterScreen(this)) }.margins(this, end = 8, weight = 1f),
            kpi("⏰", "Overdue", overdue, C.CRIT, overdue > 0) { show(RegisterScreen(this)) }.margins(this, weight = 1f),
        ).margins(this, bottom = 12).enter(n++))

        if (esc.isNotEmpty()) {
            val c = card(row(sign(Sign.WARNING, "!", 34), text("  Needs attention (${esc.size})", 16f, C.CRIT, true)))
            esc.take(3).forEach { (why, r) ->
                c.addView(text("• $why", 14f, C.INK, true).apply { setPadding(0, dp(8), 0, 0) })
                c.addView(muted("${r.optString("id")} · ${r.optString("condition").ifBlank { r.optString("description") }.take(90)}"))
            }
            c.addView(button("📣  Review and escalate", primary = true) { show(EscalationScreen(this)) }.margins(this, top = 12, bottom = 0))
            c.background = rounded(C.PANEL, dp(16).toFloat(), dp(2), C.CRIT)
            root.addView(c.margins(this, bottom = 12).enter(n++))
        }
        if (trkOverdue > 0) root.addView(iconCard("📊", C.CRIT, "Trackers: $trkOverdue overdue", "Inspections, certificates or actions past their due date")
            .apply { setOnClickListener { show(TrackersScreen(this@with)) } }.margins(this, bottom = 12).enter(n++))

        // Safety message of the day: tap for the next one
        root.addView(tipCard().margins(this, bottom = 4).enter(n++))

        fun grid(items: List<Tile>) {
            items.chunked(2).forEach { pair ->
                val r = row(); pair.forEachIndexed { i, t ->
                    r.addView(tile(t.icon, t.color, t.title, t.sub, t.go).margins(this, end = if (i == 0) 10 else 0, bottom = 0, weight = 1f))
                }
                if (pair.size == 1) r.addView(View(this).margins(this, weight = 1f))
                root.addView(r.margins(this, bottom = 10).enter(n++))
            }
        }
        root.addView(section("🦺", "Daily site work").enter(n++))
        grid(listOf(
            Tile("💬", C.BLUE, "Ask the agent", "Any safety, legal or site question") { show(ChatScreen(this)) },
            Tile("📸", C.CRIT, "Photo check", "Find violations in a site photo") { show(PhotoScreen(this)) },
            Tile("📲", C.LOW, "WhatsApp import", "Group messages into findings") { show(WhatsAppScreen(this)) },
            Tile("🚑", C.HIGH, "Incidents", "Log and investigate") { show(IncidentsScreen(this)) },
        ))
        root.addView(section("📊", "Registers & trackers").enter(n++))
        grid(listOf(
            Tile("📋", C.BLUE, "Findings register", "Inspection findings & actions") { show(RegisterScreen(this)) },
            Tile("🪢", C.ACCENT_DARK, "Trackers", "Fall protection, equipment, TPI, air, camp…") { show(TrackersScreen(this)) },
        ))
        root.addView(section("📄", "Documents & reports").enter(n++))
        grid(listOf(
            Tile("📝", C.PURPLE, "Prepare a document", "Checklists, HIRA, SOP, TBT, MoM…") { show(CatalogScreen(this)) },
            Tile("📈", C.TEAL, "EHS report", "Daily, weekly or monthly") { show(DocScreen(this, Docs.byId("report"))) },
            Tile("🏅", C.TEAL, "ISO 45001 & BOCW", "Reports, committee, labour camp") { show(CatalogScreen(this, "ISO 45001 & BOCW reports")) },
            Tile("⚖️", C.PURPLE, "Legal & compliance", "Researched answers with sources") { show(DocScreen(this, Docs.byId("legal"))) },
            Tile("🗂️", C.BLUE, "Saved documents", "${s.list("doc").size} kept on this phone") { show(SavedScreen(this)) },
            Tile("⚙️", C.MUTED, "Settings", "AI key, site details, contacts") { show(SettingsScreen(this)) },
        ))
        root.addView(text("Safety Desk · AI drafts must be checked before issue", 11.5f, C.LINE_DARK).apply { gravity = Gravity.CENTER; setPadding(0, dp(10), 0, dp(8)) })
        root
    }

    private class Tile(val icon: String, val color: Int, val title: String, val sub: String, val go: () -> Unit)

    private fun tipCard(): View = with(a) {
        val (sg, sym, msg) = Icons.TIPS[tip]
        val label = when (sg) { Sign.WARNING -> "WARNING"; Sign.MANDATORY -> "MANDATORY"; Sign.PROHIBITION -> "PROHIBITED"; Sign.SAFE -> "SAFE CONDITION"; Sign.FIRE -> "FIRE SAFETY" }
        val color = when (sg) { Sign.WARNING -> C.ACCENT_DARK; Sign.MANDATORY -> C.BLUE; Sign.PROHIBITION, Sign.FIRE -> C.CRIT; Sign.SAFE -> C.LOW }
        val r = row(sign(sg, sym, 56), col(0, text("SAFETY MESSAGE · $label", 11f, color, true).apply { letterSpacing = 0.05f },
            text(msg, 14.5f, C.INK).apply { setPadding(0, dp(3), 0, dp(2)) }, text("Tap for the next one  ›", 11.5f, C.MUTED)).margins(this, start = 14, bottom = 0, weight = 1f))
        r.setPadding(dp(14), dp(14), dp(14), dp(14))
        r.background = rounded(C.PANEL, dp(16).toFloat(), dp(1), tint(color, 90)); r.elevation = dp(1).toFloat()
        r.setOnClickListener { v -> v.animate().alpha(0f).translationX(-dp(24).toFloat()).setDuration(150).withEndAction { tip = (tip + 1) % Icons.TIPS.size; refresh() }.start() }
        r.pressable()
    }

    private fun kpi(icon: String, k: String, v: Int, color: Int, alert: Boolean, go: () -> Unit) = with(a) {
        val num = text("0", 26f, if (alert) C.CRIT else C.INK, true)
        if (v > 0) android.animation.ValueAnimator.ofInt(0, v).apply { duration = 700; addUpdateListener { num.text = it.animatedValue.toString() }; start() }
        col(14, badge(icon, color, 34), num.apply { setPadding(0, dp(6), 0, 0) }, text(k, 12f, C.MUTED)).apply {
            background = if (alert) rounded(C.PANEL, dp(16).toFloat(), dp(2), C.CRIT) else rounded(C.PANEL, dp(16).toFloat())
            elevation = dp(2).toFloat(); setOnClickListener { go() }; pressable()
        }
    }
}

// ======================= Settings =======================
class SettingsScreen(a: MainActivity, private val firstRun: Boolean = false) : Screen(a) {
    override val title = "Settings"
    override fun build(): View = with(a) {
        val s = store
        val f = Form(this)
            .choice("model", "AI model", Store.MODELS, s.model)
            .text("geminiKey", "Google Gemini API key (free)", s.geminiKey, hint = "AIza…")
            .text("apiKey", "Anthropic API key (only for Claude models)", s.apiKey, hint = "sk-ant-…")
            .text("project", "Project name", s.pref("project"))
            .text("location", "Location", s.pref("location"), hint = "e.g. Wakad, Pune, Maharashtra")
            .text("client", "Client", s.pref("client"))
            .text("contractor", "Main contractor", s.pref("contractor"))
            .text("towers", "Towers / areas", s.pref("towers"), hint = "e.g. T1–T6, podium, basement B1–B3")
            .text("workers", "Workers on site (approx.)", s.pref("workers"), number = true)
            .text("manhours", "Cumulative safe man-hours (verified)", s.pref("manhours"), number = true)
            .text("manager", "Safety manager name", s.pref("manager"))
            .text("escL2", "Escalation L2 e-mail (site in-charge / PM)", s.pref("escL2"))
            .text("escL3", "Escalation L3 e-mail (project head)", s.pref("escL3"))
            .text("escL4", "Escalation L4 e-mail (corporate EHS / management)", s.pref("escL4"))
        for (k in listOf("geminiKey", "apiKey")) f.field(k).inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        col(16,
            if (firstRun) card(text("Welcome", 17f, C.INK, true), muted("Safety Desk works with Google Gemini for free. Open aistudio.google.com, sign in with your Google account, tap Get API key → Create API key, then paste it below and save. The free tier has daily limits, and Google may use what you send to improve its products, so avoid confidential data. Then add your site details so documents are filled in correctly.")).margins(this, bottom = 8) else spacer(0),
            f.view,
            muted("Gemini Flash is free and recommended. Flash-Lite is faster with higher free limits. Gemini Pro and the Claude models give deeper analysis but need a paid key.").apply { setPadding(0, dp(8), 0, 0) },
            button("Save settings", primary = true) {
                s.setPrefs(f.values()); toast("Saved"); goBack()
            }.margins(this, top = 14),
            h2("Data on this phone"),
            muted("Findings, incidents and documents are stored only on this phone. Make a backup before changing phones."),
            button("Save backup to Downloads") { saveDownload("safety-desk-backup-${Store.today()}.json", "application/json", s.exportJson()) }.margins(this, top = 8),
        )
    }
}

// ======================= Chat =======================
class ChatScreen(a: MainActivity) : Screen(a) {
    override val title = "Ask the agent"
    override val scrolls = false
    private val turns = mutableListOf<Turn>()
    private var busy = false
    private var pendingImage: ByteArray? = null
    private var draft = ""
    private var useWeb = false

    override fun build(): View = with(a) {
        val list = col(12)
        val sv = ScrollView(this).apply { addView(list) }
        if (turns.isEmpty()) list.addView(empty("Ask anything: a legal requirement, a permit check, a warning letter, a toolbox talk, or an analysis of your open findings. Tick “Search the web” for current laws and official sources."))
        turns.forEachIndexed { i, t ->
            val tv = text(if (t.user) t.text else Html.fromHtml(Md.html(t.text), Html.FROM_HTML_MODE_COMPACT), 15f).apply {
                setTextIsSelectable(true); movementMethod = LinkMovementMethod.getInstance()
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = rounded(if (t.user) C.ACCENT_SOFT else C.PANEL, dp(12).toFloat(), if (t.user) 0 else dp(1))
            }
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = if (t.user) Gravity.END else Gravity.START; setMargins(if (t.user) dp(40) else 0, dp(4), if (t.user) 0 else dp(24), dp(4))
            }
            list.addView(tv, lp)
            if (!t.user && i == turns.lastIndex && !busy) list.addView(row(
                button("Copy") { copy(t.text) }.margins(this, end = 6),
                button("Share") { share("Safety Desk", t.text) }.margins(this, end = 6),
                button("Open as document") { show(DocViewScreen(this, "Answer", t.text)) },
            ))
        }
        if (busy && (turns.lastOrNull()?.user != false)) list.addView(muted("The agent is thinking…"))
        val box = input("Type your question…", draft, 2).apply { maxLines = 6 }
        box.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable?) { draft = e.toString() }
            override fun beforeTextChanged(s: CharSequence?, a1: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a1: Int, b: Int, c: Int) {}
        })
        val web = CheckBox(this).apply { text = "Search the web"; isChecked = useWeb; setTextColor(C.INK); setOnCheckedChangeListener { _, c -> useWeb = c } }
        val bottom = col(10,
            if (pendingImage != null) muted("📷 Photo attached").apply { setOnClickListener { pendingImage = null; refresh() } } else spacer(0),
            row(box.margins(this, end = 8, weight = 1f), button("Send", primary = true) { send() }),
            row(web, button("Attach photo") { pickImage() }.margins(this, start = 8, width = ViewGroup.LayoutParams.WRAP_CONTENT)),
        ).apply { setBackgroundColor(C.PANEL) }
        sv.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        sv.post { sv.fullScroll(View.FOCUS_DOWN) }
        col(0, sv, bottom)
    }

    override fun onImage(bytes: ByteArray) { pendingImage = bytes; a.refresh() }

    private fun send() {
        val q = draft.trim(); if (q.isEmpty() || busy) return
        turns += Turn(true, q, listOfNotNull(pendingImage)); draft = ""; pendingImage = null; busy = true; a.refresh()
        val history = turns.takeLast(16).toList()
        val start = turns.size
        a.bg({
            a.ai.ask(history, useWeb) { t ->
                a.ui { if (turns.size == start) turns += Turn(false, t) else turns[start] = Turn(false, t); throttle() }
            }
        }) { r ->
            busy = false
            r.onSuccess { if (turns.size == start) turns += Turn(false, it) else turns[start] = Turn(false, it) }
             .onFailure { if (turns.size > start) turns.removeAt(start); turns += Turn(false, "⚠️ " + (it.message ?: "Something went wrong.")) }
            a.refresh()
        }
    }

    private var last = 0L
    private fun throttle() { val n = System.currentTimeMillis(); if (n - last > 500) { last = n; a.refresh() } }
}

// ======================= Findings helpers =======================
private const val FINDINGS_JSON_SPEC = "{\"tower\":\"\",\"floor\":\"\",\"location\":\"\",\"category\":one of CATS,\"condition\":\"unsafe act or condition\",\"consequence\":\"\",\"L\":1-5,\"S\":1-5,\"immediate\":\"immediate control\",\"capa\":\"corrective and preventive action\",\"responsible\":\"contractor or person if known, else empty\",\"evidence\":\"closure evidence required\"}"

private fun findingsFromJson(arr: JSONArray, source: String, date: String = Store.today()): List<JSONObject> = (0 until arr.length()).map { i ->
    val x = arr.getJSONObject(i)
    val l = x.optInt("L", 3).coerceIn(1, 5); val s = x.optInt("S", 3).coerceIn(1, 5)
    JSONObject().put("kind", "finding").put("date", x.optString("date").takeIf { it.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) } ?: date)
        .put("tower", x.optString("tower")).put("floor", x.optString("floor")).put("location", x.optString("location"))
        .put("category", x.optString("category").takeIf { it in Store.CATS } ?: "Other")
        .put("condition", x.optString("condition")).put("consequence", x.optString("consequence"))
        .put("L", l).put("S", s).put("immediate", x.optString("immediate")).put("capa", x.optString("capa"))
        .put("responsible", x.optString("responsible")).put("reporter", x.optString("reporter"))
        .put("target", Store.addDays(date, when (Store.riskBand(l * s)) { "Critical" -> 0; "High" -> 1; "Medium" -> 3; else -> 7 }))
        .put("evidenceRequired", x.optString("evidence")).put("status", if (x.optBoolean("reportedRectified")) "Action taken – awaiting verification" else "Open")
        .put("source", source)
}

/** Splits Claude's answer into the readable report and the trailing ```json findings block. */
private fun splitReport(s: String): Pair<String, String?> {
    val m = Regex("```json\\s*([\\s\\S]*?)```").findAll(s).lastOrNull() ?: return s to null
    return s.removeRange(m.range).trim() to m.groupValues[1]
}

private fun MainActivity.findingPicker(items: MutableList<JSONObject>, picked: MutableSet<Int>, onAdded: () -> Unit): View {
    val c = card(text("${items.size} findings ready for the register", 16f, C.INK, true), muted("Untick any you don't want. Each one opens as ‘Open’; nothing is marked closed without verified evidence."))
    items.forEachIndexed { i, f ->
        val band = Store.riskBand(f.optInt("L") * f.optInt("S"))
        c.addView(row(
            CheckBox(this).apply { isChecked = i in picked; setOnCheckedChangeListener { _, ch -> if (ch) picked += i else picked -= i } },
            col(0, row(chip(band, C.sev(band)), text("  " + f.optString("category"), 12f, C.MUTED)),
                text(f.optString("condition"), 14f), muted(listOf(f.optString("tower"), f.optString("floor"), f.optString("location")).filter { it.isNotBlank() }.joinToString(", "))).margins(this, weight = 1f),
        ).margins(this, top = 6))
    }
    c.addView(button("Add selected to register", primary = true) {
        val n = picked.size
        picked.sorted().forEach { store.save(items[it]) }
        items.clear(); picked.clear(); toast("$n findings added to the register"); onAdded()
    }.margins(this, top = 10, bottom = 0))
    return c
}

// ======================= Photo check =======================
class PhotoScreen(a: MainActivity) : Screen(a) {
    override val title = "Photo check"
    private var image: ByteArray? = null
    private val out = Output(a) { "Photo inspection report" }.apply { docKind = "Photo inspection" }
    private val found = mutableListOf<JSONObject>(); private val picked = mutableSetOf<Int>()
    private var loc = ""; private var note = ""

    override fun build(): View = with(a) {
        val img = ImageView(this).apply { adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER; background = rounded(C.PANEL, dp(10).toFloat(), dp(1)) }
        image?.let { img.setImageBitmap(BitmapFactory.decodeByteArray(it, 0, it.size)) }
        val locIn = input("Tower / floor / location", loc).apply { setOnFocusChangeListener { v, _ -> loc = (v as EditText).text.toString() } }
        val noteIn = input("What should be checked? (optional)", note).apply { setOnFocusChangeListener { v, _ -> note = (v as EditText).text.toString() } }
        col(16,
            muted("Take or choose a photo of the work area. The AI describes only what is visible, rates the risk and drafts the corrective actions (Section 8 of your instructions)."),
            row(button("Take photo", primary = true) { takePhoto() }.margins(this, end = 8, weight = 1f), button("Choose photo") { pickImage() }.margins(this, weight = 1f)).margins(this, top = 10),
            if (image != null) img.margins(this, top = 10) else empty("No photo yet").margins(this, top = 10),
            label("Location"), locIn, label("Focus"), noteIn,
            button("Analyse photo", primary = true) {
                loc = locIn.text.toString(); note = noteIn.text.toString()
                val im = image ?: run { toast("Add a photo first"); return@button }
                found.clear(); picked.clear()
                out.run("Analyse the attached site photograph following Section 8 of the instructions. Location: ${loc.ifBlank { "not given" }}. ${if (note.isNotBlank()) "Focus: $note." else ""}\n" +
                    "Write the report for contractor follow-up: visible conditions, hazards and exposed persons, consequences, risk rating (provisional 5×5 unless a site matrix is given), immediate controls, permanent corrective actions, further information or inspection needed, and the limitations of photo-based inspection. Cite applicable Indian requirements only where you are confident; otherwise say what must be verified.\n" +
                    "Then end with one fenced ```json block containing an array of findings, one per hazard, each $FINDINGS_JSON_SPEC where CATS = ${JSONArray(Store.CATS)}.",
                    images = listOf(im), effort = Effort.HIGH) { full ->
                    val (report, json) = splitReport(full); out.set(report)
                    json?.let { runCatching { found += findingsFromJson(JSONArray(it), "Photo").onEach { f -> if (f.optString("location").isBlank()) f.put("location", loc) }; picked += found.indices; refresh() } }
                }
                refresh()
            }.margins(this, top = 14),
            out.view().margins(this, top = 10),
            if (found.isNotEmpty()) findingPicker(found, picked) { refresh() } else spacer(0),
        )
    }

    override fun onImage(bytes: ByteArray) { image = bytes; out.set(""); found.clear(); a.refresh() }
}

// ======================= WhatsApp import =======================
class WhatsAppScreen(a: MainActivity) : Screen(a) {
    override val title = "WhatsApp import"
    private var chat = ""
    private val out = Output(a) { "WhatsApp safety observation summary" }.apply { docKind = "WhatsApp summary" }
    private val found = mutableListOf<JSONObject>(); private val picked = mutableSetOf<Int>()

    override fun build(): View = with(a) {
        val box = input("Paste the WhatsApp messages here", chat, 8).apply { textSize = 13f }
        box.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(e: android.text.Editable?) { chat = e.toString() }
            override fun beforeTextChanged(s: CharSequence?, a1: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a1: Int, b: Int, c: Int) {}
        })
        col(16,
            muted("Fastest way: in WhatsApp open the safety group → ⋮ → More → Export chat → Without media → choose Safety Desk. The chat lands here. You can also paste messages or open an exported file."),
            box.margins(this, top = 10),
            row(button("Open exported file") { pickTextFile() }.margins(this, end = 8, weight = 1f),
                button("Extract & summarise", primary = true) { extract() }.margins(this, weight = 1f)).margins(this, top = 8),
            out.view().margins(this, top = 10),
            if (found.isNotEmpty()) findingPicker(found, picked) { refresh() } else spacer(0),
        )
    }

    override fun onTextFile(text: String) { chat = text.takeLast(120_000); a.refresh() }

    private fun extract() {
        if (chat.isBlank()) { a.toast("Paste or share the chat first"); return }
        found.clear(); picked.clear()
        out.run("Below is a WhatsApp group chat where site supervisors post safety observations (English, Hindi, Marathi or Hinglish).\n" +
            "1) Write a safety observation summary report: period covered, number of observations by category and risk (table), critical and high-risk items needing action today with responsible party, repeat issues, items people said were rectified (these still need verified closure evidence), and a short WhatsApp-ready reply (under 80 words) for the group.\n" +
            "2) End with one fenced ```json block: an array with one entry per genuine observation (ignore greetings, 'ok', media placeholders), each $FINDINGS_JSON_SPEC plus \"date\":\"YYYY-MM-DD\",\"reporter\":\"\",\"reportedRectified\":true|false, where CATS = ${JSONArray(Store.CATS)}. Translate to clear English.\n\nCHAT:\n$chat") { full ->
            val (report, json) = splitReport(full); out.set(report)
            json?.let { runCatching { found += findingsFromJson(JSONArray(it), "WhatsApp"); picked += found.indices; a.refresh() }.onFailure { a.toast("Summary ready, but the findings list could not be read.") } }
        }
        a.refresh()
    }
}

// ======================= Findings register =======================
class RegisterScreen(a: MainActivity) : Screen(a) {
    override val title = "Findings register"
    private var filter = "Open"

    override fun build(): View = with(a) {
        val t = Store.today()
        val all = store.list("finding")
        val list = when (filter) {
            "Open" -> all.filter { !it.optString("status").startsWith("Closed") }
            "Overdue" -> all.filter { !it.optString("status").startsWith("Closed") && it.optString("target").let { d -> d.isNotEmpty() && d < t } }
            "Closed" -> all.filter { it.optString("status").startsWith("Closed") }
            else -> all
        }
        val root = col(16)
        root.addView(row(*listOf("Open", "Overdue", "All", "Closed").map { f ->
            button(f, primary = f == filter) { filter = f; refresh() }.margins(this, end = 6, width = ViewGroup.LayoutParams.WRAP_CONTENT)
        }.toTypedArray()))
        root.addView(row(button("New finding", primary = true) { show(FindingScreen(this, null)) }.margins(this, end = 8, weight = 1f),
            button("Export CSV") { exportCsv(all) }.margins(this, weight = 1f)).margins(this, top = 8))
        if (list.isEmpty()) root.addView(empty("No ${filter.lowercase()} findings.").margins(this, top = 10))
        list.forEach { f ->
            val band = Store.riskBand(f.optInt("L", 3) * f.optInt("S", 3))
            val overdue = !f.optString("status").startsWith("Closed") && f.optString("target").let { it.isNotEmpty() && it < t }
            root.addView(card(
                row(text(f.optString("id") + "  ", 12f, C.MUTED, true), chip(band, C.sev(band)), if (overdue) text("  OVERDUE", 11f, C.CRIT, true) else spacer(0)),
                text(f.optString("condition"), 15f).apply { setPadding(0, dp(4), 0, dp(2)) },
                muted(listOf(f.optString("tower"), f.optString("floor"), f.optString("location")).filter { it.isNotBlank() }.joinToString(", ") +
                    " · ${f.optString("responsible").ifBlank { "owner not set" }} · target ${Store.fmt(f.optString("target"))}"),
                text(f.optString("status"), 12f, if (f.optString("status").startsWith("Closed")) C.LOW else C.MED, true),
            ).apply { setOnClickListener { show(FindingScreen(this@with, f.optString("id"))) } }.margins(this, top = 8, bottom = 0))
        }
        root
    }

    private fun exportCsv(all: List<JSONObject>) {
        val cols = listOf("id", "date", "tower", "floor", "location", "category", "condition", "consequence", "L", "S", "immediate", "capa", "responsible", "target", "evidenceRequired", "evidence", "status", "verifiedBy", "source")
        val q = { v: String -> "\"" + v.replace("\"", "\"\"") + "\"" }
        val csv = (listOf(cols.joinToString(",") + ",risk") + all.map { f -> cols.joinToString(",") { q(f.optString(it)) } + "," + f.optInt("L", 3) * f.optInt("S", 3) }).joinToString("\n")
        a.saveDownload("findings-register-${Store.today()}.csv", "text/csv", csv)
    }
}

class FindingScreen(a: MainActivity, private val id: String?) : Screen(a) {
    override val title = id ?: "New finding"
    private var photo: ByteArray? = null

    override fun build(): View = with(a) {
        val r = id?.let { store.get(it) } ?: JSONObject().put("date", Store.today()).put("target", Store.addDays(Store.today(), 2))
        val f = Form(this)
            .text("date", "Date (YYYY-MM-DD)", r.optString("date"))
            .text("tower", "Tower", r.optString("tower")).text("floor", "Floor", r.optString("floor")).text("location", "Location", r.optString("location"))
            .choice("category", "Category", Store.CATS, r.optString("category"))
            .text("condition", "Unsafe condition or activity", r.optString("condition"), 3)
            .text("consequence", "Potential consequence", r.optString("consequence"), 2)
            .choice("L", "Likelihood (1 rare – 5 almost certain)", listOf("1", "2", "3", "4", "5"), r.optInt("L", 3).toString())
            .choice("S", "Severity (1 first aid – 5 fatality)", listOf("1", "2", "3", "4", "5"), r.optInt("S", 3).toString())
            .text("immediate", "Immediate control measures", r.optString("immediate"), 2)
            .text("capa", "Corrective and preventive actions", r.optString("capa"), 3)
            .text("responsible", "Responsible person / contractor", r.optString("responsible"))
            .text("target", "Target date (YYYY-MM-DD)", r.optString("target"))
            .text("evidenceRequired", "Closure evidence required", r.optString("evidenceRequired"), 2)
            .text("evidence", "Closure evidence received", r.optString("evidence"), 2, hint = "e.g. photo of rigid barricade taken 12-10-2026, inspected by …")
            .text("verifiedBy", "Verified by", r.optString("verifiedBy"))
            .choice("status", "Verification status", Store.STATUSES, r.optString("status").ifBlank { "Open" })
        val photoFile = File(filesDir, "photos/${id ?: "new"}.jpg")
        val img = ImageView(this).apply { adjustViewBounds = true }
        (photo ?: photoFile.takeIf { it.exists() }?.readBytes())?.let { img.setImageBitmap(BitmapFactory.decodeByteArray(it, 0, it.size)) }
        col(16,
            muted("Risk rating method: 5×5 likelihood × severity (provisional unless your site matrix is used). 1–4 Low, 5–9 Medium, 10–15 High, 16–25 Critical."),
            f.view,
            label("Photo"), img,
            row(button("Take photo") { takePhoto() }.margins(this, end = 8, weight = 1f), button("Choose photo") { pickImage() }.margins(this, weight = 1f)),
            button("Save", primary = true) { save(r, f) }.margins(this, top = 14),
            if (id != null) button("Draft corrective action request with AI") {
                show(DocScreen(this, Docs.byId("car"), mapOf("contractor" to f["responsible"], "location" to listOf(f["tower"], f["floor"], f["location"]).filter { it.isNotBlank() }.joinToString(", "),
                    "finding" to "${r.optString("id")}: ${f["condition"]}. Consequence: ${f["consequence"]}. Immediate control: ${f["immediate"]}.", "target" to Store.fmt(f["target"]))))
            } else spacer(0),
            if (id != null) button("Delete finding") {
                AlertDialog.Builder(this).setMessage("Delete $id? This cannot be undone.").setPositiveButton("Delete") { _, _ -> store.delete(id); photoFile.delete(); goBack() }
                    .setNegativeButton("Cancel", null).show()
            }.apply { setTextColor(C.CRIT) }.margins(this, top = 8) else spacer(0),
        )
    }

    private fun save(r: JSONObject, f: Form) = with(a) {
        val v = f.values()
        if (v["condition"].isNullOrBlank()) { toast("Describe the unsafe condition or activity"); return }
        if (v["status"] == "Closed – verified" && (v["evidence"].isNullOrBlank() || v["verifiedBy"].isNullOrBlank())) {
            toast("A finding can only be closed with closure evidence and the name of who verified it."); return
        }
        v.forEach { (k, x) -> if (k == "L" || k == "S") r.put(k, x.toIntOrNull() ?: 3) else r.put(k, x) }
        r.put("kind", "finding"); if (!r.has("source")) r.put("source", "Site inspection")
        val saved = store.save(r)
        photo?.let { File(filesDir, "photos").mkdirs(); File(filesDir, "photos/${saved.optString("id")}.jpg").writeBytes(it) }
        toast("Saved ${saved.optString("id")}"); goBack()
    }

    override fun onImage(bytes: ByteArray) { photo = bytes; a.toast("Photo attached — tap Save") }
}

// ======================= Incidents =======================
class IncidentsScreen(a: MainActivity) : Screen(a) {
    override val title = "Incidents"
    override fun build(): View = with(a) {
        val root = col(16, button("Log an incident", primary = true) { show(IncidentScreen(this, null)) })
        val list = store.list("incident")
        if (list.isEmpty()) root.addView(empty("No incidents logged. Log near misses too; they are the cheapest lessons.").margins(this, top = 10))
        list.forEach { i ->
            val serious = i.optString("type") in Store.SERIOUS_INCIDENTS
            root.addView(card(row(text(i.optString("id") + "  ", 12f, C.MUTED, true), chip(i.optString("type"), if (serious) C.CRIT else C.MED)),
                text(i.optString("description"), 15f), muted("${Store.fmt(i.optString("date"))} · ${i.optString("location")} · ${i.optString("status")}"))
                .apply { setOnClickListener { show(IncidentScreen(this@with, i.optString("id"))) } }.margins(this, top = 8, bottom = 0))
        }
        root
    }
}

class IncidentScreen(a: MainActivity, private val id: String?) : Screen(a) {
    override val title = id ?: "New incident"
    override fun build(): View = with(a) {
        val r = id?.let { store.get(it) } ?: JSONObject().put("date", Store.today())
        val f = Form(this)
            .choice("type", "Type", Store.INCIDENT_TYPES, r.optString("type"))
            .text("date", "Date (YYYY-MM-DD)", r.optString("date")).text("time", "Time", r.optString("time"))
            .text("location", "Tower / floor / location", r.optString("location"))
            .text("persons", "Persons involved / injured", r.optString("persons"))
            .text("contractor", "Contractor", r.optString("contractor"))
            .text("description", "Verified facts: what happened", r.optString("description"), 4)
            .text("assumptions", "Unverified information", r.optString("assumptions"), 2)
            .text("actions", "Immediate actions taken", r.optString("actions"), 3)
            .text("notification", "Notifications made (who, when)", r.optString("notification"), 2)
            .choice("status", "Status", listOf("Open", "Under investigation", "Closed"), r.optString("status").ifBlank { "Open" })
        col(16, f.view,
            button("Save", primary = true) {
                if (f["description"].isBlank()) { toast("Describe what happened"); return@button }
                f.values().forEach { (k, v) -> r.put(k, v) }; r.put("kind", "incident"); val s = store.save(r); toast("Saved ${s.optString("id")}"); goBack()
            }.margins(this, top = 14),
            button("Investigate with AI (Section 6)") {
                show(DocScreen(this, Docs.byId("incident"), mapOf("type" to f["type"], "when" to "${Store.fmt(f["date"])} ${f["time"]}", "location" to f["location"],
                    "persons" to f["persons"] + if (f["contractor"].isNotBlank()) " (${f["contractor"]})" else "", "facts" to f["description"], "assumptions" to f["assumptions"], "actions" to f["actions"])))
            }.margins(this, top = 8),
        )
    }
}

// ======================= Documents =======================
class CatalogScreen(a: MainActivity, private val group: String? = null) : Screen(a) {
    override val title = group ?: "Prepare a document"
    override fun build(): View = with(a) {
        val root = col(16)
        var n = 0
        Docs.ALL.filter { group == null || it.group == group }.groupBy { it.group }.forEach { (g, items) ->
            val (gi, gc) = Icons.group(g)
            root.addView(section(gi, g).enter(n++))
            items.forEach { d ->
                root.addView(iconCard(gi, gc, d.title, (if (d.register) "📋 uses your register   " else "") + (if (d.web) "🌐 searches the web" else "Section ${d.section}"))
                    .apply { setOnClickListener { show(DocScreen(this@with, d)) } }.margins(this, bottom = 8).enter(n++))
            }
        }
        root
    }
}

class DocScreen(a: MainActivity, private val d: DocType, private val prefill: Map<String, String> = emptyMap()) : Screen(a) {
    override val title = d.title
    override val icon = Icons.group(d.group).first
    private var values: Map<String, String> = prefill
    private var web = d.web
    private val out = Output(a) { d.title }.apply { docKind = d.title }

    override fun build(): View = with(a) {
        val f = Form(this)
        d.fields.forEach { fl -> if (fl.options != null) f.choice(fl.key, fl.label, fl.options, values[fl.key]) else f.text(fl.key, fl.label, values[fl.key].orEmpty(), fl.lines, fl.hint) }
        val webBox = CheckBox(this).apply { text = "Search the web for current requirements and sources"; isChecked = web; setTextColor(C.INK); setOnCheckedChangeListener { _, c -> web = c } }
        col(16,
            if (d.register) muted("Uses the findings and incidents saved in this app for the period. Nothing is invented: missing figures are marked ‘not recorded’.") else spacer(0),
            f.view, webBox.margins(this, top = 8),
            button(if (out.markdown.isEmpty()) "Prepare with AI" else "Prepare again", primary = true) {
                values = f.values()
                out.run(Docs.prompt(store, d, values), web, effort = if (d.id in setOf("incident", "legal", "audit", "permit", "hira")) Effort.HIGH else Effort.MEDIUM)
                refresh()
            }.margins(this, top = 10),
            out.view().margins(this, top = 10),
            if (d.id == "escalation" && out.markdown.isNotEmpty() && !out.busy) button("Open in e-mail app") { emailDraft(out.markdown) }.margins(this, top = 8) else spacer(0),
        )
    }

    private fun emailDraft(md: String) {
        val subject = Regex("(?im)^\\**subject:?\\**\\s*:?\\s*(.+)$").find(md)?.groupValues?.get(1)?.trim('*', ' ') ?: d.title
        val to = listOf("escL2", "escL3", "escL4").map { a.store.pref(it) }.filter { it.isNotBlank() }.joinToString(",")
        a.email(to, subject, md)
    }
}

class SavedScreen(a: MainActivity) : Screen(a) {
    override val title = "Saved documents"
    override fun build(): View = with(a) {
        val root = col(16)
        val list = store.list("doc")
        if (list.isEmpty()) root.addView(empty("Documents you keep from any tool appear here."))
        list.forEach { r ->
            root.addView(iconCard("📄", C.BLUE, r.optString("title"), "${r.optString("id")} · ${Store.fmt(r.optString("date"))} · ${r.optString("type")}")
                .apply { setOnClickListener { show(DocViewScreen(this@with, r.optString("title"), r.optString("body"), r.optString("id"))) } }.margins(this, bottom = 6))
        }
        root
    }
}

class DocViewScreen(a: MainActivity, private val name: String, private val body: String, private val id: String? = null) : Screen(a) {
    override val title = name
    private val out = Output(a) { name }.apply { set(body) }
    override fun build(): View = with(a) {
        col(16, out.view(),
            if (id != null) button("Delete document") {
                AlertDialog.Builder(this).setMessage("Delete this document?").setPositiveButton("Delete") { _, _ -> store.delete(id); goBack() }.setNegativeButton("Cancel", null).show()
            }.apply { setTextColor(C.CRIT) }.margins(this, top = 8) else spacer(0))
    }
}

// ======================= Escalation =======================
class EscalationScreen(a: MainActivity) : Screen(a) {
    override val title = "Escalation"
    private val skip = mutableSetOf<String>()
    private val out = Output(a) { "Safety escalation" }.apply { docKind = "Escalation email" }

    override fun build(): View = with(a) {
        val items = store.escalations()
        val root = col(16, muted("Items that meet your escalation rules: critical risk, overdue corrective actions, high risk open for 2+ days, and serious incidents. The AI drafts the e-mail; you review it and send it yourself."))
        if (items.isEmpty()) root.addView(empty("Nothing needs escalating right now.").margins(this, top = 10))
        items.forEach { (why, r) ->
            val key = r.optString("id") + why
            root.addView(row(CheckBox(this).apply { isChecked = key !in skip; setOnCheckedChangeListener { _, c -> if (c) skip -= key else skip += key } },
                col(0, text(why, 14f, C.INK, true), muted("${r.optString("id")} · ${r.optString("condition").ifBlank { r.optString("description") }.take(110)} · ${r.optString("responsible").ifBlank { r.optString("contractor") }}")).margins(this, weight = 1f)
            ).margins(this, top = 8))
        }
        if (items.isNotEmpty()) root.addView(button("Draft escalation e-mail", primary = true) {
            val sel = items.filter { (w, r) -> (r.optString("id") + w) !in skip }
            if (sel.isEmpty()) { toast("Select at least one item"); return@button }
            val list = sel.joinToString("\n") { (w, r) -> "- ${r.optString("id")} | $w | ${r.optString("condition").ifBlank { r.optString("description") }} | location ${r.optString("tower")} ${r.optString("floor")} ${r.optString("location")} | responsible ${r.optString("responsible").ifBlank { r.optString("contractor") }} | raised ${Store.fmt(r.optString("date"))} | target ${Store.fmt(r.optString("target"))}" }
            out.run("Draft an escalation e-mail to project leadership following Sections 7, 10 and 11 of the instructions. Start with a Subject: line. For each item give the risk, evidence on record, what has been done at site level, and the decision or support needed with a deadline. Recommend stopping work where there is imminent danger. Then add a 3-line WhatsApp version.\nItems:\n$list")
            refresh()
        }.margins(this, top = 12))
        root.addView(out.view().margins(this, top = 10))
        if (out.markdown.isNotEmpty() && !out.busy) root.addView(button("Open in e-mail app") {
            val subject = Regex("(?im)^\\**subject:?\\**\\s*:?\\s*(.+)$").find(out.markdown)?.groupValues?.get(1)?.trim('*', ' ') ?: "Safety escalation"
            email(listOf("escL2", "escL3", "escL4").map { store.pref(it) }.filter { it.isNotBlank() }.joinToString(","), subject, out.markdown)
        }.margins(this, top = 8))
        root
    }
}
