// Adapted from PTQFlipper 1.1.0 (MIT, Copyright 2023 MPGA).
// See assets/licenses/PTQFlipper.txt.
package ptq.mpga.ptqbookpageview.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import ptq.mpga.ptqbookpageview.widget.Line.Companion.k
import ptq.mpga.ptqbookpageview.widget.Line.Companion.theta
import kotlin.math.PI


internal inline fun buildStateLoose(
    absO: Point,
    dragEvent: DragEvent,
    absC: Point,
    f: Float,
    upsideDown: Boolean,
    state: State,
    changeState: (newPageState: PageState?, theta: Float?, upsideDown: Boolean?) -> Unit
): AllPoints? {
    val (theta, points) = algorithmStateLoose(absO, dragEvent.originTouchPoint, absC, f)

    val dragEventCartesian = dragEvent.toCartesianSystem()
    val lHF = Line.withTwoPoints(points.H, dragEventCartesian.originTouchPoint)
    val dragDirection = dragEventCartesian.directionToLineInCartesianSystem(lHF)
    val flipDragDirection = dragEventCartesian.directionToOInCartesianSystem()
    val kJH = Line.withTwoPoints(points.J, points.H).k

    if (dragDirection == DragDirection.Static || kJH.isNaN()) return null

    if (state == State.EnterAnimStart || state == State.Draggable) {
        when {
            //翻转
            kJH <= 0 && flipDragDirection.isIn(DragDirection.Down, DragDirection.RightDown, DragDirection.LeftDown, DragDirection.Left, DragDirection.Right) -> {
                changeState(null, null, !upsideDown)
                return null
            }

            points.W.x < minWxRatio * points.C.x && dragDirection.isIn(DragDirection.Up, DragDirection.RightUp, DragDirection.LeftUp, DragDirection.LeftDown) -> {
                changeState(PageState.WMin, theta, null)
                return null
            }

            theta < minTheta && dragDirection.isIn(DragDirection.Up, DragDirection.RightUp, DragDirection.Right, DragDirection.RightDown) -> {
                changeState(PageState.ThetaMin, minTheta, null)
                return null
            }
        }
    }

    return points.toAbsoluteSystem()
}

internal fun algorithmStateLoose(absO: Point, absR: Point, absC: Point, f: Float): Pair<Float, AllPoints> {
    val C = absC.toCartesianSystem()
    val A = Point(absO.x, C.y)
    val B = Point(C.x, absO.y)
    val R = absR.toCartesianSystem()

    val Rf = Point(C.x, C.y + f)
    val k = (C.x - R.x) / (R.y - Rf.y)

    val lWZ = Line.withKAndOnePoint(k, R)
    val W = Point(lWZ.x(C.y), C.y)
    val Z = Point(C.x, lWZ.y(C.x))

    val lEF = Line.withKAndOnePoint(k, R..Rf)
    val E = Point(lEF.x(C.y), C.y)
    val F = Point(C.x, lEF.y(C.x))

    val S = W..E
    val T = Z..F

    val lCH = Line.withKAndOnePoint(-1 / k, C)

    val P = lEF.intersectAt(lCH)
    val H = Point(2 * P.x - C.x, 2 * P.y - C.y)

    val lHE = Line.withTwoPoints(E, H)
    val lHF = Line.withTwoPoints(F, H)
    val lST = Line.withKAndOnePoint(k, S)
    val J = R.copy()
    val I = lHE.intersectAt(lWZ)
    val U = lHE.intersectAt(lST)
    val V = lHF.intersectAt(lST)

    val M = U..S
    val N = T..V

    val allPoints = AllPoints(absO, A, B, C, H, I, J, M, N, S, T, U, V, W, Z)

    return Pair(lCH.theta(), allPoints)
}

internal inline fun buildStateWMin(absO: Point, dragEvent: DragEvent, absC: Point, f: Float, changeState: (newPageState: PageState, theta: Float) -> Unit): AllPoints? {
    val (theta, WE, points) = algorithmStateWMin(absO, dragEvent.originTouchPoint, absC, f)
    val R = dragEvent.currentTouchPoint.toCartesianSystem()
    val looseWx = Line.withKAndOnePoint((points.C.x - R.x) / (R.y - Point(points.C.x, points.C.y + f).y), R).x(points.C.y)
    val minWE = stateTightMinWERatio * points.C.x

    val dragEventCartesian = dragEvent.toCartesianSystem()
    val lHF = Line.withTwoPoints(points.H, dragEventCartesian.originTouchPoint)
    val dragDirection = dragEventCartesian.directionToLineInCartesianSystem(lHF)

    when {
        WE < minWE && dragDirection.isIn(DragDirection.Up, DragDirection.RightUp, DragDirection.Right, DragDirection.LeftUp) -> {
            changeState(PageState.Tight, maxOf(minTheta, theta))
            return null
        }

        (looseWx >= minWxRatio * points.C.x) && dragDirection.isIn(DragDirection.LeftDown, DragDirection.Down, DragDirection.RightDown, DragDirection.Left) -> {
            changeState(PageState.Loose, maxOf(minTheta, theta))
            return null
        }

        theta < minTheta && dragDirection.isIn(DragDirection.Right, DragDirection.Up, DragDirection.RightUp, DragDirection.RightDown) -> {
            changeState(PageState.ThetaMin, minTheta)
            return null
        }
    }

    return points.toAbsoluteSystem()
}

internal fun algorithmStateWMin(absO: Point, absTouchPoint: Point, absC: Point, f: Float): Triple<Float, Float, AllPoints> {
    val C = absC.toCartesianSystem()
    val A = Point(absO.x, C.y)
    val B = Point(C.x, absO.y)
    val R = absTouchPoint.toCartesianSystem()
    val W = Point(minWxRatio * C.x, C.y)

    val Rf = Point(C.x, C.y + f)
    val k = (C.x - R.x) / (R.y - Rf.y)

    val Z = Point(C.x, k * (C.x - W.x) + C.y)

    val lEF = Line.withKAndOnePoint(k, R..Rf)
    val E = Point(lEF.x(C.y), C.y)
    val F = Point(C.x, lEF.y(C.x))

    val S = W..E
    val T = Z..F

    val lCH = Line.withKAndOnePoint(-1 / k, C)

    val P = lEF.intersectAt(lCH)
    val H = Point(2 * P.x - C.x, 2 * P.y - C.y)

    val lHE = Line.withTwoPoints(E, H)
    val lHF = Line.withTwoPoints(F, H)
    val lST = Line.withKAndOnePoint(k, S)
    val lWZ = Line.withKAndOnePoint(k, W)
    val I = lHE.intersectAt(lWZ)
    val J = lHF.intersectAt(lWZ)
    val U = lHE.intersectAt(lST)
    val V = lHF.intersectAt(lST)

    val M = U..S
    val N = T..V

    val allPoints = AllPoints(absO, A, B, C, H, I, J, M, N, S, T, U, V, W, Z)

    return Triple(lCH.theta(), E.x - W.x, allPoints)
}

internal inline fun buildStateThetaMin(absO: Point, dragEvent: DragEvent, absC: Point, f: Float, changeState: (newPageState: PageState, theta: Float) -> Unit): AllPoints? {
    val minWx = minWxRatio * absC.x
    val minWE = stateTightMinWERatio * absC.x

    val (WE, points) = algorithmStateThetaMin(absO, dragEvent.originTouchPoint, absC, f)

    val dragEventCartesian = dragEvent.toCartesianSystem()
    val lHF = Line.withTwoPoints(points.H, dragEventCartesian.originTouchPoint)
    val dragDirection = dragEventCartesian.directionToLineInCartesianSystem(lHF)

    when {
        points.W.x <= minWx && WE <= minWE && dragDirection.isIn(DragDirection.RightUp, DragDirection.LeftUp, DragDirection.Up, DragDirection.Right) -> {
            changeState(PageState.Tight, minTheta)
            return null
        }

        points.H.distanceTo(dragEventCartesian.originTouchPoint) < f && points.W.x > minWx && dragDirection.isIn(
            DragDirection.Left,
            DragDirection.LeftDown,
            DragDirection.RightDown,
            DragDirection.Down,
            DragDirection.LeftUp
        ) -> {
            changeState(PageState.Loose, minTheta)
            return null
        }

        points.H.distanceTo(dragEventCartesian.originTouchPoint) < f && points.W.x <= minWx && dragDirection.isIn(DragDirection.LeftDown, DragDirection.Left, DragDirection.LeftUp) -> {
            changeState(PageState.WMin, minTheta)
            return null
        }
    }

    return points.toAbsoluteSystem()
}

internal fun algorithmStateThetaMin(absO: Point, absTouchPoint: Point, absC: Point, f: Float): Pair<Float, AllPoints> {
    val C = absC.toCartesianSystem()
    val A = Point(absO.x, C.y)
    val B = Point(C.x, absO.y)
    val R = absTouchPoint.toCartesianSystem()

    val minWx = minWxRatio * C.x
    val minWE = stateTightMinWERatio * C.x

    val looseWx = Line.withKAndOnePoint((C.x - R.x) / (R.y - Point(C.x, C.y + f).y), R).x(C.y)
    val isLoose = looseWx >= minWx

    val lCH = Line.withKAndOnePoint(k(minTheta), C)
    val k = -1 / lCH.k

    val G = Point(C.x, R.y + lCH.k * (C.x - R.x))

    val E = Point(Line.withKAndOnePoint(k, R..G).x(C.y), C.y)
    val F = Point(C.x, k * (C.x - E.x) + C.y)
    val lEF = Line.withKAndOnePoint(k, E)

    val P = lEF.intersectAt(lCH)
    val H = Point(2 * P.x - C.x, 2 * P.y - C.y)

    val isFingerOut = H.distanceTo(R) > f || R.distanceTo(Line.withKAndOnePoint(0f, C)) > f

    val Wx_ = if (isFingerOut) {
        R.x - (R.y - C.y) / k
    } else {
        if (isLoose) looseWx else minWx
    }
    val WE = (E.x - Wx_).run { maxOf(minWE, this) }

    val W = Point((E.x - WE).run { maxOf(this, minWx) }, C.y)
    val lWZ = Line.withKAndOnePoint(k, W)
    val Z = Point(C.x, lWZ.y(C.x))

    val S = W..E
    val T = Z..F

    val lHE = Line.withTwoPoints(E, H)
    val lHF = Line.withTwoPoints(F, H)
    val lST = Line.withKAndOnePoint(k, S)
    val J = lHF.intersectAt(lWZ)
    val I = lHE.intersectAt(lWZ)
    val U = lHE.intersectAt(lST)
    val V = lHF.intersectAt(lST)

    val M = U..S
    val N = T..V

    val allPoints = AllPoints(absO, A, B, C, H, I, J, M, N, S, T, U, V, W, Z)

    return Pair(E.x - W.x, allPoints)
}

internal fun onDragWhenTightState(f: Float, currentTheta: Float, absDragEvent: DragEvent, screenHeight: Float, screenWidth: Float): Float {
    val dragEvent = absDragEvent.toCartesianSystem()

    val res = when (dragEvent.directionToOInCartesianSystem()) {
        DragDirection.Right -> {
            getTightStateDeltaWhenRight(currentTheta, absDragEvent.currentTouchPoint.x, absDragEvent.dragDelta.x, screenWidth)
        }

        DragDirection.Up, DragDirection.LeftUp -> {
            getTightStateDeltaWhenUp(currentTheta, absDragEvent.dragDelta.y)
        }

        DragDirection.LeftDown, DragDirection.Down, DragDirection.Left, DragDirection.RightDown -> {
            getTightStateDeltaWhenBack(f, currentTheta, absDragEvent, screenWidth, screenHeight)
        }

        DragDirection.RightUp -> {
            val deltaRight = getTightStateDeltaWhenRight(currentTheta, absDragEvent.currentTouchPoint.x, absDragEvent.dragDelta.x, screenWidth)
            val deltaUp = getTightStateDeltaWhenUp(currentTheta, absDragEvent.dragDelta.y)
            val yToX = -absDragEvent.dragDelta.y / absDragEvent.dragDelta.x
            (yToX / (1 + yToX)) * deltaUp + (1 / (1 + yToX)) * deltaRight
        }

        else -> 0f
    }

    return currentTheta + res
}

internal fun getTightStateDeltaWhenUp(currentTheta: Float, dragDeltaY: Float): Float {
    val thetaRange = FloatRange(maxOf(currentTheta, minTheta), minTheta)
    val newTheta = (dragDeltaY * stateTightDragUpThetaRate).toRad().run { thetaRange.constraints(this + currentTheta) }
    return newTheta - currentTheta
}

internal fun getTightStateDeltaWhenRight(currentTheta: Float, currentFingerX: Float, dragDeltaX: Float, screenWidth: Float): Float {
    val dragRange = FloatRange(currentFingerX - dragDeltaX, screenWidth * (1 - stateTightDragRightScreenDistanceRatio))
    val thetaRange = FloatRange(maxOf(currentTheta, minTheta), minTheta)
    val newTheta = dragRange.linearMapping(currentFingerX, thetaRange).run { thetaRange.constraints(this) }
    return newTheta - currentTheta
}

internal fun getTightStateDeltaWhenBack(f: Float, currentTheta: Float, dragEvent: DragEvent, screenWidth: Float, screenHeight: Float): Float {
    val C = Point(screenWidth, -screenHeight)
    val W = Point(minWxRatio * screenWidth, C.y)
    val E = Point(W.x + stateTightMinWERatio * C.x, C.y)
    val possibleRc = if (dragEvent.dragDelta.x == 0f) {
        val q = dragEvent.currentTouchPoint.x
        val a = 1f
        val b = -2 * C.y
        val c = C.y * C.y - f * f - (C.x - q) * (C.x + q - 2 * E.x)
        val RyEquation = QuadraticEquationWithOneUnknown(a, b, c)
        val possibleRy = RyEquation.solve().also {
            if (it.isEmpty()) return 0f
        }
        arrayOf(Point(q, possibleRy[0]), Point(q, possibleRy[1]))
    } else {
        val touchLine = Line.withKAndOnePoint(-dragEvent.dragDelta.y / dragEvent.dragDelta.x, dragEvent.currentTouchPoint.toCartesianSystem())
        val m = touchLine.k
        val n = touchLine.b

        val a = 1 + m * m
        val b = 2 * (m * n - m * C.y - E.x)
        val c = n * n + C.y * C.y - f * f - 2 * n * C.y - C.x * C.x + 2 * C.x * E.x
        val RxEquation = QuadraticEquationWithOneUnknown(a, b, c)
        val possibleRx = RxEquation.solve().also {
            if (it.isEmpty()) return 0f
        }
        arrayOf(Point(possibleRx[0], m * possibleRx[0] + n), Point(possibleRx[1], m * possibleRx[1] + n))
    }

    val finalThetaRange = minTheta..PI.toFloat()
    var finalTheta = 0f
    val finalRc = possibleRc.firstOrNull {
        val kEF = (it.y - C.y + f) / (it.x + C.x - 2 * E.x)
        val theta = theta(-1 / kEF)
        finalTheta = theta
        finalThetaRange.contains(theta) && (0f..screenWidth).contains(it.x) && (-screenHeight..0f).contains(it.y)
    } ?: return 0f

    val thetaRange = FloatRange(minOf(currentTheta, finalTheta), finalTheta)

    val originTouchPoint = dragEvent.originTouchPoint.toCartesianSystem()
    val fingerRange = FloatRange(0f, originTouchPoint.distanceTo(finalRc))

    val newFingerValue = dragEvent.currentTouchPoint.toCartesianSystem().distanceTo(originTouchPoint)
    val newTheta = fingerRange.linearMappingWithConstraints(newFingerValue, thetaRange).run { thetaRange.constraints(this) }

    return newTheta - currentTheta
}

internal fun buildStateTight(absO: Point, absC: Point, theta: Float, dragEvent: DragEvent, f: Float, changeState: (newPageState: PageState) -> Unit): AllPoints? {
    val (lHF, allPoints) = algorithmStateTight(absO, absC, theta)

    val dragEventCartesian = dragEvent.toCartesianSystem()
    val dragDirection = dragEventCartesian.directionToLineInCartesianSystem(lHF)

    val Rc = dragEvent.currentTouchPoint.toCartesianSystem()

    if (Rc.isBelow(lHF) && dragDirection.isIn(DragDirection.RightDown, DragDirection.Down, DragDirection.LeftDown)) {
        return if (Rc.distanceTo(allPoints.H) >= f && dragDirection.isIn(DragDirection.RightDown, DragDirection.Down, DragDirection.LeftDown)) {
            changeState(PageState.ThetaMin)
            null
        } else {
            changeState(PageState.WMin)
            null
        }
    }

    return allPoints.toAbsoluteSystem()
}

internal fun algorithmStateTight(absO: Point, absC: Point, theta: Float): Pair<Line, AllPoints> {
    val C = absC.toCartesianSystem()
    val B = Point(C.x, absO.y)
    val A = Point(absO.x, C.y)

    val kCH = k(theta)
    val kEF = -1 / kCH

    val minWE = stateTightMinWERatio * C.x

    val W = Point(minWxRatio * C.x, C.y)
    val Z = Point(C.x, C.y + kEF * (C.x - W.x))

    val E = Point(W.x + minWE, C.y)
    val S = E..W

    val lCH = Line.withKAndOnePoint(kCH, C)
    val lWZ = Line.withKAndOnePoint(kEF, W)
    val lST = Line.withKAndOnePoint(kEF, S)
    val lEF = Line.withKAndOnePoint(kEF, E)

    val T = Point(C.x, lST.y(C.x))
    val F = Point(C.x, lEF.y(C.x))

    val P = lEF.intersectAt(lCH)
    val H = Point(2 * P.x - C.x, 2 * P.y - C.y)
    val lEH = Line.withTwoPoints(E, H)
    val lFH = Line.withTwoPoints(F, H)

    val I = lWZ.intersectAt(lEH)
    val U = lST.intersectAt(lEH)
    val J = lWZ.intersectAt(lFH)
    val V = lST.intersectAt(lFH)
    val M = U..S
    val N = V..T

    val allPoints = AllPoints(absO, A, B, C, H, I, J, M, N, S, T, U, V, W, Z)

    return Pair(lFH, allPoints)
}
