package com.escosis.shizubridge

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.escosis.shizubridge.databinding.ActivityMainBinding
import com.escosis.shizubridge.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // 目标导入目录
    private val targetDir = "/data/local/tmp/ShizuBridge"

    // 文件默认权限（644 = 所有者读写，其他只读）
    private val defaultFilePermission = "777"

    // 系统文件选择器注册
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        uris?.takeIf { it.isNotEmpty() }?.let {
            startImport(it)
        }
    }

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
        FileUtils.clearTempCache(this)
        ImportHelper.clearTempCache(this)
    }

    private fun initClick() {
        binding.btnRequestPermission.setOnClickListener {
            ShizukuManager.requestPermission()
        }

        binding.btnTestCommand.setOnClickListener {
            testListTargetDir()
        }

        binding.btnImportFile.setOnClickListener {
            // 打开系统文件选择器，支持所有文件类型
            filePickerLauncher.launch(arrayOf("*/*"))
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
        binding.btnImportFile.isEnabled = granted
    }

    /**
     * 测试：列出目标目录内容
     */
    private fun testListTargetDir() {
        binding.tvResult.text = "正在执行命令..."
        lifecycleScope.launch {
            // 先确保目录存在
            ShellExecutor.exec("mkdir -p $targetDir")
            val result = ShellExecutor.exec("ls -la $targetDir")
            binding.tvResult.text = if (result.isSuccess) {
                "目标目录 $targetDir 内容：\n\n${result.stdout.ifEmpty { "(目录为空)" }}"
            } else {
                "执行失败\n错误信息: ${result.stderr}"
            }
        }
    }

    /**
     * 开始批量导入文件（修复统计计数）
     */
    private fun startImport(uris: List<Uri>) {
        binding.tvResult.text = "开始导入，共 ${uris.size} 个文件\n"
        lifecycleScope.launch {
            var successCount = 0
            var failCount = 0

            // 1. 先确保目标目录存在
            binding.tvResult.append("\n[1/3] 创建目标目录...\n")
            val createDirResult = ShellExecutor.exec("mkdir -p $targetDir")
            if (!createDirResult.isSuccess) {
                binding.tvResult.append("❌ 目录创建失败：${createDirResult.stderr}\n")
                return@launch
            }
            binding.tvResult.append("✅ 目标目录就绪：$targetDir\n")

            // 2. 逐个导入文件
            binding.tvResult.append("\n[2/3] 开始导入文件...\n")
            withContext(Dispatchers.IO) {
                uris.forEachIndexed { index, uri ->
                    val fileName = FileUtils.getFileName(this@MainActivity, uri)
                    runOnUiThread {
                        binding.tvResult.append("\n--- 第 ${index+1} 个：$fileName ---\n")
                    }

                    val (success, errorMsg) = importSingleFile(uri, fileName)
                    // 计数直接在IO线程执行，确保统计准确
                    if (success) {
                        successCount++
                    } else {
                        failCount++
                    }

                    runOnUiThread {
                        if (success) {
                            binding.tvResult.append("✅ 导入成功\n")
                        } else {
                            binding.tvResult.append("❌ 导入失败：$errorMsg\n")
                        }
                    }
                }
            }

            // 3. 清理缓存
            ImportHelper.clearTempCache(this@MainActivity)

            // 4. 最终结果
            binding.tvResult.append("\n====================\n")
            binding.tvResult.append("导入完成：成功 $successCount 个，失败 $failCount 个\n")
            Toast.makeText(this@MainActivity, "导入完成", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 导入单个文件（公共目录中转方案，极致稳定）
     */
    private suspend fun importSingleFile(uri: Uri, fileName: String): Pair<Boolean, String> {
        var tempPath: String? = null
        return try {
            // 1. 主进程中转到外部公共缓存目录
            tempPath = ImportHelper.copyUriToPublicCache(this, uri, fileName)
            val targetPath = "$targetDir/$fileName"

            // 2. 高权限服务复制到目标目录（只传字符串路径，无IPC大数据）
            val copyCmd = "cp \"$tempPath\" \"$targetPath\""
            val copyResult = ShellExecutor.exec(copyCmd)
            if (!copyResult.isSuccess) {
                return false to "复制失败: ${copyResult.stderr.ifEmpty { "退出码 ${copyResult.exitCode}" }}"
            }

            // 3. 设置文件权限
            val chmodCmd = "chmod $defaultFilePermission \"$targetPath\""
            val chmodResult = ShellExecutor.exec(chmodCmd)
            if (!chmodResult.isSuccess) {
                return false to "权限设置失败: ${chmodResult.stderr}"
            }

            true to ""
        } catch (e: Exception) {
            false to "异常: ${e.message ?: e.javaClass.simpleName}"
        } finally {
            // 4. 清理临时文件
            tempPath?.let { runCatching<Unit> { File(it).delete() } }
        }
    }
}