package com.escosis.shizubridge.fragment

import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
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
import androidx.appcompat.app.AlertDialog
import com.escosis.shizubridge.R

class FileListFragment : Fragment() {

    private var _binding: FragmentFileListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FileListAdapter
    private val currentPath = "/data/local/tmp/ShizuBridge"

    private val retryDelayMs = 30000L
    private var retryJob: Job? = null
    private var timerJob: Job? = null
    private var hasRetried = false

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
                Toast.makeText(requireContext(), getString(R.string.empty_no_permission), Toast.LENGTH_SHORT).show()
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
        adapter.onDeleteClick = { fileItem ->
            showDeleteConfirmationDialog(fileItem)
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
            binding.tvEmpty.text = getString(R.string.empty_loading)
            adapter.submitList(emptyList())
            return
        }

        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = "加载中... 已等待 0 秒"

        // 启动秒级计时器
        var elapsed = 0
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
        while (true) {
                delay(1000)
                elapsed++
                binding.tvEmpty.text = getString(R.string.result_loading, elapsed)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = ShellExecutor.exec("mkdir -p $currentPath && ls -la $currentPath")
            timerJob?.cancel() // 结果返回立刻停止计时

            when {
                result.exitCode == -2 -> {
                    if (!hasRetried) {
                        hasRetried = true
                        startRetryCountdown()
                    } else {
                        binding.tvEmpty.text = getString(R.string.result_timeout)
                        adapter.submitList(emptyList())
                    }
                }
                result.isSuccess -> {
                    hasRetried = false
                    retryJob?.cancel()
                    val fileList = FileParser.parseLsOutput(currentPath, result.stdout)
                    adapter.submitList(fileList)
                    binding.tvEmpty.visibility = if (fileList.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvEmpty.text = getString(R.string.empty_directory_empty)
                }
                else -> {
                    hasRetried = false
                    retryJob?.cancel()
                    binding.tvEmpty.text = getString(R.string.result_command_failed, result.stderr)
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
        binding.tvEmpty.text = getString(R.string.result_retry, remainSeconds)

        retryJob = viewLifecycleOwner.lifecycleScope.launch {
            while (remainSeconds > 0) {
                delay(1000)
                remainSeconds--
                binding.tvEmpty.text = getString(R.string.result_retry, remainSeconds)
            }
            loadFileList()
        }
    }

    private fun startImport(uris: List<Uri>) {
        Toast.makeText(requireContext(), getString(R.string.import_start, uris.size), Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
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

            Toast.makeText(requireContext(), getString(R.string.import_complete, successCount), Toast.LENGTH_SHORT).show()
            loadFileList()
        }
    }

    private suspend fun importSingleFile(uri: Uri, targetPath: String): Boolean {
        return withContext(Dispatchers.IO) {
            var inputStream: java.io.InputStream? = null
            var outputStream: java.io.OutputStream? = null
            try {
                // 从SAF读取外部文件
                inputStream = requireContext().contentResolver.openInputStream(uri)
                    ?: return@withContext false
                // 通过Shizuku获取目标文件的写入FD（覆盖模式）
                val pfd = ShellExecutor.openFileWrite(targetPath, append = false)
                    ?: return@withContext false
                outputStream = ParcelFileDescriptor.AutoCloseOutputStream(pfd)
                // 直接复制，使用8MB缓冲区提高效率
                inputStream.copyTo(outputStream, bufferSize = 8 * 1024 * 1024)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                runCatching { inputStream?.close() }
                runCatching { outputStream?.close() }
            }
        }
    }

    private fun startExport(fileItem: FileItem, targetUri: Uri) {
        Toast.makeText(requireContext(), getString(R.string.export_start, fileItem.name), Toast.LENGTH_SHORT).show()
        viewLifecycleOwner.lifecycleScope.launch {
        val success = exportSingleFile(fileItem.path, targetUri)
            Toast.makeText(
                requireContext(),
                if (success) R.string.export_success else R.string.export_failed,
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private suspend fun exportSingleFile(sourcePath: String, targetUri: Uri): Boolean {
        return withContext(Dispatchers.IO) {
            var inputStream: java.io.InputStream? = null
            var outputStream: java.io.OutputStream? = null
            try {
                // 通过Shizuku获取源文件的读取FD
                val pfd = ShellExecutor.openFileRead(sourcePath)
                    ?: return@withContext false
                inputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
                // 通过SAF获取目标URI的输出流
                outputStream = requireContext().contentResolver.openOutputStream(targetUri)
                    ?: return@withContext false
                // 直接复制
                inputStream.copyTo(outputStream, bufferSize = 8 * 1024 * 1024)
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            } finally {
                runCatching { inputStream?.close() }
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

    /**
     * 显示删除确认对话框
     */
    private fun showDeleteConfirmationDialog(fileItem: FileItem) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_message, fileItem.name))
            .setPositiveButton(R.string.delete_positive) { _, _ -> performDelete(fileItem) }
            .setNegativeButton(R.string.delete_negative, null)
            .show()
    }

    /**
     * 执行删除操作（通过 Shizuku）
     */
    private fun performDelete(fileItem: FileItem) {
        lifecycleScope.launch {
            val targetPath = fileItem.path
            val cmd = if (fileItem.isDirectory) {
                "rm -rf \"$targetPath\""
            } else {
                "rm \"$targetPath\""
            }
            val result = ShellExecutor.exec(cmd)
            if (result.isSuccess) {
                Toast.makeText(requireContext(), R.string.delete_success, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.delete_failed, result.stderr), Toast.LENGTH_SHORT).show()
            }
        }
    }
}