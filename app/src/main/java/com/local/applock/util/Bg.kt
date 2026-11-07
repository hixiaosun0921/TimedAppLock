package com.local.applock.util

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView

/**
 * 背景管理。预设 9 张内置图，写死打包进 APK，不做相册读取。
 *
 * 背景分两组：
 * - 浅色组（配白色半透明卡片 + 深色文字）
 * - 深色组（配黑色半透明卡片 + 白色文字）
 *
 * index 顺序 = 浅色组在前、深色组在后，循环切换。
 */
object Bg {

    private const val PREFS = "applock_bg"
    private const val KEY_INDEX = "index"

    /** 浅色背景图资源 id 列表。 */
    private val LIGHT_IDS = intArrayOf(
        com.local.applock.R.drawable.bg_garden,
        com.local.applock.R.drawable.bg_squirrel,
        com.local.applock.R.drawable.bg_plane,
        com.local.applock.R.drawable.bg_cat,
        com.local.applock.R.drawable.bg_ferris,
        com.local.applock.R.drawable.bg_seaside
    )

    /** 深色背景图资源 id 列表。 */
    private val DARK_IDS = intArrayOf(
        com.local.applock.R.drawable.bg_star,
        com.local.applock.R.drawable.bg_moon,
        com.local.applock.R.drawable.bg_night
    )

    /** 全部背景图，浅色在前、深色在后。 */
    val RES_IDS = LIGHT_IDS + DARK_IDS

    /** 每张背景图对应的名字（与 RES_IDS 顺序一致）。 */
    val NAMES = arrayOf(
        "庭院", "松鼠湖边", "飞机舷窗", "猫栅栏",
        "夕阳摩天轮", "海边窗帘",
        "星空", "月夜海边", "城市夜景"
    )

    /** 浅色背景数量（用于判断 isDark）。 */
    private val LIGHT_COUNT = LIGHT_IDS.size

    /** 当前背景 index，默认 0（庭院，浅色）。 */
    fun current(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_INDEX, 0)

    /** 切换背景 index（循环）。 */
    fun next(ctx: Context): Int {
        val cur = current(ctx)
        val next = (cur + 1) % RES_IDS.size
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INDEX, next).apply()
        return next
    }

    /** 直接设置背景 index。 */
    fun set(ctx: Context, index: Int) {
        val i = index.coerceIn(0, RES_IDS.size - 1)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INDEX, i).apply()
    }

    /** 当前背景是否深色（index >= 浅色数量 即为深色）。 */
    fun isDark(ctx: Context): Boolean = current(ctx) >= LIGHT_COUNT

    /**
     * 把背景铺到根视图上。在根视图里塞一个全屏 ImageView(centerCrop) 作为背景层，
     * 避免拉伸变形。
     */
    fun apply(root: View, ctx: Context) {
        val resId = RES_IDS[current(ctx)]
        // 若 root 的父级已经是被我们包过的 FrameLayout，直接更新图片即可
        val existingHost = root.parent as? FrameLayout
        if (existingHost != null && existingHost.tag == "bg_host") {
            val bg = existingHost.getChildAt(0) as? ImageView
            bg?.setImageResource(resId)
            return
        }
        // 若 root 本身已经是 FrameLayout 且已塞过背景层，只更新图片
        if (root is FrameLayout && root.tag == "bg_host") {
            val bg = root.getChildAt(0) as? ImageView
                ?: ImageView(ctx).also { root.addView(it, 0) }
            bg.scaleType = ImageView.ScaleType.CENTER_CROP
            bg.setImageResource(resId)
            return
        }
        // 否则：把 root 包进一个 FrameLayout，塞背景 ImageView
        val container = FrameLayout(root.context)
        val parent = root.parent as? ViewGroup
        val idx = if (parent != null) parent.indexOfChild(root) else -1
        val lp = root.layoutParams
        if (parent != null) parent.removeView(root)
        container.addView(
            root,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        if (parent != null) parent.addView(container, idx, lp)
        container.tag = "bg_host"
        val bg = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(resId)
        }
        container.addView(
            bg, 0,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
}
