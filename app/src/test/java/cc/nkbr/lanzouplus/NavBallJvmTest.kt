package cc.nkbr.lanzouplus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * [DFWX] DFW-41（T1/T1b）悬浮导航回归：**核对后发现四项已实现，本轮补守卫**。
 *
 * 卡片列的四个收尾项逐一核对结论：
 * 1. 拖动跟手 / 选中反馈 / 边界（贴边、状态栏、导航栏）→ 已实现（`snapToEdge` 双侧夹取 + `EDGE_TOP_DP`/`EDGE_BOTTOM_DP`）；
 * 2. **位置记忆可靠**（重启恢复）→ 已实现（`nav_ball` 偏好存 `userCx`/`userCy`，`loadPosition` 恢复）；
 * 3. AI 输入与页面按钮不被遮挡 → 已实现（`AI_SAFE_RATIO` 避让）；
 * 4. **切页/误触后不留无法消失的菜单** → 已实现（`onDestinationChanged` 里 `closeMenu` 或 `forceHidePills`，
 *    并有 `scrim` 点击关闭）。
 *
 * 这类"已经做好但随时可能被改坏"的交互最容易在后续重构中回退，且**回退了没有测试会红**——
 * 所以把这些不变量钉住（项目里同类做法见 `PredictiveBackJvmTest` / `BrandCreditsJvmTest`）。
 *
 * 注：真机触感与遮挡需用户实际拖拽确认（本机 adb 连不上手机），此测试只守代码层不变量。
 */
class NavBallJvmTest {

    private val source: String = File(
        (System.getProperty("user.dir") ?: ".").let { dir ->
            var d = File(dir).absoluteFile
            while (!java.io.File(d, "src/main/java/cc/nkbr/lanzouplus/NavBall.java").isFile && d.parentFile != null) d = d.parentFile
            d
        },
        "src/main/java/cc/nkbr/lanzouplus/NavBall.java",
    ).readText(Charsets.UTF_8)


    /**
     * 按大括号配对取出某个方法的**完整方法体**。
     *
     * 为什么必须这样做：第一版用"从方法名起截 500 字符"来取 body，
     * 结果窗口越界到了**紧随其后的另一个方法定义**里，
     * 于是 `body.contains("forceHidePills()")` 因为看到隔壁的方法签名而恒真——
     * 把调用删掉测试照样绿（假绿）。这是"源码文本断言"最容易犯的错，
     * 必须按语法结构取，而不是按长度猜。
     */
    private fun methodBody(signature: String): String {
        val start = source.indexOf(signature)
        require(start >= 0) { "找不到方法：$signature" }
        val open = source.indexOf('{', start)
        require(open >= 0) { "找不到方法体起始：$signature" }
        var depth = 0
        var i = open
        while (i < source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return source.substring(open + 1, i)
                }
            }
            i++
        }
        error("方法体未闭合：$signature")
    }

    // ---------- ② 位置记忆必须可靠 ----------

    @Test
    fun position_isPersistedAndRestored() {
        assertTrue("必须持久化球心坐标", source.contains("persistPosition()"))
        assertTrue("必须能恢复球心坐标", source.contains("loadPosition()"))
        assertTrue("持久化必须落 SharedPreferences（跨重启可见）", source.contains("getSharedPreferences(\"nav_ball\""))
        assertTrue("必须写入 cx", source.contains("putFloat(\"cx\""))
        assertTrue("必须写入 cy", source.contains("putFloat(\"cy\""))
        assertTrue("必须读回 cx", source.contains("getFloat(\"cx\""))
        assertTrue("必须读回 cy", source.contains("getFloat(\"cy\""))
    }

    /** 没有记忆时要有默认值（不能是 0,0 把球贴左上角、或被当成"用户拖过"）。 */
    @Test
    fun position_hasSaneDefaultWhenNothingStored() {
        assertTrue(
            "未存储时应给出哨兵默认值（-1）而不是 0，否则球会被误当作已定位到原点",
            source.contains("getFloat(\"cx\", -1f)") && source.contains("getFloat(\"cy\", -1f)"),
        )
    }

    @Test
    fun snapToEdge_clampsWithinSafeArea() {
        assertTrue("松手必须贴边", source.contains("snapToEdge()"))
        assertTrue("必须夹取横向范围（不越左右边）", source.contains("clamp(currentCx"))
        assertTrue("必须夹取纵向范围（不压状态栏/导航栏）", source.contains("clamp(currentCy"))
        assertTrue("必须用到顶部安全边距", source.contains("EDGE_TOP_DP"))
        assertTrue("必须用到底部安全边距", source.contains("EDGE_BOTTOM_DP"))
    }

    // ---------- ① 拖动跟手 / 按压反馈 ----------

    @Test
    fun drag_cancelsRunningAnimationOnDown() {
        // 项目铁律：ACTION_DOWN 必须 cancel 旧动画，否则连点/快速拖动会抖动或"点不动"
        val body = methodBody("private boolean onBallTouch(View v, MotionEvent ev)")
        val idx = body.indexOf("MotionEvent.ACTION_DOWN")
        assertTrue("onBallTouch 必须有 ACTION_DOWN 分支", idx >= 0)
        val seg = body.substring(idx, minOf(body.length, idx + 300))
        assertTrue("ACTION_DOWN 必须 cancel 进行中的动画（铁律③）：$seg", seg.contains("animate().cancel()"))
    }

    @Test
    fun menuItems_givePressFeedbackAndHaptic() {
        assertTrue("菜单项必须有按压反馈", source.contains("pressFeedback("))
        assertTrue("点击应有触感反馈", source.contains("performHapticFeedback("))
    }

    // ---------- ④ 不留无法消失的菜单 ----------

    @Test
    fun pageSwitch_neverLeavesStalePills() {
        val body = methodBody("void onDestinationChanged()")
        assertTrue(
            "切页时必须关闭菜单（否则胶囊会残留）：$body",
            body.contains("closeMenu()"),
        )
        assertTrue(
            "切页时即使没开菜单也要强清胶囊（防止动画中途切页留下残影）：$body",
            body.contains("forceHidePills()"),
        )
    }

    /** 必须有遮罩点击关闭与强制清理两条兜底路径。 */
    @Test
    fun menu_hasScrimDismissAndForceClear() {
        assertTrue("必须有遮罩点击关闭", source.contains("scrim.setOnClickListener(v -> closeMenu())"))
        assertTrue("必须有强制清理胶囊的方法", source.contains("forceHidePills()"))
        assertTrue("强制清理必须把 view 置为 GONE（不是仅透明）",
            source.contains("setVisibility(View.GONE)"))
    }

    // ---------- ③ AI 页避让 ----------

    @Test
    fun aiPage_hasAvoidanceZone() {
        assertTrue("必须为 AI 页留避让区（不能遮挡输入框）", source.contains("AI_SAFE_RATIO"))
    }

    // ---------- 常驻五页 ----------

    @Test
    fun servesAllFiveDestinations() {
        val idx = source.indexOf("ITEM_LABEL")
        assertTrue(idx >= 0)
        val seg = source.substring(idx, minOf(source.length, idx + 200))
        for (label in listOf("软件库", "AI 对话", "下载", "工具箱", "设置")) {
            assertTrue("悬浮导航必须覆盖五大页，缺少「$label」", seg.contains(label))
        }
    }

    /** 不得回退到早期的弧形菜单方案（历史问题：360dp 重叠、裁切）。 */
    @Test
    fun doesNotRegressToArcMenu() {
        assertFalse(
            "不得机械改回早期弧形菜单（360dp 重叠/裁切的老问题）",
            source.contains("ArcMenu") || source.contains("arcLayout"),
        )
    }
}
