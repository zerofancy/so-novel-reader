// Adapted from PTQFlipper 1.1.0 (MIT, Copyright 2023 MPGA).
// See assets/licenses/PTQFlipper.txt.
package ptq.mpga.ptqbookpageview.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import ptq.mpga.ptqbookpageview.widget.Line.Companion.theta
import kotlin.math.absoluteValue


internal class CurlEngine(
    val bounds: Rect,
    val controller: PTQBookPageBitmapController,
    val localConfig: PTQBookPageViewConfig,
) {
    var callbacks by mutableStateOf(PTQBookPageViewScopeImpl())
    var onNext: () -> Unit = {}
    var onPrevious: () -> Unit = {}
    var pointerDown = false
    val ready get() = bounds.width > 0 && bounds.height > 0 && controller.isRenderOk()
    val busy get() = pointerDown || state != State.Idle
    //配置


    //组件宽高和原点O
    val viewHeight = bounds.height
    val viewWidth = bounds.width

    //扭曲格点数
    val bitmapMeshCount by mutableStateOf(
            Pair(
                ((viewWidth / 720) * viewWidth / localConfig.distortionInterval).toInt(), //对比720*1200的手机乘一个比例
                ((viewHeight / 1600) * viewHeight / localConfig.distortionInterval).toInt(),
            )
        )
    //connect时的下采样数
    val distortedEdgeDownSampling by mutableStateOf(
            (viewHeight * viewWidth * localConfig.distortionInterval / 1600 / 720).toInt()
        )

    //可拖动范围
    val dragXRange by mutableStateOf(FloatRange(0f, viewWidth * (1 - maxDragXRatio)))

    //组件左上角
    val absO by mutableStateOf(Point.Zero)
    //Tap回调中的组件左上角
    val leftUpOnTap by mutableStateOf(Point(0f, tapYDeltaRatio * viewHeight))
    //组件右下角
    val absC by mutableStateOf(Point(viewWidth, viewHeight))

    //位于组件一半高度的直线，用于处理翻转，绝对坐标系
    val lBCPerpendicularBisector by mutableStateOf(Line(0f, viewHeight / 2))

    //拖动事件，包括原触点，增量，现触点
    var curDragEvent by mutableStateOf(DragEvent(absO.copy(), absO.copy()))
    //当前状态
    var pageState by mutableStateOf(PageState.Loose)
    //要绘制的所有点，绝对系
    var allPoints by mutableStateOf(AllPoints.default(absO))
    //要绘制的Path，采用remember缓存，而没有使用drawWithCache
    val pathResult by mutableStateOf(
            PathResult(
                listOf(Path(), Path()),
                listOf(Path(), Path(), Path(), Path(), Path(), Path()),
                mutableListOf(
                    Pair(Point.Zero, Point.Zero),
                    Pair(Point.Zero, Point.Zero),
                    Pair(Point.Zero, Point.Zero),
                    Pair(Point.Zero, Point.Zero),
                    Pair(Point.Zero, Point.Zero),
                ),
                1f
            )
        )
    //当前翻转状态
    var upsideDown by mutableStateOf(false)

    /**
     * 点击和拖动时起手是右往左还是左往右（受[PTQBookPageViewScope.tapBehavior]和[PTQBookPageViewScope.dragBehavior]控制）
     */
    var isRightToLeftWhenStart by mutableStateOf(true)
    /**
     * 当页面Bitmap没有发生变化时，绘制的backgroundBitmap也可能不一样，因为分左起手还是右起手，所以需要一个变量记录上次是左还是右，以保证synthesizedBitmap缓存正常
     * @since v1.1.0
     */
    var isRightToLeftWhenStartLast by mutableStateOf(true)

    //有可能onDrag中没有进入开始动画（除第一页最后一页的情况外）（例如手指的触摸时间很短），这时自然不应该响应onDragEnd，因此这个变量保证开始动画一定执行了（换句话说，保证onDragEnd中，state=State.Idle的触发条件只有第一页和最后一页的情况
    var turnPageRequestWhenDrag by mutableStateOf(false)
    //应该是下一页还是上一页，因为要等动画结束才能算完全翻完页，所以回调要在动画结束之后，因此需要一个变量记录一下状态
    var isNextOrPrevious by mutableStateOf<Boolean?>(null)

    /**
     * 拖动起始点（起始动画之前，用户的最开始触摸点），用于[PTQBookPageViewScope.dragBehavior]
     */
    var dragInitialPoint by mutableStateOf(absO.copy())

    //倾角θ
    var theta by mutableStateOf(0f)
    //fixedValue
    var f by mutableStateOf(0f)

    //流程状态
    var state: State by mutableStateOf(State.Idle)
    //入动画还未结束就松手了
    var exitPreStartState by mutableStateOf(false)
    //点击翻页则匀一下进出时间
    var animDuration by mutableStateOf(arrayOf(animEnterDuration, animExitDuration))

    //（在进入State.Draggable之前）是否被responseDragWhen终止，为true意味着无任何响应（不执行退出动画）
    var interruptedInDrag by mutableStateOf(false)

    //动画的初始和最终状态
    var animStartAndEndPoint by mutableStateOf(DragEvent(absO.copy(), absO.copy()))
    //动画的上一个点origin（这里的last是“上一个”的意思，不是“最后”的意思）
    var animLastPoint by mutableStateOf(absO.copy())
    //定时，拖动多久后开始播放动画
    var time by mutableStateOf(0L)

    //页面颜色
    val pageColor = localConfig.pageColor
    //12区域阴影颜色
    val nativeShadow12Color by mutableStateOf(toArgb(shadow12Color.value.toLong()))
    //3区阴影颜色
    val nativeShadow3Color by mutableStateOf(toArgb(shadow3Color.value.toLong()))
    //光泽阴影颜色
    val nativeLustreColor by mutableStateOf(toArgb(lustreColor.value.toLong()))
    //页面颜色
    val nativePageColor by mutableStateOf(toArgb(pageColor.value.toLong()))
    //透明色
    val nativeTransparentColor by mutableStateOf(toArgb(Color.Transparent.value.toLong()))

    //退出动画lambda
    val exeExitAnim: () -> Unit = {
        val startPoint = if (!upsideDown) allPoints.J else allPoints.J.getSymmetricalPointAbout(lBCPerpendicularBisector)
        f = maxOf(0f, allPoints.J.distanceTo(allPoints.H))
        val y = if (!upsideDown) viewHeight - f else f

        val dragBehavior = callbacks.dragBehavior

        if (dragBehavior == null) {
            val isFingerAtRight = curDragEvent.currentTouchPoint.x > 0.5f * viewWidth

            //记录是下一页还是前一页，用于动画结束后触发onNext/onPrevious回调
            if (isFingerAtRight && !isRightToLeftWhenStart) {
                isNextOrPrevious = false
            }

            if (!isFingerAtRight && isRightToLeftWhenStart) {
                isNextOrPrevious = true
            }

            animStartAndEndPoint = DragEvent(startPoint.copy(), if (isFingerAtRight) Point(dragXRange.end, y) else Point(-dragXRange.end / 2, y))
        } else {
            val (isLeftLeave, isNext) = dragBehavior(absC, dragInitialPoint, curDragEvent.currentTouchPoint, isRightToLeftWhenStart)

            isNext?.let {
                isNextOrPrevious = it
            }

            animStartAndEndPoint = DragEvent(startPoint.copy(), if (!isLeftLeave) Point(dragXRange.end, y) else Point(-dragXRange.end / 2, y))
        }

        animLastPoint = startPoint.copy()
        pageState = PageState.Loose
        state = State.ExitAnimStart
    }


    fun animationFinished() {
        //动画结束时触发的回调
        when (state) {
            State.EnterAnimStart -> {
                state = if (exitPreStartState) State.ExitAnimPreStart else State.Draggable
            }

            State.ExitAnimStart -> {
                //所有流程的最后，所有状态重置
                curDragEvent = DragEvent(absO.copy(), absO.copy())
                state = State.Idle
                exitPreStartState = false
                allPoints = AllPoints.default(absO)
                upsideDown = false
                pageState = PageState.Loose
                time = 0L
                theta = 0f
                f = 0f
                animLastPoint = absO.copy()
                animStartAndEndPoint = DragEvent(absO.copy(), absO.copy())
                animDuration = arrayOf(animEnterDuration, animExitDuration)
                if (isNextOrPrevious == true) onNext() else if (isNextOrPrevious == false) onPrevious()
                isNextOrPrevious = null
                interruptedInDrag = false
                dragInitialPoint = absO.copy()
                turnPageRequestWhenDrag = false
                isRightToLeftWhenStartLast = isRightToLeftWhenStart
            }

            State.ExitAnimPreStart -> { //确保animFloatRatio回到0f
                exeExitAnim()
            }

            else -> {}
        }
    }
    //拖动结束lambda
    val onDragEnd: () -> Unit = {
        when (state) {
            //正常状态下松手
            State.Draggable -> {
                exeExitAnim()
            }
            //如果正处于进入动画时松手了，则先进入预退出状态
            State.EnterAnimStart -> {
                exitPreStartState = true
            }
            //处理第一页或者最后一页（第一页或者最后一页时，所有流程的最后）
            State.Idle -> {
                if (turnPageRequestWhenDrag) {
                    val dragBehavior = callbacks.dragBehavior

                    if (dragBehavior == null) {
                        val isFingerAtRight = curDragEvent.currentTouchPoint.x > 0.5f * viewWidth

                        if (isFingerAtRight && !isRightToLeftWhenStart) {
                            isNextOrPrevious = false
                        }

                        if (!isFingerAtRight && isRightToLeftWhenStart) {
                            isNextOrPrevious = true
                        }
                    } else {
                        val (_, isNext) = dragBehavior(absC, dragInitialPoint, curDragEvent.currentTouchPoint, isRightToLeftWhenStart)

                        isNext?.let {
                            isNextOrPrevious = it
                        }
                    }

                    if (isNextOrPrevious == true) onNext() else if (isNextOrPrevious == false) onPrevious()
                }

                turnPageRequestWhenDrag = false
                isNextOrPrevious = null
            }

            else -> {}
        }
    }


    fun turn(next: Boolean) {
        if (!ready || busy || localConfig.disabled) return
        if (next && controller.currentPage >= controller.totalPage - 1) { onNext(); return }
        if (!next && controller.currentPage <= 0) { onPrevious(); return }
        animateTap(!next, Point(viewWidth * 0.8f, viewHeight * 0.8f))
    }

    fun cancel() {
        state = State.Idle
        isNextOrPrevious = null
        pointerDown = false
        exitPreStartState = false
        upsideDown = false
        interruptedInDrag = false
        turnPageRequestWhenDrag = false
        pageState = PageState.Loose
        allPoints = AllPoints.default(absO)
        curDragEvent = DragEvent(absO.copy(), absO.copy())
        time = 0L
        theta = 0f
        f = 0f
        animLastPoint = absO.copy()
        animStartAndEndPoint = DragEvent(absO.copy(), absO.copy())
        animDuration = arrayOf(animEnterDuration, animExitDuration)
    }

    fun animateTap(isLeftToRight: Boolean, touchPoint: Point) {
        val tapYDelta = leftUpOnTap.y
                    val startPoint = Point(x = if (isLeftToRight) -dragXRange.end * 0.5f else dragXRange.end, y = if (isLeftToRight) touchPoint.y - tapYDelta else touchPoint.y)
                    f = viewHeight - touchPoint.y.absoluteValue
                    val endPoint = Point(x = if (isLeftToRight) dragXRange.end else -dragXRange.end * 0.5f, y = if (isLeftToRight) touchPoint.y else touchPoint.y - tapYDelta)
                    animLastPoint = startPoint.copy()
                    animStartAndEndPoint = DragEvent(startPoint.copy(), endPoint.copy())
                    pageState = PageState.Loose
                    animDuration = arrayOf(animEnterDuration, animEnterDuration + animExitDuration)
                    state = State.ExitAnimStart

                    isRightToLeftWhenStart = !isLeftToRight
                    isNextOrPrevious = !isLeftToRight
                    if (isRightToLeftWhenStartLast != isRightToLeftWhenStart) {
                        //since v1.1.0 上一次和这一次起手不一样则清空synthesizedBitmap缓存
//                        controller.bitmapSynthesizer.clearSynthesizedCache()
                    }
    }
}
