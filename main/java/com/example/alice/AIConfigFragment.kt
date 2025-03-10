package com.example.alice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.alice.databinding.FragmentAiConfigBinding

class AIConfigFragment : Fragment() {
    private var _binding: FragmentAiConfigBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAiConfigBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).setSupportActionBarBackVisible(true)

        // 初始化输入框
        binding.systemPromptInput.setText(SettingsData.getSystemPrompt())
        binding.temperatureInput.setText(SettingsData.getTemperature().toString())
        binding.apiKeyInput.setText(SettingsData.getApiKey())
        binding.defaultUrlInput.setText(SettingsData.getDefaultUrl())
        binding.maxTokensInput.setText(SettingsData.getMaxTokens().toString())

        // 设置焦点变化监听
        val inputs = listOf(
            binding.systemPromptInput to "systemPrompt",
            binding.temperatureInput to "temperature",
            binding.apiKeyInput to "apiKey",
            binding.defaultUrlInput to "defaultUrl",
            binding.maxTokensInput to "max_tokens"
        )
        inputs.forEach { (editText, key) ->
            editText.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    SettingsData.setValue(key, editText.text.toString())
                    SettingsData.save(requireContext())
                }
            }
        }
    }

    override fun onDestroyView() {
        (activity as MainActivity).setSupportActionBarBackVisible(false)
        super.onDestroyView()
        _binding = null
    }
}