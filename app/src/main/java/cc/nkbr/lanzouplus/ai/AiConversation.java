package cc.nkbr.lanzouplus.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话会话模型（移植自 RikkaHub {@code app/src/main/java/me/rerere/rikkahub/data/model/Conversation.kt}）。
 *
 * <p><b>上游对照</b>：{@code Conversation.kt} 的 {@code Conversation} + {@code MessageNode}。
 *
 * <p><b>核心设计（上游最有价值的结构）——消息节点树（分叉）</b>：
 * <ul>
 *   <li>会话不是"消息的线性数组"，而是 <b>节点数组</b>：每个节点代表对话中的一个位置，
 *       节点内含多条候选消息 + 一个 {@code selectIndex}（当前选中哪条）。</li>
 *   <li>这样"重新生成"不是删掉旧回复，而是<b>往同一节点追加一条候选</b>，用户可在候选间切换
 *       （上游 UI 显示 "2/3" 分支切换器）。这是 ChatGPT 式体验的基础。</li>
 *   <li>{@code currentMessages()} = 每个节点取 selectIndex 那条 → 拼成实际发给模型的消息列表。</li>
 * </ul>
 *
 * <p><b>本地化取舍</b>：上游用 Room + JSON 树存储；本实现用 org.json 持久化到 SharedPreferences
 * （与现有 AiChatCore 的存储方式一致，零迁移成本）。字段名与上游对齐便于将来互通。
 */
public final class AiConversation {

  public String id;
  public String assistantId = "";
  public String title = "";
  /** 消息节点列表（时间正序）——分叉结构的核心 */
  public final List<Node> nodes = new ArrayList<>();
  public boolean pinned = false;
  public long createdAt;
  public long updatedAt;

  public AiConversation() {
    this.id = "c" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xFFFF));
    this.createdAt = System.currentTimeMillis();
    this.updatedAt = createdAt;
  }

  /** 当前实际消息序列（每节点取选中候选）——对齐上游 currentMessages */
  public List<AiMessage> currentMessages() {
    List<AiMessage> out = new ArrayList<>();
    for (Node n : nodes) {
      AiMessage m = n.selected();
      if (m != null) out.add(m);
    }
    return out;
  }

  /** 追加一个新节点（新消息）。返回该节点。 */
  public Node append(AiMessage message) {
    Node n = new Node();
    n.messages.add(message);
    n.selectIndex = 0;
    nodes.add(n);
    touch();
    return n;
  }

  /**
   * 把一条消息写回会话（按 id 匹配：命中则原地替换，否则新建节点）——
   * 对齐上游 updateCurrentMessages 的"流式重试按固定消息 ID 覆盖分支"策略。
   */
  public void upsert(AiMessage message) {
    if (message == null) return;
    for (Node n : nodes) {
      for (int i = 0; i < n.messages.size(); i++) {
        if (n.messages.get(i).id.equals(message.id)) {
          n.messages.set(i, message);
          touch();
          return;
        }
      }
    }
    append(message);
  }

  /**
   * 重新生成：为最后一个节点追加一条候选消息并选中它（不删除旧候选）——
   * 这是上游"分叉"语义，用户可来回切换。
   */
  public Node addBranch(AiMessage message) {
    if (nodes.isEmpty()) return append(message);
    Node last = nodes.get(nodes.size() - 1);
    last.messages.add(message);
    last.selectIndex = last.messages.size() - 1;
    touch();
    return last;
  }

  /** 切换某节点的选中候选（分支切换器） */
  public void selectBranch(int nodeIndex, int messageIndex) {
    if (nodeIndex < 0 || nodeIndex >= nodes.size()) return;
    Node n = nodes.get(nodeIndex);
    if (messageIndex < 0 || messageIndex >= n.messages.size()) return;
    n.selectIndex = messageIndex;
    touch();
  }

  public void removeLastNode() {
    if (!nodes.isEmpty()) {
      nodes.remove(nodes.size() - 1);
      touch();
    }
  }

  public int messageCount() {
    return nodes.size();
  }

  public void touch() {
    updatedAt = System.currentTimeMillis();
  }

  /** 从已有消息推断标题（对齐上游自动标题） */
  public void deriveTitleFromFirstUser() {
    if (title != null && !title.isEmpty() && !"新对话".equals(title)) return;
    for (AiMessage m : currentMessages()) {
      if (AiMessage.ROLE_USER.equals(m.role)) {
        String t = m.text().trim();
        if (!t.isEmpty()) {
          title = t.length() > 16 ? t.substring(0, 16) + "…" : t;
          return;
        }
      }
    }
  }

  public JSONObject toJson() {
    try {
      JSONObject o = new JSONObject();
      o.put("id", id);
      o.put("assistantId", assistantId);
      o.put("title", title);
      o.put("pinned", pinned);
      o.put("createdAt", createdAt);
      o.put("updatedAt", updatedAt);
      JSONArray arr = new JSONArray();
      for (Node n : nodes) arr.put(n.toJson());
      o.put("nodes", arr);
      return o;
    } catch (Exception e) {
      return new JSONObject();
    }
  }

  public static AiConversation fromJson(JSONObject o) {
    AiConversation c = new AiConversation();
    if (o == null) return c;
    c.id = o.optString("id", c.id);
    c.assistantId = o.optString("assistantId", "");
    c.title = o.optString("title", "");
    c.pinned = o.optBoolean("pinned", false);
    c.createdAt = o.optLong("createdAt", c.createdAt);
    c.updatedAt = o.optLong("updatedAt", c.updatedAt);
    JSONArray arr = o.optJSONArray("nodes");
    if (arr != null) {
      for (int i = 0; i < arr.length(); i++) {
        Node n = Node.fromJson(arr.optJSONObject(i));
        if (n != null) c.nodes.add(n);
      }
    } else {
      // 兼容旧格式（messages 线性数组）
      JSONArray legacy = o.optJSONArray("messages");
      if (legacy != null) {
        for (int i = 0; i < legacy.length(); i++) {
          AiMessage m = AiMessage.fromJson(legacy.optJSONObject(i));
          if (m != null) c.append(m);
        }
      }
    }
    return c;
  }

  /** 消息节点（对齐上游 MessageNode）：一个位置 + 多个候选消息 + 当前选中 */
  public static final class Node {
    public String id = "n" + System.currentTimeMillis() + "-" + Integer.toHexString((int) (Math.random() * 0xFFFF));
    public final List<AiMessage> messages = new ArrayList<>();
    /** 当前选中的候选下标（上游 selectIndex） */
    public int selectIndex = 0;

    public AiMessage selected() {
      if (messages.isEmpty()) return null;
      int i = Math.max(0, Math.min(messages.size() - 1, selectIndex));
      return messages.get(i);
    }

    public boolean hasBranches() {
      return messages.size() > 1;
    }

    public JSONObject toJson() {
      try {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("selectIndex", selectIndex);
        JSONArray arr = new JSONArray();
        for (AiMessage m : messages) arr.put(m.toJson());
        o.put("messages", arr);
        return o;
      } catch (Exception e) {
        return new JSONObject();
      }
    }

    public static Node fromJson(JSONObject o) {
      if (o == null) return null;
      Node n = new Node();
      n.id = o.optString("id", n.id);
      n.selectIndex = o.optInt("selectIndex", 0);
      JSONArray arr = o.optJSONArray("messages");
      if (arr != null) {
        for (int i = 0; i < arr.length(); i++) {
          AiMessage m = AiMessage.fromJson(arr.optJSONObject(i));
          if (m != null) n.messages.add(m);
        }
      }
      return n;
    }
  }
}
