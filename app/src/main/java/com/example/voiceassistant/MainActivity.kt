package com.example.voiceassistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.telephony.PhoneNumberUtils
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var keyInput: EditText
    private lateinit var status: TextView
    private var recognizer: SpeechRecognizer? = null
    private val http = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    private val handler = Handler(Looper.getMainLooper())
    private val requestPermissions = arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("assistant_secure_prefs", MODE_PRIVATE)
        buildUi()
        val missing = requestPermissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) ActivityCompat.requestPermissions(this, missing.toTypedArray(), 100)
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 36, 28, 24) }
        val scroll = ScrollView(this)
        val title = TextView(this).apply { text = "Voice Assistant\nHindi • English"; textSize = 26f; setPadding(0, 0, 0, 22) }
        keyInput = EditText(this).apply {
            hint = "Gemini API key yahan paste karein"
            singleLine = true
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("gemini_key", ""))
        }
        val save = Button(this).apply { text = "Save API Key"; setOnClickListener {
            val key = keyInput.text.toString().trim()
            if (key.isBlank()) toast("API key daalein") else { prefs.edit().putString("gemini_key", key).apply(); toast("API key save ho gayi") }
        } }
        val speak = Button(this).apply { text = "🎙  Bolo"; textSize = 20f; setOnClickListener { startListening() } }
        val wakeOn = Button(this).apply { text = "Wake word ON: Hey Vyron"; setOnClickListener {
            if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                androidx.core.content.ContextCompat.startForegroundService(this@MainActivity, Intent(this@MainActivity, WakeWordService::class.java))
                setStatus("Hey Vyron wake word active (notification dikhega)")
            } else ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), 100)
        } }
        val wakeOff = Button(this).apply { text = "Wake word OFF"; setOnClickListener {
            stopService(Intent(this@MainActivity, WakeWordService::class.java)); setStatus("Wake word band hai")
        } }
        val access = Button(this).apply { text = "Accessibility Settings kholo"; setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        status = TextView(this).apply { text = "Status: Ready"; textSize = 16f; setPadding(0, 20, 0, 0) }
        root.addView(title); root.addView(keyInput); root.addView(save); root.addView(speak); root.addView(wakeOn); root.addView(wakeOff); root.addView(access); root.addView(status)
        scroll.addView(root); setContentView(scroll)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent?.getBooleanExtra("wake_word_detected", false) == true) {
            setStatus("Hey Vyron suna! Ab apna command boliye…")
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                handler.postDelayed({ startListening() }, 500)
            }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { toast("Speech recognition available nahi hai"); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, requestPermissions, 100); return
        }
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { setStatus("Sun raha hoon…") }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { setStatus("Samajh raha hoon…") }
                override fun onError(error: Int) { setStatus("Voice error: $error"); toast("Dobara Bolo dabayein") }
                override fun onResults(results: Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (text.isNullOrBlank()) setStatus("Awaaz samajh nahi aayi") else { setStatus("Aapne kaha: $text"); askGemini(text) }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            }
            sr.startListening(intent)
        }
    }

    private fun askGemini(command: String) {
        val key = prefs.getString("gemini_key", "").orEmpty()
        if (key.isBlank()) { setStatus("Pehle Gemini API key save karein"); return }
        setStatus("Gemini se jawab aa raha hai…")
        Thread {
            try {
                val system = "You are a Hindi-English Android voice assistant. Convert the user's command into ONLY a JSON array of actions. Allowed actions: {action:open_app,app:string}, {action:call,to:string}, {action:whatsapp,to:string,text:string}, {action:click,text:string}, {action:type,text:string}, {action:back}, {action:home}. Return [] if unclear. Never invent contact names or message text. Output valid JSON only."
                val body = JSONObject().put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
                    .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", command)))))
                    .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
                val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${URLEncoder.encode(key, "UTF-8")}"
                val req = Request.Builder().url(url).post(body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build()
                http.newCall(req).execute().use { response ->
                    if (!response.isSuccessful) throw IllegalStateException("Gemini HTTP ${response.code}: ${response.body?.string()?.take(300)}")
                    val json = JSONObject(response.body?.string().orEmpty())
                    val text = json.getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    val actions = JSONArray(text)
                    runOnUiThread {
                        setStatus("${actions.length()} action(s) mil gaye")
                        executeActions(actions, 0)
                    }
                }
            } catch (e: Exception) { runOnUiThread { setStatus("Error: ${e.message}") } }
        }.start()
    }

    private fun executeActions(actions: JSONArray, index: Int) {
        if (index >= actions.length()) { setStatus("Kaam poora hua"); return }
        try {
            val a = actions.getJSONObject(index)
            when (a.optString("action")) {
                "open_app" -> openApp(a.optString("app"))
                "call" -> callContact(a.optString("to"))
                "whatsapp" -> whatsappContact(a.optString("to"), a.optString("text"))
                "click" -> { val ok = AssistantService.instance?.clickText(a.optString("text")) ?: false; if (!ok) toast("Button nahi mila: ${a.optString("text")}") }
                "type" -> { val ok = AssistantService.instance?.typeText(a.optString("text")) ?: false; if (!ok) toast("Input box nahi mila") }
                "back" -> AssistantService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK) ?: toast("Accessibility ON karein")
                "home" -> AssistantService.instance?.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME) ?: toast("Accessibility ON karein")
                else -> toast("Unknown action skip kiya")
            }
        } catch (e: Exception) { toast("Action error: ${e.message}") }
        handler.postDelayed({ executeActions(actions, index + 1) }, 3000)
    }

    private fun openApp(label: String) {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val match = apps.firstOrNull { pm.getApplicationLabel(it).toString().equals(label, true) }
        val launch = match?.let { pm.getLaunchIntentForPackage(it.packageName) }
        if (launch != null) startActivity(launch) else toast("App nahi mila: $label")
    }

    private fun findNumber(name: String): String? {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) { toast("Contacts permission allow karein"); return null }
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val display = c.getString(1) ?: ""
                if (display.contains(name, true)) return c.getString(0)
            }
        }
        toast("Contact nahi mila: $name"); return null
    }

    private fun callContact(name: String) {
        val number = findNumber(name) ?: return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) { toast("Call permission allow karein"); return }
        AlertDialog.Builder(this)
            .setTitle("Call confirm karein")
            .setMessage("$name ko call lagani hai?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Call") { _, _ -> startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:${Uri.encode(number)}"))) }
            .show()
    }

    private fun whatsappContact(name: String, message: String) {
        val raw = findNumber(name) ?: return
        var digits = raw.filter { it.isDigit() }
        if (digits.length == 10) digits = "91$digits"
        val url = "https://wa.me/$digits?text=${URLEncoder.encode(message, "UTF-8")}"
        AlertDialog.Builder(this)
            .setTitle("WhatsApp message confirm karein")
            .setMessage("To: $name\nMessage: $message\n\nConfirm karne par WhatsApp khulega aur Send button dabane ki koshish hogi.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Confirm") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { setPackage("com.whatsapp") })
                    AssistantService.instance?.tapSendAfterDelay(3500)
                } catch (_: Exception) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) { toast("WhatsApp nahi mila") }
                }
            }
            .show()
    }

    private fun setStatus(value: String) { runOnUiThread { status.text = "Status: $value" } }
    private fun toast(value: String) { runOnUiThread { Toast.makeText(this, value, Toast.LENGTH_LONG).show() } }
    override fun onDestroy() { recognizer?.destroy(); super.onDestroy() }
}
