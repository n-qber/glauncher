package com.example.glauncher

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

class MainActivity : AppCompatActivity() {

    private data class State(
        val candidates: List<AppInfo>,
        val title: String
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

    private lateinit var btnBack: ImageButton
    private lateinit var headerTitle: TextView
    private lateinit var totalAppsCount: TextView

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            AppRepository.invalidate()
            resetToRoot()
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

    private fun resetToRoot() {
        val allApps = AppRepository.getApps(this)
        history.clear()
        history.add(State(allApps, "GLauncher"))
        renderCurrentState()
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

        // Partition into 6 buckets
        val buckets = GridPartition.partition(candidates)

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
                    // Show single app icon & label
                    val app = bucket.apps.first()
                    holder.rangeContainer.visibility = View.GONE
                    holder.appContainer.visibility = View.VISIBLE
                    holder.appIcon.setImageDrawable(app.icon)
                    holder.appLabel.text = app.label

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
                        // Drill down to this bucket's candidates
                        history.add(State(bucket.apps, bucket.rangeLabel))
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
            startActivity(launchIntent)
            // Reset to root state for the next time the launcher is opened
            resetToRoot()
        }
    }
}
