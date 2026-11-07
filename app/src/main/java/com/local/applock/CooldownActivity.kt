package com.local.applock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.local.applock.util.Bg

/**
 * 冷却等待页。用于"保护期"内解除/修改规则时，强制等待 30 分钟。
 *
 * 关键规则：倒计时期间若本页面进入后台（onPause），计时归零重来。
 * 30 分钟走完后，以 RESULT_OK 结束，由调用方执行真正的解除动作。
 */
class CooldownActivity : AppCompatActivity() {

    companion object {
        /** 冷却时长，秒。 */
        const val COOLDOWN_SECONDS = 30 * 60L

        /** 启动本页并等待结果。返回 RESULT_OK 表示冷却完成。 */
        fun start(ctx: android.content.Context, actionText: String) {
            ctx.startActivity(
                Intent(ctx, CooldownActivity::class.java)
                    .putExtra("action", actionText)
            )
        }

        /** 以 startActivityForResult 启动。 */
        fun startForResult(activity: Activity, actionText: String, requestCode: Int) {
            activity.startActivityForResult(
                Intent(activity, CooldownActivity::class.java)
                    .putExtra("action", actionText),
                requestCode
            )
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var remaining = COOLDOWN_SECONDS

    private val ticker = object : Runnable {
        override fun run() {
            if (remaining <= 0) {
                setResult(Activity.RESULT_OK)
                finish()
                return
            }
            remaining--
            updateCount()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cooldown)
        Bg.apply(findViewById(R.id.cooldown_root), this)
        val dark = Bg.isDark(this)
        val textColor = if (dark) 0xFFFFFFFF.toInt() else 0xFF1A1A1A.toInt()

        val action = intent.getStringExtra("action") ?: "解除锁定"
        findViewById<TextView>(R.id.cooldown_title).apply {
            text = "正在$action…"
            setTextColor(textColor)
        }
        findViewById<TextView>(R.id.cooldown_desc).apply {
            text = "你正在尝试「$action」。为防止一时冲动，需要保持本页面前台 30 分钟才能继续。"
            setTextColor(textColor)
        }
        findViewById<TextView>(R.id.cooldown_count).setTextColor(textColor)
        findViewById<TextView>(R.id.cooldown_hint).apply {
            setTextColor(if (dark) 0xFFB0B0B0.toInt() else 0xFF555555.toInt())
        }
        findViewById<Button>(R.id.cooldown_cancel).setOnClickListener {
            finish()
        }

        updateCount()
    }

    override fun onResume() {
        super.onResume()
        // 每次回到前台，重新开始计时
        remaining = COOLDOWN_SECONDS
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        // 切后台即停止计时
        handler.removeCallbacks(ticker)
    }

    private fun updateCount() {
        val m = remaining / 60
        val s = remaining % 60
        findViewById<TextView>(R.id.cooldown_count).text =
            "%02d:%02d".format(m, s)
    }
}
