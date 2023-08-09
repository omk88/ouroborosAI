package com.example.aimobileapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import android.widget.Toast
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.Date



class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .addInterceptor { chain ->
            val request = chain.request().newBuilder().addHeader("Authorization", "Bearer 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").build()
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
        setContentView(R.layout.activity_main)

        val button = findViewById<LinearLayout>(R.id.button_start_activity)
        button.setOnClickListener {
            val intent = Intent(this, AIChat::class.java)
            intent.putExtra("conversation", getRandomString(15))
            startActivity(intent)
        }

        val generateImg = findViewById<LinearLayout>(R.id.generateImg)
        generateImg.setOnClickListener {
            val intent = Intent(this, ImageActivity::class.java)
            startActivity(intent)
        }


        val conversations = loadConversationsAsList()

        val adapter = ConversationAdapter(this, conversations)
        val listView: ListView = findViewById(R.id.conversationList)
        listView.adapter = adapter
        adapter.notifyDataSetChanged()
    }

    private fun loadConversationsAsList(): ArrayList<Conversation> {
        val file = File(filesDir, "conversations.json")
        val resultList = ArrayList<Conversation>()

        if (file.exists()) {
            val jsonString = file.readText()
            println("JSONSTRING: "+ jsonString)

            val gson = Gson()
            val typeToken = object : TypeToken<MutableMap<String, List<ChatMessage>>>() {}.type
            val allConversations: MutableMap<String, List<ChatMessage>>? = gson.fromJson(jsonString, typeToken)

            if (allConversations != null) {
                allConversations.forEach { (code, messages) ->
                    val lastMessage = messages.last()
                    // You might need to adjust the way you get the timestamp. Here's a simple example:
                    val timestamp = SimpleDateFormat("dd/MM/yyyy").format(Date())
                    resultList.add(Conversation(timestamp, lastMessage.message))
                }
            }
        }

        return resultList
    }

    fun getRandomString(length: Int = 10): String {
        val allowedChars = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        return (1..length)
            .map { allowedChars.random() }
            .joinToString("")
    }

    fun deleteConversationsFile() {
        val file = File(filesDir, "conversations.json")
        if (file.exists()) {
            file.delete()
        }
    }

}

interface DallEApiService {
    @POST("v1/images/generations")
    fun createImage(@Body request: ImageCreationRequest): Call<ImageCreationResponse>
}

data class ImageCreationRequest(val prompt: String, val n: Int, val size: String)
data class ImageCreationResponse(val data: List<ImageData>)
data class ImageData(val url: String)

