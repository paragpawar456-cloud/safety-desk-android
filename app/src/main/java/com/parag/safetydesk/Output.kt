package com.parag.safetydesk

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import android.widget.LinearLayout
import org.json.JSONObject

/**
 * Shows a streamed Claude answer rendered from Markdown, with Copy / Share / Save / PDF actions.
 * Re-attaching after a screen rebuild keeps the latest text.
 */
class Output(private val a: MainActivity, private val title: () -> String) {
    var markdown = ""; private set
    var busy = false; private set
    private var error = ""
    private var lastPaint = 0L
    private var web: WebView? = null
    private var status: android.widget.TextView? = null
    private var actions: View? = null
    var onSaved: ((JSONObject) -> Unit)? = null
    var docKind = "Document"

    @SuppressLint("SetJavaScriptEnabled")
    fun view(): View {
        val st = a.muted("").apply { setPadding(0, a.dp(6), 0, a.dp(6)) }
        val wv = WebView(a).apply { settings.javaScriptEnabled = false; setBackgroundColor(C.PANEL) }
        val bar = a.col(0,
            a.row(
                a.button("Copy") { a.copy(markdown) }.margins(a, end = 6, weight = 1f),
                a.button("Share") { a.share(title(), markdown) }.margins(a, end = 6, weight = 1f),
                a.button("PDF / Print") { a.printHtml(title(), page()) }.margins(a, weight = 1f),
            ),
            a.row(
                a.button("Save to Downloads") { a.saveDownload(fileName(), "text/html", page()) }.margins(a, end = 6, weight = 1f),
                a.button("Keep in app", primary = true) { keep() }.margins(a, weight = 1f),
            ),
        )
        status = st; web = wv; actions = bar
        val box = a.col(0, st, wv.margins(a, bottom = 8), bar)
        paint(force = true)
        return box
    }

    private fun fileName() = title().replace(Regex("[^A-Za-z0-9]+"), "-").trim('-').take(60) + "-" + Store.today() + ".html"
    fun page() = Md.page(title(), Md.html(markdown), "Prepared with Safety Desk on ${Store.fmt(Store.today())}. AI-assisted draft: verify facts, legal references and figures before issuing.")

    private fun keep() {
        if (markdown.isBlank()) return
        val r = a.store.save(JSONObject().put("kind", "doc").put("date", Store.today()).put("title", title()).put("type", docKind).put("body", markdown))
        a.toast("Saved as ${r.optString("id")} in Documents")
        onSaved?.invoke(r)
    }

    private fun paint(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPaint < 400) return
        lastPaint = now
        status?.text = when {
            error.isNotEmpty() -> error
            busy && markdown.isEmpty() -> "Claude is thinking…"
            busy -> "Writing…"
            else -> ""
        }
        status?.setTextColor(if (error.isNotEmpty()) C.CRIT else C.MUTED)
        status?.visibility = if (status?.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        web?.visibility = if (markdown.isEmpty()) View.GONE else View.VISIBLE
        actions?.visibility = if (markdown.isNotEmpty() && !busy) View.VISIBLE else View.GONE
        if (markdown.isNotEmpty()) web?.loadDataWithBaseURL(null, Md.page(title(), Md.html(markdown)), "text/html", "utf-8", null)
    }

    fun set(md: String) { markdown = md; error = ""; paint(force = true) }

    /** Runs a request; returns false if one is already running. */
    fun run(prompt: String, web: Boolean = false, images: List<ByteArray> = emptyList(),
            effort: Effort = Effort.MEDIUM, done: (String) -> Unit = {}): Boolean {
        if (busy) return false
        busy = true; error = ""; markdown = ""; paint(force = true)
        a.bg({ a.ai.ask(prompt, web, images, effort) { t -> a.ui { markdown = t; paint() } } }) { r ->
            busy = false
            r.onSuccess { markdown = it; paint(force = true); done(it) }
             .onFailure { error = it.message ?: "Something went wrong."; paint(force = true) }
        }
        return true
    }
}

fun LinearLayout.addAll(vararg v: View) = v.forEach { addView(it) }
