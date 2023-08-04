package com.example.aimobileapp

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.animation.Animation
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import android.Manifest

class AIChat : AppCompatActivity() {

    private val REQUEST_RECORD_AUDIO = 1

    private lateinit var adapter: MessageArrayAdapter
    private val listItems = ArrayList<ChatMessage>()
    private val client = OkHttpClient()
    private val conversationHistory = JSONArray()
    private lateinit var textView1: TextView
    private lateinit var textView2: TextView
    private lateinit var slideDown: Animation
    private lateinit var slideUp: Animation
    private var nextText: String? = null
    private var activeTextView: TextView? = null
    private lateinit var recognizer: SpeechRecognizer

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aichat)

        textView1 = findViewById(R.id.title)
        textView2 = findViewById(R.id.typing)

        val listView = findViewById<ListView>(R.id.messageList)
        adapter = MessageArrayAdapter(this, listItems)
        listView.adapter = adapter

        val button = findViewById<Button>(R.id.sendMessage)
        val editText = findViewById<EditText>(R.id.message)

        // Request permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }

        // Set up speech recognizer
        val narrateImageView = findViewById<ImageView>(R.id.narrate)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {}

            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) editText.setText(matches[0])
            }

            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
        recognizer.setRecognitionListener(listener)

        narrateImageView.setOnClickListener {
            recognizer.startListening(intent)
        }

        // Add first message
        listItems.add(ChatMessage("Hi! I am your personal assistant, what can I help you with today?", true, false))
        adapter.notifyDataSetChanged()

        button.setOnClickListener {
            addItem()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission granted
                } else {
                    // Permission denied
                }
                return
            }
            else -> {
                // Ignore all other requests
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        recognizer.destroy()
    }

    private fun addItem() {
        val editText = findViewById<EditText>(R.id.message)
        val text = editText.text.toString()
        editText.setText("")
        textView1.visibility = View.GONE
        textView2.visibility = View.VISIBLE
        listItems.add(ChatMessage(text, false, false))
        listItems.add(ChatMessage(text, false, true))
        adapter.notifyDataSetChanged()

        activeTextView?.startAnimation(slideUp)

        val listView = findViewById<ListView>(R.id.messageList)
        listView.post {
            listView.smoothScrollToPosition(adapter.count - 1)
        }

        GlobalScope.launch(Dispatchers.IO) {
            makeApiCall(text)
        }
    }


    private suspend fun makeApiCall(text: String) {
        val json = JSONObject()

        val userMessage = JSONObject()
        userMessage.put("role", "user")
        userMessage.put("content", text)
        conversationHistory.put(userMessage)

        json.put("messages", conversationHistory)
        json.put("model", "gpt-3.5-turbo")

        val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .addHeader("Authorization", "Bearer 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")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                println("Response: ${response.body?.string()}")
                throw IOException("Unexpected code $response")
            }

            val responseData = JSONObject(response.body!!.string())
            val responseText = responseData.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")

            val assistantMessage = JSONObject()
            assistantMessage.put("role", "assistant")
            assistantMessage.put("content", responseText)
            conversationHistory.put(assistantMessage)

            withContext(Dispatchers.Main) {
                listItems.removeAt(listItems.size - 1)
                adapter.notifyDataSetChanged()
                textView1.visibility = View.VISIBLE
                textView2.visibility = View.GONE
                listItems.add(ChatMessage(responseText, true, false))
                adapter.notifyDataSetChanged()

                val listView = findViewById<ListView>(R.id.messageList)
                listView.post {
                    listView.smoothScrollToPosition(adapter.count - 1)
                }
            }
        }
    }
}






