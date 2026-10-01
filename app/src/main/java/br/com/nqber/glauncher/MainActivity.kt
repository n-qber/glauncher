package br.com.nqber.glauncher

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private data class State(
        val candidates: List<AppInfo>,
        val title: String,
        val charIndex: Int = 0
    )

    private data class CircleViewHolder(
        val root: View,
        val rangeView: CircularRangeView,
        val appContainer: View,
        val appIcon: ImageView,
        val appLabel: TextView
    )

    private val history = mutableListOf<State>()
    private val circleHolders = mutableListOf<CircleViewHolder>()
    private var shouldResetOnResume = false
    private var isPreviewActive = false

    private lateinit var btnBack: ImageButton
    private lateinit var headerTitle: TextView
    private lateinit var totalAppsCount: TextView
    private lateinit var btnSettings: ImageButton
    private lateinit var btnUpdate: ImageButton

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppRepository.invalidate(this@MainActivity)
            resetToRoot(forceReload = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme preference before activity layout inflation
        val currentTheme = LauncherSettings.getThemeMode(this)
        LauncherSettings.applyTheme(currentTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupSystemBars()
        bindViews()
        setupNavigation()
        registerPackageReceiver()

        // Apply saved circle margins
        applyCircleMargins(
            LauncherSettings.getMarginHorizontal(this),
            LauncherSettings.getMarginVertical(this)
        )

        // Initial load
        resetToRoot()

        // Background update check
        UpdateManager.checkForUpdates(this)
    }

    override fun onResume() {
        super.onResume()
        if (shouldResetOnResume) {
            shouldResetOnResume = false
            resetToRoot()
        }
    }

    override fun onPause() {
        super.onPause()
        if (isPreviewActive) {
            cancelPreview()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        setupSystemBars()
        renderCurrentState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resetToRoot()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
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

    private fun bindViews() {
        btnBack = findViewById(R.id.btn_back)
        headerTitle = findViewById(R.id.header_title)
        totalAppsCount = findViewById(R.id.total_apps_count)
        btnSettings = findViewById(R.id.btn_settings)
        btnUpdate = findViewById(R.id.btn_update)

        btnBack.setOnClickListener {
            goBack()
        }

        btnBack.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            resetToRoot()
            true
        }

        headerTitle.setOnClickListener {
            resetToRoot()
        }

        headerTitle.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            resetToRoot()
            true
        }

        btnSettings.setOnClickListener {
            showSettingsDialog()
        }

        btnUpdate.setOnClickListener {
            UpdateManager.checkForUpdates(this, force = true, manual = true)
        }

        val cellIds = listOf(
            R.id.cell_0, R.id.cell_1,
            R.id.cell_2, R.id.cell_3,
            R.id.cell_4, R.id.cell_5
        )

        circleHolders.clear()
        for (id in cellIds) {
            val cellView = findViewById<View>(id)
            circleHolders.add(
                CircleViewHolder(
                    root = cellView,
                    rangeView = cellView.findViewById(R.id.circular_range_view),
                    appContainer = cellView.findViewById(R.id.app_container),
                    appIcon = cellView.findViewById(R.id.app_icon),
                    appLabel = cellView.findViewById(R.id.app_label)
                )
            )
        }
    }

    private fun setupNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isPreviewActive) {
                    cancelPreview()
                    return
                }
                if (history.size > 1) {
                    goBack()
                }
                // When at root, do nothing to stay on the home screen
            }
        })
    }

    private fun registerPackageReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter)
    }

    private fun resetToRoot(forceReload: Boolean = false) {
        isPreviewActive = false
        if (!forceReload) {
            val cached = AppRepository.getCachedApps()
            if (cached != null) {
                history.clear()
                history.add(State(cached, "GLauncher", charIndex = 0))
                renderCurrentState()
                return
            }
        }

        lifecycleScope.launch {
            val allApps = withContext(Dispatchers.IO) {
                AppRepository.getApps(this@MainActivity, forceReload = forceReload)
            }
            history.clear()
            history.add(State(allApps, "GLauncher", charIndex = 0))
            renderCurrentState()

            // Preload icons and verify background updates
            withContext(Dispatchers.IO) {
                AppRepository.preloadIcons(this@MainActivity, allApps.map { it.packageName })
                val updatedApps = AppRepository.syncWithSystem(this@MainActivity)
                if (updatedApps != null) {
                    withContext(Dispatchers.Main) {
                        if (history.size <= 1) {
                            history.clear()
                            history.add(State(updatedApps, "GLauncher", charIndex = 0))
                            renderCurrentState()
                        }
                    }
                }
            }
        }
    }

    private fun goBack() {
        if (isPreviewActive) {
            cancelPreview()
            return
        }
        if (history.size > 1) {
            history.removeAt(history.lastIndex)
            renderCurrentState()
        }
    }

    private fun applyCircleMargins(marginHorizontalDp: Int, marginVerticalDp: Int) {
        val hPx = (marginHorizontalDp * resources.displayMetrics.density).toInt()
        val vPx = (marginVerticalDp * resources.displayMetrics.density).toInt()
        for (holder in circleHolders) {
            val lp = holder.root.layoutParams as? ViewGroup.MarginLayoutParams
            if (lp != null) {
                lp.setMargins(hPx, vPx, hPx, vPx)
                holder.root.layoutParams = lp
            }
        }
    }

    private fun renderCurrentState() {
        val currentState = history.lastOrNull() ?: return
        val candidates = currentState.candidates
        isPreviewActive = false

        // Header update
        if (history.size > 1) {
            btnBack.visibility = View.VISIBLE
            headerTitle.text = history.drop(1).joinToString(" › ") { it.title }
        } else {
            btnBack.visibility = View.INVISIBLE
            headerTitle.text = getString(R.string.app_name)
        }
        totalAppsCount.text = "${candidates.size} apps"

        // Layout mode preference (Circular vs Line)
        val layoutMode = if (LauncherSettings.getLetterLayout(this) == LauncherSettings.LAYOUT_LINE) {
            CircularRangeView.LetterLayout.LINE
        } else {
            CircularRangeView.LetterLayout.CIRCULAR
        }

        // Partition into 6 buckets using prefix funneling at charIndex
        val buckets = GridPartition.partition(candidates, currentState.charIndex)

        for (i in 0 until 6) {
            val holder = circleHolders[i]
            val bucket = buckets.getOrNull(i)

            if (bucket == null) {
                holder.root.visibility = View.INVISIBLE
                holder.root.setOnTouchListener(null)
            } else {
                holder.root.visibility = View.VISIBLE

                if (bucket.isSingleApp) {
                    // Show single app icon & label
                    val app = bucket.apps.first()
                    holder.rangeView.visibility = View.GONE
                    holder.appContainer.visibility = View.VISIBLE
                    holder.appLabel.text = app.label

                    val cachedIcon = AppRepository.getCachedIcon(app.packageName)
                    if (cachedIcon != null) {
                        holder.appIcon.setImageDrawable(cachedIcon)
                    } else {
                        holder.appIcon.setImageDrawable(AppRepository.getDefaultIcon(this))
                        holder.appIcon.tag = app.packageName
                        lifecycleScope.launch(Dispatchers.IO) {
                            val icon = AppRepository.getIcon(this@MainActivity, app.packageName)
                            withContext(Dispatchers.Main) {
                                if (holder.appIcon.tag == app.packageName) {
                                    holder.appIcon.setImageDrawable(icon)
                                }
                            }
                        }
                    }

                    // Single app touch handling: press animation, release inside to launch
                    holder.root.setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(80).start()
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start()
                                val isInside = event.x in -20f..(v.width + 20f) &&
                                        event.y in -20f..(v.height + 20f)
                                if (isInside) {
                                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    launchApp(app)
                                }
                                true
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start()
                                true
                            }
                            else -> false
                        }
                    }
                } else {
                    // Show letters & app count
                    holder.appContainer.visibility = View.GONE
                    holder.rangeView.visibility = View.VISIBLE
                    holder.rangeView.setLetterLayout(layoutMode)
                    holder.rangeView.setRange(
                        characters = bucket.chars,
                        count = bucket.countText,
                        fallback = bucket.rangeLabel
                    )

                    val nextState = State(bucket.apps, bucket.rangeLabel, charIndex = bucket.nextCharIndex)

                    // Touch handling for range circles:
                    // Press down -> immediate preview of prospective state
                    // Move -> outside cancels preview, inside re-shows
                    // Release inside -> commit drilldown; release outside -> revert
                    holder.root.setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                showPreview(nextState)
                                true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val isInside = event.x in -20f..(v.width + 20f) &&
                                        event.y in -20f..(v.height + 20f)
                                if (!isInside && isPreviewActive) {
                                    cancelPreview()
                                } else if (isInside && !isPreviewActive) {
                                    showPreview(nextState)
                                }
                                true
                            }
                            MotionEvent.ACTION_UP -> {
                                val isInside = event.x in -20f..(v.width + 20f) &&
                                        event.y in -20f..(v.height + 20f)
                                if (isInside) {
                                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                    commitPreview(nextState)
                                } else {
                                    cancelPreview()
                                }
                                true
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                cancelPreview()
                                true
                            }
                            else -> false
                        }
                    }
                }
            }
        }
    }

    private fun showPreview(previewState: State) {
        isPreviewActive = true

        // Prospective title bar update
        val prospectivePath = (history.drop(1).map { it.title } + previewState.title).joinToString(" › ")
        headerTitle.text = prospectivePath
        totalAppsCount.text = "${previewState.candidates.size} apps"
        btnBack.visibility = View.VISIBLE

        val previewBuckets = GridPartition.partition(previewState.candidates, previewState.charIndex)
        val layoutMode = if (LauncherSettings.getLetterLayout(this) == LauncherSettings.LAYOUT_LINE) {
            CircularRangeView.LetterLayout.LINE
        } else {
            CircularRangeView.LetterLayout.CIRCULAR
        }

        for (i in 0 until 6) {
            val holder = circleHolders[i]
            val bucket = previewBuckets.getOrNull(i)

            if (bucket == null) {
                holder.root.visibility = View.INVISIBLE
            } else {
                holder.root.visibility = View.VISIBLE
                if (bucket.isSingleApp) {
                    val app = bucket.apps.first()
                    holder.rangeView.visibility = View.GONE
                    holder.appContainer.visibility = View.VISIBLE
                    holder.appLabel.text = app.label

                    val cachedIcon = AppRepository.getCachedIcon(app.packageName)
                    if (cachedIcon != null) {
                        holder.appIcon.setImageDrawable(cachedIcon)
                    } else {
                        holder.appIcon.setImageDrawable(AppRepository.getDefaultIcon(this))
                        holder.appIcon.tag = app.packageName
                        lifecycleScope.launch(Dispatchers.IO) {
                            val icon = AppRepository.getIcon(this@MainActivity, app.packageName)
                            withContext(Dispatchers.Main) {
                                if (holder.appIcon.tag == app.packageName) {
                                    holder.appIcon.setImageDrawable(icon)
                                }
                            }
                        }
                    }
                } else {
                    holder.appContainer.visibility = View.GONE
                    holder.rangeView.visibility = View.VISIBLE
                    holder.rangeView.setLetterLayout(layoutMode)
                    holder.rangeView.setRange(
                        characters = bucket.chars,
                        count = bucket.countText,
                        fallback = bucket.rangeLabel
                    )
                }
            }
        }
    }

    private fun commitPreview(nextState: State) {
        isPreviewActive = false
        history.add(nextState)
        renderCurrentState()
    }

    private fun cancelPreview() {
        if (!isPreviewActive) return
        isPreviewActive = false
        renderCurrentState()
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val dialog = AlertDialog.Builder(this, R.style.Theme_Glauncher_Dialog)
            .setView(dialogView)
            .create()

        // Close button
        dialogView.findViewById<ImageButton>(R.id.btn_close_settings).setOnClickListener {
            dialog.dismiss()
        }

        // Theme options
        val currentTheme = LauncherSettings.getThemeMode(this)
        val radioSystem = dialogView.findViewById<RadioButton>(R.id.radio_theme_system)
        val radioDark = dialogView.findViewById<RadioButton>(R.id.radio_theme_dark)
        val radioLight = dialogView.findViewById<RadioButton>(R.id.radio_theme_light)

        when (currentTheme) {
            LauncherSettings.THEME_LIGHT -> radioLight.isChecked = true
            LauncherSettings.THEME_DARK -> radioDark.isChecked = true
            else -> radioSystem.isChecked = true
        }

        val themeGroup = dialogView.findViewById<RadioGroup>(R.id.theme_radio_group)
        themeGroup.setOnCheckedChangeListener { _, checkedId ->
            val newTheme = when (checkedId) {
                R.id.radio_theme_light -> LauncherSettings.THEME_LIGHT
                R.id.radio_theme_dark -> LauncherSettings.THEME_DARK
                else -> LauncherSettings.THEME_SYSTEM
            }
            if (newTheme != currentTheme) {
                LauncherSettings.setThemeMode(this, newTheme)
                dialog.dismiss()
            }
        }

        // Letter Layout options
        val currentLayout = LauncherSettings.getLetterLayout(this)
        val radioCircular = dialogView.findViewById<RadioButton>(R.id.radio_layout_circular)
        val radioLine = dialogView.findViewById<RadioButton>(R.id.radio_layout_line)

        if (currentLayout == LauncherSettings.LAYOUT_LINE) {
            radioLine.isChecked = true
        } else {
            radioCircular.isChecked = true
        }

        val layoutGroup = dialogView.findViewById<RadioGroup>(R.id.layout_radio_group)
        layoutGroup.setOnCheckedChangeListener { _, checkedId ->
            val newLayout = when (checkedId) {
                R.id.radio_layout_line -> LauncherSettings.LAYOUT_LINE
                else -> LauncherSettings.LAYOUT_CIRCULAR
            }
            LauncherSettings.setLetterLayout(this, newLayout)
            renderCurrentState()
        }

        // Spacing options
        val labelHorizontal = dialogView.findViewById<TextView>(R.id.label_spacing_horizontal)
        val seekbarHorizontal = dialogView.findViewById<SeekBar>(R.id.seekbar_spacing_horizontal)
        val labelVertical = dialogView.findViewById<TextView>(R.id.label_spacing_vertical)
        val seekbarVertical = dialogView.findViewById<SeekBar>(R.id.seekbar_spacing_vertical)

        val currentH = LauncherSettings.getMarginHorizontal(this)
        val currentV = LauncherSettings.getMarginVertical(this)

        labelHorizontal.text = "Horizontal: $currentH dp"
        seekbarHorizontal.progress = currentH

        labelVertical.text = "Vertical: $currentV dp"
        seekbarVertical.progress = currentV

        seekbarHorizontal.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                labelHorizontal.text = "Horizontal: $progress dp"
                if (fromUser) {
                    LauncherSettings.setMarginHorizontal(this@MainActivity, progress)
                    applyCircleMargins(progress, LauncherSettings.getMarginVertical(this@MainActivity))
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekbarVertical.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                labelVertical.text = "Vertical: $progress dp"
                if (fromUser) {
                    LauncherSettings.setMarginVertical(this@MainActivity, progress)
                    applyCircleMargins(LauncherSettings.getMarginHorizontal(this@MainActivity), progress)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Update check inside dialog
        val btnUpdateDialog = dialogView.findViewById<Button>(R.id.btn_check_updates_dialog)
        btnUpdateDialog.setOnClickListener {
            UpdateManager.checkForUpdates(this, force = true, manual = true)
        }

        val versionLabel = dialogView.findViewById<TextView>(R.id.app_version_label)
        versionLabel.text = "GLauncher v${BuildConfig.VERSION_NAME}"

        dialog.show()
    }

    private fun launchApp(app: AppInfo) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            shouldResetOnResume = true
            startActivity(launchIntent)
        }
    }
}
