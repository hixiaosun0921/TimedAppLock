package com.local.applock.util

import java.time.ZonedDateTime

/**
 * 时间段相关工具。核心判定逻辑在 Rule.isActive 中，这里放辅助方法。
 */
object TimeWindow {

    /** 把 now 对应的分钟数（0..1439）格式化为人可读的时间字符串，如 "22:05"。 */
    fun formatMinute(min: Int): String {
        val h = min / 60
        val m = min % 60
        return "%02d:%02d".format(h, m)
    }

    /** 描述一个窗口，用于悬浮层文案，如 "22:00–06:30"。 */
    fun describeWindow(startMin: Int, endMin: Int): String {
        return "${formatMinute(startMin)}–${formatMinute(endMin)}"
    }

    /** 计算从 now 到窗口结束还剩多少秒，用于悬浮层"剩余时间"提示。 */
    fun secondsUntilEnd(now: ZonedDateTime, endMin: Int): Long {
        val endHour = endMin / 60
        val endMinute = endMin % 60
        val end = now.withHour(endHour).withMinute(endMinute).withSecond(0).withNano(0)
        val target = if (!end.isAfter(now)) end.plusDays(1) else end
        return java.time.Duration.between(now, target).seconds
    }
}
