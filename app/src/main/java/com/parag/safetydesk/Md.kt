package com.parag.safetydesk

/** Minimal Markdown → HTML for Claude's answers (headings, lists, tables, bold, italics, code). */
object Md {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun inline(s: String): String = esc(s)
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<strong>$1</strong>")
        .replace(Regex("(^|[^*])\\*([^*\\n]+)\\*"), "$1<em>$2</em>")
        .replace(Regex("`([^`]+)`"), "<code>$1</code>")
        .replace(Regex("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)"), "<a href=\"$2\">$1</a>")

    fun html(src: String): String {
        val out = StringBuilder(); var list: String? = null; var table: MutableList<List<String>>? = null
        fun closeList() { list?.let { out.append("</$it>") }; list = null }
        fun closeTable() {
            table?.let { t ->
                out.append("<table>")
                t.forEachIndexed { i, r -> out.append("<tr>"); r.forEach { c -> out.append(if (i == 0) "<th>${inline(c)}</th>" else "<td>${inline(c)}</td>") }; out.append("</tr>") }
                out.append("</table>")
            }
            table = null
        }
        for (raw in src.replace("\r", "").split("\n")) {
            val l = raw.trim()
            if (l.startsWith("|") && l.endsWith("|") && l.length > 1) {
                closeList()
                val cells = l.substring(1, l.length - 1).split("|").map { it.trim() }
                if (cells.all { it.matches(Regex(":?-{2,}:?")) }) continue
                (table ?: mutableListOf<List<String>>().also { table = it }).add(cells); continue
            }
            closeTable()
            val h = Regex("^(#{1,4})\\s+(.*)").find(l)
            val ul = Regex("^[-*•]\\s+(.*)").find(l)
            val ol = Regex("^\\d+[.)]\\s+(.*)").find(l)
            when {
                h != null -> { closeList(); val n = minOf(h.groupValues[1].length, 4); out.append("<h$n>${inline(h.groupValues[2])}</h$n>") }
                ul != null -> { if (list != "ul") { closeList(); out.append("<ul>"); list = "ul" }; out.append("<li>${inline(ul.groupValues[1])}</li>") }
                ol != null -> { if (list != "ol") { closeList(); out.append("<ol>"); list = "ol" }; out.append("<li>${inline(ol.groupValues[1])}</li>") }
                l == "---" -> { closeList(); out.append("<hr>") }
                l.isEmpty() -> closeList()
                else -> { closeList(); out.append("<p>${inline(l)}</p>") }
            }
        }
        closeList(); closeTable()
        return out.toString()
    }

    fun page(title: String, body: String, footer: String = ""): String = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>${esc(title)}</title><style>
body{font-family:sans-serif;font-size:14px;line-height:1.5;color:#191c21;margin:14px;word-wrap:break-word}
h1{font-size:20px;border-bottom:3px solid #f0a500;padding-bottom:4px}h2{font-size:17px;margin-top:18px}h3,h4{font-size:15px}
table{border-collapse:collapse;width:100%;margin:8px 0;font-size:12.5px;display:block;overflow-x:auto}
th,td{border:1px solid #c9ced6;padding:5px 7px;text-align:left;vertical-align:top}th{background:#f1f2f4}
code{background:#f1f2f4;padding:1px 4px;border-radius:3px}ul,ol{padding-left:22px}a{color:#8a5a00}
.foot{color:#777;font-size:11px;margin-top:24px;border-top:1px solid #ddd;padding-top:6px}
@media print{table{display:table}body{margin:0}}
</style></head><body>$body${if (footer.isNotEmpty()) "<div class=\"foot\">${esc(footer)}</div>" else ""}</body></html>"""
}
