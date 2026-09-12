package cc.nkbr.lanzouplus.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文窗口管理（移植自 RikkaHub {@code ai/src/main/java/me/rerere/ai/ui/Message.kt} 的
 * {@code limitContext} / {@code alignContextStart}）。
 *
 * <p><b>上游设计意图（原文注释摘要）</b>：
 * "按阶梯式(滞回)策略限制上下文消息数量。与每轮平移一条的滑动窗口不同，截断点只在消息数越过 limit 时才前进一大步，
 * 在此之后的连续多轮里保持不动，使请求前缀保持稳定，从而命中提示词缓存。截断点仅由消息条数推导，
 * 不需要额外持久化状态，且对追加消息天然稳定。保留的条数始终落在 [limit * KEEP_RATIO, limit) 区间内。"
 *
 * <p><b>为什么值得移植</b>：普通滑动窗口每轮都把截断点后移一条 → 请求前缀每轮都变 → 提示词缓存全部失效 →
 * 成本与延迟都上升。阶梯式让截断点在多轮内保持不动，前缀稳定，命中缓存。
 *
 * <p><b>本地化取舍</b>：上游用 Kotlin 扩展函数；本实现用静态方法（纯 Java 无扩展函数），
 * 算法逻辑逐行对齐，常量保持一致（KEEP_RATIO = 0.5）。
 */
public final class ContextLimiter {

  /** 截断后保留的消息条数占上限的比例（对齐上游 CONTEXT_KEEP_RATIO）。越小则步幅越大、命中缓存的轮数越多，但一次丢弃的上下文也越多。 */
  private static final float KEEP_RATIO = 0.5f;

  /**
   * 按阶梯式（滞回）策略限制上下文消息数量。
   *
   * @param messages 完整消息列表（时间正序）
   * @param limit    触发截断的消息条数上限，&lt;= 0 表示不限制
   * @return 截断后的消息子列表（保持原顺序）
   */
  public static List<AiMessage> limitContext(List<AiMessage> messages, int limit) {
    if (messages == null || messages.isEmpty()) return messages == null ? new ArrayList<>() : messages;
    if (limit <= 0 || messages.size() <= limit) return messages;

    // 截断后回落到的目标条数，以及两次截断之间截断点前进的步幅
    // limit 为 1 时无法构造滞回（步幅至少为 1），此时退化为逐条平移的滑动窗口
    int target = Math.round(limit * KEEP_RATIO);
    target = Math.max(1, Math.min(limit, target));
    int stride = Math.max(1, limit - target);

    // 每越过一级台阶，截断点前进 stride 条；台阶之内截断点不动
    // 上界兜底保证至少保留一条消息，正常路径（limit >= 2）不会触发
    int startIndex = Math.min((((messages.size() - limit) / stride) + 1) * stride, messages.size() - 1);

    return messages.subList(alignContextStart(messages, startIndex), messages.size());
  }

  /**
   * 将截断起点回退到安全边界，避免把 tool call 与其结果拆散，或让上下文从半截的工具调用开始。
   *
   * <p>只会向前（下标减小）调整，因此不会破坏 limitContext 保留条数的下界。
   * 调整只依赖 [0, startIndex] 区间内的消息，这部分在追加新消息时不会变化，结果因此保持稳定。
   */
  private static int alignContextStart(List<AiMessage> messages, int startIndex) {
    int adjusted = startIndex;
    boolean needsAdjustment = true;
    java.util.Set<Integer> visited = new java.util.HashSet<>();

    while (needsAdjustment && adjusted > 0) {
      needsAdjustment = false;

      // 防止无限循环
      if (visited.contains(adjusted)) break;
      visited.add(adjusted);

      AiMessage current = messages.get(adjusted);

      // 如果当前消息包含已执行的 tool（有 output），往前查找对应的 tool call
      if (hasExecutedTool(current)) {
        for (int i = adjusted - 1; i >= 0; i--) {
          if (hasPendingTool(messages.get(i))) {
            adjusted = i;
            needsAdjustment = true;
            break;
          }
        }
      }

      // 如果当前消息包含未执行的 tool call，往前查找对应的用户消息
      if (hasPendingTool(current)) {
        for (int i = adjusted - 1; i >= 0; i--) {
          if (AiMessage.ROLE_USER.equals(messages.get(i).role)) {
            adjusted = i;
            needsAdjustment = true;
            break;
          }
        }
      }
    }

    return adjusted;
  }

  /** 含已执行工具（有输出）——对齐上游 isExecuted */
  private static boolean hasExecutedTool(AiMessage m) {
    if (m == null) return false;
    for (AiMessage.Part p : m.parts) {
      if (AiMessage.PART_TOOL.equals(p.type) && p.toolOutput != null && !p.toolOutput.isEmpty()) return true;
    }
    return false;
  }

  /** 含未执行工具调用（无输出）——对齐上游 !isExecuted */
  private static boolean hasPendingTool(AiMessage m) {
    if (m == null) return false;
    for (AiMessage.Part p : m.parts) {
      if (AiMessage.PART_TOOL.equals(p.type) && (p.toolOutput == null || p.toolOutput.isEmpty())) return true;
    }
    return false;
  }

  private ContextLimiter() {}
}
