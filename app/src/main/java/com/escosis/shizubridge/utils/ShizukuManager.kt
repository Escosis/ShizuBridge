package com.escosis.shizubridge.utils

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

object ShizukuManager {

    private const val REQUEST_CODE = 1001
    private val permissionListeners = mutableListOf<(Boolean) -> Unit>()

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            permissionListeners.forEach { it(granted) }
        }
    }

    fun init() {
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
    }

    fun release() {
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        permissionListeners.clear()
    }

    /**
     * 检测Shizuku服务是否可用
     */
    fun isServiceAvailable(): Boolean = Shizuku.pingBinder()

    /**
     * 检测是否已获得权限
     */
    fun hasPermission(): Boolean {
        if (!isServiceAvailable()) return false
        return runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
    }

    /**
     * 申请权限
     */
    fun requestPermission() {
        if (!isServiceAvailable()) return
        if (hasPermission()) {
            permissionListeners.forEach { it(true) }
            return
        }
        Shizuku.requestPermission(REQUEST_CODE)
    }

    fun addPermissionListener(listener: (Boolean) -> Unit) {
        permissionListeners.add(listener)
    }

    fun removePermissionListener(listener: (Boolean) -> Unit) {
        permissionListeners.remove(listener)
    }
}