package br.com.nqber.glauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
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
        val rangeContainer: View,
        val rangeLabel: TextView,
        val rangeCount: TextView,
        val appContainer: View,
        val appIcon: ImageView,
        val appLabel: TextView
    )

    private val history = mutableListOf<State>()
    private val circleHolders = mutableListOf<CircleViewHolder>()
    private var shouldResetOnResume = false

    private lateinit var btnBack: ImageButton
    private lateinit var headerTitle: TextView
    private lateinit var totalAppsCount: TextView

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppRepository.invalidate(this@MainActivity)
            resetToRoot(forceReload = true)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupNavigation()
        registerPackageReceiver()

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        resetToRoot()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(packageReceiver)
    }

    private fun bindViews() {
        btnBack = findViewById(R.id.btn_back)
        headerTitle = findViewById(R.id.header_title)
        totalAppsCount = findViewById(R.id.total_apps_count)

        btnBack.setOnClickListener {
            goBack()
        }

        headerTitle.setOnClickListener {
            resetToRoot()
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
                    rangeContainer = cellView.findViewById(R.id.range_container),
                    rangeLabel = cellView.findViewById(R.id.range_label),
                    rangeCount = cellView.findViewById(R.id.range_count),
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
        if (history.size > 1) {
            history.removeAt(history.lastIndex)
            renderCurrentState()
        }
    }

    private fun renderCurrentState() {
        val currentState = history.lastOrNull() ?: return
        val candidates = currentState.candidates

        // Header update
        if (history.size > 1) {
            btnBack.visibility = View.VISIBLE
            headerTitle.text = history.drop(1).joinToString(" › ") { it.title }
        } else {
            btnBack.visibility = View.INVISIBLE
            headerTitle.text = getString(R.string.app_name)
        }
        totalAppsCount.text = "${candidates.size} apps"

        // Partition into 6 buckets using prefix funneling at charIndex
        val buckets = GridPartition.partition(candidates, currentState.charIndex)

        for (i in 0 until 6) {
            val holder = circleHolders[i]
            val bucket = buckets.getOrNull(i)

            if (bucket == null) {
                holder.root.visibility = View.INVISIBLE
                holder.root.setOnClickListener(null)
                holder.root.setOnLongClickListener(null)
            } else {
                holder.root.visibility = View.VISIBLE

                if (bucket.isSingleApp) {
                    // Show single app icon & label (lazy load icon on demand without blocking UI thread)
                    val app = bucket.apps.first()
                    holder.rangeContainer.visibility = View.GONE
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

                    holder.root.setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        launchApp(app)
                    }
                } else {
                    // Show range label & app count
                    holder.appContainer.visibility = View.GONE
                    holder.rangeContainer.visibility = View.VISIBLE
                    holder.rangeLabel.text = bucket.rangeLabel
                    holder.rangeCount.text = bucket.countText

                    holder.root.setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        // Drill down: advance to next character index
                        history.add(State(bucket.apps, bucket.rangeLabel, charIndex = bucket.nextCharIndex))
                        renderCurrentState()
                    }
                }

                // Long press on any circle resets back to the top
                holder.root.setOnLongClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    resetToRoot()
                    true
                }
            }
        }
    }

    private fun launchApp(app: AppInfo) {
        val launchIntent = packageManager.getLaunchIntentForPackage(app.packageName)
        if (launchIntent != null) {
            shouldResetOnResume = true
            startActivity(launchIntent)
        }
    }
}

