package com.example.jarvis

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.Contents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private lateinit var transcript: TextView
    private lateinit var status: TextView
    private lateinit var input: EditText
    private lateinit var tts: TextToSpeech
    private val db by lazy { ChatStore(this) }
    private val engineDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var ttsReady = false

    private val modelPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importModel(uri)
    }

    private val voicePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input.setText(spoken)
                sendMessage(spoken)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tts = TextToSpeech(this, this)
        buildScreen()
        transcript.text = db.readAll().ifBlank {
            "JARVIS CORE // OFFLINE MODE\n\nWelcome. Import a compatible .litertlm model to activate the local AI brain.\n\nNo conversation is sent to a cloud AI service by this starter."
        }
        refreshModelStatus()
    }

    private fun buildScreen() {
        val bg = Color.rgb(5, 13, 25)
        val cyan = Color.rgb(92, 220, 255)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 20)
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S."
            textSize = 28f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            letterSpacing = 0.22f
        }
        status = TextView(this).apply {
            text = "● LOCAL CORE STANDBY"
            textSize = 12f
            setTextColor(Color.rgb(154, 190, 211))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 18)
        }
        transcript = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.rgb(226, 244, 255))
            setPadding(14, 14, 14, 14)
            setBackgroundColor(Color.rgb(12, 29, 47))
        }
        val scroll = ScrollView(this).apply { addView(transcript) }
        input = EditText(this).apply {
            hint = "Talk to JARVIS..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(15, 35, 54))
            setPadding(14, 10, 14, 10)
            minLines = 2
        }
        val modelButton = makeButton("IMPORT LOCAL AI MODEL") { modelPicker.launch(arrayOf("*/*")) }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(makeButton("SEND") { sendMessage(input.text.toString()) }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(makeButton("🎙 VOICE") { startVoiceInput() }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(makeButton("SPEAK") { speakLast() }, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(title)
        root.addView(status)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(input, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 12 })
        root.addView(row)
        root.addView(modelButton)
        setContentView(root)
    }

    private fun makeButton(label: String, action: () -> Unit): Button =
        Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setOnClickListener { action() }
        }

    private fun appendLine(who: String, text: String) {
        runOnUiThread {
            transcript.append("\n\n$who\n$text")
            (transcript.parent as? ScrollView)?.post { (transcript.parent as ScrollView).fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun refreshModelStatus() {
        val file = File(filesDir, "models/jarvis-model.litertlm")
        status.text = if (file.exists()) "● MODEL FOUND // INITIALIZING LOCALLY" else "● LOCAL CORE STANDBY // IMPORT MODEL"
        if (file.exists()) initializeModel(file)
    }

    private fun importModel(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val dir = File(filesDir, "models").apply { mkdirs() }
                val target = File(dir, "jarvis-model.litertlm")
                contentResolver.openInputStream(uri).use { stream ->
                    requireNotNull(stream) { "Cannot read selected model file." }.use { source ->
                        target.outputStream().use { output -> source.copyTo(output) }
                    }
                }
                withContext(Dispatchers.Main) {
                    status.text = "● MODEL IMPORTED // LOADING"
                    appendLine("SYSTEM", "Model copied to private app storage. Initializing on-device engine...")
                    initializeModel(target)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { appendLine("SYSTEM ERROR", e.message ?: "Model import failed.") }
            }
        }
    }

    private fun initializeModel(modelFile: File) {
        lifecycleScope.launch {
            status.text = "● LOADING LOCAL MODEL..."
            try {
                withContext(engineDispatcher) {
                    var loaded: Engine
                    try {
                        loaded = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.GPU(), cacheDir = cacheDir.absolutePath))
                        loaded.initialize()
                    } catch (_: Exception) {
                        loaded = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = Backend.CPU(), cacheDir = cacheDir.absolutePath))
                        loaded.initialize()
                    }
                    engine = loaded
                    conversation = loaded.createConversation(
                        ConversationConfig(systemInstruction = Contents.of(
                            "You are JARVIS, a helpful personal AI assistant running entirely on this Android phone. " +
                            "Be concise, capable, honest, and never claim to have performed a phone action unless the app confirms it. " +
                            "Ask for confirmation before consequential actions."
                        ))
                    )
                }
                status.text = "● JARVIS ONLINE // ON-DEVICE AI"
                appendLine("JARVIS", "Local AI core initialized. I am ready.")
            } catch (e: Exception) {
                status.text = "● MODEL LOAD FAILED"
                appendLine("SYSTEM ERROR", "${e.javaClass.simpleName}: ${e.message}\nCheck that the selected file is a compatible LiteRT-LM .litertlm model.")
            }
        }
    }

    private fun sendMessage(raw: String) {
        val prompt = raw.trim()
        if (prompt.isEmpty()) return
        input.setText("")
        appendLine("YOU", prompt)
        db.add("YOU", prompt)
        lifecycleScope.launch {
            try {
                val answer = withContext(engineDispatcher) {
                    val conv = conversation ?: error("Import a compatible .litertlm model first.")
                    val output = StringBuilder()
                    conv.sendMessageAsync(prompt).collect { message: Message -> output.append(message.text) }
                    output.toString().ifBlank { "I didn't receive a text response. Please try again." }
                }
                appendLine("JARVIS", answer)
                db.add("JARVIS", answer)
                lastReply = answer
            } catch (e: Exception) {
                appendLine("SYSTEM", e.message ?: "Unable to generate a response.")
            }
        }
    }

    private var lastReply = ""

    private fun startVoiceInput() {
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
            }
            voicePicker.launch(intent)
        } catch (_: Exception) {
            appendLine("SYSTEM", "Speech recognition service is not available on this device. Text chat still works.")
        }
    }

    private fun speakLast() {
        if (lastReply.isNotBlank() && ttsReady) tts.speak(lastReply, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
    }

    override fun onInit(statusCode: Int) {
        ttsReady = statusCode == TextToSpeech.SUCCESS
        if (ttsReady) tts.language = Locale("en", "IN")
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
        lifecycleScope.launch {
            withContext(engineDispatcher) {
                try { conversation?.close() } catch (_: Exception) {}
                try { engine?.close() } catch (_: Exception) {}
                conversation = null
                engine = null
            }
            engineDispatcher.close()
        }
    }
}
