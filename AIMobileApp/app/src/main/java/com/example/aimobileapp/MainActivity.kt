package com.example.aimobileapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import okhttp3.OkHttpClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.util.Date
import java.util.concurrent.TimeUnit


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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        instructionTextView = findViewById(R.id.instruction)
        conversationListView = findViewById(R.id.conversationList)
        val button = findViewById<LinearLayout>(R.id.button_start_activity)
        button.setOnClickListener {
            val intent = Intent(this, AIChat::class.java)
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
                val intent = Intent(this@MainActivity, AIChat::class.java)
                intent.putExtra("conversation", it.code)
                startActivity(intent)
            }
        }
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






