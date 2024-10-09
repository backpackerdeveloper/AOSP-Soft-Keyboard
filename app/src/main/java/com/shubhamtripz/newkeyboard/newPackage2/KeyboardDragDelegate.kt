package com.shubhamtripz.newkeyboard.newPackage2

import android.content.Context
import android.util.DisplayMetrics
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager

class KeyboardDragDelegate(private val context: Context, private val window: Window) {
    private var initialY: Int = 0
    private var initialX: Int = 0
    private var initialTouchX: Float = 0F
    private var initialTouchY: Float = 0F
    private var lastDraggedY: Int = 0 // Store the last dragged Y position

    init {
        // Set the initial position to the bottom of the screen when the keyboard is opened
        lastDraggedY = context.displayMetrics.heightPixels // Default position (at the bottom)
    }

    private val centerYBound by lazy {
        val screenHeight = context.displayMetrics.heightPixels
        val windowHeight = window.attributes.height
        IntRange(0 + windowHeight + getStatusBarHeight(), screenHeight - windowHeight - getNavBarHeight())
    }

    fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d("MotionEvent", "ACTION_DOWN")
                initialY = lastDraggedY // Use the last dragged position
                initialTouchY = event.rawY
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val touchYMovement = initialTouchY - event.rawY
                val newCenterY: Int = initialY + touchYMovement.toInt() // Update only Y position

                // Ensure the new Y position is within bounds
                if (isWithinYBound(newCenterY)) {
                    val params = window.attributes
                    params.y = newCenterY
                    updateViewLayout(params)
                    Log.d("MotionEvent", "Dragging to position: $newCenterY")
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP -> {
                // Update last dragged position when drag ends
                lastDraggedY = window.attributes.y
            }
        }
        return false
    }

    private fun updateViewLayout(params: WindowManager.LayoutParams) {
        window.attributes = params
    }

    private fun isWithinYBound(newY: Int): Boolean {
        return newY in centerYBound
    }

    private fun getNavBarHeight(): Int {
        val resources = context.resources
        val resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else 0
    }

    private fun getStatusBarHeight(): Int {
        var result = 0
        val resources = context.resources
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        return result
    }


    val Context.displayMetrics: DisplayMetrics
        get() {
            val metrics = DisplayMetrics()
            val display = windowManager.defaultDisplay
            display.getMetrics(metrics)
            return metrics
        }
    val Context.windowManager: WindowManager
        get() = getSystemService(Context.WINDOW_SERVICE) as WindowManager
}
