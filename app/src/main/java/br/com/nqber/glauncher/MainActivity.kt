package br.com.nqber.glauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.hardware.SensorManager
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot

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

    // Spacers for direct horizontal and vertical distance control
    private lateinit var spacerH0: View
    private lateinit var spacerH1: View
    private lateinit var spacerH2: View
    private lateinit var spacerV0: View
    private lateinit var spacerV1: View

    // Orientation handling: keep UI layout fixed, rotate contents inside circles
    private var currentRotationAngle = 0f
    private lateinit var orientationListener: OrientationEventListener

    // Drag navigation state
    private var isGestureActive = false
    private var lastDrilledIndex = -1
    private var startRawX = 0f
    private var startRawY = 0f
    private var hasMovedSignificantDistance = false

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

        applyCircleSpacing(
            LauncherSettings.getSpacingHorizontal(this),
            LauncherSettings.getSpacingVertical(this)
        )

        // Initial load
        resetToRoot()

        // Background update check
        UpdateManager.checkForUpdates(this)
    }

    override fun onResume() {
        super.onResume()
        orientationListener.enable()

        // Apply updated settings that may have changed in SettingsActivity
        applyCircleSpacing(
            LauncherSettings.getSpacingHorizontal(this),
            LauncherSettings.getSpacingVertical(this)
        )

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

        spacerH0 = findViewById(R.id.spacer_h_0)
        spacerH1 = findViewById(R.id.spacer_h_1)
        spacerH2 = findViewById(R.id.spacer_h_2)
        spacerV0 = findViewById(R.id.spacer_v_0)
        spacerV1 = findViewById(R.id.spacer_v_1)

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

        for (i in 0 until 6) {
            val holder = circleHolders[i]
            val bucket = buckets.getOrNull(i)

            // Maintain rotation
            holder.rangeView.rotation = currentRotationAngle
            holder.appContainer.rotation = currentRotationAngle
            holder.root.scaleX = 1f
            holder.root.scaleY = 1f

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

    private fun findHolderUnderPoint(rawX: Float, rawY: Float): Int? {
        val rect = Rect()
        for (i in 0 until 6) {
            val holder = circleHolders[i]
            if (holder.root.visibility == View.VISIBLE) {
                holder.root.getGlobalVisibleRect(rect)
                if (rect.contains(rawX.toInt(), rawY.toInt())) {
                    return i
                }
            }
        }
        return null
    }

    private fun setupCircleTouchListener(holder: CircleViewHolder, index: Int) {
        val touchSlop = 20 * resources.displayMetrics.density

        holder.root.setOnTouchListener { v, event ->
            val isDragNav = LauncherSettings.isDragNavigationEnabled(this)

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isGestureActive = true
                    lastDrilledIndex = index
                    startRawX = event.rawX
                    startRawY = event.rawY
                    hasMovedSignificantDistance = false

                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                    val bucket = currentVisibleBuckets.getOrNull(index)
                    if (bucket != null) {
                        if (bucket.isSingleApp) {
                            v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).start()
                        } else {
                            val nextState = State(bucket.apps, bucket.rangeLabel, charIndex = bucket.nextCharIndex)
                            showPreview(nextState)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dist = hypot((event.rawX - startRawX).toDouble(), (event.rawY - startRawY).toDouble()).toFloat()
                    if (dist > touchSlop) {
                        hasMovedSignificantDistance = true
                    }

                    if (isDragNav && hasMovedSignificantDistance) {
                        // Check if dragging over btnBack
                        val backRect = Rect()
                        btnBack.getGlobalVisibleRect(backRect)
                        if (btnBack.visibility == View.VISIBLE && backRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                            if (history.size > 1) {
                                btnBack.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                goBack()
                                lastDrilledIndex = -1
                            }
                        }

                        // Check if entering a circle on screen
                        val hoveredIndex = findHolderUnderPoint(event.rawX, event.rawY)
                        if (hoveredIndex != null && hoveredIndex != lastDrilledIndex) {
                            val targetBucket = currentVisibleBuckets.getOrNull(hoveredIndex)
                            if (targetBucket != null) {
                                lastDrilledIndex = hoveredIndex
                                circleHolders[hoveredIndex].root.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                                if (targetBucket.isSingleApp) {
                                    // Highlight single app
                                    for (j in 0 until 6) {
                                        circleHolders[j].root.animate().scaleX(if (j == hoveredIndex) 0.92f else 1f)
                                            .scaleY(if (j == hoveredIndex) 0.92f else 1f).setDuration(80).start()
                                    }
                                } else {
                                    // Drill down to that bucket
                                    val nextState = State(targetBucket.apps, targetBucket.rangeLabel, charIndex = targetBucket.nextCharIndex)
                                    showPreview(nextState)
                                }
                            }
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    for (h in circleHolders) {
                        h.root.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    }

                    val releaseIndex = findHolderUnderPoint(event.rawX, event.rawY)

                    if (isDragNav) {
                        if (releaseIndex != null) {
                            val bucket = currentVisibleBuckets.getOrNull(releaseIndex)
                            if (bucket != null) {
                                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (bucket.isSingleApp) {
                                    launchApp(bucket.apps.first())
                                } else {
                                    val nextState = State(bucket.apps, bucket.rangeLabel, charIndex = bucket.nextCharIndex)
                                    commitPreview(nextState)
                                }
                            } else {
                                if (isPreviewActive) commitCurrentPreview()
                            }
                        } else {
                            // Released outside: keep visualization active and commit current state
                            if (isPreviewActive) commitCurrentPreview()
                        }
                    } else {
                        // Standard mode without drag nav
                        if (releaseIndex == index || !hasMovedSignificantDistance) {
                            val bucket = currentVisibleBuckets.getOrNull(index)
                            if (bucket != null) {
                                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                if (bucket.isSingleApp) {
                                    launchApp(bucket.apps.first())
                                } else {
                                    val nextState = State(bucket.apps, bucket.rangeLabel, charIndex = bucket.nextCharIndex)
                                    commitPreview(nextState)
                                }
                            }
                        } else {
                            cancelPreview()
                        }
                    }

                    isGestureActive = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    for (h in circleHolders) {
                        h.root.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                    }
                    cancelPreview()
                    isGestureActive = false
                    true
                }

                else -> false
            }
        }
    }

    private var pendingPreviewState: State? = null

    private fun showPreview(previewState: State) {
        isPreviewActive = true
        pendingPreviewState = previewState

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

        for (i in 0 until 6) {
            val holder = circleHolders[i]
            val bucket = previewBuckets.getOrNull(i)

            holder.rangeView.rotation = currentRotationAngle
            holder.appContainer.rotation = currentRotationAngle

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
        pendingPreviewState = null
        history.add(nextState)
        renderCurrentState()
    }

    private fun commitCurrentPreview() {
        val state = pendingPreviewState
        if (state != null) {
            commitPreview(state)
        } else {
            renderCurrentState()
        }
    }

    private fun cancelPreview() {
        if (!isPreviewActive) return
        isPreviewActive = false
        pendingPreviewState = null
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
