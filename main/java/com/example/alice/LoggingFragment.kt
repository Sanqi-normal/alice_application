package com.example.alice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer
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

        // Markdown 内容
        val markdown = """
            ## Alice v1.0.0 - 发布日志 
            (2025-03-03)
            
            ---
            
            这是 Alice App 的第一个正式版本

            ### 功能：
            - 实现了AI对话、日程提醒、网页浏览等功能。

            ### 改进：
            - 优化了加载速度和页面展示。

            ### 修复：
            - 修复了历史记录导入和量化历史导致的闪退和卡顿。
            历史消息的格式：
            [
                {
                    "role": "user",
                    "content": "你好"
                },
                {
                    "role":"assistant",
                    "content":"你好，有什么我可以帮助你吗"
                }
            ]

            ### 已知问题：
            - 闹铃可能无法响应
            - 去除了未完善的AI工具调用
            - 填写配置后无响应可尝试退出重进
            - 如遇bug，不要惊慌，拿起手边趁手的东西照着它狠狠拍下
            ---
            ---
            ## Alice v1.0.2 
            (2025-03-13)
            
            ---
            
            ### 改进：
            - 优化了对话页，添加了删除组和标记组的功能。
            - 更新了历史导出逻辑，使可以直接发送到微信等应用。
            - 优化了对话细节
            
            ### 修复：
            - 修复了日程提醒使可正常响应
            - 添加了支持json格式课表导入
            - 导入格式应如下：
            [
                {
                    "date": "Y/M/D",
                    "time":"xx:xx",
                    "event":"无要求，可以写课程名，但是相同课程名要一致",
                    "note":"补充，任意，可填希望AI提醒你的内容，这部分会发送给AI"                    
                }
            ]
            - 把之前的闪退又给修回来了
            - 因为我发现现在的流式保存删除后的量化记录会破坏量化文件，于是回退了操作，连续删除多次会闪退，但起码能用
            
            
        """.trimIndent()

        // 将 Markdown 转换为 HTML
        val parser = Parser.builder().build()
        val document = parser.parse(markdown)
        val renderer = HtmlRenderer.builder().build()
        val html = renderer.render(document)

        // 加载 HTML 到 WebView
        binding.webview.loadData(html, "text/html", "UTF-8")
    }

    override fun onDestroyView() {
        (activity as MainActivity).setSupportActionBarBackVisible(false)
        super.onDestroyView()
        _binding = null
    }
}