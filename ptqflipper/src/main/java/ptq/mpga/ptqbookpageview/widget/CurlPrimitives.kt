// Adapted from PTQFlipper 1.1.0 (MIT, Copyright 2023 MPGA).
// See assets/licenses/PTQFlipper.txt.
package ptq.mpga.ptqbookpageview.widget

import androidx.annotation.ColorLong
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import ptq.mpga.ptqbookpageview.widget.Line.Companion.k
import ptq.mpga.ptqbookpageview.widget.Line.Companion.theta
import kotlin.math.PI
import kotlin.math.absoluteValue
import kotlin.math.atan
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan


internal fun DragEvent.getSymmetricalDragEventAbout(line: Line): DragEvent {
    val origin = originTouchPoint.getSymmetricalPointAbout(line)
    val current = currentTouchPoint.getSymmetricalPointAbout(line)
    return copy(originTouchPoint = origin, currentTouchPoint = current)
}

internal data class PathResult(val pagePaths: List<Path>, val shadowPaths: List<Path>, val shaderControlPointPairs: MutableList<Pair<Point, Point>>, var shadow12Width: Float) {
    fun recycle() {
        pagePaths.forEach {
            it.reset()
        }
        shadowPaths.forEach {
            it.reset()
        }
    }
}

internal data class DragEvent(val originTouchPoint: Point, val currentTouchPoint: Point) {
    val dragDelta get() = currentTouchPoint - originTouchPoint

    fun toCartesianSystem(): DragEvent {
        val newOrigin = originTouchPoint.toCartesianSystem()
        val newCur = currentTouchPoint.toCartesianSystem()
        return DragEvent(newOrigin, newCur)
    }

    val isUnmoved get() = currentTouchPoint.x == originTouchPoint.x && currentTouchPoint.y == originTouchPoint.y

    fun directionToLineInCartesianSystem(line: Line): DragDirection = when {
        line.k == 0f -> directionToOInCartesianSystem()
        line.k > 0f -> {
            val lParallel = Line.withKAndOnePoint(line.k, originTouchPoint)
            val lVertical = Line.withKAndOnePoint(-1 / line.k, originTouchPoint)
            currentTouchPoint.run {
                when {
                    isOn(lParallel) && isAbove(lVertical) -> DragDirection.Right
                    isAbove(lParallel) && isAbove(lVertical) -> DragDirection.RightUp
                    isAbove(lParallel) && isOn(lVertical) -> DragDirection.Up
                    isAbove(lParallel) && isBelow(lVertical) -> DragDirection.LeftUp
                    isOn(lParallel) && isBelow(lVertical) -> DragDirection.Left
                    isBelow(lParallel) && isBelow(lVertical) -> DragDirection.LeftDown
                    isBelow(lParallel) && isOn(lVertical) -> DragDirection.Down
                    isBelow(lParallel) && isAbove(lVertical) -> DragDirection.RightDown
                    else -> DragDirection.Static
                }
            }
        }

        line.k < 0f -> {
            val lParallel = Line.withKAndOnePoint(line.k, originTouchPoint)
            val lVertical = Line.withKAndOnePoint(-1 / line.k, originTouchPoint)
            currentTouchPoint.run {
                when {
                    isOn(lParallel) && isBelow(lVertical) -> DragDirection.Right
                    isAbove(lParallel) && isBelow(lVertical) -> DragDirection.RightUp
                    isAbove(lParallel) && isOn(lVertical) -> DragDirection.Up
                    isAbove(lParallel) && isAbove(lVertical) -> DragDirection.LeftUp
                    isOn(lParallel) && isAbove(lVertical) -> DragDirection.Left
                    isBelow(lParallel) && isAbove(lVertical) -> DragDirection.LeftDown
                    isBelow(lParallel) && isOn(lVertical) -> DragDirection.Down
                    isBelow(lParallel) && isBelow(lVertical) -> DragDirection.RightDown
                    else -> DragDirection.Static
                }
            }
        }

        else -> DragDirection.Static
    }

    fun directionToOInCartesianSystem(): DragDirection {
        return dragDelta.run {
            when {
                x > 0f && y == 0f -> DragDirection.Right
                x > 0f && y > 0f -> DragDirection.RightUp
                x == 0f && y > 0f -> DragDirection.Up
                x < 0f && y > 0f -> DragDirection.LeftUp
                x < 0f && y == 0f -> DragDirection.Left
                x < 0f && y < 0f -> DragDirection.LeftDown
                x == 0f && y < 0f -> DragDirection.Down
                x > 0f && y < 0f -> DragDirection.RightDown
                else -> DragDirection.Static
            }
        }
    }
}

internal enum class DragDirection {
    Right, RightUp, Up, LeftUp, Left, LeftDown, Down, RightDown, Static
}

internal fun DragDirection.isIn(vararg directions: DragDirection) = directions.contains(this)

data class Point(var x: Float, var y: Float) {
    operator fun minus(a: Point) = Point(x - a.x, y - a.y)

    operator fun plus(a: Point) = Point(x + a.x, y + a.y)

    operator fun times(m: Float) = Point(x * m, y * m)

    operator fun rangeTo(a: Point) = Point(0.5f * (x + a.x), 0.5f * (y + a.y))

    //这两个函数一样，但是还是分成两个函数写，在调用时语义更明确
    //相对系坐标转换为相对系坐标，y坐标相反
    internal fun toAbsoluteSystem() = Point(x, -y)

    //绝对系坐标转换为相对系坐标，y坐标相反
    internal fun toCartesianSystem() = Point(x, -y)

    companion object {
        val Zero = Point(0f, 0f)
    }
}

internal fun Point.getSymmetricalPointAbout(line: Line) = with(line) {
    if (k == 0f) {
        Point(x, 2 * b - y)
    } else {
        Point(x - 2 * k * (k * x - y + b) / (k * k + 1), y + 2 * (k * x - y + b) / (k * k + 1))
    }
}

internal fun Point.distanceTo(anotherPoint: Point) = sqrt((x - anotherPoint.x).pow(2) + (y - anotherPoint.y).pow(2))

internal fun Point.distanceTo(line: Line) = (line.k * x - y + line.b).absoluteValue / (1 + line.k.pow(2)).pow(0.5f)

internal fun Point.isBelow(line: Line) = line.k * x + line.b > y

internal fun Point.isAbove(line: Line) = line.k * x + line.b < y

internal fun Point.isOn(line: Line) = line.k * x + line.b == y

internal val Offset.toPoint get() = Point(x, y)
internal val Point.toOffset get() = Offset(x, y)

internal fun Path.moveTo(p: Point?) = p?.let { this.moveTo(p.x, p.y) }
internal fun Path.lineTo(p: Point?) = p?.let { this.lineTo(p.x, p.y) }
internal fun Path.quadraticBezierTo(controlPoint: Point?, endPoint: Point?) {
    if (controlPoint != null && endPoint != null) {
        this.quadraticTo(controlPoint.x, controlPoint.y, endPoint.x, endPoint.y)
    }
}

/**
 * 连接所有点得到边缘类贝塞尔曲线
 * 在一般的情况下，绘制时不需要考虑是否upsideDown的问题，因为upsideDownAllPoints中已经是翻转好了的待绘制的点
 * 但connect时仍需要手动处理翻转，这是因为在计算 @param points 时，是无论upsideDown与否都是从上往下按顺序计算的，这之中引入了翻转问题
 */
internal fun Path.connect(points: List<Float>, distortedEdgeDownSampling: Int, reverse: Boolean = false) {
    val size = points.size / 2
    /**
     * @since v1.1.0 下采样以提高绘制性能
     */
    val downSampling = maxOf(size / distortedEdgeDownSampling, 1)

    (0 until size step downSampling).forEach { i ->
        val xIndex = i + i
        if (!reverse) {
            lineTo(points[xIndex], points[xIndex + 1])
        } else {
            val rXIndex = size + size - xIndex - 2
            lineTo(points[rXIndex], points[rXIndex + 1])
        }
    }
}

//如果有NaN就置0
internal fun Point.avoidNaN() = if (x.isNaN() || y.isNaN()) Point(0f, 0f) else this

/**
 * y = kx + b
 */
internal data class Line(val k: Float, val b: Float) {
    fun intersectAt(other: Line) = Point(0f, 0f).apply {
        x = (other.b - b) / (k - other.k)
        y = k * x + b
    }

    fun y(x: Float) = k * x + b

    fun x(y: Float) = (y - b) / k

    //相对系
    fun theta() = theta(k)

    companion object {
        //两点式
        fun withTwoPoints(m: Point, n: Point): Line {
            val k = (n.y - m.y) / (n.x - m.x)
            val b = m.y - k * m.x
            return Line(k, b)
        }

        //点斜式
        fun withKAndOnePoint(k: Float, p: Point): Line {
            return Line(k, p.y - k * p.x)
        }

        //斜率=>倾斜角（弧度制）
        fun theta(k: Float) = atan(k).let { if (it < 0) PI.toFloat() + it else it }

        //倾斜角=>斜率（弧度制）
        fun k(theta: Float) = tan(theta)
    }
}

//ax²+bx+c=0
internal data class QuadraticEquationWithOneUnknown(val a: Float, val b: Float, val c: Float) {
    fun solve(): Array<Float> {
        val delta = b * b - 4 * a * c
        return if (delta < 0) emptyArray() else arrayOf((-b + sqrt(delta)) / (2 * a), (-b - sqrt(delta)) / (2 * a))
    }
}

internal fun Float.toRad() = ((this * PI) / 180).toFloat()
internal fun Float.toDeg() = (this * 180 / PI).toFloat()

//获取一个点在同一直线方向上延伸d的另一个点，d大于0则向左(若line的斜率不存在，则向下)延伸，屏幕绝对坐标系
internal fun Point.getExtensionPointInAbs(line: Line, d: Float): Point {
    if (line.k.isNaN()) return Point(x, y + d)

    val newX = x - d / (1 + line.k * line.k).pow(0.5f)
    return Point(newX, line.y(newX))
}

//private fun Bitmap.synthsize(
//    lower: Bitmap,
//    upper: Bitmap,
//    allPoints: AllPoints
//) {
//    val Z = allPoints.Z
//    val W = allPoints.W
//    val lWZ = Line.withTwoPoints(Z, W)
//    val kZWReciprocal = 1 / lWZ.k
//    val zwBufferSize = height - maxOf(0, Z.y.toInt())
//    val zwBufferOffset = height - zwBufferSize
//
//    for (i in zwBufferOffset until height) {
//        zwBuffer[i] = ((i - lWZ.b) * kZWReciprocal).toInt()
//    }
//
//    //两次拷贝，很浪费
//    for (i in 0 until height) {
//        for (j in 0 until width) {
//            val index = width * i + j
//            buffer[index] = if (j <= zwBuffer[i]) upper.getPixel(j, i) else lower.getPixel(j, i)
//        }
//    }
//    setPixels(buffer, 0, width, 0, 0, width, height)
//}

internal data class FloatRange(val start: Float, val end: Float) {
    fun constraints(value: Float) = when {
        end in start..value -> end
        start in value..end -> start
        end in value..start -> end
        start in end..value -> start
        else -> value
    }

    fun linearMapping(nowValue: Float, newRange: FloatRange): Float {
        val min2 = newRange.start
        val max2 = newRange.end
        return min2 + (max2 - min2) * (nowValue - start) / (end - start)
    }

    fun linearMappingWithConstraints(nowValue: Float, newRange: FloatRange): Float {
        return when {
            end in start..nowValue -> newRange.end
            start in nowValue..end -> newRange.start
            end in nowValue..start -> newRange.end
            start in end..nowValue -> newRange.start
            else -> linearMapping(nowValue, newRange)
        }
    }

    fun contains(value: Float) = value in start..end || value in end..start
}

internal data class AllPoints(
    val O: Point,
    val A: Point,
    val B: Point,
    val C: Point,
    val H: Point,
    val I: Point,
    val J: Point,
    val M: Point,
    val N: Point,
    val S: Point,
    val T: Point,
    val U: Point,
    val V: Point,
    val W: Point,
    val Z: Point
) {
    fun toAbsoluteSystem() = AllPoints(
        O.toAbsoluteSystem(),
        A.toAbsoluteSystem(),
        B.toAbsoluteSystem(),
        C.toAbsoluteSystem(),
        H.toAbsoluteSystem(),
        I.toAbsoluteSystem(),
        J.toAbsoluteSystem(),
        M.toAbsoluteSystem(),
        N.toAbsoluteSystem(),
        S.toAbsoluteSystem(),
        T.toAbsoluteSystem(),
        U.toAbsoluteSystem(),
        V.toAbsoluteSystem(),
        W.toAbsoluteSystem(),
        Z.toAbsoluteSystem()
    )

    companion object {
        fun default(absO: Point) = AllPoints(
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy(),
            absO.copy()
        )
    }

    fun toCartesianSystem() = AllPoints(
        O,
        A.toCartesianSystem(),
        B.toCartesianSystem(),
        C.toCartesianSystem(),
        H.toCartesianSystem(),
        I.toCartesianSystem(),
        J.toCartesianSystem(),
        M.toCartesianSystem(),
        N.toCartesianSystem(),
        S.toCartesianSystem(),
        T.toCartesianSystem(),
        U.toCartesianSystem(),
        V.toCartesianSystem(),
        W.toCartesianSystem(),
        Z.toCartesianSystem()
    )

    fun getSymmetricalPointAboutLine(line: Line) = with(line) {
        AllPoints(
            O.getSymmetricalPointAbout(this),
            A.getSymmetricalPointAbout(this),
            B.getSymmetricalPointAbout(this),
            C.getSymmetricalPointAbout(this),
            H.getSymmetricalPointAbout(this),
            I.getSymmetricalPointAbout(this),
            J.getSymmetricalPointAbout(this),
            M.getSymmetricalPointAbout(this),
            N.getSymmetricalPointAbout(this),
            S.getSymmetricalPointAbout(this),
            T.getSymmetricalPointAbout(this),
            U.getSymmetricalPointAbout(this),
            V.getSymmetricalPointAbout(this),
            W.getSymmetricalPointAbout(this),
            Z.getSymmetricalPointAbout(this)
        )
    }
}

fun toArgb(@ColorLong color: Long): Int {
    return (color shr 32).toInt()
}
