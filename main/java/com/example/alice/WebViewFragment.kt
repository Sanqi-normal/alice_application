package com.example.alice

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import com.example.alice.databinding.FragmentWebviewBinding

class WebViewFragment : Fragment() {
    private var _binding: FragmentWebviewBinding? = null
    private val binding get() = _binding!!

    companion object {
        private const val PREFS_NAME = "WebViewPrefs"
        private const val KEY_URL = "lastUrl"
        private const val DEFAULT_URL = "https://www.baidu.com"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebviewBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // WebView基本设置
        binding.webview.settings.javaScriptEnabled = true
        binding.webview.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                view?.loadUrl(request?.url.toString())
                return true
            }
        }

        // 处理返回键
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webview.canGoBack()) {
                    binding.webview.goBack()
                } else {
                    requireActivity().finish()
                }
            }
        })
    }

    override fun onStart() {
        Log.d("WebViewFragment", "onStart")
        super.onStart()
        // 每次页面显示时重新获取URL并加载
        var url = SettingsData.getDefaultUrl()
        if (url.isEmpty()) {
            url = DEFAULT_URL
        } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // 格式错误加百度搜索前缀
            url = "https://www.baidu.com/s?wd=$url"
        }
        binding.webview.loadUrl(url)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}