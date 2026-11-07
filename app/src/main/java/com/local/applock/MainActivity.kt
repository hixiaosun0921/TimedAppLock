package com.local.applock

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.local.applock.data.Rule
import com.local.applock.data.RuleStore
import com.local.applock.util.Bg
import com.local.applock.util.Perm
import com.local.applock.util.TimeWindow

/**
 * 主界面：权限自检卡片 + 规则列表。
 */
class MainActivity : AppCompatActivity() {

    private var titleText: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btn_add).setOnClickListener {
            startActivity(Intent(this, RuleEditActivity::class.java))
        }
        // 切换背景入口：弹出选择框，一眼选中，不再循环
        findViewById<Button>(R.id.btn_bg).setOnClickListener {
            showBgPicker()
        }
        titleText = findViewById(R.id.title_text)
    }

    override fun onResume() {
        super.onResume()
        applyTheme()
        refresh()
    }

    /** 应用背景 + 动态配色（按背景深浅）。 */
    private fun applyTheme() {
        val root = findViewById<android.view.ViewGroup>(R.id.main_root)
        val dark = Bg.isDark(this)
        Bg.apply(root, this)

        val title = titleText
        title?.setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())

        // 权限卡片：浅色→白色半透明卡片+深字，深色→黑色半透明卡片+白字
        val cardBg = if (dark) 0x33000000.toInt() else 0x99FFFFFF.toInt()
        val cardText = if (dark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()

        val cardAcc = findViewById<TextView>(R.id.card_acc)
        val cardNotif = findViewById<TextView>(R.id.card_notif)
        cardAcc.setBackgroundColor(cardBg)
        cardNotif.setBackgroundColor(cardBg)
        cardAcc.setTextColor(cardText)
        cardNotif.setTextColor(cardText)
    }

    private fun refresh() {
        // 权限自检卡片
        val accOk = Perm.isAccessibilityEnabled(this)
        val notifOk = Perm.isNotificationEnabled(this)

        val cardAcc = findViewById<TextView>(R.id.card_acc)
        val cardNotif = findViewById<TextView>(R.id.card_notif)
        cardAcc.text = if (accOk) "无障碍：已开启" else "无障碍：未开启（点此开启）"
        cardNotif.text = if (notifOk) "通知：已授权" else "通知：未授权（点此授权）"

        cardAcc.setOnClickListener {
            if (!accOk) Perm.openAccessibilitySettings(this)
        }
        cardNotif.setOnClickListener {
            if (!notifOk) Perm.openNotificationSettings(this)
        }

        // 规则列表
        val list = findViewById<LinearLayout>(R.id.rule_list)
        list.removeAllViews()
        val rules = RuleStore.loadAll(this)

        if (rules.isEmpty()) {
            val empty = TextView(this).apply {
                text = "还没有规则，点下方按钮添加"
                textSize = 15f
                setPadding(32, 40, 32, 40)
                val dark = Bg.isDark(this@MainActivity)
                setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
                setBackgroundColor(if (dark) 0x33000000.toInt() else 0x99FFFFFF.toInt())
            }
            list.addView(empty)
        } else {
            rules.forEach { rule ->
                list.addView(buildRuleItem(rule))
            }
        }
    }

    private fun buildRuleItem(rule: Rule): android.view.View {
        val dark = Bg.isDark(this)
        // 浅色→白色半透明卡片+深字，深色→黑色半透明卡片+白字
        val cardBg = if (dark) 0x33000000.toInt() else 0x99FFFFFF.toInt()
        val textColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()
        val subColor = if (dark) 0xFFB0B0B0.toInt() else 0xFF555555.toInt()

        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)
            setBackgroundColor(cardBg)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12 }
        }

        val daysText = rule.days.sorted().joinToString("、") { dayName(it) }
        val active = rule.isActive(java.time.ZonedDateTime.now())
        val title = TextView(this).apply {
            text = "${rule.label}  ${TimeWindow.describeWindow(rule.startMin, rule.endMin)}"
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(textColor)
        }
        val sub = TextView(this).apply {
            text = "$daysText  " + when {
                active -> "正在生效（不可修改）"
                rule.enabled -> "已启用"
                else -> "已停用"
            }
            textSize = 13f
            setTextColor(if (active) 0xFFFF5252.toInt() else subColor)
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val btnToggle = Button(this).apply {
            text = if (rule.enabled) "停用" else "启用"
            setOnClickListener {
                guard(rule, if (rule.enabled) "停用规则" else "启用规则") {
                    RuleStore.setEnabled(this@MainActivity, rule.id, !rule.enabled)
                    refresh()
                }
            }
        }
        val btnEdit = Button(this).apply {
            text = "编辑"
            setOnClickListener {
                guard(rule, "修改规则") {
                    startActivity(
                        Intent(this@MainActivity, RuleEditActivity::class.java)
                            .putExtra("id", rule.id)
                            .putExtra("pkg", rule.pkg)
                            .putExtra("label", rule.label)
                    )
                }
            }
        }
        val btnDel = Button(this).apply {
            text = "删除"
            setOnClickListener {
                guard(rule, "删除规则") {
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle("删除规则")
                        .setMessage("确定删除「${rule.label}」的锁定规则？")
                        .setPositiveButton("删除") { _, _ ->
                            RuleStore.delete(this@MainActivity, rule.id)
                            refresh()
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
            }
        }
        btnRow.addView(btnToggle)
        btnRow.addView(btnEdit)
        btnRow.addView(btnDel)

        item.addView(title)
        item.addView(sub)
        item.addView(btnRow)
        return item
    }

    /** 弹出背景选择框：九宫格缩略图，点选即换。 */
    private fun showBgPicker() {
        val grid = android.widget.GridView(this).apply {
            numColumns = 3
            horizontalSpacing = 16
            verticalSpacing = 16
            setPadding(24, 24, 24, 24)
            clipToPadding = false
            adapter = object : android.widget.BaseAdapter() {
                override fun getCount() = Bg.RES_IDS.size
                override fun getItem(position: Int) = Bg.RES_IDS[position]
                override fun getItemId(position: Int) = position.toLong()

                override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                    val v = LinearLayout(this@MainActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = android.view.Gravity.CENTER
                    }
                    val img = android.widget.ImageView(this@MainActivity).apply {
                        setImageResource(Bg.RES_IDS[position])
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                        layoutParams = LinearLayout.LayoutParams(
                            (96 * resources.displayMetrics.density).toInt(),
                            (96 * resources.displayMetrics.density).toInt()
                        )
                    }
                    val name = TextView(this@MainActivity).apply {
                        text = Bg.NAMES[position]
                        textSize = 12f
                        gravity = android.view.Gravity.CENTER
                        setTextColor(0xFF333333.toInt())
                    }
                    v.addView(img)
                    v.addView(name)
                    return v
                }
            }
            setOnItemClickListener { _, _, position, _ ->
                dialogRef?.dismiss()
                Bg.set(this@MainActivity, position)
                applyTheme()
                refresh()
            }
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("选择背景")
            .setView(grid)
            .setNegativeButton("取消", null)
            .create()
        dialogRef = dialog
        dialog.show()
    }

    private var dialogRef: android.app.Dialog? = null

    private fun toastLocked() {
        android.widget.Toast.makeText(
            this, "规则正在生效中，时间结束后才能修改", android.widget.Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * 规则操作的保护闸门。
     * - 锁定进行中：完全禁止
     * - 保护期（距开始 ≤1h）：需等待 30 分钟且不可切后台
     * - 其他时间：直接执行
     */
    private fun guard(rule: Rule, actionText: String, doIt: () -> Unit) {
        val now = java.time.ZonedDateTime.now()
        if (rule.isActive(now)) {
            toastLocked()
            return
        }
        if (rule.inGracePeriod(now)) {
            CooldownActivity.startForResult(this, actionText, REQ_COOLDOWN)
            pendingAction = doIt
            return
        }
        doIt()
    }

    private var pendingAction: (() -> Unit)? = null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_COOLDOWN && resultCode == RESULT_OK && pendingAction != null) {
            pendingAction!!.invoke()
            pendingAction = null
        }
    }

    companion object {
        private const val REQ_COOLDOWN = 100
    }

    private fun dayName(d: Int): String = when (d) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
}
