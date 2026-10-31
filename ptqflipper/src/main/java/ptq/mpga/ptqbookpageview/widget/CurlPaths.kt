// Adapted from PTQFlipper 1.1.0 (MIT, Copyright 2023 MPGA).
// See assets/licenses/PTQFlipper.txt.
package ptq.mpga.ptqbookpageview.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.PathOperation
import ptq.mpga.ptqbookpageview.widget.Line.Companion.k
import ptq.mpga.ptqbookpageview.widget.Line.Companion.theta
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sin


internal fun AllPoints.buildPath(distortedEdges: Array<List<Float>>, isUpsideDown: Boolean, distortedEdgeDownSampling: Int, pathResult: PathResult) {
    val (NVJ, ZTN, WSM, MUI) = distortedEdges

    pathResult.recycle()

    /**
     * 只需要pageBack和outline
     * @since v1.1.0
     */
    //thisPageBack
    val thisPageBack = pathResult.pagePaths[0].apply {
        moveTo(M)
        lineTo(N)
        connect(NVJ, distortedEdgeDownSampling, isUpsideDown)
        lineTo(H)
        connect(MUI, distortedEdgeDownSampling, true)
        lineTo(M)
    }

    //outline
    pathResult.pagePaths[1].apply {
        moveTo(W)
        connect(WSM, distortedEdgeDownSampling)
        connect(MUI, distortedEdgeDownSampling)
        lineTo(H)
        lineTo(J)
        connect(NVJ, distortedEdgeDownSampling, !isUpsideDown)
        connect(ZTN, distortedEdgeDownSampling, !isUpsideDown)
    }

    //计算阴影
    val lHF = Line.withTwoPoints(J, H)
    val lHE = Line.withTwoPoints(H, I)
    val WE = (S.x - W.x) * 2
    val WERange = FloatRange(0f, stateTightMinWERatio * C.x)
    val shadow12Range = FloatRange(0f, shadowThreshold)
    val shadow12Width = WERange.linearMapping(WE, shadow12Range)
    val shadow3Width = shadow12Width * shadowPart3to1Ratio

    val H1 = H.getExtensionPointInAbs(lHF, shadow12Width)
    val lIParallel = Line.withKAndOnePoint(lHF.k, I)
    val I1 = I.getExtensionPointInAbs(lIParallel, shadow12Width)
    val lUParallel = Line.withKAndOnePoint(lHF.k, U)
    val U1 = U.getExtensionPointInAbs(lUParallel, shadow12Width)

    val H2 = H.getExtensionPointInAbs(lHE, shadow12Width)
    val lJParallel = Line.withKAndOnePoint(lHE.k, J)
    val J1 = J.getExtensionPointInAbs(lJParallel, shadow12Width)
    val lVParallel = Line.withKAndOnePoint(lHE.k, V)
    val V1 = V.getExtensionPointInAbs(lVParallel, shadow12Width)

    val lST = Line.withTwoPoints(S, T)
    val S1 = if (!lST.k.isNaN()) Point(S.x + shadow3Width / ((1 - 1 / (1 + lST.k * lST.k)).pow(0.5f)), C.y) else Point(S.x + shadow3Width, C.y)
    val T1 = if (!lST.k.isNaN()) Point(C.x, Line.withKAndOnePoint(lST.k, S1).y(C.x)) else Point(S1.x, O.y)

    //shadow1
    pathResult.shadowPaths[0].apply {
        moveTo(H2)
        lineTo(J1)
        quadraticBezierTo(V1, N)
        quadraticBezierTo(V, J)
        lineTo(H)
        close()
    }

    //圆弧 shadow4
    pathResult.shadowPaths[3].apply {
        moveTo(H1)
        //处理翻转
        if (C.y == 0f) {
            arcTo(Rect(H.toOffset, shadow12Width), lHF.theta().toDeg() + 180f, -90f, true)
        } else {
            arcTo(Rect(H.toOffset, shadow12Width), lHF.theta().toDeg(), 90f, true)
        }
        lineTo(H)
        close()
    }

    //shadow2
    pathResult.shadowPaths[1].apply {
        moveTo(H)
        lineTo(H1)
        lineTo(I1)
        quadraticBezierTo(U1, M)
        quadraticBezierTo(U, I)
        close()
    }

    //shadow3
    pathResult.shadowPaths[2].apply {
        moveTo(W)
        lineTo(S1)
        //若接近垂直，则直接画成矩形，否则画梯形
        if (((T1.y - O.y) / (C.y - O.y)).absoluteValue > shadow3VerticalThreshold) {
            lineTo(S1.copy(y = (C.y - O.y).absoluteValue - S1.y))
            lineTo(W.copy(y = (C.y - O.y).absoluteValue - W.y))
        } else {
            /**
             * @since v1.1.0 越界绘制优化：如果Z在BC内，则直接画线，否则求交点
             */
            //给一组log数据供参考
            //buildPath: C.y:0 O.y:1600 upsideDown:true W: Point(x=376.90134, y=0.0) S1: Point(x=523.4354, y=0.0) T1: Point(x=720.0, y=36051.1) Z: Point(x=720.0, y=62926.297)
            //buildPath: C:1600.0 O.y:0 upsideDown:false W: Point(x=380.06815, y=1600.0) S1: Point(x=526.1546, y=1600.0) T1: Point(x=720.0, y=-46201.938) Z: Point(x=720.0, y=-82226.625)
            val S1T1_OBx = Line.withKAndOnePoint(lST.k, S1).x(O.y) //S1T1交OB的x坐标
            val WZ_OBx = Line.withKAndOnePoint(lST.k, W).x(O.y)
            lineTo(if (S1T1_OBx > C.x) T1 else Point(S1T1_OBx, O.y))
            lineTo(if (S1T1_OBx > C.x) Z else Point(WZ_OBx, O.y))
        }
        close()
    }

    //光泽计算
    val lustreStartRange = FloatRange(0f, lustreStartMinDistance)
    val lustreEndRange = FloatRange(0f, lustreEndMinDistance)
    val lustreEndShadowWidthRange = FloatRange(0f, lustreEndShadowMinWidth)
    val lustreStartDistance = WERange.linearMapping(WE, lustreStartRange).run { minOf(this, lustreStartMaxDistance) }
    val lustreEndDistance = WERange.linearMapping(WE, lustreEndRange).run { minOf(this, lustreEndMaxDistance) }
    val lustreEndShadowWidth = WERange.linearMapping(WE, lustreEndShadowWidthRange).run { minOf(this, lustreEndShadowMaxWidth) }

    val S4 = if (!lST.k.isNaN()) Point(S.x - lustreStartDistance / ((1 - 1 / (1 + lST.k * lST.k)).pow(0.5f)), C.y) else Point(S.x - lustreStartDistance, C.y)
    val T4 = if (!lST.k.isNaN()) Point(C.x, Line.withKAndOnePoint(lST.k, S4).y(C.x)) else Point(S4.x, O.y)
    val S2 = if (!lST.k.isNaN()) Point(S4.x - lustreEndDistance / ((1 - 1 / (1 + lST.k * lST.k)).pow(0.5f)), C.y) else Point(S4.x - lustreEndDistance, C.y)
    val T2 = if (!lST.k.isNaN()) Point(C.x, Line.withKAndOnePoint(lST.k, S2).y(C.x)) else Point(S2.x, O.y)
    val S3 = if (!lST.k.isNaN()) Point(S2.x - lustreEndShadowWidth / ((1 - 1 / (1 + lST.k * lST.k)).pow(0.5f)), C.y) else Point(S2.x - lustreEndShadowWidth, C.y)
    val T3 = if (!lST.k.isNaN()) Point(C.x, Line.withKAndOnePoint(lST.k, S3).y(C.x)) else Point(S3.x, O.y)
    val lS2T2 = Line.withKAndOnePoint(lST.k, S2)
    val lS3T3 = Line.withKAndOnePoint(lST.k, S3)
    val lS4T4 = Line.withKAndOnePoint(lST.k, S4)

    //lustreEndShadow
    pathResult.shadowPaths[4].apply {
        if (lustreEndDistance + lustreEndShadowWidth > H.distanceTo(lST)) {
            moveTo(O)
            close()
            return@apply
        }
        moveTo(S2)
        lineTo(T2)
        lineTo(T3)
        lineTo(S3)
        close()
        op(this, thisPageBack, operation = PathOperation.Intersect)
    }

    //lustreStartShadow
    pathResult.shadowPaths[5].apply {
        if (lustreEndDistance + lustreEndShadowWidth > H.distanceTo(lST)) {
            moveTo(O)
            close()
            return@apply
        }
        moveTo(S)
        lineTo(T)
        lineTo(T4)
        lineTo(S4)
        close()
        op(this, thisPageBack, operation = PathOperation.Intersect)
    }

    //求阴影控制点
    val lKL = Line.withKAndOnePoint(lST.k, W)
    val lS1T1 = Line.withKAndOnePoint(lST.k, S1)
    val lHC = Line.withTwoPoints(H, C)

    pathResult.shaderControlPointPairs[0] = Pair(H.avoidNaN(), H2.avoidNaN())
    pathResult.shaderControlPointPairs[1] = Pair(H.avoidNaN(), H1.avoidNaN())
    pathResult.shaderControlPointPairs[2] = Pair(lKL.intersectAt(lHC).avoidNaN(), lS1T1.intersectAt(lHC).avoidNaN())
    pathResult.shaderControlPointPairs[3] = Pair(lS2T2.intersectAt(lHC).avoidNaN(), lS3T3.intersectAt(lHC).avoidNaN())
    pathResult.shaderControlPointPairs[4] = Pair(lST.intersectAt(lHC).avoidNaN(), lS4T4.intersectAt(lHC).avoidNaN())

    pathResult.shadow12Width = shadow12Width
//    return PathResult(
//        listOf(thisPageBack, outline),
//        listOf(shadow1, shadow2, shadow3, shadow4, lustreEndShadow, lustreStartShadow),
//        listOf(
//            Pair(
//                H.avoidNaN(),
//                H2.avoidNaN()
//            ),
//            Pair(
//                H.avoidNaN(),
//                H1.avoidNaN()
//            ),
//            Pair(
//                lKL.intersectAt(lHC).avoidNaN(),
//                lS1T1.intersectAt(lHC).avoidNaN()
//            ),
//            Pair(
//                lS2T2.intersectAt(lHC).avoidNaN(),
//                lS3T3.intersectAt(lHC).avoidNaN()
//            ),
//            Pair(
//                lST.intersectAt(lHC).avoidNaN(),
//                lS4T4.intersectAt(lHC).avoidNaN()
//            )
//        ),
//        shadow12Width
//    )
}

/**
 * 获取Bitmap扭曲后的格点，相对坐标系
 * @return 第一个数组表示扭曲后边界格点[NVJ, ZTN, WSM, MUI]，第二个数组表示扭曲后格点，第三个数组表示扭曲格子数[宽，高]
 */
internal fun AllPoints.buildDistortionPoints(
    width: Float,
    height: Float,
    meshWidthCount: Int,
    meshHeightCount: Int,
    isUpsideDown: Boolean
): Pair<Array<List<Float>>, FloatArray> {
    val size = (meshWidthCount + 1) * (meshHeightCount + 1) * 2
    val vertices = FloatArray(size)
    val originalVertices = FloatArray(size)

    val kCH = Line.withTwoPoints(C, H).k
    val lWZ = Line.withKAndOnePoint(-1 / kCH, W)
    val Jr = Point(C.x, J.y + kCH * (C.x - J.x))
    val lIrJr = Line.withKAndOnePoint(lWZ.k, Jr)
    val Ir = Point(lIrJr.x(C.y), C.y)
    val lJH = Line.withTwoPoints(J, H)
    val lIH = Line.withTwoPoints(I, H)
    val F = Point(C.x, lJH.y(C.x))
    val E = Point(lIH.x(C.y), C.y)
    val delta = N.distanceTo(lWZ) * PI.toFloat() / J.distanceTo(Jr)
    val r = N.distanceTo(lWZ)
    val absC = C.toAbsoluteSystem()
    val absH = H.toAbsoluteSystem()
    val kCHAbsolute = Line.withTwoPoints(absC, absH).k

    var index = 0
    (0..meshHeightCount).forEach { i ->
        val y = height * i / meshHeightCount
        (0..meshWidthCount).forEach { j ->
            val x = width * j / meshWidthCount
            //vertices格式为[x1,y1] [x2,y2] [x3,y3]
            val xIndex = index * 2
            vertices[xIndex] = x
            vertices[xIndex + 1] = y
            originalVertices[xIndex] = x
            originalVertices[xIndex + 1]
            index++
        }
    }

    val ZTNPoints = mutableListOf<Float>()
    val NVJPoints = mutableListOf<Float>()
    val WSMPoints = mutableListOf<Float>()
    val MUIPoints = mutableListOf<Float>()
    val NVJRange = FloatRange(Jr.y, F.y)
    val ZTNRange = FloatRange(F.y, Z.y)
    val WSMRange = FloatRange(E.x, W.x)
    val MUIRange = FloatRange(Ir.x, E.x)

    val bottomMinXIndex = (meshWidthCount + 1) * meshHeightCount * 2

    (0 until size / 2).forEach { i ->
        val xIndex = i + i
        val yIndex = xIndex + 1
        val x = vertices[xIndex]
        val y = vertices[yIndex]

        val absG = Point(x, y)
        val G = Point(x, y).toCartesianSystem()

        if (!isUpsideDown) {
            if (!G.isBelow(lWZ)) return@forEach
        } else {
            if (!G.isAbove(lWZ)) return@forEach
        }

        val d = G.distanceTo(lWZ)
        val absQ = absG.getExtensionPointInAbs(Line.withKAndOnePoint(kCHAbsolute, absG), d - r * sin(d * delta / r))

        //如果是最右侧边界点
        if ((xIndex / 2 + 1) % (meshWidthCount + 1) == 0) {
            when {
                NVJRange.contains(G.y) -> {
                    NVJPoints.apply {
                        add(absQ.x)
                        add(absQ.y)
                    }
                }

                ZTNRange.contains(G.y) -> {
                    ZTNPoints.apply {
                        add(absQ.x)
                        add(absQ.y)
                    }
                }
            }
        }
        //如果是最下（上）侧边界点
        else if (if (!isUpsideDown) xIndex >= bottomMinXIndex else xIndex <= 2 * meshWidthCount) {
            when {
                WSMRange.contains(G.x) -> {
                    WSMPoints.apply {
                        add(absQ.x)
                        add(absQ.y)
                    }
                }

                MUIRange.contains(G.x) -> {
                    MUIPoints.apply {
                        add(absQ.x)
                        add(absQ.y)
                    }
                }
            }
        }

        vertices[xIndex] = absQ.x
        vertices[yIndex] = absQ.y
    }

    return Pair(arrayOf(NVJPoints, ZTNPoints, WSMPoints, MUIPoints), vertices)
}
