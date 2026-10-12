package com.parag.safetydesk

import android.app.AlertDialog
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import org.json.JSONObject

/**
 * One column of a tracker. [due] marks a date the app watches (overdue / due within 7 days);
 * [show] puts the value on the list card.
 */
data class TCol(
    val key: String, val label: String, val options: List<String>? = null, val date: Boolean = false,
    val due: Boolean = false, val lines: Int = 1, val number: Boolean = false, val hint: String = "", val show: Boolean = false,
)

/** A register the safety manager keeps up to date. Records are saved with kind "trk_<id>". */
data class Tracker(val id: String, val prefix: String, val title: String, val sub: String, val cols: List<TCol>, val report: String) {
    val kind get() = "trk_$id"
}

object Trackers {
    private val REMARKS = TCol("remarks", "Remarks", lines = 2)
    private val CONTRACTOR = TCol("contractor", "Contractor / agency")
    private val RESP = TCol("responsible", "Responsible person")
    private val OPEN_CLOSED = TCol("status", "Status", listOf("Open", "Closed"), show = true)

    val ALL = listOf(
        Tracker("fall", "FP", "Fall protection tracker", "Harnesses, lanyards, lifelines, nets, anchors", listOf(
            TCol("tag", "Item ID / tag no.", show = true),
            TCol("type", "Item", listOf("Full-body harness", "Double lanyard with shock absorber", "Fall arrester / retractable block", "Horizontal lifeline", "Vertical lifeline", "Safety net", "Anchor point", "Edge protection / guard-rail"), show = true),
            TCol("serial", "Make / serial no. / IS mark"), TCol("issuedTo", "Issued to / location", show = true), CONTRACTOR,
            TCol("issueDate", "Issue / installation date", date = true),
            TCol("lastInsp", "Last inspection", date = true), TCol("nextInsp", "Next inspection due", date = true, due = true),
            TCol("retire", "Manufacturer expiry / retire by", date = true, due = true),
            TCol("condition", "Condition", listOf("OK – in use", "Needs repair", "Quarantined", "Discarded"), show = true),
            TCol("inspector", "Inspected by"), REMARKS),
            "a fall protection equipment status report: totals by item and condition, items overdue for inspection or past retire date, quarantined/discarded items, and actions with owners"),
        Tracker("equip", "EQ", "Plant & equipment tracker", "Cranes, hoists, excavators, DG, welding sets…", listOf(
            TCol("equipId", "Equipment ID / reg. no.", show = true),
            TCol("type", "Equipment", listOf("Tower crane", "Passenger / material hoist", "Mobile crane / hydra", "Excavator", "Backhoe loader (JCB)", "Concrete pump / boom placer", "Transit mixer", "Batching plant", "DG set", "Welding machine", "Grinder / cutter", "Air compressor", "Other"), show = true),
            TCol("make", "Make / model / capacity"), CONTRACTOR, TCol("location", "Location", show = true),
            TCol("operator", "Operator name"), TCol("operatorValid", "Operator licence / competency valid till", date = true, due = true),
            TCol("docsValid", "Fitness / insurance / PUC valid till", date = true, due = true),
            TCol("lastCheck", "Last inspection", date = true), TCol("nextCheck", "Next inspection due", date = true, due = true),
            TCol("status", "Status", listOf("In use", "Under maintenance", "Not fit – tagged out", "Demobilised"), show = true), REMARKS),
            "a plant and equipment status report: equipment on site by type, anything tagged out, inspections, operator competency or documents expired or due within 7 days, and actions with owners"),
        Tracker("tpi", "TPI", "TPI inspection tracker", "Third-party / competent-person certificates", listOf(
            TCol("equipment", "Equipment / lifting tackle", hint = "e.g. tower crane TC-2, chain block, D-shackle set, slings", show = true),
            TCol("equipId", "ID / serial no."), TCol("swl", "SWL / capacity"),
            TCol("agency", "TPI agency / competent person"), TCol("certNo", "Certificate no."),
            TCol("inspDate", "Inspection date", date = true), TCol("validTill", "Valid till", date = true, due = true, show = true),
            TCol("result", "Result", listOf("Fit for use", "Fit with conditions", "Not fit"), show = true),
            TCol("observations", "Observations to close", lines = 2),
            TCol("status", "Status", listOf("Valid", "Renewal due", "Expired – stop use", "Removed from site"), show = true), REMARKS),
            "a TPI / statutory examination status report: certificates valid, due within 30 days and expired, 'not fit' or conditional results with open observations, and a stop-use list for anything expired"),
        Tracker("env", "ENV", "Environment tracker", "Noise, water, waste, spills, consents", listOf(
            TCol("date", "Date", date = true, show = true),
            TCol("aspect", "Aspect", listOf("Noise level", "Water consumption", "Wastewater / STP", "Construction & demolition waste", "Hazardous waste (oil, paint, chemicals)", "Spill", "Tree / green cover", "Consent / permit (CTE, CTO, EC)", "Other"), show = true),
            TCol("location", "Location"), TCol("value", "Measured value / quantity"), TCol("unit", "Unit", hint = "e.g. dB(A), KL, tonnes"),
            TCol("limit", "Limit / target (with source)"),
            TCol("compliant", "Within limit?", listOf("Yes", "No", "Not applicable"), show = true),
            TCol("action", "Action required", lines = 2), RESP,
            TCol("dueDate", "Action / renewal due", date = true, due = true), OPEN_CLOSED, REMARKS),
            "an environmental performance report: readings against limits, exceedances, waste quantities, consent validity, and open actions. Cite the source of each limit only if it is entered; otherwise mark 'limit to confirm'"),
        Tracker("air", "AIR", "Air pollution & dust control tracker", "PM10 / PM2.5 and dust control measures", listOf(
            TCol("date", "Date", date = true, show = true), TCol("location", "Monitoring location", show = true),
            TCol("pm10", "PM10 (µg/m³)", number = true, show = true), TCol("pm25", "PM2.5 (µg/m³)", number = true, show = true),
            TCol("aqi", "AQI (if available)"), TCol("monitoredBy", "Monitored by (sensor / agency)"),
            TCol("measures", "Dust control measures in place", lines = 3, hint = "water sprinkling, anti-smog gun, wind barrier / green net, covered trucks, wheel wash, covered stockpiles"),
            TCol("gaps", "Deficiencies seen", lines = 2), TCol("action", "Action required", lines = 2), RESP,
            TCol("dueDate", "Action due", date = true, due = true), OPEN_CLOSED),
            "an air pollution and dust control report: PM10/PM2.5 trend by location, days above the limit (state the limit and its source only if verified, else 'limit to confirm'), dust control measures and gaps, and open actions"),
        Tracker("committee", "SC", "Safety committee tracker", "Members and meetings", listOf(
            TCol("entry", "Entry", listOf("Member", "Meeting"), show = true),
            TCol("date", "Date (appointment / meeting)", date = true, show = true),
            TCol("name", "Member name / meeting title", show = true),
            TCol("role", "Role", listOf("Chairperson", "Secretary (safety officer)", "Management representative", "Worker representative", "Contractor representative", "Medical / welfare", "Not applicable – meeting")),
            TCol("org", "Organisation / trade"),
            TCol("points", "Meeting points / decisions", lines = 4), TCol("actions", "Actions, owners and target dates", lines = 3),
            TCol("next", "Next meeting due", date = true, due = true), REMARKS),
            "a safety committee status report: present composition (management vs worker representatives), meetings held, attendance, decisions, open actions and the next meeting date"),
        Tracker("camp", "LC", "Labour camp inspection tracker", "Accommodation, water, toilets, canteen, crèche", listOf(
            TCol("date", "Inspection date", date = true, show = true), TCol("camp", "Camp / block", show = true), CONTRACTOR,
            TCol("occupants", "Occupants", number = true),
            TCol("area", "Area inspected", listOf("Accommodation / rooms", "Drinking water", "Toilets & bathing", "Canteen / kitchen", "Crèche", "First aid", "Fire safety", "Electrical", "Housekeeping & drainage", "Pest / vector control", "Overall"), show = true),
            TCol("rating", "Condition", listOf("Satisfactory", "Needs improvement", "Unsatisfactory"), show = true),
            TCol("gaps", "Deficiencies", lines = 3), TCol("action", "Action required", lines = 2), RESP,
            TCol("dueDate", "Target date", date = true, due = true), OPEN_CLOSED),
            "a labour camp welfare inspection report: condition by area, deficiencies, open actions with owners, and items repeated from earlier inspections"),
        Tracker("bocw", "BC", "BOCW compliance tracker", "Registration, cess, welfare, registers, returns", listOf(
            TCol("requirement", "Requirement", lines = 2, hint = "e.g. establishment registration, cess payment, worker registration with Board", show = true),
            TCol("reference", "Legal reference (verified)", hint = "Act / rule / form, as checked"),
            TCol("frequency", "Frequency", listOf("One-time", "Monthly", "Quarterly", "Half-yearly", "Annual", "On event")),
            RESP, TCol("dueDate", "Next due", date = true, due = true, show = true),
            TCol("evidence", "Evidence / record kept", lines = 2),
            TCol("status", "Status", listOf("Compliant", "Partly compliant", "Not compliant", "Not applicable"), show = true), REMARKS),
            "a BOCW compliance status report: each requirement with status, evidence and next due date, gaps, and actions. Use only the legal references entered; flag any blank reference as 'to verify'"),
    )

    fun byId(id: String) = ALL.first { it.id == id }
    fun byKind(kind: String) = ALL.firstOrNull { it.kind == kind }

    /** Days until the earliest watched date that is still relevant (null if none). Closed rows are ignored. */
    fun dueIn(t: Tracker, r: JSONObject): Int? {
        val st = r.optString("status") + r.optString("condition")
        if (st.startsWith("Closed") || st.contains("Discarded") || st.contains("Demobilised") || st.contains("Removed")) return null
        return t.cols.filter { it.due }.mapNotNull { c -> r.optString(c.key).takeIf { it.length == 10 } }
            .minOrNull()?.let { Store.daysBetween(Store.today(), it) }
    }

    fun overdue(store: Store) = ALL.sumOf { t -> store.list(t.kind).count { (dueIn(t, it) ?: 1) < 0 } }

    fun table(store: Store, t: Tracker): String {
        val rows = store.list(t.kind)
        val sb = StringBuilder("TRACKER DATA – ${t.title} (${rows.size} rows, from the Safety Desk app on ${Store.fmt(Store.today())}). This is the only data source; do not add rows or figures that are not here.\n")
        sb.append("ID | " + t.cols.joinToString(" | ") { it.label } + "\n")
        rows.forEach { r -> sb.append(r.optString("id") + " | " + t.cols.joinToString(" | ") { c -> r.optString(c.key).let { v -> if (c.date) Store.fmt(v) else v.replace("\n", "; ") } } + "\n") }
        return sb.toString()
    }
}

class TrackersScreen(a: MainActivity) : Screen(a) {
    override val title = "Trackers"
    override fun build(): View = with(a) {
        val root = col(16, muted("Keep each register up to date here. Dates marked ‘due’ are watched: anything overdue or due within 7 days is flagged."))
        var n = 0
        Trackers.ALL.forEach { t ->
            val rows = store.list(t.kind)
            val od = rows.count { (Trackers.dueIn(t, it) ?: 1) < 0 }
            val soon = rows.count { (Trackers.dueIn(t, it) ?: -1) in 0..7 }
            val (ic, color) = Icons.tracker(t.id)
            val stats = row(chip("${rows.size} entries", C.BLUE), if (od > 0) chip("$od overdue", C.CRIT).margins(this, start = 6, bottom = 0, width = ViewGroup.LayoutParams.WRAP_CONTENT) else spacer(0),
                if (soon > 0) chip("$soon due soon", C.MED).margins(this, start = 6, bottom = 0, width = ViewGroup.LayoutParams.WRAP_CONTENT) else spacer(0)).apply { setPadding(0, dp(6), 0, 0) }
            root.addView(iconCard(ic, color, t.title, t.sub, stats).apply { setOnClickListener { show(TrackerScreen(this@with, t)) } }.margins(this, top = 10, bottom = 0).enter(n++))
        }
        root
    }
}

class TrackerScreen(a: MainActivity, private val t: Tracker) : Screen(a) {
    override val title = t.title
    override val icon = Icons.tracker(t.id).first
    private var filter = "All"
    private var web = false
    private val out = Output(a) { "${t.title} report" }.apply { docKind = "${t.title} report" }

    override fun build(): View = with(a) {
        val all = store.list(t.kind)
        val list = when (filter) {
            "Overdue" -> all.filter { (Trackers.dueIn(t, it) ?: 1) < 0 }
            "Due in 7 days" -> all.filter { (Trackers.dueIn(t, it) ?: -1) in 0..7 }
            else -> all
        }
        val root = col(16)
        root.addView(row(*listOf("All", "Overdue", "Due in 7 days").map { f ->
            button(f, primary = f == filter) { filter = f; refresh() }.margins(this, end = 6, width = ViewGroup.LayoutParams.WRAP_CONTENT)
        }.toTypedArray()))
        root.addView(row(button("New entry", primary = true) { show(TrackerItemScreen(this, t, null)) }.margins(this, end = 8, weight = 1f),
            button("Export CSV") { exportCsv(all) }.margins(this, weight = 1f)).margins(this, top = 8))
        if (list.isEmpty()) root.addView(empty(if (all.isEmpty()) "No entries yet. Tap New entry to add the first one." else "Nothing ${filter.lowercase()}.").margins(this, top = 10))
        list.forEach { r ->
            val d = Trackers.dueIn(t, r)
            val flag = when { d == null -> ""; d < 0 -> "OVERDUE ${-d} d"; d <= 7 -> "DUE IN $d d"; else -> "" }
            val shown = t.cols.filter { it.show }.map { c -> r.optString(c.key).let { if (c.date) Store.fmt(it) else it } }.filter { it.isNotBlank() }
            root.addView(card(
                row(text(r.optString("id") + "  ", 12f, C.MUTED, true), if (flag.isNotEmpty()) text(flag, 11f, if (d!! < 0) C.CRIT else C.MED, true) else spacer(0)),
                text(shown.firstOrNull().orEmpty(), 15f).apply { setPadding(0, dp(4), 0, dp(2)) },
                muted(shown.drop(1).joinToString(" · ")),
            ).apply { setOnClickListener { show(TrackerItemScreen(this@with, t, r.optString("id"))) } }.margins(this, top = 8, bottom = 0))
        }
        root.addView(h2("Report from this tracker").margins(this, top = 18))
        val webBox = CheckBox(this).apply { text = "Search the web for applicable limits and rules"; isChecked = web; setTextColor(C.INK); setOnCheckedChangeListener { _, c -> web = c } }
        root.addView(webBox)
        root.addView(button(if (out.markdown.isEmpty()) "Prepare report with AI" else "Prepare again", primary = true) {
            if (all.isEmpty()) { toast("Add some entries first."); return@button }
            out.run("Prepare ${t.report} for the Safety Manager. Follow the Master Operating Instructions (Sections 9 and 12). " +
                "Use Indian English, DD-MM-YYYY dates and metric units. Begin with a short summary for management, then tables. " +
                "Where information is missing, list it under 'Information needed' instead of inventing it.\n\n" + Trackers.table(store, t), web)
            refresh()
        }.margins(this, top = 8))
        root.addView(out.view().margins(this, top = 10))
        root
    }

    private fun exportCsv(all: List<JSONObject>) {
        val q = { v: String -> "\"" + v.replace("\"", "\"\"") + "\"" }
        val csv = (listOf((listOf("ID") + t.cols.map { it.label }).joinToString(",") { q(it) }) +
            all.map { r -> (listOf(r.optString("id")) + t.cols.map { c -> r.optString(c.key).let { if (c.date) Store.fmt(it) else it } }).joinToString(",") { q(it) } }).joinToString("\n")
        a.saveDownload("${t.id}-tracker-${Store.today()}.csv", "text/csv", csv)
    }
}

class TrackerItemScreen(a: MainActivity, private val t: Tracker, private val id: String?) : Screen(a) {
    override val title = id ?: "New – ${t.title}"
    override val icon = Icons.tracker(t.id).first
    override fun build(): View = with(a) {
        val r = id?.let { store.get(it) } ?: JSONObject()
        val f = Form(this)
        t.cols.forEach { c ->
            val v = r.optString(c.key).ifEmpty { if (c.date && !c.due && (c.key == "date" || c.key == "inspDate" || c.key == "lastInsp" || c.key == "lastCheck")) Store.today() else "" }
            if (c.options != null) f.choice(c.key, c.label, c.options, v.ifEmpty { null })
            else f.text(c.key, if (c.date) "${c.label} (YYYY-MM-DD)" else c.label, v, c.lines, if (c.date) "YYYY-MM-DD" else c.hint, c.number)
        }
        col(16,
            f.view,
            button("Save", primary = true) {
                val v = f.values()
                val bad = t.cols.filter { it.date }.firstOrNull { c -> v[c.key].orEmpty().let { it.isNotEmpty() && !Regex("\\d{4}-\\d{2}-\\d{2}").matches(it) } }
                if (bad != null) { toast("${bad.label}: use YYYY-MM-DD, e.g. ${Store.today()}"); return@button }
                val rec = JSONObject(r.toString()).put("kind", t.kind)
                v.forEach { (k, x) -> rec.put(k, x.trim()) }
                if (!rec.has("date") || rec.optString("date").isEmpty()) rec.put("date", Store.today())
                val saved = store.save(rec); toast("Saved ${saved.optString("id")}"); goBack()
            }.margins(this, top = 14),
            if (id != null) button("Delete entry") {
                AlertDialog.Builder(this).setMessage("Delete $id? This cannot be undone.").setPositiveButton("Delete") { _, _ -> store.delete(id); goBack() }.setNegativeButton("Cancel", null).show()
            }.margins(this, top = 8) else spacer(0),
        )
    }
}
