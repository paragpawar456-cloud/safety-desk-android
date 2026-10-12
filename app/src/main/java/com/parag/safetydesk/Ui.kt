package com.parag.safetydesk

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

/** Colours and small view builders so screens can be written without XML layouts. */
object C {
    const val INK = 0xFF16202E.toInt()
    const val MUTED = 0xFF5E6B7A.toInt()
    const val BG = 0xFFF2F4F8.toInt()
    const val PANEL = 0xFFFFFFFF.toInt()
    const val LINE = 0xFFE1E6EC.toInt()
    const val LINE_DARK = 0xFFB4BDC8.toInt()
    const val NAVY = 0xFF0D2A4A.toInt()
    const val NAVY2 = 0xFF1B4F86.toInt()
    const val ACCENT = 0xFFFFB300.toInt()
    const val ACCENT_DARK = 0xFFE08600.toInt()
    const val ACCENT_SOFT = 0xFFFFF4D6.toInt()
    const val BLUE = 0xFF1E6FD9.toInt()
    const val TEAL = 0xFF00897B.toInt()
    const val PURPLE = 0xFF6A4BC4.toInt()
    const val LOW = 0xFF2E8B57.toInt()
    const val MED = 0xFFB67D00.toInt()
    const val HIGH = 0xFFD35F17.toInt()
    const val CRIT = 0xFFC62828.toInt()

    fun sev(s: String) = when (s) {
        "Critical" -> CRIT; "High" -> HIGH; "Medium" -> MED; else -> LOW
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

fun rounded(color: Int, radius: Float, stroke: Int = 0, strokeColor: Int = C.LINE) = GradientDrawable().apply {
    setColor(color); cornerRadius = radius
    if (stroke > 0) setStroke(stroke, strokeColor)
}

fun Context.col(pad: Int = 0, vararg kids: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    kids.forEach { addView(it) }
}

fun Context.row(vararg kids: View): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    kids.forEach { addView(it) }
}

fun Context.text(s: CharSequence, size: Float = 15f, color: Int = C.INK, bold: Boolean = false): TextView = TextView(this).apply {
    text = s; textSize = size; setTextColor(color)
    if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    setLineSpacing(0f, 1.15f)
}

fun Context.h1(s: String) = text(s, 22f, C.INK, true).apply { setPadding(0, dp(4), 0, dp(8)) }
fun Context.h2(s: String) = text(s, 17f, C.NAVY, true).apply { setPadding(0, dp(14), 0, dp(6)) }
fun Context.muted(s: String) = text(s, 13f, C.MUTED)

fun Context.button(label: String, primary: Boolean = false, onClick: () -> Unit): Button = Button(this).apply {
    text = label; isAllCaps = false; textSize = 14f
    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    setTextColor(if (primary) C.NAVY else C.NAVY2)
    background = if (primary) gradient(C.ACCENT, 0xFFFFC94D.toInt(), dp(12).toFloat()) else rounded(C.PANEL, dp(12).toFloat(), dp(1), C.LINE_DARK)
    setPadding(dp(14), dp(10), dp(14), dp(10))
    minHeight = dp(46); minimumHeight = dp(46)
    stateListAnimator = null
    elevation = if (primary) dp(2).toFloat() else 0f
    setOnClickListener { onClick() }
    pressable()
}

fun Context.input(hint: String, value: String = "", lines: Int = 1, number: Boolean = false): EditText = EditText(this).apply {
    this.hint = hint; setText(value); textSize = 15f; setTextColor(C.INK); setHintTextColor(C.MUTED)
    background = rounded(C.PANEL, dp(12).toFloat(), dp(1))
    setPadding(dp(14), dp(12), dp(14), dp(12))
    if (lines > 1) {
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        minLines = lines; gravity = Gravity.TOP or Gravity.START
    } else if (number) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
    else inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
}

fun Context.spinner(options: List<String>, selected: String? = null): Spinner = Spinner(this).apply {
    adapter = ArrayAdapter(this@spinner, android.R.layout.simple_spinner_dropdown_item, options)
    val i = options.indexOf(selected); if (i >= 0) setSelection(i)
    background = rounded(C.PANEL, dp(12).toFloat(), dp(1))
    setPadding(dp(6), dp(8), dp(6), dp(8))
}

fun Context.label(s: String) = text(s.uppercase(), 11f, C.MUTED, true).apply { letterSpacing = 0.06f; setPadding(0, dp(10), 0, dp(4)) }

fun Context.card(vararg kids: View): LinearLayout = col(16, *kids).apply {
    background = rounded(C.PANEL, dp(16).toFloat())
    elevation = dp(2).toFloat()
}

fun Context.chip(s: String, color: Int): TextView = text(s, 11f, color, true).apply {
    background = rounded(Color.argb(30, Color.red(color), Color.green(color), Color.blue(color)), dp(10).toFloat())
    setPadding(dp(9), dp(3), dp(9), dp(3))
}

fun Context.spacer(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

fun View.margins(ctx: Context, top: Int = 0, bottom: Int = 8, start: Int = 0, end: Int = 0, weight: Float = 0f, width: Int = ViewGroup.LayoutParams.MATCH_PARENT): View {
    layoutParams = LinearLayout.LayoutParams(if (weight > 0f) 0 else width, ViewGroup.LayoutParams.WRAP_CONTENT, weight).apply {
        setMargins(ctx.dp(start), ctx.dp(top), ctx.dp(end), ctx.dp(bottom))
    }
    return this
}

/** A labelled form: keeps references to its inputs so values can be read back. */
class Form(private val ctx: Context) {
    val view: LinearLayout = ctx.col(0)
    private val fields = linkedMapOf<String, View>()

    fun text(key: String, label: String, value: String = "", lines: Int = 1, hint: String = "", number: Boolean = false): Form {
        view.addView(ctx.label(label))
        val e = ctx.input(hint, value, lines, number); fields[key] = e; view.addView(e); return this
    }

    fun choice(key: String, label: String, options: List<String>, value: String? = null): Form {
        view.addView(ctx.label(label))
        val s = ctx.spinner(options, value); fields[key] = s; view.addView(s); return this
    }

    fun field(key: String) = fields[key] as android.widget.EditText

    operator fun get(key: String): String = when (val v = fields[key]) {
        is EditText -> v.text.toString().trim()
        is Spinner -> v.selectedItem?.toString() ?: ""
        else -> ""
    }

    fun set(key: String, value: String) {
        when (val v = fields[key]) {
            is EditText -> v.setText(value)
            is Spinner -> { @Suppress("UNCHECKED_CAST") val a = v.adapter as ArrayAdapter<String>; val i = a.getPosition(value); if (i >= 0) v.setSelection(i) }
        }
    }

    fun values(): Map<String, String> = fields.keys.associateWith { get(it) }
}
