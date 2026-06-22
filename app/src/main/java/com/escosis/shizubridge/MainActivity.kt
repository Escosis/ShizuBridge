package com.escosis.shizubridge

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.escosis.shizubridge.databinding.ActivityMainBinding
import com.escosis.shizubridge.fragment.FileListFragment
import com.escosis.shizubridge.fragment.HomeFragment
import com.escosis.shizubridge.utils.ShellExecutor

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val homeFragment = HomeFragment()
    private val fileFragment = FileListFragment()
    private var activeFragment: Fragment = homeFragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 启用边到边显示，内容延伸至系统栏下方
        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        initSystemInsets()
        initFragments()
        initBottomNav()
    }

    override fun onDestroy() {
        super.onDestroy()
        ShellExecutor.release()
    }

    /**
     * 适配系统栏，自动为顶部标题栏和底部导航栏增加边距
     */
    private fun initSystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            // 顶部Toolbar增加状态栏高度的padding
            binding.toolbar.updatePadding(top = systemBars.top)

            // 底部导航栏增加导航条高度的padding
            binding.bottomNav.updatePadding(bottom = systemBars.bottom)

            insets
        }

        // 设置状态栏图标颜色（深色背景用白色图标，浅色背景用黑色图标）
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
    }

    private fun initFragments() {
        supportFragmentManager.beginTransaction().apply {
            add(R.id.fragment_container, fileFragment, "file").hide(fileFragment)
            add(R.id.fragment_container, homeFragment, "home")
        }.commit()
    }

    private fun initBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    switchFragment(homeFragment)
                    supportActionBar?.title = "ShizuBridge"
                    true
                }
                R.id.nav_file -> {
                    switchFragment(fileFragment)
                    supportActionBar?.title = "文件管理"
                    true
                }
                else -> false
            }
        }
        binding.bottomNav.selectedItemId = R.id.nav_home
    }

    private fun switchFragment(target: Fragment) {
        if (activeFragment == target) return
        supportFragmentManager.beginTransaction().apply {
            hide(activeFragment)
            show(target)
        }.commit()
        activeFragment = target
    }
}