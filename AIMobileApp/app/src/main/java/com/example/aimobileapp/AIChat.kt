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
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlin.collections.ArrayList
import android.Manifest
import android.animation.*
import android.app.ActivityManager
import android.view.MotionEvent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.TimeUnit
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import okhttp3.*


class AIChat : AppCompatActivity() {

    private val REQUEST_RECORD_AUDIO = 1

    private lateinit var adapter: MessageArrayAdapter
    private val listItems = ArrayList<ChatMessage>()
    private var conversationHistory = JSONArray()
    private lateinit var textView1: TextView
    private lateinit var textView2: TextView
    private lateinit var backArrow: ImageView
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
    private var apiKey: String? = null


    private var initialY = 0f

    @SuppressLint("MissingInflatedId", "ServiceCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_aichat)

        textView1 = findViewById(R.id.title)
        textView2 = findViewById(R.id.typing)
        backArrow = findViewById(R.id.backArrow)

        val generateImg = findViewById<ImageView>(R.id.generateImg)

        generateImg.setOnClickListener {
            val intent = Intent(this, ImageActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }




        conversationCode = intent.getStringExtra("conversation").toString()
        val newConversation = intent.getStringExtra("newConversation").toString()

        val bin = findViewById<ImageView>(R.id.bin)
        bin.setOnClickListener {

            val narratedText = findViewById<LinearLayout>(R.id.narratedText)
            val send = findViewById<ImageView>(R.id.send)
            val sendMessageIcon = findViewById<LinearLayout>(R.id.sendMessageIcon)
            val sendMessageTextBox = findViewById<LinearLayout>(R.id.sendMessageTextBox)
            val sendMessageContainer = findViewById<LinearLayout>(R.id.sendMessageContainer)

            sendMessageIcon.visibility = View.VISIBLE
            sendMessageTextBox.visibility = View.VISIBLE
            sendMessageContainer.visibility = View.VISIBLE

            bin.visibility = View.GONE
            narratedText.visibility = View.GONE
            send.visibility = View.GONE

            narrateImageView.alpha = 1f
            narrateImageView.translationY = 0f

            narrateImageView.visibility = View.VISIBLE

            if (isListening) {
                recognizer.stopListening()
                isListening = false
            }
        }

        backArrow.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }


        val listView = findViewById<ListView>(R.id.messageList)
        adapter = MessageArrayAdapter(this, listItems)
        listView.adapter = adapter

        loadCurrentConversation()

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
            narrateImageView.translationY = 0f
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


        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.VIBRATE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.VIBRATE), REQUEST_VIBRATE)
        }

        narrateImageView = findViewById(R.id.narrate)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { isListening = true }

            override fun onRmsChanged(rmsdB: Float) {
                val soundWave = findViewById<ProgressBar>(R.id.soundWave)
                soundWave.progress = (rmsdB * 2).toInt()
            }

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
            pulse.duration = 1000

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
                    v.animate().scaleX(1.3f).scaleY(1.3f).setDuration(200).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = initialY - event.rawY
                    val maxDeltaY = resources.displayMetrics.heightPixels * 0.1f

                    if (deltaY > 0 && deltaY <= maxDeltaY) {
                        v.translationY = -deltaY

                        val fadeThreshold = 0.7 * maxDeltaY

                        if (deltaY > fadeThreshold) {
                            val remainingDistance = maxDeltaY - deltaY
                            val alpha = remainingDistance / (maxDeltaY - fadeThreshold)
                            v.alpha = alpha.toFloat()
                        }

                        if (!isListening) {
                            recognizer.startListening(intent)
                        }
                    }

                    if (deltaY > maxDeltaY && v.translationY != -maxDeltaY) {
                        if (Build.VERSION.SDK_INT >= 26) {
                            narrateImageView.visibility = View.GONE
                            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

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
                            vibrator.vibrate(500)

                            if (!pulse.isStarted) {
                                pulse.start()
                            }
                        }
                        v.translationY = -maxDeltaY
                    }

                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    pulse.cancel()
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                    true
                }
                else -> false
            }
        }

        if (newConversation == "true") {
            listItems.add(ChatMessage("Hi! I am your personal assistant, what can I help you with today?", true, false, true))
            adapter.notifyDataSetChanged()
        }



        button.setOnClickListener {
            addItem(false)
        }
    }

    private fun isMainProcess(): Boolean {
        return packageName == getCurrentProcessName()
    }

    private fun getCurrentProcessName(): String? {
        return try {
            val pid = android.os.Process.myPid()
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (processInfo in manager.runningAppProcesses) {
                if (processInfo.pid == pid) {
                    return processInfo.processName
                }
            }
            null
        } catch (ignored: Exception) {
            null
        }
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_RECORD_AUDIO -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                } else {
                }
                return
            }
            REQUEST_VIBRATE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                } else {
                }
                return
            }
            else -> {
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        recognizer.destroy()
        pulse.cancel()
    }

    private fun loadCurrentConversation() {
        val conversations = loadConversations()
        println("Loading current conversation for:" + conversationCode)
        if (conversations.containsKey(conversationCode)) {
            listItems.clear()
            listItems.addAll(conversations[conversationCode]!!)

            conversationHistory = JSONArray()
            for (chatMessage in listItems) {
                val message = JSONObject()
                message.put("role", if (chatMessage.isBot) "assistant" else "user")
                message.put("content", chatMessage.message)
                conversationHistory.put(message)
            }

            adapter.notifyDataSetChanged()
        }

        println("Loaded messages:" + conversations[conversationCode]?.size)
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
        listItems.add(ChatMessage(text, false, false, false))
        listItems.add(ChatMessage(text, false, true, false))
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

    private fun makeApiCall(text: String) {
        FirebaseManager.getInstance().fetchApiKey { apiKey ->
            if (apiKey != null) {
                val json = JSONObject()

                val userMessage = JSONObject()
                userMessage.put("role", "user")
                userMessage.put("content", text)
                conversationHistory.put(userMessage)

                json.put("messages", conversationHistory)
                json.put("model", "gpt-3.5-turbo")
                json.put("max_tokens", 1000)

                val requestBody = json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                val client = OkHttpClient.Builder()
                    .readTimeout(120, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .connectTimeout(120, TimeUnit.SECONDS)
                    .build()

                client.newCall(request).enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        val responseBodyString = response.body?.string() ?: ""
                        val responseData = JSONObject(responseBodyString)
                        val responseText = responseData.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")

                        val assistantMessage = JSONObject()
                        assistantMessage.put("role", "assistant")
                        assistantMessage.put("content", responseText)
                        conversationHistory.put(assistantMessage)

                        runOnUiThread {
                            listItems.removeAt(listItems.size - 1)
                            adapter.notifyDataSetChanged()
                            textView1.visibility = View.VISIBLE
                            textView2.visibility = View.GONE
                            listItems.add(ChatMessage(responseText, true, false, false))
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

                    override fun onFailure(call: Call, e: IOException) {
                    }
                })
            } else {
            }
        }
    }


}









