package com.example.aimobileapp

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class MessageArrayAdapter(context: Context, private val items: ArrayList<ChatMessage>) :
    ArrayAdapter<ChatMessage>(context, 0, items) {

    override fun getViewTypeCount(): Int {
        return 3 // Three types of views
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            items[position].isBot -> 1 // bot message
            items[position].isTyping -> 2 // typing message
            else -> 0 // user message
        }
    }

    private fun formatTimestamp(timestamp: String): String {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            return outputFormat.format(date!!)
        } catch (e: Exception) {
            e.printStackTrace()
            return timestamp
        }
    }


    @SuppressLint("MissingInflatedId")
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        var view = convertView
        val viewType = getItemViewType(position)
        val inflater = LayoutInflater.from(context)

        if (view == null) {
            view = when (viewType) {
                0 -> inflater.inflate(R.layout.message_item, null) // User layout
                1 -> inflater.inflate(R.layout.message_item_2, null) // Bot layout
                2 -> {
                    val typingView = inflater.inflate(R.layout.message_item_4, null) // Typing layout
                    val gifImageView = typingView.findViewById<ImageView>(R.id.typingid)
                    Glide.with(context).load(R.drawable.typing)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(gifImageView)
                    typingView
                }
                else -> throw IllegalArgumentException("Invalid view type")
            }
        }

        val item = items[position]
        val textView = view?.findViewById<TextView>(R.id.messageTextView)
        if (textView != null) {
            textView.text = item.message
        }

        if (viewType == 0 || viewType == 1) {
            val timeTextView = view?.findViewById<TextView>(R.id.timeStamp)
            timeTextView?.text = formatTimestamp(item.timestamp)
        }

        if (viewType == 1) {
            val copy = view?.findViewById<ImageView>(R.id.copy)
            val share = view?.findViewById<ImageView>(R.id.share)

            if (items[position].isFirstMessage) {
                copy?.visibility = View.GONE
                share?.visibility = View.GONE
            }

            val imageView = view?.findViewById<ImageView>(R.id.copy)
            imageView?.setOnClickListener {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("label", textView?.text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "Text copied to clipboard", Toast.LENGTH_SHORT).show()
            }

            val shareImageView = view?.findViewById<ImageView>(R.id.share)
            shareImageView?.setOnClickListener {
                val sendIntent: Intent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, textView?.text)
                    type = "text/plain"
                }

                val shareIntent = Intent.createChooser(sendIntent, null)
                context.startActivity(shareIntent)
            }
        }

        return view ?: inflater.inflate(R.layout.message_item, null)
    }

}

