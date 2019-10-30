package com.connecteric.weektimerangepicker

import android.content.Context
import android.graphics.*
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.widget.OverScroller
import android.widget.ScrollView
import android.widget.Scroller
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sqrt


class TimePickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback {

    private val _tag = this.javaClass.simpleName

    private enum class touchMode { SELECT, MOVE, SCROLL, ZOOM }
    private var scroller: Scroller? = null

    private val paint = Paint()
    private var lastTouchX: Float = 0.toFloat()
    private var lastTouchY: Float = 0.toFloat()
    private val dirtyRect = RectF()
    private val backgroundColor = Color.rgb(230, 230, 230)
    private var numColumns: Int = 0
    private var numRows: Int = 0
    private var cellWidth: Int = 0
    private var cellHeight: Int = 0
    private var blackPaint = Paint()
    private var cellChecked = emptyArray<BooleanArray>()
    var viewWidth = 340
    var viewHight = 500
    var marginLTRB = intArrayOf(0, 0, 0, 0)
    var currentBlock = arrayOf(0, 0)
    val margin = 3.0f
    val topMargin = 175f
    val leftMargin = 50f
    private var isCanTouch = false
    private var point_num = 0//当前触摸的点数
    val SCALE_MAX = 4.0f //最大的缩放比例
    private val SCALE_MIN = 1f//最小缩放比例

    private var oldDist: Double = 0.0
    private var moveDist: Double = 0.0
    private var currentTouchMode = touchMode.SELECT
    private var density = 1.0f
    private var viewMaxHeight = 0
    private var viewMaxWidth = 0

    private var mScrollX = 0
    private var mScrollY = 0

    private var oldRect:Rect
    private var originRect:Rect


    init {
        oldRect = Rect()
        originRect = Rect()

        paint.isAntiAlias = true
        paint.color = Color.red(100)
        paint.style = Paint.Style.STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = STROKE_WIDTH
        this.setBackgroundColor(Color.WHITE)
/*        blackPaint.isAntiAlias = true
        blackPaint.color = Color.blue(56)
        blackPaint.style = Paint.Style.FILL
        blackPaint.strokeJoin = Paint.Join.ROUND
        blackPaint.strokeWidth = STROKE_WIDTH*/

        val metrics = resources.displayMetrics
        //density = metrics.density - 0.5f


        scroller = Scroller(context)


    }

    override fun computeScroll() {
        if (scroller!!.computeScrollOffset()) {

            //这里调用View的scrollTo()完成实际的滚动
            scrollTo(scroller!!.currX, scroller!!.currY)

            //必须调用该方法，否则不一定能看到滚动效果
            postInvalidate()
        }
        super.computeScroll()
    }


    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        viewWidth = width
        viewHight = height

        originRect = Rect(0,0,width,height)

        calculateDimensions()
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        //Log.d(_tag, "l,t, oldl, oldt : $l, $t, $oldl, $oldt")
    }

    companion object {
        private const val STROKE_WIDTH = 5f
        //private val HALF_STROKE_WIDTH = STROKE_WIDTH / 2
    }


    fun setNumColumns(numColumns: Int) {
        this.numColumns = numColumns
        calculateDimensions()
    }

    fun getNumColumns(): Int {
        return numColumns
    }

    fun setNumRows(numRows: Int) {
        this.numRows = numRows
        calculateDimensions()
    }

    fun getNumRows(): Int {
        return numRows
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateDimensions()
    }

    private fun calculateDimensions() {
        if (numColumns < 1 || numRows < 1) {
            return
        }

        cellWidth = ((viewWidth - 2 * leftMargin) / numColumns).toInt()
        cellHeight = (viewHight / numRows).toInt()

        cellChecked = Array(numColumns) { BooleanArray(numRows) }

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(backgroundColor)

        if (numColumns == 0 || numRows == 0) {
            return
        }

        val width = this.width - 2 * left
        val height = this.height
        //calculateDimensions()


        for (i in 0 until numColumns) {
            for (j in 0 until numRows) {
                if (cellChecked[i][j]) {

                    canvas.drawRect(
                        i * cellWidth * 1.0f + margin + leftMargin,
                        j * cellHeight * 1.0f + margin + topMargin,
                        (i + 1) * cellWidth * 1.0f - margin + leftMargin,
                        (j + 1) * cellHeight * 1.0f - margin + topMargin,
                        blackPaint
                    )

                }
            }
        }

        for (i in 0 until numColumns + 1) {
            canvas.drawLine(
                i * cellWidth * 1.0f + leftMargin,
                topMargin,
                i * cellWidth * 1.0f + leftMargin,
                height * 1.0f + topMargin,
                blackPaint
            )
        }

        for (i in 0 until numRows + 1) {
            canvas.drawLine(
                leftMargin,
                i * cellHeight * 1.0f + topMargin,
                width - leftMargin,
                i * cellHeight * 1.0f + topMargin,
                blackPaint
            )
            canvas.drawText(
                "%02d:00".format(i), 5f,
                i * cellHeight * 1.0f + margin + topMargin, blackPaint
            )
        }

        onDrawHeader(canvas)
        onDrawTimeHint(canvas)

        //canvas.drawRect(10f,10f,viewMaxWidth.toFloat() -20,viewMaxHeight.toFloat(), blackPaint)
    }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d(_tag, "scroll x, y: $scrollX, $scrollY")
        val column = ((event.x + scrollX - leftMargin) / cellWidth).toInt()
        val row = ((event.y + scrollY - topMargin) / cellHeight).toInt()
        try {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y

                }
                MotionEvent.ACTION_MOVE -> {


                    if (event.pointerCount == 1) {
                        if (column != currentBlock[0] || row != currentBlock[1]) {

                            cellChecked[column][row] = !cellChecked[column][row]
                            currentBlock[0] = column
                            currentBlock[1] = row

                            invalidate()
                        }
                    } else if (event.pointerCount >= 2) {
                        //Log.d(_tag, "finger number: ${event.pointerCount}")
                        moveDist = spacing(event)
                        val space = moveDist - oldDist
                        val scale = (scaleX + space / width).toFloat()
                        if (scale > SCALE_MIN && scale < SCALE_MAX) {
                            setScale(scale)
                            startScroll(event)

                        } else if (scale < SCALE_MIN) {
                            setScale(SCALE_MIN)
                            scrollTo(0, 0)

                        }
                        lastTouchX = event.x
                        lastTouchY = event.y
                        invalidate()
                    }


                    //Log.d(_tag, "motion event move, finger number: $point_num")
                    //Log.d(_tag, "cursor on ${event.x}, ${event.y}")

                }
                MotionEvent.ACTION_UP -> {

                    if (currentBlock[0] == column
                        && currentBlock[1] == row
                        && currentTouchMode == touchMode.SELECT
                    ) {
                        cellChecked[column][row] = !cellChecked[column][row]

                    }
                    invalidate()
                    currentTouchMode = touchMode.SELECT
                }

                MotionEvent.ACTION_POINTER_DOWN -> {
                    currentTouchMode = touchMode.ZOOM
                    oldDist = spacing(event) //两点按下时的距离
                    Log.d(_tag, "Pointer down")
                    invalidate()

                }

                MotionEvent.ACTION_POINTER_UP -> {
                    Log.d(_tag, "Pointer up")
                    invalidate()
                }
            }
        } catch (e: Exception) {
            Log.e(_tag, e.message)
        }


        invalidate()
        return true
    }

    private fun startScroll(event: MotionEvent) {
        if(scrollX <-20) {scrollTo(0, scrollY); return}
        if(scrollY > 700) return
        if(scrollX > width+20) return
        if(scrollY < -20) {scrollTo(scrollX, 0); return}
        scrollBy(
            (-event.getX(0) + lastTouchX).toInt(),
            (-event.getY(0) + lastTouchY).toInt()
        )
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        // Do nothing for now
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (isAttachedToWindow) {
            val canvas = holder.lockCanvas()
            canvas?.let {
                it.drawRect(Rect(100, 100, 200, 200), paint)
                it.drawRGB(230, 230, 230)
                holder.unlockCanvasAndPost(it)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

/*        val desiredWidth = suggestedMinimumWidth + paddingLeft + paddingRight
        val desiredHeight = suggestedMinimumHeight + paddingTop + paddingBottom*/

        val desiredWidth = viewMaxWidth
        val desiredHeight = viewMaxHeight

        setMeasuredDimension(
            View.resolveSize(desiredWidth, widthMeasureSpec),
            View.resolveSize(desiredHeight, heightMeasureSpec)
        )
        Log.d(_tag, "desiredWidth: $desiredWidth, desiredHeight: $desiredHeight")
        //setMeasuredDimension(600,2000)
    }

    override fun onDragEvent(event: DragEvent?): Boolean {
        Log.d(_tag, "drag event triggered.")
        return true

    }

    override fun onOverScrolled(scrollX: Int, scrollY: Int, clampedX: Boolean, clampedY: Boolean) {
        Log.d(_tag, "scrollX $scrollX, scrollY: $scrollY, clampedX: $clampedX, clampedY: $clampedY")
        if (!scroller!!.isFinished) {
            val oldX = mScrollX
            val oldY = mScrollY
            mScrollX = scrollX
            mScrollY = scrollY
            //invalidateParentIfNeeded()
            onScrollChanged(mScrollX, mScrollY, oldX, oldY)
            if (clampedY || clampedX) {
                //scroller?.springBack()//(mScrollX, mScrollY, 0, viewMaxWidth, 0, viewMaxHeight)
            }
        } else {
            super.scrollTo(scrollX, scrollY)
        }
        //super.onOverScrolled(scrollX, scrollY, clampedX, clampedY)
    }


    fun springBack(startX: Int, startY: Int, minX: Int, maxX: Int, minY: Int, maxY: Int): Boolean {
        //mMode = FLING_MODE

        // Make sure both methods are called.
        //val spingback = scroller!!.springBack(startX, startY,minX,maxX, minY, maxY)//.springback(startX, minX, maxX)
        // val spingbackY = scrollerY.springback(startY, minY, maxY)
        //return spingback
        return false
    }

    /**
     * 计算两个点的距离
     *
     * @param event
     * @return
     */
    private fun spacing(event: MotionEvent): Double {
        if (event.pointerCount == 2) {
            val x = event.getX(0) - event.getX(1)
            val y = event.getY(0) - event.getY(1)
            return sqrt((x * x + y * y).toDouble())
        } else {
            return 0.0
        }
    }

    /**
     * 平移画面，当画面的宽或高大于屏幕宽高时，调用此方法进行平移
     *
     * @param x
     * @param y
     */
    fun setPivot(x: Float, y: Float) {
        pivotX = x
        pivotY = y
    }

    /**
     * 设置放大缩小
     *
     * @param scale
     */
    fun setScale(scale: Float) {
        scaleX = scale
        scaleY = scale
    }

    /**
     * 初始化比例，也就是原始比例
     */
    fun setInitScale() {
        scaleX = 1.0f
        scaleY = 1.0f
        setPivot((width / 2).toFloat(), (height / 2).toFloat())
    }

    /**
     * 触摸使用的移动事件
     *
     * @param lessX
     * @param lessY
     */
    private fun setSelfPivot(lessX: Float, lessY: Float) {
        var setPivotX = 0f
        var setPivotY = 0f
        setPivotX = pivotX + lessX
        setPivotY = pivotY + lessY
        Log.e(
            _tag, "lawwingLog " + "setPivotX:" + setPivotX + "  setPivotY:" + setPivotY
                    + "  getWidth:" + width + "  getHeight:" + height
        )
        if (setPivotX < 0 && setPivotY < 0) {
            setPivotX = 0f
            setPivotY = 0f
        } else if (setPivotX > 0 && setPivotY < 0) {
            setPivotY = 0f
            if (setPivotX > width) {
                setPivotX = width.toFloat()
            }
        } else if (setPivotX < 0 && setPivotY > 0) {
            setPivotX = 0f
            if (setPivotY > height) {
                setPivotY = height.toFloat()
            }
        } else {
            if (setPivotX > width) {
                setPivotX = width.toFloat()
            }
            if (setPivotY > height) {
                setPivotY = height.toFloat()
            }
        }
        setPivot(setPivotX, setPivotY)
    }

    private fun onDrawHeader(canvas: Canvas) {
        val dayName = listOf<String>("SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT")
        val textPadding = 8.0f
        for (i in 0 until numColumns) {
            canvas.drawLine(
                i * cellWidth * 1.0f + leftMargin,
                topMargin - cellHeight,
                i * cellWidth * 1.0f + leftMargin,
                topMargin,
                blackPaint
            )
            canvas.drawText(
                dayName[i],
                i * cellWidth * 1f + cellWidth / 3 + leftMargin + textPadding,
                topMargin - 8,
                blackPaint
            )
        }
        canvas.drawLine(
            leftMargin,
            topMargin - cellHeight,
            leftMargin + 7 * cellWidth,
            topMargin - cellHeight,
            blackPaint
        )


    }

    private fun onDrawTimeHint(canvas: Canvas) {

    }

    fun saveImage(bitmap: Bitmap) {

        try {
            val filename = Environment.getExternalStorageDirectory().toString()

            val f = File(filename, "myImage.png")
            f.createNewFile()
            System.out.println("file created " + f.toString())
            val out = FileOutputStream(f)
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }


}
