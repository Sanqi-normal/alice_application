package com.example.alice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alice.databinding.FragmentSettingsBinding

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val categories = listOf(
            "基础配置" to AIConfigFragment::class.java,
            "历史对话" to HistoryFragment::class.java,
            "记忆管理" to MemoryFragment::class.java,
            "个性化" to PersonalizationFragment::class.java,
            "日志" to LoggingFragment::class.java,
            "关于" to AboutFragment::class.java
        )

        binding.settingsRecyclerView.layoutManager = LinearLayoutManager(context)
        binding.settingsRecyclerView.addItemDecoration(DividerItemDecoration(context, LinearLayoutManager.VERTICAL))
        binding.settingsRecyclerView.adapter = SettingsAdapter(categories) { fragmentClass, title ->
            (activity as MainActivity).navigateToFragment(fragmentClass.newInstance(), title)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}