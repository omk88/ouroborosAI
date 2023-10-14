package com.ouroboros.aimobileapp

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.res.Resources
import android.graphics.*
import android.os.Handler
import android.os.SystemClock
import android.util.AttributeSet
import android.view.*
import android.view.animation.*
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.animation.doOnEnd
import androidx.core.graphics.scale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Float.max
import java.lang.Float.min
import java.util.*

class ExpandableCanvasView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    private var originalBitmap: Bitmap? = null
    var workingBitmap: Bitmap? = null
    private var isMoveMode = false
    private var isDrawMode = false
    private var isPinching = false
    private var currentDragX = 0f
    private var currentDragY = 0f
    private var scaleFactor = 1f
    private var cumulativeExpansionOffsetX = 0f
    private var cumulativeExpansionOffsetY = 0f
    private var canvasInteractionEnabled = true
    private var generateLayout: LinearLayout? = null
    var currentScale = 1f
    var expandCount = 0
    private var hasMoved = false
    private var hasZoomedOut = false
    private var undoCount = 0
    val bitmapRedoStack: Stack<Bitmap> = Stack()
    val bitmapUndoStack: Stack<Bitmap> = Stack()
    private var expansionIsDone = true

    private var bitmapHeight: Int = 0
    private var bitmapWidth: Int = 0

    var totalExpansionCount: Int = 0


    private var currentAlpha = 1.0f
    var isAnimating = false
    private var animationThread: Thread? = null

    private var expanded = false

    private val fadeInDuration = 1000L
    private val fadeOutDuration = 1000L
    private val pauseDuration = 500L
    val undoStack: Stack<DrawCommand> = Stack()
    val redoStack: Stack<DrawCommand> = Stack()

    var globalScaleX: Float = 1.0f
    var globalScaleY: Float = 1.0f

    private lateinit var expandedBitmap: Bitmap

    private val tempPath = Path()
    lateinit var undoButton: ImageView

    private var undoButtonFlag = false

    private val strokesStack = Stack<DrawCommand>()

    var checkerboardSize = Point(0, 0)



    private val paint = Paint().apply {
        isAntiAlias = true
        isDither = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 50f
    }

    var checkerSize = 16
    private val checkerPaintDark = Paint().apply { color = Color.LTGRAY }
    private val checkerPaintLight = Paint().apply { color = Color.WHITE }
    private val currentPath = Path()
    private val gestureDetector = GestureDetector(context, SimpleGestureListener())
    private val scaleGestureDetector = ScaleGestureDetector(context, ScaleGestureListener())

    private val checkerboardMatrix = Matrix()
    val imageMatrix = Matrix()

    private val canvasTransformMatrix = Matrix()
    private val inverseCanvasTransformMatrix = Matrix()

    private var fadeAnimation: AlphaAnimation? = null


    init {
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidate() }

        val values = FloatArray(9)
        imageMatrix.getValues(values)
        globalScaleX = values[Matrix.MSCALE_X]
        globalScaleY = values[Matrix.MSCALE_Y]


    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        currentDragX = w.toFloat() / 2
        currentDragY = h.toFloat() / 2

        checkerboardSize = Point(w, h)

    }

    override fun onDraw(canvas: Canvas) {
        canvas.save()

        val cx = width / 2f
        val cy = height / 2f
        canvas.scale(currentScale, currentScale, cx, cy)

        canvas.save()
        canvas.concat(checkerboardMatrix)

        for (i in 0 until checkerboardSize.x step checkerSize * 2) {
            for (j in 0 until checkerboardSize.y step checkerSize * 2) {
                canvas.drawRect(i.toFloat(), j.toFloat(), (i + checkerSize).toFloat(), (j + checkerSize).toFloat(), checkerPaintDark)
                canvas.drawRect((i + checkerSize).toFloat(), j.toFloat(), (i + 2 * checkerSize).toFloat(), j.toFloat() + checkerSize, checkerPaintLight)
                canvas.drawRect(i.toFloat(), (j + checkerSize).toFloat(), (i + checkerSize).toFloat(), (j + 2 * checkerSize).toFloat(), checkerPaintLight)
                canvas.drawRect((i + checkerSize).toFloat(), (j + checkerSize).toFloat(), (i + 2 * checkerSize).toFloat(), (j + 2 * checkerSize).toFloat(), checkerPaintDark)
            }
        }
        canvas.restore()

        workingBitmap?.let {
            canvas.save()
            canvas.concat(imageMatrix)
            canvas.drawBitmap(it, (width - it.width) / 2f, (height - it.height) / 2f, null)
            canvas.restore()
        }

        canvas.restore()
    }

    fun reduceCheckerboard() {
        checkerboardSize.x -= 50
        checkerboardSize.y -= 50
        if (checkerboardSize.x < 0) checkerboardSize.x = 0
        if (checkerboardSize.y < 0) checkerboardSize.y = 0
        invalidate()
    }

    fun startFadeAnimation() {
        if (!isAnimating) {
            isAnimating = true
            animationThread = Thread {
                while (isAnimating) {
                    val startTime = System.currentTimeMillis()

                    currentAlpha = calculateCurrentAlpha(startTime)

                    postInvalidate()

                    val animationElapsed = System.currentTimeMillis() - startTime

                    val sleepTime = calculateSleepTime(animationElapsed)

                    try {
                        Thread.sleep(sleepTime)
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                }
            }

            animationThread?.start()
        }
    }


    fun stopFadeAnimation() {
        isAnimating = false
        animationThread?.interrupt()
        animationThread = null
        currentAlpha = 255F
        postInvalidate()
    }

    fun setGenerateLayout(layout: LinearLayout) {
        this.generateLayout = layout
    }


    private fun calculateCurrentAlpha(startTime: Long): Float {
        val animationCycleDuration = fadeInDuration + fadeOutDuration + pauseDuration
        val animationPhase = (System.currentTimeMillis() - startTime) % animationCycleDuration

        return when {
            animationPhase < fadeInDuration + pauseDuration -> 1.0f
            animationPhase < fadeInDuration -> 1.0f - animationPhase / fadeInDuration.toFloat()
            else -> (animationPhase - fadeInDuration) / fadeOutDuration.toFloat()
        }
    }


    private fun calculateSleepTime(animationElapsed: Long): Long {
        val frameDuration = 1000L / 60
        return max(0f, frameDuration - animationElapsed.toFloat()).toLong()
    }

    fun setStrokeWidth(width: Float) {
        paint.strokeWidth = width
        invalidate()
    }



    fun setImageBitmap(bitmap: Bitmap, isFadeIn: Boolean) {
        val previousDragX = currentDragX
        val previousDragY = currentDragY
        val previousScaleFactor = scaleFactor

        val newWorkingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val tempCanvas = Canvas(newWorkingBitmap)
        val scaleX = width.toFloat() / bitmap.width
        val scaleY = height.toFloat() / bitmap.height
        val combinedScale = Math.min(scaleX, scaleY)
        val dstWidth = (bitmap.width * combinedScale).toInt()
        val dstHeight = (bitmap.height * combinedScale).toInt()
        val offsetX = (width - dstWidth) / 2
        val offsetY = (height - dstHeight) / 2
        val destRect = Rect(offsetX, offsetY, offsetX + dstWidth, offsetY + dstHeight)
        tempCanvas.drawBitmap(bitmap, null, destRect, null)

        if (isFadeIn) {
            setImageBitmapWithFadeIn(newWorkingBitmap)
        } else {
            workingBitmap = newWorkingBitmap
            updateTransformationMatrices()
            invalidate()
        }

        originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)

        currentDragX = previousDragX
        currentDragY = previousDragY
        scaleFactor = previousScaleFactor

        adjustCanvasSize(dstWidth, dstHeight)
    }


    private fun adjustCanvasSize(newWidth: Int, newHeight: Int) {
        layoutParams.width = newWidth
        layoutParams.height = newHeight
        println("PARAMS"+layoutParams.width)
        println("PARAMS"+layoutParams.height)
        requestLayout()
    }

    fun printMatrixValues(matrix: Matrix, tag: String) {
        val values = FloatArray(9)
        matrix.getValues(values)
        println("$tag Matrix values: ScaleX=${values[Matrix.MSCALE_X]}, ScaleY=${values[Matrix.MSCALE_Y]}, TranslateX=${values[Matrix.MTRANS_X]}, TranslateY=${values[Matrix.MTRANS_Y]}")
    }

    private fun updateTransformationMatrices() {
        checkerboardMatrix.reset()
        checkerboardMatrix.preTranslate(currentDragX - cumulativeExpansionOffsetX, currentDragY - cumulativeExpansionOffsetY)
        checkerboardMatrix.preScale(scaleFactor, scaleFactor, width / 2f, height / 2f)
        checkerboardMatrix.preTranslate(-width / 2f, -height / 2f)

        imageMatrix.set(checkerboardMatrix)
        checkerboardMatrix.invert(inverseCanvasTransformMatrix)

        globalScaleX = scaleFactor
        globalScaleY = scaleFactor
    }






    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (!canvasInteractionEnabled) {
            return true
        }

        val touchX = event.x
        val touchY = event.y

        updateTransformationMatrices()


        imageMatrix.set(checkerboardMatrix)

        checkerboardMatrix.invert(inverseCanvasTransformMatrix)
        val transformedPoints = floatArrayOf(touchX, touchY)
        inverseCanvasTransformMatrix.mapPoints(transformedPoints)

        val adjustedX = transformedPoints[0]
        val adjustedY = transformedPoints[1]

        if (isMoveMode) {
            gestureDetector.onTouchEvent(event)
            scaleGestureDetector.onTouchEvent(event)
            hasMoved = true

            return true
        } else if (isDrawMode) {

            workingBitmap?.let { bitmap ->

                imageMatrix.invert(inverseCanvasTransformMatrix)
                val transformedPoints = floatArrayOf(touchX, touchY)
                inverseCanvasTransformMatrix.mapPoints(transformedPoints)
                val imgX = transformedPoints[0] - (width - bitmap.width) / 2f
                val imgY = transformedPoints[1] - (height - bitmap.height) / 2f

                if (imgX >= 0 && imgX <= bitmap.width && imgY >= 0 && imgY <= bitmap.height) {
                    val swipeDownAnimation = AnimationUtils.loadAnimation(context, R.anim.swipe_down)


                    if (generateLayout?.visibility == View.GONE) {
                        generateLayout?.visibility = View.VISIBLE
                        generateLayout?.startAnimation(swipeDownAnimation)
                        undoButtonFlag = true
                    }
                }
            }

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    workingBitmap?.let {
                        bitmapUndoStack.push(it.copy(Bitmap.Config.ARGB_8888, true))
                    }

                    tempPath.moveTo(adjustedX, adjustedY)

                    if (bitmapUndoStack.isEmpty()) {
                        undoButton.setImageResource(R.drawable.undo2)
                    } else {
                        undoButton.setImageResource(R.drawable.undo)
                    }

                    if (undoButtonFlag) {
                        undoButton.setImageResource(R.drawable.undo)
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    tempPath.lineTo(adjustedX, adjustedY)
                    workingBitmap?.let {
                        val tempCanvas = Canvas(it)
                        tempCanvas.drawPath(tempPath, paint)
                    }

                    invalidate()
                }
                MotionEvent.ACTION_UP -> {
                    val command = DrawCommand(Path(tempPath), Paint(paint))
                    strokesStack.push(command)
                    tempPath.reset()
                }

            }
            return true
        } else {
            return true
        }
    }

    fun getBitmap(): Bitmap? {
        return workingBitmap
    }

    fun resizeBitmap(source: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val matrix = Matrix()
        val scaleWidth = newWidth.toFloat() / source.width
        val scaleHeight = newHeight.toFloat() / source.height
        matrix.postScale(scaleWidth, scaleHeight)
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }


    fun undo() {
        if (!isAnimating) {
            if (bitmapUndoStack.isNotEmpty()) {
                workingBitmap?.let { bitmapRedoStack.push(it.copy(Bitmap.Config.ARGB_8888, true)) }

                workingBitmap = bitmapUndoStack.pop()
                updateTransformationMatrices()

                if (strokesStack.isNotEmpty()) {
                    undoStack.push(strokesStack.pop())
                }

                invalidate()
            }
        }
    }

    fun redo() {
        if (!isAnimating) {
            if (bitmapRedoStack.isNotEmpty()) {
                workingBitmap?.let { bitmapUndoStack.push(it.copy(Bitmap.Config.ARGB_8888, true)) }

                workingBitmap = bitmapRedoStack.pop()
                updateTransformationMatrices()


                invalidate()
            }
        }
    }

    fun toggleMoveModeOn() { isMoveMode = true }

    fun toggleMoveModeOff() { isMoveMode = false }

    fun toggleDrawModeOn() { isDrawMode = true }

    fun toggleDrawModeOff() { isDrawMode = false }

    fun getScreenWidth(): Int {
        val displayMetrics = Resources.getSystem().displayMetrics
        return displayMetrics.widthPixels
    }

    fun scaleBitmap(bitmap: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }


    fun expandCanvas(additionalSize: Int) {

        if (expansionIsDone && !isAnimating) {

            if (expandCount >= 3) {
                return
            }

            expansionIsDone = false

            expandCount++
            totalExpansionCount++

            val oldDragX = currentDragX
            val oldDragY = currentDragY

            val currentWidth = workingBitmap?.width ?: 0
            val currentHeight = workingBitmap?.height ?: 0

            val newWidth = currentWidth + additionalSize
            val newHeight = currentHeight + additionalSize

            val screenWidth: Float = getScreenWidth().toFloat()

            val targetScale = if (newWidth > screenWidth) {
                screenWidth / newWidth.toFloat()
            } else {
                1.0f
            }

            expandedBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
            val tempCanvas = Canvas(expandedBitmap)
            workingBitmap?.let {
                val offsetX = (newWidth - it.width) / 2
                val offsetY = (newHeight - it.height) / 2
                tempCanvas.drawBitmap(it, offsetX.toFloat(), offsetY.toFloat(), null)
            }

            val animator = ValueAnimator.ofFloat(0f, 1f)
            animator.duration = 300
            animator.addUpdateListener { valueAnimator ->
                val fraction = valueAnimator.animatedValue as Float

                val interpolatedWidth = (currentWidth + (fraction * additionalSize)).toInt()
                val interpolatedHeight = (currentHeight + (fraction * additionalSize)).toInt()

                adjustCanvasSize(interpolatedWidth, interpolatedHeight)
                invalidate()
            }

            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {

                    expansionIsDone = true

                    if (targetScale != 1.0f && !hasMoved && totalExpansionCount < 7) {
                        animateZoomOut(targetScale)
                    }
                    workingBitmap = expandedBitmap
                }
            })

            animator.start()

            globalScaleX = targetScale
            globalScaleY = targetScale

            expanded = true
        }

    }


    fun getOriginalBitmap(): Bitmap? {
        return originalBitmap
    }



    fun animateZoomOut(targetScale: Float) {
        val animator = ValueAnimator.ofFloat(currentScale, targetScale)
        animator.duration = 300

        animator.addUpdateListener { animation ->
            currentScale = animation.animatedValue as Float
            invalidate()
        }

        animator.start()
    }

    fun setImageBitmapWithFadeIn(bitmap: Bitmap) {

        val fadeInAnimation = AlphaAnimation(0f, 1f)
        fadeInAnimation.duration = 500

        fadeInAnimation.setAnimationListener(object : Animation.AnimationListener {
            override fun onAnimationStart(animation: Animation?) {
                reduceCheckerboard()
                workingBitmap = bitmap
                originalBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)


                invalidate()
            }

            override fun onAnimationEnd(animation: Animation?) {
                updateTransformationMatrices()
            }

            override fun onAnimationRepeat(animation: Animation?) {}
        })

        startAnimation(fadeInAnimation)
    }

    fun setCanvasInteractionEnabled(enabled: Boolean) {
        canvasInteractionEnabled = enabled
    }


    private inner class SimpleGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(
            e1: MotionEvent,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            currentDragX -= distanceX
            currentDragY -= distanceY
            invalidate()
            return true
        }
    }

    private inner class ScaleGestureListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isPinching = true
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            detector?.let {
                scaleFactor *= it.scaleFactor
                scaleFactor = max(0.7f, min(scaleFactor, 3.0f))
                invalidate()
            }
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isPinching = false
        }
    }
}

data class DrawCommand(val path: Path, val paint: Paint)











