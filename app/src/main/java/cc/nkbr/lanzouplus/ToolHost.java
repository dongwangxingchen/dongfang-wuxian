package cc.nkbr.lanzouplus;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/**
 * 工具箱宿主：列表页 = 奇妙工具箱式「组标题 + 个数徽章 + 折叠箭头 + 双列彩色 chip 流」，
 * 工具页 = 预览(若有) + 参数 + 动作 + 结果 + 复制。逻辑全在 Toolbox，本类只做 UI。
 * 命名与压缩风格跟随 MainActivity；颜色/动效 token 与全局黑曜紫一致。
 */
final class ToolHost {
  /** MainActivity 注入的上下文缩写（仅用到其公开 helper；Java 内部类可直接访问外部实例字段，这里用构造注入保留扩展余地） */
  private final MainActivity act;
  LinearLayout toolBody;

  ToolHost(MainActivity activity){act=activity;}

  //—— 列表页 ——

  /** 工具箱首页：搜索 + 「功能大全 / 热门排行」Tab + 分组折叠 chip 流 */
  void renderList(){
    act.primaryHeader("工具箱");
    LinearLayout body=new LinearLayout(act);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(act.dp(2),act.dp(4),act.dp(2),act.dp(16));
    // 搜索框（本地：名称/说明/关键词/分类）
    EditText search=toolSearchInput(body);
    final List<String>[] resultHolder=new List[]{null};
    LinearLayout resultBox=new LinearLayout(act);resultBox.setOrientation(LinearLayout.VERTICAL);body.addView(resultBox,new LinearLayout.LayoutParams(-1,-2));
    // Tab：功能大全 / 热门排行
    LinearLayout tabs=new LinearLayout(act);tabs.setGravity(Gravity.CENTER);tabs.setBackground(ripple(solid(Color.TRANSPARENT)));tabs.setPadding(act.dp(4),act.dp(4),act.dp(4),act.dp(4));
    TextView tabAll=tabChip("功能大全",true),tabHot=tabChip("热门排行",false);
    tabs.addView(tabAll,new LinearLayout.LayoutParams(0,act.dp(38),1));
    tabs.addView(tabHot,new LinearLayout.LayoutParams(0,act.dp(38),1));
    LinearLayout tabsWrap=new LinearLayout(act);tabsWrap.setOrientation(LinearLayout.VERTICAL);tabsWrap.addView(tabs,new LinearLayout.LayoutParams(-1,act.dp(46)));
    body.addView(tabsWrap,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout catalog=new LinearLayout(act);catalog.setOrientation(LinearLayout.VERTICAL);body.addView(catalog,new LinearLayout.LayoutParams(-1,-2));
    Runnable clearResults=()->{resultBox.removeAllViews();resultHolder[0]=null;};
    Runnable showCatalog=()->{clearResults.run();renderGroupedCatalog(catalog);};
    Runnable showHot=()->{clearResults.run();renderHotBoard(catalog);};
    tabAll.setOnClickListener(v->{selectTab(tabAll,tabHot);if(act.motionEnabled())crossFade(catalog,showCatalog);else showCatalog.run();});
    tabHot.setOnClickListener(v->{selectTab(tabHot,tabAll);if(act.motionEnabled())crossFade(catalog,showHot);else showHot.run();});
    search.addTextChangedListener(new android.text.TextWatcher(){
      public void beforeTextChanged(CharSequence s,int a,int b,int c){}
      public void onTextChanged(CharSequence s,int a,int b,int c){}
      public void afterTextChanged(android.text.Editable s){
        String q=s.toString().trim();
        tabsWrap.setVisibility(q.isEmpty()?View.VISIBLE:View.GONE);
        if(q.isEmpty()){if(resultHolder[0]==null)return;resultHolder[0]=null;catalog.setVisibility(View.VISIBLE);renderGroupedCatalog(catalog);return;}
        resultHolder[0]=Toolbox.searchTools(q);catalog.setVisibility(View.VISIBLE);
        renderSearchResults(catalog,q,resultHolder[0]);
      }
    });
    showCatalog.run();
    ScrollView scroll=new ScrollView(act);scroll.setFillViewport(true);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
    act.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    if(act.motionEnabled())enterStagger(scroll);
  }

  EditText toolSearchInput(LinearLayout parent){
    LinearLayout box=new LinearLayout(act);box.setGravity(Gravity.CENTER_VERTICAL);GradientDrawable bg=solid(act.SURFACE);bg.setStroke(act.dp(1),act.DIV);
    box.setBackground(ripple(bg));box.setPadding(act.dp(12),0,act.dp(12),0);
    ImageView icon=new ImageView(act);icon.setImageResource(R.drawable.ic_tool_search);icon.setColorFilter(act.MUTED);box.addView(icon,new LinearLayout.LayoutParams(act.dp(20),act.dp(20)));
    EditText input=new EditText(act);input.setHint("搜索工具…");input.setHintTextColor(act.MUTED);input.setTextColor(act.TEXT);input.setTextSize(14);
    input.setBackground(null);input.setSingleLine(true);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setPadding(act.dp(10),act.dp(12),act.dp(10),act.dp(12));
    box.addView(input,new LinearLayout.LayoutParams(0,act.dp(48),1));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,act.dp(10));
    parent.addView(box,params);return input;
  }

  TextView tabChip(String label,boolean active){
    TextView chip=new TextView(act);chip.setText(label);chip.setTextSize(13);chip.setGravity(Gravity.CENTER);
    chip.setTextColor(active?act.PRIMARY:act.MUTED);chip.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
    chip.setBackground(ripple(solid(active?Color.rgb(46,38,68):Color.TRANSPARENT)));chip.setContentDescription(label+(active?"，已选中":""));
    return chip;
  }
  void selectTab(TextView selected,TextView other){
    selected.setTextColor(act.PRIMARY);other.setTextColor(act.MUTED);
    selected.setBackground(ripple(solid(Color.rgb(46,38,68))));other.setBackground(ripple(solid(Color.TRANSPARENT)));
    selected.setContentDescription(selected.getText()+"，已选中");other.setContentDescription(other.getText()+"");
  }

  /** 功能大全：按分类分组，每组 = 标题 + 个数徽章 + 折叠箭头 + 双列 chip 流；仅第一组默认展开 */
  void renderGroupedCatalog(LinearLayout parent){
    parent.removeAllViews();
    String[][] groups=groupedTools();
    for(int g=0;g<groups.length;g++)parent.addView(toolGroup(groups[g][0],toolsOf(groups[g][0]),g==0),new LinearLayout.LayoutParams(-1,-2));
  }

  String[][] groupedTools(){
    List<String[]> out=new ArrayList<>();
    for(String category:Toolbox.CATEGORIES){int count=0;for(String[] t:Toolbox.TOOLS)if(category.equals(t[5]))count++;if(count>0)out.add(new String[]{category});}
    return out.toArray(new String[0][]);
  }
  List<String> toolsOf(String category){List<String> ids=new ArrayList<>();for(String[] t:Toolbox.TOOLS)if(category.equals(t[5]))ids.add(t[0]);return ids;}

  /** 热门排行：按内置热度排序的编号榜单 */
  void renderHotBoard(LinearLayout parent){
    parent.removeAllViews();
    List<String> hot=Toolbox.hotTools();
    LinearLayout card=listCard();parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
    int rank=1;
    for(String id:hot){
      LinearLayout row=new LinearLayout(act);row.setGravity(Gravity.CENTER_VERTICAL);row.setClickable(true);row.setFocusable(true);
      row.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT)));row.setPadding(act.dp(10),0,act.dp(8),0);
      TextView no=text("#"+rank,13,rank<=3?act.PRIMARY:act.MUTED);no.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);no.setMinWidth(act.dp(34));
      row.addView(no,new LinearLayout.LayoutParams(-2,act.dp(56)));
      row.addView(toolIconView(id,act.dp(30)),new LinearLayout.LayoutParams(act.dp(30),act.dp(30)));
      LinearLayout copy=new LinearLayout(act);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(act.dp(12),0,0,0);
      TextView name=text(Toolbox.toolName(id),15,act.TEXT);name.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);copy.addView(name,new LinearLayout.LayoutParams(-1,act.dp(26)));
      TextView desc=text(Toolbox.toolDesc(id),11,act.MUTED);desc.setSingleLine(true);desc.setEllipsize(TextUtils.TruncateAt.END);copy.addView(desc,new LinearLayout.LayoutParams(-1,act.dp(20)));
      row.addView(copy,new LinearLayout.LayoutParams(0,act.dp(56),1));
      String id0=id;row.setOnClickListener(v->act.openTool(id0));
      card.addView(row,new LinearLayout.LayoutParams(-1,act.dp(56)));
      if(rank<hot.size()){View divider=new View(act);divider.setBackgroundColor(act.DIV);card.addView(divider,new LinearLayout.LayoutParams(-1,act.dp(1)));}
      rank++;
    }
  }

  void renderSearchResults(LinearLayout parent,String query,List<String> ids){
    parent.removeAllViews();
    LinearLayout card=listCard();parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
    TextView head=text("“"+query+"” · "+ids.size()+" 个结果",12,act.MUTED);head.setPadding(act.dp(12),act.dp(10),act.dp(12),act.dp(4));card.addView(head,new LinearLayout.LayoutParams(-1,-2));
    if(ids.isEmpty()){TextView empty=text("没有匹配的工具",13,act.MUTED);empty.setGravity(Gravity.CENTER);empty.setPadding(0,act.dp(16),0,act.dp(20));card.addView(empty,new LinearLayout.LayoutParams(-1,-2));return;}
    GridLayout grid=new GridLayout(act);grid.setColumnCount(2);
    for(String id:ids)grid.addView(categoryChip(id),chipLayout());
    card.addView(grid,new LinearLayout.LayoutParams(-1,-2));
  }

  /** 一组：折叠容器（M3 emphasized easing 240ms，与设置页同一配方） */
  LinearLayout toolGroup(String category,List<String> ids,boolean expanded){
    LinearLayout section=new LinearLayout(act);section.setOrientation(LinearLayout.VERTICAL);GradientDrawable surface=solid(act.SURFACE);surface.setStroke(act.dp(1),act.DIV);
    section.setBackground(surface);section.setClipToOutline(true);
    LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);outer.setMargins(0,0,0,act.dp(10));section.setLayoutParams(outer);
    LinearLayout header=new LinearLayout(act);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(act.dp(14),act.dp(4),act.dp(10),act.dp(4));
    header.setClickable(true);header.setFocusable(true);header.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT)));
    LinearLayout copy=new LinearLayout(act);copy.setOrientation(LinearLayout.VERTICAL);
    TextView title=text(category,15,act.TEXT);title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);copy.addView(title,new LinearLayout.LayoutParams(-1,act.dp(28)));
    LinearLayout titleRow=new LinearLayout(act);titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
    TextView badge=text(ids.size()+" 个",10,act.PRIMARY);
    GradientDrawable pill=solid(Color.argb(30,167,139,250));pill.setCornerRadius(act.dp(20));badge.setBackground(pill);badge.setPadding(act.dp(10),act.dp(2),act.dp(10),act.dp(2));
    titleRow.addView(badge,new LinearLayout.LayoutParams(-2,act.dp(24)));
    header.addView(titleRow,new LinearLayout.LayoutParams(0,act.dp(52),1));
    ImageView arrow=new ImageView(act);arrow.setImageResource(R.drawable.ic_expand);arrow.setColorFilter(act.PRIMARY);arrow.setPadding(act.dp(8),act.dp(8),act.dp(8),act.dp(8));
    header.addView(arrow,new LinearLayout.LayoutParams(act.dp(40),act.dp(52)));
    section.addView(header,new LinearLayout.LayoutParams(-1,act.dp(60)));
    LinearLayout content=new LinearLayout(act);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(act.dp(6),0,act.dp(6),act.dp(8));
    GridLayout grid=new GridLayout(act);grid.setColumnCount(2);
    for(String id:ids)grid.addView(categoryChip(id),chipLayout());
    content.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    section.addView(content,new LinearLayout.LayoutParams(-1,-2));
    content.setVisibility(expanded?View.VISIBLE:View.GONE);arrow.setRotation(expanded?180f:0f);
    header.setContentDescription(category+"，"+(expanded?"已展开":"已收起")+"，点击"+(expanded?"收起":"展开"));
    header.setOnClickListener(v->{boolean open=content.getVisibility()!=View.VISIBLE;header.setContentDescription(category+"，"+(open?"已展开":"已收起")+"，点击"+(open?"收起":"展开"));act.animateSection(section,content,arrow,open);});
    return section;
  }

  /** 单枚工具 chip：彩色小圆底图标 + 名称，胶囊形（奇妙工具箱样式） */
  LinearLayout categoryChip(String id){
    LinearLayout chip=new LinearLayout(act);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setClickable(true);chip.setFocusable(true);
    GradientDrawable bg=solid(act.SURFACE);bg.setStroke(act.dp(1),act.DIV);chip.setBackground(ripple(bg));chip.setPadding(act.dp(10),0,act.dp(12),0);
    chip.addView(toolIconView(id,act.dp(26)),new LinearLayout.LayoutParams(act.dp(26),act.dp(26)));
    TextView name=text(Toolbox.toolName(id),13,act.TEXT);name.setSingleLine(true);name.setPadding(act.dp(8),0,0,0);
    chip.addView(name,new LinearLayout.LayoutParams(0,act.dp(44),1));
    chip.setContentDescription("打开工具："+Toolbox.toolName(id));
    chip.setOnClickListener(v->press(v,()->act.openTool(id)));
    return chip;
  }
  GridLayout.LayoutParams chipLayout(){GridLayout.LayoutParams cell=new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f));cell.width=0;cell.height=act.dp(56);((ViewGroup.MarginLayoutParams)cell).setMargins(act.dp(4),act.dp(4),act.dp(4),act.dp(4));return cell;}

  ImageView toolIconView(String id,int size){
    ImageView icon=new ImageView(act);icon.setImageResource(toolIconRes(Toolbox.toolIcon(id)));
    int tint=Toolbox.iconColor(Toolbox.toolIcon(id));
    GradientDrawable badge=solid((tint&0x00FFFFFF)|0x30000000);badge.setCornerRadius(act.dp(size/2));
    icon.setBackground(badge);icon.setPadding(act.dp(6),act.dp(6),act.dp(6),act.dp(6));icon.setColorFilter(tint);
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return icon;
  }
  static int toolIconRes(String suffix){
    switch(suffix){
      case Toolbox.CALC:return R.drawable.ic_tool_calc;case Toolbox.RULER:return R.drawable.ic_tool_ruler;case Toolbox.CALENDAR:return R.drawable.ic_tool_calendar;
      case Toolbox.DICE:return R.drawable.ic_tool_dice;case Toolbox.EDIT:return R.drawable.ic_tool_edit;case Toolbox.TEXT:return R.drawable.ic_tool_text;
      case Toolbox.COPY:return R.drawable.ic_tool_copy;case Toolbox.REFRESH:return R.drawable.ic_tool_refresh;case Toolbox.OPEN_WITH:return R.drawable.ic_tool_open_with;
      case Toolbox.SEARCH:return R.drawable.ic_tool_search;case Toolbox.IMAGE:return R.drawable.ic_tool_image;case Toolbox.PALETTE:return R.drawable.ic_tool_palette;
      case Toolbox.INFO:return R.drawable.ic_tool_info;case Toolbox.CHECK:return R.drawable.ic_tool_check;case Toolbox.TORCH:return R.drawable.ic_tool_torch;
      case Toolbox.AUDIO:return R.drawable.ic_tool_audio;case Toolbox.VOICE:return R.drawable.ic_tool_voice;case Toolbox.STAR:return R.drawable.ic_tool_star;
      case Toolbox.IDCARD:return R.drawable.ic_tool_idcard;case Toolbox.HEART:return R.drawable.ic_tool_heart;case Toolbox.QR:return R.drawable.ic_tool_copy;
      default:return R.drawable.ic_tools;
    }
  }

  //—— 工具页 ——

  void renderTool(String id){
    if("__screen_test__".equals(id)){act.rebuildToolStackTop();return;}
    String title=Toolbox.toolName(id);
    LinearLayout header=new LinearLayout(act);header.setGravity(Gravity.CENTER_VERTICAL);
    ImageButton back=act.iconButton(R.drawable.ic_back,"返回工具箱");back.setOnClickListener(v->{act.pageDirection=-1;act.popToolBack();});header.addView(back,new LinearLayout.LayoutParams(act.dp(48),act.dp(48)));
    LinearLayout copy=new LinearLayout(act);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(act.dp(8),0,0,0);
    TextView heading=text(title,19,act.TEXT);heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);copy.addView(heading,new LinearLayout.LayoutParams(-1,act.dp(30)));
    TextView sub=text(Toolbox.toolDesc(id),11,act.MUTED);sub.setSingleLine(true);sub.setEllipsize(TextUtils.TruncateAt.END);copy.addView(sub,new LinearLayout.LayoutParams(-1,act.dp(20)));
    header.addView(copy,new LinearLayout.LayoutParams(0,act.dp(52),1));
    act.root.addView(header,new LinearLayout.LayoutParams(-1,act.dp(54)));
    LinearLayout body=new LinearLayout(act);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(act.dp(4),act.dp(8),act.dp(4),act.dp(16));
    toolBody=body;
    ScrollView scroll=new ScrollView(act);scroll.setFillViewport(true);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));act.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    switch(id){
      case "calculator":calculator(body);break;
      case "unit":unit(body);break;
      case "datecalc":datecalc(body);break;
      case "decision":decision(body);break;
      case "scorecard":scorecard(body);break;
      case "calendar":calendarGrid(body);break;
      case "randomnum":randomnum(body);break;
      case "text_stats":{EditText input=input(body,"粘贴文本…",140);LinearLayout actions=actionRow(body);action(actions,"统计",()->output(body,Toolbox.textStats(input.getText().toString())));action(actions,"去重·排序",()->output(body,Toolbox.dedupeLines(input.getText().toString(),true,false)));action(actions,"去重·保序",()->output(body,Toolbox.dedupeLines(input.getText().toString(),false,false)));action(actions,"去空行",()->output(body,Toolbox.dedupeLines(input.getText().toString(),false,true)));result(body);}break;
      case "base64":{EditText input=input(body,"输入文本或 Base64…",140);LinearLayout actions=actionRow(body);action(actions,"编码",()->output(body,Toolbox.base64(true,input.getText().toString())));action(actions,"解码",()->output(body,Toolbox.base64(false,input.getText().toString())));result(body);}break;
      case "url_codec":{EditText input=input(body,"输入文本或已编码 URL…",140);LinearLayout actions=actionRow(body);action(actions,"编码",()->output(body,Toolbox.url(true,input.getText().toString())));action(actions,"解码",()->output(body,Toolbox.url(false,input.getText().toString())));result(body);}break;
      case "hash":{EditText input=input(body,"输入文本…",120);LinearLayout actions=actionRow(body);action(actions,"计算 MD5 / SHA",()->output(body,Toolbox.hashes(input.getText().toString())));result(body);}break;
      case "json":{EditText input=input(body,"粘贴 JSON…",160);LinearLayout actions=actionRow(body);action(actions,"美化",()->output(body,Toolbox.json(true,input.getText().toString())));action(actions,"压缩",()->output(body,Toolbox.json(false,input.getText().toString())));result(body);}break;
      case "regex":{EditText pattern=input(body,"正则表达式，如 \\d+",60);EditText text=input(body,"被匹配的文本…",120);LinearLayout actions=actionRow(body);action(actions,"测试",()->output(body,Toolbox.regex(pattern.getText().toString(),text.getText().toString())));result(body);}break;
      case "password":password(body);break;
      case "uuid":{LinearLayout actions=actionRow(body);action(actions,"生成 1 个",()->output(body,Toolbox.uuidBatch(1)));action(actions,"生成 10 个",()->output(body,Toolbox.uuidBatch(10)));result(body);}break;
      case "img_compress":imageCompress(body);break;
      case "sketch":act.toolHostSketch(body);break;
      case "deviceinfo":deviceinfo(body);break;
      case "screen_test":{TextView info=text("全屏循环纯色（黑/白/红/绿/蓝/灰），点按切换，返回退出。用于检查坏点、漏光与烧屏。",12,act.MUTED);info.setPadding(0,0,0,act.dp(10));body.addView(info,new LinearLayout.LayoutParams(-1,-2));LinearLayout actions=actionRow(body);action(actions,"开始检测",()->act.startScreenTest());result(body);break;}
      case "ruler":act.toolHostRuler(body);break;
      case "torch":torch(body);break;
      case "noise":noise(body);break;
      case "tts":tts(body);break;
      case "level":act.toolHostLevel(body);break;
      case "zodiac":{EditText input=input(body,"出生日期（2000-06-15）",44);LinearLayout actions=actionRow(body);action(actions,"查询生肖星座",()->output(body,Toolbox.zodiac(input.getText().toString())));result(body);}break;
      case "idcard":{EditText input=input(body,"18 位身份证号（仅本机解析，不上传）",44);LinearLayout actions=actionRow(body);action(actions,"解析",()->output(body,Toolbox.parseIdCard(input.getText().toString())));result(body);}break;
      case "agecalc":{EditText input=input(body,"出生日期（2000-06-15）",44);LinearLayout actions=actionRow(body);action(actions,"计算年龄",()->output(body,Toolbox.ageCalc(input.getText().toString())));result(body);}break;
      case "bmi":bmi(body);break;
      default:{TextView info=text("该工具即将上线",13,act.MUTED);body.addView(info,new LinearLayout.LayoutParams(-1,act.dp(48)));break;}
    }
    if(act.motionEnabled())enterStagger(scroll);
  }

  void calculator(LinearLayout body){
    EditText expr=input(body,"表达式，支持 + - * / % ( )",44);expr.setInputType(InputType.TYPE_CLASS_TEXT);expr.setSingleLine(true);
    TextView live=text("0",22,act.TEXT);live.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);live.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
    live.setBackground(solid(act.SURFACE));live.setPadding(act.dp(14),act.dp(8),act.dp(14),act.dp(8));
    body.addView(live,new LinearLayout.LayoutParams(-1,act.dp(56)));
    expr.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}
      public void afterTextChanged(android.text.Editable s){String value=s.toString().trim();if(value.isEmpty()){live.setText("0");return;}String out=Toolbox.calculate(value);live.setText(out.startsWith("表达式")?out:out);live.setTextColor(out.startsWith("表达式")?act.MUTED:act.TEXT);}});
    LinearLayout actions=actionRow(body);
    action(actions,"复制结果",()->{String value=live.getText().toString();if(value.isEmpty()||value.equals("0"))return;copy(value);});
    result(body);
  }

  void unit(LinearLayout body){
    String[] categories={"长度","重量","温度","数据","速度","面积"};
    LinearLayout catRow=chipRow(body);int[] catSel={0};
    TextView[] catChips=new TextView[categories.length];
    final Runnable[] rebuildRef={(Runnable)null};
    for(int i=0;i<categories.length;i++){final int idx=i;catChips[i]=selectChip(catRow,categories[i],i==0,()->{catSel[0]=idx;for(int j=0;j<catChips.length;j++)styleSelect(catChips[j],j==idx);if(rebuildRef[0]!=null)rebuildRef[0].run();});}
    LinearLayout fromRow=chipRow(body),toRow=chipRow(body);
    EditText value=input(body,"输入数值",44);value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);value.setSingleLine(true);
    TextView out=result(body);
    Runnable convert=()->{try{double v=Double.parseDouble(value.getText().toString());out.setText(Toolbox.convertUnit(categories[catSel[0]],v,unitOf(fromRow),unitOf(toRow)));}catch(Exception ignored){}};
    Runnable rebuildUnits=()->{
      fromRow.removeAllViews();toRow.removeAllViews();
      String[] units=unitsOf(categories[catSel[0]]);
      for(int i=0;i<units.length;i++){final int fi=i;selectChip(fromRow,units[i],i==0,convert);}
      for(int i=0;i<units.length;i++){final int ti=i;selectChip(toRow,units[i],i==Math.min(1,units.length-1),convert);}
      convert.run();
    };
    rebuildRef[0]=rebuildUnits;rebuildUnits.run();
    value.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable s){convert.run();}});
    LinearLayout actions=actionRow(body);action(actions,"交换单位",()->{String a=unitOf(fromRow),b=unitOf(toRow);swapChips(fromRow,b);swapChips(toRow,a);convert.run();});
  }
  String[] unitsOf(String category){
    switch(category){
      case "长度":return new String[]{"毫米","厘米","米","千米","英寸","英尺","英里"};
      case "重量":return new String[]{"毫克","克","千克","吨","磅","盎司"};
      case "温度":return new String[]{"摄氏度","华氏度","开尔文"};
      case "数据":return new String[]{"字节","千字节","兆字节","吉字节"};
      case "速度":return new String[]{"米/秒","公里/时","英里/时","节"};
      case "面积":return new String[]{"平方米","平方千米","公顷","亩"};
      default:return new String[]{"1"};
    }
  }
  String unitOf(LinearLayout row){for(int i=0;i<row.getChildCount();i++){View child=row.getChildAt(i);if(child instanceof TextView&&child.isSelected())return((TextView)child).getText().toString();}return row.getChildCount()>0&&row.getChildAt(0) instanceof TextView?((TextView)row.getChildAt(0)).getText().toString():"";}
  void swapChips(LinearLayout row,String selectLabel){for(int i=0;i<row.getChildCount();i++){View child=row.getChildAt(i);if(child instanceof TextView){boolean on=((TextView)child).getText().toString().equals(selectLabel);child.setSelected(on);styleSelect((TextView)child,on);}}}

  void datecalc(LinearLayout body){
    EditText a=input(body,"起始日期（2026-01-01）",44),b=input(body,"结束日期（2026-12-31，算间隔时填）",44),n=input(body,"N（推算 N 天后，可负数）",44);
    n.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);
    LinearLayout actions=actionRow(body);
    action(actions,"算间隔",()->output(body,Toolbox.dateDiff(a.getText().toString(),b.getText().toString())));
    action(actions,"N 天后",()->{try{output(body,Toolbox.dateOffset(a.getText().toString(),Integer.parseInt(n.getText().toString().trim())));}catch(Exception e){output(body,Toolbox.dateOffset(a.getText().toString(),0));}});
    action(actions,"今天日期",()->{java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.CHINA);a.setText(f.format(new java.util.Date()));});
    result(body);
  }

  void decision(LinearLayout body){
    LinearLayout actions=actionRow(body);
    TextView out=result(body);
    action(actions,"抛硬币",()->out.setText(Toolbox.coinFlip()));
    action(actions,"掷骰子",()->out.setText("点数："+Toolbox.diceRoll(6)));
    action(actions,"1-100 随机",()->out.setText(String.valueOf(Toolbox.diceRoll(100))));
    EditText options=input(body,"做个决定：候选用空格分隔（如 吃面 吃饭 麻辣烫）",60);
    action(actions,"帮我决定",()->out.setText(Toolbox.decide(options.getText().toString().trim().split("\\s+"))));
  }

  void scorecard(LinearLayout body){
    int[] scores={0,0};
    LinearLayout row=new LinearLayout(act);
    TextView a=bigScore("甲"),b=bigScore("乙");
    LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,act.dp(120),1);half.setMargins(act.dp(4),0,act.dp(4),0);
    row.addView(a,half);row.addView(b,half);
    body.addView(row,new LinearLayout.LayoutParams(-1,act.dp(120)));
    LinearLayout actions=actionRow(body);
    action(actions,"重置",()->{scores[0]=0;scores[1]=0;a.setText("0");b.setText("0");});
    TextView hint=text("点击分数 +/- 加减；长按清零该侧",11,act.MUTED);hint.setPadding(0,act.dp(6),0,0);body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    a.setOnClickListener(v->{scores[0]++;a.setText(String.valueOf(scores[0]));bump(a);});
    a.setOnLongClickListener(v->{scores[0]=0;a.setText("0");return true;});
    b.setOnClickListener(v->{scores[1]++;b.setText(String.valueOf(scores[1]));bump(b);});
    b.setOnLongClickListener(v->{scores[1]=0;b.setText("0");return true;});
  }
  TextView bigScore(String label){
    LinearLayout wrap=new LinearLayout(act);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setGravity(Gravity.CENTER);
    GradientDrawable bg=solid(act.SURFACE);bg.setStroke(act.dp(1),act.DIV);wrap.setBackground(ripple(bg));
    TextView name=text(label,12,act.MUTED);name.setGravity(Gravity.CENTER);wrap.addView(name,new LinearLayout.LayoutParams(-1,act.dp(24)));
    TextView value=text("0",34,act.TEXT);value.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);value.setGravity(Gravity.CENTER);
    wrap.addView(value,new LinearLayout.LayoutParams(-1,act.dp(64)));
    // 返回内层分数 TextView；用 tag 关联外壳
    value.setTag(wrap);return value;
  }
  void bump(View v){if(!act.motionEnabled())return;v.animate().cancel();v.setScaleX(1f);v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(90).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();}

  void calendarGrid(LinearLayout body){
    java.util.Calendar today=java.util.Calendar.getInstance();
    int[] cursor={today.get(java.util.Calendar.YEAR),today.get(java.util.Calendar.MONTH)};
    TextView title=text("",16,act.TEXT);title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);title.setGravity(Gravity.CENTER);
    GridLayout grid=new GridLayout(act);grid.setColumnCount(7);
    Runnable render=()->{
      grid.removeAllViews();
      int year=cursor[0],month=cursor[1];// month 0-based
      title.setText(year+" 年 "+(month+1)+" 月");
      String[] week={"一","二","三","四","五","六","日"};
      for(String w:week){TextView cell=gridCell();cell.setText(w);cell.setTextColor(act.MUTED);grid.addView(cell,new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f)));}
      int[] cells=Toolbox.calendarGrid(year,month+1);
      int todayY=today.get(java.util.Calendar.YEAR),todayM=today.get(java.util.Calendar.MONTH);
      boolean isThis=todayY==year&&todayM==month;
      int todayD=today.get(java.util.Calendar.DAY_OF_MONTH);
      for(int d:cells){TextView cell=gridCell();
        if(d==0){cell.setText("");grid.addView(cell,new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f)));continue;}
        cell.setText(String.valueOf(d));cell.setGravity(Gravity.CENTER);
        if(isThis&&d==todayD){cell.setTextColor(act.BG);GradientDrawable dot=solid(act.PRIMARY);dot.setCornerRadius(act.dp(18));cell.setBackground(dot);}
        grid.addView(cell,new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f)));
      }
    };
    LinearLayout header=new LinearLayout(act);header.setGravity(Gravity.CENTER_VERTICAL);
    ImageButton prev=act.iconButton(R.drawable.ic_back,"上一个月");prev.setOnClickListener(v->{cursor[1]--;if(cursor[1]<0){cursor[1]=11;cursor[0]--;}render.run();});
    ImageButton next=act.iconButton(R.drawable.ic_refresh,"下一个月");next.setRotation(180f);next.setOnClickListener(v->{cursor[1]++;if(cursor[1]>11){cursor[1]=0;cursor[0]++;}render.run();});
    header.addView(prev,new LinearLayout.LayoutParams(act.dp(44),act.dp(44)));
    header.addView(title,new LinearLayout.LayoutParams(0,act.dp(44),1));
    header.addView(next,new LinearLayout.LayoutParams(act.dp(44),act.dp(44)));
    LinearLayout card=listCard();card.addView(header,new LinearLayout.LayoutParams(-1,act.dp(52)));card.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    body.addView(card,new LinearLayout.LayoutParams(-1,-2));
    render.run();
  }
  TextView gridCell(){TextView cell=text("",13,act.TEXT);cell.setGravity(Gravity.CENTER);cell.setHeight(act.dp(42));return cell;}

  void randomnum(LinearLayout body){
    EditText min=input(body,"下限（默认 1）",44),max=input(body,"上限（默认 100）",44),count=input(body,"生成个数（默认 1，最多 200）",44);
    min.setInputType(InputType.TYPE_CLASS_NUMBER);max.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);count.setInputType(InputType.TYPE_CLASS_NUMBER);
    CheckBox unique=checkInline(checkRow(body),"去重",false);
    LinearLayout actions=actionRow(body);
    action(actions,"生成",()->{try{output(body,Toolbox.randomNumbers(parseInt(min,1),parseInt(max,100),parseInt(count,1),unique.isChecked()));}catch(Exception e){output(body,"输入有误");}});
    result(body);
  }

  void password(LinearLayout body){
    EditText length=input(body,"密码长度（6-64，默认 16）",44);length.setInputType(InputType.TYPE_CLASS_NUMBER);
    LinearLayout checks=new LinearLayout(act);checks.setGravity(Gravity.CENTER_VERTICAL);checks.setPadding(0,act.dp(8),0,0);
    CheckBox upper=checkInline(checks,"大写",true),lower=checkInline(checks,"小写",true),digits=checkInline(checks,"数字",true),symbols=checkInline(checks,"符号",false);
    body.addView(checks,new LinearLayout.LayoutParams(-1,act.dp(44)));
    LinearLayout actions=actionRow(body);
    action(actions,"生成密码",()->{output(body,Toolbox.generatePassword(parseInt(length,16),upper.isChecked(),lower.isChecked(),digits.isChecked(),symbols.isChecked()));});
    result(body);
  }

  void imageCompress(LinearLayout body){
    TextView info=text("选择图片 → 选质量 → 压缩并保存到相册“东方无限”目录。",12,act.MUTED);info.setPadding(0,0,0,act.dp(10));body.addView(info,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout actions=actionRow(body);action(actions,"选择图片",()->act.pickToolImage());
    TextView meta=text(act.toolImageInfoText(),12,act.MUTED);meta.setTag("img-info");meta.setBackground(solid(act.SURFACE));meta.setPadding(act.dp(12),act.dp(10),act.dp(12),act.dp(10));meta.setMinHeight(act.dp(72));
    body.addView(meta,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout quality=new LinearLayout(act);quality.setGravity(Gravity.CENTER_VERTICAL);quality.setPadding(0,act.dp(10),0,0);
    quality.addView(text("压缩质量",12,act.TEXT),new LinearLayout.LayoutParams(-2,act.dp(40)));
    int[] qualities={90,70,50};TextView[] chips=new TextView[qualities.length];
    for(int i=0;i<qualities.length;i++){final int q=qualities[i];chips[i]=selectChip(quality,q+"%",act.toolQuality==q,()->{act.toolQuality=q;for(int j=0;j<chips.length;j++)styleSelect(chips[j],qualities[j]==q);});}
    quality.setTag("img-quality");body.addView(quality,new LinearLayout.LayoutParams(-1,act.dp(48)));
    LinearLayout run=actionRow(body);action(run,"压缩并保存",()->{if(act.toolImageUri==null){act.showNotice("先选择图片",true);return;}act.showNotice("正在压缩…",false);act.runImageCompressPending();});
    result(body);
  }

  void deviceinfo(LinearLayout body){
    LinearLayout actions=actionRow(body);action(actions,"读取设备信息",()->output(body,collectDeviceInfo()));
    result(body);
  }
  String collectDeviceInfo(){
    android.app.ActivityManager.MemoryInfo memory=new android.app.ActivityManager.MemoryInfo();
    android.app.ActivityManager am=(android.app.ActivityManager)act.getSystemService(android.content.Context.ACTIVITY_SERVICE);
    if(am!=null)am.getMemoryInfo(memory);
    android.content.Intent battery=act.registerReceiver(null,new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
    int level=battery==null?0:battery.getIntExtra("level",0),scale=battery==null?0:battery.getIntExtra("scale",100),temperature=battery==null?0:battery.getIntExtra("temperature",0);
    android.util.DisplayMetrics metrics=act.getResources().getDisplayMetrics();
    StringBuilder out=new StringBuilder();
    out.append("品牌：").append(Build.BRAND).append(" ").append(Build.MODEL).append('\n');
    out.append("系统：Android ").append(Build.VERSION.RELEASE).append("（API ").append(Build.VERSION.SDK_INT).append("）\n");
    out.append("芯片：").append(Build.HARDWARE).append(" / ").append(Build.CPU_ABI).append('\n');
    out.append("屏幕：").append(metrics.widthPixels).append("×").append(metrics.heightPixels).append(" @").append(metrics.densityDpi).append("dpi\n");
    out.append("内存：可用 ").append(act.toolBytes(memory.availMem)).append(" / 共 ").append(act.toolBytes(memory.totalMem)).append('\n');
    if(scale>0)out.append("电池：").append(level*100/scale).append("% · ").append(temperature/10.0).append("℃\n");
    out.append("Java 堆：").append(act.toolBytes(Runtime.getRuntime().maxMemory()));
    return out.toString();
  }

  void torch(LinearLayout body){
    TextView state=text("未开启",14,act.TEXT);state.setPadding(0,act.dp(6),0,act.dp(6));
    LinearLayout actions=actionRow(body);
    action(actions,"开灯",()->{if(act.startTorch())state.setText("手电筒已开启\n返回或离开工具页自动关闭");});
    action(actions,"关灯",()->{act.stopTorch();state.setText("已关闭");});
    body.addView(state,new LinearLayout.LayoutParams(-1,-2));
    result(body);
  }

  void noise(LinearLayout body){
    TextView state=text("棕噪音：低频噪声，适合助眠与专注。播放中可退到后台。",12,act.MUTED);state.setPadding(0,0,0,act.dp(10));
    body.addView(state,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout actions=actionRow(body);
    action(actions,"播放",()->{act.startBrownNoise();act.showNotice("播放中",false);});
    action(actions,"停止",()->act.stopBrownNoise());
    result(body);
  }

  void tts(LinearLayout body){
    EditText input=input(body,"输入要朗读的文字…",100);
    LinearLayout actions=actionRow(body);
    action(actions,"朗读",()->act.speakTts(input.getText().toString()));
    action(actions,"停止",()->act.stopTts());
    result(body);
  }

  void bmi(LinearLayout body){
    EditText height=input(body,"身高（cm）",44),weight=input(body,"体重（kg）",44);
    height.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);weight.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL);
    LinearLayout actions=actionRow(body);
    action(actions,"计算 BMI",()->{try{output(body,Toolbox.bmiInfo(Double.parseDouble(height.getText().toString()),Double.parseDouble(weight.getText().toString())));}catch(Exception e){output(body,"请输入身高体重");}});
    result(body);
  }

  //—— 通用部件 ——

  LinearLayout checkRow(LinearLayout parent){LinearLayout row=new LinearLayout(act);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,act.dp(8),0,0);parent.addView(row,new LinearLayout.LayoutParams(-1,act.dp(44)));return row;}
  CheckBox checkInline(LinearLayout parent,String label,boolean checked){
    CheckBox box=new CheckBox(act);box.setText(label);box.setTextColor(act.TEXT);box.setTextSize(12);box.setChecked(checked);box.setPadding(act.dp(4),0,act.dp(4),0);
    parent.addView(box,new LinearLayout.LayoutParams(-2,-2));return box;
  }
  LinearLayout listCard(){LinearLayout card=new LinearLayout(act);card.setOrientation(LinearLayout.VERTICAL);GradientDrawable bg=solid(act.SURFACE);bg.setStroke(act.dp(1),act.DIV);card.setBackground(bg);card.setClipToOutline(true);card.setPadding(act.dp(4),act.dp(4),act.dp(4),act.dp(6));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,act.dp(10));card.setLayoutParams(params);return card;}
  LinearLayout chipRow(LinearLayout parent){LinearLayout row=new LinearLayout(act);row.setOrientation(LinearLayout.VERTICAL);TextView label=text("",11,act.MUTED);row.addView(label,new LinearLayout.LayoutParams(-1,act.dp(22)));parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;}
  TextView selectChip(LinearLayout row,String label,boolean selected,Runnable click){
    TextView chip=text(label,12,selected?act.PRIMARY:act.TEXT);chip.setGravity(Gravity.CENTER);chip.setClickable(true);chip.setFocusable(true);chip.setSelected(selected);
    styleSelect(chip,selected);chip.setOnClickListener(v->press(v,click));row.addView(chip,chipMargin());return chip;
  }
  void styleSelect(TextView chip,boolean selected){
    chip.setTextColor(selected?act.PRIMARY:act.TEXT);chip.setSelected(selected);
    GradientDrawable bg=solid(selected?Color.rgb(46,38,68):act.SURFACE);bg.setStroke(act.dp(1),selected?act.PRIMARY:act.DIV);
    chip.setBackground(ripple(bg));
  }
  LinearLayout.LayoutParams chipMargin(){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,act.dp(40));params.setMargins(0,0,act.dp(8),act.dp(8));return params;}
  EditText input(LinearLayout parent,String hint,int minDp){
    EditText field=new EditText(act);field.setHint(hint);field.setHintTextColor(act.MUTED);field.setTextColor(act.TEXT);field.setTextSize(14);
    field.setGravity(Gravity.TOP|Gravity.START);field.setBackground(solid(act.SURFACE));field.setPadding(act.dp(12),act.dp(10),act.dp(12),act.dp(10));field.setMinHeight(act.dp(minDp));
    field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,act.dp(minDp));params.setMargins(0,0,0,act.dp(8));
    parent.addView(field,params);return field;
  }
  LinearLayout actionRow(LinearLayout parent){LinearLayout row=new LinearLayout(act);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,act.dp(10));parent.addView(row,params);return row;}
  void action(LinearLayout row,String label,Runnable click){
    Button button=new Button(act);button.setText(label);button.setTextColor(act.PRIMARY);button.setTextSize(12);button.setAllCaps(false);
    button.setBackground(ripple(solid(act.SURFACE)));button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(act.dp(40));button.setPadding(act.dp(14),0,act.dp(14),0);
    button.setOnClickListener(v->press(v,click));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,act.dp(40));params.setMargins(0,0,act.dp(8),0);
    row.addView(button,params);
  }
  TextView result(LinearLayout parent){
    TextView label=text("结果",11,act.MUTED);label.setPadding(0,act.dp(4),0,act.dp(2));parent.addView(label,new LinearLayout.LayoutParams(-1,-2));
    TextView output=new TextView(act);output.setTextColor(act.TEXT);output.setTextSize(13);output.setTextIsSelectable(true);output.setLineSpacing(act.dp(2),1f);
    output.setBackground(solid(act.SURFACE));output.setPadding(act.dp(12),act.dp(10),act.dp(12),act.dp(10));output.setMinHeight(act.dp(72));
    parent.addView(output,new LinearLayout.LayoutParams(-1,-2));
    Button copyB=new Button(act);copyB.setText("复制结果");copyB.setTextColor(act.PRIMARY);copyB.setTextSize(12);copyB.setAllCaps(false);
    copyB.setBackground(ripple(solid(act.SURFACE)));copyB.setMinWidth(0);copyB.setMinimumWidth(0);copyB.setMinHeight(act.dp(40));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,act.dp(40));params.setMargins(0,act.dp(8),0,0);
    copyB.setOnClickListener(v->{String value=output.getText().toString();if(value.isEmpty())return;copy(value);});
    parent.addView(copyB,params);
    output.setTag("tool-output");return output;
  }
  void output(LinearLayout parent,String value){if(parent==null)return;View found=parent.findViewWithTag("tool-output");if(found instanceof TextView){((TextView)found).setText(value);if(act.motionEnabled()){found.setAlpha(.4f);found.animate().alpha(1f).setDuration(150).start();}}}
  void copy(String value){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("东方无限工具结果",value));act.showNotice("已复制",false);}
  TextView text(String s,int sp,int color){TextView v=new TextView(act);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
  GradientDrawable solid(int color){return act.solidShape(color,14);}
  Drawable ripple(Drawable content){return act.filterRipple(content);}
  /** 按压缩放反馈（fast spring 感：90ms 缩到 0.96，90ms 回弹） */
  void press(View v,Runnable action){
    if(!act.motionEnabled()){action.run();return;}
    v.animate().cancel();v.animate().scaleX(.96f).scaleY(.96f).setDuration(70).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(90).withEndAction(action).start()).start();
  }
  /** 内容入场：整体淡入 + 轻微上移（emphasized-decelerate 感） */
  void enterStagger(View content){content.setAlpha(0f);content.setTranslationY(act.dp(10));content.animate().alpha(1f).translationY(0).setDuration(240).setInterpolator(new android.view.animation.PathInterpolator(0.05f,0.7f,0.1f,1f)).start();}
  /** 两个内容块交叉淡化切换 */
  void crossFade(LinearLayout target,Runnable rebuild){
    target.animate().cancel();
    target.animate().alpha(0f).setDuration(90).withEndAction(()->{rebuild.run();target.setAlpha(0f);target.animate().alpha(1f).setDuration(190).setInterpolator(new android.view.animation.PathInterpolator(0.05f,0.7f,0.1f,1f)).start();}).start();
  }
  int parseInt(EditText field,int fallback){try{return Integer.parseInt(field.getText().toString().trim());}catch(Exception ignored){return fallback;}}
}
