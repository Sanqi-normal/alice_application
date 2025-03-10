package com.example.alice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.alice.databinding.FragmentLoggingBinding

class LoggingFragment : Fragment() {
    private var _binding: FragmentLoggingBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoggingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (activity as MainActivity).setSupportActionBarBackVisible(true)
    }

    override fun onDestroyView() {
        (activity as MainActivity).setSupportActionBarBackVisible(false)
        super.onDestroyView()
        _binding = null
    }
}