package br.com.nqber.glauncher

import android.content.res.Configuration
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        val currentTheme = LauncherSettings.getThemeMode(this)
        LauncherSettings.applyTheme(currentTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupSystemBars()
        setupHeader()
        setupThemeSection()
        setupLetterLayoutSection()
        setupCircleSizeSection()
        setupSpacingSection()
        setupUpdatesSection()
    }

    private fun setupSystemBars() {
        val isNight = when (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) {
            Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }
    }

    private fun setupHeader() {
        val btnBack = findViewById<ImageButton>(R.id.btn_back_settings)
        btnBack.setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        })
    }

    private fun setupThemeSection() {
        val currentTheme = LauncherSettings.getThemeMode(this)
        val radioSystem = findViewById<RadioButton>(R.id.radio_theme_system)
        val radioDark = findViewById<RadioButton>(R.id.radio_theme_dark)
        val radioLight = findViewById<RadioButton>(R.id.radio_theme_light)

        when (currentTheme) {
            LauncherSettings.THEME_LIGHT -> radioLight.isChecked = true
            LauncherSettings.THEME_DARK -> radioDark.isChecked = true
            else -> radioSystem.isChecked = true
        }

        val themeGroup = findViewById<RadioGroup>(R.id.theme_radio_group)
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.radio_theme_light -> LauncherSettings.THEME_LIGHT
                R.id.radio_theme_dark -> LauncherSettings.THEME_DARK
                else -> LauncherSettings.THEME_SYSTEM
            }
            if (newTheme != currentTheme) {
                LauncherSettings.setThemeMode(this, newTheme)
            }
        }
    }

    private fun setupLetterLayoutSection() {
        val currentLayout = LauncherSettings.getLetterLayout(this)
        val radioCircular = findViewById<RadioButton>(R.id.radio_layout_circular)
        val radioLine = findViewById<RadioButton>(R.id.radio_layout_line)

        if (currentLayout == LauncherSettings.LAYOUT_LINE) {
            radioLine.isChecked = true
        } else {
            radioCircular.isChecked = true
        }

        val layoutGroup = findViewById<RadioGroup>(R.id.layout_radio_group)
        layoutGroup.setOnCheckedChangeListener { _, checkedId ->
            val newLayout = when (checkedId) {
                R.id.radio_layout_line -> LauncherSettings.LAYOUT_LINE
                else -> LauncherSettings.LAYOUT_CIRCULAR
            }
            LauncherSettings.setLetterLayout(this, newLayout)
        }
    }

    private fun setupCircleSizeSection() {
        val labelSize = findViewById<TextView>(R.id.label_circle_size)
        val seekbarSize = findViewById<SeekBar>(R.id.seekbar_circle_size)

        val currentScale = LauncherSettings.getCircleScale(this)
        labelSize.text = "Tamanho: $currentScale%"
        seekbarSize.progress = currentScale - 60

        seekbarSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val scale = progress + 60
                labelSize.text = "Tamanho: $scale%"
                if (fromUser) {
                    LauncherSettings.setCircleScale(this@SettingsActivity, scale)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupSpacingSection() {
        val labelHorizontal = findViewById<TextView>(R.id.label_spacing_horizontal)
        val seekbarHorizontal = findViewById<SeekBar>(R.id.seekbar_spacing_horizontal)
        val labelVertical = findViewById<TextView>(R.id.label_spacing_vertical)
        val seekbarVertical = findViewById<SeekBar>(R.id.seekbar_spacing_vertical)

        val currentH = LauncherSettings.getSpacingHorizontal(this)
        val currentV = LauncherSettings.getSpacingVertical(this)

        labelHorizontal.text = "Espaçamento horizontal: $currentH dp"
        seekbarHorizontal.progress = currentH

        labelVertical.text = "Espaçamento vertical: $currentV dp"
        seekbarVertical.progress = currentV

        seekbarHorizontal.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                labelHorizontal.text = "Espaçamento horizontal: $progress dp"
                if (fromUser) {
                    LauncherSettings.setSpacingHorizontal(this@SettingsActivity, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekbarVertical.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                labelVertical.text = "Espaçamento vertical: $progress dp"
                if (fromUser) {
                    LauncherSettings.setSpacingVertical(this@SettingsActivity, progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupUpdatesSection() {
        val btnUpdate = findViewById<Button>(R.id.btn_check_updates)
        btnUpdate.setOnClickListener {
            UpdateManager.checkForUpdates(this, force = true, manual = true)
        }

        val versionLabel = findViewById<TextView>(R.id.app_version_label)
        versionLabel.text = "GLauncher v${BuildConfig.VERSION_NAME}"
    }
}
