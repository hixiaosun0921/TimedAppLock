package com.local.applock.data

import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * 一条锁定规则。
 *
 * @param id       自增主键
 * @param pkg      目标应用包名
 * @param label    目标应用显示名
 * @param days     生效的星期，1=周一 ... 7=周日，指"窗口开始那天"
 * @param startMin 开始时间，0..1439
 * @param endMin   结束时间，0..1439
 * @param enabled  是否启用
 */
data class Rule(
    val id: Int,
    val pkg: String,
    val label: String,
    val days: Set<Int>,
    val startMin: Int,
    val endMin: Int,
    val enabled: Boolean
) {
    /**
     * 判定规则在 now 时刻是否处于生效状态。
     * 支持跨零点（startMin > endMin）与跨周（周五晚到周六早）。
     */
    fun isActive(now: ZonedDateTime): Boolean {
        if (!enabled || startMin == endMin) return false
        val m = now.hour * 60 + now.minute
        val today = now.dayOfWeek.value
        val yesterday = now.minusDays(1).dayOfWeek.value
        return if (startMin < endMin) {
            today in days && m in startMin until endMin
        } else {
            (today in days && m >= startMin) ||
                (yesterday in days && m < endMin)
        }
    }

    /**
     * 判定规则此刻是否处于"保护期"：距下一次开始 ≤ 1 小时，且尚未开始。
     * 即从 startMin 往前 60 分钟这一窗口。
     * 规则必须启用，且当天（指"开始那天"）在 days 内。
     */
    fun inGracePeriod(now: ZonedDateTime): Boolean {
        if (!enabled || startMin == endMin) return false
        val today = now.dayOfWeek.value
        if (today !in days) return false
        val m = now.hour * 60 + now.minute
        // 距开始还有多少分钟（未开始为正）
        val diff = startMin - m
        return diff in 1..60
    }
}
