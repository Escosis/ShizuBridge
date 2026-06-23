package com.escosis.shizubridge

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import com.escosis.shizubridge.databinding.ActivityMainBinding
import com.escosis.shizubridge.fragment.FileListFragment
import com.escosis.shizubridge.fragment.HomeFragment
import com.escosis.shizubridge.fragment.SettingsFragment
import com.escosis.shizubridge.utils.ShellExecutor
import androidx.appcompat.app.AppCompatDelegate
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var homeFragment: HomeFragment? = null
    private var fileFragment: FileListFragment? = null
    private var settingsFragment: SettingsFragment? = null
    private var activeFragment: Fragment? = null

    private var currentNavItemId: Int = R.id.nav_home

    override fun onCreate(savedInstanceState: Bundle?) {
        // 在 super.onCreate 之前应用主题（从 SharedPreferences 读取）
        applySavedTheme()
        applySavedLanguage()

        super.onCreate(savedInstanceState)

        savedInstanceState?.let {
            currentNavItemId = it.getInt("current_nav_item", R.id.nav_home)
        }

        WindowCompat.setDecorFitsSystemWindows(window, false)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        initSystemInsets()
        updateStatusBarAppearance()
        initFragments()
        initBottomNav()
    }

    private fun applySavedTheme() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val mode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun applySavedLanguage() {
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val mode = prefs.getInt("language_mode", SettingsFragment.LANG_FOLLOW_SYSTEM)
        val locale = when (mode) {
            SettingsFragment.LANG_CHINESE -> Locale("zh", "CN")
            SettingsFragment.LANG_ENGLISH -> Locale.ENGLISH
            else -> null
        }
        if (locale != null) {
            Locale.setDefault(locale)
            val config = resources.configuration
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        } else {
            // 跟随系统：恢复系统默认（可从 Configuration 获取）
            val config = resources.configuration
            val systemLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                config.locales.get(0)
            } else {
                config.locale
            }
            if (systemLocale != null) {
                Locale.setDefault(systemLocale)
                config.setLocale(systemLocale)
                resources.updateConfiguration(config, resources.displayMetrics)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_nav_item", currentNavItemId)
    }

    override fun onDestroy() {
        super.onDestroy()
        ShellExecutor.release()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateStatusBarAppearance()
    }

    private fun initSystemInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = systemBars.top)
            binding.bottomNav.updatePadding(bottom = systemBars.bottom)
            insets
        }
    }

    private fun initFragments() {
        val fm = supportFragmentManager

        var home = fm.findFragmentByTag("home") as? HomeFragment
        var file = fm.findFragmentByTag("file") as? FileListFragment
        var settings = fm.findFragmentByTag("settings") as? SettingsFragment

        val transaction = fm.beginTransaction()

        if (home == null) {
            home = HomeFragment()
            transaction.add(R.id.fragment_container, home, "home")
        }
        if (file == null) {
            file = FileListFragment()
            transaction.add(R.id.fragment_container, file, "file")
        }
        if (settings == null) {
            settings = SettingsFragment()
            transaction.add(R.id.fragment_container, settings, "settings")
        }

        homeFragment = home
        fileFragment = file
        settingsFragment = settings

        // 根据 currentNavItemId 决定显示哪个 Fragment，默认隐藏其他两个
        val targetFragment = when (currentNavItemId) {
            R.id.nav_home -> home
            R.id.nav_file -> file
            R.id.nav_settings -> settings
            else -> home
        }
        val fragments = listOf(home, file, settings)
        fragments.forEach { frag ->
            if (frag == targetFragment) {
                transaction.show(frag)
            } else {
                transaction.hide(frag)
            }
        }

        transaction.commitNow()
        activeFragment = targetFragment
    }

    private fun initBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    homeFragment?.let { switchFragment(it) }
                    supportActionBar?.title = getString(R.string.title_home)
                    currentNavItemId = R.id.nav_home
                    true
                }
                R.id.nav_file -> {
                    fileFragment?.let { switchFragment(it) }
                    supportActionBar?.title = getString(R.string.title_file)
                    currentNavItemId = R.id.nav_file
                    true
                }
                R.id.nav_settings -> {
                    settingsFragment?.let { switchFragment(it) }
                    supportActionBar?.title = getString(R.string.title_settings)
                    currentNavItemId = R.id.nav_settings
                    true
                }
                else -> false
            }
        }

        binding.bottomNav.selectedItemId = currentNavItemId
    }

    private fun switchFragment(target: Fragment) {
        if (activeFragment == target) return
        val transaction = supportFragmentManager.beginTransaction()
        activeFragment?.let { transaction.hide(it) }
        transaction.show(target)
        transaction.commit()
        activeFragment = target
    }

    private fun updateStatusBarAppearance() {
        val nightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isNight = nightMode == Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }
    }
}