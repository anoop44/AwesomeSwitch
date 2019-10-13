package ss.anoop.awesomeswitch

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.Paint.ANTI_ALIAS_FLAG
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec.*
import android.view.animation.LinearInterpolator
import kotlin.math.abs

class AwesomeSwitch @JvmOverloads constructor(
    context: Context,
    attributeSet: AttributeSet? = null,
    private val defStyleRes: Int = 0
) : View(context, attributeSet, defStyleRes) {
    private var radius = dpToPx(8)

    private var innerPadding = dpToPx(2)

    private var checkedCircleColor = Color.WHITE

    private var uncheckedCircleColor = Color.WHITE

    private var background = Color.parseColor("#777777")

    private var checkedBackground = background

    private lateinit var backgroundBitmap: Bitmap

    private val circlePoint = PointF()

    private var uncheckedX = 0f

    private var checkedX = 0f

    private var middleX = 0f

    private var stateChangeDuration = 300

    private var perPixelDuration = 0f

    private var isDragging = false

    private var listener: OnCheckedListener? = null

    private var isChecked = false

    private val circlePaint = Paint(ANTI_ALIAS_FLAG).apply {
        color = uncheckedCircleColor
    }

    init {
        attributeSet?.let(::initAttrs)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (getMode(widthMeasureSpec) == EXACTLY) {
            getSize(widthMeasureSpec)
        } else {
            (radius.times(3.6) + innerPadding.times(2)).toInt()
        }

        val height = if (getMode(heightMeasureSpec) == EXACTLY) {
            getSize(heightMeasureSpec)
        } else {
            (radius.times(2) + innerPadding.times(2)).toInt()
        }

        uncheckedX = radius + innerPadding
        checkedX = radius.times(2.6f) + innerPadding
        middleX = (uncheckedX + checkedX).div(2f)
        perPixelDuration = stateChangeDuration.div(abs(checkedX - uncheckedX))

        setMeasuredDimension(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        generateBackgroundBitmap(if (isChecked) checkedBackground else background)
        initSwitchCircle()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.apply {
            drawBitmap(backgroundBitmap, 0f, 0f, null)
            drawCircle(circlePoint.x, circlePoint.y, radius, circlePaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> onDragCircle(event.x)
            MotionEvent.ACTION_UP -> onStopDrag()
        }
        return true
    }

    fun setOnCheckedListener(listener: OnCheckedListener?) {
        this.listener = listener
    }

    fun isChecked() = isChecked

    fun setChecked(isChecked: Boolean) {
        if (isChecked != this.isChecked) {
            animateToState()
            this.isChecked = isChecked
        }
    }

    private fun animateToState() {
        if (width > 0 && height > 0) {
            if (isChecked) {
                animateToUnchecked()
            } else {
                animateToChecked()
            }
        }
    }

    private fun onDragCircle(x: Float) {
        isDragging = true
        circlePoint.x = when {
            x < uncheckedX -> uncheckedX
            x > checkedX -> checkedX
            else -> x
        }
        postInvalidate()
    }


    private fun onStopDrag() {
        if (isDragging) {
            isDragging = false
            if (circlePoint.x in uncheckedX.plus(0.1)..checkedX.minus(0.1)) {
                if (circlePoint.x < middleX) {
                    transitionToUnchecked()
                } else {
                    transitionToChecked()
                }
            } else {
                switchToStateAfterDrag()
            }
        } else {
            onClick()
        }
    }

    private fun onClick() {
        if (isChecked) {
            transitionToUnchecked()
        } else {
            transitionToChecked()
        }
    }

    private fun switchToStateAfterDrag() {
        if (circlePoint.x < middleX) {
            circlePaint.color = uncheckedCircleColor
            generateBackgroundBitmap(background)
            isChecked = false
        } else {
            circlePaint.color = checkedCircleColor
            generateBackgroundBitmap(checkedBackground)
            isChecked = true
        }

        invalidate()
    }

    private fun transitionToUnchecked() {
        animateToUnchecked()
        if (isChecked) {
            setState(false)
        }
    }

    private fun animateToUnchecked() {
        val animations = mutableListOf<Animator>().apply {
            add(makeStateChangeAnimation(uncheckedX))
        }

        if (checkedCircleColor != uncheckedCircleColor) {
            animations.add(makeCircleColorChangeAnimation(checkedCircleColor, uncheckedCircleColor))
        }

        if (background != checkedBackground) {
            animations.add(makeBackgroundColorChangeAnimation(checkedBackground, background))
        }

        AnimatorSet().apply {
            playTogether(animations)
        }.start()
    }

    private fun transitionToChecked() {
        animateToChecked()
        if (isChecked.not()) {
            setState(true)
        }
    }

    private fun animateToChecked() {
        val animations = mutableListOf<Animator>().apply {
            add(makeStateChangeAnimation(checkedX))
        }

        if (checkedCircleColor != uncheckedCircleColor) {
            animations.add(makeCircleColorChangeAnimation(uncheckedCircleColor, checkedCircleColor))
        }

        if (background != checkedBackground) {
            animations.add(makeBackgroundColorChangeAnimation(background, checkedBackground))
        }

        AnimatorSet().apply {
            playTogether(animations)
        }.start()
    }

    private fun makeStateChangeAnimation(toPoint: Float): Animator {
        return ValueAnimator.ofFloat(circlePoint.x, toPoint).apply {
            interpolator = LinearInterpolator()
            duration = abs(circlePoint.x - toPoint).times(perPixelDuration).toLong()
            addUpdateListener(::onStateChangeAnimationUpdate)
        }
    }

    private fun onStateChangeAnimationUpdate(animator: ValueAnimator) {
        circlePoint.x = animator.animatedValue as Float
        postInvalidate()
    }

    private fun makeColorChangeAnimation(from: Int, to: Int): ValueAnimator {
        return ValueAnimator.ofInt(from, to).apply {
            setEvaluator(ArgbEvaluator())
            interpolator = LinearInterpolator()
            startDelay = abs(uncheckedX - middleX).times(perPixelDuration).toLong()
            duration = abs(checkedX - middleX).times(perPixelDuration).toLong()
        }
    }

    private fun makeCircleColorChangeAnimation(from: Int, to: Int): Animator {
        return makeColorChangeAnimation(from, to).apply {
            addUpdateListener(::onCircleColorChangeUpdate)
        }
    }

    private fun makeBackgroundColorChangeAnimation(from: Int, to: Int): Animator {
        return makeColorChangeAnimation(from, to).apply {
            addUpdateListener(::onBackgroundColorChangeUpdate)
        }
    }

    private fun onBackgroundColorChangeUpdate(animator: ValueAnimator) {
        generateBackgroundBitmap(animator.animatedValue as Int)
        postInvalidate()
    }

    private fun onCircleColorChangeUpdate(animator: ValueAnimator) {
        circlePaint.color = animator.animatedValue as Int
        postInvalidate()
    }


    private fun generateBackgroundBitmap(bgColor: Int) {
        val backgroundRadius = radius + innerPadding
        val path = Path().apply {
            moveTo(0f, 0f)
            addRoundRect(
                RectF(
                    0f, 0f,
                    radius.times(3.6f) + innerPadding.times(2),
                    radius.times(2) + innerPadding.times(2)
                ),
                floatArrayOf(
                    backgroundRadius, backgroundRadius, backgroundRadius, backgroundRadius,
                    backgroundRadius, backgroundRadius, backgroundRadius, backgroundRadius
                ), Path.Direction.CW
            )
        }

        val paint = Paint(ANTI_ALIAS_FLAG).apply { color = bgColor }
        backgroundBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(backgroundBitmap).apply {
            drawPath(path, paint)
        }
    }

    private fun setState(checked: Boolean) {
        isChecked = checked
        listener?.invoke(checked)
    }

    private fun initSwitchCircle() {
        circlePoint.apply {
            x = if (isChecked) checkedX else uncheckedX
            y = innerPadding + radius
        }
    }

    private fun initAttrs(attributeSet: AttributeSet) {
        val typedArray =
            context.obtainStyledAttributes(attributeSet, R.styleable.AwesomeSwitch, 0, defStyleRes)
        radius = typedArray.getDimension(R.styleable.AwesomeSwitch_radius, radius)
        innerPadding = typedArray.getDimension(R.styleable.AwesomeSwitch_innerPadding, innerPadding)
        checkedCircleColor =
            typedArray.getColor(R.styleable.AwesomeSwitch_checkedColor, checkedCircleColor)
        uncheckedCircleColor =
            typedArray.getColor(R.styleable.AwesomeSwitch_uncheckedColor, uncheckedCircleColor)
        background = typedArray.getColor(R.styleable.AwesomeSwitch_backgroundColor, background)
        checkedBackground = background
        checkedBackground =
            typedArray.getColor(R.styleable.AwesomeSwitch_checkedBackgroundColor, checkedBackground)
        isChecked = typedArray.getBoolean(R.styleable.AwesomeSwitch_isChecked, isChecked)
        stateChangeDuration =
            typedArray.getInteger(R.styleable.AwesomeSwitch_animationDuration, stateChangeDuration)
        typedArray.recycle()

        circlePaint.color = if (isChecked) checkedCircleColor else uncheckedCircleColor
    }


    private fun dpToPx(dp: Int): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            resources.displayMetrics
        )
    }
}

typealias OnCheckedListener = (Boolean) -> Unit