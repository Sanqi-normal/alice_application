package com.example.alice

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alice.databinding.FragmentChatBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
class ChatFragment : Fragment() {
    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!
    lateinit var adapter: MessageAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var assistantMessageIndex: Int = -1

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MessageAdapter(mutableListOf(), requireActivity() as MainActivity, ::deleteMessagePair, ::toggleMark)

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
            adapter = this@ChatFragment.adapter
            setHasFixedSize(true)
        }

        (activity as MainActivity).setupRecyclerView(adapter)

        binding.sendButton.setOnClickListener {
            val message = binding.userInput.text.toString().trim()
            if (message.isNotEmpty()) {
                adapter.addMessage(Message("user", message, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())))
                scrollToBottom()
                assistantMessageIndex = adapter.itemCount
                adapter.addMessage(Message("assistant", "", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())))
                (activity as MainActivity).sendMessage(message)
                binding.userInput.text.clear()
            }
        }
    }

    private var isDeleting = false
    private fun deleteMessagePair(position: Int) {
        if (isDeleting||position >= adapter.messages.size) return

        isDeleting = true
        scope.launch(Dispatchers.Main) {
                (activity as MainActivity).aliceService?.getService()?.deleteMessagePair(position - 1)
                adapter.messages.removeAt(position)
                adapter.messages.removeAt(position - 1)
                adapter.notifyItemRangeRemoved(position - 1, 2)
                isDeleting = false

        }
    }

    private fun toggleMark(position: Int) {
        if (position < adapter.messages.size && adapter.messages[position].role == "user") {
            (activity as MainActivity).aliceService?.getService()?.let { service ->
                service.toggleMark(position)
                // 同步适配器的 messages 列表
                scope.launch {
                    val updatedMessages = service.getMessages()
                    // 可选：仅更新特定范围以提高性能
                     adapter.messages[position] = updatedMessages[position]
                     adapter.notifyItemRangeChanged(position, 2)
                    Log.d("ChatFragment", "toggleMark: $position")
                }
            }
        }
    }

    fun updateMessages(messages: List<Message>) {
        scope.launch {
            adapter.updateMessages(messages)
            scrollToBottom()
        }
    }

    fun updateAssistantMessage(content: String) {
        if (assistantMessageIndex >= 0 && assistantMessageIndex < adapter.itemCount) {
            adapter.updateMessage(assistantMessageIndex, content)
            scrollToBottom()
        }
    }

    private fun scrollToBottom() {
        binding.messagesRecyclerView.post {
            if (adapter.itemCount > 0) {
                binding.messagesRecyclerView.scrollToPosition(adapter.itemCount - 1)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
        _binding = null
    }
}