package com.oscarcreator.weekview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.OverScroller
import androidx.core.graphics.withClip
import androidx.core.graphics.withTranslation
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class WeekView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr),
    ScaleGestureDetector.OnScaleGestureListener,
    GestureDetector.OnGestureListener {

    companion object {
        const val TAG = "WeekView"
    }


    private var scale = 2f

    private val minScale = 1.2f
    private val maxScale = 6f

    private val leftBarWidth: Int = 200

    private var contentWidth: Int = 0

    private var hourHeight = 100
    private val hours = 24

    private val topBarHeight: Int = 180

    // TODO update
    private var week = 0

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 55f
        textAlign = Paint.Align.CENTER
    }

    private val weekCharacterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 34f
        textAlign = Paint.Align.CENTER
    }

    private val topBarBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
    }

    private val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        textSize = 36f
    }

    private val currentTimeBarPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 3f
    }

    private val currentDayPaint = Paint().apply {
        color = Color.BLUE
    }

    private val verticalScroller = OverScroller(context)
    private val horizontalScroller = OverScroller(context, FastOutLinearInInterpolator())

    private val scaleDetector = ScaleGestureDetector(context, this)
    private val gestureDetector = GestureDetector(context, this)

    private enum class Direction {
        NONE, HORIZONTAL, VERTICAL
    }

    private var currentScrollDirection = Direction.NONE
    private var currentFlingDirection = Direction.NONE

    private var currentWeek: Int
    private var cacheWeekNumbers: Array<Int>
    private var cacheWeekCharacters: Array<String>
    private var currentYear: Int

    private var currentTimeInMinutes: Int
    private val minutesInDay = 1440

    private var currentDayInWeek: Int
    private val currentDayInMonth: Int

    private val simpleDateFormat = SimpleDateFormat("E", Locale.getDefault())

    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            onTimeChanged()
        }
    }

    init {

        setBackgroundColor(Color.DKGRAY)

        val currentDate = Calendar.getInstance()
        var dayInWeekNumber = currentDate.get(Calendar.DAY_OF_WEEK) - 1
        if (dayInWeekNumber == 0) dayInWeekNumber = 7
        currentDayInWeek = dayInWeekNumber

        currentDayInMonth = currentDate.get(Calendar.DAY_OF_MONTH)

        currentWeek = currentDate.get(Calendar.WEEK_OF_YEAR)
        currentYear = currentDate.get(Calendar.YEAR)
        currentDate.set(Calendar.DAY_OF_WEEK, currentDate.firstDayOfWeek)
        currentDate.add(Calendar.DAY_OF_MONTH, -8)
        val checkPointTime = currentDate.timeInMillis
        cacheWeekNumbers = 1.rangeTo(23).map {
            currentDate.get(Calendar.DAY_OF_MONTH).also {
                currentDate.add(Calendar.DAY_OF_MONTH, 1)
            }
        }.toTypedArray()

        currentDate.timeInMillis = checkPointTime
        cacheWeekCharacters = 1.rangeTo(23).map {
            simpleDateFormat.format(currentDate.time)[0].toString().also {
                currentDate.add(Calendar.DAY_OF_MONTH, 1)
            }
        }.toTypedArray()

        currentTimeInMinutes = getCurrentTimeInMinutes(currentDate)
    }

    private fun getContentHeight(): Int = (hours * hourHeight * scale).toInt()

    private fun recalculateWeek() {
        val right = week * contentWidth + contentWidth / 2f
        val left = week * contentWidth - contentWidth / 2f

        if (scrollX > right) {
            // right
            week++
            Log.d(TAG, "week++ -> $week")

            updateWeekNumberCache()

        } else if(scrollX < left) {
            // left
            week--
            Log.d(TAG, "week-- -> $week")

            updateWeekNumberCache()
            updateWeekCharacterCache()
        }
    }

    private fun updateWeekNumberCache(currentDate: Calendar = Calendar.getInstance()) {
        currentDate.set(Calendar.DAY_OF_WEEK, currentDate.firstDayOfWeek)
        currentDate.add(Calendar.WEEK_OF_YEAR, week)
        currentWeek = currentDate.get(Calendar.WEEK_OF_YEAR)
        currentDate.add(Calendar.DAY_OF_MONTH, -8)
        cacheWeekNumbers = 1.rangeTo(23).map {
            currentDate.get(Calendar.DAY_OF_MONTH).also {
                currentDate.add(Calendar.DAY_OF_MONTH, 1)
            }
        }.toTypedArray()
    }

    private fun updateWeekCharacterCache(currentDate: Calendar = Calendar.getInstance()) {
        currentDate.set(Calendar.DAY_OF_WEEK, currentDate.firstDayOfWeek)
        currentDate.add(Calendar.WEEK_OF_YEAR, week)
        currentDate.add(Calendar.DAY_OF_MONTH, -8)
        cacheWeekCharacters = 1.rangeTo(23).map {
            simpleDateFormat.format(currentDate.time)[0].toString().also {
                currentDate.add(Calendar.DAY_OF_MONTH, 1)
            }
        }.toTypedArray()
    }

    private fun getCurrentTimeInMinutes(currentDate: Calendar = Calendar.getInstance()): Int {
        return currentDate.get(Calendar.HOUR_OF_DAY) * 60 + currentDate.get(Calendar.MINUTE)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        canvas.apply {

            // horizontal lines and hour text
            withTranslation(scrollX.toFloat(), topBarHeight.toFloat()) {
                for (i in 1 until hours) {
                    val y = i * hourHeight * scale
                    drawLine(leftBarWidth.toFloat() - 20, y, width.toFloat(), y, linePaint)
                    if (i < 10) {
                        drawText("0$i:00", 50f, y, timePaint)
                    } else {
                        drawText("$i:00", 50f, y, timePaint)
                    }
                }
            }

            // top bar
            withTranslation(scrollX.toFloat(), scrollY.toFloat()) {
                drawRect(0f, 0f, width.toFloat(), topBarHeight.toFloat(), topBarBackground)
                // week number
                drawText(currentWeek.toString(), (leftBarWidth / 2).toFloat(), 130f, textPaint)
            }

            // vertical lines
            drawVerticalLines()

            drawDateNumbers()


            // stationary vertical line by left bar
            withTranslation((scrollX + leftBarWidth).toFloat(), scrollY + topBarHeight - 20f) {
                drawLine(0f, 0f, 0f, height - topBarHeight + 20f, linePaint)
            }

            // time bar
            drawCurrentTimeBar()


        }

    }

    /**
     * Draws the vertical lines which separate the days of the week.
     * */
    private fun Canvas.drawVerticalLines() {
        withTranslation(leftBarWidth.toFloat(), scrollY + topBarHeight - 30f) {
            withClip(scrollX.toFloat(), 0f, contentWidth + scrollX.toFloat(), height.toFloat()) {
                for (i in 0..7) {
                    val o = floor(scrollX.toFloat() / (contentWidth / 7f))
                    val x = contentWidth / 7f * (i + o)

                    drawLine(x, 0f, x, height - topBarHeight + 30f, linePaint)
                }
            }
        }
    }

    /**
     * Draws the date numbers with a character corresponding to the day of the week.
     * */
    private fun Canvas.drawDateNumbers() {
        withTranslation(leftBarWidth.toFloat(), scrollY.toFloat()) {
            withClip(scrollX.toFloat(), 0f, contentWidth + scrollX.toFloat(), topBarHeight.toFloat()) {
                for (i in 0..8) {
                    val o = floor(scrollX.toFloat() / (contentWidth / 7f)).toInt()
                    val index = i + o - week * 7 + 7

                    val x = contentWidth / 7f * (i + o)

                    // if close to starting week and at correct day in month, draw circle
                    if (week in -1..1 && cacheWeekNumbers[index] == currentDayInMonth) {
                        drawCircle(x - (contentWidth / 7f) / 2f, 110f, 50f, currentDayPaint)
                    }
                    drawText(cacheWeekNumbers[index].toString(), x - (contentWidth / 7f) / 2f, 130f, textPaint)
                    drawText(cacheWeekCharacters[index], x - (contentWidth / 7f) / 2f, 50f, weekCharacterPaint)
                }
            }
        }
    }

    private fun Canvas.drawCurrentTimeBar() {
        withTranslation(leftBarWidth.toFloat(), topBarHeight.toFloat()) {
            val maxHeight = hourHeight * scale * hours
            val timeBarHeightFraction = currentTimeInMinutes.toFloat() / minutesInDay

            val displayHeight = maxHeight * timeBarHeightFraction

            val barWidth = contentWidth / 7f
            val startX = contentWidth / 7f * (currentDayInWeek - 1)
            withClip(scrollX.toFloat(), scrollY.toFloat(), scrollX + contentWidth.toFloat(), hourHeight * scale * hours) {

                drawLine(startX, displayHeight, startX + barWidth, displayHeight, currentTimeBarPaint)
            }

            withClip(scrollX.toFloat() - 15f, scrollY.toFloat(), scrollX + contentWidth.toFloat(), hourHeight * scale * hours) {
                val barFraction = (scrollX - startX) / barWidth
                if (barFraction > 0) {
                    if (barFraction in 0.0..1.0) {
                        // radius can be from 0.2 * 15 = 3f = strokeWidth to 1 * 15f = full radius
                        drawCircle(startX + scrollX, displayHeight, 15f * (1 - ((barFraction + 0.2f) / 1.2f)), currentTimeBarPaint)
                    }
                    // if barFraction is over 1 then it's outside of the window

                } else if (-scrollX < contentWidth) {
                    // draw full circle if not small circle is drawn and is on first page
                    drawCircle(startX, displayHeight, 15f, currentTimeBarPaint)
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        val filter = IntentFilter(Intent.ACTION_TIME_TICK)
        context.registerReceiver(broadcastReceiver, filter)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(broadcastReceiver)
    }

    private fun onTimeChanged() {
        currentTimeInMinutes = getCurrentTimeInMinutes()

        val currentDate = Calendar.getInstance()
        var dayInWeekNumber = currentDate.get(Calendar.DAY_OF_WEEK) - 1
        if (dayInWeekNumber == 0) dayInWeekNumber = 7
        currentDayInWeek = dayInWeekNumber

        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        if (event?.action == MotionEvent.ACTION_UP) {
            onTouchUp(event)
        }

        return true
    }

    override fun computeScroll() {
        super.computeScroll()

        if (verticalScroller.computeScrollOffset()) {

            Log.d(TAG, "Vertical Scroller: $scrollY -> y:${verticalScroller.currY}")
            // scroll end is limited with function fling
            scrollTo(scrollX, verticalScroller.currY)
            invalidate()

            if (verticalScroller.isFinished) {

                currentFlingDirection = Direction.NONE

                val right = week * contentWidth + contentWidth / 2f
                val left = week * contentWidth - contentWidth / 2f

                val scrollDistance = if (scrollX > right) {
                    // right
                    (week + 1) * contentWidth - scrollX
                } else if(scrollX < left) {
                    // left
                    (week - 1) * contentWidth - scrollX
                } else {
                    // back
                    week * contentWidth - scrollX
                }


                Log.d(TAG, "V Scroller: 'Bounce Back' sx:$scrollX d:$scrollDistance")

                horizontalScroller.startScroll(
                    scrollX, 0,
                    scrollDistance, 0
                )
            }
        }

        // both scrollers will never run together with touch
        if (horizontalScroller.computeScrollOffset()) {
            Log.d(TAG, "Horizontal Scroller: $scrollX -> y:${horizontalScroller.currX}")
            scrollTo(horizontalScroller.currX, scrollY)
            invalidate()

            if (horizontalScroller.isFinished) {

                if (scrollX % contentWidth != 0) {
                    // needs 'bounce back' from small fling

                    val target = week * contentWidth
                    val distanceLeft = scrollX - target


                    Log.d(TAG, "H Scroller: 'Bounce Back' sx:$scrollX d:$distanceLeft")
                    horizontalScroller.startScroll(
                        scrollX, 0,
                        -distanceLeft, 0
                    )

                } else {
                    // done
                    currentFlingDirection = Direction.NONE
                    Log.d(TAG, "Horizontal Scroller: DONE! week:$week")
                }
            }
        }

        // if scrolling or mid flinging then recalculate week
        recalculateWeek()

    }

    override fun scrollTo(x: Int, y: Int) {
        super.scrollTo(x, min(max(y, 0), getContentHeight() - height + topBarHeight))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        contentWidth = w - leftBarWidth
    }

    // DONE
    override fun onScale(detector: ScaleGestureDetector): Boolean {
        // TODO hour height should be same on both orientations
        // allow scaling between max and min heights
        scale = min(max(scale * detector.scaleFactor, minScale), maxScale)

        if (scale > minScale && scale < maxScale) {
            // center zooming around focus point
            scrollBy(0, ((detector.scaleFactor - 1) * (detector.focusY + scrollY)).toInt())
        }

        invalidate()
        return true
    }

    // DONE
    override fun onScaleBegin(detector: ScaleGestureDetector?): Boolean {
        return true
    }

    override fun onDown(e: MotionEvent?): Boolean {
        // Stop all current motion
        verticalScroller.forceFinished(true)
        horizontalScroller.forceFinished(true)

        currentFlingDirection = Direction.NONE
        return true
    }

    override fun onScroll(
        e1: MotionEvent?,
        e2: MotionEvent?,
        distanceX: Float,
        distanceY: Float
    ): Boolean {

        val absDx = abs(distanceX)
        val absDy = abs(distanceY)

        if (currentScrollDirection == Direction.NONE) {
            if (absDx > absDy) {
                currentScrollDirection = Direction.HORIZONTAL
            } else {
                currentScrollDirection = Direction.VERTICAL

            }
        }

        if (currentScrollDirection == Direction.VERTICAL) {
            Log.d(TAG, "Vertical: $scrollY + dx:$distanceY")
            scrollBy(0, distanceY.toInt())
        } else if(currentScrollDirection == Direction.HORIZONTAL) {
            Log.d(TAG, "Horizontal: $scrollX + dx:$distanceX")
            scrollBy(distanceX.toInt(), 0)
        }
        invalidate()
        return true
    }

    override fun onFling(
        e1: MotionEvent?,
        e2: MotionEvent?,
        velocityX: Float,
        velocityY: Float
    ): Boolean {

        val absVx = abs(velocityX)
        val absVy = abs(velocityY)

        if (absVx > absVy) {
            currentFlingDirection = Direction.HORIZONTAL

            val target = week * contentWidth
            val distanceFrom = scrollX - target

            // if scrolling to far into another week then you will not be able to
            // scroll back past your current week
            var visibleWeek = week
            if (distanceFrom > 0 && velocityX > 0 || distanceFrom < 0 && velocityX < 0) {
                if (abs(distanceFrom) > contentWidth * 0.2f) {
                    if (distanceFrom > 0) {
                        visibleWeek += 1
                    } else {
                        visibleWeek -= 1
                    }
                }
            }

            Log.d(TAG, "Horizontal fling: sx:$scrollX vx:$velocityX df:$distanceFrom")


            // horizontal
            // if less than 1000 'bounce back'
            if(abs(velocityX) > 1000) {
                if (velocityX > 0) {
                    // scroll to left page
                    horizontalScroller.startScroll(scrollX, scrollY, - scrollX + contentWidth * (visibleWeek - 1), 0)
                } else {
                    // scroll to right page
                    horizontalScroller.startScroll(scrollX, scrollY, contentWidth * (visibleWeek + 1) - (scrollX), 0)
                }
            } else {


                if (abs(distanceFrom) > contentWidth / 2f) {
                    if (distanceFrom > 0) {
                        // scroll to right page
                        horizontalScroller.startScroll(scrollX, scrollY, contentWidth * (week + 1) - (scrollX), 0)
                    } else {
                        // scroll to left page
                        horizontalScroller.startScroll(scrollX, scrollY, - scrollX + contentWidth * (week - 1), 0)
                    }
                } else {
                    // bounce back scroll/fling
                    horizontalScroller.fling(
                        scrollX, 0,
                        -velocityX.toInt(), 0,
                        contentWidth * (week - 1), contentWidth * (week + 1),
                        0,0,
                    )
                }
            }
            postInvalidateOnAnimation()
        } else {
            if (currentScrollDirection == Direction.VERTICAL) {
                currentFlingDirection = Direction.VERTICAL
                // vertical
                Log.d(TAG, "Vertical fling: sy:$scrollY vy:$velocityY")

                verticalScroller.fling(
                    0, scrollY,
                    0, -velocityY.toInt(),
                    0,0,
                    0, getContentHeight() - height + topBarHeight
                )
                postInvalidateOnAnimation()
            }
        }

        return true
    }

    private fun onTouchUp(e1: MotionEvent?) {
        Log.d(TAG, "Touch up, resetting direction: sd: $currentScrollDirection, fd: $currentFlingDirection")
        currentScrollDirection = Direction.NONE

        if (currentFlingDirection == Direction.NONE) {
            // needs 'bounce back' from no fling

            val right = week * contentWidth + contentWidth / 2f
            val left = week * contentWidth - contentWidth / 2f

            val scrollDistance = if (scrollX > right) {
                // right
                (week + 1) * contentWidth - scrollX
            } else if(scrollX < left) {
                // left
                (week - 1) * contentWidth - scrollX
            } else {
                // back
                week * contentWidth - scrollX
            }


            Log.d(TAG, "Touch up: 'Bounce Back' sx:$scrollX d:$scrollDistance")

            horizontalScroller.startScroll(
                scrollX, 0,
                scrollDistance, 0
            )


            postInvalidateOnAnimation()
        }

    }

    // not used
    override fun onSingleTapUp(e: MotionEvent?): Boolean {
        return false
    }

    override fun onLongPress(e: MotionEvent?) {}
    override fun onScaleEnd(detector: ScaleGestureDetector?) {}
    override fun onShowPress(e: MotionEvent?) {}
}