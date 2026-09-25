package cc.nkbr.lanzouplus;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * v1.21.1 图标试衣间（美学方向 A 选型工具，01-图标库.md 待办）：同一界面在 5 套图标间切换——
 * 当前 / Material Symbols(filled) / Lucide / Tabler / Phosphor，底部导航+常用操作+列表场景三段同布局对比。
 * 资产 fit_{ms,lu,tb,ph}_{concept} 仅经 getIdentifier 动态引用（res/raw/keep.xml 已 keep）；
 * 用户拍板后按选中库全站替换 ic_*，本页与落选 fit_* 资产一并移除。仅 adb 调试入口，非用户功能。
 */
public class IconFittingRoom extends Activity {
  static final String[] SET_NAMES={"当前","Material Symbols","Lucide","Tabler","Phosphor"};
  static final String[] SET_PREFIX={"","ms","lu","tb","ph"};
  static final String[] KEYS={"home","ai","download","tools","settings","search","back","folder","play","share","star","trash","edit","copy","link","history","more","refresh"};
  static final String[] LABELS={"软件库","AI 对话","下载","工具箱","设置","搜索","返回","文件夹","播放","分享","收藏","删除","编辑","复制","链接","历史","更多","刷新"};
  /** 当前库 18 概念对应的既有 ic_*（无直译的取最近语义：link=打开外链,history=日历,more=展开） */
  static final String[] CURRENT={"ic_home","ic_ai","ic_download","ic_tools","ic_settings","ic_search","ic_back","ic_folder","ic_play","ic_share","ic_tool_star","ic_delete_record","ic_edit","ic_copy","ic_open_with","ic_tool_calendar","ic_expand","ic_refresh"};
  int set;
  int BG,SURFACE,BORDER,PRIMARY,TEXT,MUTED;
  LinearLayout body; TextView header; TextView[] chips;

  @Override protected void onCreate(Bundle b){
    super.onCreate(b);
    set=getIntent().getIntExtra("fit_set",0);
    ThemeEngine.Design d=ThemeEngine.active(this);
    BG=d.bg;SURFACE=d.surface;BORDER=d.border;PRIMARY=d.primary;TEXT=d.text;MUTED=d.muted;
    getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);

    LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BG);
    int pad=dp(14);root.setPadding(pad,pad,pad,dp(8));

    header=text("",16,TEXT,true);
    root.addView(header,new LinearLayout.LayoutParams(-1,-2));

    LinearLayout chipRow=new LinearLayout(this);
    chips=new TextView[SET_NAMES.length];
    for(int i=0;i<SET_NAMES.length;i++){
      TextView c=text(SET_NAMES[i],11,MUTED,false);c.setGravity(Gravity.CENTER);c.setAllCaps(false);
      GradientDrawable g=new GradientDrawable();g.setColor(SURFACE);g.setCornerRadius(dp(16));
      c.setBackground(g);c.setPadding(dp(4),dp(7),dp(4),dp(7));
      final int idx=i;c.setOnClickListener(v->{set=idx;render();});
      chips[i]=c;
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
      lp.leftMargin=i==0?0:dp(6);
      chipRow.addView(c,lp);
    }
    root.addView(chipRow,new LinearLayout.LayoutParams(-1,-2));

    ScrollView sc=new ScrollView(this);sc.setFillViewport(true);
    body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(2),0,dp(20));
    sc.addView(body,new ScrollView.LayoutParams(-1,-2));
    root.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
    setContentView(root);
    render();
  }

  void render(){
    header.setText("图标试衣间 · "+SET_NAMES[set]);
    for(int i=0;i<chips.length;i++){
      GradientDrawable g=new GradientDrawable();
      g.setColor(i==set?ThemeEngine.tint(PRIMARY,40):SURFACE);g.setCornerRadius(dp(16));
      chips[i].setBackground(g);chips[i].setTextColor(i==set?PRIMARY:MUTED);
    }
    body.removeAllViews();
    body.addView(sectionLabel("底部导航"));
    body.addView(navReplica(),ml(0,dp(4),0,0));
    body.addView(sectionLabel("常用操作 18 项"));
    body.addView(opGrid(),ml(0,dp(4),0,0));
    body.addView(sectionLabel("列表场景"));
    body.addView(listRow("folder","视频音乐合集","文件夹 · 12 项","more"),ml(0,dp(4),0,0));
    body.addView(listRow("play","云帧享_2.8.0_纯净版.apk","86.2 MB · 下载完成","share"),ml(0,dp(8),0,0));
    body.addView(searchBar(),ml(0,dp(8),0,0));
  }

  /** 复刻 makePrimaryNav 选中态：第一项 primary+胶囊底，其余 muted */
  View navReplica(){
    LinearLayout nav=new LinearLayout(this);nav.setOrientation(LinearLayout.HORIZONTAL);nav.setGravity(Gravity.CENTER);
    nav.setPadding(dp(6),dp(5),dp(6),dp(6));
    GradientDrawable g=new GradientDrawable();g.setColor(SURFACE);g.setCornerRadius(dp(20));nav.setBackground(g);
    for(int i=0;i<5;i++){
      LinearLayout it=new LinearLayout(this);it.setOrientation(LinearLayout.VERTICAL);it.setGravity(Gravity.CENTER);
      FrameLayout pill=new FrameLayout(this);
      GradientDrawable pg=new GradientDrawable();
      pg.setColor(i==0?ThemeEngine.tint(PRIMARY,30):Color.TRANSPARENT);pg.setCornerRadius(dp(14));
      pill.setBackground(pg);
      ImageView iv=icon(resFor(KEYS[i]),i==0?PRIMARY:MUTED);
      pill.addView(iv,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
      it.addView(pill,new LinearLayout.LayoutParams(dp(46),dp(28)));
      TextView lb=text(LABELS[i],10,i==0?PRIMARY:MUTED,false);lb.setGravity(Gravity.CENTER);
      it.addView(lb,new LinearLayout.LayoutParams(-2,-2));
      nav.addView(it,new LinearLayout.LayoutParams(0,-2,1));
    }
    return nav;
  }

  /** 18 概念 × 6 列三行，行内 weight 均分（GridLayout 权重繁琐，直接 LinearLayout 行） */
  View opGrid(){
    LinearLayout grid=new LinearLayout(this);grid.setOrientation(LinearLayout.VERTICAL);
    for(int r=0;r<3;r++){
      LinearLayout row=new LinearLayout(this);
      for(int c=0;c<6;c++){
        int i=r*6+c;
        LinearLayout cell=new LinearLayout(this);cell.setOrientation(LinearLayout.VERTICAL);cell.setGravity(Gravity.CENTER);
        ImageView iv=icon(resFor(KEYS[i]),TEXT);
        cell.addView(iv,new LinearLayout.LayoutParams(dp(24),dp(24)));
        TextView lb=text(LABELS[i],10,MUTED,false);lb.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(5);
        cell.addView(lb,lp);
        row.addView(cell,new LinearLayout.LayoutParams(0,-2,1));
      }
      LinearLayout.LayoutParams rlp=new LinearLayout.LayoutParams(-1,-2);
      if(r>0)rlp.topMargin=dp(10);
      grid.addView(row,rlp);
    }
    return grid;
  }

  View listRow(String leadKey,String title,String sub,String trailKey){
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(dp(12),dp(10),dp(12),dp(10));
    GradientDrawable g=new GradientDrawable();g.setColor(SURFACE);g.setCornerRadius(dp(14));row.setBackground(g);
    row.addView(icon(resFor(leadKey),PRIMARY),new LinearLayout.LayoutParams(dp(24),dp(24)));
    LinearLayout col=new LinearLayout(this);col.setOrientation(LinearLayout.VERTICAL);
    col.addView(text(title,14,TEXT,true));
    col.addView(text(sub,11,MUTED,false));
    LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(0,-2,1);clp.leftMargin=dp(10);
    row.addView(col,clp);
    row.addView(icon(resFor(trailKey),MUTED),new LinearLayout.LayoutParams(dp(20),dp(20)));
    return row;
  }

  View searchBar(){
    LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);
    bar.setPadding(dp(14),0,dp(14),0);
    GradientDrawable g=new GradientDrawable();g.setColor(SURFACE);g.setCornerRadius(dp(21));bar.setBackground(g);
    bar.addView(icon(resFor("search"),MUTED),new LinearLayout.LayoutParams(dp(20),dp(20)));
    TextView t=text("搜索资源、文件夹…",13,MUTED,false);t.setPadding(dp(10),0,0,0);
    bar.addView(t,new LinearLayout.LayoutParams(0,-2,1));
    return bar;
  }

  View sectionLabel(String s){
    TextView t=text(s,12,MUTED,false);t.setTypeface(Typeface.DEFAULT_BOLD);
    return t;
  }

  int resFor(String key){
    if(set==0)return resId(CURRENT[index(KEYS,key)]);
    return resId("fit_"+SET_PREFIX[set]+"_"+key);
  }

  int resId(String name){
    if(name==null||name.isEmpty())return 0;
    return getResources().getIdentifier(name,"drawable",getPackageName());
  }

  static int index(String[] arr,String v){for(int i=0;i<arr.length;i++)if(arr[i].equals(v))return i;return 0;}

  ImageView icon(int res,int color){
    ImageView iv=new ImageView(this);
    if(res!=0){iv.setImageResource(res);iv.setColorFilter(color);}
    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
    return iv;
  }

  TextView text(String s,int sp,int color,boolean bold){
    TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);
    if(bold)t.setTypeface(Typeface.DEFAULT_BOLD);
    return t;
  }

  int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
  LinearLayout.LayoutParams ml(int l,int t,int r,int b){
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);
    lp.setMargins(l,t,r,b);return lp;
  }
}
