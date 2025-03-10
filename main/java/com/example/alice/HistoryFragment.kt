package com.example.alice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.alice.databinding.FragmentHistoryBinding

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

        binding.importHistoryButton.setOnClickListener{
            (activity as MainActivity).showConfirmDialog("导入历史", "注意，导入历史会先清空之前的历史记录") {
                (activity as MainActivity).clearHistory()
                (activity as MainActivity).importHistory()
            }
        }
        binding.exportHistoryButton.setOnClickListener {
           (activity as MainActivity).exportHistory(useFilePicker = true)
        }
        binding.clearHistoryButton.setOnClickListener {
            (activity as MainActivity).showConfirmDialog("清除历史", "确定要清除历史记录吗？此过程不可逆") {
            (activity as MainActivity).clearHistory()
        } }
    }

    override fun onDestroyView() {
        (activity as MainActivity).setSupportActionBarBackVisible(false)
        super.onDestroyView()
        _binding = null
    }
}