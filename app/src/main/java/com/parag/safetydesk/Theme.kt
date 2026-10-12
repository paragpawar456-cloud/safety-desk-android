package com.parag.safetydesk

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Construction-site look: navy and safety amber, ISO 7010-style signs, hazard tape. */

/** Black-and-amber diagonal hazard tape. */
class HazardDrawable(private val band: Float) : Drawable() {
    private val amber = Paint().apply { color = C.ACCENT }
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B1B1B.toInt() }
    override fun draw(c: Canvas) {
        val b = bounds; c.drawRect(b, amber)
        val h = b.height().toFloat()
        var x = b.left - h
        while (x < b.right + h) {
            c.drawPath(Path().apply { moveTo(x, b.bottom.toFloat()); lineTo(x + h, b.top.toFloat()); lineTo(x + h + band, b.top.toFloat()); lineTo(x + band, b.bottom.toFloat()); close() }, black)
            x += band * 2
        }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.OPAQUE
}

/** ISO 7010-style sign shapes: warning triangle, mandatory blue disc, prohibition ring, safe-condition green, fire red. */
enum class Sign { WARNING, MANDATORY, PROHIBITION, SAFE, FIRE }

class SignDrawable(private val sign: Sign, private val stroke: Float) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(c: Canvas) {
        val b = RectF(bounds); val s = stroke
        when (sign) {
            Sign.WARNING -> {
                val path = Path().apply { moveTo(b.centerX(), b.top + s); lineTo(b.right - s, b.bottom - s); lineTo(b.left + s, b.bottom - s); close() }
                p.style = Paint.Style.FILL; p.color = 0xFFFFC400.toInt(); c.drawPath(path, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 1.6f; p.strokeJoin = Paint.Join.ROUND; p.color = 0xFF1B1B1B.toInt(); c.drawPath(path, p)
            }
            Sign.MANDATORY -> { p.style = Paint.Style.FILL; p.color = 0xFF1565C0.toInt(); c.drawOval(b, p) }
            Sign.PROHIBITION -> {
                p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawOval(b, p)
                p.style = Paint.Style.STROKE; p.strokeWidth = s * 2.2f; p.color = 0xFFD32F2F.toInt()
                val i = RectF(b.left + s * 1.1f, b.top + s * 1.1f, b.right - s * 1.1f, b.bottom - s * 1.1f); c.drawOval(i, p)
            }
            Sign.SAFE -> { p.style = Paint.Style.FILL; p.color = 0xFF2E7D32.toInt(); c.drawRoundRect(b, s * 2, s * 2, p) }
            Sign.FIRE -> { p.style = Paint.Style.FILL; p.color = 0xFFD32F2F.toInt(); c.drawRoundRect(b, s * 2, s * 2, p) }
        }
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** The red diagonal bar drawn over a prohibition sign's symbol. */
private class SlashDrawable(private val stroke: Float) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD32F2F.toInt(); style = Paint.Style.STROKE; strokeCap = Paint.Cap.BUTT }
    override fun draw(c: Canvas) {
        val b = RectF(bounds); p.strokeWidth = stroke * 2.2f
        val r = b.width() / 2 - stroke * 1.1f; val k = (r * 0.7071f)
        c.drawLine(b.centerX() - k, b.centerY() - k, b.centerX() + k, b.centerY() + k, p)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: ColorFilter?) {}
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** A safety sign with a symbol inside. */
fun Context.sign(sign: Sign, symbol: String, sizeDp: Int = 48): View {
    val f = FrameLayout(this)
    f.background = SignDrawable(sign, dp(2).toFloat())
    val t = TextView(this).apply {
        text = symbol; gravity = Gravity.CENTER
        textSize = sizeDp * if (sign == Sign.WARNING) 0.30f else 0.42f
        if (sign == Sign.WARNING) setPadding(0, dp(sizeDp / 5), 0, 0)
        if (sign == Sign.MANDATORY || sign == Sign.SAFE || sign == Sign.FIRE) setShadowLayer(0f, 0f, 0f, 0)
    }
    f.addView(t, FrameLayout.LayoutParams(dp(sizeDp), dp(sizeDp)))
    if (sign == Sign.PROHIBITION) f.foreground = SlashDrawable(dp(2).toFloat())
    f.layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    return f
}

/** A rounded, softly tinted badge holding an icon. */
fun Context.badge(icon: String, color: Int, sizeDp: Int = 44): TextView = TextView(this).apply {
    text = icon; gravity = Gravity.CENTER; textSize = sizeDp * 0.42f
    background = rounded(tint(color, 34), dp(sizeDp / 3).toFloat())
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
}

fun tint(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

fun gradient(from: Int, to: Int, radius: Float) = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(from, to)).apply { cornerRadius = radius }

/** Shrinks slightly while pressed, like a physical button. */
@SuppressLint("ClickableViewAccessibility")
fun View.pressable(): View {
    setOnTouchListener { v, e ->
        when (e.action) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).start()
        }
        false
    }
    return this
}

/** Slides and fades a view in; [order] staggers a list. */
fun View.enter(order: Int = 0): View {
    alpha = 0f; translationY = context.dp(18).toFloat()
    animate().alpha(1f).translationY(0f).setStartDelay(40L * order.coerceAtMost(12)).setDuration(320).setInterpolator(DecelerateInterpolator()).start()
    return this
}

/** A home-screen tile: tinted icon badge, title and a one-line description. */
fun Context.tile(icon: String, color: Int, title: String, sub: String, onClick: () -> Unit): View {
    val c = col(14, badge(icon, color), text(title, 15f, C.INK, true).apply { setPadding(0, dp(10), 0, dp(2)) }, text(sub, 12.5f, C.MUTED).apply { maxLines = 2 })
    c.background = rounded(C.PANEL, dp(16).toFloat())
    c.elevation = dp(2).toFloat()
    c.minimumHeight = dp(132)
    c.setOnClickListener { onClick() }
    return c.pressable()
}

/** Section heading with a small icon. */
fun Context.section(icon: String, title: String) = row(text(icon, 16f), text("  $title", 16f, C.INK, true)).apply { setPadding(0, dp(18), 0, dp(8)) }

/** A list row card with an icon badge on the left. */
fun Context.iconCard(icon: String, color: Int, title: String, sub: String, extra: View? = null): LinearLayout {
    val body = col(0, text(title, 15f, C.INK, true), text(sub, 12.5f, C.MUTED).apply { setPadding(0, dp(2), 0, 0) })
    if (extra != null) body.addView(extra)
    val r = row(badge(icon, color, 40), body.margins(this, start = 12, bottom = 0, weight = 1f), text("›", 22f, C.LINE_DARK))
    r.setPadding(dp(14), dp(12), dp(14), dp(12))
    r.background = rounded(C.PANEL, dp(14).toFloat())
    r.elevation = dp(1).toFloat()
    r.pressable()
    return r
}

object Icons {
    /** Icon and colour for each screen title. */
    val SCREENS = mapOf(
        "Settings" to "⚙️", "Ask the agent" to "💬", "Photo check" to "📸", "WhatsApp import" to "📲", "Findings register" to "📋",
        "Incidents" to "🚑", "Prepare a document" to "📄", "Saved documents" to "🗂️", "Escalation" to "📣", "Trackers" to "📊",
    )

    /** Icon for each document group in the catalogue. */
    fun group(g: String) = when {
        g.startsWith("Inspections") -> "🔍" to C.BLUE
        g.startsWith("Permits") -> "🦺" to C.ACCENT_DARK
        g.startsWith("Training") -> "🎓" to C.PURPLE
        g.startsWith("Reports") -> "📈" to C.TEAL
        g.startsWith("Emergency") -> "🚨" to C.CRIT
        g.startsWith("Incidents") -> "🚑" to C.CRIT
        g.startsWith("Contractor") -> "👷" to C.ACCENT_DARK
        g.startsWith("Communication") -> "✉️" to C.BLUE
        g.startsWith("ISO") -> "🏅" to C.TEAL
        g.startsWith("Compliance") -> "⚖️" to C.PURPLE
        else -> "📄" to C.BLUE
    }

    /** Icon and colour for each tracker. */
    fun tracker(id: String) = when (id) {
        "fall" -> "🪢" to C.ACCENT_DARK
        "equip" -> "🏗️" to C.BLUE
        "tpi" -> "🔖" to C.PURPLE
        "env" -> "🌳" to C.LOW
        "air" -> "🌫️" to C.TEAL
        "committee" -> "🤝" to C.BLUE
        "camp" -> "🏕️" to C.ACCENT_DARK
        "bocw" -> "⚖️" to C.PURPLE
        else -> "📊" to C.BLUE
    }

    /** Rotating safety messages, each with the sign that goes with it. */
    val TIPS = listOf(
        Triple(Sign.MANDATORY, "⛑️", "Helmet with chin strap fastened at all times on site."),
        Triple(Sign.MANDATORY, "🦺", "Full-body harness with double lanyard, anchored at all times when working at height."),
        Triple(Sign.WARNING, "⚡", "Isolate and lock out before any work on panels, DBs or cables."),
        Triple(Sign.WARNING, "🏗️", "Stay out of the crane swing radius and never stand under a suspended load."),
        Triple(Sign.PROHIBITION, "🚬", "No smoking near hot work, fuel stores or flammable material."),
        Triple(Sign.FIRE, "🧯", "Keep a charged extinguisher within reach of every hot-work job."),
        Triple(Sign.SAFE, "➕", "Know your nearest first-aid box, first-aider and assembly point."),
        Triple(Sign.WARNING, "🕳️", "Cover, barricade and mark every floor opening and shaft."),
        Triple(Sign.MANDATORY, "🥾", "Safety shoes on site: nails and falling objects don't give warnings."),
        Triple(Sign.PROHIBITION, "📵", "No phone use while operating machinery or working at an edge."),
        Triple(Sign.MANDATORY, "🥽", "Goggles and face shield for grinding, cutting and chipping."),
        Triple(Sign.WARNING, "🧱", "Never overload slabs or scaffolds with stacked material."),
    )
}
