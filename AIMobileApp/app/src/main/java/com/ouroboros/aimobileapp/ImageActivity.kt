package com.ouroboros.aimobileapp

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.PurchasesUpdatedListener
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.load.resource.gif.GifDrawable
import com.bumptech.glide.request.RequestListener
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.lang.reflect.Type
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

class ImageActivity: AppCompatActivity() {

    private lateinit var adapter: ImageItemAdapter
    private var imageUrls = mutableListOf<String>()
    private var isRequestInProgress = false
    private lateinit var backArrow: ImageView
    private var lastTotalItemCount = 0
    private var openAIApiKey: String? = null
    private lateinit var db: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private var flag = false
    private lateinit var generateImageView: ImageView
    private lateinit var loadingImageView: ImageView
    private lateinit var generateExtraImagesLayout: View

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            if (openAIApiKey == null) {
                openAIApiKey = getOpenAIApiKeySynchronously()
            }

            val request = chain.request().newBuilder().addHeader("Authorization", "Bearer $openAIApiKey").build()
            chain.proceed(request)
        }
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .build()




    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()

    private val service: DallEApiService = retrofit.create(DallEApiService::class.java)


    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_image)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        backArrow = findViewById(R.id.backArrow)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        backArrow.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("back", "back")
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        val gifImageView = findViewById<ImageView>(R.id.loading)
        Glide.with(this)
            .asGif()
            .load(R.drawable.loading)
            .into(gifImageView)

        val gifImageView2 = findViewById<ImageView>(R.id.largeLoading)
        Glide.with(this)
            .asGif()
            .load(R.drawable.loading)
            .into(gifImageView2)

        loadImageUrls()

        val promptEditText: EditText = findViewById(R.id.prompt)


        if (imageUrls.isNotEmpty()) {
            println("NOT EMPTY")
            val imageListView: ListView = findViewById(R.id.imageListView)
            val instruction: TextView = findViewById(R.id.instruction)
            imageListView.visibility = View.VISIBLE
            instruction.visibility = View.GONE

            loadPromptHint()?.let {
                promptEditText.hint = it
            }

        } else {
            println("EMPTY")
            val imageListView: ListView = findViewById(R.id.imageListView)
            val instruction: TextView = findViewById(R.id.instruction)
            imageListView.visibility = View.GONE
            instruction.visibility = View.VISIBLE
        }

        adapter = ImageItemAdapter(this, imageUrls)
        val listView: ListView = findViewById(R.id.imageListView)
        listView.adapter = adapter

        val titleTextView: TextView = findViewById(R.id.instruction)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            titleTextView.text = Html.fromHtml(getString(R.string.prompt_instruction), Html.FROM_HTML_MODE_LEGACY)
        } else {
            titleTextView.text = Html.fromHtml(getString(R.string.prompt_instruction))
        }


        val generateButton: Button = findViewById(R.id.generate)
        val instruction: TextView = findViewById(R.id.instruction)
        val largeLoading: ImageView = findViewById(R.id.largeLoading)

        generateButton.setOnClickListener {

            val promptText = promptEditText.text.toString().trim()

            promptEditText.clearFocus()

            if (promptText.isNotEmpty()) {

                promptEditText.hint = promptText
                savePromptHint(promptText)

                if (imageUrls.isNotEmpty()) {
                    println("NOT EMPTY")
                    imageUrls.clear()
                    saveImageUrls()
                }

                adapter.notifyDataSetChanged()

                val imageListView: ListView = findViewById(R.id.imageListView)
                val largeLoading: ImageView = findViewById(R.id.largeLoading)
                largeLoading.visibility = View.VISIBLE
                imageListView.visibility = View.GONE

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(promptEditText.windowToken, 0)

                promptEditText.hint = promptText
                promptEditText.setText("")

                requestImagesFromAPI(promptText, 4)
            } else {
                Toast.makeText(this, "Enter a Prompt!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getOpenAIApiKeySynchronously(): String {
        var apiKeyLocal: String? = null
        val latch = CountDownLatch(1)

        FirebaseManager.getInstance().fetchApiKey { apiKey ->
            apiKeyLocal = apiKey
            latch.countDown()
        }

        try {
            latch.await()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        return apiKeyLocal ?: ""
    }


    private fun savePromptHint(hint: String) {
        val sharedPreferences = getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("promptHint", hint)
        editor.apply()
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            1001 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                } else {
                    Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun saveImageUrls() {
        val sharedPreferences = getSharedPreferences("imageUrls", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(imageUrls)
        editor.putString("imageList", json)
        editor.apply()
    }

    private fun loadPromptHint(): String? {
        val sharedPreferences = getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("promptHint", null)
    }

    private fun clearPromptHint() {
        val sharedPreferences = getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.remove("promptHint")
        editor.apply()
    }





    private fun loadImageUrls() {
        val sharedPreferences = getSharedPreferences("imageUrls", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("imageList", null)
        val type: Type = object : TypeToken<MutableList<String>>() {}.type
        val tempList = gson.fromJson<MutableList<String>>(json, type)
        if(tempList != null) {
            imageUrls = tempList
        }

    }


    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, PurchaseActivity.RC_SIGN_IN)
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

    private fun checkSubscriptionAndStartActivity() {
        userHasSubscription { hasSubscription ->
            if (hasSubscription) {
                checkSubscriptionType { subscriptionType ->
                    runOnUiThread {
                        if (subscriptionType == "monthly" || subscriptionType == "yearly") {
                            // Start PurchaseActivity2
                            val intent = Intent(this@ImageActivity, PurchaseActivity2::class.java)
                            startActivity(intent)
                        } else {
                            // Start PurchaseActivity
                            val intent = Intent(this@ImageActivity, PurchaseActivity::class.java)
                            startActivity(intent)
                        }
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    }
                }
            } else {
                runOnUiThread {
                    val intent = Intent(this@ImageActivity, PurchaseActivity::class.java)
                    startActivity(intent)
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
                }
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun requestImagesFromAPI(prompt: String, count: Int) {

        if (auth.currentUser != null) {
            println("NOT NULL")
            val docRef = db.collection("users").document(auth.currentUser!!.uid)

            docRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        println("DOC EXISTS")
                        val credits = document.getLong("credits") ?: 0

                        if (credits.toInt() < 4) {
                            checkSubscriptionAndStartActivity()
                        } else {
                            println("HAVE CREDITS")
                            docRef.update("credits", FieldValue.increment(-4))
                                .addOnSuccessListener {
                                    println("HAVE CREEDITS")

                                    val loadingLayout = findViewById<ImageView>(R.id.loading)
                                    if (isRequestInProgress) return@addOnSuccessListener

                                    isRequestInProgress = true

                                    val requests = List(4) {
                                        val request = ImageCreationRequest(prompt, count / 4, "512x512")
                                        service.createImage(request)
                                    }

                                    val deferreds = requests.map { it.enqueueToDeferred() }

                                    CoroutineScope(Dispatchers.Main).launch {
                                        try {
                                            val responses = deferreds.awaitAll()

                                            val newImageUrls = responses.flatMap {
                                                it.body()?.data?.map { image -> image.url } ?: emptyList()
                                            }

                                            var imageListView: ListView = findViewById(R.id.imageListView)
                                            val largeLoading: ImageView = findViewById(R.id.largeLoading)
                                            val instruction: TextView = findViewById(R.id.instruction)

                                            if (newImageUrls.isNotEmpty()) {
                                                largeLoading.visibility = View.GONE
                                                imageListView.visibility = View.VISIBLE
                                                instruction.visibility = View.GONE
                                                imageUrls.addAll(newImageUrls)
                                                adapter.notifyDataSetChanged()





                                                if (!flag) {
                                                    generateExtraImagesLayout = layoutInflater.inflate(R.layout.generate_extra_images, null)
                                                    generateImageView = generateExtraImagesLayout.findViewById<ImageView>(R.id.gen)
                                                    loadingImageView = generateExtraImagesLayout.findViewById<ImageView>(R.id.loading)

                                                    imageListView.addFooterView(generateExtraImagesLayout)

                                                    flag = true
                                                }




                                                val gifImageView = generateExtraImagesLayout.findViewById<ImageView>(R.id.loading)
                                                Glide.with(this@ImageActivity)
                                                    .asGif()
                                                    .load(R.drawable.loading)
                                                    .into(gifImageView)

                                                generateImageView.visibility = View.VISIBLE
                                                loadingImageView.visibility = View.GONE

                                                generateImageView.setOnClickListener {
                                                    requestImagesFromAPI(prompt, 4)
                                                    generateImageView.visibility = View.GONE
                                                    loadingImageView.visibility = View.VISIBLE
                                                }


                                            } else { }


                                            isRequestInProgress = false
                                            loadingLayout.visibility = View.GONE

                                        } catch (e: Exception) {
                                            isRequestInProgress = false
                                            loadingLayout.visibility = View.GONE
                                        }

                                        saveImageUrls()
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



    private fun Call<ImageCreationResponse>.enqueueToDeferred() = CompletableDeferred<Response<ImageCreationResponse>>().apply {
        enqueue(object : Callback<ImageCreationResponse> {
            override fun onResponse(call: Call<ImageCreationResponse>, response: Response<ImageCreationResponse>) {
                complete(response)
            }
            override fun onFailure(call: Call<ImageCreationResponse>, t: Throwable) {
            }
        })
    }
}


interface DallEApiService {
    @POST("v1/images/generations")
    fun createImage(@Body request: ImageCreationRequest): Call<ImageCreationResponse>

    @POST("v1/images/edits")
    fun editImage(
        @Body request: ImageEditRequest
    ): Call<ImageCreationResponse>
}

data class ImageCreationRequest(val prompt: String, val n: Int, val size: String)
data class ImageCreationResponse(val data: List<ImageData>)
data class ImageData(val url: String)



