package com.example.alice

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.example.alice.databinding.FragmentHistoryBinding
import java.io.File

class HistoryFragment : Fragment() {
    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).setSupportActionBarBackVisible(true)

        binding.importHistoryButton.setOnClickListener {
            (activity as MainActivity).showConfirmDialog("导入历史", "注意，导入历史会先清空之前的历史记录") {
                (activity as MainActivity).clearHistory()
                (activity as MainActivity).importHistory()
            }
        }

        // 修改导出按钮逻辑：直接导出并分享
        binding.exportHistoryButton.setOnClickListener {
            exportAndShare()
        }

        binding.clearHistoryButton.setOnClickListener {
            (activity as MainActivity).showConfirmDialog("清除历史", "确定要清除历史记录吗？此过程不可逆") {
                (activity as MainActivity).clearHistory()
            }
        }
    }

    // 导出并分享
    private fun exportAndShare() {
        try {
            // 调用 exportHistory，它返回 Unit
            (activity as MainActivity).exportHistory()

            // 手动构造文件路径和 Uri
            val file = File("${requireActivity().filesDir}/alice_chat_history.json")
            val fileUri: Uri = FileProvider.getUriForFile(
                requireActivity(),
                "com.example.alice.fileprovider",
                file
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "分享历史记录到"))
        } catch (e: Exception) {
            e.printStackTrace()
            (activity as MainActivity).showConfirmDialog("错误", "导出失败: ${e.message}") {}
        }
    }

    override fun onDestroyView() {
        (activity as MainActivity).setSupportActionBarBackVisible(false)
        super.onDestroyView()
        _binding = null
    }
}