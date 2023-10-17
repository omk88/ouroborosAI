package com.ouroboros.aimobileapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.google.firebase.ktx.Firebase
import okhttp3.MultipartBody
import okhttp3.Request
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


class MainActivity : AppCompatActivity(), ConversationAdapter.OnConversationRemoveListener {
    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().addHeader("Authorization", "Bearer YOUR_AUTH_TOKEN").build()
            chain.proceed(request)
        }
        .build()
    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()
    private val service: DallEApiService = retrofit.create(DallEApiService::class.java)
    private val mixedItems: ArrayList<Any> = ArrayList()
    private lateinit var instructionTextView: TextView
    private lateinit var conversationListView: ListView
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var db: FirebaseFirestore
    private var canGoBack = false
    private var isSignInProcess = false
    private var isFirstRun = true
    private var isPurchase = false

    private lateinit var billingClient: BillingClient
    private val skuList = listOf("monthly", "yearly")

    var signInState = SignInState.NOT_STARTED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val userCredits = loadCreditsFromFile(this)
        if (userCredits != null) {
            val creditsText = findViewById<TextView>(R.id.creditsText)
            creditsText.text = "CREDITS: ${userCredits.credits}"
        }


        billingClient = BillingClient.newBuilder(this)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        //For sign out
        //FirebaseAuth.getInstance().signOut()
        //googleSignInClient.signOut()
        //

        MyApplication.currentUser = FirebaseAuth.getInstance().currentUser
        if (MyApplication.currentUser != null) {
            canGoBack = true

            val docRef = db.collection("users").document(MyApplication.currentUser!!.uid)

            docRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val credits = document.getLong("credits") ?: 0

                        val creditsText = findViewById<TextView>(R.id.creditsText)
                        creditsText.setText("CREDITS: $credits")

                        val updatedUserCredits = UserCredits(credits.toInt())
                        saveCreditsToFile(this, updatedUserCredits)

                        if (credits.toInt() <= 10) {
                            if (!isPurchase) {
                                checkSubscriptionAndStartActivity()
                            }
                        }
                    } else {
                        Log.d("Error", "No such document")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("Error", "Error getting credits: $exception")
                }
        } else {
            signIn()
        }


        instructionTextView = findViewById(R.id.instruction)
        conversationListView = findViewById(R.id.conversationList)
        val button = findViewById<LinearLayout>(R.id.button_start_activity)
        button.setOnClickListener {
            val intent = Intent(this, com.ouroboros.aimobileapp.AIChat::class.java)
            intent.putExtra("conversation", getRandomString(15))
            intent.putExtra("newConversation", "true")
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }
        val generateImg = findViewById<LinearLayout>(R.id.generateImg)
        generateImg.setOnClickListener {
            val intent = Intent(this, ImageActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
        }

        mixedItems.addAll(loadConversationsAsList())
        toggleListViewAndInstruction()
        val adapter = ConversationAdapter(this, mixedItems, this, instructionTextView)
        conversationListView.adapter = adapter
        adapter.notifyDataSetChanged()
        conversationListView.setOnItemClickListener { parent, view, position, id ->
            val selectedConversation = parent.getItemAtPosition(position) as? Conversation
            selectedConversation?.let {
                val intent = Intent(this@MainActivity, com.ouroboros.aimobileapp.AIChat::class.java)
                intent.putExtra("conversation", it.code)
                startActivity(intent)
            }
        }
    }


    override fun onResume() {
        super.onResume()

        if (intent.getStringExtra("back") != "back") {
            if (signInState != SignInState.COMPLETED) {
                signIn()
            } else {
                connectBillingClient()
            }
        } else {
            connectBillingClient()
        }
    }

    private fun connectBillingClient() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    checkCredits()
                } else {
                    // Handle initialization failure
                }
            }

            override fun onBillingServiceDisconnected() {
                // Implement reconnection logic here if needed
            }
        })
    }


    fun loadCreditsFromFile(context: Context): UserCredits? {
        val gson = Gson()

        try {
            val fileInputStream = context.openFileInput("user_credits.json")
            val inputStreamReader = InputStreamReader(fileInputStream)
            val bufferedReader = BufferedReader(inputStreamReader)

            val json = bufferedReader.readText()
            bufferedReader.close()

            return gson.fromJson(json, UserCredits::class.java)
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
    }

    fun saveCreditsToFile(context: Context, userCredits: UserCredits) {
        val gson = Gson()
        val json = gson.toJson(userCredits)

        try {
            // Save to a private file in the app's internal storage
            val fileOutputStream = context.openFileOutput("user_credits.json", Context.MODE_PRIVATE)
            fileOutputStream.write(json.toByteArray())
            fileOutputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private fun checkCredits() {
        MyApplication.currentUser?.let { currentUser ->
            val docRef = db.collection("users").document(currentUser.uid)

            docRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        val credits = document.getLong("credits") ?: 0

                        val creditsText = findViewById<TextView>(R.id.creditsText)
                        creditsText.setText("CREDITS: $credits")

                        val updatedUserCredits = UserCredits(credits.toInt())
                        saveCreditsToFile(this, updatedUserCredits)

                        if (credits.toInt() <= 10 && signInState != SignInState.SIGNED_IN_NO_CREDITS) {
                            launchPurchaseActivity()
                        } else {
                            signInState = SignInState.SIGNED_IN_WITH_CREDITS
                        }
                    } else {
                        Log.d("Error", "No such document")
                    }
                }
                .addOnFailureListener { exception ->
                    Log.d("Error", "Error getting credits: $exception")
                }
        }
    }

    private fun launchPurchaseActivity() {
        checkSubscriptionAndStartActivity()
        signInState = SignInState.SIGNED_IN_NO_CREDITS
    }

    private fun signIn() {
        if (signInState != SignInState.COMPLETED) {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, PurchaseActivity.RC_SIGN_IN)

            signInState = SignInState.IN_PROCESS
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PurchaseActivity.RC_SIGN_IN) {
            signInState = SignInState.COMPLETED
            isSignInProcess = false
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account?.idToken!!)
            } catch (e: ApiException) {
                Log.d("Error", "Google Sign In failed")
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    canGoBack = true
                    val user = auth.currentUser
                    user?.let {
                        checkIfUserExistsOrAssignCredits(it.uid)
                    }
                } else {
                    Log.d("Error", "signInWithCredential:failure")
                }
            }
    }

    private fun checkIfUserExistsOrAssignCredits(uid: String) {
        val userRef = db.collection("users").document(uid)
        userRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    val credits = document.getLong("credits") ?: 0

                    val creditsText = findViewById<TextView>(R.id.creditsText)
                    creditsText.setText("CREDITS: $credits")

                    val updatedUserCredits = UserCredits(credits.toInt())
                    saveCreditsToFile(this, updatedUserCredits)

                    if (credits.toInt() <= 10) {
                        if (!isPurchase) {
                            checkSubscriptionAndStartActivity()

                            isPurchase = true
                        }
                    }
                } else {
                    val userCredits = hashMapOf(
                        "credits" to 10
                    )
                    if (!isPurchase) {
                        checkSubscriptionAndStartActivity()

                        isPurchase = true
                    }
                    userRef.set(userCredits)
                        .addOnSuccessListener {
                            Log.d("Error", "Assigned 10 credits to new user.")
                        }
                        .addOnFailureListener { e ->
                            Log.d("Error", "Error writing document")
                        }
                }
            }
            .addOnFailureListener { exception ->
                Log.d("Error", "get failed with ")
            }
    }

    private fun checkSubscriptionAndStartActivity() {
        if (intent.getStringExtra("exit") != "exit") {
            userHasSubscription { hasSubscription ->
                if (hasSubscription) {
                    checkSubscriptionType { subscriptionType ->
                        runOnUiThread {
                            if (subscriptionType == "monthly" || subscriptionType == "yearly") {
                                // Start PurchaseActivity2
                                val intent =
                                    Intent(this@MainActivity, PurchaseActivity2::class.java)
                                startActivity(intent)
                            } else {
                                // Start PurchaseActivity
                                val intent = Intent(this@MainActivity, PurchaseActivity::class.java)
                                startActivity(intent)
                            }
                            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                            isPurchase = true
                        }
                    }
                } else {
                    runOnUiThread {
                        val intent = Intent(this@MainActivity, PurchaseActivity::class.java)
                        startActivity(intent)
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                        isPurchase = true
                    }
                }
            }
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




    private fun userHasSubscription(callback: (Boolean) -> Unit) {
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid

        if (currentUserUid != null) {
            val userDocRef = db.collection("users").document(currentUserUid)

            userDocRef.get()
                .addOnSuccessListener { documentSnapshot ->
                    if (documentSnapshot.exists()) {
                        val credits = documentSnapshot.getLong("credits") ?: 0

                        val creditsText = findViewById<TextView>(R.id.creditsText)
                        creditsText.setText("CREDITS: $credits")

                        val updatedUserCredits = UserCredits(credits.toInt())
                        saveCreditsToFile(this, updatedUserCredits)

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


    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
            // Handle successful purchase updates here
        } else if (billingResult.responseCode == BillingClient.BillingResponseCode.USER_CANCELED) {
            // Handle user canceled the purchase
        } else {
            // Handle other error cases
        }
    }



    override fun onBackPressed() {
        if (canGoBack || !isSignInProcess) {
            super.onBackPressed()
        }
        // Do nothing if canGoBack is false and sign-in process is ongoing
    }

    override fun onRemove(conversation: Conversation) {
        removeConversationFromJson(conversation)
        cleanUpEmptyDividers()
        toggleListViewAndInstruction()
    }

    private fun toggleListViewAndInstruction() {
        if (mixedItems.isEmpty()) {
            instructionTextView.visibility = View.VISIBLE
            conversationListView.visibility = View.GONE
        } else {
            instructionTextView.visibility = View.GONE
            conversationListView.visibility = View.VISIBLE
        }
    }

    private fun cleanUpEmptyDividers() {
        val todayItems = mixedItems.filterIsInstance<Conversation>().filter {
            val dateDiff = TimeUnit.DAYS.convert(Calendar.getInstance().time.time - SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.timestamp).time, TimeUnit.MILLISECONDS)
            dateDiff == 0L
        }
        val last7DaysItems = mixedItems.filterIsInstance<Conversation>().filter {
            val dateDiff = TimeUnit.DAYS.convert(Calendar.getInstance().time.time - SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.timestamp).time, TimeUnit.MILLISECONDS)
            dateDiff in 1..6
        }
        val olderThan30DaysItems = mixedItems.filterIsInstance<Conversation>().filter {
            val dateDiff = TimeUnit.DAYS.convert(Calendar.getInstance().time.time - SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it.timestamp).time, TimeUnit.MILLISECONDS)
            dateDiff > 6
        }
        if (todayItems.isEmpty()) mixedItems.removeAll { it is ConversationAdapter.Divider && it.type == ConversationAdapter.DividerType.TODAY }
        if (last7DaysItems.isEmpty()) mixedItems.removeAll { it is ConversationAdapter.Divider && it.type == ConversationAdapter.DividerType.LAST_7_DAYS }
        if (olderThan30DaysItems.isEmpty()) mixedItems.removeAll { it is ConversationAdapter.Divider && it.type == ConversationAdapter.DividerType.OLDER_THAN_30_DAYS }
        val listView: ListView = findViewById(R.id.conversationList)
        (listView.adapter as ConversationAdapter).notifyDataSetChanged()
    }

    private fun removeConversationFromJson(conversation: Conversation) {
        val file = File(filesDir, "conversations.json")
        if (file.exists()) {
            val jsonString = file.readText()
            val gson = Gson()
            val typeToken = object : TypeToken<MutableMap<String, List<ChatMessage>>>() {}.type
            val allConversations: MutableMap<String, List<ChatMessage>> = gson.fromJson(jsonString, typeToken)
            val keyToRemove = allConversations.entries.find { it.value.last().timestamp.contains(conversation.timestamp) && it.value.last().message == conversation.message }?.key
            if (keyToRemove != null) {
                allConversations.remove(keyToRemove)
                val updatedJsonString = gson.toJson(allConversations)
                file.writeText(updatedJsonString)
            }
        }
    }

    private fun loadConversationsAsList(): ArrayList<Any> {
        val file = File(filesDir, "conversations.json")
        val resultList = ArrayList<Conversation>()
        if (file.exists()) {
            val jsonString = file.readText()
            val gson = Gson()
            val typeToken = object : TypeToken<MutableMap<String, List<ChatMessage>>>() {}.type
            val allConversations: MutableMap<String, List<ChatMessage>>? = gson.fromJson(jsonString, typeToken)
            allConversations?.forEach { (code, messages) ->
                val lastMessage = messages.last()
                val datePart = lastMessage.timestamp.split(" ")[0]
                resultList.add(Conversation(datePart, lastMessage.message, code))
            }
        }
        val sortedList = resultList.sortedByDescending { it.timestamp }
        val mixedItems: ArrayList<Any> = ArrayList()
        var addedTodayDivider = false
        var addedLast7DaysDivider = false
        var added30DaysDivider = false
        for (conversation in sortedList) {
            val conversationDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(conversation.timestamp)
            val today = Calendar.getInstance().time
            val diff = TimeUnit.DAYS.convert(today.time - conversationDate.time, TimeUnit.MILLISECONDS)
            when {
                diff == 0L && !addedTodayDivider -> {
                    mixedItems.add(ConversationAdapter.Divider(ConversationAdapter.DividerType.TODAY, "Today"))
                    addedTodayDivider = true
                }
                diff in 1..6 && !addedLast7DaysDivider -> {
                    mixedItems.add(ConversationAdapter.Divider(ConversationAdapter.DividerType.LAST_7_DAYS, "Last 7 days"))
                    addedLast7DaysDivider = true
                }
                diff > 6 && !added30DaysDivider -> {
                    mixedItems.add(ConversationAdapter.Divider(ConversationAdapter.DividerType.OLDER_THAN_30_DAYS, "30+ days"))
                    added30DaysDivider = true
                }
            }
            mixedItems.add(conversation)
        }
        return mixedItems
    }

    fun getRandomString(length: Int = 10): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length).map { allowedChars.random() }.joinToString("")
    }

    fun deleteConversationsFile() {
        val file = File(filesDir, "conversations.json")
        if (file.exists()) file.delete()
    }
}

enum class SignInState {
    NOT_STARTED, IN_PROCESS, COMPLETED, SIGNED_IN_WITH_CREDITS, SIGNED_IN_NO_CREDITS
}

interface SubscriptionCallback {
    fun onSubscriptionCheck(result: Boolean)
}

data class UserCredits(val credits: Int)








