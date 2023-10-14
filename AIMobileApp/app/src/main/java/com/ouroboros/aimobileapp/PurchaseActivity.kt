package com.ouroboros.aimobileapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.SkuDetails
import com.android.billingclient.api.SkuDetailsParams
import com.bumptech.glide.Glide
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import pl.droidsonroids.gif.GifDrawable

class PurchaseActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var db: FirebaseFirestore

    private lateinit var billingClient: BillingClient

    private lateinit var monthlySkuDetails: SkuDetails
    private lateinit var yearlySkuDetails: SkuDetails

    private lateinit var gifDrawable: GifDrawable

    private lateinit var loadingLayout: LinearLayout


    @SuppressLint("MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase)

        val monthlyRadioButton = findViewById<RadioButton>(R.id.monthly)
        val yearlyRadioButton = findViewById<RadioButton>(R.id.yearly)
        val monthlyLayout = findViewById<LinearLayout>(R.id.linearLayou36)
        val yearlyLayout = findViewById<LinearLayout>(R.id.linearLayou37)

        val exit = findViewById<ImageView>(R.id.exit)

        exit.setOnClickListener{
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("exit","exit")
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }

        loadingLayout = findViewById<LinearLayout>(R.id.loadingLayout)

        // Set default state
        yearlyRadioButton.isChecked = true
        yearlyLayout.setBackgroundResource(R.drawable.radio_background)
        monthlyLayout.setBackgroundResource(android.R.color.transparent)

        monthlyLayout.setOnClickListener {
            if (!monthlyRadioButton.isChecked) {
                monthlyRadioButton.isChecked = true
                yearlyRadioButton.isChecked = false
                monthlyLayout.setBackgroundResource(R.drawable.radio_background)
                yearlyLayout.setBackgroundResource(android.R.color.transparent)
            }
        }

        yearlyLayout.setOnClickListener {
            if (!yearlyRadioButton.isChecked) {
                yearlyRadioButton.isChecked = true
                monthlyRadioButton.isChecked = false
                yearlyLayout.setBackgroundResource(R.drawable.radio_background)
                monthlyLayout.setBackgroundResource(android.R.color.transparent)
            }
        }

        // Ensure mutual exclusivity for radio buttons too
        monthlyRadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                yearlyRadioButton.isChecked = false
                monthlyLayout.setBackgroundResource(R.drawable.radio_background)
                yearlyLayout.setBackgroundResource(android.R.color.transparent)
            }
        }

        yearlyRadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                monthlyRadioButton.isChecked = false
                yearlyLayout.setBackgroundResource(R.drawable.radio_background)
                monthlyLayout.setBackgroundResource(android.R.color.transparent)
            }
        }

        setupBillingClient()

        val continueButton = findViewById<Button>(R.id.cont)
        continueButton.setOnClickListener {
            val selectedSubscription: SkuDetails = if (monthlyRadioButton.isChecked) {
                monthlySkuDetails
            } else {
                yearlySkuDetails
            }

            val billingFlowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(selectedSubscription)
                .build()

            val billingResult = billingClient.launchBillingFlow(this, billingFlowParams)
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e("BillingFlow", "Error: ${billingResult.responseCode}, Debug Message: ${billingResult.debugMessage}")
            }
        }


        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()


        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)


    }

    private fun setupBillingClient() {
        val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && purchases != null) {
                for (purchase in purchases) {
                    handlePurchase(purchase)  // Call a method to handle the purchase
                }
            } else {
                Log.e("PurchaseError", "Error: ${billingResult.responseCode}, Debug Message: ${billingResult.debugMessage}")
            }
        }

        billingClient = BillingClient.newBuilder(this)
            .setListener(purchasesUpdatedListener)
            .enablePendingPurchases()
            .build()

        startBillingConnection()
    }

    private fun startBillingConnection() {
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    querySkuDetails()
                } else {
                    Log.e("BillingSetup", "Error: ${billingResult.responseCode}, Debug Message: ${billingResult.debugMessage}")
                }
            }

            override fun onBillingServiceDisconnected() {
                // Implementing a simple retry mechanism.
                Handler(Looper.getMainLooper()).postDelayed({ startBillingConnection() }, 2000)
            }
        })
    }

    private fun querySkuDetails() {
        val params = SkuDetailsParams.newBuilder()
            .setSkusList(listOf("monthly", "yearly"))
            .setType(BillingClient.SkuType.SUBS)
            .build()

        billingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                for (skuDetails in skuDetailsList) {
                    val skuId = skuDetails.sku
                    if (skuId == "monthly") {
                        monthlySkuDetails = skuDetails
                    } else if (skuId == "yearly") {
                        yearlySkuDetails = skuDetails
                    }
                }
            } else {
                // Handle any error responses.
                Log.e("BillingResult", "Response Code: ${billingResult.responseCode}, Debug Message: ${billingResult.debugMessage}")
            }
        }
    }



    override fun onStart() {
        super.onStart()

        //For sign out
        //FirebaseAuth.getInstance().signOut()
        //googleSignInClient.signOut()
        //

    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)

                val intent = Intent(this, MainActivity::class.java)
                intent.putExtra("signed in", "signed in")
                startActivity(intent)
                overridePendingTransition(R.anim.fade_in, R.anim.fade_out)

            } catch (e: ApiException) {
                println("Google sign in failed with status code: ${e.statusCode}")
                e.printStackTrace()            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    user?.let {
                        checkIfUserExistsOrAssignCredits(it.uid)
                    }
                } else {
                    println("signInWithCredential:failure")
                }
            }
    }

    private fun handlePurchase(purchase: Purchase) {
        val purchaseToken = purchase.purchaseToken
        val userUid = auth.currentUser?.uid ?: run {
            Log.w("Auth", "User is not signed in or UID is not available.")
            return
        }

        val purchaseDocRef = db.collection("purchases").document(purchaseToken)

        val purchaseData = hashMapOf(
            "purchaseToken" to purchaseToken,
            "userUid" to userUid
        )

        purchaseDocRef.set(purchaseData)
            .addOnSuccessListener {
                Log.d("Firestore", "DocumentSnapshot successfully written!")
                when (purchase.skus[0]) {
                    "monthly" -> {

                        val gifImageView = findViewById<ImageView>(R.id.loading)
                        Glide.with(this)
                            .asGif()
                            .load(R.drawable.loading)
                            .into(gifImageView)

                        val purchaseLayout = findViewById<LinearLayout>(R.id.purchaseLayout)


                        loadingLayout.visibility = View.VISIBLE
                        purchaseLayout.visibility = View.GONE

                        getUserCredits(userUid, purchase, "monthly")


                    }
                    "yearly" -> {

                        val gifImageView = findViewById<ImageView>(R.id.loading)
                        Glide.with(this)
                            .asGif()
                            .load(R.drawable.loading)
                            .into(gifImageView)

                        val purchaseLayout = findViewById<LinearLayout>(R.id.purchaseLayout)


                        loadingLayout.visibility = View.VISIBLE
                        purchaseLayout.visibility = View.GONE

                        getUserCredits(userUid, purchase, "yearly")                    }
                    else -> {
                        Log.e("PurchaseError", "Unrecognized SKU: ${purchase.skus[0]}")
                    }
                }

            }
            .addOnFailureListener { e ->
                Log.w("Firestore", "Error writing document", e)
            }
    }

    private fun getUserCredits(userUid: String, purchase: Purchase, subscriptionId: String) {
        val userDocRef = db.collection("users").document(userUid)
        userDocRef.get()
            .addOnSuccessListener { document ->
                val currentCredits = document.getLong("credits") ?: 0
                // Now trigger the function to update the credits
                updateCredits(userUid, currentCredits, purchase, subscriptionId)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error getting document", e)
            }
    }

    private fun updateCredits(userUid: String, currentCredits: Long, purchase: Purchase, subscriptionId: String) {
        val userDocRef = db.collection("users").document(userUid)

        var creditsToAdd = 0

        if (subscriptionId == "monthly") {
            creditsToAdd = 100
        } else if (subscriptionId == "yearly") {
            creditsToAdd = 1200
        }

        userDocRef.update("credits", currentCredits + creditsToAdd)
            .addOnSuccessListener {
                // Now fetch the updated credits to verify the update
                checkUpdatedCredits(userUid, currentCredits, purchase, subscriptionId)
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error updating credits", e)
            }
    }

    private fun checkUpdatedCredits(userUid: String, oldCredits: Long, purchase: Purchase, subscriptionId: String) {
        val userDocRef = db.collection("users").document(userUid)

        var creditsToAdd = 0

        if (subscriptionId == "monthly") {
            creditsToAdd = 100
        } else if (subscriptionId == "yearly") {
            creditsToAdd = 1200
        }

        userDocRef.get()
            .addOnSuccessListener { document ->
                val updatedCredits = document.getLong("credits") ?: 0
                if (updatedCredits == oldCredits + creditsToAdd) {

                    // Update the subscriptionId field
                    userDocRef.update("subscriptionId", subscriptionId)
                        .addOnSuccessListener {
                            // Acknowledge the purchase after updating the subscriptionId
                            acknowledgePurchase(purchase)
                        }
                        .addOnFailureListener { e ->
                            // Handle error: Unable to update subscriptionId
                            Log.e("Firestore", "Error updating subscriptionId", e)
                        }
                } else {
                    // Handle error: Credits not updated correctly
                    Log.e("CreditsUpdateError", "Credits not updated correctly")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error getting document", e)
            }
    }


    private fun acknowledgePurchase(purchase: Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                Log.d("Billing", "Purchase acknowledged successfully")

                runOnUiThread {
                    loadingLayout.visibility = View.GONE

                    val gifImageView = findViewById<ImageView>(R.id.coins)
                    Glide.with(this)
                        .asGif()
                        .load(R.drawable.coins)
                        .into(gifImageView)

                    val purchasedLayout = findViewById<LinearLayout>(R.id.purchasedLayout)
                    purchasedLayout.visibility = View.VISIBLE

                    val delayMillis: Long = 3000

                    Handler(Looper.getMainLooper()).postDelayed({
                        val intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
                    }, delayMillis)
                }



            } else {
                Log.e("Billing", "Failed to acknowledge purchase: ${billingResult.debugMessage}")
            }
        }
    }



    private fun checkIfUserExistsOrAssignCredits(uid: String) {
        val db = FirebaseFirestore.getInstance()
        val userRef = db.collection("users").document(uid)
        userRef.get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    // User exists, you can read their credits if needed
                    val credits = document.getLong("credits") ?: 0
                    println("User has $credits credits.")
                } else {
                    // New user, assign 0 credits
                    val userCredits = hashMapOf(
                        "credits" to 0
                    )
                    userRef.set(userCredits)
                        .addOnSuccessListener {
                            println("Assigned 0 credits to new user.")
                        }
                        .addOnFailureListener { e ->
                            println("Error writing document")
                        }
                }
            }
            .addOnFailureListener { exception ->
                println("get failed with ")
            }
    }


    companion object {
        const val RC_SIGN_IN = 9001
    }

}



