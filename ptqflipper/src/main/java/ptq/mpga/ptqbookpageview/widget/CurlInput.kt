// Adapted from PTQFlipper 1.1.0 (MIT, Copyright 2023 MPGA).
// See assets/licenses/PTQFlipper.txt.
package ptq.mpga.ptqbookpageview.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import ptq.mpga.ptqbookpageview.widget.Line.Companion.theta
import kotlin.math.absoluteValue


internal fun CurlEngine.inputModifier(): Modifier {
    return Modifier
        .fillMaxSize()
        .pointerInput(
            localConfig,
            dragXRange,
            leftUpOnTap,
            absC
        ) {
            detectTapGestures { touchOffset: Offset ->
                if (state != State.Idle || !controller.isRenderOk() || localConfig.disabled) {
                    return@detectTapGestures
                }

                val touchPoint = touchOffset.toPoint

                if (!dragXRange.contains(touchPoint.x)) {
                    return@detectTapGestures
                }

                val tapYDelta = leftUpOnTap.y

                //最顶上预留tapYDelta的距离
                if (touchPoint.y > tapYDelta) {
                    val onTapBehavior = callbacks.tapBehavior

                    val isLeftToRight = if (onTapBehavior == null) {
                        touchPoint.x < 0.5f * viewWidth
                    } else {
                        val result = onTapBehavior(leftUpOnTap.copy(), absC.copy(), touchPoint.copy())

                        if (result == null) {
                            return@detectTapGestures
                        } else {
                            !result
                        }
                    }

                    if (controller.currentPage >= controller.totalPage - 1 && !isLeftToRight) {
                        onNext()
                        return@detectTapGestures
                    }

                    if (controller.currentPage <= 0 && isLeftToRight) {
                        onPrevious()
                        return@detectTapGestures
                    }

                    animateTap(isLeftToRight, touchPoint)
                }
            }
        }
        .pointerInput(
            localConfig,
            dragXRange,
            leftUpOnTap,
            absC,
            interruptedInDrag,
        ) {
            detectDragGestures(
                onDragStart = { viewSystemOffset: Offset ->
                    if (state != State.Idle || !controller.isRenderOk() || localConfig.disabled) {
                        return@detectDragGestures
                    }

                    val touchPoint = viewSystemOffset.toPoint

                    if (!dragXRange.contains(touchPoint.x)) {
                        return@detectDragGestures
                    }

                    dragInitialPoint = touchPoint
                    pageState = PageState.Loose
                    interruptedInDrag = false
                    curDragEvent = DragEvent(touchPoint, touchPoint)
                    f = viewHeight - touchPoint.y.absoluteValue
                    animStartAndEndPoint = animStartAndEndPoint.copy(originTouchPoint = touchPoint)
                    time = System.currentTimeMillis()
                },
                onDrag = { _: PointerInputChange, dragAmount: Offset ->
                    if (!controller.isRenderOk() || localConfig.disabled) {
                        return@detectDragGestures
                    }

                    val cur = (curDragEvent.currentTouchPoint + dragAmount.toPoint)
                    curDragEvent = DragEvent(curDragEvent.currentTouchPoint.copy(), cur)

                    if (state == State.Idle) {
                        if (System.currentTimeMillis() - time > animStartTimeout) {
                            if (animStartAndEndPoint
                                    .directionToOInCartesianSystem()
                                    .isIn(DragDirection.Up, DragDirection.Down, DragDirection.Static)
                            ) {
                                return@detectDragGestures
                            }

                            animStartAndEndPoint = animStartAndEndPoint.copy(currentTouchPoint = cur)

                            val responseDragWhen = callbacks.responseDragWhen

                            //手指从右向左翻，还是从左向右
                            val isRightToLeft = if (responseDragWhen == null) {
                                animStartAndEndPoint.currentTouchPoint.x < animStartAndEndPoint.originTouchPoint.x
                            } else {
                                responseDragWhen(absC.copy(), animStartAndEndPoint.originTouchPoint.copy(), cur.copy())
                            }

                            if (isRightToLeft == null) {
                                interruptedInDrag = true
                                return@detectDragGestures
                            }

                            isRightToLeftWhenStart = isRightToLeft
                            if (isRightToLeftWhenStartLast != isRightToLeftWhenStart) {
                                //since v1.1.0 上一次和这一次起手不一样则清空synthesizedBitmap缓存
//                                controller.bitmapSynthesizer.clearSynthesizedCache()
                            }

                            //如果是第一页或者最后一页，直接return，之后松手就会触发onDragEnd，且state=State.Idle
                            if ((isRightToLeft && controller.currentPage >= controller.totalPage - 1) ||
                                (!isRightToLeft && controller.currentPage <= 0)
                            ) {
                                turnPageRequestWhenDrag = true
                                return@detectDragGestures
                            }

                            animStartAndEndPoint.originTouchPoint.x = if (isRightToLeft) dragXRange.end else dragXRange.start

                            //默认向上翻，如果向下则颠倒
                            val isUpToDown = animStartAndEndPoint.currentTouchPoint.y > animStartAndEndPoint.originTouchPoint.y
                            if (isUpToDown) {
                                upsideDown = true
                                f = viewHeight - f
                            }
                            animLastPoint = animStartAndEndPoint.originTouchPoint.copy()
                            state = State.EnterAnimStart
                        }
                    }

                    if (state == State.Draggable) {
                        if (!dragXRange.contains(cur.x)) {
                            interruptedInDrag = true
                            onDragEnd()
                            return@detectDragGestures
                        }

                        val dragEvent = if (upsideDown) curDragEvent.getSymmetricalDragEventAbout(lBCPerpendicularBisector) else curDragEvent

                        if (pageState == PageState.Tight) {
                            theta = onDragWhenTightState(f, theta, dragEvent, viewHeight, viewWidth)
                        }
                    }
                },
                onDragEnd = {
                    if (interruptedInDrag || localConfig.disabled) {
                        return@detectDragGestures
                    }

                    onDragEnd()
                }
            )
        }
        .background(color = Color.Transparent)

}
