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
import android.animation.*
import android.view.MotionEvent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File


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
    private var isListening: Boolean = false
    private val REQUEST_VIBRATE = 2
    private lateinit var pulse: AnimatorSet
    private lateinit var recognisedText: String
    private lateinit var narrateImageView: ImageView
    private lateinit var conversationCode: String



    private var initialY = 0f

    @SuppressLint("MissingInflatedId", "ServiceCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aichat)

        textView1 = findViewById(R.id.title)
        textView2 = findViewById(R.id.typing)
        conversationCode = intent.getStringExtra("conversation").toString()

        val listView = findViewById<ListView>(R.id.messageList)
        adapter = MessageArrayAdapter(this, listItems)
        listView.adapter = adapter

        val button = findViewById<ImageView>(R.id.sendMessage)
        val editText = findViewById<EditText>(R.id.message)
        val textVisual = findViewById<TextView>(R.id.textVisual)


        val send = findViewById<ImageView>(R.id.send)
        send.setOnClickListener {
            if (pulse.isRunning) {
                pulse.end()
            }
            pulse.cancel()
            narrateImageView.clearAnimation()
            narrateImageView.translationY = 0f // This line resets the microphone's position
            addItem(true)

            val sendMessageIcon = findViewById<LinearLayout>(R.id.sendMessageIcon)
            val sendMessageTextBox = findViewById<LinearLayout>(R.id.sendMessageTextBox)
            val sendMessageContainer = findViewById<LinearLayout>(R.id.sendMessageContainer)

            sendMessageIcon.visibility = View.VISIBLE
            sendMessageTextBox.visibility = View.VISIBLE
            sendMessageContainer.visibility = View.VISIBLE

            val bin = findViewById<ImageView>(R.id.bin)
            val narratedText = findViewById<LinearLayout>(R.id.narratedText)
            val send = findViewById<ImageView>(R.id.send)

            bin.visibility = View.GONE
            narratedText.visibility = View.GONE
            send.visibility = View.GONE
        }


        // Request permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.VIBRATE), REQUEST_VIBRATE)
        }

        // Set up speech recognizer
        narrateImageView = findViewById(R.id.narrate)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { isListening = true }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                pulse.cancel()
                narrateImageView.scaleX = 1f
                narrateImageView.scaleY = 1f
                narrateImageView.clearAnimation()

                if (pulse.isStarted) {
                    println("Animator has started.")
                }

                if (pulse.isRunning) {
                    println("Animator is currently running.")
                }

                if (pulse.isPaused) {
                    println("Animator is paused.")
                }

            }

            override fun onError(error: Int) { isListening = false }

            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    textVisual.text = matches[0]
                    recognisedText = matches[0]
                    pulse.cancel()
                    narrateImageView.clearAnimation()

                    if (pulse.isStarted) {
                        println("Animator has started.")
                    }

                    if (pulse.isRunning) {
                        println("Animator is currently running.")
                    }

                    if (pulse.isPaused) {
                        println("Animator is paused.")
                    }

                }
            }


            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        recognizer.setRecognitionListener(listener)

        narrateImageView.setOnTouchListener { v, event ->
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            // Define AnimatorSet here to be used in ACTION_DOWN and ACTION_UP
            val scaleXUp = ObjectAnimator.ofFloat(narrateImageView, "scaleX", 1f, 1.2f)
            val scaleYUp = ObjectAnimator.ofFloat(narrateImageView, "scaleY", 1f, 1.2f)
            val scaleXDown = ObjectAnimator.ofFloat(narrateImageView, "scaleX", 1.2f, 1f)
            val scaleYDown = ObjectAnimator.ofFloat(narrateImageView, "scaleY", 1.2f, 1f)

            scaleXUp.repeatCount = ValueAnimator.INFINITE
            scaleXUp.repeatMode = ValueAnimator.REVERSE

            scaleYUp.repeatCount = ValueAnimator.INFINITE
            scaleYUp.repeatMode = ValueAnimator.REVERSE

            val scaleUpSet = AnimatorSet()
            scaleUpSet.play(scaleXUp).with(scaleYUp)

            val scaleDownSet = AnimatorSet()
            scaleDownSet.play(scaleXDown).with(scaleYDown)

            pulse = AnimatorSet()
            pulse.play(scaleUpSet).before(scaleDownSet)
            pulse.duration = 1000  // adjust to control the speed

            pulse.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    narrateImageView.scaleX = 1f
                    narrateImageView.scaleY = 1f
                }

                override fun onAnimationCancel(animation: Animator) {
                    narrateImageView.scaleX = 1f
                    narrateImageView.scaleY = 1f
                }
            })



            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = event.rawY
                    v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(200).start() // increase the image size to 130%
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.rawY
                    val maxDeltaY = resources.displayMetrics.heightPixels * 0.1f // 10% of screen height

                    if (deltaY > 0 && deltaY <= maxDeltaY) {
                        // Only allow upward movement up to 30% of screen height
                        v.translationY = -deltaY // Move the ImageView

                        // Start listening if not already doing so
                        if (!isListening) {
                            recognizer.startListening(intent)
                        }

                    }

                    // Vibrate and keep at maximum point if limit is reached
                    if (deltaY > maxDeltaY && v.translationY != -maxDeltaY) {
                        if (Build.VERSION.SDK_INT >= 26) {
                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

                            // Start pulse animation
                            val sendMessageIcon = findViewById<LinearLayout>(R.id.sendMessageIcon)
                            val sendMessageTextBox = findViewById<LinearLayout>(R.id.sendMessageTextBox)
                            val sendMessageContainer = findViewById<LinearLayout>(R.id.sendMessageContainer)

                            sendMessageIcon.visibility = View.GONE
                            sendMessageTextBox.visibility = View.GONE
                            sendMessageContainer.visibility = View.GONE

                            val bin = findViewById<ImageView>(R.id.bin)
                            val narratedText = findViewById<LinearLayout>(R.id.narratedText)
                            val send = findViewById<ImageView>(R.id.send)

                            bin.visibility = View.VISIBLE
                            narratedText.visibility = View.VISIBLE
                            send.visibility = View.VISIBLE

                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(500) // Deprecated in API 26

                            // Start pulse animation
                            if (!pulse.isStarted) {
                                pulse.start()
                            }
                        }
                        v.translationY = -maxDeltaY // Keep the ImageView at the maximum point
                    }

                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Do not reset the ImageView position when the user lifts their finger
                    // Instead, do it when the recognition is done
                    pulse.cancel()
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).start() // Reset the image size when the user lifts their finger
                    true
                }
                else -> false
            }
        }


        // Add first message
        listItems.add(ChatMessage("Hi! I am your personal assistant, what can I help you with today?", true, false))
        adapter.notifyDataSetChanged()

        button.setOnClickListener {
            addItem(false)
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
            REQUEST_VIBRATE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Vibrate permission granted
                } else {
                    // Vibrate permission denied
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
        pulse.cancel()
    }

    private fun addItem(voiceMessage: Boolean) {
        val editText = findViewById<EditText>(R.id.message)
        var text = ""
        if (voiceMessage) {
            text = recognisedText
        } else {
            text = editText.text.toString()
        }

        editText.setText("")
        textView1.visibility = View.GONE
        textView2.visibility = View.VISIBLE
        listItems.add(ChatMessage(text, false, false))
        listItems.add(ChatMessage(text, false, true))
        saveCurrentConversation()

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

    private fun saveCurrentConversation() {
        val conversations = loadConversations()
        conversations[conversationCode] = ArrayList(listItems)
        saveConversations(conversations)
    }


    fun saveConversations(conversations: MutableMap<String, ArrayList<ChatMessage>>) {
        val gson = Gson()
        val jsonString = gson.toJson(conversations)
        val file = File(filesDir, "conversations.json")
        file.writeText(jsonString)
    }





    fun loadConversations(): MutableMap<String, ArrayList<ChatMessage>> {
        val file = File(filesDir, "conversations.json")
        if (file.exists()) {
            val jsonString = file.readText()
            val gson = Gson()
            val typeToken = object : TypeToken<MutableMap<String, ArrayList<ChatMessage>>>() {}.type
            return gson.fromJson(jsonString, typeToken)
        }
        return mutableMapOf()
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
            .addHeader("Authorization", "Bearer ")
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
                saveCurrentConversation()
                val content = loadConversations()
                println(content)

                adapter.notifyDataSetChanged()

                val listView = findViewById<ListView>(R.id.messageList)
                listView.post {
                    listView.smoothScrollToPosition(adapter.count - 1)
                }
            }
        }
    }
}









