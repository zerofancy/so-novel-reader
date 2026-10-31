// Adapted from PTQFlipper 1.1.0 (MIT, Copyright 2023 MPGA).
// See assets/licenses/PTQFlipper.txt.
package ptq.mpga.ptqbookpageview.widget

import android.graphics.LinearGradient
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import ptq.mpga.ptqbookpageview.widget.Line.Companion.theta
import kotlin.math.absoluteValue


@Composable
internal fun CurlEngine.Draw(animFloatRatio: Float) {
    Box(Modifier.fillMaxSize()) {
        if (state == State.Idle) {
            callbacks.contents(this, controller.currentPage) {}
        } else {
            //如果正在动画，则使用动画的animDragEvent，否则使用拖动的
            val dragEvent = when (state) {
                State.EnterAnimStart, State.ExitAnimStart -> {
                    val curPoint = animStartAndEndPoint.originTouchPoint + animStartAndEndPoint.dragDelta * animFloatRatio
                    val animDragEvent = DragEvent(animLastPoint, curPoint)
                    animLastPoint = curPoint
                    animDragEvent
                }

                State.Draggable -> {
                    curDragEvent
                }

                else -> {
                    null
                }
            }

            //用于处理垂直
            var upsideDownChange = false
            var upsideDownDragEvent = DragEvent(Point(0f, 0f), Point(0f, 0f))

            //计算本轮点坐标，如果有状态变化，则舍弃本轮（直接使用上一轮的allPoints）
            val newAllPoints = if (dragEvent != null && !dragEvent.isUnmoved) {
                val _dragEvent = if (upsideDown) dragEvent.getSymmetricalDragEventAbout(lBCPerpendicularBisector) else dragEvent
                when (pageState) {
                    PageState.Loose -> {
                        buildStateLoose(absO.copy(), _dragEvent, Point(viewWidth, viewHeight), f, upsideDown, state) { newState, newTheta, newUpsideDown ->
                            newUpsideDown?.let {
                                upsideDown = it
                                f = viewHeight - f
                                upsideDownChange = true
                                upsideDownDragEvent = if (it) dragEvent.getSymmetricalDragEventAbout(lBCPerpendicularBisector) else dragEvent
                                return@buildStateLoose
                            }
                            newState?.let { pageState = it }
                            newTheta?.let { theta = it }
                        }
                    }

                    PageState.WMin -> {
                        buildStateWMin(absO.copy(), _dragEvent, Point(viewWidth, viewHeight), f) { newState, newTheta ->
                            theta = newTheta
                            pageState = newState
                        }
                    }

                    PageState.ThetaMin -> {
                        buildStateThetaMin(absO.copy(), _dragEvent, Point(viewWidth, viewHeight), f) { newState, newTheta ->
                            theta = newTheta
                            pageState = newState
                        }
                    }

                    PageState.Tight -> {
                        buildStateTight(absO.copy(), Point(viewWidth, viewHeight), theta, _dragEvent, f) { newState ->
                            pageState = newState
                        }
                    }
                }
            } else {
                null
            }

            val distortBitmap = controller.getBitmapCurrent(if (isRightToLeftWhenStart) 1 else 0)
            val backgroundBitmap = controller.getBitmapCurrent(if (isRightToLeftWhenStart) 2 else 1)
            val currentBitmap = if (isRightToLeftWhenStart) distortBitmap else backgroundBitmap

            val nonNullAllPoints = newAllPoints ?: if (upsideDownChange) { //处理垂直抖动，重新计算一遍
                buildStateLoose(absO.copy(), upsideDownDragEvent, Point(viewWidth, viewHeight), f, upsideDown, state) { _, _, _ -> } ?: allPoints
            } else {
                allPoints
            }

            var upsideDownAllPoints = if (upsideDown) nonNullAllPoints.getSymmetricalPointAboutLine(lBCPerpendicularBisector) else nonNullAllPoints

            var (distortedEdges, distortedVertices) = upsideDownAllPoints
                .toCartesianSystem().buildDistortionPoints(viewWidth, viewHeight, bitmapMeshCount.first, bitmapMeshCount.second, upsideDown)

            //如果newAllPoint为空，或者页面完全垂直，则舍弃本轮，使用上一轮的点重新计算
            if (newAllPoints != null) {
                if (distortedEdges[0].isNotEmpty()) {
                    allPoints = newAllPoints
                } else {
                    //newAllPoints不为null意味着使用的是本轮的点计算的，因此重算一次，如果为null则已经用的是allPoints算的了，不需要重算
                    upsideDownAllPoints = if (upsideDown) allPoints.getSymmetricalPointAboutLine(lBCPerpendicularBisector) else allPoints
                    val lastAll = upsideDownAllPoints.toCartesianSystem().buildDistortionPoints(viewWidth, viewHeight, bitmapMeshCount.first, bitmapMeshCount.second, upsideDown)
                    distortedEdges = lastAll.first
                    distortedVertices = lastAll.second
                }
            }

            upsideDownAllPoints.buildPath(distortedEdges, upsideDown, distortedEdgeDownSampling, pathResult)
            val (paths, shadowPaths, shaderControlPointPairs, shadow12Width) = pathResult

            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas {
                    val paint = Paint()
                    val frameworkPaint = paint.asFrameworkPaint()
                    paint.isAntiAlias = true
                    frameworkPaint.isAntiAlias = true
                    val nativeCanvas = it.nativeCanvas
                    //注意图层绘制顺序

                    //点还没计算出来就只画当前
                    if (upsideDownAllPoints.C.x == upsideDownAllPoints.O.x) {
                        nativeCanvas.drawBitmap(currentBitmap, absO.x, absO.y, frameworkPaint)
                        return@drawIntoCanvas
                    }

                    //画当前页
                    /**
                     * @since v1.1.0
                     * 从前的做法是先画底图后画drawBitmapMesh，但drawBitmapMesh方法可能会有闪动，即在底图区域有闪动，因此采用了clip的方式，但clip会导致耗时增加
                     * 因此优化方案是空间换时间，把bitmapMesh画到一个bitmap上，而后和底图进行合成，在native层直接操纵像素
                     */
                    frameworkPaint.shader = null
                    frameworkPaint.color = nativePageColor

                    //接近垂直则不drawBitmapMesh
                    if (((upsideDownAllPoints.W.y - upsideDownAllPoints.Z.y) / viewHeight).absoluteValue < shadow3VerticalThreshold) {
                        //绘制synthesizedBitmap
//                        nativeCanvas.drawBitmap(controller.bitmapSynthesizer.synthesizedBitmap, 0f, 0f, frameworkPaint)
//                        controller.bitmapSynthesizer.synthesize(backgroundBitmap, allPoints.W, allPoints.Z, allPoints.S, upsideDown)
                        nativeCanvas.drawBitmap(backgroundBitmap, 0f, 0f, frameworkPaint)

                        //画阴影区域3，在synthesizedBitmap之上
                        frameworkPaint.shader =
                            LinearGradient(
                                shaderControlPointPairs[2].first.x,
                                shaderControlPointPairs[2].first.y,
                                shaderControlPointPairs[2].second.x,
                                shaderControlPointPairs[2].second.y,
                                nativeShadow3Color,
                                nativeTransparentColor,
                                Shader.TileMode.CLAMP
                            )
                        it.drawPath(shadowPaths[2], paint)

                        //distortionBitmap图层，在阴影区域3图层之上
                        nativeCanvas.drawBitmapMesh(distortBitmap, bitmapMeshCount.first, bitmapMeshCount.second, distortedVertices, 0, null, 0, frameworkPaint)
                    } else {
                        //画底页
//                        nativeCanvas.drawBitmap(controller.bitmapSynthesizer.synthesizedBitmap, 0f, 0f, frameworkPaint)
//                        controller.bitmapSynthesizer.synthesize(backgroundBitmap, allPoints.W, allPoints.Z, allPoints.S, upsideDown)
                        nativeCanvas.drawBitmap(backgroundBitmap, 0f, 0f, frameworkPaint)

                        //画阴影区域3
                        val nextPage = with(upsideDownAllPoints) {
                            val (_, ZTN, WSM, _) = distortedEdges
                            Path().apply {
                                moveTo(C)
                                lineTo(W)
                                connect(WSM, distortedEdgeDownSampling)
                                lineTo(M)
                                lineTo(N)
                                connect(ZTN, distortedEdgeDownSampling, !upsideDown)
                                lineTo(Z)
                                lineTo(C)
                            }
                        }
                        frameworkPaint.shader =
                            LinearGradient(
                                shaderControlPointPairs[2].first.x,
                                shaderControlPointPairs[2].first.y,
                                shaderControlPointPairs[2].second.x,
                                shaderControlPointPairs[2].second.y,
                                nativeShadow3Color,
                                nativeTransparentColor,
                                Shader.TileMode.CLAMP
                            )
                        it.drawPath(shadowPaths[2], paint)
                        it.clipPath(nextPage, ClipOp.Difference)

                        //画当前页
                        frameworkPaint.shader = null
                        frameworkPaint.color = nativePageColor
                        nativeCanvas.drawBitmapMesh(distortBitmap, bitmapMeshCount.first, bitmapMeshCount.second, distortedVertices, 0, null, 0, frameworkPaint)
                    }

                    //画阴影区域1
                    frameworkPaint.shader = LinearGradient(
                        shaderControlPointPairs[0].first.x,
                        shaderControlPointPairs[0].first.y,
                        shaderControlPointPairs[0].second.x,
                        shaderControlPointPairs[0].second.y,
                        nativeShadow12Color,
                        nativeTransparentColor,
                        Shader.TileMode.CLAMP
                    )
                    it.drawPath(shadowPaths[0], paint)

                    //画阴影区域2
                    frameworkPaint.shader = LinearGradient(
                        shaderControlPointPairs[1].first.x,
                        shaderControlPointPairs[1].first.y,
                        shaderControlPointPairs[1].second.x,
                        shaderControlPointPairs[1].second.y,
                        nativeShadow12Color,
                        nativeTransparentColor,
                        Shader.TileMode.CLAMP
                    )
                    it.drawPath(shadowPaths[1], paint)

                    //画阴影区域4（圆弧）
                    frameworkPaint.shader =
                        RadialGradient(shaderControlPointPairs[0].first.x, shaderControlPointPairs[0].first.y, shadow12Width, nativeShadow12Color, nativeTransparentColor, Shader.TileMode.CLAMP)
                    it.drawPath(shadowPaths[3], paint)

                    //画当前页背面
                    //TODO:垂直Path优化
                    frameworkPaint.shader = null
                    frameworkPaint.color = nativePageColor
                    it.drawPath(paths[0], paint)

                    //画光泽左侧阴影
                    frameworkPaint.shader = LinearGradient(
                        shaderControlPointPairs[3].first.x,
                        shaderControlPointPairs[3].first.y,
                        shaderControlPointPairs[3].second.x,
                        shaderControlPointPairs[3].second.y,
                        nativeLustreColor,
                        nativeTransparentColor,
                        Shader.TileMode.CLAMP
                    )
                    it.drawPath(shadowPaths[4], paint)

                    //画光泽右侧阴影
                    frameworkPaint.shader = LinearGradient(
                        shaderControlPointPairs[4].first.x,
                        shaderControlPointPairs[4].first.y,
                        shaderControlPointPairs[4].second.x,
                        shaderControlPointPairs[4].second.y,
                        nativeLustreColor,
                        nativeLustreColor,
                        Shader.TileMode.CLAMP
                    )
                    it.drawPath(shadowPaths[5], paint)

                    //加强一下轮廓
//                    paint.shader = null
//                    paint.color = shadow12Color.copy(alpha = 0.13f)
//                    paint.style = PaintingStyle.Stroke
//                    paint.strokeWidth = 0.5f
//                    it.drawPath(paths[1], paint)

                }
            }

        }
    }
}
