package com.parag.safetydesk

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream

/** A full-screen page. Screens are kept on a stack; Back returns to the previous one. */
abstract class Screen(val a: MainActivity) {
    abstract val title: String
    /** Builds the content. Called each time the screen is shown. */
    abstract fun build(): View
    open fun onImage(bytes: ByteArray) {}
    open fun onTextFile(text: String) {}
    /** Scrollable screens wrap their content; chat-style screens manage their own scrolling. */
    open val scrolls = true
    /** Emoji shown before the title in the top bar; blank uses [Icons.SCREENS]. */
    open val icon: String = ""
}

class MainActivity : Activity() {
    lateinit var store: Store
    lateinit var ai: Ai
    private val stack = ArrayDeque<Screen>()
    private lateinit var titleView: TextView
    private lateinit var iconView: TextView
    private lateinit var backView: TextView
    private lateinit var content: FrameLayout
    private val pool = Executors.newCachedThreadPool()
    private var cameraUri: Uri? = null
    private var printView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = Store(this); ai = Ai(this, store)
        window.statusBarColor = C.NAVY
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        backView = text("‹", 30f, 0xFFFFFFFF.toInt(), true).apply {
            setPadding(dp(14), 0, dp(10), dp(4)); setOnClickListener { goBack() }
        }
        iconView = text("⛑️", 22f).apply { setPadding(dp(12), 0, dp(10), 0) }
        titleView = text("Safety Desk", 19f, 0xFFFFFFFF.toInt(), true).apply { maxLines = 2 }
        val stripe = View(this).apply {
            background = HazardDrawable(dp(10).toFloat())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(6))
        }
        val bar = row(backView, iconView, titleView).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(C.NAVY, C.NAVY2))
            setPadding(dp(4), dp(14), dp(16), dp(14)); fitsSystemWindows = true
        }
        content = FrameLayout(this).apply { setBackgroundColor(C.BG) }
        val root = col(0, bar, stripe, content).apply { fitsSystemWindows = true; setBackgroundColor(C.NAVY) }
        content.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        setContentView(root)

        show(HomeScreen(this))
        if (store.apiKey.isBlank() && store.geminiKey.isBlank()) show(SettingsScreen(this, firstRun = true))
        handleShare(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleShare(intent) }

    // ---------- navigation ----------
    fun show(s: Screen) { stack.addLast(s); render() }
    fun replace(s: Screen) { stack.removeLastOrNull(); show(s) }
    fun refresh() = render()

    private fun render() {
        val s = stack.last()
        titleView.text = s.title
        iconView.text = s.icon.ifEmpty { Icons.SCREENS[s.title] ?: "⛑️" }
        backView.visibility = if (stack.size > 1) View.VISIBLE else View.GONE
        iconView.setPadding(dp(if (stack.size > 1) 0 else 12), 0, dp(10), 0)
        content.removeAllViews()
        val v = s.build()
        if (s.scrolls) {
            val sv = ScrollView(this).apply { isFillViewport = true }
            sv.addView(v); content.addView(sv)
        } else content.addView(v)
        v.alpha = 0f; v.animate().alpha(1f).setDuration(220).start()
    }

    fun goBack() { if (stack.size > 1) { stack.removeLast(); render() } else finish() }
    @Deprecated("Deprecated in Java") override fun onBackPressed() { goBack() }

    // ---------- background work ----------
    fun <T> bg(work: () -> T, done: (Result<T>) -> Unit) {
        pool.execute { val r = runCatching(work); runOnUiThread { if (!isFinishing) done(r) } }
    }
    fun ui(f: () -> Unit) = runOnUiThread(f)
    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    // ---------- photos & files ----------
    fun pickImage() = startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_PICK)

    fun takePhoto() {
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "safety_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SafetyDesk")
        }
        cameraUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
        try { startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, cameraUri), REQ_CAMERA) }
        catch (e: Exception) { toast("No camera app found. Choose a photo from the gallery instead.") }
    }

    fun pickTextFile() = startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("*/*")
        .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/zip")), REQ_TEXT)

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = if (requestCode == REQ_CAMERA) cameraUri else data?.data
        uri ?: return
        when (requestCode) {
            REQ_PICK, REQ_CAMERA -> bg({ loadImage(uri) }) { r -> r.onSuccess { stack.last().onImage(it) }.onFailure { toast("Could not read that photo.") } }
            REQ_TEXT -> bg({ readText(uri) }) { r -> r.onSuccess { stack.last().onTextFile(it) }.onFailure { toast("Could not read that file.") } }
        }
    }

    /** Loads, rotates upright and downsizes a photo to a JPEG suitable for Claude (long side ≤ 1568 px). */
    fun loadImage(uri: Uri): ByteArray {
        val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var sample = 1
        while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= 1568) sample *= 2
        var bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("not an image")
        val rot = try {
            when (ExifInterface(bytes.inputStream()).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f; ExifInterface.ORIENTATION_ROTATE_180 -> 180f; ExifInterface.ORIENTATION_ROTATE_270 -> 270f; else -> 0f
            }
        } catch (e: Exception) { 0f }
        val scale = minOf(1f, 1568f / maxOf(bmp.width, bmp.height))
        if (rot != 0f || scale < 1f) {
            val m = Matrix().apply { postScale(scale, scale); postRotate(rot) }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
    }

    /** Reads a .txt, or the chat .txt inside a WhatsApp export .zip. */
    fun readText(uri: Uri): String {
        val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        if (bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(bytes.inputStream()).use { z ->
                var e = z.nextEntry
                while (e != null) { if (e.name.endsWith(".txt")) return z.readBytes().toString(Charsets.UTF_8); e = z.nextEntry }
            }
            throw IllegalArgumentException("no chat text in zip")
        }
        return bytes.toString(Charsets.UTF_8)
    }

    /** Text or photos shared into the app from WhatsApp, the gallery or any other app. */
    private fun handleShare(i: Intent?) {
        if (i == null || (i.action != Intent.ACTION_SEND && i.action != Intent.ACTION_SEND_MULTIPLE)) return
        val type = i.type ?: ""
        @Suppress("DEPRECATION") val stream: Uri? = i.getParcelableExtra(Intent.EXTRA_STREAM)
        val txt = i.getStringExtra(Intent.EXTRA_TEXT)
        i.action = null
        when {
            type.startsWith("image/") && stream != null -> {
                val ps = PhotoScreen(this); show(ps); bg({ loadImage(stream) }) { r -> r.onSuccess { ps.onImage(it) } }
            }
            stream != null -> { val ws = WhatsAppScreen(this); show(ws); bg({ readText(stream) }) { r -> r.onSuccess { ws.onTextFile(it) }.onFailure { toast("Could not read the shared file.") } } }
            txt != null -> { val ws = WhatsAppScreen(this); show(ws); ws.onTextFile(txt) }
        }
    }

    // ---------- output actions ----------
    fun copy(text: String) {
        (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Safety Desk", text))
        toast("Copied")
    }

    /** Opens Android's share sheet (WhatsApp, Gmail, …). Nothing is sent until the user sends it. */
    fun share(subject: String, text: String) {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, text), "Share"))
    }

    fun email(to: String, subject: String, body: String) {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:")).putExtra(Intent.EXTRA_EMAIL, to.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
            .putExtra(Intent.EXTRA_SUBJECT, subject).putExtra(Intent.EXTRA_TEXT, body)
        try { startActivity(i) } catch (e: Exception) { share(subject, body) }
    }

    /** Saves a file into the phone's Downloads/SafetyDesk folder. */
    fun saveDownload(name: String, mime: String, data: String) {
        try {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name); put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/SafetyDesk")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)!!
            contentResolver.openOutputStream(uri)!!.use { it.write(data.toByteArray()) }
            toast("Saved to Downloads/SafetyDesk/$name")
        } catch (e: Exception) { toast("Could not save the file: ${e.message}") }
    }

    /** Opens the Android print dialog, where the user can print or choose "Save as PDF". */
    fun printHtml(job: String, html: String) {
        val wv = WebView(this); printView = wv
        wv.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                (getSystemService(Context.PRINT_SERVICE) as PrintManager)
                    .print(job, view.createPrintDocumentAdapter(job), PrintAttributes.Builder().setMediaSize(PrintAttributes.MediaSize.ISO_A4).build())
            }
        }
        wv.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    companion object {
        const val REQ_PICK = 11; const val REQ_CAMERA = 12; const val REQ_TEXT = 13
    }
}

/** Centered empty-state message. */
fun Context.empty(s: String): TextView = text(s, 14f, C.MUTED).apply {
    gravity = Gravity.CENTER; setPadding(dp(20), dp(28), dp(20), dp(28))
    background = rounded(C.PANEL, dp(10).toFloat(), dp(1))
}
