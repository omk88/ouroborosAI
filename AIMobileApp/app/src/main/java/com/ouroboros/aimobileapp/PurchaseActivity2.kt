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
import com.android.billingclient.api.ConsumeParams
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



class PurchaseActivity2 : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var db: FirebaseFirestore

    private lateinit var billingClient: BillingClient

    private lateinit var selectedProductSku: String
    private var skuDetailsList: List<SkuDetails> = emptyList()

    private lateinit var loadingLayout: LinearLayout



    @SuppressLint("MissingInflatedId")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_purchase_2)

        loadingLayout = findViewById<LinearLayout>(R.id.loadingLayout)

        val exit = findViewById<ImageView>(R.id.exit)

        exit.setOnClickListener{
            val intent = Intent(this, MainActivity::class.java)
            intent.putExtra("exit","exit")
            startActivity(intent)
            overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
        }


        val credits50RadioButton = findViewById<RadioButton>(R.id.credits50)
        val credits100RadioButton = findViewById<RadioButton>(R.id.credits100)
        val credits200RadioButton = findViewById<RadioButton>(R.id.credits200)
        val credits500RadioButton = findViewById<RadioButton>(R.id.credits500)

        val credits50Layout = findViewById<LinearLayout>(R.id.credits50Layout)
        val credits100Layout = findViewById<LinearLayout>(R.id.credits100Layout)
        val credits200Layout = findViewById<LinearLayout>(R.id.credits200Layout)
        val credits500Layout = findViewById<LinearLayout>(R.id.credits500Layout)



        // Set default state
        credits200RadioButton.isChecked = true
        credits200Layout.setBackgroundResource(R.drawable.radio_background)
        credits100Layout.setBackgroundResource(android.R.color.transparent)
        credits50Layout.setBackgroundResource(android.R.color.transparent)
        credits500Layout.setBackgroundResource(android.R.color.transparent)

        credits50Layout.setOnClickListener {

            selectedProductSku = "credits50"

            if (!credits50RadioButton.isChecked) {
                credits50RadioButton.isChecked = true
                credits100RadioButton.isChecked = false
                credits200RadioButton.isChecked = false
                credits500RadioButton.isChecked = false
                credits50Layout.setBackgroundResource(R.drawable.radio_background)
                credits100Layout.setBackgroundResource(android.R.color.transparent)
                credits200Layout.setBackgroundResource(android.R.color.transparent)
                credits500Layout.setBackgroundResource(android.R.color.transparent)

            }
        }

        credits100Layout.setOnClickListener {

            selectedProductSku = "credits100"

            if (!credits100RadioButton.isChecked) {
                credits100RadioButton.isChecked = true
                credits50RadioButton.isChecked = false
                credits200RadioButton.isChecked = false
                credits500RadioButton.isChecked = false
                credits100Layout.setBackgroundResource(R.drawable.radio_background)
                credits50Layout.setBackgroundResource(android.R.color.transparent)
                credits200Layout.setBackgroundResource(android.R.color.transparent)
                credits500Layout.setBackgroundResource(android.R.color.transparent)

            }
        }

        credits200Layout.setOnClickListener {

            selectedProductSku = "credits200"

            if (!credits200RadioButton.isChecked) {
                credits200RadioButton.isChecked = true
                credits50RadioButton.isChecked = false
                credits100RadioButton.isChecked = false
                credits500RadioButton.isChecked = false
                credits200Layout.setBackgroundResource(R.drawable.radio_background)
                credits50Layout.setBackgroundResource(android.R.color.transparent)
                credits100Layout.setBackgroundResource(android.R.color.transparent)
                credits500Layout.setBackgroundResource(android.R.color.transparent)

            }
        }

        credits500Layout.setOnClickListener {

            selectedProductSku = "credits500"

            if (!credits500RadioButton.isChecked) {
                credits500RadioButton.isChecked = true
                credits50RadioButton.isChecked = false
                credits100RadioButton.isChecked = false
                credits200RadioButton.isChecked = false
                credits500Layout.setBackgroundResource(R.drawable.radio_background)
                credits50Layout.setBackgroundResource(android.R.color.transparent)
                credits100Layout.setBackgroundResource(android.R.color.transparent)
                credits200Layout.setBackgroundResource(android.R.color.transparent)

            }
        }


        // Ensure mutual exclusivity for radio buttons too
        credits50RadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                credits100RadioButton.isChecked = false
                credits200RadioButton.isChecked = false
                credits500RadioButton.isChecked = false
                credits50Layout.setBackgroundResource(R.drawable.radio_background)
                credits100Layout.setBackgroundResource(android.R.color.transparent)
                credits200Layout.setBackgroundResource(android.R.color.transparent)
                credits500Layout.setBackgroundResource(android.R.color.transparent)
            }
        }

        credits100RadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                credits50RadioButton.isChecked = false
                credits200RadioButton.isChecked = false
                credits500RadioButton.isChecked = false
                credits100Layout.setBackgroundResource(R.drawable.radio_background)
                credits50Layout.setBackgroundResource(android.R.color.transparent)
                credits200Layout.setBackgroundResource(android.R.color.transparent)
                credits500Layout.setBackgroundResource(android.R.color.transparent)
            }
        }

        credits200RadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                credits50RadioButton.isChecked = false
                credits100RadioButton.isChecked = false
                credits500RadioButton.isChecked = false
                credits200Layout.setBackgroundResource(R.drawable.radio_background)
                credits50Layout.setBackgroundResource(android.R.color.transparent)
                credits100Layout.setBackgroundResource(android.R.color.transparent)
                credits500Layout.setBackgroundResource(android.R.color.transparent)
            }
        }

        credits500RadioButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                credits50RadioButton.isChecked = false
                credits100RadioButton.isChecked = false
                credits200RadioButton.isChecked = false
                credits500Layout.setBackgroundResource(R.drawable.radio_background)
                credits50Layout.setBackgroundResource(android.R.color.transparent)
                credits100Layout.setBackgroundResource(android.R.color.transparent)
                credits200Layout.setBackgroundResource(android.R.color.transparent)
            }
        }

        setupBillingClient()


        val continueButton = findViewById<Button>(R.id.cont)
        continueButton.setOnClickListener {

            if (credits50RadioButton.isChecked) {
                selectedProductSku = "credits50"
            } else if (credits100RadioButton.isChecked) {
                selectedProductSku = "credits100"
            } else if (credits200RadioButton.isChecked) {
                selectedProductSku = "credits200"
            } else if (credits500RadioButton.isChecked) {
                selectedProductSku = "credits500"
            }


            if (::selectedProductSku.isInitialized) {
                initiatePurchase(selectedProductSku)
            } else { }
        }


    }

    private fun handlePurchase(purchase: Purchase) {

        val buyLayout = findViewById<LinearLayout>(R.id.buyLayout)

        val gifImageView = findViewById<ImageView>(R.id.loading)
        Glide.with(this)
            .asGif()
            .load(R.drawable.loading)
            .into(gifImageView)

        loadingLayout.visibility = View.VISIBLE
        buyLayout.visibility = View.GONE

        val skus = purchase.skus
        if (skus.isNotEmpty()) {
            val sku = skus[0] // Get the first SKU of the purchased item

            // Check if the purchase corresponds to one of your products
            when (sku) {
                "credits50", "credits100", "credits200", "credits500" -> {
                    // Determine the number of credits based on the SKU
                    val creditsToAdd = when (sku) {
                        "credits50" -> 50
                        "credits100" -> 100
                        "credits200" -> 200
                        "credits500" -> 500
                        else -> 0 // Handle unknown SKU
                    }

                    // Add credits to the user's account in Firestore
                    addCreditsToUser(creditsToAdd)

                    consumePurchase(purchase)

                    // Acknowledge the purchase to prevent it from being reprocessed
                    /*val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()

                    billingClient.acknowledgePurchase(acknowledgePurchaseParams) { billingResult ->
                        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {

                        } else {
                            // Handle acknowledgment failure
                            // You may want to retry or display an error message to the user
                        }
                    }*/
                }
                else -> {
                    // Handle unknown SKU or unexpected purchases
                }
            }
        }
    }

    private fun consumePurchase(purchase: Purchase) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()

        billingClient.consumeAsync(params) { billingResult, purchaseToken ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                // Purchase consumed successfully
                // You can update your UI or provide a confirmation to the user
                Log.d("TAG", "Consuming Successful: $purchaseToken")
                // Update your UI or perform any necessary actions here
            } else {
                // Handle consumption failure
                // You may want to retry or display an error message to the user
                Log.d("TAG", "Consuming Failed: ${billingResult.debugMessage}")
            }
        }
    }



    private fun addCreditsToUser(creditsToAdd: Int) {
        val userUid = FirebaseAuth.getInstance().currentUser?.uid
        if (userUid != null) {
            val userRef = FirebaseFirestore.getInstance().collection("users").document(userUid)

            userRef.get().addOnSuccessListener { documentSnapshot ->
                val currentCredits = documentSnapshot.getLong("credits") ?: 0
                val newCredits = currentCredits + creditsToAdd

                // Update the user's credits in Firestore
                userRef.update("credits", newCredits).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
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
                        // Handle Firestore update failure
                        // You may want to retry or display an error message to the user
                    }
                }
            }
        }
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
            .setSkusList(listOf("credits50", "credits100", "credits200", "credits500")) // Replace with your SKUs
            .setType(BillingClient.SkuType.INAPP) // Use INAPP for one-time purchases
            .build()

        billingClient.querySkuDetailsAsync(params) { billingResult, skuDetailsList ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && skuDetailsList != null) {
                this@PurchaseActivity2.skuDetailsList = skuDetailsList // Store SKU details in the class variable
            } else {
                // Handle any error responses.
                Log.e("BillingResult", "Response Code: ${billingResult.responseCode}, Debug Message: ${billingResult.debugMessage}")
            }
        }
    }

    private fun initiatePurchase(sku: String) {
        val selectedSkuDetails = skuDetailsList.find { it.sku == sku }

        if (selectedSkuDetails != null) {
            val billingFlowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(selectedSkuDetails)
                .build()

            val billingResult = billingClient.launchBillingFlow(this, billingFlowParams)
            if (billingResult.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.e("BillingFlow", "Error: ${billingResult.responseCode}, Debug Message: ${billingResult.debugMessage}")
            }
        } else {
            // Handle the case where the selected SKU details are not found
            // You can display a message to the user or prevent the purchase flow
        }
    }

}



