package com.escosis.shizubridge.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.escosis.shizubridge.adapter.FileListAdapter
import com.escosis.shizubridge.data.FileItem
import com.escosis.shizubridge.databinding.FragmentFileListBinding
import com.escosis.shizubridge.utils.FileParser
import com.escosis.shizubridge.utils.ImportHelper
import com.escosis.shizubridge.utils.ShellExecutor
import com.escosis.shizubridge.utils.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

class FileListFragment : Fragment() {

    private var _binding: FragmentFileListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FileListAdapter
    private val currentPath = "/data/local/tmp/ShizuBridge"

    private val retryDelayMs = 30000L
    private var retryJob: Job? = null
    private var timerJob: Job? = null
    private var hasRetried = false
    // Base64分块大小：原始数据32KB，编码后约43KB，平衡效率与命令长度安全性
    private val TRANSFER_CHUNK_SIZE = 32 * 1024

    private val permissionListener: (Boolean) -> Unit = { }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        uris?.takeIf { it.isNotEmpty() }?.let { startImport(it) }
    }

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        uri?.let {
            pendingExportFile?.let { file -> startExport(file, it) }
            pendingExportFile = null
        }
    }

    private var pendingExportFile: FileItem? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFileListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ShizukuManager.addPermissionListener(permissionListener)
        initList()
        binding.tvPath.text = currentPath

        binding.btnRefresh.setOnClickListener {
            timerJob?.cancel()
            retryJob?.cancel()
            hasRetried = false
            loadFileList()
        }

        binding.btnImport.setOnClickListener {
            if (!ShizukuManager.hasPermission()) {
                Toast.makeText(requireContext(), "请先在首页申请权限", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            importLauncher.launch(arrayOf("*/*"))
        }
    }

    override fun onResume() {
        super.onResume()
        loadFileList()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerJob?.cancel()
        retryJob?.cancel()
        ShizukuManager.removePermissionListener(permissionListener)
        _binding = null
    }

    private fun initList() {
        adapter = FileListAdapter()
        adapter.onExportClick = { fileItem ->
            pendingExportFile = fileItem
            exportLauncher.launch(fileItem.name)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
    }

    /**
     * 加载文件列表，带秒级计时
     * 规律说明：Shizuku启动后30秒内连接会假死；
     * 加速方法：关闭权限后重新授权，可立即恢复连接
     */
    private fun loadFileList() {
        if (!ShizukuManager.hasPermission()) {
            binding.tvEmpty.text = "请先在首页申请 Shizuku 权限"
            adapter.submitList(emptyList())
            return
        }

        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = "加载中... 已等待 0 秒"

        // 启动秒级计时器
        var elapsed = 0
        timerJob = lifecycleScope.launch {
            while (true) {
                delay(1000)
                elapsed++
                binding.tvEmpty.text = "加载中... 已等待 $elapsed 秒"
            }
        }

        lifecycleScope.launch {
            val result = ShellExecutor.exec("mkdir -p $currentPath && ls -la $currentPath")
            timerJob?.cancel() // 结果返回立刻停止计时

            when {
                result.exitCode == -2 -> {
                    if (!hasRetried) {
                        hasRetried = true
                        startRetryCountdown()
                    } else {
                        binding.tvEmpty.text = "连接超时，30秒后仍未就绪\n请打开Shizuku关闭权限重新授权"
                        adapter.submitList(emptyList())
                    }
                }
                result.isSuccess -> {
                    hasRetried = false
                    retryJob?.cancel()
                    val fileList = FileParser.parseLsOutput(currentPath, result.stdout)
                    adapter.submitList(fileList)
                    binding.tvEmpty.visibility = if (fileList.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvEmpty.text = "目录为空，点击右上角导入文件"
                }
                else -> {
                    hasRetried = false
                    retryJob?.cancel()
                    binding.tvEmpty.text = "加载失败：${result.stderr}"
                    adapter.submitList(emptyList())
                }
            }
        }
    }

    /**
     * 30秒重试倒计时
     */
    private fun startRetryCountdown() {
        var remainSeconds = 30
        binding.tvEmpty.text = "服务连接未就绪，30秒后自动重试（剩余 $remainSeconds 秒）\n可打开Shizuku关闭权限重新授权以跳过等待"

        retryJob = lifecycleScope.launch {
            while (remainSeconds > 0) {
                delay(1000)
                remainSeconds--
                binding.tvEmpty.text = "服务连接未就绪，30秒后自动重试（剩余 $remainSeconds 秒）\n可打开Shizuku关闭权限重新授权以跳过等待"
            }
            loadFileList()
        }
    }

    // ========== 以下导入/导出逻辑保持不变 ==========
// ========== 导入：Base64分块传输，无中转文件 ==========
    private fun startImport(uris: List<Uri>) {
        Toast.makeText(requireContext(), "开始导入 ${uris.size} 个文件", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            var successCount = 0
            // 确保目标目录存在
            ShellExecutor.exec("mkdir -p $currentPath")

            withContext(Dispatchers.IO) {
                uris.forEach { uri ->
                    val fileName = getFileName(uri) ?: return@forEach
                    val success = importSingleFile(uri, "$currentPath/$fileName")
                    if (success) {
                        // 设置777全局权限
                        ShellExecutor.exec("chmod 777 \"$currentPath/$fileName\"")
                        successCount++
                    }
                }
            }

            Toast.makeText(requireContext(), "导入完成：成功 $successCount 个", Toast.LENGTH_SHORT).show()
            loadFileList()
        }
    }

    /**
     * 单文件导入：Base64分块写入，无中转文件，兼容多用户隔离
     */
    private suspend fun importSingleFile(uri: Uri, targetPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            var inputStream: java.io.InputStream? = null
            try {
                inputStream = requireContext().contentResolver.openInputStream(uri)
                    ?: return@withContext false
                val buffer = ByteArray(TRANSFER_CHUNK_SIZE)
                var isFirstChunk = true
                var readLen: Int

                while (inputStream.read(buffer).also { readLen = it } != -1) {
                    // 截取实际读取的字节，转无换行Base64
                    val chunk = buffer.copyOf(readLen)
                    val base64Str = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)

                    // 第一块覆盖写入，后续块追加写入
                    val redirect = if (isFirstChunk) ">" else ">>"
                    // 单引号包裹Base64串，避免Shell特殊字符解析问题
                    val cmd = "printf '%s' '$base64Str' | base64 -d $redirect \"$targetPath\""
                    val result = ShellExecutor.exec(cmd)

                    if (!result.isSuccess) {
                        return@withContext false
                    }
                    isFirstChunk = false
                }
                true
            } catch (e: Exception) {
                false
            } finally {
                runCatching { inputStream?.close() }
            }
        }
    }

    // ========== 导出：Base64分块读取，无中转文件 ==========
    private fun startExport(fileItem: FileItem, targetUri: Uri) {
        Toast.makeText(requireContext(), "正在导出：${fileItem.name}", Toast.LENGTH_SHORT).show()
        lifecycleScope.launch {
            val success = exportSingleFile(fileItem.path, targetUri)
            Toast.makeText(
                requireContext(),
                if (success) "导出成功" else "导出失败",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /**
     * 单文件导出：dd按偏移分块 + Base64编码，无中转文件，兼容多用户隔离
     */
    private suspend fun exportSingleFile(sourcePath: String, targetUri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            var outputStream: java.io.OutputStream? = null
            try {
                outputStream = requireContext().contentResolver.openOutputStream(targetUri)
                    ?: return@withContext false
                var offset = 0L

                while (true) {
                    // dd按字节偏移读取指定大小块，管道给base64编码，丢弃stderr避免统计信息干扰
                    val cmd = "dd if=\"$sourcePath\" bs=1 count=$TRANSFER_CHUNK_SIZE skip=$offset 2>/dev/null | base64"
                    val result = ShellExecutor.exec(cmd)

                    if (!result.isSuccess || result.stdout.isBlank()) {
                        break
                    }

                    // 去掉末尾换行，解码Base64
                    val base64Str = result.stdout.trim()
                    val chunk = try {
                        android.util.Base64.decode(base64Str, android.util.Base64.DEFAULT)
                    } catch (e: Exception) {
                        return@withContext false
                    }

                    outputStream.write(chunk)
                    offset += chunk.size

                    // 读到文件末尾，退出循环
                    if (chunk.size < TRANSFER_CHUNK_SIZE) {
                        break
                    }
                }
                true
            } catch (e: Exception) {
                false
            } finally {
                runCatching { outputStream?.flush() }
                runCatching { outputStream?.close() }
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        runCatching {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }
}