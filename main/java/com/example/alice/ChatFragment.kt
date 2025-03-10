package com.example.alice

import android.os.Bundle
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
    private lateinit var adapter: MessageAdapter
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var assistantMessageIndex: Int = -1 // 新增：跟踪助理消息位置

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

        adapter = MessageAdapter(mutableListOf())

        binding.messagesRecyclerView.apply {
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = this@ChatFragment.adapter
            setHasFixedSize(true)
        }

        (activity as MainActivity).setupRecyclerView(adapter)

        binding.sendButton.setOnClickListener {
            val message = binding.userInput.text.toString().trim()
            if (message.isNotEmpty()) {
                adapter.addMessage(Message("user", message, SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())))
                scrollToBottom()
                assistantMessageIndex = adapter.itemCount // 设置助理消息起始位置
                adapter.addMessage(Message("assistant", "", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())))
                (activity as MainActivity).sendMessage(message)
                binding.userInput.text.clear()
            }
        }
    }

    fun updateMessages(messages: List<Message>) {
        scope.launch {
            adapter.updateMessages(messages)
            scrollToBottom()
        }
    }

    // 新增：更新助理消息内容
    fun updateAssistantMessage(content: String) {
        if (assistantMessageIndex >= 0 && assistantMessageIndex < adapter.itemCount) {
            adapter.updateMessage(assistantMessageIndex, content) // 更新已有项
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

    override fun onResume() {
        super.onResume()

    }
}