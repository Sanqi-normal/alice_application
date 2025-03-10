package com.example.alice

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.alice.databinding.ItemMessageBinding

class MessageAdapter(val messages: MutableList<Message>) :
    RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    class MessageViewHolder(val binding: ItemMessageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        with(holder.binding) {
            messageContent.text = message.content
            messageTime.text = message.timeStamp
            when (message.role) {
                "user" -> messageContainer.setBackgroundResource(R.color.user_message_bg)
                "assistant" -> messageContainer.setBackgroundResource(R.color.assistant_message_bg)
                "system" -> messageContainer.setBackgroundResource(R.color.system_message_bg)
            }
        }
    }

    override fun getItemCount() = messages.size

    // 更新消息列表并通知 UI
    fun updateMessages(newMessages: List<Message>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged() // 整体刷新，适合流式更新
    }
    fun updateMessage(position: Int, content: String) {
        if (position >= 0 && position < messages.size) {
            messages[position] = Message(messages[position].role, content, messages[position].timeStamp)
            notifyItemChanged(position)
        }
    }

    // 添加单条消息并通知插入
    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }
}