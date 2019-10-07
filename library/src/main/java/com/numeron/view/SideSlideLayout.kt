package com.numeron.view

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.*
import android.widget.FrameLayout
import android.widget.Scroller
import kotlin.math.abs


class SideSlideLayout @JvmOverloads constructor(c: Context, a: AttributeSet? = null, i: Int = 0) :
    FrameLayout(c, a, i) {

    enum class Direction {

        NON, LEFT, TOP, RIGHT, BOTTOM;

        val isVertical: Boolean
            get() = this == TOP || this == BOTTOM

        val isHorizontal: Boolean
            get() = this == LEFT || this == RIGHT

    }

    private val bound = Rect()
    private val scroller = Scroller(c)
    private val listeners = Listeners()
    private val gestureDetector = GestureDetector(c, listeners)

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {

        val parentTop = paddingTop
        val parentLeft = paddingStart
        val parentRight = right - left - paddingEnd
        val parentBottom = bottom - top - paddingBottom

        repeat(childCount) { childIndex ->

            val childView = getChildAt(childIndex)

            if (childView.visibility != GONE) {

                val childLayoutParams = childView.layoutParams as LayoutParams

                val childWidth = childView.measuredWidth
                val childHeight = childView.measuredHeight

                val (layoutLeft, layoutTop) = when (childLayoutParams.direction) {
                    Direction.NON -> {
                        val layoutLeft = parentLeft + childLayoutParams.marginStart
                        val layoutTop = parentTop + childLayoutParams.topMargin

                        bound.set(parentLeft, parentTop, parentRight, parentBottom)

                        layoutLeft to layoutTop
                    }
                    Direction.LEFT -> {
                        val layoutLeft = -childWidth - childLayoutParams.marginEnd
                        val layoutTop = parentTop + childLayoutParams.topMargin
                        //修正左边界限
                        val boundLeft = layoutLeft - childLayoutParams.marginStart
                        bound.left = bound.left.coerceAtMost(boundLeft)
                        layoutLeft to layoutTop
                    }
                    Direction.TOP -> {
                        val layoutLeft = parentLeft + childLayoutParams.marginStart
                        val layoutTop = -childHeight - childLayoutParams.bottomMargin
                        //修正顶部界限
                        val boundTop = layoutTop - childLayoutParams.topMargin
                        bound.top = bound.top.coerceAtMost(boundTop)
                        layoutLeft to layoutTop
                    }
                    Direction.RIGHT -> {
                        val layoutLeft = parentRight + childLayoutParams.marginStart
                        val layoutTop = parentTop + childLayoutParams.topMargin
                        //修正右边界限
                        val boundRight = layoutLeft + childWidth + childLayoutParams.marginEnd
                        bound.right = bound.right.coerceAtLeast(boundRight)
                        layoutLeft to layoutTop
                    }
                    Direction.BOTTOM -> {
                        val layoutLeft = parentLeft + childLayoutParams.marginStart
                        val layoutTop = parentBottom + childLayoutParams.topMargin
                        //修正底部界限
                        val boundBottom = layoutTop + childHeight + childLayoutParams.bottomMargin
                        bound.bottom = bound.bottom.coerceAtLeast(boundBottom)
                        layoutLeft to layoutTop
                    }
                }

                val layoutRight = layoutLeft + childWidth
                val layoutBottom = layoutTop + childHeight

                //为子View布局
                childView.layout(layoutLeft, layoutTop, layoutRight, layoutBottom)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        //将触摸事件交给gestureDetector处理
        val detectResult = gestureDetector.onTouchEvent(event)
        //当触摸事件为松开手指时，调用Listeners中的onTouchActionUp方法
        if (event.action == MotionEvent.ACTION_UP) {
            listeners.onFling(null, event, 0f, 0f)
        }
        return detectResult
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            scrollTo(scroller.currX, scroller.currY)
            invalidate()
        }
    }

    override fun generateDefaultLayoutParams(): LayoutParams {
        return LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams {
        return LayoutParams(context, attrs)
    }

    override fun generateLayoutParams(lp: ViewGroup.LayoutParams): LayoutParams {
        return when (lp) {
            is MarginLayoutParams -> LayoutParams(lp)
            is LayoutParams -> LayoutParams(lp)
            else -> LayoutParams(lp)
        }
    }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean {
        return p is LayoutParams
    }

    private fun scrollToTop() {
        scroller.startScroll(scrollX, scrollY, -scrollX, bound.top - scrollY, 420)
        invalidate()
    }

    private fun scrollToLeft() {
        scroller.startScroll(scrollX, scrollY, bound.left - scrollX, -scrollY, 420)
        invalidate()
    }

    private fun scrollToRight() {
        scroller.startScroll(scrollX, scrollY, bound.right - scrollX - width, -scrollY, 420)
        invalidate()
    }

    private fun scrollToBottom() {
        scroller.startScroll(scrollX, scrollY, -scrollX, bound.bottom - scrollY - height, 420)
        invalidate()
    }

    private fun scrollToCenter() {
        scroller.startScroll(scrollX, scrollY, -scrollX, -scrollY, 420)
        invalidate()
    }

    fun scrollTo(direction: Direction) {
        when (direction) {
            Direction.TOP -> scrollToTop()
            Direction.LEFT -> scrollToLeft()
            Direction.NON -> scrollToCenter()
            Direction.RIGHT -> scrollToRight()
            Direction.BOTTOM -> scrollToBottom()
        }
    }

    inner class LayoutParams : FrameLayout.LayoutParams {

        var direction: Direction = Direction.NON

        constructor(c: Context, a: AttributeSet?) : super(c, a) {
            val typedArray = c.obtainStyledAttributes(a, R.styleable.SideSlideLayout_Layout)
            val sideValue = typedArray.getInt(R.styleable.SideSlideLayout_Layout_layout_side, 0)
            direction = Direction.values().first {
                it.ordinal == sideValue
            }
            typedArray.recycle()
        }

        @JvmOverloads
        constructor(width: Int, height: Int, gravity: Int = -1) : super(width, height, gravity)

        constructor(layoutParams: ViewGroup.LayoutParams) : super(layoutParams)

        constructor(layoutParams: FrameLayout.LayoutParams) : super(layoutParams)

        constructor(layoutParams: MarginLayoutParams) : super(layoutParams)

    }

    private inner class Listeners : GestureDetector.SimpleOnGestureListener() {

        private var locate = Direction.NON

        private var toTop = false
        private var toLeft = false
        private var toRight = false
        private var toBottom = false

        private val isVertical
            get() = toTop || toBottom

        private val isHorizontal
            get() = toLeft || toRight

        override fun onDown(e: MotionEvent): Boolean {
            //在手指接触到屏幕时，记录下当前位置
            if (scrollX == 0 && scrollY == 0) {
                locate = Direction.NON
            } else if (scrollX < 0 && scrollY == 0) {
                locate = Direction.LEFT
            } else if (scrollX > 0 && scrollY == 0) {
                locate = Direction.RIGHT
            } else if (scrollY < 0 && scrollX == 0) {
                locate = Direction.TOP
            } else if (scrollY > 0 && scrollX == 0) {
                locate = Direction.BOTTOM
            }
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            return callOnClick()
        }

        override fun onScroll(
            e1: MotionEvent,
            e2: MotionEvent,
            distanceX: Float,
            distanceY: Float
        ): Boolean {
            //禁止父视图容器对触摸事件的拦截权限
            parent?.requestDisallowInterceptTouchEvent(true)
            //计算此次滑动的方向
            calculateScrollDirection(distanceX, distanceY)
            //根据滑动的方向和当前位置，来处理滑动效果
            if (!locate.isVertical && isHorizontal) {
                //当前方向不是垂直的，并且是水平操作时，允许拖动
                val distance =
                    distanceX.toInt().coerceIn(bound.left - scrollX, bound.right - scrollX - width)
                scrollBy(distance, 0)
            } else if (!locate.isHorizontal && isVertical) {
                //当前方向不是水平的，并且是垂直操作时，允许拖动
                val distance =
                    distanceY.toInt().coerceIn(bound.top - scrollY, bound.bottom - scrollY - height)
                scrollBy(0, distance)
            }
            return true
        }

        /**
         * 根据滑动距离来判断此次滑动的方向
         */
        private fun calculateScrollDirection(distanceX: Float, distanceY: Float) {
            if (!toLeft && !toRight && !toTop && !toBottom) {

                val absoluteX = abs(distanceX)
                val absoluteY = abs(distanceY)

                val isHorizontal = absoluteX > absoluteY
                val isVertical = absoluteX < absoluteY

                toTop = isVertical && distanceY < 0
                toBottom = isVertical && distanceY > 0

                toLeft = isHorizontal && distanceX < 0
                toRight = isHorizontal && distanceX > 0
            }
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float
        ): Boolean {
            if (toLeft || toTop || toRight || toBottom) {
                when (locate) {
                    Direction.TOP -> if (toBottom && velocityY <= -200) scrollToCenter() else scrollToTop()
                    Direction.LEFT -> if (toRight && velocityX <= -200) scrollToCenter() else scrollToLeft()
                    Direction.RIGHT -> if (toLeft && velocityX >= 200) scrollToCenter() else scrollToRight()
                    Direction.BOTTOM -> if (toTop && velocityY >= 200) scrollToCenter() else scrollToBottom()
                    Direction.NON -> when {
                        toTop -> if (velocityY >= 200) scrollToTop() else scrollToCenter()
                        toLeft -> if (velocityX >= 200) scrollToLeft() else scrollToCenter()
                        toRight -> if (velocityX <= -200) scrollToRight() else scrollToCenter()
                        toBottom -> if (velocityY <= -200) scrollToBottom() else scrollToCenter()
                    }
                }
                //复位方向标记
                toBottom = false
                toRight = false
                toLeft = false
                toTop = false
                //恢复父视图容器对触摸事件的拦截权限
                parent?.requestDisallowInterceptTouchEvent(false)
                //消耗事件
                return true
            }
            return false
        }

    }

}