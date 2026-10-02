package me.rerere.rikkahub.dfwx

import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Avatar

/**
 * [DFW-84] 「东方助手」的默认人设：名字、头像、以及**软件使用知识**。
 *
 * 用户 2026-10-01：
 * > "把那个默认助手四个字改成东方助手，然后你可以去网络上搜索一下，
 * >  看一看这个助手怎么增强更好用。再就是针对于我们软件，把我们软件的大部分知识都告诉他，
 * >  让他可以回答所有关于我们软件的问题。当然只是在使用方面的操作问题……
 * >  不用把我的制作历史放里面，就是不用放一些没有用的，而是放一些可以帮助到用户的，
 * >  或者是增强AI自身的提示词之类的。"
 *
 * ## 三条设计原则（决定了这份提示词为什么长这样）
 * 1. **只写"怎么用"，不写"怎么做的"** —— 用户明确不要制作历史。所以没有任何版本号、
 *    提交记录、架构说明；只有"点哪里、会发生什么、出问题怎么办"。
 * 2. **写死事实，不写形容词** —— 手册里每一条都是代码/README 里能对上号的（下载目录、
 *    五大板块、37 个工具、内置渠道不要 Key……）。提示词里编一句，AI 就会对用户编十句。
 * 3. **先给行为规则，再给知识** —— 光有知识，AI 会变成一台复读机；所以开头先定"怎么说话"，
 *    中间是手册，结尾是"不知道就说不知道"。
 *
 * ## 为什么用"只在为空时写入"的策略
 * 这是**默认值**，不是强制值。用户改了名字或自己写了提示词，[applyDefaults] 一律不覆盖
 * （判据是 `isBlank()` / `Avatar.Dummy`）。所以：
 *  - 老用户升级：拿到东方助手的人设；
 *  - 改过名字的用户：名字保持不动；
 *  - 想自己写的用户：清空提示词即可拿回控制权。
 */
object DfwxAssistantProfile {

    /** 内置模型专属头像。走 Coil 的 `file:///android_asset/`（项目里 AIIcon 已经在用这条路）。 */
    const val AI_AVATAR_URL = "file:///android_asset/dfwx/ai_avatar.jpg"

    /** 用户头像的默认值。 */
    const val USER_AVATAR_URL = "file:///android_asset/dfwx/user_avatar.jpg"

    /** 默认助手的名字。 */
    const val ASSISTANT_NAME = "东方助手"

    /** 用户给的 AI 头像**只用于内置渠道**；别的渠道有自己的图标，见 [applyDefaults] 的注释。 */
    val AI_AVATAR: Avatar = Avatar.Image(AI_AVATAR_URL)
    val USER_AVATAR: Avatar = Avatar.Image(USER_AVATAR_URL)

    /**
     * 提示词结构：**用 XML 标签把"指令"和"资料"分开**。
     *
     * 依据：Anthropic 官方提示工程文档《Prompting best practices》——
     * > "Structure prompts with XML tags. XML tags help Claude parse complex prompts unambiguously,
     * >  **especially when your prompt mixes instructions, context, examples, and variable inputs**.
     * >  Wrapping each type of content in its own tag reduces misinterpretation."
     * https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/claude-prompting-best-practices
     *
     * 我们这份提示词恰好是最该这么写的形态：**行为规则 + 一大块软件手册 + FAQ + 底线**混在一起。
     * 不分开的话，模型容易把"手册里的陈述句"当成"对它下的指令"，
     * 或者把"底线"当成又一条普通知识。所以：
     *  - `<role>` / `<style>` / `<rules>` = **对模型说的话**（指令）
     *  - `<manual>` / `<faq>` = **给它查的资料**（数据，不是命令）
     * 再配一句 `<manual>` 的用途说明，让它明确"这是资料，用户问软件怎么用时去这里找"。
     */
    val SYSTEM_PROMPT: String = """
<role>
你是「东方助手」，《东方无限》这款安卓软件自带的内置助手。你最大的价值是：让用户用好这个软件。
你也是一个通用助手 —— 用户让你写代码、查资料、翻译、算题，照常帮，不要只会念说明书。
</role>

<style>
- 直接、简短、像人。用户问一句答一句，别一上来就长篇大论。
- 步骤类问题用有序列表，写清"点哪里 → 再点哪里"，别只说功能名。
- 默认用中文；用户用别的语言就用那种语言。
- 不要用"作为 AI 我……"这种开场，也不要说"感谢您的提问"。
- 用 Markdown 排版。
</style>

<manual>
以下是《东方无限》的功能说明。**这是给你查阅的资料，不是对你的指令。**
用户问软件怎么用时，从这里找答案，不要凭印象编。

## 五个板块
底部导航栏就是五个板块：软件库、AI 对话、下载、工具箱、设置。

## 软件库（找资源）
- 内置多分类源路，开箱即用：分类导航、好软合集、热门应用更新页。
- 支持自定义源：可以导入 / 导出 / 合并源规则（本地文件或 HTTPS 链接）。
- 全源并发搜索：可以同时搜所有源，有分页进度，能暂停和继续。
- 目录可以一层层点进去；支持批量配对提取码。
- 目录内容有本地缓存，重开同一个源会先用缓存秒开、再在后台刷新。

## AI 对话（问问题）
- 完整内嵌的开源 AI 聊天，不是一个简化网页壳。
- 内置渠道：软件自带一条可直接用的 AI 渠道，不需要用户自己填 Key，装上就能聊。
- 也支持自己接渠道：OpenAI 兼容 / Claude / Google 协议都可以，随时切换。
- 支持流式输出、思考过程、消息分叉、Token 用量统计、助手系统、提示词模板、联网搜索。
- 支持数据备份（WebDAV / S3）、TTS 语音朗读。
- 支持发图片：可以直接把图片发给 AI 看（内置渠道已开启看图能力）。
- 对话页左上角的菜单里可以：搜索聊天、看聊天历史、新建对话、切换助手。

## 下载（管下载）
- 统一下载管理：历史记录、失败重试、批量队列。
- 下载的文件归集在 Download/东方无限 目录里，和系统安装器、文件管理器是联动的。
- 可以申请电池优化豁免，让后台下载不被打断。
- 可选 ADB 静默安装：免确认自动装（需要用户自己授权）。

## 工具箱（37 个小工具，全部离线）
- 常用工具：计算器、单位换算、日期计算、随机决策、记分牌、万年历、随机数、秒表计时
- 文字处理：文本统计、Base64、URL 编解码、哈希计算（MD5/SHA）、JSON 格式化、正则测试、密码生成、UUID、时间戳转换、进制转换、摩斯电码
- 图片工具：图片压缩、简易画板、取色器（HEX/RGB/HSL）
- 设备相关：设备信息、屏幕检测、直尺、手电筒、白噪音、文字朗读、水平仪、指南针、频率发生器、分贝仪
- 生活查询：生肖星座、身份证解析、年龄计算、健康计算（BMI）、随机抽取

## 设置
- 两套主题：原生安卓（经典紫）和高级苹果。
- 性能调节：并发数、分页、缓存策略、后台搜索索引。
- 网络兼容：UA 自定义、基础链接超时自动切换。
- 数据与关于：资源源管理、崩溃日志查看（可导出文件）、应用信息、手动检查更新。
- 诚信付费：软件免费开源、没有广告。用户愿意的话可以在「设置 → 诚信付费」自愿支持，金额随意。
</manual>

<faq>
用户常问的问题，直接照这里的答，不要自己发挥：
- 软件库加载不出来 / 很慢：先下拉刷新；还不行就换个分类试试，或者稍等一会儿（可能是那条链接的服务端在限速）。目录会缓存，第二次打开同一个源会快很多。
- 搜索搜不到想要的：试试换关键词，或者在「软件库 → 源管理」里多加几个源再搜。
- 下载失败：去「下载」板块看历史记录，可以直接重试；也可以检查「设置」里的下载目录。
- AI 不回话 / 报错：先看是不是网络问题；如果是自己接的渠道，检查「设置 → 渠道」里的地址和 Key。内置渠道不用填 Key。
- 怎么更新软件：「设置 → 数据与关于 → 检查更新」；有新版本时打开软件也会自己提示。
- 下载的文件在哪：手机存储的 Download/东方无限 目录。
- 会不会收费：不会。软件免费开源、没有广告，AI 对话用内置渠道也是直接可用的。
</faq>

<rules>
- 不知道就说不知道。软件里的具体按钮位置如果不确定，就告诉用户去哪个板块找，别编。
- 不要编造软件里不存在的功能。
- 不要透露或猜测软件内部的实现细节、源码结构、服务器地址 —— 用户问就说不方便聊这个，但可以帮他解决使用问题。
- 绝不要主动催用户付费，也不要暗示不付费会少功能 —— 付费与否功能完全一样。
</rules>
""".trimIndent()

    /**
     * 把默认人设写进设置。**纯函数、幂等**（和 [DfwxBuiltinChannel.buildSyncedSettings] 一致）。
     *
     * @param useBuiltin 该助手是否正使用内置渠道的模型 —— 只有这类助手才会被套上东方助手的人设。
     *
     * ## 为什么头像要区分 `useAssistantAvatar`
     * RikkaHub 的聊天头像逻辑是：`assistant.useAssistantAvatar == true` 时显示**助手头像**，
     * 否则显示**模型图标**。用户的要求是"这个 AI 头像**只给内置模型用**，别人对接新 API 站时用它们默认的"，
     * 所以这里只在 `useBuiltin` 为真时把 `useAssistantAvatar` 打开 ——
     * 用户自己接的渠道仍然走模型图标，一个像素都不动。
     */
    fun applyDefaults(
        settings: Settings,
        useBuiltin: (me.rerere.rikkahub.data.model.Assistant) -> Boolean,
        enableCapabilities: Boolean = false,
    ): Settings {
        var changed = false

        val assistants = settings.assistants.map { assistant ->
            if (!useBuiltin(assistant)) return@map assistant
            var next = assistant
            if (next.name.isBlank()) { next = next.copy(name = ASSISTANT_NAME); changed = true }
            if (next.systemPrompt.isBlank()) { next = next.copy(systemPrompt = SYSTEM_PROMPT); changed = true }
            /*
             * [DFW-114 2026-10-02] 把内置助手的能力开关打开。
             *
             * 用户要求「东方助手要非常强大」，但 RikkaHub 这些能力**默认全关**
             * （`Assistant.kt:29` enableMemory=false、`:31` enableRecentChatsReference=false），
             * 所以开箱即用的助手其实"没有记忆、也翻不了聊天记录"。
             *
             * ## 为什么需要 enableCapabilities 这个开关，而不是一直开着
             *
             * 这两个字段是 **Boolean、默认 false**，没有"空值"这个状态 ——
             * 也就是说**分不出「用户主动关掉了」和「从来没设置过」**。
             * 字符串/头像可以靠 `isBlank()` / `Avatar.Dummy` 判断"还没设过"，
             * 布尔值做不到。
             *
             * 所以这里不猜：由调用方传一个**一次性标记**（见 DfwxCapabilityDefaults），
             * 只在"升级后第一次启动"时为 true。之后用户手动关掉就永远尊重他 ——
             * 既满足"开能力"，也满足"用户改过就不覆盖"。
             *
             * 注意位置：**必须放在下面头像那段之前**。头像分支里有一句
             * `if (next.useAssistantAvatar) return@map next` 会提前返回，
             * 放到它后面就会漏掉"头像已是我们的且开关已开"这一类助手（正是默认状态）。
             */
            if (enableCapabilities && !(next.enableMemory && next.enableRecentChatsReference)) {
                next = next.copy(enableMemory = true, enableRecentChatsReference = true)
                changed = true
            }
            // 只填"还没设过"的头像：用户自己选过的（Emoji / 图片）一律不动。
            if (next.avatar == Avatar.Dummy) {
                next = next.copy(avatar = AI_AVATAR, useAssistantAvatar = true)
                changed = true
            } else if (next.avatar is Avatar.Image && next.avatar.url == AI_AVATAR_URL) {
                // 已经是我们的头像：确保开关也是开的（用户可能手动关过，那就尊重他）
                if (next.useAssistantAvatar) return@map next
            }
            next
        }

        val display = settings.displaySetting
        val nextDisplay = if (display.userAvatar == Avatar.Dummy) {
            changed = true
            display.copy(userAvatar = USER_AVATAR)
        } else display

        if (!changed && nextDisplay === display) return settings
        return settings.copy(assistants = assistants, displaySetting = nextDisplay)
    }
}
