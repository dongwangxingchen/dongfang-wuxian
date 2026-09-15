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
  private final Host act;private final android.content.Context ctx;
  LinearLayout toolBody;

  ToolHost(Host activity){act=activity;ctx=activity.context();}

  //—— 列表页 ——

  /** 工具箱首页：搜索 + 「功能大全 / 热门排行」Tab + 分组折叠 chip 流 */
  void renderList(){
    act.primaryHeader("工具箱");
    LinearLayout body=new LinearLayout(ctx);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(act.dp(2),act.dp(4),act.dp(2),act.dp(16));
    // 搜索框（本地：名称/说明/关键词/分类）
    EditText search=toolSearchInput(body);
    final List<String>[] resultHolder=new List[]{null};
    LinearLayout resultBox=new LinearLayout(ctx);resultBox.setOrientation(LinearLayout.VERTICAL);body.addView(resultBox,new LinearLayout.LayoutParams(-1,-2));
    // Tab：功能大全 / 热门排行
    LinearLayout tabs=new LinearLayout(ctx);tabs.setGravity(Gravity.CENTER);tabs.setBackground(ripple(solid(Color.TRANSPARENT)));tabs.setPadding(act.dp(4),act.dp(4),act.dp(4),act.dp(4));
    TextView tabAll=tabChip("功能大全",true),tabHot=tabChip("热门排行",false);
    tabs.addView(tabAll,new LinearLayout.LayoutParams(0,act.dp(38),1));
    tabs.addView(tabHot,new LinearLayout.LayoutParams(0,act.dp(38),1));
    LinearLayout tabsWrap=new LinearLayout(ctx);tabsWrap.setOrientation(LinearLayout.VERTICAL);tabsWrap.addView(tabs,new LinearLayout.LayoutParams(-1,act.dp(46)));
    body.addView(tabsWrap,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout catalog=new LinearLayout(ctx);catalog.setOrientation(LinearLayout.VERTICAL);body.addView(catalog,new LinearLayout.LayoutParams(-1,-2));
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
    ScrollView scroll=new ScrollView(ctx);scroll.setFillViewport(true);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
    act.root().addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    if(act.motionEnabled())enterStagger(scroll);
  }

  EditText toolSearchInput(LinearLayout parent){
    LinearLayout box=new LinearLayout(ctx);box.setGravity(Gravity.CENTER_VERTICAL);GradientDrawable bg=solid(act.SURFACE());bg.setStroke(act.dp(1),act.DIV());
    box.setBackground(ripple(bg));box.setPadding(act.dp(12),0,act.dp(12),0);
    ImageView icon=new ImageView(ctx);icon.setImageResource(R.drawable.ic_tool_search);icon.setColorFilter(act.MUTED());box.addView(icon,new LinearLayout.LayoutParams(act.dp(20),act.dp(20)));
    EditText input=new EditText(ctx);input.setHint("搜索工具…");input.setHintTextColor(act.MUTED());input.setTextColor(act.TEXT());input.setTextSize(14);
    input.setBackground(null);input.setSingleLine(true);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);input.setPadding(act.dp(10),act.dp(12),act.dp(10),act.dp(12));
    box.addView(input,new LinearLayout.LayoutParams(0,act.dp(48),1));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,act.dp(10));
    parent.addView(box,params);return input;
  }

  TextView tabChip(String label,boolean active){
    TextView chip=new TextView(ctx);chip.setText(label);chip.setTextSize(13);chip.setGravity(Gravity.CENTER);
    chip.setTextColor(active?act.PRIMARY():act.MUTED());chip.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
    chip.setBackground(ripple(solid(active?ThemeEngine.selectedFill(ctx):Color.TRANSPARENT)));chip.setContentDescription(label+(active?"，已选中":""));
    return chip;
  }
  void selectTab(TextView selected,TextView other){
    selected.setTextColor(act.PRIMARY());other.setTextColor(act.MUTED());
    selected.setBackground(ripple(solid(ThemeEngine.selectedFill(ctx))));other.setBackground(ripple(solid(Color.TRANSPARENT)));
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
      LinearLayout row=new LinearLayout(ctx);row.setGravity(Gravity.CENTER_VERTICAL);row.setClickable(true);row.setFocusable(true);
      row.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT)));row.setPadding(act.dp(10),0,act.dp(8),0);
      TextView no=text("#"+rank,13,rank<=3?act.PRIMARY():act.MUTED());no.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);no.setMinWidth(act.dp(34));
      row.addView(no,new LinearLayout.LayoutParams(-2,act.dp(56)));
      row.addView(toolIconView(id,act.dp(30)),new LinearLayout.LayoutParams(act.dp(30),act.dp(30)));
      LinearLayout copy=new LinearLayout(ctx);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(act.dp(12),0,0,0);
      TextView name=text(Toolbox.toolName(id),15,act.TEXT());name.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);copy.addView(name,new LinearLayout.LayoutParams(-1,act.dp(26)));
      TextView desc=text(Toolbox.toolDesc(id),11,act.MUTED());desc.setSingleLine(true);desc.setEllipsize(TextUtils.TruncateAt.END);copy.addView(desc,new LinearLayout.LayoutParams(-1,act.dp(20)));
      row.addView(copy,new LinearLayout.LayoutParams(0,act.dp(56),1));
      String id0=id;row.setOnClickListener(v->act.openTool(id0));
      card.addView(row,new LinearLayout.LayoutParams(-1,act.dp(56)));
      if(rank<hot.size()){View divider=new View(ctx);divider.setBackgroundColor(act.DIV());card.addView(divider,new LinearLayout.LayoutParams(-1,act.dp(1)));}
      rank++;
    }
  }

  void renderSearchResults(LinearLayout parent,String query,List<String> ids){
    parent.removeAllViews();
    LinearLayout card=listCard();parent.addView(card,new LinearLayout.LayoutParams(-1,-2));
    TextView head=text("“"+query+"” · "+ids.size()+" 个结果",12,act.MUTED());head.setPadding(act.dp(12),act.dp(10),act.dp(12),act.dp(4));card.addView(head,new LinearLayout.LayoutParams(-1,-2));
    if(ids.isEmpty()){TextView empty=text("没有匹配的工具",13,act.MUTED());empty.setGravity(Gravity.CENTER);empty.setPadding(0,act.dp(16),0,act.dp(20));card.addView(empty,new LinearLayout.LayoutParams(-1,-2));return;}
    GridLayout grid=new GridLayout(ctx);grid.setColumnCount(2);
    for(String id:ids)grid.addView(categoryChip(id),chipLayout());
    card.addView(grid,new LinearLayout.LayoutParams(-1,-2));
  }

  /** 一组：折叠容器（M3 emphasized easing 240ms，与设置页同一配方） */
  LinearLayout toolGroup(String category,List<String> ids,boolean expanded){
    LinearLayout section=new LinearLayout(ctx);section.setOrientation(LinearLayout.VERTICAL);GradientDrawable surface=solid(act.SURFACE());surface.setStroke(act.dp(1),act.DIV());
    section.setBackground(surface);section.setClipToOutline(true);
    LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(-1,-2);outer.setMargins(0,0,0,act.dp(10));section.setLayoutParams(outer);
    LinearLayout header=new LinearLayout(ctx);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(act.dp(14),act.dp(4),act.dp(10),act.dp(4));
    header.setClickable(true);header.setFocusable(true);header.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT)));
    LinearLayout copy=new LinearLayout(ctx);copy.setOrientation(LinearLayout.VERTICAL);
    TextView title=text(category,15,act.TEXT());title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);copy.addView(title,new LinearLayout.LayoutParams(-1,act.dp(28)));
    LinearLayout titleRow=new LinearLayout(ctx);titleRow.setGravity(Gravity.CENTER_VERTICAL);titleRow.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
    TextView badge=text(ids.size()+" 个",10,act.PRIMARY());
    GradientDrawable pill=solid(ThemeEngine.tint(act.PRIMARY(),30));pill.setCornerRadius(act.dp(20));badge.setBackground(pill);badge.setPadding(act.dp(10),act.dp(2),act.dp(10),act.dp(2));
    titleRow.addView(badge,new LinearLayout.LayoutParams(-2,act.dp(24)));
    header.addView(titleRow,new LinearLayout.LayoutParams(0,act.dp(52),1));
    ImageView arrow=new ImageView(ctx);arrow.setImageResource(R.drawable.ic_expand);arrow.setColorFilter(act.PRIMARY());arrow.setPadding(act.dp(8),act.dp(8),act.dp(8),act.dp(8));
    header.addView(arrow,new LinearLayout.LayoutParams(act.dp(44),act.dp(52)));
    section.addView(header,new LinearLayout.LayoutParams(-1,act.dp(60)));
    LinearLayout content=new LinearLayout(ctx);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(act.dp(6),0,act.dp(6),act.dp(8));
    GridLayout grid=new GridLayout(ctx);grid.setColumnCount(2);
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
    LinearLayout chip=new LinearLayout(ctx);chip.setGravity(Gravity.CENTER_VERTICAL);chip.setClickable(true);chip.setFocusable(true);
    GradientDrawable bg=solid(act.SURFACE());bg.setStroke(act.dp(1),act.DIV());chip.setBackground(ripple(bg));chip.setPadding(act.dp(10),0,act.dp(12),0);
    chip.addView(toolIconView(id,act.dp(26)),new LinearLayout.LayoutParams(act.dp(26),act.dp(26)));
    TextView name=text(Toolbox.toolName(id),13,act.TEXT());name.setSingleLine(true);name.setPadding(act.dp(8),0,0,0);
    chip.addView(name,new LinearLayout.LayoutParams(0,act.dp(44),1));
    chip.setContentDescription("打开工具："+Toolbox.toolName(id));
    chip.setOnClickListener(v->press(v,()->act.openTool(id)));
    return chip;
  }
  GridLayout.LayoutParams chipLayout(){GridLayout.LayoutParams cell=new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f));cell.width=0;cell.height=act.dp(56);((ViewGroup.MarginLayoutParams)cell).setMargins(act.dp(4),act.dp(4),act.dp(4),act.dp(4));return cell;}

  ImageView toolIconView(String id,int size){
    ImageView icon=new ImageView(ctx);icon.setImageResource(toolIconRes(Toolbox.toolIcon(id)));
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
    if("__screen_test__".equals(id)){act.startScreenTest();return;}// 复审3:配置重建/切页返回时直接重进全屏检测，而不是弹回列表
    String title=Toolbox.toolName(id);
    LinearLayout header=new LinearLayout(ctx);header.setGravity(Gravity.CENTER_VERTICAL);
    ImageButton back=act.iconButton(R.drawable.ic_back,"返回工具箱");back.setOnClickListener(v->{act.setPageDirection(-1);act.popToolBack();});header.addView(back,new LinearLayout.LayoutParams(act.dp(48),act.dp(48)));
    LinearLayout copy=new LinearLayout(ctx);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(act.dp(8),0,0,0);
    TextView heading=text(title,19,act.TEXT());heading.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);copy.addView(heading,new LinearLayout.LayoutParams(-1,act.dp(30)));
    TextView sub=text(Toolbox.toolDesc(id),11,act.MUTED());sub.setSingleLine(true);sub.setEllipsize(TextUtils.TruncateAt.END);copy.addView(sub,new LinearLayout.LayoutParams(-1,act.dp(20)));
    header.addView(copy,new LinearLayout.LayoutParams(0,act.dp(52),1));
    act.root().addView(header,new LinearLayout.LayoutParams(-1,act.dp(54)));
    LinearLayout body=new LinearLayout(ctx);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(act.dp(4),act.dp(8),act.dp(4),act.dp(16));
    toolBody=body;
    ScrollView scroll=new ScrollView(ctx);scroll.setFillViewport(true);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));act.root().addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    switch(id){
      case "calculator":calculator(body);break;
      case "unit":unit(body);break;
      case "datecalc":datecalc(body);break;
      case "decision":decision(body);break;
      case "scorecard":scorecard(body);break;
      case "calendar":calendarGrid(body);break;
      case "randomnum":randomnum(body);break;
      case "text_stats":{
        // v1.11.2 工具精修14：实时全维度统计（基准 Countable live 计数 + Word 字数口径，见 07-研究报告/工具精修-14）
        TextView hint=text("输入即实时统计；字数 = 汉字 + 英文单词（与 Word 口径一致）。",12,act.MUTED());hint.setPadding(0,0,0,act.dp(8));body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
        EditText input=input(body,"粘贴文本…",120);
        String[] statLabels={"字数","汉字","英文单词","数字串","字符(含空格)","字符(不含空白)","句数","段落","行数","标点"};
        android.widget.GridLayout statGrid=new android.widget.GridLayout(ctx);statGrid.setColumnCount(2);
        TextView[] statVals=new TextView[statLabels.length];
        for(int i=0;i<statLabels.length;i++){
          LinearLayout cell=new LinearLayout(ctx);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);
          GradientDrawable cellBg=solid(act.SURFACE());cellBg.setCornerRadius(act.dp(12));cell.setBackground(cellBg);
          TextView num=text("0",17,act.TEXT());num.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);num.setGravity(Gravity.CENTER);num.setIncludeFontPadding(false);
          TextView lab=text(statLabels[i],11,act.MUTED());lab.setGravity(Gravity.CENTER);
          cell.addView(num,new LinearLayout.LayoutParams(-1,-2));cell.addView(lab,new LinearLayout.LayoutParams(-1,-2));
          android.widget.GridLayout.LayoutParams clp=new android.widget.GridLayout.LayoutParams(
              android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f),
              android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED,1f));
          clp.width=0;clp.height=act.dp(62);
          if(i%2==0)clp.rightMargin=act.dp(6);
          clp.topMargin=act.dp(6);
          statGrid.addView(cell,clp);statVals[i]=num;
        }
        body.addView(statGrid,new LinearLayout.LayoutParams(-1,-2));
        TextView foot=text("",12,act.MUTED());foot.setPadding(act.dp(4),act.dp(8),act.dp(4),0);body.addView(foot,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout freqCard=new LinearLayout(ctx);freqCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable freqBg=solid(act.SURFACE());freqBg.setCornerRadius(act.dp(12));freqCard.setBackground(freqBg);freqCard.setPadding(act.dp(12),act.dp(10),act.dp(12),act.dp(10));freqCard.setClickable(true);freqCard.setFocusable(true);
        freqCard.setContentDescription("高频字词，点击复制");
        TextView freq=text("高频字词：—",13,act.TEXT());freq.setLineSpacing(act.dp(2),1f);freqCard.addView(freq,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.topMargin=act.dp(8);body.addView(freqCard,fp);
        freqCard.setOnClickListener(v->{String t=freq.getText().toString();if(!t.endsWith("—"))copy(t);});
        final EditText inputF=input;final TextView[] statValsF=statVals;final TextView footF=foot;final TextView freqF=freq;
        Runnable recalc=()->{
          String v=inputF.getText().toString();
          int[] s=Toolbox.textStatsAll(v);
          for(int i=0;i<statValsF.length;i++)statValsF[i].setText(String.valueOf(s[i]));
          if(v.isEmpty()){footF.setText("UTF-8 0 字节 · 预计阅读 —");freqF.setText("高频字词：—");return;}
          long bytes=v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
          String kb=bytes>=1024?String.format(java.util.Locale.US,"（%.1f KB）",bytes/1024.0):"";
          String read=s[0]==0?"—":(s[0]<300?"<1 分钟":((s[0]+299)/300)+" 分钟");
          footF.setText("UTF-8 "+bytes+" 字节"+kb+" · 预计阅读 "+read+"（按 300 字/分钟）");
          String top=Toolbox.textTopFreq(v);
          freqF.setText(top.isEmpty()?"高频字词：—":top.trim());
        };
        inputF.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable s){recalc.run();}});
        recalc.run();
        LinearLayout actions=actionRow(body);
        action(actions,"去重·保序",()->output(body,Toolbox.dedupeLines(input.getText().toString(),false,false)));
        action(actions,"去重·排序",()->output(body,Toolbox.dedupeLines(input.getText().toString(),true,false)));
        action(actions,"去空行",()->output(body,Toolbox.dedupeLines(input.getText().toString(),false,true)));
        action(actions,"清空",()->{input.setText("");output(body,"");});
        result(body);
      }break;
      case "base64":{
        // v1.12.0 工具精修15：RFC 4648 向量背书 + URL-safe/去填充变体（见研究报告）
        EditText input=input(body,"输入文本或 Base64…",140);
        LinearLayout opts=new LinearLayout(ctx);opts.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox urlSafe=checkInline(opts,"URL-safe（-_）",false);
        CheckBox noPad=checkInline(opts,"去填充（=）",false);
        body.addView(opts,new LinearLayout.LayoutParams(-1,act.dp(40)));
        LinearLayout actions=actionRow(body);
        action(actions,"编码",()->output(body,Toolbox.base64Encode(input.getText().toString(),urlSafe.isChecked(),noPad.isChecked())));
        action(actions,"解码",()->output(body,Toolbox.base64Decode(input.getText().toString())));
        result(body);
      }break;
      case "url_codec":{
        // v1.12.0 工具精修16：路径（%20）与表单（+）双口径
        EditText input=input(body,"输入文本或已编码 URL…",140);
        LinearLayout opts=new LinearLayout(ctx);opts.setGravity(Gravity.CENTER_VERTICAL);
        CheckBox formMode=checkInline(opts,"表单口径（空格→+）",false);
        body.addView(opts,new LinearLayout.LayoutParams(-1,act.dp(40)));
        LinearLayout actions=actionRow(body);
        action(actions,"编码",()->output(body,Toolbox.urlEncode(input.getText().toString(),formMode.isChecked())));
        action(actions,"解码",()->output(body,Toolbox.urlDecode(input.getText().toString(),formMode.isChecked())));
        result(body);
      }break;
      case "hash":hash(body);break;
      case "json":json(body);break;
      case "regex":regex(body);break;
      case "password":password(body);break;
      case "uuid":uuid(body);break;
      case "img_compress":imageCompress(body);break;
      case "sketch":act.toolHostSketch(body);break;
      case "deviceinfo":deviceinfo(body);break;
      case "screen_test":{TextView info=text("全屏循环纯色（黑/白/红/绿/蓝/灰），点按切换，返回退出。用于检查坏点、漏光与烧屏。",12,act.MUTED());info.setPadding(0,0,0,act.dp(10));body.addView(info,new LinearLayout.LayoutParams(-1,-2));LinearLayout actions=actionRow(body);primaryAction(actions,"开始检测",()->act.startScreenTest());result(body);break;}
      case "ruler":act.toolHostRuler(body);break;
      case "torch":torch(body);break;
      case "noise":noise(body);break;
      case "tts":tts(body);break;
      case "level":act.toolHostLevel(body);break;
      case "zodiac":{EditText input=input(body,"出生日期（2000-06-15）",44);LinearLayout actions=actionRow(body);primaryAction(actions,"查询生肖星座",()->output(body,Toolbox.zodiac(input.getText().toString())));result(body);}break;
      case "idcard":{EditText input=input(body,"18 位身份证号（仅本机解析，不上传）",44);LinearLayout actions=actionRow(body);primaryAction(actions,"解析",()->output(body,Toolbox.parseIdCard(input.getText().toString())));result(body);}break;
      case "agecalc":{EditText input=input(body,"出生日期（2000-06-15）",44);LinearLayout actions=actionRow(body);primaryAction(actions,"计算年龄",()->output(body,Toolbox.ageCalc(input.getText().toString())));result(body);}break;
      case "bmi":bmi(body);break;
      case "stopwatch":stopwatch(body);break;
      case "timestamp":timestamp(body);break;
      case "radix":radix(body);break;
      case "compass":compass(body);break;
      case "freqgen":freqgen(body);break;
      case "picker":picker(body);break;
      case "morse":morse(body);break;
      default:{TextView info=text("该工具即将上线",13,act.MUTED());body.addView(info,new LinearLayout.LayoutParams(-1,act.dp(48)));break;}
    }
    if(act.motionEnabled())enterStagger(scroll);
  }

  void calculator(LinearLayout body){
    // —— 显示区：可编辑表达式行 + 大字结果行（点结果复制）——
    EditText expr=new EditText(ctx);expr.setSingleLine(true);expr.setTextSize(18);expr.setTextColor(act.TEXT());expr.setHintTextColor(act.MUTED());expr.setHint("点按下方按键，或直接输入");
    expr.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);expr.setBackground(solid(act.SURFACE()));expr.setPadding(act.dp(16),0,act.dp(16),0);expr.setMinHeight(act.dp(60));expr.setCursorVisible(true);
    LinearLayout.LayoutParams exprParams=new LinearLayout.LayoutParams(-1,act.dp(60));exprParams.setMargins(0,0,0,act.dp(8));body.addView(expr,exprParams);
    TextView live=text("0",36,act.TEXT());live.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);live.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
    live.setBackground(solid(act.SURFACE()));live.setPadding(act.dp(16),0,act.dp(16),0);live.setMinHeight(act.dp(92));
    body.addView(live,new LinearLayout.LayoutParams(-1,act.dp(92)));// v1.6.2：显示区放大（真机反馈「像手表界面」：48/60dp→60/92dp）
    live.setClickable(true);live.setFocusable(true);live.setContentDescription("计算结果，点按复制");
    live.setOnClickListener(v->{String value=live.getText().toString();if(value.isEmpty()||value.equals("0")||value.startsWith("表达式"))return;copy(value);act.showNotice("已复制",false);});
    TextView hint=text("支持 + − × ÷ 与括号 · 点结果复制",11,act.MUTED());hint.setPadding(act.dp(2),act.dp(6),act.dp(2),0);body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    java.util.function.Consumer<String> evaluate=(String value)->{if(value.trim().isEmpty()){live.setText("0");live.setTextColor(act.MUTED());return;}String out=Toolbox.calculate(value);live.setText(out);live.setTextColor(out.startsWith("表达式")?act.MUTED():act.TEXT());};
    expr.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable s){evaluate.accept(s.toString());}});
    // —— 实体键盘面板：5 行 × 4 列（每行恰好 4 键，M3 计算器规格：键距 8dp、圆角 12、三色分层）——
    LinearLayout pad=new LinearLayout(ctx);pad.setOrientation(LinearLayout.VERTICAL);pad.setPadding(act.dp(2),act.dp(4),act.dp(2),act.dp(2));
    body.addView(pad,new LinearLayout.LayoutParams(-1,-2));
    String[] rows={"C·⌫·(·)·÷","7·8·9·×","4·5·6·−","1·2·3·+","±·0·.·="};
    String[] descs={"清除全部·退格删除·左括号·右括号·除","数字7·数字8·数字9·乘","数字4·数字5·数字6·减","数字1·数字2·数字3·加","正负取反·数字0·小数点·等于"};
    java.util.function.Consumer<String> append=(String piece)->{
      int start=expr.getSelectionStart(),end=expr.getSelectionEnd();
      if(start<0)start=expr.length();if(end<0)end=start;
      expr.getText().replace(Math.min(start,end),Math.max(start,end),piece);
      expr.setSelection(Math.min(start,end)+piece.length());
    };
    for(int r=0;r<rows.length;r++){
      String[] labels=rows[r].split("·");String[] des=descs[r].split("·");
      LinearLayout row=new LinearLayout(ctx);row.setOrientation(LinearLayout.HORIZONTAL);
      pad.addView(row,new LinearLayout.LayoutParams(-1,-2));
      for(int c=0;c<4;c++){
        final String k=labels[c];
        TextView key=new TextView(ctx);key.setText(k);key.setTextSize(23);key.setGravity(Gravity.CENTER);
        key.setMinHeight(act.dp(68));key.setMinimumHeight(act.dp(68));key.setClickable(true);key.setFocusable(true);key.setContentDescription(des[c]);
        final boolean operator=k.equals("÷")||k.equals("×")||k.equals("−")||k.equals("+");
        final boolean soft=k.equals("C")||k.equals("⌫")||k.equals("(")||k.equals(")")||k.equals("±");
        // v1.7.2 计算器视觉对齐（研究 OpenCalc 1534★ 真实截图 + 快照目检修正）：
        // 四色分层，但运算符用「浅色底 + 主色字」而非实心大色块（实心块过重、与 OpenCalc 精致感不符）；
        // 数字键必须有可见底色（apple 主题 surface2 与背景同色 → 改用 border 色系保证可见）
        int keyBase=act.SURFACE2();
        if(keyBase==act.BG())keyBase=act.BORDER();// apple 主题 surface2==bg，按键会隐形 → 换 border 色（浅灰，可见）
        GradientDrawable keyBg=solid(keyBase);keyBg.setCornerRadius(act.dp(24));// 24dp 圆角 ≈ 胶囊（68dp 高时接近正圆）
        if(k.equals("=")){key.setTextColor(0xFFFFFFFF);keyBg.setColor(act.SECONDARY());key.setBackground(ripple(keyBg));}
        else if(operator){key.setTextColor(act.PRIMARY());keyBg.setColor(ThemeEngine.tint(act.PRIMARY(),56));keyBg.setStroke(act.dp(1),ThemeEngine.tint(act.PRIMARY(),120));key.setBackground(ripple(keyBg));}
        else if(soft){key.setTextColor(act.MUTED());key.setBackground(ripple(keyBg));}
        else{key.setTextColor(act.TEXT());key.setBackground(ripple(keyBg));}
        key.setOnClickListener(v->{
          Runnable action=()->{
            if(k.equals("C")){expr.setText("");}
            else if(k.equals("⌫")){int start=expr.getSelectionStart(),end=expr.getSelectionEnd();if(start<0||end<0){start=end=expr.length();}if(start==end&&start>0){expr.getText().delete(start-1,start);expr.setSelection(start-1);}else if(start!=end){expr.getText().delete(Math.min(start,end),Math.max(start,end));}}
            else if(k.equals("=")){String result=live.getText().toString().trim();if(!result.isEmpty()&&!result.equals("0")&&!result.startsWith("表达式")){expr.setText(result);expr.setSelection(expr.length());}}
            else if(k.equals("±")){
              String cur=expr.getText().toString();int sel=expr.getSelectionStart();
              int start=Math.max(0,sel<0?0:sel);while(start>0&&Character.isDigit(cur.charAt(start-1)))start--;
              if(start<cur.length()&&cur.charAt(start)=='-'&&(start==0||!Character.isDigit(cur.charAt(start-1))&&cur.charAt(start-1)!='.')){expr.getText().delete(start,start+1);}
              else{expr.getText().insert(start,"-");}
              int newLen=expr.getText().length();expr.setSelection(Math.min(newLen,Math.max(0,start)+1));
            }
            else if(k.equals(".")){append.accept(".");}
            else{String piece=k;if(piece.equals("÷"))piece="/";else if(piece.equals("×"))piece="*";else if(piece.equals("−"))piece="-";append.accept(piece);}
          };
          if(act.motionEnabled())press(v,action);else action.run();
        });
        LinearLayout.LayoutParams keyParams=new LinearLayout.LayoutParams(0,act.dp(68),1);keyParams.setMargins(act.dp(3),act.dp(3),act.dp(3),act.dp(3));// v1.6.2：按键 52→68dp（真机反馈过小）
        row.addView(key,keyParams);
      }
    }
  }

  void unit(LinearLayout body){
    String[] categories={"长度","重量","温度","数据","速度","面积"};
    TextView catTitle=text("类别",11,act.MUTED());catTitle.setPadding(0,act.dp(4),0,act.dp(2));body.addView(catTitle,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout catRow=chipRow(body);int[] catSel={0};
    TextView[] catChips=new TextView[categories.length];
    final Runnable[] rebuildRef={(Runnable)null};
    for(int i=0;i<categories.length;i++){final int idx=i;catChips[i]=selectChip(catRow,categories[i],i==0,()->{catSel[0]=idx;for(int j=0;j<catChips.length;j++)styleSelect(catChips[j],j==idx);if(rebuildRef[0]!=null)rebuildRef[0].run();});}
    // v1.7.3 单位换算重做（研究 Unitto 720★ 真实截图）：结果区改为「上下两行大数字对照」，单位符号独立小字在右下
    LinearLayout display=new LinearLayout(ctx);display.setOrientation(LinearLayout.VERTICAL);display.setBackground(solid(act.SURFACE()));display.setPadding(act.dp(16),act.dp(14),act.dp(16),act.dp(14));
    LinearLayout fromLine=new LinearLayout(ctx);fromLine.setOrientation(LinearLayout.VERTICAL);fromLine.setGravity(Gravity.END);
    TextView fromValue=text("1",32,act.TEXT());fromValue.setGravity(Gravity.END);fromValue.setSingleLine(true);
    fromLine.addView(fromValue,new LinearLayout.LayoutParams(-1,-2));
    TextView fromUnitLabel=text("",13,act.MUTED());fromUnitLabel.setGravity(Gravity.END);LinearLayout.LayoutParams fromUnitLp=new LinearLayout.LayoutParams(-1,-2);fromUnitLp.topMargin=act.dp(2);fromLine.addView(fromUnitLabel,fromUnitLp);
    display.addView(fromLine,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout toLine=new LinearLayout(ctx);toLine.setOrientation(LinearLayout.VERTICAL);toLine.setGravity(Gravity.END);
    LinearLayout.LayoutParams toLineLp=new LinearLayout.LayoutParams(-1,-2);toLineLp.topMargin=act.dp(18);// v1.7.3：两行数字间距加大（Unitto 的输入/输出是明确分离的两个区块）
    TextView toValue=text("",40,act.PRIMARY());toValue.setGravity(Gravity.END);toValue.setSingleLine(true);toValue.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
    toValue.setContentDescription("换算结果，点按复制");
    toLine.addView(toValue,new LinearLayout.LayoutParams(-1,-2));
    TextView toUnitLabel=text("",13,act.MUTED());toUnitLabel.setGravity(Gravity.END);LinearLayout.LayoutParams toUnitLp=new LinearLayout.LayoutParams(-1,-2);toUnitLp.topMargin=act.dp(2);toLine.addView(toUnitLabel,toUnitLp);
    display.addView(toLine,toLineLp);
    toValue.setClickable(true);toValue.setFocusable(true);
    toValue.setOnClickListener(v->{String value=toValue.getText().toString().trim();if(value.isEmpty())return;copy(value);act.showNotice("已复制",false);});
    body.addView(display,new LinearLayout.LayoutParams(-1,-2));
    final EditText value=new EditText(ctx);value.setText("1");value.setSingleLine(true);value.setTextColor(act.TEXT());value.setHintTextColor(act.MUTED());value.setHint("输入数值");value.setTextSize(16);
    value.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);
    value.setBackground(solid(act.SURFACE2()));value.setPadding(act.dp(14),0,act.dp(14),0);value.setMinHeight(act.dp(52));
    LinearLayout.LayoutParams valueLp=new LinearLayout.LayoutParams(-1,act.dp(52));valueLp.topMargin=act.dp(10);body.addView(value,valueLp);
    TextView fromTitle=text("从",11,act.MUTED());fromTitle.setPadding(0,act.dp(10),0,act.dp(2));body.addView(fromTitle,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout fromRow=chipRow(body);
    // v1.7.3：交换按钮移到两个单位选择器之间（对齐 Unitto 的位置逻辑）
    LinearLayout swapRow=new LinearLayout(ctx);swapRow.setGravity(Gravity.CENTER);LinearLayout.LayoutParams swapParams=new LinearLayout.LayoutParams(-1,-2);swapParams.setMargins(0,act.dp(6),0,act.dp(6));body.addView(swapRow,swapParams);
    TextView toTitle=text("到",11,act.MUTED());toTitle.setPadding(0,act.dp(2),0,act.dp(2));body.addView(toTitle,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout toRow=chipRow(body);
    Runnable convert=()->{
      String fromU=unitOf(fromRow),toU=unitOf(toRow);
      fromUnitLabel.setText(fromU);toUnitLabel.setText(toU);
      String raw=value.getText().toString().trim();
      if(raw.isEmpty()){fromValue.setText("—");toValue.setText("");return;}
      fromValue.setText(raw);
      try{double v=Double.parseDouble(raw);String converted=Toolbox.convertUnit(categories[catSel[0]],v,fromU,toU);
        // Toolbox 返回的是完整句子，这里只取数值部分做大字展示
        String num=converted;
        int arrow=converted.indexOf("→");
        if(arrow>=0){num=converted.substring(arrow+1).trim();
          int sp=num.indexOf(' ');
          if(sp>0){num=num.substring(0,sp);}else{int u=num.indexOf(categories[catSel[0]]);if(u>0)num=num.substring(0,u).trim();}
          // 去掉单位后缀（保留纯数值）
          num=num.replaceAll("[^0-9.\\-eE+]","");}
        toValue.setText(num.isEmpty()?converted:num);
      }catch(Exception e){toValue.setText("请输入数字");}
    };
    Runnable rebuildUnits=()->{
      fromRow.removeAllViews();toRow.removeAllViews();
      String[] units=unitsOf(categories[catSel[0]]);
      for(int i=0;i<units.length;i++)selectChip(fromRow,units[i],i==0,convert);
      for(int i=0;i<units.length;i++)selectChip(toRow,units[i],i==Math.min(1,units.length-1),convert);
      convert.run();
    };
    rebuildRef[0]=rebuildUnits;rebuildUnits.run();
    value.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable s){convert.run();}});
    TextView swap=selectChip(swapRow,"⇄ 交换单位",false,()->{String a=unitOf(fromRow),b=unitOf(toRow);swapChips(fromRow,b);swapChips(toRow,a);convert.run();});
    ((LinearLayout.LayoutParams)swap.getLayoutParams()).gravity=Gravity.CENTER_HORIZONTAL;
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
    // v1.7.4 快捷预设（研究结论：日期工具的高频场景高度集中，主流做法是给预设按钮）
    TextView quickTitle=text("快捷",11,act.MUTED());quickTitle.setPadding(0,act.dp(6),0,act.dp(2));body.addView(quickTitle,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout quick=chipRow(body);
    java.text.SimpleDateFormat fmt=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.CHINA);
    selectChip(quick,"今天",false,()->{a.setText(fmt.format(new java.util.Date()));});
    selectChip(quick,"+7 天",false,()->{n.setText("7");});
    selectChip(quick,"+30 天",false,()->{n.setText("30");});
    selectChip(quick,"+90 天",false,()->{n.setText("90");});
    selectChip(quick,"今年还剩",false,()->{a.setText(fmt.format(new java.util.Date()));b.setText(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)+"-12-31");});
    LinearLayout actions=actionRow(body);
    action(actions,"算间隔",()->output(body,Toolbox.dateDiff(a.getText().toString(),b.getText().toString())));
    action(actions,"N 天后",()->{try{output(body,Toolbox.dateOffset(a.getText().toString(),Integer.parseInt(n.getText().toString().trim())));}catch(Exception e){output(body,Toolbox.dateOffset(a.getText().toString(),0));}});
    action(actions,"今天日期",()->{a.setText(fmt.format(new java.util.Date()));});
    result(body);
  }

  void decision(LinearLayout body){
    // v1.7.5 随机决策重做（研究 Google 官方 dice roller 示例：结果必须是图形而非文字）
    // 上半：图形结果区（骰子点阵 / 硬币圆形，自绘零依赖）
    // 骰子面必须与背景有明确对比：legacy 的 surface2 偏暗、apple 的 surface2 与背景同色 → 统一用「主色淡染」保证两个主题都清晰可见
    int diceFace=ThemeEngine.tint(act.PRIMARY(),44);
    LinearLayout stage=new LinearLayout(ctx);stage.setGravity(Gravity.CENTER);stage.setMinimumHeight(act.dp(132));
    DiceView dice=new DiceView(ctx,diceFace,act.TEXT(),18);
    DiceView.CoinView coin=new DiceView.CoinView(ctx,act.PRIMARY(),act.BG(),"正","反");
    int stageSize=act.dp(120);
    stage.addView(dice,new LinearLayout.LayoutParams(stageSize,stageSize));
    stage.addView(coin,new LinearLayout.LayoutParams(stageSize,stageSize));
    coin.setVisibility(View.GONE);
    body.addView(stage,new LinearLayout.LayoutParams(-1,-2));
    // 结果大字（辅助文字说明，图形为主）
    TextView bigResult=text("点按下方按钮开始",15,act.MUTED());bigResult.setGravity(Gravity.CENTER);bigResult.setPadding(0,act.dp(10),0,act.dp(6));
    body.addView(bigResult,new LinearLayout.LayoutParams(-1,-2));
    // 主操作按钮（加大加粗，对齐官方 24sp 大按钮的设计意图）
    LinearLayout actions=actionRow(body);
    action(actions,"抛硬币",()->{
      boolean heads=Math.random()<0.5;
      coin.setVisibility(View.VISIBLE);dice.setVisibility(View.GONE);
      coin.setHeads(heads);
      bigResult.setText(heads?"正面":"反面");bigResult.setTextColor(act.PRIMARY());bigResult.setTextSize(22);
    });
    action(actions,"掷骰子",()->{
      int v=Toolbox.diceRoll(6);
      dice.setVisibility(View.VISIBLE);coin.setVisibility(View.GONE);
      dice.setValue(v);
      bigResult.setText("点数 "+v);bigResult.setTextColor(act.PRIMARY());bigResult.setTextSize(22);
    });
    action(actions,"1-100 随机",()->{
      int v=Toolbox.diceRoll(100);
      dice.setVisibility(View.GONE);coin.setVisibility(View.GONE);
      bigResult.setText(String.valueOf(v));bigResult.setTextColor(act.PRIMARY());bigResult.setTextSize(28);
    });
    EditText options=input(body,"做个决定：候选用空格分隔（如 吃面 吃饭 麻辣烫）",60);
    LinearLayout decideRow=actionRow(body);
    action(decideRow,"帮我决定",()->{
      String out=Toolbox.decide(options.getText().toString().trim().split("\\s+"));
      dice.setVisibility(View.GONE);coin.setVisibility(View.GONE);
      bigResult.setText(out);bigResult.setTextColor(act.PRIMARY());bigResult.setTextSize(20);
    });
    result(body);
  }

  void scorecard(LinearLayout body){
    // v1.7.6 记分牌重做（研究 Google Android Basics 课程标准记分牌规格）：
    // 补齐两个功能缺失——①只有加没有减（记分牌硬伤）②步长固定为 1；并把分数字号放大到接近官方 56sp
    int[] scores={0,0};
    final int[] step={1};
    // 分数展示区（大字，远距离可读——官方用 56sp 细体）
    LinearLayout row=new LinearLayout(ctx);
    TextView a=bigScore("甲"),b=bigScore("乙");
    LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-1,1);half.setMargins(act.dp(4),0,act.dp(4),0);
    row.addView((View)a.getTag(),half);row.addView((View)b.getTag(),half);
    body.addView(row,new LinearLayout.LayoutParams(-1,act.dp(148)));
    // 步长选择（1/2/3/5/10，覆盖篮球/乒乓/桌游等主流场景）
    TextView stepTitle=text("步长",11,act.MUTED());stepTitle.setPadding(0,act.dp(10),0,act.dp(2));body.addView(stepTitle,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout stepRow=chipRow(body);
    int[] steps={1,2,3,5,10};TextView[] stepChips=new TextView[steps.length];
    for(int i=0;i<steps.length;i++){final int v=steps[i];final int idx=i;
      stepChips[i]=selectChip(stepRow,String.valueOf(v),i==0,()->{step[0]=v;for(int j=0;j<stepChips.length;j++)styleSelect(stepChips[j],j==idx);});
    }
    // 加减双向计分（v1.7.6 目检修正：原四按钮一行会分不清归属 → 改为左右分栏，每侧的 +/− 在各自列内，对齐 Google 课程的左右分栏布局）
    LinearLayout deltaRow=new LinearLayout(ctx);deltaRow.setGravity(Gravity.CENTER_VERTICAL);
    LinearLayout colA=new LinearLayout(ctx);colA.setOrientation(LinearLayout.VERTICAL);
    LinearLayout colB=new LinearLayout(ctx);colB.setOrientation(LinearLayout.VERTICAL);
    TextView minusA=scoreButton("−",act.ERROR_TOKEN());TextView plusA=scoreButton("＋",act.PRIMARY());
    TextView minusB=scoreButton("−",act.ERROR_TOKEN());TextView plusB=scoreButton("＋",act.PRIMARY());
    LinearLayout.LayoutParams btnLp=new LinearLayout.LayoutParams(-1,act.dp(52));btnLp.setMargins(act.dp(4),act.dp(3),act.dp(4),act.dp(3));
    colA.addView(plusA,btnLp);
    LinearLayout.LayoutParams btnLp2=new LinearLayout.LayoutParams(-1,act.dp(52));btnLp2.setMargins(act.dp(4),act.dp(3),act.dp(4),act.dp(3));
    colA.addView(minusA,btnLp2);
    LinearLayout.LayoutParams btnLp3=new LinearLayout.LayoutParams(-1,act.dp(52));btnLp3.setMargins(act.dp(4),act.dp(3),act.dp(4),act.dp(3));
    colB.addView(plusB,btnLp3);
    LinearLayout.LayoutParams btnLp4=new LinearLayout.LayoutParams(-1,act.dp(52));btnLp4.setMargins(act.dp(4),act.dp(3),act.dp(4),act.dp(3));
    colB.addView(minusB,btnLp4);
    LinearLayout.LayoutParams colLp=new LinearLayout.LayoutParams(0,-2,1);colLp.setMargins(act.dp(4),0,act.dp(4),0);
    deltaRow.addView(colA,colLp);
    LinearLayout.LayoutParams colLp2=new LinearLayout.LayoutParams(0,-2,1);colLp2.setMargins(act.dp(4),0,act.dp(4),0);
    deltaRow.addView(colB,colLp2);
    body.addView(deltaRow,new LinearLayout.LayoutParams(-1,-2));
    // 独立重置按钮（对齐官方做法；长按分数清零保留为快捷方式）
    LinearLayout actions=actionRow(body);
    action(actions,"重置比分",()->{scores[0]=0;scores[1]=0;a.setText("0");b.setText("0");});
    TextView hint=text("点 ＋/− 按当前步长加减；长按分数直接清零",11,act.MUTED());hint.setPadding(0,act.dp(6),0,0);body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    Runnable refresh=()->{a.setText(String.valueOf(scores[0]));b.setText(String.valueOf(scores[1]));};
    plusA.setOnClickListener(v->{scores[0]+=step[0];refresh.run();bump(a);});
    minusA.setOnClickListener(v->{scores[0]-=step[0];refresh.run();bump(a);});
    plusB.setOnClickListener(v->{scores[1]+=step[0];refresh.run();bump(b);});
    minusB.setOnClickListener(v->{scores[1]-=step[0];refresh.run();bump(b);});
    a.setOnLongClickListener(v->{scores[0]=0;refresh.run();return true;});
    b.setOnLongClickListener(v->{scores[1]=0;refresh.run();return true;});
  }
  /** 记分牌加减按钮（大尺寸，颜色区分方向） */
  TextView scoreButton(String label,int color){
    TextView key=text(label,26,color);key.setGravity(Gravity.CENTER);key.setClickable(true);key.setFocusable(true);
    key.setContentDescription(label.equals("＋")?"加分":"减分");
    GradientDrawable bg=solid(ThemeEngine.tint(color,40));bg.setCornerRadius(act.dp(16));bg.setStroke(act.dp(1),ThemeEngine.tint(color,110));
    key.setBackground(ripple(bg));
    return key;
  }
  TextView bigScore(String label){
    LinearLayout wrap=new LinearLayout(ctx);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setGravity(Gravity.CENTER);
    GradientDrawable bg=solid(act.SURFACE());bg.setStroke(act.dp(1),act.DIV());wrap.setBackground(ripple(bg));
    TextView name=text(label,12,act.MUTED());name.setGravity(Gravity.CENTER);wrap.addView(name,new LinearLayout.LayoutParams(-1,act.dp(24)));
    TextView value=text("0",56,act.TEXT());value.setTypeface(android.graphics.Typeface.create("sans-serif-light",android.graphics.Typeface.NORMAL));value.setGravity(Gravity.CENTER);// v1.7.6：34->56sp 细体（对齐 Google 课程规格 56sp sans-serif-light，远距离可读）
    wrap.addView(value,new LinearLayout.LayoutParams(-1,act.dp(64)));
    // 返回内层分数 TextView；用 tag 关联外壳
    value.setTag(wrap);return value;
  }
  void bump(View v){if(!act.motionEnabled())return;v.animate().cancel();v.setScaleX(1f);v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(90).withEndAction(()->v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()).start();}

  void calendarGrid(LinearLayout body){
    // v1.7.7 万年历重做（研究 myjoybar/android-calendar-view 434★ + kizitonwose/Calendar 5595★）：
    // 补齐三个交互缺失——①点击日期无反馈 ②无「回到今天」③无年月快速跳转；并加节气/节日标记
    java.util.Calendar today=java.util.Calendar.getInstance();
    final int[] cursor={today.get(java.util.Calendar.YEAR),today.get(java.util.Calendar.MONTH)};
    TextView title=text("",16,act.TEXT());title.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);title.setGravity(Gravity.CENTER);
    TextView detail=text("点按日期查看详情",12,act.MUTED());detail.setGravity(Gravity.CENTER);detail.setPadding(act.dp(8),act.dp(8),act.dp(8),act.dp(4));
    GridLayout grid=new GridLayout(ctx);grid.setColumnCount(7);
    final Runnable[] renderRef={(Runnable)null};
    Runnable render=()->{
      grid.removeAllViews();
      int year=cursor[0],month=cursor[1];// month 0-based
      title.setText(year+" 年 "+(month+1)+" 月");
      String[] week={"一","二","三","四","五","六","日"};
      for(String w:week){TextView cell=gridCell();cell.setText(w);cell.setTextColor(act.MUTED());grid.addView(cell,new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f)));}
      int[] cells=Toolbox.calendarGrid(year,month+1);
      int todayY=today.get(java.util.Calendar.YEAR),todayM=today.get(java.util.Calendar.MONTH);
      boolean isThis=todayY==year&&todayM==month;
      int todayD=today.get(java.util.Calendar.DAY_OF_MONTH);
      for(final int d:cells){TextView cell=gridCell();
        if(d==0){cell.setText("");grid.addView(cell,new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f)));continue;}
        // 每格两行：上排日期数字 + 下排节气/节日标记（对齐国内日历的标准做法）
        String mark=Toolbox.solarTermOrFestival(year,month+1,d);
        if(mark.isEmpty()){cell.setText(String.valueOf(d));cell.setTextSize(13);}
        else{cell.setText(d+"\n"+mark);cell.setTextSize(11);cell.setLineSpacing(0,0.9f);}
        cell.setGravity(Gravity.CENTER);
        if(isThis&&d==todayD){cell.setTextColor(act.BG());GradientDrawable dot=solid(act.PRIMARY());dot.setCornerRadius(act.dp(18));cell.setBackground(dot);}
        // v1.7.7：格子可点，显示该日详情（研究结论：主流日历点击必须有反馈）
        final int fy=year,fm=month+1;
        cell.setClickable(true);cell.setFocusable(true);
        cell.setOnClickListener(v->{
          java.util.Calendar c=java.util.Calendar.getInstance();c.set(fy,fm-1,d);
          int dow=c.get(java.util.Calendar.DAY_OF_WEEK);
          int doy=c.get(java.util.Calendar.DAY_OF_YEAR);
          int total=c.getActualMaximum(java.util.Calendar.DAY_OF_YEAR);
          String term=Toolbox.solarTermOrFestival(fy,fm,d);
          StringBuilder sb=new StringBuilder();
          sb.append(fy).append("-").append(fm<10?"0":"").append(fm).append("-").append(d<10?"0":"").append(d);
          sb.append(" · 星期").append("日一二三四五六".charAt(dow-1));
          sb.append(" · 今年第 ").append(doy).append(" 天");
          sb.append(" · 距年末 ").append(total-doy).append(" 天");
          if(!term.isEmpty())sb.append("\n").append(term);
          detail.setText(sb.toString());detail.setTextColor(act.TEXT());
        });
        grid.addView(cell,new GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED,1f),GridLayout.spec(GridLayout.UNDEFINED,1f)));
      }
    };
    renderRef[0]=render;
    LinearLayout header=new LinearLayout(ctx);header.setGravity(Gravity.CENTER_VERTICAL);
    ImageButton prev=act.iconButton(R.drawable.ic_back,"上一个月");prev.setOnClickListener(v->{cursor[1]--;if(cursor[1]<0){cursor[1]=11;cursor[0]--;}render.run();});
    ImageButton next=act.iconButton(R.drawable.ic_refresh,"下一个月");next.setRotation(180f);next.setOnClickListener(v->{cursor[1]++;if(cursor[1]>11){cursor[1]=0;cursor[0]++;}render.run();});
    header.addView(prev,new LinearLayout.LayoutParams(act.dp(44),act.dp(44)));
    header.addView(title,new LinearLayout.LayoutParams(0,act.dp(44),1));
    header.addView(next,new LinearLayout.LayoutParams(act.dp(44),act.dp(44)));
    // v1.7.7：点标题弹年月选择（对齐主流做法，避免逐月翻）
    title.setClickable(true);title.setFocusable(true);title.setBackground(ripple(new ColorDrawable(Color.TRANSPARENT)));
    title.setOnClickListener(v->{
      EditText input=new EditText(ctx);input.setSingleLine(true);input.setTextColor(act.TEXT());input.setHintTextColor(act.MUTED());input.setHint("如 2026-9");input.setTextSize(16);
      input.setText(cursor[0]+"-"+(cursor[1]+1));
      LinearLayout panel=new LinearLayout(ctx);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(act.dp(22),act.dp(8),act.dp(22),0);
      TextView hint=text("输入 年-月 快速跳转，例如 2026-9",12,act.MUTED());hint.setPadding(0,0,0,act.dp(8));panel.addView(hint,new LinearLayout.LayoutParams(-1,-2));
      panel.addView(input,new LinearLayout.LayoutParams(-1,act.dp(56)));
      android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(ctx).setTitle("跳转到").setView(panel).setNegativeButton("取消",null).setPositiveButton("跳转",null).create();
      dialog.setOnShowListener(w->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
        try{String[] parts=input.getText().toString().trim().split("-");int y=Integer.parseInt(parts[0].trim()),m=Integer.parseInt(parts[1].trim());
          if(m<1||m>12||y<1900||y>2200){input.setError("范围：1900-2200 年，1-12 月");return;}
          cursor[0]=y;cursor[1]=m-1;render.run();dialog.dismiss();
        }catch(Exception e){input.setError("格式：2026-9");}
      }));
      dialog.show();
    });
    LinearLayout card=listCard();card.addView(header,new LinearLayout.LayoutParams(-1,act.dp(52)));card.addView(grid,new LinearLayout.LayoutParams(-1,-2));
    body.addView(card,new LinearLayout.LayoutParams(-1,-2));
    body.addView(detail,new LinearLayout.LayoutParams(-1,-2));
    // v1.7.7：回到今天（翻走后一键返回当前月）
    LinearLayout actions=actionRow(body);
    action(actions,"回到今天",()->{
      cursor[0]=today.get(java.util.Calendar.YEAR);cursor[1]=today.get(java.util.Calendar.MONTH);render.run();
      int dow=today.get(java.util.Calendar.DAY_OF_WEEK),doy=today.get(java.util.Calendar.DAY_OF_YEAR),total=today.getActualMaximum(java.util.Calendar.DAY_OF_YEAR);
      detail.setText("今天 · "+cursor[0]+"-"+(cursor[1]+1)+"-"+today.get(java.util.Calendar.DAY_OF_MONTH)+" · 星期"+"日一二三四五六".charAt(dow-1)+" · 今年第 "+doy+" 天");
      detail.setTextColor(act.PRIMARY());
    });
    render.run();
  }
  TextView gridCell(){TextView cell=text("",13,act.TEXT());cell.setGravity(Gravity.CENTER);cell.setHeight(act.dp(42));return cell;}

  void randomnum(LinearLayout body){
    // v1.10.4 工具精修11：排序选项（不排序=抽签序/升/降）+重新生成+复制结果；SecureRandom 在 Toolbox 层
    EditText min=input(body,"下限（默认 1）",44),max=input(body,"上限（默认 100）",44),count=input(body,"生成个数（默认 1，最多 200）",44);
    min.setInputType(InputType.TYPE_CLASS_NUMBER);max.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_SIGNED);count.setInputType(InputType.TYPE_CLASS_NUMBER);
    CheckBox unique=checkInline(checkRow(body),"去重（抽签模式）",false);
    final EditText minF=min,maxF=max,countF=count;final CheckBox uniqueF=unique;final int[] sort={0};
    TextView hint=text("",12,act.MUTED());hint.setPadding(0,act.dp(4),0,act.dp(4));body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout actions=actionRow(body);
    final String[] current={null};
    action(actions,"复制结果",()->{if(current[0]!=null)copy(current[0]);});
    result(body);
    Runnable recalcRnd=()->{
      String r=Toolbox.randomNumbers(parseInt(minF,1),parseInt(maxF,100),parseInt(countF,1),uniqueF.isChecked(),sort[0]);
      current[0]=r;output(body,r);
      hint.setText(r.startsWith("上限")||r.startsWith("去重")?r:"已生成 · SecureRandom 加密级随机");
    };
    android.text.TextWatcher w=new android.text.TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){}@Override public void afterTextChanged(android.text.Editable s){recalcRnd.run();}};
    min.addTextChangedListener(w);max.addTextChangedListener(w);count.addTextChangedListener(w);
    unique.setOnClickListener(v->recalcRnd.run());
    LinearLayout sortRow=chipRow(body);sortRow.setPadding(0,act.dp(8),0,0);
    LinearLayout.LayoutParams slp=chipMargin();slp.rightMargin=act.dp(12);sortRow.addView(text("排序",12,act.TEXT()),slp);
    TextView[] schips=new TextView[3];int[] sorts={0,1,2};String[] slabels={"不排序","升序","降序"};
    for(int i=0;i<3;i++){final int idx=i;schips[i]=selectChip(sortRow,slabels[i],sort[0]==sorts[i],()->{sort[0]=sorts[idx];for(int j=0;j<3;j++)styleSelect(schips[j],sorts[j]==sort[0]);recalcRnd.run();});}
    recalcRnd.run();
  }

  void password(LinearLayout body){
    // v1.7.8 工具精修07：对齐 Bitwarden/KeePassDX 规格（见 07-研究报告）——滑杆长度/类别开关/形近排除/
    // 熵值五档强度条/按类别着色等宽结果/重新生成与复制/最近5条；SecureRandom 在 Toolbox 层。
    final int[] len={16};
    TextView hint=text("加密级安全随机生成；强度按字符池熵值估算。",12,act.MUTED());hint.setPadding(0,0,0,act.dp(8));body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout lenRow=new LinearLayout(ctx);lenRow.setGravity(Gravity.CENTER_VERTICAL);
    TextView lenLabel=text("长度",13,act.TEXT());lenRow.addView(lenLabel,new LinearLayout.LayoutParams(-2,-2));
    TextView lenValue=text("16",15,act.PRIMARY());lenValue.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    LinearLayout.LayoutParams lvLp=new LinearLayout.LayoutParams(-2,-2);lvLp.leftMargin=act.dp(10);lenRow.addView(lenValue,lvLp);
    TextView lenRange=text("（8-64）",11,act.MUTED());
    LinearLayout.LayoutParams lrLp=new LinearLayout.LayoutParams(-2,-2);lrLp.leftMargin=act.dp(6);lenRow.addView(lenRange,lrLp);
    body.addView(lenRow,new LinearLayout.LayoutParams(-1,act.dp(36)));
    android.widget.SeekBar slider=new android.widget.SeekBar(ctx);slider.setMax(56);slider.setProgress(8);
    slider.setProgressTintList(android.content.res.ColorStateList.valueOf(act.PRIMARY()));
    slider.setThumbTintList(android.content.res.ColorStateList.valueOf(act.PRIMARY()));
    body.addView(slider,new LinearLayout.LayoutParams(-1,act.dp(36)));
    LinearLayout checks=new LinearLayout(ctx);checks.setGravity(Gravity.CENTER_VERTICAL);
    CheckBox upper=checkInline(checks,"大写",true),lower=checkInline(checks,"小写",true),digits=checkInline(checks,"数字",true),symbols=checkInline(checks,"符号",true);
    body.addView(checks,new LinearLayout.LayoutParams(-1,act.dp(44)));
    LinearLayout checks2=new LinearLayout(ctx);checks2.setGravity(Gravity.CENTER_VERTICAL);
    CheckBox avoid=checkInline(checks2,"排除形近字符（l I 1 O 0）",true);
    body.addView(checks2,new LinearLayout.LayoutParams(-1,act.dp(40)));
    LinearLayout strengthBar=new LinearLayout(ctx);strengthBar.setPadding(0,act.dp(6),0,0);
    final View[] segs=new View[5];final int[] segColors={0xFFE5484D,0xFFE08A00,0xFFD4A72C,0xFF46A758,0xFF2BA672};
    for(int i=0;i<5;i++){segs[i]=new View(ctx);android.graphics.drawable.GradientDrawable sg=solid(act.DIV());
      LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,act.dp(6),1f);sp.setMargins(i==0?0:act.dp(3),0,i==4?0:act.dp(3),0);
      strengthBar.addView(segs[i],sp);segs[i].setBackground(sg);}
    body.addView(strengthBar,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout gradeRow=new LinearLayout(ctx);gradeRow.setGravity(Gravity.CENTER_VERTICAL);
    TextView grade=text("—",13,act.MUTED());grade.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
    gradeRow.addView(grade,new LinearLayout.LayoutParams(-2,-2));
    TextView bits=text("",11,act.MUTED());
    LinearLayout.LayoutParams bitsLp=new LinearLayout.LayoutParams(-2,-2);bitsLp.leftMargin=act.dp(10);gradeRow.addView(bits,bitsLp);
    body.addView(gradeRow,new LinearLayout.LayoutParams(-1,act.dp(30)));
    TextView out=text("",22,act.TEXT());out.setTypeface(android.graphics.Typeface.MONOSPACE);out.setGravity(Gravity.CENTER);
    out.setPadding(act.dp(12),act.dp(14),act.dp(12),act.dp(14));out.setMinHeight(act.dp(72));out.setBackground(solid(act.SURFACE()));
    out.setTextIsSelectable(false);
    body.addView(out,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout actions=actionRow(body);
    TextView histTitle=text("最近生成（点击复制）",12,act.MUTED());histTitle.setPadding(0,act.dp(12),0,act.dp(4));body.addView(histTitle,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout hist=new LinearLayout(ctx);hist.setOrientation(LinearLayout.VERTICAL);
    body.addView(hist,new LinearLayout.LayoutParams(-1,-2));
    final java.util.ArrayList<String> history=new java.util.ArrayList<>();
    final String[] current={null};
    final CheckBox uF=upper,lF=lower,dF=digits,sF=symbols,aF=avoid;
    Runnable regen=()->{
      String p=Toolbox.generatePassword(len[0],uF.isChecked(),lF.isChecked(),dF.isChecked(),sF.isChecked(),aF.isChecked());
      current[0]=p;
      if(p==null){
        out.setText("至少选择一种字符");out.setTextColor(act.MUTED());
        grade.setText("—");grade.setTextColor(act.MUTED());bits.setText("");
        for(View s:segs)s.setBackgroundColor(act.DIV());
        return;
      }
      out.setTextColor(act.TEXT());
      android.text.SpannableString span=new android.text.SpannableString(p);
      for(int i=0;i<p.length();i++){char c=p.charAt(i);int color;
        if(c>='0'&&c<='9')color=0xFFF64F3E;else if(c=='!'||c=='@'||c=='#'||c=='$'||c=='%'||c=='^'||c=='&'||c=='*')color=0xFF27A6E4;else color=act.TEXT();
        span.setSpan(new android.text.style.ForegroundColorSpan(color),i,i+1,android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);}
      out.setText(span);
      int poolSize=Toolbox.passwordPoolSize(uF.isChecked(),lF.isChecked(),dF.isChecked(),sF.isChecked(),aF.isChecked());
      double eb=Toolbox.passwordEntropyBits(len[0],poolSize);
      int level=eb>=128?5:eb>=60?4:eb>=36?3:eb>=28?2:1;
      String gradeText=level==5?"极强":level==4?"强":level==3?"一般":level==2?"弱":"非常弱";
      grade.setText(gradeText);grade.setTextColor(segColors[level-1]);
      bits.setText(String.format(java.util.Locale.US,"%.0f bits",eb));
      for(int i=0;i<5;i++)segs[i].setBackgroundColor(i<level?segColors[i]:act.DIV());
      if(history.isEmpty()||!history.get(0).equals(p)){history.add(0,p);if(history.size()>5)history.remove(history.size()-1);
        hist.removeAllViews();
        for(final String h:history){TextView row=text(h,13,act.TEXT());row.setTypeface(android.graphics.Typeface.MONOSPACE);
          row.setPadding(act.dp(10),act.dp(8),act.dp(10),act.dp(8));row.setBackground(solid(act.SURFACE()));row.setClickable(true);row.setFocusable(true);
          row.setOnClickListener(v->copy(h));
          LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=act.dp(6);hist.addView(row,hp);}
      }
    };
    android.widget.SeekBar.OnSeekBarChangeListener sliderListener=new android.widget.SeekBar.OnSeekBarChangeListener(){
      @Override public void onProgressChanged(android.widget.SeekBar bar,int progress,boolean fromUser){len[0]=progress+8;lenValue.setText(String.valueOf(len[0]));}
      @Override public void onStartTrackingTouch(android.widget.SeekBar bar){}
      @Override public void onStopTrackingTouch(android.widget.SeekBar bar){regen.run();}
    };
    slider.setOnSeekBarChangeListener(sliderListener);
    android.view.View.OnClickListener recheck=v->regen.run();
    upper.setOnClickListener(recheck);lower.setOnClickListener(recheck);digits.setOnClickListener(recheck);symbols.setOnClickListener(recheck);avoid.setOnClickListener(recheck);
    action(actions,"重新生成",()->regen.run());
    primaryAction(actions,"复制",()->{if(current[0]!=null)copy(current[0]);});
    regen.run();
  }

  void json(LinearLayout body){
    // v1.10.2 工具精修09：缩进可选/压缩/校验报告（行列定位+统计）/非 ASCII 转义；研究见 07-研究报告
    EditText input=input(body,"粘贴 JSON…",160);
    LinearLayout indentRow=chipRow(body);indentRow.setPadding(0,act.dp(10),0,0);
    LinearLayout.LayoutParams ilp=chipMargin();ilp.rightMargin=act.dp(12);indentRow.addView(text("缩进",12,act.TEXT()),ilp);
    final int[] indent={2};TextView[] chips=new TextView[2];int[] indents={2,4};String[] labels={"2 空格","4 空格"};
    for(int i=0;i<2;i++){final int idx=i;chips[i]=selectChip(indentRow,labels[i],indent[0]==indents[i],()->{indent[0]=indents[idx];for(int j=0;j<2;j++)styleSelect(chips[j],indents[j]==indent[0]);});}
    CheckBox ascii=checkInline(checkRow(body),"非 ASCII 转义为 \\uXXXX",false);
    LinearLayout actions=actionRow(body);primaryAction(actions,"美化",()->{try{output(body,Toolbox.jsonFormat(input.getText().toString(),indent[0],ascii.isChecked()));}catch(Exception e){output(body,Toolbox.jsonError((org.json.JSONException)e));}});
    action(actions,"压缩",()->{try{output(body,Toolbox.jsonFormat(input.getText().toString(),0,ascii.isChecked()));}catch(Exception e){output(body,Toolbox.jsonError((org.json.JSONException)e));}});
    action(actions,"校验",()->output(body,Toolbox.jsonValidate(input.getText().toString())));
    result(body);
  }

  void regex(LinearLayout body){
    // v1.10.3 工具精修10：对齐 regex101 约定——实时高亮/匹配清单/替换预览/忽略大小写/常用模板；研究见 07-研究报告
    EditText pattern=input(body,"正则表达式，如 \\d+（或点下方模板）",60);
    EditText text=input(body,"被匹配的文本…",120);
    CheckBox ic=checkInline(checkRow(body),"忽略大小写",false);
    TextView summary=text("",12,act.MUTED());summary.setPadding(0,act.dp(6),0,act.dp(4));body.addView(summary,new LinearLayout.LayoutParams(-1,-2));
    TextView preview=text("",13,act.TEXT());preview.setTypeface(android.graphics.Typeface.MONOSPACE);preview.setPadding(act.dp(12),act.dp(10),act.dp(12),act.dp(10));preview.setBackground(solid(act.SURFACE()));preview.setMinHeight(act.dp(56));
    body.addView(preview,new LinearLayout.LayoutParams(-1,-2));
    EditText repl=input(body,"替换为（支持 $1 组引用，留空仅测试）",44);
    LinearLayout actions=actionRow(body);primaryAction(actions,"替换",()->{String r=Toolbox.regexReplace(pattern.getText().toString(),text.getText().toString(),repl.getText().toString(),ic.isChecked());output(body,r);});
    result(body);
    final EditText pF=pattern,tF=text,rF=repl;final CheckBox icF=ic;
    Runnable recalcRegex=()->{
      String p=pF.getText().toString();
      int[][] ranges=Toolbox.regexRanges(p,tF.getText().toString(),icF.isChecked());
      if(ranges==null){summary.setText("正则有误，请检查语法");summary.setTextColor(0xFFE5484D);preview.setText("");return;}
      summary.setText("匹配 "+ranges.length+" 处");summary.setTextColor(ranges.length>0?0xFF46A758:act.MUTED());
      String src=tF.getText().toString();
      android.text.SpannableString span=new android.text.SpannableString(src);
      int limit=Math.min(src.length(),2000);
      for(int[] r:ranges){if(r[0]>=limit)break;int e=Math.min(r[1],limit);
        span.setSpan(new android.text.style.BackgroundColorSpan(0x59A78BFA),r[0],e,android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        span.setSpan(new android.text.style.ForegroundColorSpan(0xFF46A758),r[0],e,android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);}
      preview.setText(span);
    };
    android.text.TextWatcher w=new android.text.TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){recalcRegex.run();}@Override public void afterTextChanged(android.text.Editable s){}};
    pattern.addTextChangedListener(w);text.addTextChangedListener(w);repl.addTextChangedListener(w);
    ic.setOnClickListener(v->recalcRegex.run());
    LinearLayout tpl=chipRow(body);tpl.setPadding(0,act.dp(8),0,0);
    LinearLayout.LayoutParams tlp=chipMargin();tlp.rightMargin=act.dp(8);tpl.addView(text("模板",12,act.TEXT()),tlp);
    String[][] tpls={{"手机号","1[3-9]\\d{9}"},{"邮箱","[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"},{"URL","https?://[^\\s]+"},{"日期","\\d{4}-\\d{2}-\\d{2}"},{"身份证","\\d{17}[0-9X]"},{"中文","[\\u4e00-\\u9fa5]+"},{"数字","\\d+(\\.\\d+)?"}};
    for(int i=0;i<tpls.length;i++){final String p=tpls[i][1];selectChip(tpl,tpls[i][0],false,()->{pattern.setText(p);recalcRegex.run();});}
    recalcRegex.run();
  }

  void uuid(LinearLayout body){
    // v1.10.5 工具精修12：RFC 4122 多版本（v4 随机批量 / v5 SHA-1 / v3 MD5 名称型）+大写/去连字符+逐行点击复制；研究见 07-研究报告
    LinearLayout verRow=chipRow(body);verRow.setPadding(0,act.dp(4),0,0);
    LinearLayout.LayoutParams vlp=chipMargin();vlp.rightMargin=act.dp(12);verRow.addView(text("版本",12,act.TEXT()),vlp);
    final int[] version={4};TextView[] vchips=new TextView[3];int[] vers={4,5,3};String[] vlabels={"v4 随机","v5 SHA-1","v3 MD5"};
    EditText name=input(body,"名称（v5/v3 用：同一名称与命名空间结果恒定）",44);
    LinearLayout nsRow=chipRow(body);
    LinearLayout.LayoutParams nlp=chipMargin();nlp.rightMargin=act.dp(12);nsRow.addView(text("命名空间",12,act.TEXT()),nlp);
    final String[][] nsH={{"DNS","6ba7b8109dad11d180b400c04fd430c8"},{"URL","6ba7b8119dad11d180b400c04fd430c8"},{"OID","6ba7b8129dad11d180b400c04fd430c8"},{"X500","6ba7b8149dad11d180b400c04fd430c8"}};
    final int[] nsel={0};
    EditText count=input(body,"生成数量（v4，默认 5，最多 100）",44);count.setInputType(InputType.TYPE_CLASS_NUMBER);
    CheckBox upper=checkInline(checkRow(body),"大写",false);
    CheckBox noDash=checkInline(checkRow(body),"去连字符",false);
    TextView hint=text("",12,act.MUTED());hint.setPadding(0,act.dp(4),0,act.dp(4));body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout list=new LinearLayout(ctx);list.setOrientation(LinearLayout.VERTICAL);body.addView(list,new LinearLayout.LayoutParams(-1,-2));
    final String[] current={null};
    LinearLayout actions=actionRow(body);
    final EditText nameF=name,countF=count;final CheckBox upF=upper,dashF=noDash;
    Runnable recalcUuid=()->{
      boolean up=upF.isChecked(),nd=dashF.isChecked();
      list.removeAllViews();StringBuilder all=new StringBuilder();
      if(version[0]==4){
        int n=Math.max(1,Math.min(100,parseInt(countF,5)));
        for(int i=0;i<n;i++){String u=Toolbox.uuidFormat(java.util.UUID.randomUUID().toString(),up,nd);all.append(u).append('\n');addUuidRow(list,u);}
      }else{
        String u=Toolbox.uuidFormat(Toolbox.uuidNameBased(version[0],nameF.getText().toString(),nsH[nsel[0]][1]),up,nd);
        all.append(u);addUuidRow(list,u);
      }
      current[0]=all.toString().trim();
      hint.setText(version[0]==4?"v4：加密级随机，逐行点击复制":"RFC 4122 名称型：同一名称+命名空间结果恒定");
    };
    android.text.TextWatcher w=new android.text.TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){recalcUuid.run();}@Override public void afterTextChanged(android.text.Editable s){}};
    name.addTextChangedListener(w);count.addTextChangedListener(w);
    upper.setOnClickListener(v->recalcUuid.run());noDash.setOnClickListener(v->recalcUuid.run());
    primaryAction(actions,"重新生成",recalcUuid::run);action(actions,"复制全部",()->{if(current[0]!=null)copy(current[0]);});
    primaryAction(actions,"重新生成",recalcUuid::run);action(actions,"复制全部",()->{if(current[0]!=null)copy(current[0]);});
    for(int i=0;i<3;i++){final int idx=i;vchips[i]=selectChip(verRow,vlabels[i],version[0]==vers[i],()->{version[0]=vers[idx];for(int j=0;j<3;j++)styleSelect(vchips[j],vers[j]==version[0]);recalcUuid.run();});}
    TextView[] nschips=new TextView[nsH.length];
    for(int i=0;i<nsH.length;i++){final int idx=i;nschips[i]=selectChip(nsRow,nsH[i][0],nsel[0]==idx,()->{nsel[0]=idx;for(int j=0;j<nsH.length;j++)styleSelect(nschips[j],nsel[0]==j);recalcUuid.run();});}
    recalcUuid.run();
  }
  private void addUuidRow(LinearLayout wrap,String value){
    TextView row=text(value,13,act.TEXT());row.setTypeface(android.graphics.Typeface.MONOSPACE);
    row.setPadding(act.dp(10),act.dp(8),act.dp(10),act.dp(8));row.setBackground(solid(act.SURFACE()));
    row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->copy(value));
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=act.dp(6);wrap.addView(row,lp);
  }

  void hash(LinearLayout body){
    // v1.10.1 工具精修08：对齐 hash-checker 规格（见 07-研究报告）——六算法实时计算+期望值比对高亮+点击复制+大写开关
    TextView hint=text("输入即实时计算；粘贴期望哈希自动比对校验。",12,act.MUTED());hint.setPadding(0,0,0,act.dp(8));body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    EditText input=input(body,"输入文本…",100);
    LinearLayout opts=new LinearLayout(ctx);opts.setGravity(Gravity.CENTER_VERTICAL);
    CheckBox upper=checkInline(opts,"大写输出",false);
    body.addView(opts,new LinearLayout.LayoutParams(-1,act.dp(40)));
    EditText expect=input(body,"粘贴期望哈希值（可选，自动比对）",44);
    TextView matchTip=text("",12,act.MUTED());matchTip.setPadding(0,act.dp(4),0,act.dp(4));body.addView(matchTip,new LinearLayout.LayoutParams(-1,-2));
    String[] algos=Toolbox.HASH_ALGOS;
    LinearLayout[] rows=new LinearLayout[algos.length];
    TextView[] hexViews=new TextView[algos.length];
    TextView[] nameViews=new TextView[algos.length];
    for(int i=0;i<algos.length;i++){
      LinearLayout row=new LinearLayout(ctx);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(act.dp(12),act.dp(8),act.dp(12),act.dp(8));row.setBackground(solid(act.SURFACE()));row.setClickable(true);row.setFocusable(true);
      TextView name=text(algos[i],12,act.PRIMARY());name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
      TextView hex=text("—",13,act.TEXT());hex.setTypeface(android.graphics.Typeface.MONOSPACE);
      LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=act.dp(2);row.addView(name,new LinearLayout.LayoutParams(-1,-2));row.addView(hex,hp);
      LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=i==0?act.dp(4):act.dp(8);body.addView(row,rp);
      rows[i]=row;hexViews[i]=hex;nameViews[i]=name;
      final String algo=algos[i];final TextView hexF=hex;final CheckBox upperF=upper;
      row.setOnClickListener(v->{String h=hexF.getText().toString();if(!h.equals("—"))copy(upperF.isChecked()?h.toUpperCase(java.util.Locale.US):h);});
    }
    final EditText inputF=input,expectF=expect;
    final CheckBox upperF=upper;
    Runnable recalc=()->{
      String v=inputF.getText().toString();
      String expected=expectF.getText().toString().trim();
      boolean anyMatch=false;
      for(int i=0;i<algos.length;i++){
        String hex=Toolbox.hashHex(algos[i],v);
        if(hex==null){hexViews[i].setText("不支持");}
        else{
          hexViews[i].setText(upperF.isChecked()?hex.toUpperCase(java.util.Locale.US):hex);
          boolean match=expected.length()>0&&hex.equalsIgnoreCase(expected);
          hexViews[i].setTextColor(match?0xFF46A758:act.TEXT());
          nameViews[i].setText(algos[i]+(match?"  ✓ 匹配":""));
          if(match)anyMatch=true;
        }
      }
      matchTip.setText(expected.length()==0?"":(anyMatch?"校验通过：与期望值匹配":"校验失败：与期望值不匹配"));
      matchTip.setTextColor(expected.length()==0?act.MUTED():(anyMatch?0xFF46A758:0xFFE5484D));
    };
    input.addTextChangedListener(new android.text.TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){recalc.run();}@Override public void afterTextChanged(android.text.Editable s){}});
    expect.addTextChangedListener(new android.text.TextWatcher(){@Override public void beforeTextChanged(CharSequence s,int st,int c,int a){}@Override public void onTextChanged(CharSequence s,int st,int b,int c){recalc.run();}@Override public void afterTextChanged(android.text.Editable s){}});
    upper.setOnClickListener(v->recalc.run());
    recalc.run();
  }

  void imageCompress(LinearLayout body){
    TextView info=text("选择图片 → 选质量 → 压缩并保存到相册“东方无限”目录。",12,act.MUTED());info.setPadding(0,0,0,act.dp(10));body.addView(info,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout actions=actionRow(body);action(actions,"选择图片",()->act.pickToolImage());
    TextView meta=text(act.toolImageInfoText(),12,act.MUTED());meta.setTag("img-info");meta.setBackground(solid(act.SURFACE()));meta.setPadding(act.dp(12),act.dp(10),act.dp(12),act.dp(10));meta.setMinHeight(act.dp(72));
    body.addView(meta,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout quality=chipRow(body);quality.setPadding(0,act.dp(10),0,0);
    LinearLayout.LayoutParams labelLp=chipMargin();labelLp.rightMargin=act.dp(12);quality.addView(text("压缩质量",12,act.TEXT()),labelLp);
    int[] qualities={90,70,50};TextView[] chips=new TextView[qualities.length];
    for(int i=0;i<qualities.length;i++){final int q=qualities[i];chips[i]=selectChip(quality,q+"%",act.toolQuality()==q,()->{act.setToolQuality(q);for(int j=0;j<chips.length;j++)styleSelect(chips[j],qualities[j]==q);});}
    quality.setTag("img-quality");
    LinearLayout run=actionRow(body);action(run,"压缩并保存",()->{if(act.toolImageUri()==null){act.showNotice("先选择图片",true);return;}act.showNotice("正在压缩…",false);act.runImageCompressPending();});
    result(body);
  }

  void deviceinfo(LinearLayout body){
    // 复审2-推倒重做:进入即加载,分组卡片,不要"点按钮才出结果";所有读取包 try-catch,任一项失败显示"不可用"而不是崩溃
    body.addView(infoSection(ctx,"系统"));
    body.addView(kvCard(ctx,new String[][]{
      {"设备名称",safe(()->Build.BRAND+" "+Build.MODEL)},
      {"制造商",safe(()->Build.MANUFACTURER)},
      {"型号",safe(()->Build.MODEL)},
      {"品牌",safe(()->Build.BRAND)},
      {"Android 版本",safe(()->"Android "+Build.VERSION.RELEASE+"（API "+Build.VERSION.SDK_INT+"）")},
      {"系统构建号",safe(()->Build.DISPLAY)},
      {"安全补丁",Build.VERSION.SDK_INT>=23?safe(()->Build.VERSION.SECURITY_PATCH):"需要 Android 6.0+"},
      {"Bootloader",safe(()->Build.BOOTLOADER)},
    }));
    body.addView(infoSection(ctx,"处理器与内存"));
    body.addView(kvCard(ctx,new String[][]{
      {"芯片",safe(()->Build.HARDWARE)},
      {"架构",safe(()->Build.SUPPORTED_ABIS!=null&&Build.SUPPORTED_ABIS.length>0?Build.SUPPORTED_ABIS[0]:"—")},
      {"核心数",safe(()->String.valueOf(Runtime.getRuntime().availableProcessors()))},
      {"Java 堆上限",safe(()->act.toolBytes(Runtime.getRuntime().maxMemory()))},
      {"内存总量",safe(()->{android.app.ActivityManager am=(android.app.ActivityManager)act.getSystemService(android.content.Context.ACTIVITY_SERVICE);android.app.ActivityManager.MemoryInfo mi=new android.app.ActivityManager.MemoryInfo();if(am==null)return"不可用";am.getMemoryInfo(mi);return act.toolBytes(mi.totalMem);})},
      {"内存可用",safe(()->{android.app.ActivityManager am=(android.app.ActivityManager)act.getSystemService(android.content.Context.ACTIVITY_SERVICE);android.app.ActivityManager.MemoryInfo mi=new android.app.ActivityManager.MemoryInfo();if(am==null)return"不可用";am.getMemoryInfo(mi);return act.toolBytes(mi.availMem)+"（"+(mi.totalMem>0?mi.availMem*100/mi.totalMem:0)+"%）";})},
      {"内存低水位",safe(()->{android.app.ActivityManager am=(android.app.ActivityManager)act.getSystemService(android.content.Context.ACTIVITY_SERVICE);android.app.ActivityManager.MemoryInfo mi=new android.app.ActivityManager.MemoryInfo();if(am==null)return"不可用";am.getMemoryInfo(mi);return mi.lowMemory?"是（系统内存紧张）":"否";})},
    }));
    body.addView(infoSection(ctx,"存储"));
    body.addView(kvCard(ctx,new String[][]{
      {"内置存储总量",safe(()->storageInfo(true))},
      {"内置存储可用",safe(()->storageInfo(false))},
      {"数据目录",safe(()->act.getApplicationInfo().dataDir)},
    }));
    body.addView(infoSection(ctx,"电池"));
    body.addView(kvCard(ctx,new String[][]{
      {"电量",safe(()->batteryInfo("level"))},
      {"状态",safe(()->batteryInfo("status"))},
      {"电源",safe(()->batteryInfo("plugged"))},
      {"温度",safe(()->batteryInfo("temperature"))},
      {"健康度",safe(()->batteryInfo("health"))},
    }));
    body.addView(infoSection(ctx,"屏幕"));
    body.addView(kvCard(ctx,new String[][]{
      {"分辨率",safe(()->{android.util.DisplayMetrics m=act.getResources().getDisplayMetrics();return m.widthPixels+" × "+m.heightPixels;})},
      {"密度",safe(()->{android.util.DisplayMetrics m=act.getResources().getDisplayMetrics();return m.densityDpi+" dpi（"+m.density+"x）";})},
      {"物理尺寸",safe(()->{android.util.DisplayMetrics m=act.getResources().getDisplayMetrics();double wIn=m.widthPixels/m.xdpi,hIn=m.heightPixels/m.ydpi;if(m.xdpi<=0||m.ydpi<=0)return"不可用";return String.format(java.util.Locale.US,"%.1f 英寸（对角线）",Math.sqrt(wIn*wIn+hIn*hIn));})},
      {"刷新率",Build.VERSION.SDK_INT>=30?safe(()->{android.view.Display display=act.getDisplay()==null?act.getWindowManager().getDefaultDisplay():act.getDisplay();return display==null?"不可用":display.getRefreshRate()+" Hz";}):safe(()->{android.view.Display display=act.getWindowManager().getDefaultDisplay();return display==null?"不可用":display.getRefreshRate()+" Hz";})},
    }));
    body.addView(infoSection(ctx,"传感器"));
    body.addView(sensorCard(ctx));
    body.addView(infoSection(ctx,"运行状态"));
    body.addView(kvCard(ctx,new String[][]{
      {"开机时长",safe(()->{long ms=android.os.SystemClock.uptimeMillis();long h=ms/3600000,m=(ms%3600000)/60000;return h+" 小时 "+m+" 分钟";})},
      {"开机时长（含休眠）",safe(()->{long ms=android.os.SystemClock.elapsedRealtime();long h=ms/3600000,m=(ms%3600000)/60000;return h+" 小时 "+m+" 分钟";})},
      {"应用版本",safe(()->{try{return act.getPackageManager().getPackageInfo(act.getPackageName(),0).versionName;}catch(Exception e){return"不可用";}})},
      {"目标 SDK",safe(()->String.valueOf(act.getApplicationInfo().targetSdkVersion))},
    }));
    LinearLayout actions=actionRow(body);
    action(actions,"复制全部信息",()->{StringBuilder all=new StringBuilder();for(int i=0;i<body.getChildCount();i++){View child=body.getChildAt(i);Object tag=child.getTag();if(tag instanceof String[][])for(String[] row:(String[][])tag)all.append(row[0]).append("：").append(row[1]).append('\n');}if(all.length()==0){act.showNotice("没有可复制的信息",true);return;}copy(all.toString());});
  }
  /** 每项读取都兜异常：任一 API 在该机型上缺失时显示"不可用"，绝不崩整页 */
  String safe(java.util.function.Supplier<String> read){try{String value=read.get();return value==null||value.trim().isEmpty()?"不可用":value;}catch(Throwable ignored){return"不可用";}}
  String storageInfo(boolean total){
    try{
      java.io.File dir=android.os.Environment.getDataDirectory();
      long space=total?dir.getTotalSpace():dir.getUsableSpace();
      return act.toolBytes(space);
    }catch(Throwable ignored){return"不可用";}
  }
  String batteryInfo(String field){
    android.content.Intent intent=act.registerReceiver(null,new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
    if(intent==null)return"不可用";
    switch(field){
      case "level":{int level=intent.getIntExtra("level",-1),scale=intent.getIntExtra("scale",-1);return level<0||scale<=0?"不可用":level*100/scale+"%";}
      case "status":{int status=intent.getIntExtra("status",-1);switch(status){case 2:return"充电中";case 3:return"放电中";case 4:return"未充电";case 5:return"已充满";default:return"未知";}}
      case "plugged":{int plugged=intent.getIntExtra("plugged",0);switch(plugged){case 1:return"交流充电器";case 2:return"USB";case 4:return"无线充电";default:return"电池供电";}}
      case "temperature":{int temperature=intent.getIntExtra("temperature",-1);return temperature<0?"不可用":temperature/10.0+"℃";}
      case "health":{int health=intent.getIntExtra("health",-1);switch(health){case 2:return"良好";case 3:return"过热";case 4:return"已损坏";case 5:return"电压过高";case 7:return"健康（冷）";default:return"未知";}}
      default:return"不可用";
    }
  }
  View sensorCard(android.content.Context context){
    android.hardware.SensorManager manager=(android.hardware.SensorManager)act.getSystemService(android.content.Context.SENSOR_SERVICE);
    LinearLayout card=kvCardShell(ctx);
    if(manager==null){addKvRow(ctx,card,"传感器","此设备没有传感器服务");return card;}
    java.util.List<android.hardware.Sensor> sensors=manager.getSensorList(android.hardware.Sensor.TYPE_ALL);
    if(sensors.isEmpty()){addKvRow(ctx,card,"传感器","未检测到传感器");return card;}
    java.util.Set<String> seen=new java.util.LinkedHashSet<>();
    java.util.Map<String,String> names=new java.util.HashMap<>();
    names.put(android.hardware.Sensor.STRING_TYPE_ACCELEROMETER,"加速度计");names.put(android.hardware.Sensor.STRING_TYPE_MAGNETIC_FIELD,"磁力计");names.put(android.hardware.Sensor.STRING_TYPE_GYROSCOPE,"陀螺仪");
    names.put(android.hardware.Sensor.STRING_TYPE_LIGHT,"光线传感器");names.put(android.hardware.Sensor.STRING_TYPE_PRESSURE,"气压计");names.put(android.hardware.Sensor.STRING_TYPE_PROXIMITY,"距离传感器");
    names.put(android.hardware.Sensor.STRING_TYPE_GRAVITY,"重力传感器");names.put(android.hardware.Sensor.STRING_TYPE_LINEAR_ACCELERATION,"线性加速度");names.put(android.hardware.Sensor.STRING_TYPE_ROTATION_VECTOR,"旋转矢量");
    names.put(android.hardware.Sensor.STRING_TYPE_AMBIENT_TEMPERATURE,"环境温度");names.put(android.hardware.Sensor.STRING_TYPE_RELATIVE_HUMIDITY,"湿度");names.put(android.hardware.Sensor.STRING_TYPE_STEP_COUNTER,"计步器");
    for(android.hardware.Sensor sensor:sensors){
      String key=sensor.getStringType()==null?sensor.getName():sensor.getStringType();
      String label=names.getOrDefault(key,key);
      if(seen.add(label))addKvRow(ctx,card,label,sensor.getVendor()+" · "+sensor.getMaximumRange());
    }
    return card;
  }
  String collectDeviceInfo(){return"";}// 已由 deviceinfo 分组卡片取代（保留空壳防外部引用）

  void torch(LinearLayout body){
    TextView state=text("未开启",14,act.TEXT());state.setPadding(0,act.dp(6),0,act.dp(6));
    LinearLayout actions=actionRow(body);
    action(actions,"开灯",()->{if(act.startTorch())state.setText("手电筒已开启\n返回或离开工具页自动关闭");});
    action(actions,"关灯",()->{act.stopTorch();state.setText("已关闭");});
    body.addView(state,new LinearLayout.LayoutParams(-1,-2));
    result(body);
  }

  void noise(LinearLayout body){
    TextView state=text("棕噪音：低频噪声，适合助眠与专注。播放中可退到后台。",12,act.MUTED());state.setPadding(0,0,0,act.dp(10));
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

  LinearLayout checkRow(LinearLayout parent){LinearLayout row=new LinearLayout(ctx);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,act.dp(8),0,0);parent.addView(row,new LinearLayout.LayoutParams(-1,act.dp(44)));return row;}
  CheckBox checkInline(LinearLayout parent,String label,boolean checked){
    CheckBox box=new CheckBox(ctx);box.setText(label);box.setTextColor(act.TEXT());box.setTextSize(12);box.setChecked(checked);box.setPadding(act.dp(4),0,act.dp(4),0);
    parent.addView(box,new LinearLayout.LayoutParams(-2,-2));return box;
  }
  LinearLayout listCard(){LinearLayout card=new LinearLayout(ctx);card.setOrientation(LinearLayout.VERTICAL);GradientDrawable bg=solid(act.SURFACE());bg.setStroke(act.dp(1),act.DIV());card.setBackground(bg);card.setClipToOutline(true);card.setPadding(act.dp(4),act.dp(4),act.dp(4),act.dp(6));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,act.dp(10));card.setLayoutParams(params);return card;}
  LinearLayout chipRow(LinearLayout parent){LinearLayout row=new LinearLayout(ctx);row.setOrientation(LinearLayout.HORIZONTAL);parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;}
  TextView selectChip(LinearLayout row,String label,boolean selected,Runnable click){
    TextView chip=text(label,12,selected?act.PRIMARY():act.TEXT());chip.setGravity(Gravity.CENTER);chip.setClickable(true);chip.setFocusable(true);chip.setSelected(selected);
    styleSelect(chip,selected);chip.setOnClickListener(v->press(v,click));row.addView(chip,chipMargin());return chip;
  }
  void styleSelect(TextView chip,boolean selected){
    chip.setTextColor(selected?act.PRIMARY():act.TEXT());chip.setSelected(selected);
    GradientDrawable bg=solid(selected?ThemeEngine.selectedFill(ctx):act.SURFACE());bg.setStroke(act.dp(1),selected?act.PRIMARY():act.DIV());
    chip.setBackground(ripple(bg));
  }
  LinearLayout.LayoutParams chipMargin(){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,act.dp(44));params.setMargins(0,0,act.dp(8),act.dp(8));return params;}
  EditText input(LinearLayout parent,String hint,int minDp){
    EditText field=new EditText(ctx);field.setHint(hint);field.setHintTextColor(act.MUTED());field.setTextColor(act.TEXT());field.setTextSize(14);
    field.setGravity(Gravity.TOP|Gravity.START);field.setBackground(solid(act.SURFACE()));field.setPadding(act.dp(12),act.dp(10),act.dp(12),act.dp(10));field.setMinHeight(act.dp(minDp));
    field.setOnFocusChangeListener((v,hasFocus)->{GradientDrawable bg=solid(act.SURFACE());bg.setStroke(act.dp(hasFocus?2:1),hasFocus?act.PRIMARY():act.BORDER());field.setBackground(bg);});
    field.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE);
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,act.dp(minDp));params.setMargins(0,0,0,act.dp(8));
    parent.addView(field,params);return field;
  }
  LinearLayout actionRow(LinearLayout parent){LinearLayout row=new LinearLayout(ctx);row.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,act.dp(10));parent.addView(row,params);return row;}
  void action(LinearLayout row,String label,Runnable click){
    Button button=new Button(ctx);button.setText(label);button.setTextColor(act.PRIMARY());button.setTextSize(12);button.setAllCaps(false);
    button.setBackground(ripple(solid(act.SURFACE())));button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(act.dp(44));button.setPadding(act.dp(14),0,act.dp(14),0);
    button.setOnClickListener(v->press(v,click));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,act.dp(44));params.setMargins(0,0,act.dp(8),0);
    row.addView(button,params);
  }
  TextView result(LinearLayout parent){
    LinearLayout box=new LinearLayout(ctx);box.setOrientation(LinearLayout.VERTICAL);box.setVisibility(View.GONE);box.setTag("tool-result-box");parent.addView(box,new LinearLayout.LayoutParams(-1,-2));
    TextView label=text("结果",11,act.MUTED());label.setPadding(0,act.dp(4),0,act.dp(2));box.addView(label,new LinearLayout.LayoutParams(-1,-2));
    android.widget.FrameLayout wrap=new android.widget.FrameLayout(ctx);
    TextView output=new TextView(ctx);output.setTextColor(act.TEXT());output.setTextSize(13);output.setTextIsSelectable(true);output.setLineSpacing(act.dp(2),1f);
    output.setBackground(solid(act.SURFACE()));output.setPadding(act.dp(12),act.dp(10),act.dp(56),act.dp(10));output.setMinHeight(act.dp(44));
    wrap.addView(output,new android.widget.FrameLayout.LayoutParams(-1,-2));
    TextView copyBtn=text("复制",11,act.PRIMARY());copyBtn.setGravity(Gravity.CENTER);copyBtn.setClickable(true);copyBtn.setFocusable(true);copyBtn.setPadding(act.dp(12),0,act.dp(12),0);
    copyBtn.setBackground(ripple(solid(act.SURFACE())));
    copyBtn.setOnClickListener(v->{String value=output.getText().toString();if(value.isEmpty())return;copy(value);act.showNotice("已复制",false);});
    android.widget.FrameLayout.LayoutParams cp=new android.widget.FrameLayout.LayoutParams(-2,act.dp(44),Gravity.END|Gravity.TOP);
    wrap.addView(copyBtn,cp);
    box.addView(wrap,new LinearLayout.LayoutParams(-1,-2));
    output.setTag("tool-output");return output;
  }
  /** 主操作按钮：实心品牌色；次级操作用 action()（v1.2.3 工具页主次分明） */
  void primaryAction(LinearLayout row,String label,Runnable click){
    Button button=new Button(ctx);button.setText(label);button.setTextColor(act.BG());button.setTextSize(12);button.setAllCaps(false);
    button.setBackground(ripple(solid(act.PRIMARY())));button.setMinWidth(0);button.setMinimumWidth(0);button.setMinHeight(act.dp(44));button.setPadding(act.dp(16),0,act.dp(16),0);
    button.setOnClickListener(v->press(v,click));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,act.dp(44));params.setMargins(0,0,act.dp(8),0);
    row.addView(button,params);
  }
  View gap(int heightDp){View v=new View(ctx);v.setLayoutParams(new LinearLayout.LayoutParams(-1,heightDp));return v;}
  void output(LinearLayout parent,String value){if(parent==null)return;View found=parent.findViewWithTag("tool-output");if(found instanceof TextView){((TextView)found).setText(value);if(act.motionEnabled()){found.setAlpha(.4f);found.animate().alpha(1f).setDuration(150).start();}}View box=parent.findViewWithTag("tool-result-box");if(box!=null)box.setVisibility(View.VISIBLE);}
  void copy(String value){android.content.ClipboardManager clipboard=(android.content.ClipboardManager)act.getSystemService(android.content.Context.CLIPBOARD_SERVICE);if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("东方无限工具结果",value));act.showNotice("已复制",false);}
  TextView text(String s,int sp,int color){TextView v=new TextView(ctx);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
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

  //—— 设备信息页部件：小节标题 + 键值卡片 ——

  TextView infoSection(android.content.Context context,String title){
    TextView label=text(title,12,act.PRIMARY());label.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);
    label.setPadding(act.dp(4),act.dp(8),0,act.dp(4));label.setContentDescription(title+"信息组");
    return label;
  }
  LinearLayout kvCardShell(android.content.Context context){
    LinearLayout card=new LinearLayout(ctx);card.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable bg=solid(act.SURFACE());bg.setStroke(act.dp(1),act.DIV());card.setBackground(bg);card.setClipToOutline(true);
    card.setPadding(act.dp(4),act.dp(2),act.dp(4),act.dp(2));
    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,act.dp(12));
    card.setLayoutParams(params);return card;
  }
  /** 行数据卡片：键值对两列布局，偶数行淡底色；tag 存数据供"复制全部"用 */
  LinearLayout kvCard(android.content.Context context,String[][] rows){
    LinearLayout card=kvCardShell(context);
    java.util.List<String[]> kept=new ArrayList<>();
    for(int i=0;i<rows.length;i++){
      if(rows[i].length<2)continue;
      addKvRow(ctx,card,rows[i][0],rows[i][1]);
      kept.add(new String[]{rows[i][0],rows[i][1]});
    }
    card.setTag(kept.toArray(new String[0][]));
    return card;
  }
  void addKvRow(android.content.Context context,LinearLayout card,String key,String value){
    LinearLayout row=new LinearLayout(ctx);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(act.dp(10),act.dp(9),act.dp(10),act.dp(9));
    if(card.getChildCount()%2==2){GradientDrawable stripe=solid(ThemeEngine.tint(act.PRIMARY(),10));row.setBackground(stripe);}
    TextView k=text(key,12,act.MUTED());k.setSingleLine(true);
    row.addView(k,new LinearLayout.LayoutParams(0,-2,1));
    TextView v=text(value==null||value.isEmpty()?"—":value,12,act.TEXT());v.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);v.setTextIsSelectable(false);
    boolean longValue=value!=null&&(value.contains("\n")||value.length()>28);
    if(longValue){v.setGravity(Gravity.START);row.setOrientation(LinearLayout.VERTICAL);k.setPadding(0,0,0,act.dp(2));row.addView(v,new LinearLayout.LayoutParams(-1,-2));}
    else{v.setSingleLine(true);v.setEllipsize(TextUtils.TruncateAt.MIDDLE);row.addView(v,new LinearLayout.LayoutParams(0,-2,1));}
    row.setContentDescription(key+"："+v.getText());
    card.addView(row,new LinearLayout.LayoutParams(-1,-2));
  }
  //——— v1.2.2 新增工具 ———

  void stopwatch(LinearLayout body){
    final android.os.Handler handler=new android.os.Handler(android.os.Looper.getMainLooper());
    TextView clock=text("00:00.0",32,act.TEXT());clock.setTypeface(android.graphics.Typeface.DEFAULT,android.graphics.Typeface.BOLD);clock.setGravity(Gravity.CENTER);
    GradientDrawable clockBg=solid(act.SURFACE());clockBg.setStroke(act.dp(1),act.DIV());clock.setBackground(clockBg);
    clock.setPadding(act.dp(12),act.dp(24),act.dp(12),act.dp(24));
    body.addView(clock,new LinearLayout.LayoutParams(-1,-2));
    body.addView(gap(act.dp(12)));
    final EditText mins=input(body,"倒计时分钟（可留空）",44);mins.setInputType(InputType.TYPE_CLASS_NUMBER);
    body.addView(gap(act.dp(4)));
    LinearLayout actions=actionRow(body);result(body);
    final long[] accum={0},startAt={0},cdEnd={0};final boolean[] running={false},cdMode={false};
    final Runnable[] tick={null};
    tick[0]=new Runnable(){public void run(){
      if(cdMode[0]){
        long shown=Math.max(0,cdEnd[0]-System.currentTimeMillis());long s=shown/1000;
        clock.setText(String.format(java.util.Locale.US,"%02d:%02d",s/60,s%60));
        if(shown<=0){running[0]=false;cdMode[0]=false;act.showNotice("时间到",true);return;}
        if(running[0])handler.postDelayed(tick[0],100);
        return;
      }
      long shown=accum[0]+(running[0]?System.currentTimeMillis()-startAt[0]:0);long t=shown/100;
      clock.setText(String.format(java.util.Locale.US,"%02d:%02d.%d",t/600,(t/1000)%60,t%10));
      if(running[0])handler.postDelayed(tick[0],100);
    }};
    primaryAction(actions,"开始/暂停",()->{
      if(cdMode[0]){if(cdEnd[0]-System.currentTimeMillis()<=0){act.showNotice("已结束，请清零",true);return;}running[0]=!running[0];if(running[0])handler.post(tick[0]);return;}
      if(running[0]){accum[0]+=System.currentTimeMillis()-startAt[0];running[0]=false;}
      else{startAt[0]=System.currentTimeMillis();running[0]=true;handler.post(tick[0]);}
    });
    action(actions,"计圈",()->{long shown=accum[0]+(running[0]?System.currentTimeMillis()-startAt[0]:0);output(body,"圈："+String.format(java.util.Locale.US,"%.1f 秒",shown/1000.0));});
    action(actions,"清零",()->{running[0]=false;cdMode[0]=false;accum[0]=0;clock.setText("00:00.0");});
    action(actions,"倒计时",()->{try{long ms=Long.parseLong(mins.getText().toString().trim())*60000;if(ms<=0)throw new NumberFormatException();cdMode[0]=true;running[0]=true;cdEnd[0]=System.currentTimeMillis()+ms;handler.post(tick[0]);}catch(Exception e){act.showNotice("先填倒计时分钟数",true);}});
  }
  void timestamp(LinearLayout body){
    // v1.12.0 工具精修17：自动判别方向实时转换（纯数字=时间戳，否则=日期）；ISO 8601+星期
    TextView hint=text("粘贴即自动转换：纯数字按时间戳（秒/毫秒自动判别），否则按日期。",12,act.MUTED());hint.setPadding(0,0,0,act.dp(8));body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    final EditText field=input(body,"时间戳或日期",44);
    TextView live=text("",13,act.TEXT());live.setTypeface(android.graphics.Typeface.MONOSPACE);live.setLineSpacing(act.dp(2),1f);live.setTextIsSelectable(true);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=act.dp(8);body.addView(live,lp);
    LinearLayout actions=actionRow(body);
    action(actions,"当前时间戳",()->output(body,Toolbox.timestampNow()));
    Runnable recalc=()->{
      String v=field.getText().toString();
      live.setText(v.trim().isEmpty()?"":Toolbox.timestampAuto(v));
    };
    field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int b,int c){}public void onTextChanged(CharSequence s,int a,int b,int c){}public void afterTextChanged(android.text.Editable s){recalc.run();}});
    recalc.run();
    result(body);
  }
  void radix(LinearLayout body){
    final EditText value=input(body,"数值（支持负号与 0x/0b 前缀）",44);
    LinearLayout actions=actionRow(body);result(body);
    action(actions,"按十进制解析",()->output(body,Toolbox.radixConvert(value.getText().toString().trim(),10)));
    action(actions,"按十六进制解析",()->output(body,Toolbox.radixConvert(value.getText().toString().trim(),16)));
    action(actions,"按二进制解析",()->output(body,Toolbox.radixConvert(value.getText().toString().trim(),2)));
    action(actions,"按三十六进制解析",()->output(body,Toolbox.radixConvert(value.getText().toString().trim(),36)));// v1.12.0 精修18：短链常见 base36
  }
  void compass(LinearLayout body){
    TextView dial=text("…",44,act.PRIMARY());dial.setGravity(Gravity.CENTER);dial.setBackground(solid(act.SURFACE()));body.addView(dial,new LinearLayout.LayoutParams(-1,act.dp(150)));
    TextView degree=text("",13,act.TEXT());degree.setGravity(Gravity.CENTER);body.addView(degree,new LinearLayout.LayoutParams(-1,act.dp(30)));
    TextView note=text("电子设备/磁场附近可能不准。",11,act.MUTED());body.addView(note,new LinearLayout.LayoutParams(-1,act.dp(24)));
    android.hardware.SensorManager sm=(android.hardware.SensorManager)act.getSystemService(android.content.Context.SENSOR_SERVICE);
    android.hardware.Sensor sensor=sm==null?null:sm.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR);
    if(sensor==null&&sm!=null)sensor=sm.getDefaultSensor(android.hardware.Sensor.TYPE_ORIENTATION);
    if(sm==null||sensor==null){dial.setText("此设备不支持方向传感器");return;}
    final float[] rot=new float[9],ori=new float[3];
    final String[] dirs={"北","东北","东","东南","南","西南","西","西北"};
    android.hardware.SensorEventListener listener=new android.hardware.SensorEventListener(){
      public void onSensorChanged(android.hardware.SensorEvent event){
        int deg;
        if(event.sensor.getType()==android.hardware.Sensor.TYPE_ROTATION_VECTOR){
          android.hardware.SensorManager.getRotationMatrixFromVector(rot,event.values);
          android.hardware.SensorManager.getOrientation(rot,ori);
          deg=(int)Math.round(Math.toDegrees(ori[0]));
        }else deg=(int)event.values[0];
        deg=(deg%360+360)%360;
        dial.setText(dirs[Math.round(deg/45f)%8]);
        degree.setText(deg+"°");
      }
      public void onAccuracyChanged(android.hardware.Sensor s,int a){}
    };
    sm.registerListener(listener,sensor,android.hardware.SensorManager.SENSOR_DELAY_UI);
    if(act.levelCleanup()!=null){try{act.levelCleanup().run();}catch(Exception ignored){}}
    act.setLevelCleanup(()->sm.unregisterListener(listener));// 独立清理槽（同水平仪），离开工具页自动注销
  }
  void freqgen(LinearLayout body){
    TextView status=text("未播放",14,act.TEXT());status.setPadding(0,act.dp(8),0,act.dp(8));body.addView(status,new LinearLayout.LayoutParams(-1,act.dp(32)));
    final EditText hz=input(body,"频率 Hz（20–20000）",44);hz.setInputType(InputType.TYPE_CLASS_NUMBER);hz.setText("440");
    LinearLayout actions=actionRow(body);result(body);
    final android.media.AudioTrack[] track={null};
    action(actions,"播放",()->{
      try{
        int f=Math.max(20,Math.min(20000,parseInt(hz,440)));
        if(track[0]!=null){try{track[0].stop();track[0].release();}catch(Exception ignored){}track[0]=null;}
        int rate=44100,n=rate;
        short[] wave=new short[n];
        for(int i=0;i<n;i++)wave[i]=(short)(Math.sin(2*Math.PI*f*i/rate)*8000);
        android.media.AudioTrack t=new android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC,rate,android.media.AudioFormat.CHANNEL_OUT_MONO,android.media.AudioFormat.ENCODING_PCM_16BIT,n,android.media.AudioTrack.MODE_STATIC);
        t.write(wave,0,n);t.setLoopPoints(0,n,-1);t.play();track[0]=t;
        status.setText("正在播放 "+f+" Hz（注意音量）");
      }catch(Exception e){act.showNotice("播放失败："+e.getMessage(),true);}
    });
    action(actions,"停止",()->{if(track[0]!=null){try{track[0].stop();track[0].release();}catch(Exception ignored){}track[0]=null;}status.setText("已停止");});
    if(act.levelCleanup()!=null){try{act.levelCleanup().run();}catch(Exception ignored){}}
    act.setLevelCleanup(()->{if(track[0]!=null){try{track[0].stop();track[0].release();}catch(Exception ignored){}track[0]=null;}});// 离开工具页自动停止
  }
  void picker(LinearLayout body){
    final EditText names=input(body,"名单（换行或逗号分隔）",120);
    final EditText count=input(body,"抽几项（默认 1）",44);count.setInputType(InputType.TYPE_CLASS_NUMBER);
    LinearLayout actions=actionRow(body);primaryAction(actions,"抽取",()->output(body,Toolbox.pickFrom(names.getText().toString(),parseInt(count,1))));result(body);
  }
  void morse(LinearLayout body){
    // v1.12.0 工具精修19：ITU 全表（字母+数字+标点），·—容错，未知电码计数
    final EditText field=input(body,"英文/数字/标点 或 摩斯电码（. - · — 与 /）",100);
    LinearLayout actions=actionRow(body);result(body);
    action(actions,"编码为摩斯",()->output(body,Toolbox.morseConvert(true,field.getText().toString())));
    action(actions,"解码为英文",()->output(body,Toolbox.morseConvert(false,field.getText().toString())));
  }
  interface Host {
    int dp(int v);
    android.content.Context context();
    int BG();int TEXT();int MUTED();int SURFACE();int PRIMARY();int DIV();
    int BORDER();int SURFACE2();int SECONDARY();int PRIMARY_HI();int PRIMARY_LO();int ERROR_TOKEN();
    boolean motionEnabled();
    String toolBytes(long value);
    LinearLayout root();
    void showNotice(String message,boolean longLived);
    void openTool(String id);void popToolBack();void startScreenTest();
    boolean startTorch();void stopTorch();void startBrownNoise();void stopBrownNoise();void speakTts(String value);void stopTts();
    void toolHostSketch(LinearLayout body);void toolHostRuler(LinearLayout body);void toolHostLevel(LinearLayout body);
    void pickToolImage();void runImageCompressPending();
    Runnable levelCleanup();void setLevelCleanup(Runnable value);
    int pageDirection();void setPageDirection(int value);
    int toolQuality();void setToolQuality(int value);
    android.net.Uri toolImageUri();void setToolImageUri(android.net.Uri value);
    String toolImageInfoText();void setToolImageInfoText(String value);
    ImageButton iconButton(int icon,String description);
    GradientDrawable solidShape(int color,int radius);
    android.graphics.drawable.Drawable filterRipple(android.graphics.drawable.Drawable content);
    void animateSection(LinearLayout section,LinearLayout content,ImageView arrow,boolean open);
    TextView primaryHeader(String title);
    Object getSystemService(String name);
    android.content.res.Resources getResources();
    android.view.WindowManager getWindowManager();
    android.view.Display getDisplay();
    android.content.pm.ApplicationInfo getApplicationInfo();
    android.content.pm.PackageManager getPackageManager();
    String getPackageName();
    android.content.Intent registerReceiver(android.content.BroadcastReceiver receiver,android.content.IntentFilter filter);
  }
}
