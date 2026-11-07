package com.local.applock.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 规则持久化。用 SharedPreferences 存一个 JSON 数组，不引第三方依赖。
 */
object RuleStore {

    private const val PREFS = "applock_rules"
    private const val KEY_RULES = "rules"
    private const val KEY_NEXT_ID = "next_id"

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 读取全部规则，按 id 升序。 */
    fun loadAll(ctx: Context): List<Rule> {
        val raw = prefs(ctx).getString(KEY_RULES, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i -> parse(arr.getJSONObject(i)) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 保存一条规则。id <= 0 视为新建，返回带新 id 的规则。 */
    fun save(ctx: Context, rule: Rule): Rule {
        val p = prefs(ctx)
        val all = loadAll(ctx).toMutableList()
        val saved = if (rule.id <= 0) {
            val newId = p.getInt(KEY_NEXT_ID, 1)
            p.edit().putInt(KEY_NEXT_ID, newId + 1).apply()
            rule.copy(id = newId)
        } else {
            rule
        }
        val idx = all.indexOfFirst { it.id == saved.id }
        if (idx >= 0) all[idx] = saved else all.add(saved)
        write(ctx, all)
        return saved
    }

    /** 删除指定 id 的规则。 */
    fun delete(ctx: Context, id: Int) {
        val all = loadAll(ctx).filter { it.id != id }
        write(ctx, all)
    }

    /** 更新启用状态。 */
    fun setEnabled(ctx: Context, id: Int, enabled: Boolean) {
        val all = loadAll(ctx).map { if (it.id == id) it.copy(enabled = enabled) else it }
        write(ctx, all)
    }

    /** 当前时刻生效的规则列表。 */
    fun activeRules(ctx: Context, now: java.time.ZonedDateTime): List<Rule> =
        loadAll(ctx).filter { it.isActive(now) }

    private fun write(ctx: Context, rules: List<Rule>) {
        val arr = JSONArray()
        rules.forEach { arr.put(serialize(it)) }
        prefs(ctx).edit().putString(KEY_RULES, arr.toString()).apply()
    }

    private fun serialize(r: Rule): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("pkg", r.pkg)
        put("label", r.label)
        put("days", JSONArray(r.days.toList()))
        put("startMin", r.startMin)
        put("endMin", r.endMin)
        put("enabled", r.enabled)
    }

    private fun parse(o: JSONObject): Rule {
        val daysArr = o.optJSONArray("days")
        val days = buildSet {
            if (daysArr != null) for (i in 0 until daysArr.length()) add(daysArr.getInt(i))
        }
        return Rule(
            id = o.optInt("id"),
            pkg = o.optString("pkg"),
            label = o.optString("label"),
            days = days,
            startMin = o.optInt("startMin"),
            endMin = o.optInt("endMin"),
            enabled = o.optBoolean("enabled", true)
        )
    }
}
