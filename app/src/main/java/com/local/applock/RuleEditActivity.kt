package com.local.applock

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.local.applock.data.Rule
import com.local.applock.data.RuleStore
import com.local.applock.util.Bg

/**
 * 新建 / 编辑规则。时间用文本输入（HH:mm），不用钟表 TimePicker。
 */
class RuleEditActivity : AppCompatActivity() {

    private var editingId = -1
    private var pkg = ""
    private var label = ""
    private var dark = false

    private val dayBoxes = mutableMapOf<Int, CheckBox>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rule_edit)
        dark = Bg.isDark(this)
        applyBg()
        applyColors()

        editingId = intent.getIntExtra("id", -1)
        pkg = intent.getStringExtra("pkg") ?: ""
        label = intent.getStringExtra("label") ?: ""

        val tvApp = findViewById<TextView>(R.id.edit_app)
        val etStart = findViewById<EditText>(R.id.edit_start)
        val etEnd = findViewById<EditText>(R.id.edit_end)
        val daysRow = findViewById<LinearLayout>(R.id.edit_days)
        val btnSave = findViewById<Button>(R.id.edit_save)

        // 星期复选框 1..7，分成两行：第一行周一~周四，第二行周五~周日
        val dayNames = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val row2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (d in 1..7) {
            val cb = CheckBox(this).apply {
                text = dayNames[d - 1]
                // 浅色→深字深勾，深色→白字白勾
                setTextColor(if (dark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt())
                buttonTintList = android.content.res.ColorStateList.valueOf(
                    if (dark) 0xFFFFFFFF.toInt() else 0xFF333333.toInt()
                )
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }
            dayBoxes[d] = cb
            if (d <= 4) row1.addView(cb) else row2.addView(cb)
        }
        daysRow.addView(row1)
        daysRow.addView(row2)
        // 星期区块加半透明衬底：浅色白卡、深色黑卡
        val dayCardBg = if (dark) 0x33000000.toInt() else 0x99FFFFFF.toInt()
        daysRow.setBackgroundColor(dayCardBg)
        daysRow.setPadding(16, 8, 16, 8)

        // 载入已有规则
        if (editingId > 0) {
            val rule = RuleStore.loadAll(this).firstOrNull { it.id == editingId }
            if (rule != null) {
                pkg = rule.pkg
                label = rule.label
                etStart.setText(minToHhMm(rule.startMin))
                etEnd.setText(minToHhMm(rule.endMin))
                rule.days.forEach { dayBoxes[it]?.isChecked = true }
                // 规则正在生效时，禁止编辑保存
                if (rule.isActive(java.time.ZonedDateTime.now())) {
                    btnSave.isEnabled = false
                    btnSave.text = "生效中，不可修改"
                    Toast.makeText(
                        this, "规则正在生效中，时间结束后才能修改",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        tvApp.text = if (label.isNotEmpty()) label else "未选择"

        tvApp.setOnClickListener {
            startActivityForResult(
                Intent(this, AppPickerActivity::class.java), 100
            )
        }

        btnSave.setOnClickListener {
            val startMin = parseHhMm(etStart.text.toString())
            val endMin = parseHhMm(etEnd.text.toString())
            val days = dayBoxes.filterValues { it.isChecked }.keys
            if (pkg.isEmpty()) {
                Toast.makeText(this, "请先选择要锁定的 App", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (days.isEmpty()) {
                Toast.makeText(this, "请至少选择一天", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (startMin < 0 || endMin < 0) {
                Toast.makeText(this, "时间格式错误，请用 HH:mm，如 22:00", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (startMin == endMin) {
                Toast.makeText(this, "开始和结束时间不能相同", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val rule = Rule(
                id = editingId,
                pkg = pkg,
                label = label,
                days = days,
                startMin = startMin,
                endMin = endMin,
                enabled = true
            )
            RuleStore.save(this, rule)
            finish()
        }
    }

    private fun minToHhMm(min: Int): String =
        "%02d:%02d".format(min / 60, min % 60)

    private fun applyBg() {
        val root = findViewById<android.view.ViewGroup>(R.id.edit_root)
        Bg.apply(root, this)
    }

    /** 统一配色：覆盖 Material3 默认的紫色，标签/输入框按背景深浅设色。 */
    private fun applyColors() {
        val textColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()
        val hintColor = if (dark) 0xAAFFFFFF.toInt() else 0x88333333.toInt()
        val cardBg = if (dark) 0x33000000.toInt() else 0x99FFFFFF.toInt()

        // 标签：按背景深浅设色，加同色系半透明衬底保证可读
        val labelBg = if (dark) 0x33000000.toInt() else 0x99FFFFFF.toInt()
        intArrayOf(
            R.id.edit_label_app, R.id.edit_label_days,
            R.id.edit_label_start, R.id.edit_label_end
        ).forEach { id ->
            findViewById<TextView>(id).apply {
                setTextColor(textColor)
                setBackgroundColor(labelBg)
                setPadding(24, 12, 24, 12)
            }
        }

        // App 选择框：半透明卡片 + 文字色
        findViewById<TextView>(R.id.edit_app).apply {
            setBackgroundColor(cardBg)
            setTextColor(textColor)
        }

        // 输入框：文字色 + 提示色 + 半透明衬底
        listOf(R.id.edit_start, R.id.edit_end).forEach { id ->
            findViewById<EditText>(id).apply {
                setTextColor(textColor)
                setHintTextColor(hintColor)
                setBackgroundColor(cardBg)
            }
        }
    }

    /** 解析 "HH:mm"，失败返回 -1。也接受 "H:mm" 或 "H:m"。 */
    private fun parseHhMm(s: String): Int {
        val t = s.trim()
        val parts = t.split(":", "：")
        if (parts.size != 2) return -1
        val h = parts[0].toIntOrNull() ?: return -1
        val m = parts[1].toIntOrNull() ?: return -1
        if (h !in 0..23 || m !in 0..59) return -1
        return h * 60 + m
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            pkg = data.getStringExtra("pkg") ?: ""
            label = data.getStringExtra("label") ?: ""
            findViewById<TextView>(R.id.edit_app).text = label
        }
    }
}
