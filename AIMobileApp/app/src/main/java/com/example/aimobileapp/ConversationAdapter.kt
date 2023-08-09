package com.example.aimobileapp

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView

class ConversationAdapter(private val context: Context, private val dataSource: ArrayList<Conversation>) : BaseAdapter() {

    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getCount(): Int = dataSource.size

    override fun getItem(position: Int): Any = dataSource[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.conversation_item, parent, false)

        val conversation = getItem(position) as Conversation
        val timestampTextView = view.findViewById<TextView>(R.id.timeStamp)
        val messageTextView = view.findViewById<TextView>(R.id.messageTextView)

        timestampTextView.text = conversation.timestamp
        messageTextView.text = truncateMessage(conversation.message)  // Apply truncation here

        return view
    }



    private fun truncateMessage(message: String, maxLength: Int = 63): String {
        if (message.length <= maxLength) return message
        var truncated = message.substring(0, maxLength)
        if (truncated.last() == ' ') {
            truncated = truncated.trimEnd()
        }
        return "$truncated..."
    }


}
