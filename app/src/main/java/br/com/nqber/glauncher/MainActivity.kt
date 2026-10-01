package br.com.nqber.glauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
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
    private var currentVisibleBuckets = listOf<GridBucket?>()
    private var shouldResetOnResume = false
    private var isPreviewActive = false

    private lateinit var btnBack: ImageButton
    private lateinit var headerTitle: TextView
    private lateinit var totalAppsCount: TextView
    private lateinit var btnSettings: ImageButton
    private lateinit var btnUpdate: ImageButton

    // Grid and Row containers
    private lateinit var gridContainer: LinearLayout
    private lateinit var row0: LinearLayout
    private lateinit var row1: LinearLayout
    private lateinit var row2: LinearLayout

    // Spacers for direct horizontal and vertical distance control
    private lateinit var spacerH0: View
    private lateinit var spacerH1: View
    private lateinit var spacerH2: View
    private lateinit var spacerV0: View
    private lateinit var spacerV1: View

    // Orientation handling: keep UI layout fixed, rotate contents inside circles
    private var currentRotationAngle = 0f
    private lateinit var orientationListener: OrientationEventListener

    // Touch gesture tracking
    private var touchedBucketAtDown: GridBucket? = null
    private var touchedIndexAtDown: Int = -1

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppRepository.invalidate(this@MainActivity)
            resetToRoot(forceReload = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val currentTheme = LauncherSettings.getThemeMode(this)
        LauncherSettings.applyTheme(currentTheme)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setupSystemBars()
        bindViews()
        setupNavigation()
        setupOrientationListener()
        registerPackageReceiver()

        // Initial load
        resetToRoot()

        // Background update check
        UpdateManager.checkForUpdates(this)
    }

    override fun onResume() {
        super.onResume()
        orientationListener.enable()

        // Apply updated settings from SettingsActivity
        applyCircleSpacing(
            LauncherSettings.getSpacingHorizontal(this),
            LauncherSettings.getSpacingVertical(this)
        )
        applyCircleScale(LauncherSettings.getCircleScale(this))

        if (shouldResetOnResume) {
            shouldResetOnResume = false
            resetToRoot()
        } else {
            renderCurrentState()
        }
    }

    override fun onPause() {
        super.onPause()
        orientationListener.disable()
        if (isPreviewActive) {
            cancelPreview()
        }
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
        val isNight = when (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
            android.content.res.Configuration.UI_MODE_NIGHT_YES -> true
            else -> false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }
    }

    private fun setupOrientationListener() {
        orientationListener = object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_UI) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return

                val targetAngle = when (orientation) {
                    in 45..134 -> 270f
                    in 135..224 -> 180f
                    in 225..314 -> 90f
                    else -> 0f
                }

                if (targetAngle != currentRotationAngle) {
                    currentRotationAngle = targetAngle
                    applyContentRotation(targetAngle)
                }
            }
        }
    }

    private fun applyContentRotation(angle: Float) {
        for (holder in circleHolders) {
            holder.rangeView.animate().rotation(angle).setDuration(250).start()
            holder.appContainer.animate().rotation(angle).setDuration(250).start()
        }
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btn_back)
        headerTitle = findViewById(R.id.header_title)
        totalAppsCount = findViewById(R.id.total_apps_count)
        btnSettings = findViewById(R.id.btn_settings)
        btnUpdate = findViewById(R.id.btn_update)

        gridContainer = findViewById(R.id.grid_container)
        row0 = findViewById(R.id.row_0)
        row1 = findViewById(R.id.row_1)
        row2 = findViewById(R.id.row_2)

        spacerH0 = findViewById(R.id.spacer_h_0)
        spacerH1 = findViewById(R.id.spacer_h_1)
        spacerH2 = findViewById(R.id.spacer_h_2)
        spacerV0 = findViewById(R.id.spacer_v_0)
        spacerV1 = findViewById(R.id.spacer_v_1)

        gridContainer.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if ((right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)) {
                applyCircleSpacing(
                    LauncherSettings.getSpacingHorizontal(this),
                    LauncherSettings.getSpacingVertical(this)
                )
            }
        }

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
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
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

    private fun applyCircleSpacing(spacingHDp: Int, spacingVDp: Int) {
        val hPx = (spacingHDp * resources.displayMetrics.density).toInt()
        val vPx = (spacingVDp * resources.displayMetrics.density).toInt()

        val hSpacers = listOf(spacerH0, spacerH1, spacerH2)
        for (spacer in hSpacers) {
            val lp = spacer.layoutParams
            lp.width = hPx
            spacer.layoutParams = lp
        }

        val vSpacers = listOf(spacerV0, spacerV1)
        for (spacer in vSpacers) {
            val lp = spacer.layoutParams
            lp.height = vPx
            spacer.layoutParams = lp
        }

        val gridWidth = gridContainer.width
        val gridHeight = gridContainer.height
        if (gridWidth > 0 && gridHeight > 0) {
            val paddingH = gridContainer.paddingLeft + gridContainer.paddingRight
            val availableWidth = gridWidth - paddingH - hPx
            val cellWidth = availableWidth / 2

            val paddingV = gridContainer.paddingTop + gridContainer.paddingBottom
            val availableHeight = gridHeight - paddingV - (2 * vPx)
            val maxCellHeight = availableHeight / 3

            val finalCellSize = minOf(cellWidth, maxCellHeight)

            if (finalCellSize > 0) {
                val rows = listOf(row0, row1, row2)
                for (row in rows) {
                    val lp = row.layoutParams as? LinearLayout.LayoutParams
                    if (lp != null) {
                        lp.height = finalCellSize
                        lp.weight = 0f
                        row.layoutParams = lp
                    }
                }
            }
        }
    }

    private fun applyCircleScale(scalePercent: Int) {
        val scale = scalePercent / 100f
        for (holder in circleHolders) {
            holder.root.scaleX = scale
            holder.root.scaleY = scale
        }
    }

    private fun renderCurrentState() {
        val currentState = history.lastOrNull() ?: return
        val candidates = currentState.candidates
        isPreviewActive = false

        if (history.size > 1) {
            btnBack.visibility = View.VISIBLE
            headerTitle.text = history.drop(1).joinToString(" › ") { it.title }
        } else {
            btnBack.visibility = View.INVISIBLE
            headerTitle.text = getString(R.string.app_name)
        }
        totalAppsCount.text = "${candidates.size} apps"

        val layoutMode = if (LauncherSettings.getLetterLayout(this) == LauncherSettings.LAYOUT_LINE) {
            CircularRangeView.LetterLayout.LINE
        } else {
            CircularRangeView.LetterLayout.CIRCULAR
        }

        val buckets = GridPartition.partition(candidates, currentState.charIndex)
        currentVisibleBuckets = buckets

        val baseScale = LauncherSettings.getCircleScale(this) / 100f

        for (i in 0 until 6) {
            val holder = circleHolders[i]
            val bucket = buckets.getOrNull(i)

            holder.rangeView.rotation = currentRotationAngle
            holder.appContainer.rotation = currentRotationAngle
            holder.root.scaleX = baseScale
            holder.root.scaleY = baseScale

            if (bucket == null) {
                holder.root.visibility = View.INVISIBLE
                holder.root.setOnTouchListener(null)
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

                setupCircleTouchListener(holder, i)
            }
        }
    }

    private fun setupCircleTouchListener(holder: CircleViewHolder, index: Int) {
        holder.root.setOnTouchListener { v, event ->
            val baseScale = LauncherSettings.getCircleScale(this) / 100f
            val pressScale = baseScale * 0.92f

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchedIndexAtDown = index
                    touchedBucketAtDown = currentVisibleBuckets.getOrNull(index)

                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    v.animate().scaleX(pressScale).scaleY(pressScale).setDuration(80).start()

                    val bucket = touchedBucketAtDown
                    if (bucket != null && !bucket.isSingleApp) {
                        val nextState = State(bucket.apps, bucket.rangeLabel, charIndex = bucket.nextCharIndex)
                        showPreview(nextState)
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Preview remains active without flickering while holding finger
                    true
                }

                MotionEvent.ACTION_UP -> {
                    for (h in circleHolders) {
                        h.root.animate().scaleX(baseScale).scaleY(baseScale).setDuration(80).start()
                    }

                    // Generous hit tolerance around the touched circle:
                    val tolerance = 40f * resources.displayMetrics.density
                    val isInside = event.x in -tolerance..(v.width + tolerance) &&
                                   event.y in -tolerance..(v.height + tolerance)

                    val downBucket = touchedBucketAtDown

                    if (isInside && downBucket != null) {
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        if (downBucket.isSingleApp) {
                            // Tapped on a single app -> launch it
                            launchApp(downBucket.apps.first())
                        } else {
                            // Tapped on a range bucket -> commit drilldown to show its children
                            val nextState = State(downBucket.apps, downBucket.rangeLabel, charIndex = downBucket.nextCharIndex)
                            commitPreview(nextState)
                        }
                    } else {
                        // User slid far away to cancel
                        cancelPreview()
                    }

                    touchedBucketAtDown = null
                    touchedIndexAtDown = -1
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    for (h in circleHolders) {
                        h.root.animate().scaleX(baseScale).scaleY(baseScale).setDuration(80).start()
                    }
                    cancelPreview()
                    touchedBucketAtDown = null
                    touchedIndexAtDown = -1
                    true
                }

                else -> false
            }
        }
    }

    private fun showPreview(previewState: State) {
        isPreviewActive = true

        val prospectivePath = (history.drop(1).map { it.title } + previewState.title).joinToString(" › ")
        headerTitle.text = prospectivePath
        totalAppsCount.text = "${previewState.candidates.size} apps"
        btnBack.visibility = View.VISIBLE

        val previewBuckets = GridPartition.partition(previewState.candidates, previewState.charIndex)
        currentVisibleBuckets = previewBuckets

        val layoutMode = if (LauncherSettings.getLetterLayout(this) == LauncherSettings.LAYOUT_LINE) {
            CircularRangeView.LetterLayout.LINE
        } else {
            CircularRangeView.LetterLayout.CIRCULAR
        }

        val baseScale = LauncherSettings.getCircleScale(this) / 100f

        for (i in 0 until 6) {
            val holder = circleHolders[i]
            val bucket = previewBuckets.getOrNull(i)

            holder.rangeView.rotation = currentRotationAngle
            holder.appContainer.rotation = currentRotationAngle
            holder.root.scaleX = baseScale
            holder.root.scaleY = baseScale

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

    private fun launchApp(app: AppInfo) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            shouldResetOnResume = true
            startActivity(launchIntent)
        }
    }
}
