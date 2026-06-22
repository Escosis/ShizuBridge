package com.escosis.shizubridge

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.escosis.shizubridge.databinding.ActivityMainBinding
import com.escosis.shizubridge.utils.ShellExecutor
import com.escosis.shizubridge.utils.ShizukuManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val permissionListener: (Boolean) -> Unit = { granted ->
        refreshStatus()
        Toast.makeText(
            this,
            if (granted) "授权成功" else "授权被拒绝",
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initClick()
        refreshStatus()
    }

    override fun onStart() {
        super.onStart()
        ShizukuManager.addPermissionListener(permissionListener)
    }

    override fun onStop() {
        super.onStop()
        ShizukuManager.removePermissionListener(permissionListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        ShellExecutor.release()
    }

    private fun initClick() {
        binding.btnRequestPermission.setOnClickListener {
            ShizukuManager.requestPermission()
        }

        binding.btnTestCommand.setOnClickListener {
            testListTmp()
        }
    }

    private fun refreshStatus() {
        val available = ShizukuManager.isServiceAvailable()
        val granted = ShizukuManager.hasPermission()

        binding.tvStatus.text = when {
            !available -> "❌ Shizuku 服务未运行"
            !granted -> "⚠️ 服务已连接，尚未授权"
            else -> "✅ 服务正常，已获得权限"
        }

        binding.btnRequestPermission.isEnabled = available && !granted
        binding.btnTestCommand.isEnabled = granted
    }

    private fun testListTmp() {
        binding.tvResult.text = "正在执行命令..."
        lifecycleScope.launch {
            val result = ShellExecutor.exec("ls -la /data/local/tmp")
            binding.tvResult.text = result
        }
    }
}