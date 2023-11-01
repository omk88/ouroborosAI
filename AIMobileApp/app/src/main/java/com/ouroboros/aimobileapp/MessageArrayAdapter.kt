package com.ouroboros.aimobileapp

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
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
        return 5 // Three types of views
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            items[position].isCode -> 3 // code message
            items[position].isBot -> 1 // bot message
            items[position].isTyping -> 2 // typing message
            items[position].isCodeText -> 4
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
                0 -> inflater.inflate(R.layout.message_item, parent, false) // User layout
                1 -> inflater.inflate(R.layout.message_item_2, parent, false) // Bot layout
                2 -> {
                    val typingView = inflater.inflate(R.layout.message_item_4, parent, false) // Typing layout
                    val gifImageView = typingView.findViewById<ImageView>(R.id.typingid)
                    Glide.with(context).load(R.drawable.typing)
                        .transition(DrawableTransitionOptions.withCrossFade())
                        .into(gifImageView)
                    typingView
                }
                3 -> inflater.inflate(R.layout.message_item_5, parent, false)
                4 -> inflater.inflate(R.layout.message_item_6, parent, false)
                else -> throw IllegalArgumentException("Invalid view type")
            }

        }

        val item = items[position]
        val textView = view?.findViewById<TextView>(R.id.messageTextView)

        if (viewType != 3 && viewType != 4 && viewType != 1) {
            if (textView != null) {
                textView.text = item.message
            }
        }

        if (viewType == 4 || viewType == 1) {
            var modifiedMessage = item.message

            val stringPattern = "`([^`]+)`".toRegex()
            val matches = stringPattern.findAll(item.message).toList()

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

            if (textView != null) {
                textView.text = spannable
            }
        }




        if (viewType == 3) {


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

            var first = ""

            if (item.message.contains("\n")) {
                first = item.message.split("\n").first()
            } else {
                first = item.message.trim().split(Regex("\\s+")).first()
            }

            Log.d("MOSSAGE", item.message)
            Log.d("MOSSAGE", first)


            val codeList = listOf("python", "java", "javascript", "c", "cpp", "csharp", "php", "ruby", "swift",
                "kotlin", "go", "rust", "typescript", "matlab", "r", "haskell", "scala", "perl", "lua",
                "sql", "html", "css", "bash", "zsh", "dart", "groovy", "objc", "fortran", "cobol", "vhdl",
                "verilog", "prolog", "scheme", "lisp", "erlang", "elixir", "fsharp", "vbnet", "assembly", "julia",
                "elm", "pascal", "racket", "vb", "javac")


            if (first.lowercase() in codeList) {
                val firstWord = item.message.split("\n").first()

                val codeType = view?.findViewById<TextView>(R.id.codeType)
                if (codeType != null) {
                    codeType.text = firstWord.capitalize()
                }
            } else {
                val codeType = view?.findViewById<TextView>(R.id.codeType)

                if (codeType != null) {
                    val codeLayout = view?.findViewById<LinearLayout>(R.id.codeLayout)
                    val divider = view?.findViewById<View>(R.id.divider)
                    if (divider != null) {
                        divider.visibility = View.GONE
                    }
                    if (codeLayout != null) {
                        codeLayout.visibility = View.GONE
                    }
                }
            }


            if (textView != null) {
                //cleanedMessage = cleanedMessage.replaceFirst("java", "").trimStart()

                if (item.message.trim().split(Regex("\\s+")).firstOrNull() in codeList) {
                    var cleanedMessage = item.message.replaceFirst(first, "").trimStart()

                    val spannable = SpannableString(cleanedMessage)

                    val ORANGE_COLOR = Color.parseColor("#FFA500")
                    val PURPLE_COLOR = Color.parseColor("#B300CC")
                    val RED_COLOR = Color.parseColor("#D30101")
                    val GREEN_COLOR = Color.parseColor("#62E600")
                    val GREY_COLOR = Color.parseColor("#A4A3A3")
                    val BLUE_COLOR = Color.parseColor("#217FFF")
                    val YELLOW_COLOR = Color.parseColor("#FCFF21")

                    val orangeWords = listOf("for", "while", "in", "import", "true", "false", "var", "if", "else", "try", "except", "return", "def")
                    val purpleWords = listOf("print", "byte", "int", "short", "long", "float", "char", "double", "args")
                    val blueWords = listOf("static", "void", "private", "protected", "public", "class")
                    val yellowWords = listOf(", ")

                    for (word in yellowWords) {
                        val pattern = "\\b$word\\b".toRegex()
                        pattern.findAll(cleanedMessage).forEach { matchResult ->
                            val start = matchResult.range.first
                            val end = matchResult.range.last + 1
                            val yellowSpan = ForegroundColorSpan(YELLOW_COLOR)
                            spannable.setSpan(yellowSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }

                    for (word in blueWords) {
                        val pattern = "\\b$word\\b".toRegex()
                        pattern.findAll(cleanedMessage).forEach { matchResult ->
                            val start = matchResult.range.first
                            val end = matchResult.range.last + 1
                            val blueSpan = ForegroundColorSpan(BLUE_COLOR)
                            spannable.setSpan(blueSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }

                    for (word in orangeWords) {
                        val pattern = "\\b$word\\b".toRegex()
                        pattern.findAll(cleanedMessage).forEach { matchResult ->
                            val start = matchResult.range.first
                            val end = matchResult.range.last + 1
                            val orangeSpan = ForegroundColorSpan(ORANGE_COLOR)
                            spannable.setSpan(orangeSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }

                    for (word in purpleWords) {
                        val pattern = "\\b$word\\b".toRegex()
                        pattern.findAll(cleanedMessage).forEach { matchResult ->
                            val start = matchResult.range.first
                            val end = matchResult.range.last + 1
                            val purpleSpan = ForegroundColorSpan(PURPLE_COLOR)
                            spannable.setSpan(purpleSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }

                    // Highlight comments in red
                    val commentPattern = "#.*".toRegex()
                    commentPattern.findAll(cleanedMessage).forEach { matchResult ->
                        val start = matchResult.range.first
                        val end = matchResult.range.last + 1
                        val redSpan = ForegroundColorSpan(RED_COLOR)
                        spannable.setSpan(redSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }

                    val commentPattern2 = "//.*".toRegex()
                    commentPattern2.findAll(cleanedMessage).forEach { matchResult ->
                        val start = matchResult.range.first
                        val end = matchResult.range.last + 1
                        val redSpan = ForegroundColorSpan(GREY_COLOR)
                        spannable.setSpan(redSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }

                    val commentPattern3 = "--.*".toRegex()
                    commentPattern3.findAll(cleanedMessage).forEach { matchResult ->
                        val start = matchResult.range.first
                        val end = matchResult.range.last + 1
                        val redSpan = ForegroundColorSpan(GREY_COLOR)
                        spannable.setSpan(redSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }

                    // Highlight strings in green
                    val stringPattern = "\".*?\"|'.*?'".toRegex()
                    stringPattern.findAll(cleanedMessage).forEach { matchResult ->
                        val start = matchResult.range.first
                        val end = matchResult.range.last + 1
                        val greenSpan = ForegroundColorSpan(GREEN_COLOR)
                        spannable.setSpan(greenSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }

                    textView.text = spannable
                } else {

                    textView.text = item.message
                }
            }

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

