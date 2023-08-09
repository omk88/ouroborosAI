package com.example.aimobileapp

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ImageActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().addHeader("Authorization", "Bearer ").build()
            chain.proceed(request)
        }
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .client(client)
        .build()

    private val service: DallEApiService = retrofit.create(DallEApiService::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_generate_image)

        requestImagesFromAPI("a sea otter with a pearl earring by Johannes Vermeer", 3)
    }

    private fun requestImagesFromAPI(prompt: String, count: Int) {
        val request = ImageCreationRequest(prompt, count, "1024x1024")

        service.createImage(request).enqueue(object : Callback<ImageCreationResponse> {
            override fun onResponse(call: Call<ImageCreationResponse>, response: Response<ImageCreationResponse>) {
                if (response.isSuccessful) {
                    val imageUrls = response.body()?.data?.map { it.url }
                    if (!imageUrls.isNullOrEmpty()) {
                        loadImageIntoView(R.id.imageView, imageUrls[0])
                        if (imageUrls.size > 1) loadImageIntoView(R.id.imageView2, imageUrls[1])
                        if (imageUrls.size > 2) loadImageIntoView(R.id.imageView3, imageUrls[2])
                    }
                } else {
                    // Handle error
                }
            }

            override fun onFailure(call: Call<ImageCreationResponse>, t: Throwable) {
                // Handle error
            }
        })
    }

    private fun loadImageIntoView(imageViewId: Int, imageUrl: String) {
        val imageView: ImageView = findViewById(imageViewId)
        Glide.with(this)
            .load(imageUrl)
            .into(imageView)
    }
}