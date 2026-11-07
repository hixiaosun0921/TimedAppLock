package com.local.applock

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.local.applock.util.Bg

/**
 * 选择目标 App。用 queryIntentActivities 列出所有带桌面入口的应用（最可靠，
 * 一定包含 B站 等所有能从桌面打开的应用）。排除自身。
 */
class AppPickerActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_picker)
        Bg.apply(findViewById(R.id.picker_root), this)
        val dark = Bg.isDark(this)

        val cardBg = if (dark) 0x33000000.toInt() else 0x99FFFFFF.toInt()
        val textColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()

        val list = findViewById<LinearLayout>(R.id.picker_list)
        val pm = packageManager

        val launchIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolvers = pm.queryIntentActivities(launchIntent, 0)

        val items = resolvers
            .map { ri ->
                val pkg = ri.activityInfo.packageName
                val label = ri.loadLabel(pm).toString()
                pkg to label
            }
            .filter { it.first != packageName }
            .distinctBy { it.first }           // 同一包多个入口只留一个
            .sortedBy { it.second }

        items.forEach { (pkg, label) ->
            val item = TextView(this).apply {
                text = label
                textSize = 18f
                setPadding(40, 32, 40, 32)
                setTextColor(textColor)
                // 半透明圆角卡片底
                background = GradientDrawable().apply {
                    setColor(cardBg)
                    cornerRadius = 24f
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = 32
                    rightMargin = 32
                    bottomMargin = 20
                }
                setOnClickListener {
                    val result = Intent().apply {
                        putExtra("pkg", pkg)
                        putExtra("label", label)
                    }
                    setResult(RESULT_OK, result)
                    finish()
                }
            }
            list.addView(item)
        }
    }
}
