package com.ouroboros.aimobileapp

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.TypedValue
import android.view.*
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.annotation.RequiresApi
import androidx.appcompat.widget.AppCompatSeekBar
import androidx.core.animation.doOnEnd
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.ContextCompat.startActivity
import androidx.core.content.FileProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.*
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


class ImageItemAdapter(val context: Context, val images: MutableList<String>) : BaseAdapter() {

    private lateinit var currentImageView: ImageView
    private lateinit var canvasView: ExpandableCanvasView
    private var animationRunnable: Runnable? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var animationsRunning = false
    private val handler = Handler()
    private var stopAnimation = false
    private lateinit var textAndButtonHolder: LinearLayout
    private lateinit var textAndLoadingHolder: LinearLayout
    private lateinit var generateLayout: LinearLayout
    private var isDraw = false
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore



    override fun getCount(): Int = images.size / 2

    override fun getItem(position: Int): Pair<String, String> = Pair(images[position * 2], images[position * 2 + 1])

    override fun getItemId(position: Int): Long = position.toLong()

    @SuppressLint("MissingInflatedId")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View = LayoutInflater.from(context).inflate(R.layout.image_item, parent, false)

        val imageView1: ImageView = view.findViewById(R.id.imageView1)
        val imageView2: ImageView = view.findViewById(R.id.imageView2)


        val currentPair = getItem(position)

        Glide.with(context)
            .load(currentPair.first)
            .thumbnail(0.001f)
            .transition(DrawableTransitionOptions.withCrossFade())
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    imageView1.setOnClickListener {
                        showImageFullScreen(currentPair.first)
                    }
                    return false
                }
            })
            .into(imageView1)

        Glide.with(context)
            .load(currentPair.second)
            .thumbnail(0.001f)
            .transition(DrawableTransitionOptions.withCrossFade())
            .listener(object : RequestListener<Drawable?> {
                override fun onLoadFailed(
                    e: GlideException?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    isFirstResource: Boolean
                ): Boolean {
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable?,
                    model: Any?,
                    target: Target<Drawable?>?,
                    dataSource: DataSource?,
                    isFirstResource: Boolean
                ): Boolean {
                    imageView2.setOnClickListener {
                        showImageFullScreen(currentPair.second)
                    }
                    return false
                }
            })
            .into(imageView2)






        return view
    }

    fun spToPx(sp: Float, context: Context): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics).toInt()
    }


    fun scaleViewToSize(view: View, targetWidth: Int, targetHeight: Int, duration: Long, endAction: (() -> Unit)?) {
        val startWidth = view.width
        val startHeight = view.height

        ValueAnimator.ofFloat(0f, 1f).apply {
            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                val currentWidth = startWidth + progress * (targetWidth - startWidth)
                val currentHeight = startHeight + progress * (targetHeight - startHeight)
                val layoutParams = view.layoutParams
                layoutParams.width = currentWidth.toInt()
                layoutParams.height = currentHeight.toInt()
                view.layoutParams = layoutParams
            }

            doOnEnd { endAction?.invoke() }

            this.duration = duration
            start()
        }
    }



    @SuppressLint("MissingInflatedId")
    private fun showImageFullScreen(url: String) {
        val dialog = Dialog(context, R.style.DialogStyle)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_fullscreen_image, null)

        dialog.window?.attributes?.windowAnimations = R.style.DialogAnimation


        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)


        val backArrow = view.findViewById<ImageView>(R.id.backArrow)

        backArrow.setOnClickListener {
            dialog.dismiss()
        }

        canvasView = view.findViewById(R.id.expandableCanvas)
        currentImageView = view.findViewById<ImageView>(R.id.dialog_image)

        currentImageView.setOnClickListener {
            dialog.dismiss()
        }

        val promptEditText = view.findViewById<EditText>(R.id.prompt_edit_text)
        val stroke_size_slider_layout = view.findViewById<LinearLayout>(R.id.stroke_size_slider_layout)
        val canvasView: ExpandableCanvasView = view.findViewById(R.id.expandableCanvas)

        textAndButtonHolder = view.findViewById<LinearLayout>(R.id.textAndButtonHolder)
        textAndLoadingHolder = view.findViewById<LinearLayout>(R.id.textAndLoadingHolder)

        view.findViewById<ImageView>(R.id.edit).setOnClickListener {
            val expandableCanvas: ExpandableCanvasView = view.findViewById(R.id.expandableCanvas)
            Glide.with(context).load(url).into(currentImageView)
            val drawable = currentImageView.drawable as BitmapDrawable
            val bitmap = drawable.bitmap

            val targetWidth = spToPx(300f, context)
            val targetHeight = (currentImageView.height * (targetWidth.toFloat() / currentImageView.width)).toInt()

            scaleViewToSize(currentImageView, targetWidth, targetHeight, 200) {
                currentImageView.visibility = View.GONE
            }

            val canvasContainer = view.findViewById<RelativeLayout>(R.id.canvasContainer)
            canvasContainer.setOnTouchListener { _, event ->
                val newEvent = MotionEvent.obtain(
                    event.downTime,
                    event.eventTime,
                    event.action,
                    event.x - canvasView.left,
                    event.y - canvasView.top,
                    event.metaState
                )
                canvasView.dispatchTouchEvent(newEvent)
                true
            }

            expandableCanvas.undoButton = view.findViewById(R.id.undo)


            val undoButton: ImageView = view.findViewById(R.id.undo)
            val redoButton: ImageView = view.findViewById(R.id.redo)

            undoButton.setOnClickListener {
                expandableCanvas.undo()

                if (expandableCanvas.bitmapUndoStack.isEmpty()) {
                    undoButton.setImageResource(R.drawable.undo2)
                } else {
                    undoButton.setImageResource(R.drawable.undo)
                }

                if (expandableCanvas.bitmapRedoStack.isEmpty()) {
                    redoButton.setImageResource(R.drawable.undo2)
                } else {
                    redoButton.setImageResource(R.drawable.undo)
                }

            }

            redoButton.setOnClickListener {
                expandableCanvas.redo()

                if (expandableCanvas.bitmapUndoStack.isEmpty()) {
                    undoButton.setImageResource(R.drawable.undo2)
                } else {
                    undoButton.setImageResource(R.drawable.undo)
                }

                if (expandableCanvas.bitmapRedoStack.isEmpty()) {
                    redoButton.setImageResource(R.drawable.undo2)
                } else {
                    redoButton.setImageResource(R.drawable.undo)
                }
            }


            val strokeSizeSlider: SeekBar = view.findViewById(R.id.stroke_size_slider)

            strokeSizeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    expandableCanvas.setStrokeWidth(progress.toFloat())
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })

            canvasView.setImageBitmap(bitmap, false)

            val buttonsLayout = view.findViewById<LinearLayout>(R.id.buttonsLayout)
            buttonsLayout.visibility = View.GONE

            generateLayout = view.findViewById<LinearLayout>(R.id.generateLayout)
            val editButtonsLayout = view.findViewById<LinearLayout>(R.id.editButtonsLayout)

            val swipeDownAnimation = AnimationUtils.loadAnimation(context, R.anim.swipe_down)
            val swipeUpAnimation = AnimationUtils.loadAnimation(context, R.anim.swipe_up)

            editButtonsLayout.visibility = View.VISIBLE

            view.findViewById<Button>(R.id.generate).setOnClickListener {
                val promptText = promptEditText.text.toString()

                if (promptText.isNotEmpty()) {

                    canvasView.setCanvasInteractionEnabled(false)

                    val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    inputMethodManager.hideSoftInputFromWindow(currentImageView.windowToken, 0)

                    promptEditText.setText("")
                    promptEditText.hint = "Prompt"
                    promptEditText.clearFocus()
                    inputMethodManager.hideSoftInputFromWindow(promptEditText.windowToken, 0)

                    canvasView.expandCount = 0

                    val gifImageView = view.findViewById<ImageView>(R.id.loading)
                    Glide.with(view)
                        .asGif()
                        .load(R.drawable.loading)
                        .into(gifImageView)

                    textAndButtonHolder.visibility = View.GONE
                    textAndLoadingHolder.visibility = View.VISIBLE

                    stopAnimation = false
                    val canvasBitmap = canvasView.getBitmap() ?: return@setOnClickListener

                    val fadeInAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_in_loading)
                    val fadeOutAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_out_loading)

                    loopAnimations(canvasView, fadeOutAnimation, fadeInAnimation)

                    FirebaseManager.getInstance().fetchApiKey { apiKey ->
                        if (apiKey != null) {
                            editImageWithDALLE(context, canvasBitmap, promptText, canvasView, apiKey) { generatedBitmap ->
                                if (generatedBitmap != null) {
                                    loopAnimations(canvasView, fadeOutAnimation, fadeInAnimation)
                                } else {
                                }
                            }
                        } else {
                            Toast.makeText(context, "Error retrieving API Key!", Toast.LENGTH_SHORT).show()
                        }
                    }

                } else {
                    Toast.makeText(context, "Please Enter a Prompt!", Toast.LENGTH_SHORT).show()
                }

            }

            val moveButton: ImageView = view.findViewById(R.id.move)
            val edit_image: ImageView = view.findViewById(R.id.edit_image)

            expandableCanvas.setGenerateLayout(generateLayout)

            moveButton.setOnClickListener {
                expandableCanvas.toggleMoveModeOn()
                canvasView.toggleMoveModeOn()
                moveButton.setBackgroundColor(Color.parseColor("#80737373"))
                edit_image.setBackgroundColor(Color.TRANSPARENT)

                val stroke_size_slider_layout = view.findViewById<LinearLayout>(R.id.stroke_size_slider_layout)

                if (stroke_size_slider_layout.visibility == View.VISIBLE) {
                    stroke_size_slider_layout.visibility = View.GONE
                }
            }

            edit_image.setOnClickListener {

                if (!isDraw) {
                    isDraw = true
                    expandableCanvas.toggleMoveModeOff()
                    canvasView.toggleMoveModeOff()
                    edit_image.setBackgroundColor(Color.parseColor("#80737373"))
                    moveButton.setBackgroundColor(Color.TRANSPARENT)


                    expandableCanvas.toggleDrawModeOn()

                    val stroke_size_slider_layout =
                        view.findViewById<LinearLayout>(R.id.stroke_size_slider_layout)

                    if (stroke_size_slider_layout.visibility == View.GONE) {
                        stroke_size_slider_layout.visibility = View.VISIBLE
                    }
                } else {
                    isDraw = false
                    expandableCanvas.toggleMoveModeOff()
                    canvasView.toggleMoveModeOff()
                    edit_image.setBackgroundColor(Color.TRANSPARENT)
                    moveButton.setBackgroundColor(Color.TRANSPARENT)


                    expandableCanvas.toggleDrawModeOff()

                    val stroke_size_slider_layout =
                        view.findViewById<LinearLayout>(R.id.stroke_size_slider_layout)

                    if (stroke_size_slider_layout.visibility == View.VISIBLE) {
                        stroke_size_slider_layout.visibility = View.GONE
                    }
                }

            }

            view.findViewById<ImageView>(R.id.expand).setOnClickListener {
                canvasView.expandCanvas(300)
                if (generateLayout.visibility == View.GONE) {
                    generateLayout.visibility = View.VISIBLE
                    generateLayout.startAnimation(swipeDownAnimation)
                }
            }

        }

        Glide.with(context).load(url).into(currentImageView)

        view.findViewById<ImageView>(R.id.download).setOnClickListener {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 1001)
            } else {
                val dialogImageView: ImageView = view.findViewById(R.id.dialog_image)
                downloadImageFromImageView(dialogImageView)
            }
        }

        view.findViewById<ImageView>(R.id.download2).setOnClickListener {
            if (!canvasView.isAnimating) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    ActivityCompat.requestPermissions(
                        context as Activity,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        1001
                    )
                } else {
                    canvasView.workingBitmap?.let { it1 -> downloadBitmap(it1) }
                }
            }
        }


        val shareImageView = view.findViewById<ImageView>(R.id.share)
        shareImageView.setOnClickListener {
            shareImage(currentImageView, context)
        }

        dialog.setContentView(view)
        dialog.show()

    }

    private fun loopAnimations(canvasView: ExpandableCanvasView, vararg animations: Animation) {
        if (stopAnimation == false) {
            animationsRunning = true
            animationRunnable?.let { handler.removeCallbacks(it) }
            var currentIndex = 0

            animationRunnable = object : Runnable {
                override fun run() {
                    val animation = animations[currentIndex % animations.size]
                    canvasView.startAnimation(animation)
                    currentIndex++
                    handler.postDelayed(this, animation.duration)
                }
            }

            handler.post(animationRunnable as Runnable)
        }

    }


    private fun stopLoopingAnimations() {
        animationsRunning = false
        stopAnimation = true
        animationRunnable?.let { handler.removeCallbacks(it) }
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

                    if (context is Activity) {

                        context.runOnUiThread {
                            if (subscriptionType == "monthly" || subscriptionType == "yearly") {
                                // Start PurchaseActivity2
                                val intent = Intent(context, PurchaseActivity2::class.java)
                                context.startActivity(intent)
                            } else {
                                // Start PurchaseActivity
                                val intent = Intent(context, PurchaseActivity::class.java)
                                context.startActivity(intent)
                            }
                            context.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                        }
                    }
                }
            } else {
                if (context is Activity) {

                    context.runOnUiThread {
                        val intent = Intent(context, PurchaseActivity::class.java)
                        context.startActivity(intent)
                        context.overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    }
                }
            }
        }
    }

    private fun editImageWithDALLE(
        context: Context,
        canvasBitmap: Bitmap,
        prompt: String,
        canvasView: ExpandableCanvasView,
        openAIApiKey: String,
        callback: (Bitmap?) -> Unit
    ) {

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(context, gso)

        canvasView.startFadeAnimation()


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

                                val maskBitmap = Bitmap.createBitmap(canvasBitmap.width, canvasBitmap.height, Bitmap.Config.ARGB_8888)
                                val maskCanvas = Canvas(maskBitmap)

                                maskCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

                                maskCanvas.drawBitmap(canvasBitmap, 0f, 0f, null)

                                val maskFileName = "mask.png"
                                val maskFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), maskFileName)
                                val maskStream = FileOutputStream(maskFile)
                                maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, maskStream)
                                maskStream.close()

                                val expandedStream = FileInputStream(maskFile)
                                val expandedByteArray = expandedStream.readBytes()
                                expandedStream.close()

                                val client = OkHttpClient.Builder()
                                    .readTimeout(120, TimeUnit.SECONDS)
                                    .writeTimeout(120, TimeUnit.SECONDS)
                                    .connectTimeout(120, TimeUnit.SECONDS)
                                    .build()

                                val requestBody = MultipartBody.Builder()
                                    .setType(MultipartBody.FORM)
                                    .addFormDataPart(
                                        "image",
                                        "expanded_mask.png",
                                        RequestBody.create("image/png".toMediaTypeOrNull(), expandedByteArray)
                                    )
                                    .addFormDataPart("prompt", prompt)
                                    .build()

                                val request = Request.Builder()
                                    .url("https://api.openai.com/v1/images/edits")
                                    .addHeader(
                                        "Authorization",
                                        "Bearer $openAIApiKey"
                                    )
                                    .post(requestBody)
                                    .build()

                                client.newCall(request).enqueue(object : Callback {
                                    override fun onFailure(call: Call, e: IOException) {
                                        e.printStackTrace()
                                        callback(null)

                                        mainHandler.post {
                                            canvasView.stopFadeAnimation()
                                        }
                                    }

                                    override fun onResponse(call: Call, response: Response) {
                                        if (response.isSuccessful) {

                                            val responseBody = response.body?.string()
                                            val jsonObject = JSONObject(responseBody)
                                            val data = jsonObject.getJSONArray("data")
                                            val editedImageUrl = if (data.length() > 0) data.getJSONObject(0).getString("url") else null
                                            if (editedImageUrl != null) {
                                                (context as Activity).runOnUiThread {
                                                    loadImageOntoCanvas(editedImageUrl, canvasView, context)


                                                    val canvasBitmap = canvasView.getBitmap()
                                                    /*if (canvasBitmap != null) {
                                                        val newCanvasWidth = canvasBitmap.width
                                                        val newCanvasHeight = canvasBitmap.height
                                                        canvasView.resizeCanvas(newCanvasWidth, newCanvasHeight)
                                                    }*/

                                                    canvasView.stopFadeAnimation()

                                                    callback(canvasBitmap)
                                                }
                                            } else {
                                                println("URL NOT FOUND")
                                            }
                                        } else {
                                            println("ERROR")
                                            println("Error Body: ${response.body?.string()}")
                                            println("HTTP Status Code: ${response.code}")
                                            mainHandler.post {
                                                canvasView.stopFadeAnimation()
                                            }
                                        }
                                    }
                                })

                                println("SUCCESSS")
                                println("Credits decremented successfully")
                                }
                        }
                    }
                }

        } else {

        }

    }



    private fun loadImageOntoCanvas(imageUrl: String, canvasView: ExpandableCanvasView, context: Context) {

        Glide.with(context)
            .asBitmap()
            .load(imageUrl)
            .into(object : CustomTarget<Bitmap>() {
                override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                    canvasView.setImageBitmap(resource, true)
                    stopLoopingAnimations()
                    canvasView.setCanvasInteractionEnabled(true)
                    textAndButtonHolder.visibility = View.VISIBLE
                    textAndLoadingHolder.visibility = View.GONE
                    generateLayout.visibility = View.GONE
                    canvasView.checkerSize += canvasView.totalExpansionCount * 5
                }

                override fun onLoadCleared(placeholder: Drawable?) {
                }
            })
    }

    private fun checkStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestStoragePermission(activity: Activity) {
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
    }



    fun getRandomString(length: Int): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    private fun downloadImageFromImageView(imageView: ImageView) {
        val drawable = imageView.drawable as? BitmapDrawable ?: return
        val bitmap = drawable.bitmap
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageToDownloads(bitmap, context, getRandomString(16)+".jpg")
            Toast.makeText(context, "Image Saved!", Toast.LENGTH_SHORT).show()
        } else {
        }
    }

    private fun downloadBitmap(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageToDownloads(bitmap, context, getRandomString(16)+".jpg")
            Toast.makeText(context, "Image Saved!", Toast.LENGTH_SHORT).show()
        } else {
        }
    }


    @RequiresApi(Build.VERSION_CODES.Q)
    fun saveImageToDownloads(bitmap: Bitmap, context: Context, fileName: String) {
        val resolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }

        val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            val outputStream = resolver.openOutputStream(it)
            outputStream?.let { os ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, os)
                os.close()
            }
        } ?: run {
            Toast.makeText(context, "Image Saving Failed.", Toast.LENGTH_SHORT).show()        }
    }

    fun shareImage(imageView: ImageView, context: Context) {
        val bitmap = (imageView.drawable as BitmapDrawable).bitmap
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val stream = FileOutputStream("$cachePath/image_to_share.png")
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()

        val imagePath = File(context.cacheDir, "images")
        val newFile = File(imagePath, "image_to_share.png")
        val contentUri = FileProvider.getUriForFile(context, "com.ouroboros.aimobileapp.fileprovider", newFile)

        if (contentUri != null) {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                putExtra(Intent.EXTRA_STREAM, contentUri)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Share Image"))
        }
    }

}




