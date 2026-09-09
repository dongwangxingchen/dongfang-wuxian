package cc.nkbr.lanzouplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** AI 对话核心:OpenAI 兼容中转站客户端(SSE 流式)+ 会话持久化 + 出站 URL 安全校验。
 *  协议参照 openai-java / langchain4j 的开源实现约定(delta.content/delta.reasoning/[DONE]),零第三方依赖。 */
final class AiChatCore {
  static final class Settings {
    String name = "", url = "", key = "", model = "", provider = "";
    boolean toolsEnabled = true;
    int contextMessages = 12, maxTokens = 2048;
    JSONObject toJson() {try{return new JSONObject().put("name",name).put("url",url).put("key",key).put("model",model).put("provider",provider).put("tools",toolsEnabled).put("ctx",contextMessages).put("max",maxTokens);}catch(Exception e){return new JSONObject();}}
    static Settings from(JSONObject o) {Settings s=new Settings();try{s.name=o.optString("name");s.url=o.optString("url");s.key=o.optString("key");s.model=o.optString("model");s.provider=o.optString("provider");s.toolsEnabled=o.optBoolean("tools",true);s.contextMessages=Math.max(2,o.optInt("ctx",12));s.maxTokens=Math.max(64,o.optInt("max",2048));}catch(Exception ignored){}return s;}
  }
  static final class Message {
    String role = "user", content = "", reasoning = "";
    String toolCallsJson = "", toolCallId = "", toolName = "";
    Message() {}
    Message(String role,String content) {this.role=role;this.content=content==null?"":content;}
    JSONObject toJson() {try{JSONObject o=new JSONObject().put("role",role).put("content",content);if(!reasoning.isEmpty())o.put("reasoning",reasoning);if(!toolCallsJson.isEmpty())o.put("tcalls",toolCallsJson);if(!toolCallId.isEmpty())o.put("tcid",toolCallId);if(!toolName.isEmpty())o.put("tname",toolName);return o;}catch(Exception e){return new JSONObject();}}
    static Message from(JSONObject o) {Message m=new Message(o.optString("role","user"),o.optString("content"));try{m.reasoning=o.optString("reasoning");m.toolCallsJson=o.optString("tcalls");m.toolCallId=o.optString("tcid");m.toolName=o.optString("tname");}catch(Exception ignored){}return m;}
  }
  static final class Session {
    String id, title = "新对话";
    long createdAt;
    final List<Message> messages = new ArrayList<>();
    JSONObject toJson() {try{JSONArray a=new JSONArray();for(Message m:messages)a.put(m.toJson());return new JSONObject().put("id",id).put("title",title).put("at",createdAt).put("messages",a);}catch(Exception e){return new JSONObject();}}
    static Session from(JSONObject o) {Session s=new Session();try{s.id=o.optString("id");s.title=o.optString("title","新对话");s.createdAt=o.optLong("at");JSONArray a=o.optJSONArray("messages");if(a!=null)for(int i=0;i<a.length();i++)s.messages.add(Message.from(a.optJSONObject(i)));}catch(Exception ignored){}return s;}
  }
  /** v1.2.2:function calling 的单次工具调用 */
  static final class ToolCall {final String id,name,arguments;ToolCall(String id,String name,String arguments){this.id=id==null?"":id;this.name=name==null?"":name;this.arguments=arguments==null||arguments.trim().isEmpty()?"{}":arguments;}}
  interface StreamListener {
    void onOpen();
    void onDelta(String content,String reasoning);
    void onDone(String fullContent,String reasoning,String error);
    default void onToolCalls(List<ToolCall> calls) {}
  }
  static final class Request implements AutoCloseable {
    volatile HttpURLConnection connection;volatile boolean cancelled;
    public void close() {cancelled=true;HttpURLConnection c=connection;if(c!=null)try{c.disconnect();}catch(Exception ignored){}}
  }
  private final Context context;private final Handler ui=new Handler(Looper.getMainLooper());
  AiChatCore(Context context) {this.context=context;}
  private SharedPreferences prefs() {return context.getSharedPreferences("ai_chat_settings",Context.MODE_PRIVATE);}
  Settings settings() {try{return Settings.from(new JSONObject(prefs().getString("config","{}")));}catch(Exception e){return new Settings();}}
  void saveSettings(Settings s) {prefs().edit().putString("config",s.toJson().toString()).apply();}
  boolean configured() {Settings s=settings();return !s.url.isEmpty()&&!s.key.isEmpty()&&!s.model.isEmpty();}
  List<Session> sessions() {
    List<Session> out=new ArrayList<>();
    try {JSONArray a=new JSONArray(prefs().getString("sessions","[]"));for(int i=0;i<a.length();i++)out.add(Session.from(a.getJSONObject(i)));}catch(Exception ignored){}
    return out;
  }
  void saveSessions(List<Session> sessions) {
    try {JSONArray a=new JSONArray();int start=Math.max(0,sessions.size()-30);for(int i=start;i<sessions.size();i++)a.put(sessions.get(i).toJson());prefs().edit().putString("sessions",a.toString()).apply();}catch(Exception ignored){}
  }
  /** 出站安全校验:仅 http/https;拒绝 localhost、环回、私有与保留地址。返回空串表示通过,否则为原因。 */
  static String validateOutboundUrl(String raw) {
    String value=raw==null?"":raw.trim();
    if(value.isEmpty())return "请填写 API 地址";
    if(!value.matches("(?i)^[a-z][a-z0-9+.-]*://.*"))value="https://"+value;
    URL url;
    try {url=new URL(value);}catch(Exception e){return "API 地址格式不正确";}
    String scheme=url.getProtocol().toLowerCase(Locale.ROOT);
    if(!scheme.equals("http")&&!scheme.equals("https"))return "仅支持 http/https 地址";
    String host=url.getHost();
    if(host==null||host.isEmpty())return "API 地址缺少主机名";
    String lower=host.toLowerCase(Locale.ROOT).replace("[","").replace("]","");
    if(lower.equals("localhost")||lower.endsWith(".localhost")||lower.endsWith(".local")||lower.endsWith(".internal")||lower.equals("0.0.0.0"))return "不允许使用本机或内网地址";
    if(lower.contains(":")) {String v6=lower;while(v6.startsWith(":"))v6=v6.substring(1);if(v6.isEmpty()||v6.startsWith("fe8")||v6.startsWith("fe9")||v6.startsWith("fea")||v6.startsWith("feb")||v6.startsWith("fd")||v6.startsWith("fc"))return "不允许使用本机或内网地址";}
    java.util.regex.Matcher m=java.util.regex.Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$").matcher(lower);
    if(m.matches()) {
      try {
        int a=Integer.parseInt(m.group(1)),b=Integer.parseInt(m.group(2));
        if(a==0||a==10||a==127||a==169&&b==254||a==172&&b>=16&&b<=31||a==192&&b==168||a==100&&b>=64&&b<=127||a>=224)return "不允许使用内网或保留地址";
      }catch(Exception e){return "API 地址格式不正确";}
    }
    return "";
  }
  /** DNS 解析级复核:拦截解析到环回/私有/保留地址的域名(防 DNS 重绑定)。在后台线程调用。 */
  static String validateResolvedHost(String host) {
    try {
      for(InetAddress address:InetAddress.getAllByName(host)) {
        if(address.isLoopbackAddress()||address.isAnyLocalAddress()||address.isLinkLocalAddress()||address.isSiteLocalAddress()||address.isMulticastAddress())return "该域名解析到内网地址，已拒绝";
        byte[] b=address.getAddress();
        if(b!=null&&b.length==4) {int a=b[0]&0xFF,second=b[1]&0xFF;if(a==100&&second>=64&&second<=127)return "该域名解析到保留地址，已拒绝";}
      }
    }catch(Exception e){return "无法解析 API 域名";}
    return "";
  }
  interface ModelsCallback {void onResult(List<String> models,String error);}
  void fetchModels(final ModelsCallback callback) {
    final Settings s=settings();String invalid=validateOutboundUrl(s.url);
    if(!invalid.isEmpty()) {callback.onResult(null,invalid);return;}
    new Thread(() -> {
      List<String> models=new ArrayList<>();String error="";
      try {
        String resolved=validateResolvedHost(new URL(normalizeBase(s.url)).getHost());
        if(!resolved.isEmpty())throw new java.io.IOException(resolved);
        HttpURLConnection c=(HttpURLConnection)new URL(normalizeBase(s.url)+"/models").openConnection();
        c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setRequestProperty("Authorization","Bearer "+s.key);
        int code=c.getResponseCode();String body=read(c);
        if(code!=200)throw new java.io.IOException("HTTP "+code);
        JSONObject root=new JSONObject(body);JSONArray array=root.optJSONArray("data");
        if(array!=null)for(int i=0;i<array.length();i++) {String id=array.optJSONObject(i)==null?"":array.optJSONObject(i).optString("id");if(!id.isEmpty())models.add(id);}
      }catch(Exception e){error=e.getMessage()==null?"获取模型列表失败":e.getMessage();}
      final List<String> out=models;final String err=error;
      ui.post(() -> callback.onResult(out,err));
    },"ai-models").start();
  }
  static String normalizeBase(String url) {
    String value=url==null?"":url.trim();
    if(!value.matches("(?i)^[a-z][a-z0-9+.-]*://.*"))value="https://"+value;
    while(value.endsWith("/"))value=value.substring(0,value.length()-1);
    // v1.2.2:已带版本段的地址(智谱 /v4、豆包 /v3 等)不再追加 /v1
    if(!value.matches("(?i).*/v\\d+$"))value=value+"/v1";
    return value;
  }
  /** 流式对话。返回 Request 用于取消;回调全部回主线程。 */
  Request chat(final Settings s,final List<Message> context,final StreamListener listener) {
    final Request request=new Request();
    new Thread(() -> {
      String full="",reasoning="",error="";
      try {
        String invalid=validateOutboundUrl(s.url);
        if(!invalid.isEmpty())throw new java.io.IOException(invalid);
        String host=new URL(normalizeBase(s.url)).getHost();
        String resolved=validateResolvedHost(host);
        if(!resolved.isEmpty())throw new java.io.IOException(resolved);
        JSONArray payload=new JSONArray();
        int from=Math.max(0,context.size()-Math.max(1,s.contextMessages));
        for(int i=from;i<context.size();i++) {Message m=context.get(i);
          if(m.toolCallsJson!=null&&!m.toolCallsJson.isEmpty()){JSONObject o=new JSONObject().put("role","assistant").put("content",m.content==null?"":m.content);try{o.put("tool_calls",new JSONArray(m.toolCallsJson));}catch(Exception ignored){}payload.put(o);}
          else if(m.toolCallId!=null&&!m.toolCallId.isEmpty())payload.put(new JSONObject().put("role","tool").put("tool_call_id",m.toolCallId).put("content",m.content==null?"":m.content));
          else payload.put(new JSONObject().put("role",m.role).put("content",m.content==null?"":m.content));}
        JSONObject body=new JSONObject().put("model",s.model).put("messages",payload).put("max_tokens",s.maxTokens).put("stream",true);
        if(s.toolsEnabled){try{body.put("tools",AiTools.openaiToolSchemas());body.put("tool_choice","auto");}catch(Exception ignored){}}
        HttpURLConnection c=(HttpURLConnection)new URL(normalizeBase(s.url)+"/chat/completions").openConnection();
        request.connection=c;
        c.setConnectTimeout(15000);c.setReadTimeout(120000);c.setDoOutput(true);c.setRequestMethod("POST");
        c.setRequestProperty("Authorization","Bearer "+s.key);c.setRequestProperty("Content-Type","application/json");
        c.setRequestProperty("Accept","text/event-stream");
        byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);
        OutputStream out=c.getOutputStream();out.write(bytes);out.close();
        int code=c.getResponseCode();
        if(code!=200)throw new java.io.IOException(read(c).isEmpty()?("HTTP "+code):compactError(read(c),code));
        ui.post(listener::onOpen);
        BufferedReader reader=new BufferedReader(new InputStreamReader(c.getInputStream(),StandardCharsets.UTF_8));
        String line;
        java.util.Map<Integer,ToolCall> acc=new java.util.LinkedHashMap<>();
        try {
        while(!request.cancelled&&(line=reader.readLine())!=null) {
          if(!line.startsWith("data:"))continue;
          String data=line.substring(5).trim();
          if(data.equals("[DONE]"))break;
          JSONObject chunk;
          try {chunk=new JSONObject(data);}catch(Exception ignored){continue;}
          JSONArray choices=chunk.optJSONArray("choices");
          if(choices==null||choices.length()==0)continue;
          JSONObject delta=choices.optJSONObject(0)==null?null:choices.optJSONObject(0).optJSONObject("delta");
          if(delta==null)continue;
          JSONArray tcalls=delta.optJSONArray("tool_calls");
          if(tcalls!=null)for(int i=0;i<tcalls.length();i++){JSONObject t=tcalls.optJSONObject(i);if(t==null)continue;int idx=t.optInt("index",i);ToolCall cur=acc.get(idx);JSONObject fn=t.optJSONObject("function");String name=fn==null?"":fn.optString("name");String args=fn==null?"":fn.optString("arguments");if(cur==null){cur=new ToolCall(t.optString("id"),name,args);acc.put(idx,cur);}else{String id=t.optString("id");if(!id.isEmpty()&&cur.id.isEmpty()){acc.put(idx,new ToolCall(id,cur.name.isEmpty()?name:cur.name,cur.arguments+args));continue;}if(!name.isEmpty()&&cur.name.isEmpty()){acc.put(idx,new ToolCall(cur.id,name,args+cur.arguments));continue;}acc.put(idx,new ToolCall(cur.id,cur.name.isEmpty()?name:cur.name,cur.arguments+args));}continue;}
          String piece=delta.optString("content","");
          String think=firstNonEmpty(delta.optString("reasoning_content"),delta.optString("reasoning"));
          if(!piece.isEmpty()) {full+=piece;ui.post(() -> listener.onDelta(piece,""));}
          else if(!think.isEmpty()) {reasoning+=think;ui.post(() -> listener.onDelta("",think));}
        }
        } finally {try{reader.close();}catch(Exception ignored){}}
        if(!acc.isEmpty()&&!request.cancelled) {
          java.util.List<ToolCall> calls=new ArrayList<>();
          for(int k=0;k<acc.size();k++){ToolCall tc=acc.get(k);if(tc!=null&&!tc.name.isEmpty())calls.add(tc);}
          if(!calls.isEmpty()){ui.post(() -> listener.onToolCalls(calls));return;}
        }
      }catch(Exception e) {
        if(request.cancelled) {final String cancelledFull=full,cancelledThink=reasoning;ui.post(() -> listener.onDone(cancelledFull,cancelledThink,null));return;}
        error=e.getMessage()==null?"请求失败":e.getMessage();
      }
      final String outFull=full,outThink=reasoning,outError=error;
      ui.post(() -> listener.onDone(outFull,outThink,outError));
    },"ai-chat").start();
    return request;
  }
  private static String firstNonEmpty(String a,String b) {return a==null||a.isEmpty()?b==null?"":b:a;}
  private static String read(HttpURLConnection c) {
    try {java.io.InputStream in=c.getErrorStream()!=null?c.getErrorStream():c.getInputStream();if(in==null)return "";BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();String line;while((line=r.readLine())!=null)b.append(line);return b.toString();}catch(Exception e){return "";}
  }
  private static String compactError(String raw,int code) {
    try {JSONObject o=new JSONObject(raw);String info=firstNonEmpty(o.optString("error",null)==null?"":o.optJSONObject("error")==null?o.optString("error"):o.optJSONObject("error").optString("message"),o.optString("message"));if(!info.isEmpty())return info;}catch(Exception ignored){}
    return "HTTP "+code;
  }
}
