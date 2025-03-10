package com.example.alice

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.view.WindowManager
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.alice.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var serviceIntent: Intent
    private var aliceService: AliceService.AliceBinder? = null
    private lateinit var progressDialog: AlertDialog // 新增：进度条对话框
    private lateinit var progressBar: ProgressBar // 新增：进度条控件


    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var adapter: MessageAdapter
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            aliceService = service as AliceService.AliceBinder // 修改：强制转换为 AliceBinder
            loadHistory()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            aliceService = null
        }
    }


    // 新增：用于文件导入的 Activity Result Launcher
    private val importLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { aliceService?.importHistory(it.toString()) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        SettingsData.initialize(this)
        serviceIntent = Intent(this, AliceService::class.java)
        if (!isServiceRunning(AliceService::class.java)) {
        startForegroundService(serviceIntent)
        }
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        val dialogView = layoutInflater.inflate(R.layout.progress_dialog, null)
        progressBar = dialogView.findViewById(R.id.progressBar)
        progressDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "alice_channel",
                "Alice Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        setupFragments(savedInstanceState)
        setupNavigation()
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayShowTitleEnabled(false)
    }
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        return manager.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }
    private fun setupFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            val fragments = mapOf(
                R.id.nav_chat to ChatFragment(),
                R.id.nav_webview to WebViewFragment(),
                R.id.nav_schedule to ScheduleFragment(),
                R.id.nav_settings to SettingsFragment()
            )

            supportFragmentManager.beginTransaction().apply {
                fragments.forEach { (id, fragment) ->
                    add(R.id.fragment_container, fragment, id.toString())
                    if (id != R.id.nav_chat) hide(fragment)
                }
            }.commit()

            binding.toolbarTitle.text = "Alice"
            findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_chat
        }
    }

    private fun setupNavigation() {
        findViewById<BottomNavigationView>(R.id.bottom_navigation).setOnItemSelectedListener { item ->
            val fragments = mapOf(
                R.id.nav_chat to "Alice",
                R.id.nav_webview to "blog",
                R.id.nav_schedule to "日程",
                R.id.nav_settings to "设置"
            )
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            }
            supportFragmentManager.beginTransaction().apply {
                fragments.keys.forEach { id ->
                    val fragment = supportFragmentManager.findFragmentByTag(id.toString())!!
                    if (id == item.itemId) show(fragment) else hide(fragment)
                }
            }.commit()

            binding.toolbarTitle.text = fragments[item.itemId]
            setSupportActionBarBackVisible(false)
            if (item.itemId == R.id.nav_chat) loadHistory()
            true
        }
    }

    fun setSupportActionBarBackVisible(visible: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(visible)
        supportActionBar?.setHomeButtonEnabled(visible)
        if (visible) {
            val backArrow = resources.getDrawable(android.R.drawable.ic_menu_revert, null)
            backArrow.setTint(android.graphics.Color.WHITE)
            supportActionBar?.setHomeAsUpIndicator(backArrow)
        }
    }

    fun setupRecyclerView(adapter: MessageAdapter) {
        this.adapter = adapter
        loadHistory()
    }

    private fun loadHistory() {
        aliceService?.getService()?.let { service ->
            scope.launch {
                val messages = service.getMessages()
                withContext(Dispatchers.Main) {
                    adapter.updateMessages(messages)
                    (supportFragmentManager.findFragmentByTag(R.id.nav_chat.toString()) as? ChatFragment)?.updateMessages(messages)
                }
                // 恢复：量化历史逻辑
                val embeddingsFile = File(filesDir, "memory_embeddings.json")
                if (!embeddingsFile.exists() && messages.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        window.setFlags(
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        )
                        progressDialog.show()
                    }
                    service.quantizeHistory { progress ->
                        progressBar.progress = progress
                    }
                    withContext(Dispatchers.Main) {
                        progressDialog.dismiss()
                        window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
                    }
                }
            }
        }
    }

    fun sendMessage(message: String) {
        aliceService?.getService()?.let { service ->
            service.sendMessage(message) { response ->
                (supportFragmentManager.findFragmentByTag(R.id.nav_chat.toString()) as? ChatFragment)?.updateAssistantMessage(response)
            }
        }
    }

    // 新增：清除历史记录
    fun clearHistory() {
        aliceService?.clearHistory {
            val messages = aliceService?.getMessages() ?: emptyList()
            adapter.updateMessages(messages)
            (supportFragmentManager.findFragmentByTag(R.id.nav_chat.toString()) as? ChatFragment)?.updateMessages(messages)
        }
    }

    // 新增：导出历史记录
    fun exportHistory(useFilePicker: Boolean = false) {
        if (useFilePicker) {
            // 暂未实现文件选择器逻辑，默认导出到 filesDir
            aliceService?.exportHistory()
        } else {
            aliceService?.exportHistory()
        }
    }

    // 新增：导入历史记录
    fun importHistory() {
        importLauncher.launch("application/*")
    }
    fun navigateToFragment(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .hide(supportFragmentManager.findFragmentByTag(R.id.nav_chat.toString())!!)
            .hide(supportFragmentManager.findFragmentByTag(R.id.nav_webview.toString())!!)
            .hide(supportFragmentManager.findFragmentByTag(R.id.nav_schedule.toString())!!)
            .hide(supportFragmentManager.findFragmentByTag(R.id.nav_settings.toString())!!)
            .add(R.id.fragment_container, fragment)
            .addToBackStack(null)
            .commit()
        binding.toolbarTitle.text = title
        setSupportActionBarBackVisible(true)
        findViewById<BottomNavigationView>(R.id.bottom_navigation).menu.findItem(R.id.nav_settings).isChecked = true
    }

    fun showConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("确定") { _, _ -> onConfirm() }
            .setNegativeButton("取消", null)
            .show()
    }
    override fun onSupportNavigateUp(): Boolean {
        return handleBackNavigation() || super.onSupportNavigateUp()
    }
    override fun onBackPressed() {
        if (!handleBackNavigation()) {
            super.onBackPressed()
        }
    }

    // 新增：处理返回逻辑
    private fun handleBackNavigation(): Boolean {
        if (supportFragmentManager.backStackEntryCount > 0) {
            supportFragmentManager.popBackStack() // 弹出返回栈中的 Fragment
            supportFragmentManager.beginTransaction()
                .hide(supportFragmentManager.findFragmentByTag(R.id.nav_chat.toString())!!)
                .hide(supportFragmentManager.findFragmentByTag(R.id.nav_webview.toString())!!)
                .hide(supportFragmentManager.findFragmentByTag(R.id.nav_schedule.toString())!!)
                .show(supportFragmentManager.findFragmentByTag(R.id.nav_settings.toString())!!)
                .commit()
            binding.toolbarTitle.text = "设置"
            setSupportActionBarBackVisible(false)
            findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.nav_settings
            return true
        }
        return false
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindService(connection)
        scope.cancel()
    }
}