// Adapted from PTQFlipper 1.1.0 (MIT, Copyright 2023 MPGA).
// See assets/licenses/PTQFlipper.txt.
package ptq.mpga.ptqbookpageview.widget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import kotlin.math.PI


//带ratio的要乘屏幕比例
internal const val stateTightMinWERatio = 1 / 8f //进入tight状态的最小WE长度
internal const val minTheta = ((137 * PI) / 180).toFloat() //最小θ，弧度制
internal const val stateTightDragUpThetaRate = 0.1f //tight状态向上拉扯的速率，该值越大则拉扯越快
internal const val stateTightDragRightScreenDistanceRatio = 1 / 40f //tight状态手指移到屏幕右侧多少时到达最终态
internal const val maxDragXRatio = stateTightDragRightScreenDistanceRatio //手指最大X，和上个变量一样
internal const val minWxRatio = 1 / 25f //W.x的最小值

internal const val animStartTimeout = 70//动画在手指拖动多久后开始，此值不宜过大，会造成不准确
internal const val animEnterDuration = 100 //入动画时间，此值不宜过大，会造成不准确
internal const val animExitDuration = animEnterDuration //出动画时间

internal const val tapYDeltaRatio = 1 / 170f //点击翻页时，y方向偏移，不宜过大

internal const val shadowThreshold = 26f //阴影1、2阈值
internal const val shadowPart3to1Ratio = 1.5f //阴影3与阴影1的宽度比
internal const val shadow3VerticalThreshold = 30 //处理当接近垂直时，底层绘制api不正常工作的问题

internal val shadow12Color = Color.Black.copy(alpha = 0.35f) //12部分阴影的颜色
internal val shadow3Color = Color.Black.copy(alpha = 0.68f) //3部分阴影的颜色
internal val lustreColor = Color.Black.copy(alpha = 0.02f) //光泽部分阴影的颜色

//最好设置为统一比例
internal const val lustreStartMaxDistance = 9f //光泽右侧最大距离
internal const val lustreStartMinDistance = 6f //光泽右侧最小距离，即当WEmin时的距离
internal const val lustreEndMaxDistance = 9f //光泽左侧最大距离
internal const val lustreEndMinDistance = 6f //光泽左侧最小距离，即当WEmin时的距离
internal const val lustreEndShadowMaxWidth = 30f //光泽左侧阴影最大宽度
internal const val lustreEndShadowMinWidth = 20f //光泽左侧阴影最小宽度，即当WEmin时的宽度

//private const val distortionInterval = 25 //扭曲的间隔
//private const val bezierEdgeDownSampling = 50 //NVJ、ZTN等贝塞尔曲线边的下采样值（此值越小，采样点越少）

internal enum class PageState {
    Loose, WMin, ThetaMin, Tight
}

//翻页器组件的流程状态
internal enum class State {
    Idle, EnterAnimStart, Draggable, ExitAnimStart, ExitAnimPreStart
}
