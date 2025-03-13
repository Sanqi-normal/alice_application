package com.example.alice

import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.alice.databinding.ItemMessageBinding

class MessageAdapter(
    val messages: MutableList<Message>,
    private val mainActivity: MainActivity,
    private val onDelete: (Int) -> Unit,
    private val onToggleMark: (Int) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(val binding: ItemMessageBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        with(holder.binding) {
            messageContent.text = message.content
            messageTime.text = message.timeStamp

            val marginPx = (16 * holder.itemView.context.resources.displayMetrics.density).toInt()
            val params = messageContentContainer.layoutParams as ConstraintLayout.LayoutParams

            params.startToStart = ConstraintLayout.LayoutParams.UNSET
            params.endToEnd = ConstraintLayout.LayoutParams.UNSET
            params.horizontalBias = 0.5f

            when (message.role) {
                "user" -> {
                    messageContentContainer.setBackgroundResource(R.color.user_message_bg)
                    params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    params.marginEnd = marginPx
                    params.marginStart = marginPx * 2
                    deleteIcon.visibility = View.GONE
                    markIcon.visibility = if (position + 1 < messages.size && messages[position + 1].role == "assistant") View.VISIBLE else View.GONE
                    markIcon.setImageResource(if (message.isMarked) R.drawable.ic_check else R.drawable.ic_check_unmarked)
                    markIcon.setColorFilter(if (message.isMarked) Color.GREEN else Color.GRAY)
                }
                "assistant" -> {
                    messageContentContainer.setBackgroundResource(R.color.assistant_message_bg)
                    params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    params.marginStart = marginPx
                    params.marginEnd = marginPx * 2
                    deleteIcon.visibility = View.VISIBLE
                    markIcon.visibility = View.GONE
                }
                "system" -> {
                    messageContentContainer.setBackgroundResource(R.color.system_message_bg)
                    params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    params.marginStart = marginPx
                    params.marginEnd = marginPx
                    deleteIcon.visibility = View.GONE
                    markIcon.visibility = View.GONE
                }
            }

            messageContentContainer.layoutParams = params

            deleteIcon.setOnClickListener {
                mainActivity.showConfirmDialog("删除", "删除此条消息？") {
                    onDelete(position)
                }
            }
            markIcon.setOnClickListener { onToggleMark(position) }
        }
    }
    override fun getItemCount() = messages.size

    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun updateMessage(position: Int, content: String) {
        if (position >= 0 && position < messages.size) {
            messages[position] = messages[position].copy(content = content)
            notifyItemChanged(position)
        }
    }

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}