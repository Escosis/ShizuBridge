package com.escosis.shizubridge.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.escosis.shizubridge.databinding.FragmentHomeBinding
import com.escosis.shizubridge.utils.ShellExecutor
import com.escosis.shizubridge.utils.ShizukuManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.escosis.shizubridge.R

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val retryDelayMs = 30000L // 规律：30秒后服务就绪
    private var retryJob: Job? = null
    private var timerJob: Job? = null
    private var hasRetried = false // 仅自动重试1次

    // 权限回调
    private val permissionListener: (Boolean) -> Unit = { granted ->
        refreshStatus()
        Toast.makeText(
            requireContext(),
            if (granted) R.string.permission_granted else R.string.permission_rejected,
            Toast.LENGTH_SHORT
        ).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ShizukuManager.addPermissionListener(permissionListener)
        initClick()
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // 页面销毁取消所有计时/重试任务
        timerJob?.cancel()
        retryJob?.cancel()
        ShizukuManager.removePermissionListener(permissionListener)
        _binding = null
    }

    private fun initClick() {
        binding.btnRequestPermission.setOnClickListener {
            ShizukuManager.requestPermission()
        }

        binding.btnTestCommand.setOnClickListener {
            // 手动点击重置所有状态
            timerJob?.cancel()
            retryJob?.cancel()
            hasRetried = false
            testListTargetDir()
        }

        binding.btnOpenShizuku.setOnClickListener {
            val shizukuPkg = "moe.shizuku.privileged.api"
            val intent = requireContext().packageManager.getLaunchIntentForPackage(shizukuPkg)
            if (intent != null) {
                startActivity(intent)
                Toast.makeText(requireContext(), getString(R.string.toast_authorize_success), Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), R.string.shizuku_not_running, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun refreshStatus() {
        val available = ShizukuManager.isServiceAvailable()
        val granted = ShizukuManager.hasPermission()

        binding.tvStatus.text = when {
            !available -> getString(R.string.status_not_running)
            !granted -> getString(R.string.status_connected_no_perm)
            else -> getString(R.string.status_ready)
        }

        binding.btnRequestPermission.isEnabled = available && !granted
        binding.btnTestCommand.isEnabled = granted
    }

    /**
     * 执行测试命令，带秒级计时
     * 规律说明：Shizuku启动后30秒内连接会假死，重新授权无效；
     * 加速方法：在Shizuku中关闭本应用权限后重新授权，可立即恢复连接
     */
    private fun testListTargetDir() {
        val targetDir = "/data/local/tmp/ShizuBridge"
        binding.tvResult.text = "${getString(R.string.result_loading, 0)}"

        // 启动秒级计时器（与命令并行）
        var elapsed = 0
        timerJob = viewLifecycleOwner.lifecycleScope.launch {
        while (true) {
                delay(1000)
                elapsed++
                binding.tvResult.text = getString(R.string.result_loading, elapsed)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
        val result = ShellExecutor.exec("mkdir -p $targetDir && ls -la $targetDir")
            timerJob?.cancel() // 命令返回立刻停止计时

            when {
                result.exitCode == -2 -> {
                    if (!hasRetried) {
                        hasRetried = true
                        startRetryCountdown(targetDir)
                    } else {
                        binding.tvResult.text = getString(R.string.result_timeout)
                    }
                }
                result.isSuccess -> {
                    hasRetried = false
                    retryJob?.cancel()
                    binding.tvResult.text = getString(R.string.result_command_success, targetDir, result.stdout.ifEmpty { "(empty)" })
                }
                else -> {
                    hasRetried = false
                    retryJob?.cancel()
                    binding.tvResult.text = getString(R.string.result_command_failed, result.stderr)
                }
            }
        }
    }

    /**
     * 30秒重试倒计时
     */
    private fun startRetryCountdown(targetDir: String) {
        var remainSeconds = 30
        binding.tvResult.text = getString(R.string.result_retry, remainSeconds)

        retryJob = viewLifecycleOwner.lifecycleScope.launch {
        while (remainSeconds > 0) {
                delay(1000)
                remainSeconds--
                binding.tvResult.text = getString(R.string.result_retry, remainSeconds)
            }
            testListTargetDir()
        }
    }
}