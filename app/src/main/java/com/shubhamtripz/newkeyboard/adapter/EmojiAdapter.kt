package com.shubhamtripz.newkeyboard.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.shubhamtripz.newkeyboard.R

class EmojiAdapter(
    private val emojiList: MutableList<String>,
    private val onEmojiClick: OnEmojiClickListener
) : RecyclerView.Adapter<EmojiAdapter.EmojiViewHolder>() {

    interface OnEmojiClickListener{
        fun onEmojiClick(emoji:String)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_emoji, parent, false)
        return EmojiViewHolder(view)
    }

    override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
        val emoji = emojiList[position]
        holder.emojiTextView.text = emoji
        holder.emojiTextView.setOnClickListener {
            onEmojiClick.onEmojiClick(emoji)
        }
    }

    override fun getItemCount(): Int {
        return emojiList.size
    }

    class EmojiViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val emojiTextView: TextView = view.findViewById(R.id.emojiTextView)
    }
}