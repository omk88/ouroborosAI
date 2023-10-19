package com.ouroboros.aimobileapp

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
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
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
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import okhttp3.*


class AIChat : AppCompatActivity() {

    private val REQUEST_RECORD_AUDIO = 1

    private lateinit var db: FirebaseFirestore
    private lateinit var adapter: com.ouroboros.aimobileapp.MessageArrayAdapter
    private val listItems = ArrayList<com.ouroboros.aimobileapp.ChatMessage>()
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
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth



    private var initialY = 0f

    @SuppressLint("MissingInflatedId", "ServiceCast")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.ouroboros.aimobileapp.R.layout.activity_aichat)

        val textView: TextView = findViewById(R.id.changeGptText)

        textView.setOnClickListener {
            showDropDownMenu(it)
        }

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        textView1 = findViewById(com.ouroboros.aimobileapp.R.id.title)
        textView2 = findViewById(com.ouroboros.aimobileapp.R.id.typing)
        backArrow = findViewById(com.ouroboros.aimobileapp.R.id.backArrow)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(com.ouroboros.aimobileapp.R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        val generateImg = findViewById<ImageView>(com.ouroboros.aimobileapp.R.id.generateImg)

        generateImg.setOnClickListener {
            val intent = Intent(this, com.ouroboros.aimobileapp.ImageActivity::class.java)
            startActivity(intent)
            overridePendingTransition(
                com.ouroboros.aimobileapp.R.anim.slide_in_right,
                com.ouroboros.aimobileapp.R.anim.slide_out_left
            )
        }




        conversationCode = intent.getStringExtra("conversation").toString()
        val newConversation = intent.getStringExtra("newConversation").toString()

        val bin = findViewById<ImageView>(com.ouroboros.aimobileapp.R.id.bin)
        bin.setOnClickListener {

            val narratedText = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.narratedText)
            val send = findViewById<ImageView>(com.ouroboros.aimobileapp.R.id.send)
            val sendMessageIcon = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.sendMessageIcon)
            val sendMessageTextBox = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.sendMessageTextBox)
            val sendMessageContainer = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.sendMessageContainer)

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
            val intent = Intent(this, com.ouroboros.aimobileapp.MainActivity::class.java)
            intent.putExtra("back", "back")
            startActivity(intent)
            overridePendingTransition(
                com.ouroboros.aimobileapp.R.anim.slide_in_left,
                com.ouroboros.aimobileapp.R.anim.slide_out_right
            )
        }


        val listView = findViewById<ListView>(com.ouroboros.aimobileapp.R.id.messageList)
        adapter = com.ouroboros.aimobileapp.MessageArrayAdapter(this, listItems)
        listView.adapter = adapter

        loadCurrentConversation()

        val button = findViewById<ImageView>(com.ouroboros.aimobileapp.R.id.sendMessage)
        val editText = findViewById<EditText>(com.ouroboros.aimobileapp.R.id.message)
        val textVisual = findViewById<TextView>(com.ouroboros.aimobileapp.R.id.textVisual)


        val send = findViewById<ImageView>(com.ouroboros.aimobileapp.R.id.send)
        send.setOnClickListener {
            /*if (pulse.isRunning) {
                pulse.end()
            }
            pulse.cancel()*/
            narrateImageView.clearAnimation()
            narrateImageView.translationY = 0f
            addItem(true)

            val sendMessageIcon = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.sendMessageIcon)
            val sendMessageTextBox = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.sendMessageTextBox)
            val sendMessageContainer = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.sendMessageContainer)

            sendMessageIcon.visibility = View.VISIBLE
            sendMessageTextBox.visibility = View.VISIBLE
            sendMessageContainer.visibility = View.VISIBLE

            val bin = findViewById<ImageView>(com.ouroboros.aimobileapp.R.id.bin)
            val narratedText = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.narratedText)
            val send = findViewById<ImageView>(com.ouroboros.aimobileapp.R.id.send)

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

        narrateImageView = findViewById(com.ouroboros.aimobileapp.R.id.narrate)
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        val listener = object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() { isListening = true }

            override fun onRmsChanged(rmsdB: Float) {
                val soundWave = findViewById<ProgressBar>(com.ouroboros.aimobileapp.R.id.soundWave)
                soundWave.progress = (rmsdB * 2).toInt()
            }

            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListening = false
                //pulse.cancel()
                narrateImageView.scaleX = 1f
                narrateImageView.scaleY = 1f
                narrateImageView.clearAnimation()

                /*if (pulse.isStarted) {
                    println("Animator has started.")
                }

                if (pulse.isRunning) {
                    println("Animator is currently running.")
                }

                if (pulse.isPaused) {
                    println("Animator is paused.")
                }*/

            }

            override fun onError(error: Int) { isListening = false }

            override fun onResults(results: Bundle) {
                val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (matches != null) {
                    textVisual.text = matches[0]
                    recognisedText = matches[0]
                    //.cancel()
                    narrateImageView.clearAnimation()

                    /*if (pulse.isStarted) {
                        println("Animator has started.")
                    }

                    if (pulse.isRunning) {
                        println("Animator is currently running.")
                    }

                    if (pulse.isPaused) {
                        println("Animator is paused.")
                    }*/

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

            /*pulse = AnimatorSet()
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
            })*/



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

                            val sendMessageIcon = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.sendMessageIcon)
                            val sendMessageTextBox = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.sendMessageTextBox)
                            val sendMessageContainer = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.sendMessageContainer)

                            sendMessageIcon.visibility = View.GONE
                            sendMessageTextBox.visibility = View.GONE
                            sendMessageContainer.visibility = View.GONE

                            val bin = findViewById<ImageView>(com.ouroboros.aimobileapp.R.id.bin)
                            val narratedText = findViewById<LinearLayout>(com.ouroboros.aimobileapp.R.id.narratedText)
                            val send = findViewById<ImageView>(com.ouroboros.aimobileapp.R.id.send)

                            bin.visibility = View.VISIBLE
                            narratedText.visibility = View.VISIBLE
                            send.visibility = View.VISIBLE

                        } else {
                            @Suppress("DEPRECATION")
                            vibrator.vibrate(500)

                            /*if (!pulse.isStarted) {
                                pulse.start()
                            }*/
                        }
                        v.translationY = -maxDeltaY
                    }

                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    //pulse.cancel()
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200).start()
                    true
                }
                else -> false
            }
        }

        if (newConversation == "true") {
            listItems.add(
                com.ouroboros.aimobileapp.ChatMessage(
                    "Hi! I am your personal assistant, what can I help you with today?",
                    true,
                    false,
                    true
                )
            )
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

    private fun showDropDownMenu(anchor: View) {
        val popupMenu = PopupMenu(this, anchor)
        popupMenu.inflate(R.menu.textview_dropdown_menu)
        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_option1 -> {
                    // Handle option 1 click
                    true
                }
                R.id.action_option2 -> {
                    // Handle option 2 click
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
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
        //pulse.cancel()
    }

    private fun loadCurrentConversation() {
        val conversations = loadConversations()
        println("Loading current conversation for:" + conversationCode)
        if (conversations.containsKey(conversationCode)) {
            listItems.clear()

            val conversationMessages = conversations[conversationCode]!!
            val filteredMessages = mutableListOf<ChatMessage>()

            for (i in conversationMessages.indices) {
                val chatMessage = conversationMessages[i]
                if (!(chatMessage.isTyping || (!chatMessage.isBot && i == conversationMessages.size - 1))) {
                    filteredMessages.add(chatMessage)
                }
            }

            listItems.addAll(filteredMessages)

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
        val editText = findViewById<EditText>(com.ouroboros.aimobileapp.R.id.message)
        var text = ""
        if (voiceMessage) {
            text = recognisedText
        } else {
            text = editText.text.toString()
        }

        editText.setText("")
        textView1.visibility = View.GONE
        textView2.visibility = View.VISIBLE
        listItems.add(com.ouroboros.aimobileapp.ChatMessage(text, false, false, false))
        listItems.add(com.ouroboros.aimobileapp.ChatMessage(text, false, true, false))
        saveCurrentConversation()

        adapter.notifyDataSetChanged()

        activeTextView?.startAnimation(slideUp)

        val listView = findViewById<ListView>(com.ouroboros.aimobileapp.R.id.messageList)
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


    fun saveConversations(conversations: MutableMap<String, ArrayList<com.ouroboros.aimobileapp.ChatMessage>>) {
        val gson = Gson()
        val jsonString = gson.toJson(conversations)
        val file = File(filesDir, "conversations.json")
        file.writeText(jsonString)
    }



    fun loadConversations(): MutableMap<String, ArrayList<com.ouroboros.aimobileapp.ChatMessage>> {
        val file = File(filesDir, "conversations.json")
        if (file.exists()) {
            val jsonString = file.readText()
            val gson = Gson()
            val typeToken = object : TypeToken<MutableMap<String, ArrayList<com.ouroboros.aimobileapp.ChatMessage>>>() {}.type
            return gson.fromJson(jsonString, typeToken)
        }
        return mutableMapOf()
    }

    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent,
            com.ouroboros.aimobileapp.PurchaseActivity.Companion.RC_SIGN_IN
        )
    }

    private fun userHasSubscription(callback: (Boolean) -> Unit) {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserUid != null) {
            val userDocRef = db.collection("users").document(currentUserUid)

            userDocRef.get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val subscriptionId = documentSnapshot.getString("subscriptionId")
                        val hasSubscription = subscriptionId == "monthly" || subscriptionId == "yearly"
                        callback(hasSubscription)
                    } else {
                        // User document does not exist or is missing subscriptionId
                        callback(false)
                    }
                }
                .addOnFailureListener { exception ->
                    // Handle Firestore query failure
                    Log.e("TAGG", "Firestore query failed: $exception")
                    callback(false)
                }
        } else {
            // Current user is not authenticated
            Log.d("TAGG", "User not authenticated")
            callback(false)
        }
    }

    private fun checkSubscriptionType(callback: (String?) -> Unit) {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserUid != null) {
            val userRef = db.collection("users").document(currentUserUid)
            userRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val subscriptionType = document.getString("subscriptionId")
                        callback(subscriptionType)
                    } else {
                        // User document does not exist
                        callback(null)
                    }
                }
                .addOnFailureListener { exception ->
                    // Handle the failure to fetch the user's subscription type
                    callback(null)
                }
        } else {
            // Current user is not authenticated
            callback(null)
        }
    }

    private fun checkSubscriptionAndStartActivity() {
        userHasSubscription { hasSubscription ->
            if (hasSubscription) {
                checkSubscriptionType { subscriptionType ->
                    runOnUiThread {
                        if (subscriptionType == "monthly" || subscriptionType == "yearly") {
                            // Start PurchaseActivity2
                            val intent = Intent(this@AIChat, PurchaseActivity2::class.java)
                            startActivity(intent)
                        } else {
                            // Start PurchaseActivity
                            val intent = Intent(this@AIChat, PurchaseActivity::class.java)
                            startActivity(intent)
                        }
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    }
                }
            } else {
                runOnUiThread {
                    val intent = Intent(this@AIChat, PurchaseActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                }
            }
        }
    }

    private fun makeApiCall(text: String) {
        if (auth.currentUser != null) {
            println("NOT NULL")
            val docRef = db.collection("users").document(auth.currentUser!!.uid)

            docRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        println("DOC EXISTS")
                        val credits = document.getLong("credits") ?: 0

                        if (credits.toInt() == 0) {
                            println("ZERO CREDITS")
                            checkSubscriptionAndStartActivity()
                        } else {
                            println("HAVE CREDITS")
                            docRef.update("credits", FieldValue.increment(-1))
                                .addOnSuccessListener {
                                    com.ouroboros.aimobileapp.FirebaseManager.Companion.getInstance()
                                        .fetchApiKey { apiKey ->
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
                                                        listItems.add(
                                                            com.ouroboros.aimobileapp.ChatMessage(
                                                                responseText,
                                                                true,
                                                                false,
                                                                false
                                                            )
                                                        )
                                                        saveCurrentConversation()
                                                        val content = loadConversations()
                                                        println(content)

                                                        adapter.notifyDataSetChanged()

                                                        val listView = findViewById<ListView>(com.ouroboros.aimobileapp.R.id.messageList)
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
                                    println("SUCCESSS")
                                    println("Credits decremented successfully")
                                }
                                .addOnFailureListener { e ->
                                    // Handle error
                                    println("Error decrementing credits: $e")
                                }
                        }
                    } else {
                        println("No such document")
                    }
                }
                .addOnFailureListener { exception ->
                    println("Error getting credits: $exception")
                }
        } else {
            println("NO SIGN IN")
            signIn()
        }

    }



}









