package com.ouroboros.aimobileapp

import android.provider.Settings
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import android.widget.AbsListView.OnScrollListener.SCROLL_STATE_IDLE
import androidx.annotation.RequiresApi
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
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.ouroboros.aimobileapp.MyApplication.Companion.context
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Math.abs
import java.lang.reflect.Type
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

class ImageActivity: AppCompatActivity() {

    private lateinit var adapter: ImageItemAdapter
    private lateinit var adapter2: ImageItemAdapter

    private var imageUrls = mutableListOf<String>()
    private var imageUrls2 = mutableListOf<String>()
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
    private var generationType = 1

    private var newImageUrls = mutableListOf<String>()


    private lateinit var listView1: ListView
    private lateinit var listView2: ListView
    private lateinit var instruction: TextView


    private lateinit var indicator: View
    private lateinit var topLayout: View

    private var initialMargin = 0

    private lateinit var parentLayout: LinearLayout
    private var initialX = 0f
    private var threshold = 100
    private var loadFlag = false
    private var loadFlag2 = false


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

    val requestBody = MultipartBody.Builder()
        .setType(MultipartBody.FORM)
        .addFormDataPart("model_version", "1")
        .addFormDataPart("style_id", "30")
        .build()

    val gson = GsonBuilder()
        .setLenient()
        .create()





    private val client2 = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("bearer", "vk-RVQzGWGi0L3gOuGClbXqALG59xkD8jD8FDjkR5PdLSs6s")
                .build()
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

    private val retrofit2: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.vyro.ai/")
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(client2)
        .build()

    private val service: DallEApiService = retrofit.create(DallEApiService::class.java)
    private val service2: ImagineApiService = retrofit2.create(ImagineApiService::class.java)



    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_image)

        instruction = findViewById(R.id.instruction)



        val indicator = findViewById<View>(R.id.indicator)
        val topLayout = findViewById<View>(R.id.topLayout)
        val parentLayout = findViewById<View>(R.id.parentLayout)

        val parentLayoutWidth = parentLayout.width.toFloat()

        val initialIndicatorWidth = indicator.width
        var initialX = indicator.x
        var threshold = 0f
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var moved = false
        var end = false
        var clampedX = 0f

        val text1 = findViewById<TextView>(R.id.text1)
        val text2 = findViewById<TextView>(R.id.text2)


        text1.text = Html.fromHtml("<b>DALL-E</b>")

        listView1 = findViewById<ListView>(R.id.imageListView)
        listView2 = findViewById<ListView>(R.id.imageListView2)

        val promptEditText: EditText = findViewById(R.id.prompt)

        instruction = findViewById(R.id.instruction)


        text1.setOnClickListener {

            generationType = 1


            indicator.animate().translationX(0f).duration = 200

            clampedX = 0f


            text1.text = Html.fromHtml("<font color='#36454F'><b>DALL-E</b></font>")
            text2.text = Html.fromHtml("<font color='#8036454F'>MIDJOURNEY</font>")

            if (!loadFlag2) {
                loadImageUrls()

                if (imageUrls.isNotEmpty()) {

                    loadFlag2 = true

                    adapter2.notifyDataSetChanged()

                    listView1.visibility = View.VISIBLE
                    listView2.visibility = View.GONE
                    instruction.visibility = View.GONE



                    loadPromptHint2()?.let {
                        promptEditText.hint = it
                    }
                } else if (!imageUrls.isNotEmpty()) {
                    listView1.visibility = View.GONE
                    listView2.visibility = View.GONE
                    instruction.visibility = View.VISIBLE
                }
            }  else if (loadFlag2) {
                listView1.visibility = View.VISIBLE
                listView2.visibility = View.GONE
                instruction.visibility = View.GONE

                loadPromptHint2()?.let {
                    promptEditText.hint = it
                }
            }




        }

        text2.setOnClickListener {
            end = true

            val maxX = parentLayout.width / 2 - initialIndicatorWidth
            indicator.animate().translationX(maxX.toFloat()).duration = 200

            clampedX = maxX.toFloat()



            text1.text = Html.fromHtml("<font color='#8036454F'>DALL-E</font>")
            text2.text = Html.fromHtml("<font color='#36454F'><b>MIDJOURNEY</b></font>")


            generationType = 2

            if (!loadFlag) {
                loadImageUrls2()

                if (newImageUrls.isNotEmpty()) {

                    loadFlag = true

                    imageUrls2.addAll(newImageUrls)
                    adapter.notifyDataSetChanged()

                    listView1.visibility = View.GONE
                    listView2.visibility = View.VISIBLE
                    instruction.visibility = View.GONE


                    loadPromptHint1()?.let {
                        promptEditText.hint = it
                    }
                } else if (!newImageUrls.isNotEmpty()) {
                    listView1.visibility = View.GONE
                    listView2.visibility = View.GONE
                    instruction.visibility = View.VISIBLE
                }
            } else if (loadFlag) {
                listView1.visibility = View.GONE
                listView2.visibility = View.VISIBLE
                instruction.visibility = View.GONE

                loadPromptHint1()?.let {
                    promptEditText.hint = it
                }
            }
        }


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

        loadPromptHint2()?.let {
            promptEditText.hint = it
        }



        if (imageUrls.isNotEmpty()) {
            Log.d("AAAAA", "OOOO")
            val imageListView: ListView = findViewById(R.id.imageListView)
            imageListView.visibility = View.VISIBLE
            instruction.visibility = View.GONE


        } else {
            Log.d("AAAAA", "OOOO1")
            val imageListView: ListView = findViewById(R.id.imageListView)
            imageListView.visibility = View.GONE
            instruction.visibility = View.VISIBLE
        }


        adapter = ImageItemAdapter(this, imageUrls)
        adapter2 = ImageItemAdapter(this, imageUrls2)
        val listView: ListView = findViewById(R.id.imageListView)
        val listView2: ListView = findViewById(R.id.imageListView2)

        listView.adapter = adapter
        listView2.adapter = adapter2

        val titleTextView: TextView = findViewById(R.id.instruction)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            titleTextView.text = Html.fromHtml(getString(R.string.prompt_instruction), Html.FROM_HTML_MODE_LEGACY)
        } else {
            titleTextView.text = Html.fromHtml(getString(R.string.prompt_instruction))
        }


        val generateButton: Button = findViewById(R.id.generate)
        val largeLoading: ImageView = findViewById(R.id.largeLoading)

        generateButton.setOnClickListener {

            val promptText = promptEditText.text.toString().trim()

            promptEditText.clearFocus()

            if (promptText.isNotEmpty()) {

                promptEditText.hint = promptText

                if (generationType == 1) {
                    savePromptHint1(promptText)
                } else if (generationType == 2) {
                    savePromptHint2(promptText)
                }

                if (imageUrls.isNotEmpty()) {
                    println("NOT EMPTY")
                    imageUrls.clear()
                    saveImageUrls()
                }

                if (newImageUrls.isNotEmpty()) {
                    Log.d("DONSS", "SAVEDDD")
                    newImageUrls.clear()
                    saveImageUrls2()
                }


                val imageListView: ListView = findViewById(R.id.imageListView)
                val largeLoading: ImageView = findViewById(R.id.largeLoading)
                largeLoading.visibility = View.VISIBLE
                imageListView.visibility = View.GONE

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(promptEditText.windowToken, 0)

                promptEditText.hint = promptText
                promptEditText.setText("")

                if (generationType == 1) {
                    requestImagesFromAPI(promptText, 4)
                } else if (generationType == 2) {
                    requestImagesFromAPI2(promptText)
                    adapter.notifyDataSetChanged()
                }

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

    @RequiresApi(Build.VERSION_CODES.N)
    private fun toggleGravity() {
        val newGravity = if (parentLayout.gravity == Gravity.START) Gravity.END else Gravity.START
        parentLayout.gravity = newGravity
        // You can add animation/transition code here for a smooth effect
    }




    private fun savePromptHint1(hint: String) {
        val sharedPreferences = getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("promptHint1", hint)
        editor.apply()
    }

    private fun savePromptHint2(hint: String) {
        val sharedPreferences = getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putString("promptHint2", hint)
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

    private fun saveImageUrls2() {
        Log.d("SAVEDD", "SAVVY")
        val sharedPreferences = getSharedPreferences("imageUrls2", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        val gson = Gson()
        val json = gson.toJson(newImageUrls)
        editor.putString("imageList2", json)
        editor.apply()
    }

    private fun loadPromptHint1(): String? {
        val sharedPreferences = getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("promptHint1", null)
    }

    private fun loadPromptHint2(): String? {
        val sharedPreferences = getSharedPreferences("appPrefs", Context.MODE_PRIVATE)
        return sharedPreferences.getString("promptHint2", null)
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

    private fun loadImageUrls2() {
        Log.d("LARGEE", "LARGO")
        val sharedPreferences = getSharedPreferences("imageUrls2", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = sharedPreferences.getString("imageList2", null)
        val type: Type = object : TypeToken<MutableList<String>>() {}.type
        val tempList = gson.fromJson<MutableList<String>>(json, type)
        if(tempList != null) {
            newImageUrls = tempList
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
                        callback(false)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("TAGG", "Firestore query failed: $exception")
                    callback(false)
                }
        } else {
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
                        Log.d("DOC EXISTS", "DOC EXISTS")
                        val credits = document.getLong("credits") ?: 0

                        if (credits.toInt() < 4) {
                            checkSubscriptionAndStartActivity()
                        } else {
                            Log.d("HAVE CREDITS", "HAVE CREDITS")
                            docRef.update("credits", FieldValue.increment(-4))
                                .addOnSuccessListener {
                                    Log.d("HAVE CREEDITS", "HAVE CREEDITS")

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

                                            if (newImageUrls.isNotEmpty()) {
                                                largeLoading.visibility = View.GONE
                                                imageListView.visibility = View.VISIBLE
                                                instruction.visibility = View.GONE
                                                imageUrls.addAll(newImageUrls)
                                                adapter2.notifyDataSetChanged()


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

    @SuppressLint("MissingInflatedId")
    private fun requestImagesFromAPI2(prompt: String) {
        if (auth.currentUser != null) {
            Log.d("NOT NULL", "NOT NULL")
            val docRef = db.collection("users").document(auth.currentUser!!.uid)

            docRef.get()
                .addOnSuccessListener { document ->
                    if (document != null && document.exists()) {
                        Log.d("DOC EXISTS", "DOC EXISTS")
                        val credits = document.getLong("credits") ?: 0

                        if (credits.toInt() < 4) {
                            checkSubscriptionAndStartActivity()
                        } else {
                            Log.d("HAVE CREDITS", "HAVE CREDITS")
                            docRef.update("credits", FieldValue.increment(-4))
                                .addOnSuccessListener {
                                    Log.d("HAVE CREDITS", "HAVE CREDITS")

                                    val loadingLayout = findViewById<ImageView>(R.id.loading)
                                    if (isRequestInProgress) return@addOnSuccessListener

                                    isRequestInProgress = true

                                    val requests = List(4) {
                                        val modelVersion = RequestBody.create("text/plain".toMediaTypeOrNull(), "1")
                                        val promptPart = RequestBody.create("text/plain".toMediaTypeOrNull(), prompt)
                                        val styleId = RequestBody.create("text/plain".toMediaTypeOrNull(), "30")
                                        val highResResults = RequestBody.create("text/plain".toMediaTypeOrNull(), "1")

                                        service2.createImage(modelVersion, promptPart, styleId, highResResults)
                                    }





                                    requests.forEach { call ->
                                        call.enqueue(object : Callback<ResponseBody> {
                                            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                                                val imageBytes = response.body()?.bytes()
                                                val imagePath = saveBitmapToFile(imageBytes, this@ImageActivity)

                                                Log.d("REPONSEE", response.toString())
                                                Log.d("REPONSEE", response.body().toString())

                                                // Update newImageUrls
                                                if (imagePath != null) {
                                                    newImageUrls.add(imagePath)
                                                }

                                                // If all images are processed, update the ListView
                                                if (newImageUrls.size == requests.size) {
                                                    updateListViewWithNewImages()
                                                }
                                            }

                                            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                                                Log.d("FAILL","API call failed: ${t.message}")
                                            }
                                        })
                                    }

                                    var imageListView: ListView = findViewById(R.id.imageListView2)
                                    val largeLoading: ImageView = findViewById(R.id.largeLoading)

                                    if (newImageUrls.isNotEmpty()) {



                                        largeLoading.visibility = View.GONE
                                        imageListView.visibility = View.VISIBLE
                                        instruction.visibility = View.GONE
                                        imageUrls2.addAll(newImageUrls)
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
                                            requestImagesFromAPI2(prompt)
                                            generateImageView.visibility = View.GONE
                                            loadingImageView.visibility = View.VISIBLE
                                        }


                                    }

                                    isRequestInProgress = false
                                    loadingLayout.visibility = View.GONE

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


    fun saveBitmapToFile(imageBytes: ByteArray?, context: Context): String? {
        Log.d("CALLEDDD", "CALLEDDD")
        // Convert the image bytes into a bitmap
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes?.size ?: 0)

        // Create a unique file name using the current timestamp
        val fileName = "img_${System.currentTimeMillis()}.jpg"
        val file = File(context.cacheDir, fileName)

        // Write the bitmap to a file
        try {
            val outputStream = FileOutputStream(file)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }

        return file.absolutePath
    }

    fun updateListViewWithNewImages() {

        Log.d("CALLEDDD11", "CALLEDDD11")

        if (newImageUrls.isNotEmpty()) {

            loadFlag = true

            saveImageUrls2()

            Log.d("EMPTYY ", "CALLEDDD11")
            imageUrls2.addAll(newImageUrls)
            adapter.notifyDataSetChanged()

            // UI updates
            val largeLoading: ImageView = findViewById(R.id.largeLoading)
            val imageListView: ListView = findViewById(R.id.imageListView2)

            largeLoading.visibility = View.GONE
            imageListView.visibility = View.VISIBLE
            instruction.visibility = View.GONE

            // Clear newImageUrls for any subsequent API calls
            newImageUrls.clear()
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

interface ImagineApiService {

    @Multipart
    @POST("v1/imagine/api/generations")
    fun createImage(
        @Part("model_version") modelVersion: RequestBody,
        @Part("prompt") promptPart: RequestBody,
        @Part("style_id") styleId: RequestBody,
        @Part("high_res_results") highResResults: RequestBody

    ): Call<ResponseBody>

}





data class ImageCreationRequest(val prompt: String, val n: Int, val size: String)

data class ImageCreationRequest2(val model_version: String, val prompt: String, val style_id: String)
data class ImageCreationResponse(val data: List<ImageData>)
data class ImageData(val url: String)



