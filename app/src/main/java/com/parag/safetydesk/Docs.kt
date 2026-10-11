package com.parag.safetydesk

import org.json.JSONObject

/** One input on a document form. [options] makes it a drop-down. */
data class Field(val key: String, val label: String, val lines: Int = 1, val options: List<String>? = null, val hint: String = "")

/**
 * A ready-to-use document the agent can prepare. [section] names the part of the
 * instructions file it follows; [register] adds the app's findings/incidents for the period.
 */
data class DocType(
    val id: String, val group: String, val title: String, val section: String,
    val fields: List<Field>, val ask: String, val web: Boolean = false, val register: Boolean = false,
)

object Docs {
    private val LOC = Field("location", "Tower / floor / location", hint = "e.g. Tower B, 14th floor, east side")
    private val CONTRACTOR = Field("contractor", "Contractor / agency")
    private val DETAILS = Field("details", "Details / notes", 5, hint = "What you saw, what is planned, anything the document must include")
    private val LANG = Field("language", "Language", options = listOf("English", "Hindi", "Marathi", "English + Hindi", "English + Marathi"))
    private val PERIOD = Field("period", "Reporting period", options = listOf("Today", "Last 7 days", "This month", "Last 30 days", "All records"))

    val ALL = listOf(
        // Daily safety operations — Section 4
        DocType("daily", "Inspections & checklists", "Daily site safety inspection checklist", "4A",
            listOf(LOC, Field("activities", "Activities running today", 3, hint = "e.g. slab casting T2-L18, façade work T1, excavation for UG tank")),
            "a daily site safety inspection checklist tailored to today's activities, with columns Item | Requirement | Yes/No/NA | Remarks, grouped by activity, plus a findings log table with the fields required in Section 4"),
        DocType("uauc", "Inspections & checklists", "Unsafe act / unsafe condition report", "4B",
            listOf(LOC, CONTRACTOR, Field("observation", "What was observed", 4), Field("people", "People exposed (number / trade)")),
            "an unsafe act and unsafe condition report with every finding field listed in Section 4 (finding ID, location, condition, consequence, risk rating and method, immediate control, CAPA, responsible, target date, closure evidence, verification status)"),
        DocType("wah", "Inspections & checklists", "Work-at-height inspection checklist", "4C", listOf(LOC, Field("activity", "Height activity", hint = "e.g. shuttering at slab edge, façade glazing, lift shaft")),
            "a work-at-height inspection checklist (edge protection, openings, harness and anchorage, lifelines, safety nets, access, fitness and training, permit, rescue plan)"),
        DocType("crane", "Inspections & checklists", "Tower crane & material hoist checklist", "4D",
            listOf(Field("equipment", "Equipment", options = listOf("Tower crane", "Material hoist", "Passenger hoist", "Mobile crane / hydra", "Gantry / other")), Field("equipmentId", "Equipment ID / make / model"), LOC),
            "a pre-use and periodic inspection checklist for this lifting equipment, including certificates and competent-person examination records to check, operator and signaller competence, limit switches, wire ropes, foundations/ties, exclusion zones and wind limits. Do not state statutory inspection intervals unless verified"),
        DocType("scaffold", "Inspections & checklists", "Scaffolding, formwork & platform checklist", "4E", listOf(LOC, Field("type", "Type", hint = "e.g. cup-lock scaffold, aluminium formwork, cantilever platform")),
            "an inspection checklist for scaffolding, formwork and temporary platforms including tagging (green/red), base, bracing, ties, platforms, guard-rails, toe-boards, access, load limits and pre-pour checks"),
        DocType("fire", "Inspections & checklists", "Fire, electrical & housekeeping checklist", "4F", listOf(LOC),
            "a combined fire prevention, temporary electrical safety and housekeeping checklist (extinguishers, hot work, flammables, DBs, RCCB/ELCB, earthing, cables, lighting, access routes, stacking, debris)"),
        // Permits & risk — Section 5 and 4G
        DocType("permit", "Permits & risk assessment", "Work permit / RA review", "5",
            listOf(Field("permitType", "Document to review", options = listOf("Work-at-height permit", "Hot work permit", "Excavation permit", "Confined space permit", "Electrical / LOTO permit", "Lifting plan", "HIRA / JSA", "Method statement")), LOC,
                Field("content", "Paste the permit / risk assessment text", 8)),
            "a review of this permit or risk assessment against Section 5: list each missing item, unsafe condition and required correction in a table, then a summary of what must be fixed before work starts. You must not approve or sign the permit; end with a note that approval rests with the authorised person"),
        DocType("hira", "Permits & risk assessment", "HIRA (hazard identification & risk assessment)", "4G, 11",
            listOf(Field("activity", "Activity"), LOC, DETAILS),
            "a HIRA table: Step | Hazard | Who is exposed | Consequence | Likelihood | Severity | Risk | Controls (hierarchy) | Residual risk | Responsible. Use the site's approved matrix if given in the notes; otherwise a 5×5 matrix clearly labelled provisional, with its assumptions"),
        DocType("jsa", "Permits & risk assessment", "Job safety analysis (JSA)", "4G", listOf(Field("job", "Job / task"), LOC, Field("crew", "Crew size and trades")),
            "a job safety analysis: sequence of job steps, hazards for each step, controls, PPE, permits needed and responsible person"),
        DocType("method", "Permits & risk assessment", "Method statement review template", "4G", listOf(Field("activity", "Activity"), DETAILS),
            "a method statement review template with the checks a safety manager must make, and comments on the details given"),
        DocType("sop", "Permits & risk assessment", "Safe operating procedure (SOP)", "4G", listOf(Field("activity", "Activity / equipment"), LANG, DETAILS),
            "an SOP: purpose, scope, responsibilities, pre-start requirements and permits, step-by-step procedure with hazards and controls, PPE, inspection points, emergency response, references (distinguish mandatory requirements from good practice), records"),
        // Training — 4H
        DocType("tbt", "Training", "Toolbox talk", "4H", listOf(Field("topic", "Topic"), LANG, Field("minutes", "Duration", options = listOf("10 minutes", "15 minutes", "30 minutes"))),
            "a toolbox talk in simple words workers understand (Devanagari script for Hindi/Marathi): opening question, why it matters, key do's and don'ts, PPE, emergency actions, 3 questions to check understanding, and an attendance sheet table"),
        DocType("training", "Training", "Training session plan & material", "4H", listOf(Field("topic", "Training topic"), Field("audience", "Audience", hint = "e.g. new workers, supervisors, riggers"), LANG, Field("hours", "Duration")),
            "a training session plan with learning objectives, timed agenda, content notes for the trainer, practical demonstration, assessment questions and attendance/feedback format"),
        // Meetings & reports — 4J, 4K, 9
        DocType("report", "Reports & meetings", "EHS report (daily / weekly / monthly)", "4J, 9",
            listOf(PERIOD, Field("manhours", "Man-hours in period (if known)", hint = "leave blank if not verified"), Field("extra", "Other inputs (training, drills, audits…)", 4)),
            "an EHS report for the period using only the register data and inputs provided: period, data source, key indicators, findings by category and risk, open and overdue actions, incidents, highlights, outstanding actions, and limitations. Never fabricate statistics; mark anything unknown as 'not recorded'", register = true),
        DocType("kpi", "Reports & meetings", "KPI summary for management", "9", listOf(PERIOD),
            "an EHS KPI summary table and short commentary computed only from the register data (counts, closure rate, overdue, repeat hazards, incidents). State the limitations", register = true),
        DocType("agenda", "Reports & meetings", "Safety meeting agenda", "4K", listOf(Field("meeting", "Meeting", options = listOf("Weekly safety review", "Monthly safety committee", "Contractor safety meeting", "Incident review", "Management review")), Field("date", "Date and time"), DETAILS),
            "a meeting agenda with timings, owners and pre-reads; include open high-risk items from the register", register = true),
        DocType("mom", "Reports & meetings", "Minutes of meeting (MoM)", "4K", listOf(Field("meeting", "Meeting"), Field("date", "Date"), Field("attendees", "Attendees", 2), Field("notes", "Rough notes", 8)),
            "formal minutes of meeting: details, attendees, discussion points, decisions, and an action table (Action | Responsible | Target date | Status). Do not add decisions that are not in the notes"),
        DocType("ppt", "Reports & meetings", "Management presentation outline", "4K", listOf(Field("purpose", "Purpose / audience"), PERIOD),
            "a slide-by-slide outline (title, 3–5 bullets, suggested chart) for a management presentation using the register data", register = true),
        // Emergency — 4L
        DocType("erp", "Emergency", "Emergency response plan", "4L", listOf(Field("scenarios", "Scenarios to cover", 2, hint = "fire, fall from height, crane collapse, electrocution, medical emergency…"), DETAILS),
            "an emergency response plan: organisation and contacts table (leave numbers blank to be filled), alarm, scenario-wise actions, evacuation and assembly points, rescue arrangements (including suspended-worker rescue), first aid and hospital tie-up, drills and records"),
        DocType("drill", "Emergency", "Mock drill evaluation", "4L", listOf(Field("drill", "Drill type"), Field("date", "Date and time"), Field("observations", "Observations and timings", 6)),
            "a mock drill evaluation report: scenario, timeline, what went well, gaps, corrective actions with responsibility and dates"),
        // Incidents — Section 6
        DocType("incident", "Incidents", "Incident investigation report", "6",
            listOf(Field("type", "Type", options = Store.INCIDENT_TYPES), Field("when", "Date and time"), LOC, Field("persons", "Persons involved / injured"),
                Field("facts", "Verified facts", 5), Field("assumptions", "Unverified information / assumptions", 3), Field("actions", "Immediate actions already taken", 3)),
            "an incident investigation following all ten steps of Section 6: facts vs assumptions, immediate/underlying/organisational factors, 5-Why or causal-factor analysis, evidence still to collect, containment, CAPA with proposed owners and realistic deadlines, notification obligations to verify, and the formal report. Never invent witness statements, dates, injuries or root causes"),
        // Contractor management — Section 7
        DocType("nc", "Contractor management", "Non-compliance notice", "7", listOf(CONTRACTOR, LOC, Field("evidence", "Documented evidence", 4), Field("deadline", "Deadline to comply")),
            "a formal non-compliance notice to the contractor separating documented evidence from allegations"),
        DocType("warning", "Contractor management", "Safety warning letter", "7", listOf(CONTRACTOR, Field("evidence", "Documented evidence and previous warnings", 5)),
            "a professional safety warning letter. Do not impose penalties; mention that any penalty is subject to management authorisation and the contract"),
        DocType("car", "Contractor management", "Corrective action request (CAR)", "7", listOf(CONTRACTOR, LOC, Field("finding", "Finding", 4), Field("target", "Target date")),
            "a corrective action request with finding, requirement, root cause to be submitted by the contractor, corrective and preventive actions, closure evidence required, and verification sign-off"),
        DocType("cartrack", "Contractor management", "Contractor corrective-action tracker", "4I", listOf(Field("contractor", "Contractor (blank = all)"), PERIOD),
            "a contractor compliance and corrective-action tracker table from the register: contractor, finding ID, finding, risk, target, status, days open, overdue flag, then a summary per contractor", register = true),
        DocType("score", "Contractor management", "Contractor safety scorecard", "7", listOf(Field("contractor", "Contractor (blank = all)"), PERIOD, Field("extra", "Other inputs (PPE audits, training, permits)", 3)),
            "a contractor safety scorecard with transparent criteria and weights, scored only from the register data and inputs, with comments and improvement actions", register = true),
        DocType("penalty", "Contractor management", "Safety penalty recommendation", "7", listOf(CONTRACTOR, Field("evidence", "Documented violations", 5), Field("clause", "Contract clause on safety penalties (if known)")),
            "a penalty recommendation for management. It is a recommendation only: state that a penalty needs management authorisation and a contractual basis"),
        DocType("stopwork", "Contractor management", "Stop-work recommendation", "7, 11", listOf(LOC, CONTRACTOR, Field("danger", "Imminent danger observed", 4)),
            "an urgent stop-work recommendation: the danger, people at risk, immediate isolation of the danger zone, who must be notified now, conditions to restart and who verifies them"),
        DocType("escalation", "Contractor management", "Escalation email to project leadership", "7, 10", listOf(Field("to", "To (names / roles)"), DETAILS),
            "an escalation email with subject line: issues with risk and evidence, what was already done at site level, the decision or support needed, and the deadline. Include open critical and overdue items from the register", register = true),
        // Communication — Section 9
        DocType("alert", "Communication", "Safety alert", "9", listOf(Field("topic", "What happened / hazard"), LANG),
            "a one-page safety alert: what happened, why it matters, lessons, actions required by all contractors, and a short WhatsApp version"),
        DocType("email", "Communication", "Email to contractor / management", "9", listOf(Field("to", "To"), Field("purpose", "Purpose"), DETAILS),
            "a concise professional email with subject line"),
        DocType("pr", "Communication", "Purchase request (PR) for safety items", "9", listOf(Field("items", "Items and quantities", 5, hint = "e.g. 50 helmets, 20 full-body harnesses, 6 ABC extinguishers"), Field("reason", "Justification / linked findings", 3), Field("needBy", "Required by")),
            "a purchase request table: item, specification (cite the relevant IS standard where you are confident, else 'specification to confirm'), quantity, unit, estimated rate if known (otherwise blank), justification; with approval sign-off lines"),
        // ISO 45001 & BOCW reports, safety committee, labour camp
        DocType("iso_mr", "ISO 45001 & BOCW reports", "ISO 45001 management review report (clause 9.3)", "9", listOf(PERIOD, DETAILS),
            "an ISO 45001:2018 management review report structured on the clause 9.3 inputs (status of previous actions, changes in issues and legal requirements, OH&S policy and objectives performance, incidents, nonconformities and corrective actions, monitoring results, compliance evaluation, audit results, consultation and participation of workers, risks and opportunities, resources) and outputs (decisions, actions, owners, dates). Use only the register data for figures", register = true),
        DocType("iso_audit", "ISO 45001 & BOCW reports", "ISO 45001 internal audit checklist & report (clause 9.2)", "9",
            listOf(Field("scope", "Audit scope / clauses", hint = "e.g. clauses 5–10 at Tower B site"), Field("auditees", "Auditees / departments"), DETAILS),
            "an ISO 45001:2018 internal audit checklist mapped to the clauses in scope (clause | requirement summary | audit question | evidence to look for | conformity C/NC/OFI | remarks), followed by an audit report format with findings classification and corrective action table"),
        DocType("iso_legal", "ISO 45001 & BOCW reports", "Evaluation of compliance (clause 9.1.2)", "3, 9",
            listOf(Field("scope", "Legal areas", hint = "e.g. BOCW Act & Rules, Factories Act (batching plant), fire, environment, CLRA"), Field("workers", "Approx. workers on site")),
            "an evaluation of compliance register for ISO 45001 clause 9.1.2: legal or other requirement | source with link | applicability | evidence checked | compliance status | action. Verify each requirement online and mark anything not verified", web = true),
        DocType("iso_obj", "ISO 45001 & BOCW reports", "OH&S objectives & performance report (clauses 6.2, 9.1)", "9",
            listOf(PERIOD, Field("objectives", "Objectives and targets", 4, hint = "e.g. zero fatal incidents; close 90% of findings within target date; 100% induction")),
            "an OH&S objectives and performance report: objective | target | measure | actual (from register data only) | status | action. Mark any measure not available in the data as 'not recorded'", register = true),
        DocType("iso_nc", "ISO 45001 & BOCW reports", "Nonconformity & corrective action report (clause 10.2)", "6, 10",
            listOf(Field("nc", "Nonconformity / incident", 4), LOC, Field("source", "Source", options = listOf("Internal audit", "Inspection", "Incident", "Worker report", "Client / external audit"))),
            "an ISO 45001 clause 10.2 nonconformity and corrective action report: description, immediate correction, root cause analysis (5 Whys), corrective actions with owners and dates, effectiveness check, and updates needed to the HIRA or procedures"),
        DocType("bocw_status", "ISO 45001 & BOCW reports", "BOCW compliance status report", "3, 9",
            listOf(PERIOD, Field("workers", "Workers on site"), Field("status", "Current status notes", 5, hint = "registration, cess paid up to, worker registration with Board, welfare facilities, safety officer, medical checks…")),
            "a BOCW Act 1996 and Central/Maharashtra Rules compliance status report for management: each requirement with legal source and link, status from the notes, evidence, gaps and actions. Verify current requirements and Maharashtra notifications online; never invent rule or form numbers", web = true, register = true),
        DocType("bocw_registers", "ISO 45001 & BOCW reports", "BOCW registers, forms & returns checklist", "3",
            listOf(Field("workers", "Approx. workers on site"), Field("state", "State", options = listOf("Maharashtra", "Other (mention in notes)")), DETAILS),
            "a checklist of the registers, records, notices, forms and returns a building contractor must maintain under the BOCW Act 1996 and the applicable Rules: name, legal source with link, who maintains it, frequency, where kept. Verify each one online and mark anything not verified", web = true),
        DocType("committee", "ISO 45001 & BOCW reports", "Safety committee formation", "3, 4K",
            listOf(Field("workers", "Workers on site"), Field("members", "Proposed members (name – designation)", 5), Field("frequency", "Meeting frequency", options = listOf("Monthly", "Fortnightly", "Quarterly"))),
            "a safety committee formation pack: (1) applicability and composition requirements with their legal source (BOCW Rules and any Maharashtra rules), verified online; (2) constitution order / office memo; (3) members table with roles, equal management and worker representation where required; (4) terms of reference and duties; (5) meeting procedure and standing agenda; (6) display notice for workers. Never invent the worker threshold or rule number", web = true),
        DocType("camp_check", "ISO 45001 & BOCW reports", "Labour camp inspection checklist", "4",
            listOf(Field("camp", "Camp / block"), Field("occupants", "Occupants"), CONTRACTOR),
            "a labour camp inspection checklist: accommodation (space, ventilation, lighting, bedding), drinking water (quality testing, storage, cooling), toilets and bathing (ratio, cleanliness, separate for women), canteen/kitchen hygiene, crèche, first aid, fire safety, electrical safety, drainage and waste, pest/vector control, security and emergency contacts. Columns: Item | Requirement | Yes/No/NA | Remarks | Action"),
        DocType("camp_docs", "ISO 45001 & BOCW reports", "Labour camp documents & welfare compliance", "3",
            listOf(Field("camp", "Camp / site"), Field("occupants", "Occupants"), Field("state", "State", options = listOf("Maharashtra", "Other (mention in notes)")), DETAILS),
            "a labour camp documents pack: the welfare facilities required (housing, drinking water, latrines and urinals, canteen, crèche, first aid) with legal source and link, the records to keep (occupancy register, water test reports, pest control, fire extinguisher checks, medical camp records, cleaning schedules), and a camp rules notice in English and Hindi. Verify requirements online and never invent numbers or rule references", web = true),
        // Compliance research — Section 3
        DocType("legal", "Compliance & legal", "Legal / compliance question (web research)", "3",
            listOf(Field("question", "Question", 4, hint = "e.g. Is a safety committee mandatory for 350 workers in Maharashtra? What registers are required under BOCW?")),
            "a researched answer following Section 3 and the Section 12 response format: mandatory requirements separated from good practice, each source with title, link and date, Maharashtra-specific points, and what could not be verified", web = true),
        DocType("audit", "Compliance & legal", "Statutory compliance checklist", "3, 9", listOf(Field("scope", "Scope", hint = "e.g. BOCW, CLRA, fire, lifting equipment, environment"), Field("workers", "Approx. workers on site")),
            "a statutory compliance checklist for a high-rise site in Maharashtra: requirement, legal source with link, applicability conditions, evidence/record to keep, status column to fill. Verify currency and Maharashtra notifications online", web = true),
    )

    fun byId(id: String) = ALL.first { it.id == id }

    /** Findings and incidents for the chosen period, as compact text for the prompt. */
    fun registerData(store: Store, period: String, contractor: String = ""): String {
        val t = Store.today()
        val from = when (period) { "Today" -> t; "Last 7 days" -> Store.addDays(t, -6); "This month" -> t.substring(0, 8) + "01"; "Last 30 days" -> Store.addDays(t, -29); else -> "0000" }
        fun inP(r: JSONObject) = r.optString("date") >= from
        fun byC(r: JSONObject) = contractor.isBlank() || r.optString("responsible").contains(contractor, true) || r.optString("contractor").contains(contractor, true)
        val f = store.list("finding").filter { (inP(it) || !it.optString("status").startsWith("Closed")) && byC(it) }
        val inc = store.list("incident").filter { inP(it) && byC(it) }
        val sb = StringBuilder("REGISTER DATA from the Safety Desk app (period ${Store.fmt(from.takeIf { it != "0000" } ?: "all")} to ${Store.fmt(t)}; open findings from earlier dates are included). This is the only data source; do not add figures that are not here.\n")
        sb.append("Findings (${f.size}): ID | date | location | category | condition | L×S=risk | responsible | target | status\n")
        f.forEach { sb.append("${it.optString("id")} | ${Store.fmt(it.optString("date"))} | ${listOf(it.optString("tower"), it.optString("floor"), it.optString("location")).filter { s -> s.isNotBlank() }.joinToString(" ")} | ${it.optString("category")} | ${it.optString("condition")} | ${it.optInt("L", 3)}×${it.optInt("S", 3)}=${it.optInt("L", 3) * it.optInt("S", 3)} | ${it.optString("responsible")} | ${Store.fmt(it.optString("target"))} | ${it.optString("status")}\n") }
        sb.append("Incidents (${inc.size}): ID | date | type | location | description | status\n")
        inc.forEach { sb.append("${it.optString("id")} | ${Store.fmt(it.optString("date"))} | ${it.optString("type")} | ${it.optString("location")} | ${it.optString("description")} | ${it.optString("status")}\n") }
        store.pref("manhours").takeIf { it.isNotBlank() }?.let { sb.append("Cumulative safe man-hours as entered in settings: $it\n") }
        return sb.toString()
    }

    fun prompt(store: Store, d: DocType, v: Map<String, String>): String {
        val inputs = d.fields.joinToString("\n") { f -> "- ${f.label}: ${v[f.key].orEmpty().ifBlank { "(not provided)" }}" }
        val reg = if (d.register) "\n\n" + registerData(store, v["period"] ?: "Last 30 days", v["contractor"] ?: "") else ""
        return "Prepare a ${d.title} for the Safety Manager. Follow the Master Operating Instructions, especially Section ${d.section}.\n" +
            "Produce ${d.ask}.\nUse Indian English, DD-MM-YYYY dates and metric units. Where information is missing, list it under 'Information needed' instead of inventing it.\n\n" +
            "Inputs:\n$inputs$reg"
    }
}
