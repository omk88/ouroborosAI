package com.ouroboros.aimobileapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList

class ConversationAdapter(
    private val context: Context,
    private val dataSource: ArrayList<Any>,
    private val onConversationRemoveListener: OnConversationRemoveListener,
    private val instructionTextView: TextView
) : BaseAdapter() {

    interface OnConversationRemoveListener {
        fun onRemove(conversation: Conversation)
    }

    companion object {
        private const val TYPE_MESSAGE = 0
        private const val TYPE_TODAY_DIVIDER = 1
        private const val TYPE_LAST_7_DAYS_DIVIDER = 2
        private const val TYPE_30_DAYS_DIVIDER = 3
    }

    private val inflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater

    override fun getCount(): Int = dataSource.size

    override fun getItem(position: Int): Any = dataSource[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is Conversation -> TYPE_MESSAGE
            is Divider -> when ((getItem(position) as Divider).type) {
                DividerType.TODAY -> TYPE_TODAY_DIVIDER
                DividerType.LAST_7_DAYS -> TYPE_LAST_7_DAYS_DIVIDER
                DividerType.OLDER_THAN_30_DAYS -> TYPE_30_DAYS_DIVIDER
            }
            else -> throw IllegalArgumentException("Unsupported item type")
        }
    }

    override fun getViewTypeCount(): Int {
        return 4
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val item = getItem(position)
        val view: View = when (item) {
            is Conversation -> LayoutInflater.from(context).inflate(R.layout.conversation_item, parent, false)
            is Divider -> when (item.type) {
                DividerType.TODAY -> LayoutInflater.from(context).inflate(R.layout.divider_item, parent, false)
                DividerType.LAST_7_DAYS -> LayoutInflater.from(context).inflate(R.layout.divider_item_2, parent, false)
                DividerType.OLDER_THAN_30_DAYS -> LayoutInflater.from(context).inflate(R.layout.divider_item_3, parent, false)
            }
            else -> throw IllegalArgumentException("Unknown item type")
        }

        if (item is Conversation) {
            val messageTextView = view.findViewById<TextView>(R.id.messageTextView)
            val timestamp = view.findViewById<TextView>(R.id.timeStamp)


            var displayMessage = item.message
            if (displayMessage.length > 63) {
                displayMessage = displayMessage.substring(0, 60) + "..."
            }
            displayMessage = displayMessage.replace("\n", " ").trim()
            if (displayMessage.last() == ' ') {
                displayMessage = displayMessage.dropLast(1)
            }

            var modifiedMessage = displayMessage

            val stringPattern = "`([^`]+)`".toRegex()
            val matches = stringPattern.findAll(displayMessage).toList()

            matches.reversed().forEach { matchResult ->
                // Get the content without the backticks
                val contentWithoutBackticks = matchResult.groups[1]?.value ?: ""

                // Replace the original content (with backticks) in the modified message
                modifiedMessage = modifiedMessage.replaceRange(matchResult.range, contentWithoutBackticks)
            }

            val spannable = SpannableString(modifiedMessage)

            matches.forEach { matchResult ->
                val contentWithoutBackticks = matchResult.groups[1]?.value ?: ""

                val start = modifiedMessage.indexOf(contentWithoutBackticks)

                if (start != -1) {
                    val end = start + contentWithoutBackticks.length

                    val boldSpan = StyleSpan(Typeface.BOLD)
                    spannable.setSpan(boldSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }


            messageTextView?.text = spannable


            timestamp.text = formatDateForDisplay(item.timestamp)

            val binLayout = view.findViewById<LinearLayout>(R.id.bin)
            binLayout?.setOnClickListener {
                val fadeOut = AnimationUtils.loadAnimation(context, R.anim.fade_out)
                val translateUp = AnimationUtils.loadAnimation(context, R.anim.translate_up)
                fadeOut.setAnimationListener(object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation?) {}
                    override fun onAnimationEnd(animation: Animation?) {
                        dataSource.removeAt(position)
                        onConversationRemoveListener.onRemove(item)
                        notifyDataSetChanged()

                        if (dataSource.isEmpty()) {
                            instructionTextView.visibility = View.VISIBLE
                        }
                    }
                    override fun onAnimationRepeat(animation: Animation?) {}
                })
                view.startAnimation(fadeOut)
                val listView = parent as ListView
                for (i in position + 1 until listView.childCount) {
                    val child = listView.getChildAt(i)
                    child?.startAnimation(translateUp)
                }
            }
            view.setOnClickListener {
                val intent = Intent(context, com.ouroboros.aimobileapp.AIChat::class.java)
                intent.putExtra("conversation", item.code)
                context.startActivity(intent)
                (context as Activity).overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
            }
        }
        return view
    }

    private fun formatDateForDisplay(timestamp: String): String {
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            val outputFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            return outputFormat.format(date!!)
        } catch (e: Exception) {
            e.printStackTrace()
            return timestamp
        }
    }

    private fun truncateMessage(message: String, maxLength: Int = 63): String {
        if (message.length <= maxLength) return message
        var truncated = message.substring(0, maxLength)
        if (truncated.last() == ' ') {
            truncated = truncated.trimEnd()
        }
        return "$truncated..."
    }

    enum class DividerType {
        TODAY, LAST_7_DAYS, OLDER_THAN_30_DAYS
    }

    data class Divider(val type: DividerType, val title: String)
}



