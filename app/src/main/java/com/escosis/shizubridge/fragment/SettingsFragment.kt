package com.escosis.shizubridge.fragment

import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.escosis.shizubridge.R
import com.escosis.shizubridge.databinding.FragmentSettingsBinding
import rikka.shizuku.shared.BuildConfig
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: SharedPreferences

    companion object {
        private const val PREF_NAME = "settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE_MODE = "language_mode"

        // 语言模式常量
        const val LANG_FOLLOW_SYSTEM = 0
        const val LANG_CHINESE = 1
        const val LANG_ENGLISH = 2

        private const val GITHUB_URL = "https://github.com/Escosis/ShizuBridge"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext().getSharedPreferences(PREF_NAME, android.content.Context.MODE_PRIVATE)

        val versionName = try {
            val packageInfo = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            packageInfo.versionName
        } catch (e: Exception) {
            "unknown"
        }
        binding.tvVersion.text = getString(R.string.version) + " $versionName"

        // --- 加载主题设置 ---
        val currentTheme = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> binding.radioFollowSystem.isChecked = true
            AppCompatDelegate.MODE_NIGHT_NO -> binding.radioLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.radioDark.isChecked = true
        }
        binding.themeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioFollowSystem -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                R.id.radioLight -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.radioDark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            applyTheme(mode)
        }

        // --- 加载语言设置 ---
        val currentLang = prefs.getInt(KEY_LANGUAGE_MODE, LANG_FOLLOW_SYSTEM)
        when (currentLang) {
            LANG_FOLLOW_SYSTEM -> binding.radioLangFollowSystem.isChecked = true
            LANG_CHINESE -> binding.radioLangChinese.isChecked = true
            LANG_ENGLISH -> binding.radioLangEnglish.isChecked = true
        }
        binding.languageRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.radioLangFollowSystem -> LANG_FOLLOW_SYSTEM
                R.id.radioLangChinese -> LANG_CHINESE
                R.id.radioLangEnglish -> LANG_ENGLISH
                else -> LANG_FOLLOW_SYSTEM
            }
            applyLanguage(mode)
        }

        // --- GitHub 链接点击 ---
        binding.llGitHub.setOnClickListener {
            openGitHub()
        }
    }

    private fun applyTheme(mode: Int) {
        prefs.edit().putInt(KEY_THEME_MODE, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
        activity?.recreate()
    }

    private fun applyLanguage(mode: Int) {
        prefs.edit().putInt(KEY_LANGUAGE_MODE, mode).apply()
        // 应用语言
        val locale = when (mode) {
            LANG_CHINESE -> Locale("zh", "CN")
            LANG_ENGLISH -> Locale.ENGLISH
            else -> null // 跟随系统，不覆盖
        }
        if (locale != null) {
            Locale.setDefault(locale)
            val config = resources.configuration
            config.setLocale(locale)
            resources.updateConfiguration(config, resources.displayMetrics)
        } else {
            // 跟随系统：恢复系统默认 Locale
            val systemLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                resources.configuration.locales.get(0)
            } else {
                resources.configuration.locale
            }
            if (systemLocale != null) {
                Locale.setDefault(systemLocale)
                val config = resources.configuration
                config.setLocale(systemLocale)
                resources.updateConfiguration(config, resources.displayMetrics)
            }
        }
        // 重建 Activity
        activity?.recreate()
    }

    private fun openGitHub() {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL))
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), R.string.open_github_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}