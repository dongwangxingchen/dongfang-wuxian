package cc.nkbr.lanzouplus;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.util.LruCache;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.view.animation.PathInterpolator;
import android.animation.ValueAnimator;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import android.widget.*;
import java.io.*;
import java.net.URL;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends androidx.activity.ComponentActivity implements ToolHost.Host {
  // v1.19.9 整包默认简体中文：vendor（RikkaHub Compose）资源跟随系统语言，Activity 的 base
  // 必须与 App 一样包装，否则 AI 页在英文系统下仍显示英文（主 app 硬编码中文不受影响）
  @Override protected void attachBaseContext(android.content.Context base){super.attachBaseContext(App.wrapSimplifiedChinese(base));}
  @Override public int BG(){return BG;}@Override public int TEXT(){return TEXT;}@Override public int MUTED(){return MUTED;}@Override public int SURFACE(){return SURFACE;}@Override public int PRIMARY(){return PRIMARY;}@Override public int DIV(){return DIV;}
  @Override public int BORDER(){return BORDER;}@Override public int SURFACE2(){return SURFACE2;}@Override public int SECONDARY(){return SECONDARY;}@Override public int PRIMARY_HI(){return PRIMARY_HI;}@Override public int PRIMARY_LO(){return PRIMARY_LO;}@Override public int ERROR_TOKEN(){return ERROR_TOKEN;}
  @Override public LinearLayout root(){return root;}@Override public android.content.Context context(){return this;}
  /** v1.22.0 悬浮导航球宿主：球挂 host 层（最外层 FrameLayout），天然跨越全部页面含 AI 页，零权限 */
  void installNavBall(){if(host==null)return;if(navBall==null)navBall=new NavBall(new NavBall.Host(){
    @Override public int dp(int v){return MainActivity.this.dp(v);}
    @Override public android.content.Context context(){return MainActivity.this;}
    @Override public int BG(){return BG;}@Override public int SURFACE2(){return SURFACE2;}@Override public int PRIMARY(){return PRIMARY;}@Override public int PRIMARY_HI(){return PRIMARY_HI;}
    @Override public int PRIMARY_LO(){return PRIMARY_LO;}@Override public int TEXT(){return TEXT;}@Override public int MUTED(){return MUTED;}@Override public int BORDER(){return BORDER;}@Override public int ERROR(){return ERROR_TOKEN;}
    @Override public boolean motionEnabled(){return MainActivity.this.motionEnabled();}
    @Override public void goToDestination(int destination){MainActivity.this.goToDestination(destination);}
    @Override public int currentDestination(){return primaryDestination<=1?0:primaryDestination;}
    @Override public int contentWidth(){return safeContentWidth();}
    @Override public int contentHeight(){return safeContentHeight();}
    @Override public boolean isAiPage(){return pageKind==5;}
    @Override public void openNoticeCenter(){MainActivity.this.showNoticeCenter();}
    @Override public int activeDestination(){return pageKind==10?-2:primaryDestination;}
    @Override public boolean isNoticePage(){return pageKind==10;}
    @Override public int unreadNoticeCount(){return noticeCenter().unreadCount(noticeSnapshot);}
  });
    navBall.attach(host);ui.post(this::reflowNavBall);}
  void refreshNavBall(){if(navBall!=null)navBall.onDestinationChanged();syncBackCallbackEnabled();}
  void reflowNavBall(){if(navBall!=null)navBall.onHostLayout();}
  @Override public Runnable levelCleanup(){return levelCleanup;}@Override public void setLevelCleanup(Runnable value){levelCleanup=value;}
  @Override public int pageDirection(){return pageDirection;}@Override public void setPageDirection(int value){pageDirection=value;}
  @Override public int toolQuality(){return toolQuality;}@Override public void setToolQuality(int value){toolQuality=value;}
  @Override public int toolTargetSizeKb(){return toolTargetSizeKb;}@Override public void setToolTargetSizeKb(int value){toolTargetSizeKb=value;}
  @Override public android.net.Uri toolImageUri(){return toolImageUri;}@Override public void setToolImageUri(android.net.Uri value){toolImageUri=value;}@Override public void setToolImageInfoText(String value){toolImageInfoText=value;}
  
  static final String PRODUCT_NAME="东方无限";
  /**
   * [DFW-7 → 2026-10-03 改] 自动检查更新的最小间隔：**24h 收紧到 60 秒**。
   *
   * ## 为什么推翻 DFW-7 的 24h
   * DFW-7 的理由是「冷启动不再每次都打网络，24h 一次足够（用户可随时手动查）」。
   * 用户 2026-10-03 报的问题说明这个取舍错了：
   * > 「我发布更新后，打开软件并没有弹出来弹窗让我更新。」
   *
   * 用户当天已经开过一次软件（那次检查把时间戳落了盘），**之后再怎么开关都被节流挡住**，
   * 于是"发布新版本"这件事在当天对他完全不可见 —— 只能靠手动点「检查更新」。
   *
   * ## 为什么这个取舍确实该反过来
   * 1. **收不到更新的代价 ≫ 多打一次网络的代价。** 更新检查是这个 App **唯一**的更新投递通道
   *    （DFW-82 那个「能下到新包但永远不提示更新」就是这条通道静默失效的形态）。
   *    而检查本身只是几个几百字节的 JSON GET，服务器在国内。
   * 2. **要判断有没有更新，就必须去查。** 节流省掉的那次请求，恰好就是唯一能发现更新的那次。
   *    任何"先节流再看"的设计都必然把更新推迟到节流窗口之后 —— 24h 就是最多推迟一天。
   * 3. **进程内去重已经把病态情况挡住了**（{@link #STARTUP_UPDATE_CHECKED_IN_PROCESS}）：
   *    同一次启动只会查一次。跨冷启动本来就不该再挡。
   *
   * ## 为什么留 60 秒而不是直接取消
   * 留一个**极小**的下限，防的是病态循环（比如崩溃重启风暴里 Activity 被反复重建）
   * 把服务器打爆。60 秒对真实使用**完全无感**（正常用户不会在一分钟内冷启两次）。
   *
   * ⚠️ **[2026-10-03 对抗性复查纠正] 这里原来写的是「能把最坏情况下的请求量钉在每分钟一次」——
   * 那句话不准确，已改。** 它只覆盖**更新检查这一路**：冷启动时还有
   * `maybeEnterMaintenance` 和 `maybeFetchNotices` **两路也打同一个接口，而且都没有节流**。
   *
   * 现在真正的兜底在**源头**：`RemoteConfigClient.FETCH_MEMO_MS` 让 3 秒内的多次
   * `fetch()` 只打一轮网络（4 个集合）。所以"最坏 12 个请求/冷启动"已经收敛成 4 个，
   * 而不是靠这一路节流。
   */
  static final long AUTO_UPDATE_CHECK_INTERVAL_MS=60L*1000;
  static final String UPDATE_CHECK_PREFS="update_check";
  static final String LANZOU_PLUS_REPOSITORY="https://github.com/nekobyran/lanzouplus";
  // 本应用自身开源主页（设置页"开源项目主页"与关于页指向此处）；上游 LanzouPlus 仓库保留在关于页致谢中
  static final String DFWX_REPOSITORY="https://github.com/dongwangxingchen/dongfang-wuxian";
  int BG,SURFACE,SURFACE2,PRIMARY,PRIMARY_HI,PRIMARY_LO,SECONDARY,TEXT,MUTED,DIV,BORDER,ERROR_TOKEN; boolean darkMode;
  NavBall navBall;
  /** v1.22.0 AI 首启权限引导链状态（串行请求：列表 + 当前位置 + 完成后放行动作） */
  java.util.List<String> pendingAiPermissions; int pendingAiIndex; Runnable pendingAiProceed;
    final ExecutorService io=Executors.newFixedThreadPool(Math.max(1,LanzouCore.adaptiveNetworkWorkers(Integer.MAX_VALUE))),imageIo=Executors.newFixedThreadPool(Math.max(1,LanzouCore.adaptiveSourceWorkers(0,Integer.MAX_VALUE))),searchIndexIo=Executors.newSingleThreadExecutor(r->{Thread thread=new Thread(r,"search-index-cache");thread.setDaemon(true);return thread;}); final DownloadHistoryStore downloadHistory=new DownloadHistoryStore(this::serializeDownloadHistory,json->{getSharedPreferences("download_history",MODE_PRIVATE).edit().putString("items",json).commit();}); final Handler ui=new Handler(Looper.getMainLooper()); LanzouCore core; DirectLinkResolver directResolver; AdbShellManager adbShell;
  ScrollView homeScroll; LinearLayout homeColumn;
  FrameLayout host,pageHost,searchScrollFrame,folderPullFrame; ViewGroup homeStage; ScrollView toastScroll,pageScroll; FolderPullScrollView folderPullScroll; HorizontalScrollView homeRecommendationsScroll; PopupWindow breadcrumbChooser; LinearLayout root,content,toastLayer,downloadList,selectionBar,sourceSelectionBar,downloadSelectionBar,folderPullIndicator,pageHeaderRow,primaryShell,homeBrand,homeHistory,homeRecommendations,homeLibsBand,sourceCategoryStrip,downloadFilterStrip,downloadExtensionStrip,autoInstallDownloadsRow; EditText search,sourceSearch,sourceFilter; ImageButton searchBack,selectionCopyDescription,selectionCategorize,selectionRenameFolder,sourceSelectionRename;  ImageButton downloadHeaderMenuButton; ProgressBar progress,indexProgressBar; DfwxLoadingRing folderMoreSpinner; Button selectionAllButton,sourceSelectionAllButton,downloadSelectionAllButton,searchPauseButton,indexControlButton; TextView status,statusRight,selectionSummary,sourceSelectionSummary,downloadSelectionSummary,sourceHeading,homeRecommendation,downloadPathText,settingsDownloadPathText,folderPullLabel,adbPermissionState,indexProgressText,indexCurrentText,indexUpdatedText; LumaSwitch adbSilentInstallSwitch; GridLayout itemsGrid,liveGrid,sourceGrid,historyGrid; SearchDragBar searchDragBar; SearchCategoryPicker homeCategoryPicker; View pageFrame,homeSearchBox,homeSearchCategory,folderEmptyState;
  List<Models.Item> current=new ArrayList<>(),folderItems=new ArrayList<>(),homeItems=new ArrayList<>(); List<FolderSearchEntry> folderSearchIndex=new ArrayList<>(),folderSearchCandidates=new ArrayList<>(); final Set<String> liveUrls=new HashSet<>(),currentSourceSearchUrls=new LinkedHashSet<>(),selectedUrls=new LinkedHashSet<>(),selectedSourceUrls=new LinkedHashSet<>(),testingSourceUrls=new HashSet<>(),autoRetriedDirs=new HashSet<>(); final Map<String,CheckBox> selectionChecks=new HashMap<>(),sourceSelectionChecks=new HashMap<>(); final Map<String,View> liveRows=new HashMap<>(); final Map<String,LinkedHashSet<String>> sourceCategories=new LinkedHashMap<>(); final Map<String,String> sourceSearchCorpora=new HashMap<>(); final List<Models.Source> visibleSources=new ArrayList<>(); final boolean[] sourceSelectionKinds={true,true,true,true}; List<Models.Source> sourcePageSources; final Set<DownloadEntry> selectedDownloads=new LinkedHashSet<>(); final Map<DownloadEntry,CheckBox> downloadChecks=new IdentityHashMap<>(); final List<FolderPageState> folderTrail=new ArrayList<>(); final Object sourceSearchLock=new Object(),searchUiLock=new Object(),directoryIndexPauseLock=new Object(); final ArrayDeque<Models.Item> pendingSearchAdds=new ArrayDeque<>(); final LinkedHashMap<String,Models.Item> pendingSearchUpdates=new LinkedHashMap<>(); FolderPageState activeFolderState; Models.Folder activeFolderProfile; Models.Source activeSource; Runnable systemBackAction,sourceSearchRunnable,folderLoadRunnable,folderPullCountdownRunnable; String currentSourceQuery="",folderSearchPreviousQuery="",downloadQuery="",downloadStateFilter="全部",downloadExtensionFilter="全部",activeSourceCategory="全部",folderPullRequestUrl=""; int folderSearchIndexedSize=-1,liveColumns=2,navigationSession,sourceSearchSession,sourceSearchPages,sourceRenderSession,sourceDataRevision,downloadFilterGeneration,sourceFilterGeneration,visible=50,pageDirection=1,folderNextPage=2,lastLayoutWidth,lastHostWidth,lastHostHeight,primaryDestination=-1,pageKind,folderPullRequestSession=-1,folderPullRequestPage,folderAutoExpandInitialRemaining,folderAutoExpandNextRemaining,pendingSearchSession=-1,pendingSearchEpoch=-1,searchFolderCount,searchWindowTarget,searchWindowDirtyFrom; volatile int searchGeneration,searchRenderEpoch; volatile GridLayout searchRenderGrid; GridLayout pendingSearchGrid; long folderNextReadyAt,folderEndNoticeUntil,searchUiTokenAt; double searchUiTokens; boolean profilePresent,folderProfilePending,downloadsPage,selectionMode,sourceSelectionMode,downloadSelectionMode,folderRootSources,folderHasMore,folderLoadingMore,folderRefreshing,primaryNavigationSwitch,homeSearchFocused,homeSearchRequested,homeSearchHistoryOnly,homePrefetchStarted,restoringFolderState,sourceSearchRunning,sourceSearchPaused,searchUiPosted,pendingSearchRefresh,searchWindowPosted,sessionBatchDownloadSingleItem,sessionOpenWebExternal,directoryIndexUserPaused,directoryIndexSearchPaused,directoryIndexResumePending;
  /**
   * DFW-40：图标加载的**失败记忆**（负缓存）。
   *
   * 原实现的失败路径只打一行日志、什么都不记：于是 404/超时的图标
   * **每次滚动回可视区都会重新发起一次网络请求**——弱网下这是明显的浪费，
   * 而且会让列表滚动卡顿（每个失败图标都要等一次 3 秒连接超时）。
   *
   * 现在是"失败后 N 分钟内不再重试"的有界记忆：
   *  - 用 LruCache 限容（不会无界增长）；
   *  - 带 TTL（过期后允许再试，避免服务端恢复了却永远拿不到图）；
   *  - 与 onTrimMemory 联动（内存紧张时一并清掉）。
   */
  static final long IMAGE_FAILURE_TTL_MS=5L*60*1000;
  final LruCache<String,Long> imageFailures=new LruCache<String,Long>(256){@Override protected int sizeOf(String key,Long value){return 1;}};
  final Object imageFailureLock=new Object();
  boolean imageRecentlyFailed(String url){
    if(url==null)return false;
    synchronized(imageFailureLock){
      Long at=imageFailures.get(url);
      if(at==null)return false;
      if(System.currentTimeMillis()-at>IMAGE_FAILURE_TTL_MS){imageFailures.remove(url);return false;}
      return true;
    }
  }
  void rememberImageFailure(String url){if(url==null)return;synchronized(imageFailureLock){imageFailures.put(url,System.currentTimeMillis());}}
  final LruCache<String,Bitmap> imageCache=new LruCache<String,Bitmap>(Math.max(1024,Math.min(8192,(int)(Runtime.getRuntime().maxMemory()/1024/24)))){@Override protected int sizeOf(String key,Bitmap value){return Math.max(1,value.getByteCount()/1024);}};
  final Object imageLock=new Object(); final Map<String,List<java.lang.ref.WeakReference<ImageView>>> imageWaiters=new HashMap<>(); final ArrayDeque<ImageDelivery> imageDeliveries=new ArrayDeque<>(); boolean imageDeliveryPosted; private static final java.util.regex.Pattern WEB_URL_CJK=java.util.regex.Pattern.compile("(?i)(?<![A-Z0-9._%+-])(?:(?:https?|ftp)://)?(?:[A-Z0-9-]+\\.)+[A-Z]{2,63}(?::[0-9]{1,5})?(?:/[A-Z0-9._~%!$&'()*+,;=:@/?#-]*)?(?![A-Z0-9._%+-])"),LANZOU_CLOUD_HOST=java.util.regex.Pattern.compile("^(?:[a-z0-9-]+[.])*(?:lanzou[a-z0-9]?|lanzov)[.]com$");
  static final String ACTION_WEB_DOWNLOAD="w",ACTION_OPEN_DOWNLOADS="h"; static final int STARTUP_NOTIFICATION_PERMISSION=4310,DELETE_PERMISSION=-2,DELETE_FAILED=-1,DELETE_MISSING=0,DELETE_OK=1,STORAGE_PERMISSION=62,IMPORT_RULES=64,EXPORT_RULES=65,STARTUP_STORAGE_PERMISSION=66,DIRECT_STORAGE_PERMISSION=67,FOLDER_PULL_THRESHOLD_DP=52,FOLDER_PULL_SETTLE_DP=56,FOLDER_PULL_MAX_DP=72,DEFAULT_TRANSFER_PARALLELISM=0,DEFAULT_INSTALL_PARALLELISM=0,DEFAULT_SOURCE_PROBE_PARALLELISM=0,SOURCE_LIST_MIN_DISPLAY=32,SEARCH_WINDOW=64,SEARCH_RENDER_CHUNK=64,IMAGE_UI_CHUNK=12,SOURCE_SELECT_LIST=0,SOURCE_SELECT_CUSTOM=1,SOURCE_SELECT_CHILD=2,SOURCE_SELECT_SOFTWARE=3,TOOL_PICK_IMAGE=68,TOOL_PICK_IMAGE2=69,TOOL_MIC_PERMISSION=70,AI_PERMISSION=71; static final long DOWNLOAD_PERSIST_INTERVAL_MS=1200L,DOWNLOAD_PERSIST_DEBOUNCE_MS=750L,DOWNLOAD_UI_INTERVAL_MS=100L; static final String DOWNLOAD_WAITING="等待中",DOWNLOAD_RESOLVING="解析中",DOWNLOAD_RUNNING="下载中",DOWNLOAD_PAUSED="已暂停",DOWNLOAD_CANCELLED="已取消",DOWNLOAD_COMPLETED="已完成",DOWNLOAD_FAILED="失败",ENTRY_DOWNLOAD="download",DOWNLOAD_SOURCE_LANZOU=DownloadSourcePolicy.LANZOU,DOWNLOAD_SOURCE_EXTERNAL=DownloadSourcePolicy.EXTERNAL,DOWNLOAD_SOURCE_UPDATE=DownloadSourcePolicy.UPDATE,DOWNLOAD_SOURCE_LEGACY=DownloadSourcePolicy.LEGACY; static final java.util.regex.Pattern SIZE_VALUE=java.util.regex.Pattern.compile("(?i)([0-9]+(?:\\.[0-9]+)?)\\s*([KMGT]?)"); Models.Item pendingPermissionDownload; boolean pendingPermissionAutoInstall; Runnable pendingToolColorImagePick,pendingMicAction; PendingRetry pendingRetryDownload; DownloadEntry pendingInstallEntry; List<Models.Item> pendingPermissionBatch; final List<DownloadEntry> downloadEntries=new CopyOnWriteArrayList<>(); final Set<DownloadEntry> dirtyDownloadUi=Collections.newSetFromMap(new IdentityHashMap<>()); final Map<DownloadEntry,LinearLayout> downloadActions=new IdentityHashMap<>(); final List<View> selectionHiddenViews=new ArrayList<>(); final Map<DownloadEntry,View> downloadRows=new IdentityHashMap<>(); final Map<DownloadEntry,TextView> downloadLabels=new IdentityHashMap<>(); final Map<DownloadEntry,ProgressBar> downloadBars=new IdentityHashMap<>(); final Map<String,TextView> batchDownloadLabels=new HashMap<>(); final Map<String,ProgressBar> batchDownloadBars=new HashMap<>(); final Map<String,View> batchDownloadRows=new HashMap<>(); final Map<String,CheckBox> batchDownloadChecks=new HashMap<>();  boolean downloadUiFramePosted,crashFolderEnsured;
  /**
   * 解析超时阈值（毫秒）。到点仍未返回就判失败，见 {@link #armResolveWatchdog}。
   *
   * ## 这个数字背后是一段失败史（2026-10-03 复盘）
   * 用户**从 v1.0.5 一路报到 v1.0.12**，症状始终是「下载永远停在解析中」：
   * 不崩溃、不报错、不超时，界面无限等待。
   *
   * DFW-101（2026-10-01）当时的做法是：在 `updateDownloadUi()` 里记录条目进入解析中的时刻，
   * 下次再被调用时若已超过本阈值就判失败。**它没有生效，而且原因很隐蔽**：
   * `updateDownloadUi()` 根本没有周期性调用者 —— 解析成功才有进度回调，解析失败才有失败回调，
   * 而它要防的恰恰是"解析器什么都不回调"。**看门狗在它唯一要防的场景里是死的。**
   *
   * 教训：**超时必须由独立定时器驱动，不能寄生在"某个函数会被反复调用"这个假设上。**
   * 现在由 {@link #armResolveWatchdog} 排一个真正的延时任务，不依赖任何其它代码路径。
   */
  static final long RESOLVE_WATCHDOG_MS=25000L;
  static final class BatchResolved{final DownloadEntry entry;final boolean cached;BatchResolved(DownloadEntry entry,boolean cached){this.entry=entry;this.cached=cached;}}
  static final class PendingRetry{final DownloadEntry entry;final int generation;final String state;PendingRetry(DownloadEntry entry){this.entry=entry;this.generation=entry.controlGeneration;this.state=entry.state;}}
  static final class FilterTab{final TextView label;final View underline;FilterTab(TextView label,View underline){this.label=label;this.underline=underline;}}
  static final class BulkPreparation{volatile Future<List<Models.Item>> future;volatile LinearLayout panel;volatile TextView label;volatile String progress="正在整理所选项目";}
  static final class SearchState{final List<Models.Item> items=new ArrayList<>();final Map<String,Models.Item> byUrl=new HashMap<>();String query="",source="",right="";int active,done,total,pages,percent,failures,folderCount;boolean running,nameOnly,indexOnly,paused;}
  static final class ItemCard{Models.Item item;final ImageView icon;final TextView title,meta,sourceBadge;final CheckBox check;String iconUrl;boolean folder;ItemCard(Models.Item item,ImageView icon,TextView title,TextView meta,TextView sourceBadge,CheckBox check){this.item=item;this.icon=icon;this.title=title;this.meta=meta;this.sourceBadge=sourceBadge;this.check=check;}}
  static final class FolderSearchEntry{final Models.Item item;final String folded;FolderSearchEntry(Models.Item item){this.item=item;this.folded=item.title.toLowerCase(Locale.ROOT);}}
  static final class ImageDelivery{final String url;final Bitmap bitmap;final List<java.lang.ref.WeakReference<ImageView>> targets;ImageDelivery(String url,Bitmap bitmap,List<java.lang.ref.WeakReference<ImageView>> targets){this.url=url;this.bitmap=bitmap;this.targets=targets;}}
  static final class DownloadEntry{String entryType=ENTRY_DOWNLOAD,source=DOWNLOAD_SOURCE_LANZOU,batchId="",batchTitle="",name="",state=DOWNLOAD_WAITING,error="",iconUrl="",shareUrl="",password="",uriString="",parentUriString="",sourceSizeText="",directUrl="",savedState="",uiState="",expectedUpdateVersion="";long createdAt,startedAt,completedAt,downloadedBytes,totalBytes,verifiedTotalBytes,resolvedAt,speedBps,etaSeconds=-1,lastSpeedAt,lastSpeedBytes,lastPersistAt,savedDownloadedBytes=-1,savedVerifiedTotalBytes=-1;int percent,savedPercent=-1,controlGeneration;boolean batchAdvanced,autoInstall,autoInstallFromPreference,installConfirmationGranted,directFallbackTried,stopRequested,resumeAfterPause,updateVerified,updateVerificationRunning;Uri target,verifiedUpdateUri;Runnable nextBatch,cancelPending;DirectLinkResolver.Ticket resolverTicket;SegmentDownloader downloader;UpdateClient.UpdateInfo updateInfo;
    // DFWX-STAB-001 generation 防竞态：以下两个判定方法必须在 synchronized(entry) 内调用，
    // SegmentDownloader 回调落地前先做 ownership 校验，旧 generation / 已让渡所有权的回调一律忽略。
    /** 进度/完成/失败/paused 回调的过期判定：generation 与 downloader 双重匹配才算当前所有者。 */
    boolean transferOwnedBy(int generation,SegmentDownloader owner){return generation==controlGeneration&&downloader==owner;}
    /** cancelled 回调的过期判定：只看 owner——cancelDownload 会先 ++controlGeneration，
     *  cancelled 自身的旧 generation 永远过期，必须放行才能清理 partial 并推进批次；owner 已被 retry 替换时拒绝。 */
    boolean transferOwnerIs(SegmentDownloader owner){return downloader==owner;}
  }
  static final class SelectionBarLayout{final TextView summary;final Button all;final ImageButton[] actions;final String[] labels;final int allWidth,actionWidth;boolean twoRows;int available=-1;SelectionBarLayout(TextView summary,Button all,int allWidth,int actionWidth,String[] labels,ImageButton[] actions){this.summary=summary;this.all=all;this.allWidth=allWidth;this.actionWidth=actionWidth;this.labels=labels;this.actions=actions;}}
  static final class FolderPageState{final Models.Source source=new Models.Source();Models.Folder folder;List<Models.Item> folderItems=new ArrayList<>(),current=new ArrayList<>();final Map<String,Bitmap> pinnedIcons=new HashMap<>();String query="";int visible=50;int nextPage=2;int scrollY;long nextReadyAt;boolean hasMore;FolderPageState(Models.Source value){copySource(value,source);}}
  final class SourceListPageState{
    final View pageFrame;
    final LinearLayout root,primaryShell,pageHeaderRow,sourceCategoryStrip,sourceSelectionBar;
    final ScrollView pageScroll;
    final GridLayout sourceGrid;
    final TextView sourceHeading,sourceSelectionSummary;
    final EditText sourceFilter;
    final SearchDragBar searchDragBar;
    final ImageButton sourceSelectionRename;
    final Button sourceSelectionAllButton;
    final List<Models.Source> sourcePageSources,visibleSources;
    final Map<String,CheckBox> sourceSelectionChecks;
    final Set<String> selectedSourceUrls;
    final boolean sourceSelectionMode;
    final boolean[] sourceSelectionKinds;
    final String activeSourceCategory,query;
    final int sourceDataRevision,densityDpi,colorSignature;
    SourceListPageState(){
      pageFrame=MainActivity.this.pageFrame;root=MainActivity.this.root;primaryShell=MainActivity.this.primaryShell;pageHeaderRow=MainActivity.this.pageHeaderRow;pageScroll=MainActivity.this.pageScroll;sourceGrid=MainActivity.this.sourceGrid;sourceHeading=MainActivity.this.sourceHeading;sourceFilter=MainActivity.this.sourceFilter;sourceCategoryStrip=MainActivity.this.sourceCategoryStrip;searchDragBar=MainActivity.this.searchDragBar;sourceSelectionBar=MainActivity.this.sourceSelectionBar;sourceSelectionSummary=MainActivity.this.sourceSelectionSummary;sourceSelectionRename=MainActivity.this.sourceSelectionRename;sourceSelectionAllButton=MainActivity.this.sourceSelectionAllButton;
      sourcePageSources=MainActivity.this.sourcePageSources;visibleSources=new ArrayList<>(MainActivity.this.visibleSources);sourceSelectionChecks=new HashMap<>(MainActivity.this.sourceSelectionChecks);selectedSourceUrls=new LinkedHashSet<>(MainActivity.this.selectedSourceUrls);sourceSelectionMode=MainActivity.this.sourceSelectionMode;sourceSelectionKinds=MainActivity.this.sourceSelectionKinds.clone();activeSourceCategory=MainActivity.this.activeSourceCategory;query=sourceFilter==null?"":sourceFilter.getText().toString();sourceDataRevision=MainActivity.this.sourceDataRevision;densityDpi=getResources().getDisplayMetrics().densityDpi;colorSignature=sourceListColorSignature();
    }
  }
  static final class SourceDestination{final String category,nodeId,path;final Models.Source composite;SourceDestination(String category,Models.Source composite,String nodeId,String path){this.category=category==null?"":category;this.composite=composite;this.nodeId=nodeId==null?"":nodeId;this.path=path==null?"":path;}boolean categoryOnly(){return composite==null&&!category.isEmpty();}String key(){return categoryOnly()?"category:"+category:"directory:"+LanzouCore.sourceId(composite)+":"+nodeId;}}
  final Models.Source home=new Models.Source(),sharedHome=new Models.Source();
  final SearchState globalSearch=new SearchState();
    int sessionSearchConcurrency,sessionSearchBatchSeconds=15,sessionSearchMaxPages,sessionSearchViewRate=0,sessionSearchMode=Models.SearchOptions.MODE_DIRECTORY,sessionFileListModeMask=Models.SearchOptions.MASK_API|Models.SearchOptions.MASK_DIRECTORY,sessionSearchModeMask=Models.SearchOptions.MASK_DIRECTORY,sessionSearchFuzzyMask,sessionAutoExpandInitialPages=1,sessionAutoExpandNextPages=1,sessionIndexThreads,sessionIndexRetentionHours=24,sessionListInitialCount=50,sessionListAppendCount=50,sessionSourceListDisplayCount=SOURCE_LIST_MIN_DISPLAY,folderRenderGeneration,sessionUaPreset=LanzouCore.UA_PRESET_MOBILE_CHROME,sessionUaScopeMask=LanzouCore.UA_SCOPE_ALL,sessionUaFileListPreset=LanzouCore.UA_PRESET_MOBILE_CHROME,sessionUaDirectorySearchPreset=LanzouCore.UA_PRESET_MOBILE_CHROME,sessionUaApiSearchPreset=LanzouCore.UA_PRESET_MOBILE_CHROME,sessionUaDirectPreset=LanzouCore.UA_PRESET_MOBILE_CHROME;boolean sessionSearchRecursiveFolders,sessionSearchFuzzyMatching,sessionBackgroundIndex=true,sessionIndexEnabled=false,sessionAutoExpand,directLanzouListOpen,showSourceLinks,sessionLanzouTimeoutFailover=true,folderRenderingRows;
    String sessionSearchCategory="全部",sessionLanzouBaseOrigin="https://oreojiang.lanzout.com",sessionCustomUserAgent="";
    SourceListPageState retainedSourceListPage,sourceListRebuildFallback; final ArrayDeque<Runnable> pendingStorageAccessActions=new ArrayDeque<>(); boolean storageAccessPromptShowing,manageAllFilesSettingsPending,manageAllFilesStartupFlow; int sourceSearchCorporaRevision=-1; Runnable sourceListFilterRunnable;
  static final java.util.concurrent.atomic.AtomicBoolean STARTUP_UPDATE_CHECKED_IN_PROCESS=new java.util.concurrent.atomic.AtomicBoolean(); static MainActivity ACTIVE_OWNER;static java.lang.ref.WeakReference<MainActivity> ACTIVE_INSTANCE=new java.lang.ref.WeakReference<>(null);
  static final int FUZZY_FILE_LIST=1,FUZZY_DIRECTORY=2,FUZZY_INDEX=4,FUZZY_ALL=FUZZY_FILE_LIST|FUZZY_DIRECTORY|FUZZY_INDEX;
  final java.util.concurrent.atomic.AtomicBoolean updateCheckRunning=new java.util.concurrent.atomic.AtomicBoolean();
  final java.util.concurrent.atomic.AtomicBoolean directoryIndexRunning=new java.util.concurrent.atomic.AtomicBoolean();
  final java.util.concurrent.atomic.AtomicBoolean indexProgressPollPosted=new java.util.concurrent.atomic.AtomicBoolean();
  final java.util.concurrent.atomic.AtomicBoolean manualUpdateFeedbackRequested=new java.util.concurrent.atomic.AtomicBoolean();
  String pendingPermissionUpdateVersion="";
  boolean suppressUiMotion,mainExperienceStarted,ownsStartupUpdateCheck;
  long homeBrandCialloGeneration; final Set<View> homeBrandCialloViews=Collections.newSetFromMap(new IdentityHashMap<>());
  final View.OnClickListener systemBackClick=v->performSystemBack(),downloadDirectoryClick=v->openDownloadDirectory();
  // v1.19.3 ANR 修复：adbShell.start() 注册 Shizuku sticky 监听会立即在主线程做 binder transact
  // （refresh→pingBinder/checkSelfPermission/getUid），真机上 Shizuku 服务被 ROM 冻结时 transact
  // 无超时阻塞=黑屏 ANR；模拟器无 Shizuku 无法复现。静默安装状态晚 3 秒初始化无感知，
  // sticky 语义保证延迟期间连接不丢事件。
  void deferredAdbShellStart(){adbShell.start();}
  @Override public void onCreate(Bundle b){super.onCreate(b);ACTIVE_OWNER=this;ACTIVE_INSTANCE=new java.lang.ref.WeakReference<>(this);applySystemColors();deleteSharedPreferences("premium-session-v1");io.execute(()->{try{java.security.KeyStore ks=java.security.KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(ks.containsAlias("cc.nkbr.lanzouplus.premium.v1"))ks.deleteEntry("cc.nkbr.lanzouplus.premium.v1");}catch(Exception ignored){}});// v1.23 优享移除:一次性清理旧版本本机加密会话,不留无法清除的残留
loadSearchSettings();applyUserAgentSettings();detectWeakDevice();installBackAnimationCallback();core=new LanzouCore(this);core.setDirectoryCachingEnabled(true);core.setIndexPauseSupplier(this::directoryIndexPaused);directResolver=new DirectLinkResolver(this,core);adbShell=new AdbShellManager(this,this::onAdbShellStateChanged);ui.postDelayed(this::deferredAdbShellStart,3000);loadDownloadHistory();loadRecommendations();activeSource=home;getPreferences(0).edit().putBoolean("accepted",true).apply();startMainExperience();if(!getPreferences(0).getBoolean("oldAiNoticed",false)&&!getSharedPreferences("ai_chat_settings",0).getAll().isEmpty()){getPreferences(0).edit().putBoolean("oldAiNoticed",true).apply();ui.post(()->showNotice("AI 对话已全新升级：旧版 AI 配置已停用（不影响其他数据），新版请到 AI 页抽屉底部的设置里添加自己的渠道",true));}handleExternalAction(getIntent());}

  /**
   * DFW-14：系统内存紧张时主动释放**可重建**的缓存。
   *
   * 为什么需要：本应用把图标位图放在 LruCache + 文件夹快照的 pinnedIcons 里，
   * 滑动大量目录后这一块是最大且**最容易重建**的内存占用；此前完全没有回收点，
   * 后台被杀之前只能等系统强杀，返回前台就得重新下载图片。
   *
   * 只清"能重新拿到"的东西（图标位图）；不动任何用户数据、不清下载历史、不打断进行中的任务。
   * `UI_HIDDEN` 之外的档位（RUNNING_MODERATE/COMPLETE、BACKGROUND 等）同样按档处理：
   * 轻度紧张先降解码质量即可，重度才整块清空——避免一有压力就狂重载图片。
   */
  @Override public void onTrimMemory(int level){
    super.onTrimMemory(level);
    try{
      if(level>=TRIM_MEMORY_COMPLETE||level>=TRIM_MEMORY_BACKGROUND){
        imageCache.evictAll();
        // DFW-40：失败记忆同样是有界缓存，内存紧张时一并释放
        synchronized(imageFailureLock){imageFailures.evictAll();}
        // 文件夹快照里的 pinnedIcons 同样持有位图强引用，一并释放（可重新下载，不是用户数据）
        if(activeFolderState!=null)activeFolderState.pinnedIcons.clear();
        for(FolderPageState state:folderTrail)state.pinnedIcons.clear();
        return;
      }
      if(level>=TRIM_MEMORY_RUNNING_LOW||level>=TRIM_MEMORY_MODERATE){
        // 中度：只清掉一部分（LruCache 的 trimToSize 会按 LRU 淘汰），保留热图不闪
        imageCache.trimToSize(Math.max(1,imageCache.maxSize()/3));
        return;
      }
      if(level>=TRIM_MEMORY_RUNNING_MODERATE||level>=TRIM_MEMORY_UI_HIDDEN){
        // 轻微：清空"未交付"的图片投递队列即可（这些是滑动过快时排队的过期请求）
        synchronized(imageLock){imageWaiters.clear();imageDeliveries.clear();}
      }
    }catch(Throwable error){
      android.util.Log.w("MainActivity","onTrimMemory: "+error.getMessage(),error);
    }
  }
  @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);handleExternalAction(intent);}
  /**
   * [DFWX PATCH P16] 内嵌 AI 页的音量键滚动：上游音量键监听挂在 RouteActivity 实例上，
   * 内嵌态（AppRoutes 跑在本 Activity 的 ComposeView 里）拿不到该实例，故经进程级
   * VolumeKeyBridge 注册与分发。仅在 AI 页（pageKind==5）且监听者消费时才拦截。
   *
   * `@SuppressLint("RestrictedApi")` 是**误报抑制**，理由（DFW-123）：
   *  - 报的是 androidx 的 `@RestrictTo(LIBRARY_GROUP_PREFIX)` 内部策略标记，**不是运行期限制**；
   *  - 覆盖一个 public 方法并调 `super` 是标准 Java 用法，而且这里**不调 super 才是 bug** ——
   *    `ComponentActivity.dispatchKeyEvent` 负责把返回键喂给 `OnBackPressedDispatcher`，
   *    去掉它等于把返回键处理弄坏；
   *  - 本覆盖是 PATCH P16 明确引入的，有别处替代不了的需求（音量键桥）。
   * 不写进 `lint.xml` 是因为它的粒度是"文件+issue"，会把本文件**将来所有** RestrictedApi 一起静音；
   * 写在这里只影响这一个方法，且读代码的人立刻看得到理由。
   */
  @android.annotation.SuppressLint("RestrictedApi")
  @Override public boolean dispatchKeyEvent(android.view.KeyEvent event){
    if(pageKind==5&&event.getAction()==android.view.KeyEvent.ACTION_DOWN){
      int code=event.getKeyCode();
      if(code==android.view.KeyEvent.KEYCODE_VOLUME_UP||code==android.view.KeyEvent.KEYCODE_VOLUME_DOWN){
        if(me.rerere.rikkahub.dfwx.VolumeKeyBridge.INSTANCE.dispatch(code==android.view.KeyEvent.KEYCODE_VOLUME_UP))return true;
      }
    }
    return super.dispatchKeyEvent(event);
  }
  static boolean enqueueWebDownload(String url,String name){MainActivity activity=ACTIVE_OWNER!=null?ACTIVE_OWNER:ACTIVE_INSTANCE.get();if(activity==null||activity.isFinishing())return false;activity.runOnUiThread(()->activity.startWebDirectDownload(url,name));return true;}
  static boolean showWebNotice(String message,boolean longLived){MainActivity activity=ACTIVE_OWNER!=null?ACTIVE_OWNER:ACTIVE_INSTANCE.get();if(activity==null||activity.isFinishing())return false;activity.showNotice(message,longLived);return true;}
  /** 给 SupportActivity 的静态回执（同 showWebNotice 通道；SupportActivity 无法直接拿到 activity 实例）。
   *  [DFW-78] 主路径改成站内页后，只剩**遗留**的 SupportActivity.unlockNow() 还在用它；
   *  站内页的解锁直接走自己的 showNotice(...)，不再绕这一层。 */
  static boolean showSupportNotice(Activity host,String message){
    MainActivity activity=ACTIVE_OWNER!=null?ACTIVE_OWNER:ACTIVE_INSTANCE.get();
    if(activity!=null&&!activity.isFinishing()){activity.showNotice(message,false);return true;}
    return false;
  }
  // —— [DFW-98 2026-10-01] **下载不再有任何付费拦截** ——
  //
  // 用户原话："下载居然需要付费下载权限，我早就改了……你能不能去掉这些限制呢？
  //           去掉所有的限制，让所有人都可以免费使用这些功能。
  //           只有那个内置的 AI API 是需要付费才能使用的。"
  //
  // 删掉的：
  //   · `gateDownload(Runnable)` —— 它把**主下载路径**（beginDownload）、批量下载、重试下载，
  //     **连 App 自己的更新下载**（beginVerifiedUpdateDownload）全包了一层；
  //     而 `Support.askOnDownload()` 默认是 true，所以**每个新装用户下载时都会被弹付费窗**。
  //     更糟的是弹窗接管了 action：**它不往下放行，下载就永远停在「解析中」** ——
  //     用户报的"一直解析中、下载不了"很可能就是这里，而不是网络。
  //   · `supportNaggedThisSession` 会话标记
  //   · `showSupportPrompt(Runnable)` 付费弹窗本体
  //
  // **付费现在只剩一个门**：内置 AI 渠道（`BuiltinAiChannel` 的付费门）。
  // 其余功能对所有人免费 —— 这是用户明确的产品决定。
  /** 支持弹窗（轻量版付费页：开发者信一句话 + 权益一行 + 双按钮），完整版是站内页 showSupportPage() */

  /**
   * [DFW-78] 打开诚信付费页 —— 现在是**站内页**（`showSupportPage()`），不再是独立 Activity。
   *
   * 用户 2026-10-01 第二次投诉这一页的动画：
   * > "点击诚信付费按钮后的动画效果和关闭那个页面的动画效果太磨叽且不自然不流畅，重置，
   * >  用和打开关于东方无限页面一样的动画效果。"
   *
   * 病根在**结构**：站内推入会把下面那页缩到 0.94 并压暗到 55%，因为那是同一个窗口里的另一块
   * View，底下是应用自己的黑底；独立 Activity 上做同样的事，透出来的是**桌面壁纸**，所以之前
   * 只能不做 —— 观感必然"不一样"。改成站内页后，进/出都走 `animatePage`（推入 +1 / 弹出 -1），
   * 与关于东方无限、崩溃日志同一条配方。
   *
   * 方法名保留：设置页顶部按钮与下载拦截弹窗两处调用点都不必改。
   */
  void openSupportActivity(){showSupportPage();}
  /* v1.5.1：按钮永远打开赞助码页，解锁确认统一由页内按钮触发爱心感谢页——删除原已付费简陋弹窗分流。
     [DFW-78] 解锁后的感谢页现在是**同一站内页里换内容**（renderThankYou），不再跳第二个窗口。 */

  void handleExternalAction(Intent intent){if(intent==null)return;String action=intent.getAction();
    if(ACTION_OPEN_DOWNLOADS.equals(action)){pageDirection=0;showDownloads();return;}
    if(ACTION_WEB_DOWNLOAD.equals(action)){String url=intent.getStringExtra("url"),name=intent.getStringExtra("name");startWebDirectDownload(url,name);return;}
    // DFW-12：接收系统分享/链接打开（用户在微信/QQ 里收到蓝奏链接，可直接"分享到东方无限"）。
    // 延迟一拍执行：冷启动时 Activity 还没完成首次布局，直接开弹窗/解析会拿不到正确的窗口环境。
    if(Intent.ACTION_SEND.equals(action)){String text=sharedText(intent);if(text.isEmpty())return;ui.post(()->openRecognizedLink(extractSharedUrl(text),""));return;}
    if(Intent.ACTION_VIEW.equals(action)){Uri data=intent.getData();if(data==null)return;String value=data.toString();if(!isShareableWebUrl(value))return;ui.post(()->openRecognizedLink(value,""));return;}
  }
  /** 分享进来的文本：优先 EXTRA_TEXT，其次 EXTRA_SUBJECT（有些应用只填后者）。 */
  String sharedText(Intent intent){if(intent==null)return"";String text=intent.getStringExtra(Intent.EXTRA_TEXT);if(text==null||text.trim().isEmpty())text=intent.getStringExtra(Intent.EXTRA_SUBJECT);return text==null?"":text.trim();}
  /**
   * 从分享文本里挑出第一条可用的 http(s) 链接（微信/QQ 分享常带"标题 + 链接 + 口令"混合文本）。
   * 找不到链接就返回原文，交给 normalizedWebUrl 去补 https 前缀（用户可能只分享了裸域名）。
   */
  String extractSharedUrl(String text){return LinkPolicy.extractSharedUrl(text);}
  /** 只接收 http/https 的网页链接：拒绝 file/content/其它 scheme，避免变成任意文件/意图入口。 */
  boolean isShareableWebUrl(String value){return LinkPolicy.isShareableWebUrl(value);}
    /** [DFW-73] 首屏按用户选的「初始界面」进入（软件库 / AI 对话 / 工具箱，见 startDestinationPreference）。
     *  AI 分支必须和悬浮球那条路径一样先过权限引导链，否则首启直接进 AI 页会缺权限提示。 */
    void startMainExperience(){if(mainExperienceStarted)return;mainExperienceStarted=true;int start=startDestinationPreference();if(start==4)maybeGuideAiPermissions(this::enterAiPage);else if(start==5)showTools();else showHomeLanding();prefetchHome();ui.postDelayed(()->{if(sessionBackgroundIndex)requestDirectoryIndexUpdate(false,false);},2500);
    // v1.22.10：已授权就静默建好 Download/东方无限/崩溃日志（空目录也要让用户看得见）；
    // 未授权不在启动时弹窗打扰——用户 2026-09-29 明确要求"下载后再问存储权限"。
    if(storageAccessGranted())ensureCrashFolder();
    /*
     * [DFW-7 → 2026-10-03 改] 冷启动查更新：**6 秒 → 1 秒**。
     *
     * 用户 2026-10-03 报：
     * > 「按理来说，每一次打开软件，它都会刷新更新。为什么我打开软件后，
     * >   过了好久才弹出来更新弹窗呢？」
     *
     * DFW-7 当初排 6 秒的理由只写了四个字「错开启动高峰」，但**这个理由站不住**：
     * 同一个方法里，维护拦截（{@code maybeEnterMaintenance}）和公告
     * （{@code maybeFetchNotices}）都是 {@code ui.post} **立即**发出去的，
     * 而且它们打的是**同一个** {@code RemoteConfigClient.fetch()} 接口。
     * 既然那两条能立即发，更新这条没有任何理由排到最后 ——
     * 结果只是"用户要盯着屏幕干等 6 秒才看到更新提示"。
     *
     * 留 1 秒而不是 0：更新提示是个**对话框**，要等首屏那一帧画完再弹，
     * 否则会在用户还没看清界面时糊上来（和公告弹窗的时机保持一致）。
     */
    ui.postDelayed(this::maybeCheckForUpdates,1000);
    // DFW-60：维护/停更拦截。放在启动后异步拉取，不阻塞首屏——拉不到就按正常放行（fail-open），
    // 绝不因服务器故障把用户挡在门外。
    ui.post(this::maybeEnterMaintenance);
    // DFW-61：公告与未读红点。同样异步拉取，不阻塞首屏。
    ui.post(this::maybeFetchNotices);}
    @Override protected void onResume(){super.onResume();if(adbShell!=null)adbShell.refresh();
      /*
       * [DFW-97] **一开软件就要存储权限和通知权限。**
       * 用户 2026-10-02：「一开软件就要『存储权限』和『通知权限』，其他的按照需要打开，明白不。」
       *
       * 为什么放在 onResume + postDelayed 而不是 onCreate：
       * ① onCreate 时窗口还没画出来，系统权限弹窗会被"应用正在启动"的状态盖住，
       *    用户看到的是"卡了一下"而不是"它在问我要权限"；
       * ② 延后 700ms 让首屏先出来，用户知道自己在哪个页面被问的，不会觉得突兀。
       * 只问一次（askedStartupPermissions），后面进出页面不再骚扰。
       */
      if(!askedStartupPermissions){askedStartupPermissions=true;ui.postDelayed(this::requestStartupPermissions,700);}
    // v1.22.11：用户可能在系统设置里自己开了"管理所有文件"（不走我们的引导），回来时补建一次崩溃目录。
    // 幂等且放后台线程，避免每次回前台都在主线程做文件 IO。
    if(!crashFolderEnsured&&storageAccessGranted()){crashFolderEnsured=true;io.execute(this::ensureCrashFolder);}if(manageAllFilesSettingsPending&&Build.VERSION.SDK_INT>=30){manageAllFilesSettingsPending=false;boolean startup=manageAllFilesStartupFlow;manageAllFilesStartupFlow=false;if(Environment.isExternalStorageManager()){ensureCrashFolder();runPendingStorageAccessActions();}else{pendingStorageAccessActions.clear();showNotice("未获得管理所有文件权限；下载、更新、删除和自定义路径可能不可用",true);}if(startup)ui.post(this::maybeRequestBatteryExemption);}if(pendingInstallEntry!=null&&(Build.VERSION.SDK_INT<26||getPackageManager().canRequestPackageInstalls())){DownloadEntry ready=pendingInstallEntry;pendingInstallEntry=null;ui.post(()->installEntryWithSystemInstaller(ready));}}
  final List<Models.Source> libraries=new ArrayList<>();final Map<String,String> libraryNames=new HashMap<>();
  /**
   * 是否允许「85 个内置软件源」的自动批量导入。
   *
   * [2026-10-03 修测试偷偷联网] 这个开关原来是 `= true`，**靠 44 个测试类各自在
   * `@BeforeClass` 里置 false 来压制**。但那是"每个测试自己记得关"，必然漏：
   * 实测有 5 个类会启动完整 `MainActivity.setup()` 却**没设**它
   * （`DfLogWiringJvmTest` / `CrashReportExportJvmTest` / `SettingsStructureTest` /
   *  `SearchPageBudgetJvmTest` / `AiPermissionGateJvmTest`）。
   *
   * 更隐蔽的是**它看起来是好的**：`app/build.gradle.kts:122` 是 `forkEvery = 24`，
   * 24 个测试类共用一个 JVM —— 只要同批里**前面**有一个类置过 false，静态字段就一直是 false，
   * 后面这 5 个类跟着白蹭，全量跑往往看不出来。
   * 但 `bash tools/run-tests.sh --class SearchPageBudgetJvmTest` **单跑就现原形**：
   * 走 `MainActivity:349` 的自动导入 → `LanzouCore.addUserSourcesBatch` →
   * `probeSingleSource` → **对 85 个源发真实网络请求**。
   * 而 `run-tests.sh` 的 `--class` 恰恰是主要用法。
   *
   * 这正是 DFW-124（"测试偶发失败"）的**第二条联网路径** —— 当时只堵了
   * `RemoteConfigClient.get()` 那一个咽喉，`LanzouCore` 全文 `isJvmUnitTest` 出现 0 次。
   *
   * 修法：默认值直接取**唯一那份判据** `App.isJvmUnitTest()`
   * （`App.java` 的 javadoc 明确写了"判据只有这一处实现，不要在别处再抄一份"）。
   * 线上 APK 里 classpath 没有 Robolectric → `isJvmUnitTest()=false` → 这里为 true，行为不变；
   * 测试里恒为 false，**不再依赖任何测试类"记得关"**。
   * 44 个 `@BeforeClass` 里现有的 `= false` 保留不动（它们是显式声明，无害）。
   */
  static volatile boolean LIBRARY_AUTO_IMPORT=!App.isJvmUnitTest();
  void loadRecommendations(){
    List<Models.Source> values=core.recommendations();
    libraries.clear();libraryNames.clear();libraries.addAll(values);
    for(Models.Source lib:libraries)if(!lib.url.isEmpty())libraryNames.put(LanzouCore.sourceId(lib),lib.title);
    if(!values.isEmpty())copySource(values.get(0),home);
    if(values.size()>1)copySource(values.get(1),sharedHome);
    // v1.19.6 内置清单改名（「分类·内容」格式）：flag 版本化 .v4——存量安装按 URL 强推一次标题同步
    // （导入探测会把蓝奏云页面标题落库，不匹配旧命名特征，只能无条件覆盖这一次），再幂等补导；
    // 之后用户手动改名不再被动。新装照常批量导入
    if(!libraries.isEmpty()&&LIBRARY_AUTO_IMPORT&&!getPreferences(0).getBoolean("librariesImported.v4",false)){
      java.util.Map<String,String> titles=new java.util.LinkedHashMap<>();
      StringBuilder rules=new StringBuilder();
      for(Models.Source lib:libraries)if(!lib.url.isEmpty()){titles.put(lib.url,lib.title);rules.append(lib.url);if(!lib.password.isEmpty())rules.append(" 提取码:").append(lib.password);rules.append('\n');}
      final String batch=rules.toString();
      final java.util.Map<String,String> batchTitles=titles;
      // 两次同步：导入前覆盖存量设备的旧标题；导入后再覆盖一次——新装设备 addUserSourcesBatch
      // 探测成功会用蓝奏云页面标题落库，必须等导入完成后补同步，r 清单的新名才能统一生效
      io.execute(()->{try{core.syncLibraryTitles(batchTitles,true);}catch(Exception syncError){android.util.Log.w("MainActivity", "syncLibraryTitles failed: "+syncError.getMessage());}
        try{core.addUserSourcesBatch(batch);}catch(InterruptedException ignored){android.util.Log.i("MainActivity", "MainActivity interrupted: "+ignored.getMessage());return;}
        try{core.syncLibraryTitles(batchTitles,true);}catch(Exception ignored){}
        getPreferences(0).edit().putBoolean("librariesImported.v4",true).apply();});// 静默导入：探测提交成功后才落 flag——离线首启全失败时不落旗、下次启动自动重试（URL 去重幂等），重复 URL 自动跳过
    }
  }
  String libraryNameFor(Models.Item item){
    if(item==null)return"";
    if(!item.source.isEmpty())return item.source;
    String url=item.url==null?"":item.url;
    for(Models.Source lib:libraries)if(!lib.url.isEmpty()&&url.contains(lib.url))return lib.title;
    return"";
  }
  static void copySource(Models.Source from,Models.Source to){to.id=from.id;to.nodeId=from.nodeId;to.kind=from.kind;to.title=from.title;to.url=from.url;to.password=from.password;to.searchable=from.searchable;to.error=from.error;to.publisher=from.publisher;to.avatarUrl=from.avatarUrl;to.description=from.description;to.childDirectory=from.childDirectory;to.originPath=from.originPath;to.originUrl=from.originUrl;to.members.clear();for(Models.SourceMember member:from.members){Models.SourceMember copy=new Models.SourceMember();copy.kind=member.kind;copy.id=member.id;copy.parentId=member.parentId;copy.title=member.title;copy.url=member.url;copy.password=member.password;copy.iconUrl=member.iconUrl;copy.size=member.size;copy.time=member.time;copy.description=member.description;copy.error=member.error;copy.searchable=member.searchable;to.members.add(copy);}}
  Models.Source runtimeLanzouSource(Models.Source input){if(input==null)return null;Models.Source out=new Models.Source();copySource(input,out);out.url=preferredLanzouUrl(out.url);out.originUrl=preferredLanzouUrl(out.originUrl);for(Models.SourceMember member:out.members)if(!member.url.isEmpty())member.url=preferredLanzouUrl(member.url);return out;}
  static String sourceKey(Models.Source source){return source==null?"":LanzouCore.sourceId(source);}
  static String firstNonEmpty(String value,String fallback){return value==null||value.trim().isEmpty()?fallback:value.trim();}
  static boolean compositeSource(Models.Source source){return source!=null&&source.kind==Models.SOURCE_COMPOSITE;}
  static boolean singleFileSource(Models.Source source){return source!=null&&source.kind==Models.SOURCE_SINGLE;}
  static int sourceSelectionKind(Models.Source source){if(source!=null&&source.childDirectory)return SOURCE_SELECT_CHILD;if(compositeSource(source))return SOURCE_SELECT_CUSTOM;if(singleFileSource(source))return SOURCE_SELECT_SOFTWARE;return SOURCE_SELECT_LIST;}
  static boolean sourceMatchesSelectionKinds(Models.Source source,boolean[] enabled){int kind=sourceSelectionKind(source);return enabled!=null&&kind>=0&&kind<enabled.length&&enabled[kind];}
  static boolean transientLinkSource(Models.Source source){return compositeSource(source)&&source.id.startsWith("link:");}
  static boolean collectionSource(Models.Source source){if(source==null)return false;if(source.kind==Models.SOURCE_COMPOSITE)return true;if(source.kind==Models.SOURCE_SINGLE)return false;if(!source.members.isEmpty())return source.members.size()>1||source.members.get(0).kind!=Models.MEMBER_FILE;return true;}
  static Models.Item singleFileItem(Models.Source source){Models.Item item=new Models.Item();Models.SourceMember member=source.members.isEmpty()?null:source.members.get(0);item.title=member==null||member.title.isEmpty()?source.title:member.title;item.url=member==null||member.url.isEmpty()?source.url:member.url;item.shareUrl=source.url.isEmpty()?item.url:source.url;item.password=member==null||member.password.isEmpty()?source.password:member.password;item.iconUrl=member==null||member.iconUrl.isEmpty()?source.avatarUrl:member.iconUrl;item.size=member==null?"":member.size;item.time=member==null?"":member.time;item.description=member==null||member.description.isEmpty()?source.description:member.description;item.source=source.title;return item;}
  @Override protected void onDestroy(){/** F8:v1.22.1 起返回逻辑走 androidx dispatcher（backCallback 字段），随 Activity 销毁自动清理；旧的裸 OnBackInvokedCallback 注销代码一并移除（其方法引用每次都是新对象，本来就注销不掉） */MainActivity active=ACTIVE_INSTANCE.get();if(active==this)ACTIVE_INSTANCE.clear();if(ACTIVE_OWNER==this)ACTIVE_OWNER=null;if(ownsStartupUpdateCheck){STARTUP_UPDATE_CHECKED_IN_PROCESS.set(false);ownsStartupUpdateCheck=false;}synchronized(globalSearch){searchGeneration++;globalSearch.paused=false;globalSearch.notifyAll();}synchronized(sourceSearchLock){sourceSearchSession++;sourceSearchPaused=false;sourceSearchLock.notifyAll();}/* DFWX-STAB-001（修审计 H-P0a）：先摘掉 UI 待执行消息再 close 组件——DirectLinkResolver.close 会同步回调 failed()，
  回调链若在 ui 队列再排新任务会触碰已 teardown 的 UI；drainDownloadUi 的 isDestroyed() 防线拦截这些晚到任务 */ui.removeCallbacksAndMessages(null);flushDownloadHistoryNow();releaseToolMedia();if(core!=null)core.close();if(directResolver!=null)directResolver.close();if(adbShell!=null)adbShell.close();searchIndexIo.shutdownNow();io.shutdownNow();imageIo.shutdownNow();super.onDestroy();}
  long lastSystemBackAt;/** F3:侧滑/返回键 260ms 节流,防手势取消重提交与连滑双触发放大静默失败 */
  /** [DFW-73] 顶级页"再返回一次退出软件"：横幅视图、自动收起任务、上一次触发时刻。 */
  View exitConfirmBanner;Runnable exitConfirmHideRunnable;long lastExitConfirmAt;
  /** v1.22.1 预返回（用户要求"侧滑时提前出现过渡动画，没拉完就弹回"）：
   *  官方路线 = OnBackPressedDispatcher + OnBackAnimationCallback（androidx.activity 1.13）。**静态裸
   *  OnBackInvokedCallback 会禁用系统预返回动画**（v1.19.x 起就一直是这样，所以用户从没见过预览效果）。
   *  根页面禁用 callback → 系统"回桌面"预览动画自动出现（Android 14+）；应用内子页启用 → 跟手驱动
   *  pageFrame 缩放/侧移/压暗，松手未完则弹回，拉完才真正返回。
   *  动效红线同全站：只改 scale/translation/alpha，跟手阶段直接 set（零动画器），弹回走有界 VPA。 */
  androidx.activity.OnBackPressedCallback backCallback;
  float backStartProgress;boolean backPreviewActive;
  void installBackAnimationCallback(){
    backCallback=new androidx.activity.OnBackPressedCallback(false){
      @Override public void handleOnBackStarted(androidx.activity.BackEventCompat event){
        backStartProgress=event.getProgress();backPreviewActive=false;
        settlePageTransition();
      }
      @Override public void handleOnBackProgressed(androidx.activity.BackEventCompat event){
        float progress=Math.max(0f,Math.min(1f,event.getProgress()));
        View frame=pageFrame;if(frame==null||!motionEnabled())return;
        // [DFW-73] 不该跟手的两处（AI 页 / 顶级页）直接不动：见 predictiveBackPreviewAllowed()
        if(!predictiveBackPreviewAllowed())return;
        if(!backPreviewActive){backPreviewActive=true;frame.animate().cancel();frame.setLayerType(View.LAYER_TYPE_HARDWARE,null);}
        // 跟手：缩放 1→0.93、随滑动方向侧移 ≤26dp。**不做淡出**——动作条下面没有别的页面，
        // 淡出会露出黑底，正是用户说的"像碎纸条"的来源；预览期一律保持不透明。
        float scale=1f-0.07f*progress;
        frame.setScaleX(scale);frame.setScaleY(scale);
        frame.setTranslationX((event.getSwipeEdge()==androidx.activity.BackEventCompat.EDGE_LEFT?-dp(26):dp(26))*progress);
        frame.setAlpha(1f);
      }
      @Override public void handleOnBackCancelled(){
        View frame=pageFrame;
        if(frame!=null&&backPreviewActive){
          if(motionEnabled())frame.animate().scaleX(1f).scaleY(1f).translationX(0f).alpha(1f).setDuration(200)
              .setInterpolator(new android.view.animation.PathInterpolator(0.34f,0.80f,0.34f,1f))
              .withEndAction(()->frame.setLayerType(View.LAYER_TYPE_NONE,null)).start();
          else{frame.setScaleX(1f);frame.setScaleY(1f);frame.setTranslationX(0f);frame.setAlpha(1f);frame.setLayerType(View.LAYER_TYPE_NONE,null);}
        }
        backPreviewActive=false;
      }
      @Override public void handleOnBackPressed(){
        if(backPreviewActive){View frame=pageFrame;if(frame!=null){frame.animate().cancel();frame.setScaleX(1f);frame.setScaleY(1f);frame.setTranslationX(0f);frame.setAlpha(1f);frame.setLayerType(View.LAYER_TYPE_NONE,null);}backPreviewActive=false;}
        performSystemBack();
      }
    };
    getOnBackPressedDispatcher().addCallback(this,backCallback);
    syncBackCallbackEnabled();
  }
  /** isEnabled() 在 androidx 1.13 是 final，无法覆写——改为在每次页面状态变化后主动同步。
   *  [DFW-73] 现在恒为启用：顶级页也要自己吃掉这次返回（先弹"再返回一次退出软件"），
   *  只有横幅提示出现之后，第二次返回才真的退出。 */
  void syncBackCallbackEnabled(){if(backCallback==null)return;backCallback.setEnabled(canHandleBack());}
  /** [DFW-73] 预测式返回的跟手预览何时允许：
   *  - AI 内嵌页不预览（用户明确要求"AI 对话保持原有动画"）；
   *  - 顶级页不预览（返回语义只是"再返回一次退出软件"，页面本身并不离开）。 */
  /**
   * [DFW-79] 预测式返回的跟手预览：**已关闭**。
   *
   * 这是用户 2026-10-01 明确要求关掉的：
   * > "我不希望有预动画。就是说这个我就不需要有那些提前预判我返回的动画了，
   * >  因为看起来很难看很卡。"
   *
   * 历史：v1.22.1 用户**要求**过"侧滑时提前出现过渡动画，没拉完就弹回"（见上面 backCallback 的注释），
   * 所以 DFW-73 实现了跟手预览（缩放 1→0.93 + 侧移 ≤26dp）。现在用户改主意了 ——
   * 侧滑到一半整页就开始缩放位移，看起来是"页面在抖"，而且跟手期间走的是逐帧 set（零动画器），
   * 在弱机上就是"卡"。**以用户最新意见为准**：预览关掉，页面在松手前完全不动，
   * 只有真正返回时才走一次完整的弹出转场。
   *
   * 保留这个开关而不是删掉整段代码，是因为它随时可能被再次要求打开；
   * 关掉后 `handleOnBackProgressed` 会在第一行就 return，`backPreviewActive` 恒为 false，
   * `handleOnBackCancelled` 也自然什么都不做。
   */
  boolean predictiveBackPreviewAllowed(){return false;}
  /**
   * [DFW-73] 恒为 true —— 顶级页也必须由**我们自己**消费这次返回。
   *
   * 旧实现让顶级页把返回键交还给系统（"根页面禁用回调 → 系统回桌面预览动画自动出现"），
   * 但用户 2026-10-01 定的是"顶级页先提示『再返回一次退出软件』、第二次才退出"：
   * 一旦交还系统，Activity 立刻 finish，横幅根本没机会出现。
   *
   * 维护页 / 更新面板 / AI 页 / 多选态本来就都要消费，一并被这条覆盖。
   */
  boolean canHandleBack(){return true;}
  void performSystemBack(){if(maintenanceBlocking)return;if(offerSheet!=null&&offerSheet.isShowing()){offerSheet.handleBack();return;}if(SystemClock.uptimeMillis()-lastSystemBackAt<260)return;lastSystemBackAt=SystemClock.uptimeMillis();/* [DFW-73 修正] 原来这里有一句"AI 页若 Compose 还有回调就转交给它"—— 那个判据（hasEnabledCallbacks）在我们自己的
   回调恒启用之后恒为 true，于是变成**自我派发**：转交给自己 → 被开头的 260ms 节流挡回 → 净效果"返回键毫无反应"。
   现在直接删掉：Compose 的 BackHandler 后注册、优先级更高，它要消费根本轮不到我们；能走到这里就说明它已经放弃。 */if(sourceSelectionMode){exitSourceSelection();return;}if(downloadSelectionMode){exitDownloadSelection();return;}if(selectionMode){exitSelection();return;}if(pageKind==6&&!toolBackStack.isEmpty()){pageDirection=-1;popToolBack();return;}if(systemBackAction!=null){Runnable action=systemBackAction;systemBackAction=null;pageDirection=-1;action.run();return;}if(primaryDestination==1){/* [DFW-73] 软件库列表是软件库的下一层：返回回软件库，不是顶级页 */navigateHome();return;}/* [DFW-73] 走到这里 = 顶级页且没有上一层：**再返回一次退出软件**，不再一律回软件库（用户 2026-10-01 口述） */confirmExitToSoftware();}
  /* v1.22.1 预返回：onBackPressed() 覆写已删除——覆写它会退回旧的按键式返回路径，系统预返回动画不会播。
     返回统一走 androidx OnBackPressedDispatcher（backCallback，见 installBackAnimationCallback）。 */

  /**
   * [DFW-73] 顶级页判定：当前就是悬浮球六项之一，且**没有任何"上一层"可回**。
   *
   * 用户 2026-10-01 口述："所有界面都要平级；在顶级页面并且没有子页面的情况下，
   * 返回就是先提示『再返回一次退出软件』，然后再退出。"
   *
   * 注意：公告页虽然也是顶级页，但它记着"从哪来"（`systemBackAction=noticeReturnAction()`），
   * 所以不算顶级根 —— 从公告返回仍然回到进来之前那一页。
   *
   * [DFW-73 修正] **AI 页不再单独判定**。旧实现问的是
   * `getOnBackPressedDispatcher().hasEnabledCallbacks()`，而那个 API 的语义是"任意 enabled handler"——
   * 我们自己的 backCallback 现在恒启用（见 {@link #canHandleBack()}），于是它恒为 true，
   * AI 页被永远判成"不是顶级根"：横幅不弹、也不换页，**返回键彻底失效**。
   *
   * 其实正确的判据根本不用写：`OnBackPressedDispatcher` 按**后注册优先**派发，Compose 的 BackHandler
   * 是在组合期注册的（晚于 onCreate 里注册的我们），所以只要 Compose 有启用的 handler，
   * 事件根本到不了这里；能走到 `performSystemBack()`，就说明 Compose 侧已经放弃消费。
   */
  boolean atTopLevelRoot(){
    if(maintenanceBlocking)return false;
    if(offerSheet!=null&&offerSheet.isShowing())return false;
    if(sourceSelectionMode||downloadSelectionMode||selectionMode)return false;
    if(systemBackAction!=null)return false;
    if(pageKind==6&&!toolBackStack.isEmpty())return false;
    // [DFW-73] 软件库列表（primaryDestination==1，从软件库页的「更多/软件」进来）是**软件库的下一层**，
    // 不是顶级根：返回要回软件库，而不是提示退出。以前靠 performSystemBack 结尾那条
    // `if(primaryDestination>0) navigateHome()` 兜住，那条被 DFW-73 删掉后必须在这里显式认领。
    if(primaryDestination==1)return false;
    return true;
  }

  /** 顶级页的返回兜底：第一次顶部横幅提示，窗口内第二次才真的退出。 */
  void confirmExitToSoftware(){
    long now=SystemClock.uptimeMillis();
    if(lastExitConfirmAt>0L&&now-lastExitConfirmAt<=EXIT_CONFIRM_WINDOW_MS){lastExitConfirmAt=0L;hideExitConfirmBanner();finishAfterTransition();return;}
    lastExitConfirmAt=now;
    showExitConfirmBanner();
  }

  /** 「再返回一次退出软件」顶部横幅：从状态栏下方滑入，2 秒后自动收起（与二次返回窗口同长）。 */
  void showExitConfirmBanner(){
    if(host==null)return;
    if(exitConfirmBanner==null){
      LinearLayout box=new LinearLayout(this);
      box.setOrientation(LinearLayout.HORIZONTAL);
      box.setGravity(Gravity.CENTER_VERTICAL);
      GradientDrawable bg=solidShape(SET_HIGH,18);
      bg.setStroke(dp(1),BORDER);
      box.setBackground(bg);
      box.setElevation(dp(6));
      box.setPadding(dp(18),0,dp(18),0);
      TextView label=text("再返回一次退出软件",13,TEXT);
      label.setGravity(Gravity.CENTER_VERTICAL);
      box.addView(label,new LinearLayout.LayoutParams(-1,dp(46)));
      box.setContentDescription("再返回一次退出软件");
      box.setAlpha(0f);
      box.setTranslationY(-dp(72));
      box.setVisibility(View.GONE);
      exitConfirmBanner=box;
      FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,dp(46),Gravity.TOP);
      lp.setMargins(dp(16),dp(8),dp(16),0);
      host.addView(box,lp);
    }
    final View banner=exitConfirmBanner;
    if(exitConfirmHideRunnable!=null)ui.removeCallbacks(exitConfirmHideRunnable);
    banner.animate().cancel();
    banner.setVisibility(View.VISIBLE);
    banner.bringToFront();
    if(motionEnabled())banner.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(new android.view.animation.PathInterpolator(0.05f,0.7f,0.1f,1f)).start();
    else{banner.setAlpha(1f);banner.setTranslationY(0f);}
    exitConfirmHideRunnable=()->{View v=exitConfirmBanner;if(v==null)return;v.animate().cancel();if(motionEnabled())v.animate().alpha(0f).translationY(-dp(72)).setDuration(150).setInterpolator(standardEase()).withEndAction(()->{if(exitConfirmBanner==v)v.setVisibility(View.GONE);}).start();else{v.setAlpha(0f);v.setTranslationY(-dp(72));v.setVisibility(View.GONE);}};
    ui.postDelayed(exitConfirmHideRunnable,EXIT_CONFIRM_WINDOW_MS);
  }

  void hideExitConfirmBanner(){
    if(exitConfirmHideRunnable!=null)ui.removeCallbacks(exitConfirmHideRunnable);
    View banner=exitConfirmBanner;
    if(banner==null)return;
    banner.animate().cancel();
    banner.setAlpha(0f);
    banner.setTranslationY(-dp(72));
    banner.setVisibility(View.GONE);
  }

  String downloadHistoryJson(){return getSharedPreferences("download_history",MODE_PRIVATE).getString("items","[]");}
  void loadDownloadHistory(){
    boolean normalized=false;String raw=downloadHistoryJson();
    try{
      org.json.JSONArray items=new org.json.JSONArray(raw);
      for(int i=0;i<items.length();i++){
        org.json.JSONObject value=items.getJSONObject(i);DownloadEntry entry=new DownloadEntry();
        entry.entryType=value.optString("type",ENTRY_DOWNLOAD);entry.source=DownloadSourcePolicy.normalize(value.optString("source",null));entry.batchId=value.optString("batchId");entry.batchTitle=value.optString("batchTitle");entry.name=value.optString("name");entry.state=value.optString("state",DOWNLOAD_FAILED);entry.error=value.optString("error");
        if("premium_save".equals(entry.entryType))continue;// v1.23 优享转存移除：旧版「保存中/已保存」记录直接丢弃，不再展示或恢复
        if(entry.state.startsWith("失败：")){entry.error=entry.state.substring(3);entry.state=DOWNLOAD_FAILED;normalized=true;}
        if(entry.state.equals("未完成")||entry.state.equals("等待下载")||entry.state.equals(DOWNLOAD_WAITING)||entry.state.equals(DOWNLOAD_RESOLVING)||entry.state.equals(DOWNLOAD_RUNNING)||entry.state.equals("8 线程下载中")){entry.state=DOWNLOAD_FAILED;if(entry.error.isEmpty())entry.error="下载中断，点击继续";normalized=true;}
        entry.percent=value.optInt("percent");
        entry.iconUrl=value.optString("icon");entry.shareUrl=value.optString("share");entry.password=value.optString("password");entry.uriString=value.optString("uri");entry.parentUriString=value.optString("parent");entry.sourceSizeText=value.optString("sourceSize");entry.directUrl=value.optString("direct");entry.expectedUpdateVersion=value.optString("expectedUpdateVersion");entry.createdAt=value.optLong("created");entry.startedAt=value.optLong("started");entry.completedAt=value.optLong("completed");entry.downloadedBytes=value.optLong("doneBytes",value.optLong("downloaded"));entry.totalBytes=value.optLong("totalBytes",value.optLong("total"));entry.verifiedTotalBytes=value.optLong("verifiedTotalBytes");entry.resolvedAt=value.optLong("resolvedAt",value.optLong("resolved"));entry.speedBps=value.optLong("speed");entry.etaSeconds=value.optLong("eta",-1);
        if(entry.totalBytes<=0)entry.totalBytes=parseSize(entry.sourceSizeText);if(entry.state.equals(DOWNLOAD_COMPLETED)&&entry.downloadedBytes<=0)entry.downloadedBytes=entry.totalBytes;if(entry.totalBytes>0)entry.percent=(int)Math.min(100,entry.downloadedBytes*100/entry.totalBytes);if(!entry.uriString.isEmpty())entry.target=Uri.parse(entry.uriString);if(DOWNLOAD_SOURCE_LANZOU.equals(entry.source))directResolver.remember(entry.shareUrl,entry.directUrl,entry.resolvedAt);
        entry.savedState=entry.state;entry.uiState=entry.state;entry.savedPercent=entry.percent;entry.savedDownloadedBytes=entry.downloadedBytes;entry.savedVerifiedTotalBytes=entry.verifiedTotalBytes;entry.lastPersistAt=System.currentTimeMillis();downloadEntries.add(entry);
      }
    }catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}
    if(normalized)persistDownloadHistory();
  }
  void applySystemColors(){// 主题引擎唯一色源:所有中性色带主题色温(品牌色温贯穿),纯黑纯灰会让 UI 显得廉价
    darkMode=true;ThemeEngine.Design d=ThemeEngine.active(this);
    BG=d.bg;SURFACE=d.surface;SURFACE2=d.surface2;PRIMARY=d.primary;PRIMARY_HI=d.primaryHi;PRIMARY_LO=d.primaryLo;SECONDARY=d.secondary;
    TEXT=d.text;MUTED=d.muted;DIV=d.border;BORDER=d.border;ERROR_TOKEN=d.error;Window window=getWindow();if(Build.VERSION.SDK_INT>=30)window.setDecorFitsSystemWindows(false);window.setStatusBarColor(BG);window.setNavigationBarColor(BG);int flags=window.getDecorView().getSystemUiVisibility();flags=flags&~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    // v1.4.0「高级苹果」为浅色分组体系（灰底白卡）：状态栏/导航栏切换深色字，否则 #F2F2F7 浅底配浅字不可读
    boolean lightChrome=d.bgIsLight&&Build.VERSION.SDK_INT>=23;
    if(lightChrome)flags|=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    window.getDecorView().setSystemUiVisibility(flags);if(host!=null)host.setBackgroundColor(BG);}
  final class SearchDragBar extends View{
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);final ScrollView target;final View body;boolean dragging;Runnable hideRun;
    SearchDragBar(ScrollView target,View body,String description){super(MainActivity.this);this.target=target;this.body=body;setContentDescription(description);setClickable(true);setVisibility(View.GONE);// v1.6.1（R-B3）：初始不可见，判定可滚动后才出现
    }
    int contentHeight(){return body==null?0:body.getHeight();}
    float thumbHeight(){int view=getHeight(),content=contentHeight();return content<=view?view:Math.max(dp(48),Math.min(dp(240),view*(float)view/content));}// R-B3：最小 48dp、最大半屏
    float thumbTop(){int view=getHeight(),content=contentHeight(),range=Math.max(1,content-view);return (view-thumbHeight())*target.getScrollY()/range;}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float top=thumbTop(),height=thumbHeight(),w=dragging?dp(8):dp(6),left=getWidth()-dp(2)-w,right=getWidth()-dp(2);// R-B3：静止 6dp 半透明主色，拖动 8dp 不透明，距右缘 2dp，胶囊
      paint.setColor(dragging?PRIMARY:ThemeEngine.tint(PRIMARY,115));canvas.drawRoundRect(left,top,right,top+height,w/2,w/2,paint);}
    void bumpActivity(){if(getVisibility()!=View.VISIBLE)return;setAlpha(1f);invalidate();// v1.19.5：补重绘——此前只重置淡出计时器，纯手指滚动时 thumbTop() 变了但不重绘，紫色胶囊停在原地
removeCallbacks(hideRun);hideRun=()->{if(!dragging)animate().alpha(0f).setDuration(250).setInterpolator(standardEase()).start();};postDelayed(hideRun,1500);}// R-B3：滚动停止 1500ms 后 250ms 淡出
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);bumpActivity();}
    void dragTo(float y){int view=getHeight(),content=contentHeight();if(content<=view)return;float track=Math.max(1,view-thumbHeight());float top=Math.max(0,Math.min(track,y-thumbHeight()/2));target.scrollTo(0,Math.round((content-view)*top/track));invalidate();}
    @Override public boolean onTouchEvent(android.view.MotionEvent event){if(event.getAction()==android.view.MotionEvent.ACTION_DOWN){dragging=true;setAlpha(1f);removeCallbacks(hideRun);getParent().requestDisallowInterceptTouchEvent(true);dragTo(event.getY());invalidate();return true;}if(event.getAction()==android.view.MotionEvent.ACTION_MOVE&&dragging){dragTo(event.getY());return true;}if(event.getAction()==android.view.MotionEvent.ACTION_UP||event.getAction()==android.view.MotionEvent.ACTION_CANCEL){dragging=false;invalidate();removeCallbacks(hideRun);hideRun=()->{if(!dragging)animate().alpha(0f).setDuration(250).setInterpolator(standardEase()).start();};postDelayed(hideRun,1200);getParent().requestDisallowInterceptTouchEvent(false);return true;}return super.onTouchEvent(event);}
  }
  final class FolderPullScrollView extends ScrollView{
    final int touchSlop;float lastY,bottomDrag,topDrag,pullDistance;boolean pulling,refreshPulling;
    FolderPullScrollView(Context context){super(context);touchSlop=ViewConfiguration.get(context).getScaledTouchSlop();setOverScrollMode(View.OVER_SCROLL_NEVER);setVerticalScrollBarEnabled(false);setBackgroundColor(BG);}// v1.19.5：关原生滚动条,右侧指示统一交给 SearchDragBar
    @Override public boolean dispatchTouchEvent(MotionEvent event){int action=event.getActionMasked();if(action==MotionEvent.ACTION_DOWN){animate().cancel();if(!folderLoadingMore&&!folderRefreshing&&getTranslationY()!=0f){setTranslationY(0f);pullDistance=0f;resetFolderPullIndicator();}lastY=event.getY();bottomDrag=topDrag=0f;pulling=refreshPulling=false;}else if(action==MotionEvent.ACTION_MOVE){float y=event.getY(),delta=lastY-y;lastY=y;if(refreshPulling){float next=pullDistance-delta*(delta<=0f?.52f:1f);setRefreshPullDistance(Math.max(0f,Math.min(dp(FOLDER_PULL_MAX_DP),next)));}else if(pulling){float next=pullDistance+delta*(delta>=0f?.52f:1f);setPullDistance(Math.max(0f,Math.min(dp(FOLDER_PULL_MAX_DP),next)));}else if(!folderLoadingMore&&!folderRefreshing&&folderRefreshAvailable()&&!canScrollVertically(-1)&&delta<0f){topDrag+=-delta;if(topDrag>touchSlop){refreshPulling=true;setRefreshPullDistance(Math.min(dp(FOLDER_PULL_MAX_DP),(topDrag-touchSlop)*.52f));ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(true);}}else if(!folderLoadingMore&&!folderRefreshing&&folderPullAvailable()&&!canScrollVertically(1)&&delta>0f){bottomDrag+=delta;if(bottomDrag>touchSlop){pulling=true;setPullDistance(Math.min(dp(FOLDER_PULL_MAX_DP),(bottomDrag-touchSlop)*.52f));ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(true);}}else{if(pulling||refreshPulling)releaseFolderPull(false);if(delta<=0f)bottomDrag=0f;if(delta>=0f)topDrag=0f;}}boolean wasPulling=pulling||refreshPulling,endAttempt=!wasPulling&&!folderLoadingMore&&!folderRefreshing&&!folderPullAvailable()&&bottomDrag>touchSlop,handled=super.dispatchTouchEvent(event);if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&wasPulling){releaseFolderPull(action==MotionEvent.ACTION_UP);return true;}if((action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL)&&endAttempt&&action==MotionEvent.ACTION_UP)showFolderEndNoticeOnce();if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){bottomDrag=topDrag=0f;}return handled||wasPulling;}
    @Override protected void onScrollChanged(int x,int y,int oldX,int oldY){super.onScrollChanged(x,y,oldX,oldY);if(sessionAutoExpand&&y>=oldY)post(MainActivity.this::maybeAutoExpand);}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){return pulling||refreshPulling||super.onInterceptTouchEvent(event);}
    @Override public boolean onTouchEvent(MotionEvent event){return pulling||refreshPulling||super.onTouchEvent(event);}
    void setPullDistance(float value){pullDistance=value;setTranslationY(-value);updateFolderPull(value);}
    void setRefreshPullDistance(float value){pullDistance=value;setTranslationY(value);updateFolderRefreshPull(value);}
    void animatePullTo(float value,boolean animated){animatePullOffsetTo(-value,animated);}
    void animateRefreshTo(float value,boolean animated){animatePullOffsetTo(value,animated);}
    /**
   * [DFW-54] 松手后的回弹/收起。
   *
   * 旧版用 180ms + `DecelerateInterpolator` —— 那是 M2 遗留，全站只有这里还在用；
   * 站内其它动效一律 `PathInterpolator(0.2,0,0,1)`（= M3 emphasized 的减速段）。
   * 时长取 200ms：这是 androidx `SwipeRefreshLayout` 的官方松手时序
   * （`ANIMATE_TO_START_DURATION` 与 `ANIMATE_TO_TRIGGER_DURATION` 都是 200ms），
   * 比 180ms 略长但曲线更"稳"，不会显得急停。
   */
void animatePullOffsetTo(float value,boolean animated){animate().cancel();pullDistance=Math.abs(value);if(!animated||!motionEnabled()){setTranslationY(value);return;}animate().translationY(value).setDuration(200).setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f)).start();}
    void releaseFolderPull(boolean commit){boolean refresh=refreshPulling,armed=commit&&pullDistance>=dp(FOLDER_PULL_THRESHOLD_DP)&&(refresh?folderRefreshAvailable():folderPullAvailable());pulling=refreshPulling=false;bottomDrag=topDrag=0f;ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(false);if(armed){if(refresh){if(folderPullLabel!=null)folderPullLabel.announceForAccessibility("开始刷新目录");refreshCurrentFolder();}else{if(folderPullLabel!=null)folderPullLabel.announceForAccessibility("开始加载下一页");expandMore();}}else collapseFolderPull(true);}
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);if(folderRefreshAvailable()&&!folderRefreshing&&!canScrollVertically(-1))info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,"刷新目录"));if(folderPullAvailable()&&!folderLoadingMore&&!canScrollVertically(1))info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD,"加载下一页"));}
    @Override public boolean performAccessibilityAction(int action,Bundle arguments){if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD&&!canScrollVertically(-1)&&folderRefreshAvailable()&&!folderRefreshing){holdFolderRefresh();if(folderPullLabel!=null)folderPullLabel.announceForAccessibility("开始刷新目录");refreshCurrentFolder();return true;}if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD&&!canScrollVertically(1)&&folderPullAvailable()&&!folderLoadingMore){holdFolderPull();if(folderPullLabel!=null)folderPullLabel.announceForAccessibility("开始加载下一页");expandMore();return true;}return super.performAccessibilityAction(action,arguments);}
  }
  final class SearchCategoryPicker{
    final List<String> categories;final LinearLayout trigger;final TextView value;final ImageView arrow;final ImageButton go;final java.util.function.Consumer<String> changed;final Runnable searchAction;String selected;PopupWindow popup;
    SearchCategoryPicker(List<String> categories,String current,java.util.function.Consumer<String> changed,Runnable searchAction){this.categories=new ArrayList<>(categories);this.selected=this.categories.contains(current)?current:this.categories.get(0);this.changed=changed;this.searchAction=searchAction;trigger=new LinearLayout(MainActivity.this);trigger.setGravity(Gravity.CENTER_VERTICAL);trigger.setPadding(dp(12),0,dp(3),0);trigger.setClickable(true);trigger.setFocusable(true);trigger.setMinimumHeight(dp(54));trigger.setBackground(categoryRipple(triggerShape(false)));value=text(this.selected,14,TEXT);value.setSingleLine(true);value.setEllipsize(android.text.TextUtils.TruncateAt.END);value.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);value.setPadding(0,0,dp(2),0);// v1.6.1（R-B2）：分类文字贴右，紧挨 ▾ 与 🔍
trigger.addView(value,new LinearLayout.LayoutParams(0,dp(54),1));arrow=new ImageView(MainActivity.this);arrow.setImageResource(R.drawable.ic_expand);arrow.setColorFilter(PRIMARY);arrow.setPadding(dp(8),dp(8),dp(8),dp(8));arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);trigger.addView(arrow,new LinearLayout.LayoutParams(dp(36),dp(48)));go=iconButton(R.drawable.ic_search,"搜索");go.setOnClickListener(v->{if(this.searchAction!=null)this.searchAction.run();});trigger.addView(go,new LinearLayout.LayoutParams(dp(48),dp(48)));trigger.setOnClickListener(v->toggle());updateTriggerSemantics(false);}
    View view(){return trigger;}
    void fitWidth(int width){// v1.6.3 修「搜索钮变小/全部消失」：文字区优先保底 40dp（「全部」两字约 30dp），箭头 28dp、搜索钮恒 44dp 不再压缩
      int available=Math.max(dp(2),width-dp(12));int goWidth=Math.min(dp(44),Math.max(dp(40),available-dp(76)));int arrowWidth=Math.min(dp(28),Math.max(dp(22),available-goWidth-dp(40)));ViewGroup.LayoutParams arrowParams=arrow.getLayoutParams(),goParams=go.getLayoutParams();arrowParams.width=arrowWidth;goParams.width=goWidth;arrow.setLayoutParams(arrowParams);go.setLayoutParams(goParams);}
    String selected(){return selected;}
    Drawable categoryRipple(Drawable content){return new RippleDrawable(android.content.res.ColorStateList.valueOf(ThemeEngine.tint(PRIMARY,36)),content,null);}
    GradientDrawable categoryShape(int color,int topRadius,int bottomRadius,boolean stroke){GradientDrawable surface=new GradientDrawable();surface.setColor(color);float top=dp(topRadius),bottom=dp(bottomRadius);surface.setCornerRadii(new float[]{top,top,top,top,bottom,bottom,bottom,bottom});if(stroke)surface.setStroke(dp(1),DIV);return surface;}
    GradientDrawable triggerShape(boolean expanded){GradientDrawable surface=new GradientDrawable();surface.setColor(expanded?SURFACE:Color.TRANSPARENT);float left=dp(16),right=dp(26),bottom=expanded?0:right;surface.setCornerRadii(new float[]{left,left,right,right,bottom,bottom,expanded?0:left,expanded?0:left});if(expanded)surface.setStroke(dp(1),DIV);return surface;}
    void updateTriggerSemantics(boolean expanded){trigger.setContentDescription("源分类，当前 "+selected+"，"+(expanded?"已展开":"已收起")+"，点击"+(expanded?"收起":"展开"));trigger.setSelected(expanded);}
    void toggle(){if(popup!=null&&popup.isShowing())popup.dismiss();else show();}
    void show(){
      if(trigger.getWindowToken()==null)return;
      LinearLayout options=new LinearLayout(MainActivity.this);options.setOrientation(LinearLayout.VERTICAL);options.setPadding(dp(6),dp(6),dp(6),dp(6));options.setBackground(categoryShape(SURFACE,0,18,true));
      int selectedFill=ThemeEngine.selectedFill(MainActivity.this);
      for(String name:categories){boolean active=name.equals(selected);TextView option=text(name,14,active?PRIMARY:TEXT);option.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);option.setPadding(dp(14),0,dp(14),0);option.setClickable(true);option.setFocusable(true);option.setSelected(active);option.setTypeface(active?AppFonts.bold(MainActivity.this):AppFonts.normal(MainActivity.this));option.setContentDescription("源分类 "+name+(active?"，已选择":""));option.setBackground(categoryRipple(solidShape(active?selectedFill:SURFACE,12)));option.setOnClickListener(v->select(name));options.addView(option,new LinearLayout.LayoutParams(-1,dp(48)));}
      ScrollView scroll=new ScrollView(MainActivity.this);scroll.setVerticalScrollBarEnabled(categories.size()>5);scroll.setFillViewport(true);scroll.addView(options,new ScrollView.LayoutParams(-1,-2));int width=trigger.getWidth();if(width<=0)return;int height=Math.min(dp(246),dp(12)+categories.size()*dp(48));PopupWindow next=new PopupWindow(scroll,width,height,true);popup=next;next.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));next.setOutsideTouchable(true);next.setClippingEnabled(true);next.setElevation(dp(10));next.setOnDismissListener(()->{if(popup==next)popup=null;setExpanded(false);});setExpanded(true);next.showAsDropDown(trigger,0,-dp(1));if(motionEnabled()){scroll.setAlpha(0f);scroll.setTranslationY(-dp(6));scroll.animate().alpha(1f).translationY(0f).setDuration(150).setInterpolator(standardEase()).start();}}
    void select(String name){if(!categories.contains(name))return;selected=name;value.setText(name);updateTriggerSemantics(true);trigger.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);if(changed!=null)changed.accept(name);if(popup!=null)popup.dismiss();}
    void setExpanded(boolean expanded){trigger.setBackground(categoryRipple(triggerShape(expanded)));arrow.animate().cancel();if(motionEnabled())arrow.animate().rotation(expanded?180f:0f).setDuration(150).setInterpolator(standardEase()).start();else arrow.setRotation(expanded?180f:0f);updateTriggerSemantics(expanded);}
    void dismiss(){if(popup!=null)popup.dismiss();}
  }
  void persistDownloadHistory(DownloadEntry changed){long now=System.currentTimeMillis();if(changed!=null&&changed.state.equals(changed.savedState)&&changed.verifiedTotalBytes==changed.savedVerifiedTotalBytes&&now-changed.lastPersistAt<DOWNLOAD_PERSIST_INTERVAL_MS&&Math.abs(changed.downloadedBytes-changed.savedDownloadedBytes)<1048576)return;if(changed!=null){changed.savedPercent=changed.percent;changed.savedState=changed.state;changed.savedDownloadedBytes=changed.downloadedBytes;changed.savedVerifiedTotalBytes=changed.verifiedTotalBytes;changed.lastPersistAt=now;}persistDownloadHistory();}
  // DFWX-STAB-001（修审计 H-P1-1）：历史持久化全部委托 DownloadHistoryStore——
  // debounce 合并、后台线程落盘、flush 不在主线程等磁盘。closed 后不再产生写。
  void persistDownloadHistory(){downloadHistory.markChanged();}
  void writeDownloadHistoryNow(){downloadHistory.writeOnce();}
  void flushDownloadHistoryNow(){downloadHistory.flushAndClose();}
  /** 序列化当前下载历史（download-history 后台线程内执行；synchronized(entry) 防止单条中途变形）。 */
  String serializeDownloadHistory(){try{org.json.JSONArray items=new org.json.JSONArray();int start=Math.max(0,downloadEntries.size()-100);for(int i=start;i<downloadEntries.size();i++){DownloadEntry entry=downloadEntries.get(i);synchronized(entry){items.put(new org.json.JSONObject().put("type",entry.entryType).put("source",entry.source).put("batchId",entry.batchId).put("batchTitle",entry.batchTitle).put("name",entry.name).put("state",entry.state).put("error",entry.error).put("percent",entry.percent).put("icon",entry.iconUrl).put("share",entry.shareUrl).put("password",entry.password).put("uri",entry.uriString).put("parent",entry.parentUriString).put("sourceSize",entry.sourceSizeText).put("expectedUpdateVersion",entry.expectedUpdateVersion).put("created",entry.createdAt).put("started",entry.startedAt).put("completed",entry.completedAt).put("doneBytes",entry.downloadedBytes).put("totalBytes",entry.totalBytes).put("verifiedTotalBytes",entry.verifiedTotalBytes).put("direct",entry.directUrl).put("resolvedAt",entry.resolvedAt).put("speed",entry.speedBps).put("eta",entry.etaSeconds));}}return items.toString();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);return null;}}
  public int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
  /**
   * DFW-13（适老化）：**跟随系统字体的高度换算**。
   *
   * 问题：sp 文字放进固定 dp 容器时，系统字体调大后文字变大、容器不变，于是被裁切/挤压
   * （例如条目卡片把 15sp、最多 2 行的标题塞进 `dp(48)` 的盒子）。
   * 解决：凡是"为了装文字而定的高度"都改用本方法，让它随 fontScale 等比放大——
   * 放大多少倍字体，就放大多少倍盒子，比例关系不变，因此不会裁切。
   *
   * 只用于**文字容器**；间距、图标、圆角、描边等与文字无关的尺寸仍用 dp()，
   * 否则界面会被整体拉肿（项目规矩：不为统一视觉到处硬改同一个数值）。
   */
  public int dpText(int v){
    float scale=1f;
    try{scale=getResources().getConfiguration().fontScale;}catch(Exception ignored){android.util.Log.w("MainActivity","fontScale unavailable: "+ignored.getMessage(),ignored);}
    if(!(scale>0.1f)||scale>4f)scale=1f;
    // 上限 1.8 倍：极端 fontScale 下不至于把一屏塞不满两行，仍能滚动查看。
    return (int)(v*Math.min(scale,1.8f)*getResources().getDisplayMetrics().density+.5f);
  } TextView text(String s,int sp,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);v.setFontFeatureSettings("kern");v.setTypeface(AppFonts.normal(this));return v;}

  /** v1.19.7 字重角色（wear-ui-system.md §6）：标题靠字重不靠放大。weight≥650→BOLD，≥500→sans-serif-medium，0 不动。 */
  TextView text(String s,int sp,int color,int weight){TextView v=text(s,sp,color);v.setTypeface(weight>=650?AppFonts.bold(this):weight>=500?AppFonts.medium(this):AppFonts.normal(this));return v;}
  /** v1.19.7 形状 Token 归档（wear-ui-system.md §4）：radius≥24→26(Panel)，14–23→20(Card)，5–13→8(Micro 内嵌小块)，<5 原样(指示条)。历史 14 种圆角经此收敛为四档+指示条，调用点零改动。 */
  public GradientDrawable solidShape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius>=24?26:radius>=14?20:radius>=5?8:radius));return g;}
  String friendlyError(Throwable error){
    /*
     * [DFW-101] 全站**唯一**的"把异常变成给人看的话"的出口，47 个调用点都从这里过。
     *
     * 所以这一行等于给"任何会显示给用户的错误"自动装上了留证 ——
     * 不需要在任何一个具体 bug 旁边插埋点，也不会漏掉还没被发现的那些。
     * 这就是"不做针对性埋点"：给**通道**打点，不是给**已知故障**打点。
     */
    DfLog.failure("ui","user-visible-error",error);
    String raw=error==null||error.getMessage()==null?"":error.getMessage().trim(),lower=raw.toLowerCase(Locale.ROOT);if(raw.contains("分享已取消"))return"分享已取消";if(raw.contains("不受信任")||raw.contains("跳转"))return"该源已失效或跳转异常";if(raw.contains("超时")||lower.contains("timeout")||lower.contains("timed out"))return"连接超时，请稍后重试";if(raw.contains("密码"))return raw;if(raw.contains("过快")||raw.contains("频率")||raw.contains("受限"))return"请求频率受限，请稍后重试";if(raw.contains("ACW"))return"蓝奏验证暂不可用，请稍后重试";if(lower.contains("socket")||lower.contains("connect")||lower.contains("host")||raw.contains("网络"))return"网络连接异常，请稍后重试";if(raw.startsWith("请输入")||raw.startsWith("无法")||raw.startsWith("未获得")||raw.startsWith("没有"))return raw;return"操作失败，请稍后重试";}
  GradientDrawable shape(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));g.setStroke(dp(1),BORDER);return g;}
  public Drawable filterRipple(Drawable content){
    return new RippleDrawable(android.content.res.ColorStateList.valueOf(ThemeEngine.tint(PRIMARY,42)),content,null);
  }
  /** v1.5.0 苹果按压缩放（研究 R2 规格表#2）：按下缩小（0ms），松手 220ms 弱过冲回弹 (0.2,0.9,0.3,1.05)。
      v1.19.7 起全主题启用（wear-ui-system.md §3：主反馈=scale 形变+回位，ripple 降辅助），统一按下 0.96。 */
  /* 踩坑(v1.19.7 真机卡死根因)：非苹果主题曾用 dynamicanimation 弹簧回位——
     ①缓存在 view tag 的弹簧在下次 ACTION_DOWN 不会被取消（view.animate().cancel() 只管 VPA），
       连点时旧弹簧把 0.96 按压态直接拽回 1.0，按钮"看起来点不动"；
     ②stiffness 350 软弹簧 settle 无上界，弱手表每次点击近 0.5s 逐帧重绘，连点即渲染风暴→ANR 闪退；
     ③弹簧与 VPA 两套系统同时写同一视图的 SCALE_X/Y 互相打架。
     v1.19.8 删除弹簧实现：全主题统一 v1.5.0 已真机验证数月的 VPA 曲线，时长有界必 settle。 */
  /* v1.22.3 统一按压弹簧（用户反馈 v1.22.2 仍僵硬"点到石头上"）：wear-ui-system.md §2/§3 配方，
     dynamicanimation 1.1.0（用户 2026-09-22 批准 a 类；v1.19.8 删弹簧时依赖行被误删，孤儿注释留存至今）。
     DOWN=SPATIAL_FAST 800/.70 压至 0.96；UP/CANCEL=SPATIAL_DEFAULT 350/.75 由受压态弹回平衡（规范禁三段人为 bounce）。
     同一 view 复用 SpringAnimation 实例，animateToFinalPosition 重定目标速度连续——yan-apple-design《Designing Fluid
     Interfaces》：VPA 定时动画中断即速度硬切（brick wall），弹簧天然可中断且继承速度；快速点按/长按松手/反复按压全平滑。
     detach 时清缓存防 view 树滞留（WeakHashMap value 持 view 强引用，靠 attach listener 摘除）。 */
  final java.util.WeakHashMap<View,SpringAnimation[]> pressSprings=new java.util.WeakHashMap<>();
  /** [DFW-24] 圆按钮/圆形芯片的按下档（长条行与胶囊仍用 0.96）。 */
  static final float PRESS_SCALE_ROUND=0.94f;
  /** [DFW-24] 长条行/胶囊的按下档。 */
  static final float PRESS_SCALE_PILL=0.96f;
  public void applePressScale(View v){applePressScale(v,PRESS_SCALE_PILL);}
  public void applePressScale(View v,float pressedScale){
    if(v==null)return;
    v.setOnTouchListener((view,event)->{
      if(!motionEnabled())return false;
      android.graphics.drawable.Drawable bg=view.getBackground();
      if(event.getActionMasked()==android.view.MotionEvent.ACTION_DOWN){
        SpringAnimation[] springs=pressSprings.get(view);
        if(springs==null){
          springs=new SpringAnimation[]{new SpringAnimation(view,DynamicAnimation.SCALE_X,1f),new SpringAnimation(view,DynamicAnimation.SCALE_Y,1f)};
          view.addOnAttachStateChangeListener(new android.view.View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View attached){}
            @Override public void onViewDetachedFromWindow(View detached){SpringAnimation[] stale=pressSprings.remove(detached);if(stale!=null)for(SpringAnimation staleSpring:stale)staleSpring.cancel();}
          });
          pressSprings.put(view,springs);
        }
        for(SpringAnimation spring:springs){spring.getSpring().setStiffness(800f).setDampingRatio(0.70f);spring.animateToFinalPosition(pressedScale);}
        // v1.21.0 按压提亮（06/02 配方）：白 8% SRC_ATOP 只作用于背景不透明像素，透明底不加灰罩；UP/CANCEL 即清。
        if(bg!=null)bg.setColorFilter(0x14FFFFFF,android.graphics.PorterDuff.Mode.SRC_ATOP);
      }else if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP||event.getActionMasked()==android.view.MotionEvent.ACTION_CANCEL){
        SpringAnimation[] springs=pressSprings.get(view);
        if(springs!=null)for(SpringAnimation spring:springs){spring.getSpring().setStiffness(350f).setDampingRatio(0.75f);spring.animateToFinalPosition(1f);}
        if(bg!=null)bg.clearColorFilter();
        /* [DFW-24] 规范 §3：按下的确认反馈 = 一次轻触觉。之前全仓只有悬浮球三处有，
           主 UI 一次都没有 —— 用户点下去只有画面变化，手上没有反馈。 */
        if(event.getActionMasked()==android.view.MotionEvent.ACTION_UP)view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
      }
      return false;
    });
  }
  void resetButtonChrome(Button b){b.setAllCaps(false);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));b.setPadding(dp(12),0,dp(12),0);b.setStateListAnimator(null);applePressScale(b);}
  void styleDialogButton(Button b,boolean emphasized){if(b==null)return;resetButtonChrome(b);b.setTextSize(13);b.setTextColor(emphasized?PRIMARY:MUTED);b.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));b.setGravity(Gravity.CENTER);}
  void animateIn(View view,int order){float scale=1f;try{scale=Settings.Global.getFloat(getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f);}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}view.animate().cancel();view.setTranslationY(0);if(scale==0f){view.setAlpha(1f);return;}view.setAlpha(0f);view.animate().alpha(1f).setDuration(150).setInterpolator(standardEase()).start();}
  public ImageButton iconButton(int icon,String description){ImageButton b=new ImageButton(this);b.setImageResource(icon);b.setColorFilter(PRIMARY);b.setContentDescription(description);b.setScaleType(ImageView.ScaleType.CENTER_INSIDE);b.setPadding(dp(10),dp(10),dp(10),dp(10));b.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));b.setMinimumWidth(dp(44));b.setMinimumHeight(dp(44));b.setFocusable(true);applePressScale(b,PRESS_SCALE_ROUND);return b;}
  Button button(String label){Button b=new Button(this);b.setText(label);b.setTextColor(PRIMARY);b.setTextSize(13);resetButtonChrome(b);b.setBackground(filterRipple(shape(SURFACE,12)));return b;}
  Button toolbarTextButton(String label){Button b=new Button(this);b.setText(label);b.setTextColor(PRIMARY);b.setTextSize(11);resetButtonChrome(b);b.setPadding(dp(8),0,dp(8),0);b.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));return b;}
  /* T5-S v7 设置页局部色板（对齐 docs/plan/20260926-long-term-execution/cards/T5-S-mockup-v7.html 的 CSS 变量；legacy 暗色唯一主题下使用，不改 ThemeEngine 真源） */
  static final int SET_LOW=0xFF0F0E13,SET_HIGH=0xFF17151F,SET_STROKE=0x1AFFFFFF,SET_STROKE2=0x0FFFFFFF;
  static final int SET_PRIMARY_HI=0xFFC4B5FD,SET_SEL=0x33A78BFA,SET_SEL_STROKE=0x47A78BFA,SET_T2=0xA8F2F0F7,SET_T3=0x66F2F0F7;
  Drawable settingsPress(){return new RippleDrawable(android.content.res.ColorStateList.valueOf(ThemeEngine.tint(0xFFFFFFFF,26)),new ColorDrawable(Color.TRANSPARENT),null);}// v7 §12:设置页按压=白 10% 预混,不用全 app 的紫 ripple
  LumaSlider lumaSlider(int max,int value,String desc,LumaSlider.OnChange change){LumaSlider bar=new LumaSlider(this);bar.setMax(max);bar.setProgressValue(value,false);if(desc!=null)bar.setContentDescription(desc);bar.setOnChangeListener(change);return bar;}
  int settingsRowHeight(){return dpText(56);}
  LinearLayout settingsRowShell(){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),0,dp(8),0);row.setMinimumHeight(settingsRowHeight());return row;}
  LinearLayout.LayoutParams settingsIconBox(){return new LinearLayout.LayoutParams(dp(28),dp(28));}
  ImageView settingsLeadingIcon(int icon){ImageView image=new ImageView(this);image.setImageResource(icon);image.setColorFilter(PRIMARY);image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);image.setPadding(dp(2),dp(2),dp(2),dp(2));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return image;}
  TextView settingsRowTitle(String label){TextView title=text(label,15,TEXT,560);title.setGravity(Gravity.CENTER_VERTICAL);title.setPadding(dp(12),0,dp(8),0);title.setMaxLines(2);return title;}
  View settingsAction(int icon,String label,View.OnClickListener click){LinearLayout row=settingsRowShell();row.setClickable(true);row.setFocusable(true);row.setBackground(settingsPress());row.setContentDescription(label);row.setOnClickListener(click);applePressScale(row);ImageView image=settingsLeadingIcon(icon);row.addView(image,settingsIconBox());TextView title=settingsRowTitle(label);row.addView(title,new LinearLayout.LayoutParams(0,-1,1));return row;}
  /**
   * [DFW-91] 带**右侧说明文字**的设置行（用于「检查更新」右侧显示当前版本号）。
   *
   * 用户原话：「你就找一个地方放版本号吧，在检查更新四个字右侧放着版本号。
   * 然后关于东方无限底部的和其他地方显示版本号的地方，你都给我删除，优化掉。
   * 不然的话，你每次更新都得改一堆地方。」
   */
  View settingsAction(int icon,String label,String trailing,View.OnClickListener click){
    View row=settingsAction(icon,label,click);
    if(trailing==null||trailing.trim().isEmpty())return row;
    TextView value=text(trailing.trim(),13,MUTED);
    value.setGravity(Gravity.CENTER_VERTICAL);
    value.setMaxLines(1);
    value.setPadding(dp(8),0,dp(6),0);
    if(row instanceof LinearLayout)((LinearLayout)row).addView(value,new LinearLayout.LayoutParams(-2,-1));
    return row;
  }
  TextView recoveryOption(String label,Runnable action){TextView option=text(label,12,TEXT);option.setGravity(Gravity.CENTER);option.setPadding(dp(10),0,dp(10),0);option.setMinWidth(dp(56));option.setClickable(true);option.setFocusable(true);option.setBackground(settingsPress());option.setContentDescription(label);option.setOnClickListener(v->action.run());applePressScale(option);return option;}
  View recoveryGroup(){LinearLayout group=settingsRowShell();ImageView icon=settingsLeadingIcon(R.drawable.ic_refresh);group.addView(icon,settingsIconBox());TextView title=settingsRowTitle("恢复");group.addView(title,new LinearLayout.LayoutParams(0,-1,1));LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);GradientDrawable boundary=solidShape(SET_HIGH,16);boundary.setStroke(dp(1),SET_STROKE);actions.setBackground(boundary);actions.setClipToOutline(true);String[] labels=new String[]{"所有列表","默认设置"};Runnable[] callbacks=new Runnable[]{this::confirmResetAllSources,this::restoreSettingsDefaults};for(int i=0;i<labels.length;i++){if(i>0){View divider=new View(this);divider.setBackgroundColor(DIV);actions.addView(divider,new LinearLayout.LayoutParams(dp(1),dp(28)));}actions.addView(recoveryOption(labels[i],callbacks[i]),new LinearLayout.LayoutParams(-2,dp(56)));}group.addView(actions,new LinearLayout.LayoutParams(-2,dp(56)));group.setContentDescription("恢复：所有列表、默认设置");return group;}
  ScrollView limitedDialogScroll(View body,int maxDp){ScrollView scroll=new ScrollView(this){@Override protected void onMeasure(int widthSpec,int heightSpec){int available=Math.min(dp(maxDp),Math.max(dp(240),getResources().getDisplayMetrics().heightPixels-dp(180)));super.onMeasure(widthSpec,MeasureSpec.makeMeasureSpec(available,MeasureSpec.AT_MOST));}};scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(true);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));return scroll;}
  Drawable filterChipShape(boolean selected){GradientDrawable surface=new GradientDrawable();surface.setColor(selected?ThemeEngine.selectedFill(this):Color.TRANSPARENT);surface.setCornerRadius(dp(16));surface.setStroke(dp(1),selected?PRIMARY:DIV);return filterRipple(surface);}
  void updateFilterChip(TextView chip,String label,boolean selected){chip.setSelected(selected);chip.setText((selected?"✓  ":"")+label);chip.setTextColor(selected?PRIMARY:TEXT);chip.setTypeface(selected?AppFonts.bold(this):AppFonts.normal(this));chip.setBackground(filterChipShape(selected));chip.setContentDescription(label+"，"+(selected?"已选择":"未选择")+"，点击"+(selected?"取消选择":"选择"));}
  TextView sessionFilterChip(String label,boolean selected,java.util.function.Consumer<Boolean> changed){TextView chip=text("",12,TEXT);chip.setGravity(Gravity.CENTER);chip.setClickable(true);chip.setFocusable(true);updateFilterChip(chip,label,selected);chip.setOnClickListener(v->{boolean next=!chip.isSelected();updateFilterChip(chip,label,next);changed.accept(next);chip.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_VIEW_SELECTED);});return chip;}
  LinearLayout settingsSwitchRow(String label,boolean checked,java.util.function.Consumer<Boolean> changed){return settingsSwitchRow(R.drawable.ic_folder,label,checked,changed);}
  LinearLayout settingsSwitchRow(int iconRes,String label,boolean checked,java.util.function.Consumer<Boolean> changed){LinearLayout row=settingsRowShell();row.setClickable(true);row.setFocusable(true);row.setBackground(settingsPress());applePressScale(row);ImageView icon=settingsLeadingIcon(iconRes);row.addView(icon,settingsIconBox());TextView title=settingsRowTitle(label);row.addView(title,new LinearLayout.LayoutParams(0,-1,1));LumaSwitch toggle=new LumaSwitch(this);toggle.setChecked(checked);toggle.setContentDescription(label);row.addView(toggle,new LinearLayout.LayoutParams(-2,dp(48)));row.setTag(toggle);// T5-S:行持有开关引用,供节能档置灰文件夹递归
java.util.function.Consumer<Boolean> apply=value->{row.setContentDescription(label+"，"+(value?"已开启":"已关闭"));changed.accept(value);};// v1.22.4 删 icon alpha 0.5→1 闪烁动画：用户报"点开关左边图标闪一下"（bug 非动效），图标恒定不闪
toggle.setOnCheckedChangeListener((button,value)->apply.accept(value));row.setOnClickListener(v->{if(!toggle.isEnabled())return;toggle.setChecked(!toggle.isChecked());});row.setContentDescription(label+"，"+(checked?"已开启":"已关闭"));return row;}
  void roundDialog(AlertDialog dialog){Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));int viewport=safeContentWidth(),width=Math.max(dp(1),Math.min(dp(560),viewport-dp(28)));WindowManager.LayoutParams p=window.getAttributes();p.gravity=Gravity.CENTER;p.x=0;p.y=0;p.width=width;p.height=WindowManager.LayoutParams.WRAP_CONTENT;window.setAttributes(p);}View content=dialog.findViewById(android.R.id.content),panel=content;if(content instanceof ViewGroup&&((ViewGroup)content).getChildCount()>0)panel=((ViewGroup)content).getChildAt(0);if(panel!=null){panel.setBackground(solidShape(SURFACE,22));panel.setClipToOutline(true);panel.setElevation(dp(10));}
styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEGATIVE),false);styleDialogButton(dialog.getButton(AlertDialog.BUTTON_POSITIVE),true);styleDialogButton(dialog.getButton(AlertDialog.BUTTON_NEUTRAL),true);}
  void showRounded(AlertDialog dialog){dialog.show();roundDialog(dialog);}
  void prepareRoundedInputDialog(AlertDialog dialog,View focus){Window window=dialog.getWindow();if(window!=null)window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);focus.requestFocus();}
  /** 长按工具条：摘要行高度。 */
  static final int SELECTION_ROW_H=48;
  /** 长按工具条：单个「图标+中文」格子高度（56dp 才够放下 24dp 图标 + 11sp 标签）。 */
  static final int SELECTION_CELL_H=56;
  LinearLayout makeSelectionBar(TextView summary,Button all,int allWidth,int actionWidth,String[] labels,ImageButton... actions){LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);bar.setPadding(dp(8),0,dp(4),0);bar.setBackground(solidShape(SURFACE,14));bar.setElevation(dp(8));bar.setTag(new SelectionBarLayout(summary,all,allWidth,actionWidth,labels,actions));summary.setMaxLines(1);root.setPadding(dp(16),dp(8),dp(16),0);root.addView(bar);layoutSelectionBar(bar);bar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{if(r-l!=or_-ol)layoutSelectionBar(bar);});animateIn(bar,0);return bar;}
  /**
   * [DFW-68] 长按工具条里的一格：**图标 + 中文标签**。
   *
   * 用户 2026-10-01："长按软件库界面某个软件出现的底部菜单太丑了，而且**还没中文标注**，
   * 希望你可以把它优化成**排列整齐的按钮形式**。"
   *
   * 旧的格子是纯 `ImageButton`：语义只写在 `contentDescription` 里（读屏能读、屏幕上看不见），
   * "复制"和"下载"两个图标形状又接近，用户只能靠猜。
   *
   * 整格可点（不只是图标本身），标签区域也算触控区——原来指头稍微偏一点就点空。
   */
  LinearLayout selectionActionCell(String[] labels,int index,ImageButton action){
    LinearLayout cell=new LinearLayout(this);
    cell.setOrientation(LinearLayout.VERTICAL);
    cell.setGravity(Gravity.CENTER);
    String label=labels!=null&&index<labels.length&&labels[index]!=null?labels[index]:"";
    cell.setContentDescription(action.getContentDescription());
    cell.setClickable(true);
    cell.setFocusable(true);
    cell.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));
    cell.setOnClickListener(v->action.performClick());
    action.setPadding(dp(3),dp(3),dp(3),dp(3));
    action.setMinimumWidth(0);
    action.setMinimumHeight(0);
    cell.addView(action,new LinearLayout.LayoutParams(dp(28),dp(28)));
    if(!label.isEmpty()){
      TextView caption=text(label,11,PRIMARY);
      caption.setSingleLine(true);
      caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
      caption.setGravity(Gravity.CENTER);
      caption.setIncludeFontPadding(false);
      cell.addView(caption,new LinearLayout.LayoutParams(-1,-2));
    }
    applePressScale(cell);
    return cell;
  }

  void layoutSelectionBar(LinearLayout bar){
    if(bar==null||!(bar.getTag() instanceof SelectionBarLayout))return;
    SelectionBarLayout spec=(SelectionBarLayout)bar.getTag();
    // 只收集可见项，同时记住它们在原数组里的下标——**标签按同一个下标对齐**（重命名那格是 GONE 的）
    List<Integer> visible=new ArrayList<>();
    for(int i=0;i<spec.actions.length;i++)if(spec.actions[i].getVisibility()!=View.GONE)visible.add(i);
    int available=root!=null&&root.getWidth()>0?root.getWidth()-root.getPaddingLeft()-root.getPaddingRight():getResources().getDisplayMetrics().widthPixels-dp(32),target=dp(Math.max(54,spec.actionWidth)),count=visible.size();
    boolean twoRows=!wideNavigation()&&dp(spec.allWidth+72)+target*count>available;
    if(bar.getChildCount()>0&&spec.twoRows==twoRows&&spec.available==available)return;
    spec.twoRows=twoRows;
    spec.available=available;
    bar.removeAllViews();
    removeFromParent(spec.summary);
    removeFromParent(spec.all);
    for(ImageButton action:spec.actions)removeFromParent(action);
    if(twoRows){
      bar.setOrientation(LinearLayout.VERTICAL);
      LinearLayout top=new LinearLayout(this);
      top.setGravity(Gravity.CENTER_VERTICAL);
      top.addView(spec.summary,new LinearLayout.LayoutParams(0,dp(SELECTION_ROW_H),1));
      top.addView(spec.all,new LinearLayout.LayoutParams(dp(spec.allWidth),dp(SELECTION_ROW_H)));
      bar.addView(top,new LinearLayout.LayoutParams(-1,dp(SELECTION_ROW_H)));
      GridLayout actions=new GridLayout(this);
      int columns=Math.max(1,(count+1)/2);
      actions.setColumnCount(columns);
      actions.setRowCount(2);
      actions.setContentDescription("多选操作，两行显示");
      for(int i=0;i<count;i++){
        int index=visible.get(i);
        GridLayout.LayoutParams cell=new GridLayout.LayoutParams(GridLayout.spec(i/columns),GridLayout.spec(i%columns,1f));
        cell.width=0;
        cell.height=dp(SELECTION_CELL_H);
        actions.addView(selectionActionCell(spec.labels,index,spec.actions[index]),cell);
      }
      bar.addView(actions,new LinearLayout.LayoutParams(-1,dp(SELECTION_CELL_H*2)));
    }else{
      bar.setOrientation(LinearLayout.HORIZONTAL);
      bar.addView(spec.summary,new LinearLayout.LayoutParams(0,dp(SELECTION_ROW_H),1));
      bar.addView(spec.all,new LinearLayout.LayoutParams(dp(spec.allWidth),dp(SELECTION_ROW_H)));
      int width=target;
      if(dp(spec.allWidth+72)+target*count>available)width=Math.max(dp(40),(available-dp(spec.allWidth+72))/Math.max(1,count));
      for(int index:visible)bar.addView(selectionActionCell(spec.labels,index,spec.actions[index]),new LinearLayout.LayoutParams(width,dp(SELECTION_CELL_H)));
    }
    ViewGroup.LayoutParams raw=bar.getLayoutParams();
    LinearLayout.LayoutParams layout=raw instanceof LinearLayout.LayoutParams?(LinearLayout.LayoutParams)raw:new LinearLayout.LayoutParams(-1,dp(50));
    layout.width=-1;
    layout.height=dp(twoRows?SELECTION_ROW_H+SELECTION_CELL_H*2:SELECTION_CELL_H);
    bar.setLayoutParams(layout);
    bar.requestLayout();
  }
  void removeFromParent(View view){ViewParent parent=view.getParent();if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(view);}
  void showChecks(Map<?,CheckBox> checks,boolean show){for(CheckBox check:checks.values()){if(!show)check.setChecked(false);check.setVisibility(show?View.VISIBLE:View.GONE);}}
  <K> boolean toggleChosen(Set<K> selected,K key,Map<K,CheckBox> checks){if(!selected.add(key))selected.remove(key);CheckBox check=checks.get(key);if(check!=null)check.setChecked(selected.contains(key));return selected.isEmpty();}
  <K> void syncChecks(Set<K> selected,Map<K,CheckBox> checks){for(Map.Entry<K,CheckBox> entry:checks.entrySet())entry.getValue().setChecked(selected.contains(entry.getKey()));}
  boolean allChosen(Collection<?> visible,Set<?> selected){return !visible.isEmpty()&&selected.containsAll(visible);}
  void resetSelection(Set<?> selected,Map<?,CheckBox> checks,LinearLayout bar){selected.clear();if(bar!=null&&bar.getParent()==root)root.removeView(bar);if(root!=null)root.setPadding(dp(16),dp(8),dp(16),dp(16));showChecks(checks,false);}
  public boolean motionEnabled(){if(suppressUiMotion)return false;try{return Settings.Global.getFloat(getContentResolver(),Settings.Global.ANIMATOR_DURATION_SCALE,1f)>0f;}catch(Exception ignored){return true;}}
  /** 2026-09-24 性能审计 P3：弱设备判定——低内存设备或 totalMem≤1.5GB。弱机上 350-400ms 整页推入
      是逐帧全屏重绘负担，animatePage 会把它降级为 ≤190ms 原位淡切；标准设备转场曲线不变。 */
  boolean weakDevice;
  void detectWeakDevice(){boolean weak=false;try{android.app.ActivityManager am=(android.app.ActivityManager)getSystemService(ACTIVITY_SERVICE);if(am!=null){if(am.isLowRamDevice())weak=true;android.app.ActivityManager.MemoryInfo info=new android.app.ActivityManager.MemoryInfo();am.getMemoryInfo(info);if(info.totalMem>0&&info.totalMem<=1536L*1024*1024)weak=true;}}catch(Exception ignored){}weakDevice=weak;}
  View underlineFilter(String label,boolean selected,Runnable action){return underlineFilter(label,selected,action,14);}
  /** v1.22.3 可调水平内边距：下载页状态标签在 360dp 窄屏上会溢出，所以下载页传一个更小的值。
   *
   *  ⚠️ [2026-10-03 修正] 这段注释原来的数字**全是错的**：它说「6 个标签 / 14dp 时总宽 426dp /
   *  下载页传 7dp / 落在 322dp」，而实际代码是 **5 个标签、传 5dp**（见 `renderDownloadFilters`）。
   *  同一件事在 `renderDownloadFilters` 里还有一份注释，写的是「6 标签 / 改 6dp ≈ 320dp」——
   *  三个数字（7/6/5）里没一个对，标签数也错。
   *
   *  按今天的代码重算（360dp、fontScale 1.0）：可用 328dp，5 标签 16 个汉字 × 12dp = 192，
   *  加内边距 2×5×5 = 50、右间距 4×5 = 20、strip 内边距 12，合计 **274dp，余 54dp**。
   *  退回 14dp 是 364dp > 328dp，所以"要传小值"这件事仍然成立，只是理由和数字都要重写。
   *
   *  另外「最右被屏幕右缘裁切」这个失败模式**今天已不可能发生**：外层是 HorizontalScrollView，
   *  超宽时是横滚。真实价值是**可发现性**——5 个标签默认全部可见，不用横滑才发现有「下载失败」。 */
  View underlineFilter(String label,boolean selected,Runnable action,int hPad){FrameLayout tab=new FrameLayout(this);tab.setClickable(true);tab.setFocusable(true);tab.setSelected(selected);tab.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));TextView title=text(label,12,selected?PRIMARY:MUTED);title.setGravity(Gravity.CENTER);title.setSingleLine(true);title.setSelected(selected);title.setPadding(dp(hPad),0,dp(hPad),dp(3));tab.addView(title,new FrameLayout.LayoutParams(-2,-1,Gravity.CENTER));View underline=new View(this);underline.setBackground(solidShape(PRIMARY,2));underline.setAlpha(selected?1f:0f);underline.setScaleX(selected?1f:0f);FrameLayout.LayoutParams line=new FrameLayout.LayoutParams(-1,dp(2),Gravity.BOTTOM);line.setMargins(dp(Math.max(2,hPad-4)),0,dp(Math.max(2,hPad-4)),dp(1));tab.addView(underline,line);tab.setTag(new FilterTab(title,underline));tab.setContentDescription(label+(selected?"，已选择":""));tab.setOnClickListener(v->{if(v.isSelected())return;transferUnderline((LinearLayout)v.getParent(),v);action.run();});return tab;}
  void transferUnderline(LinearLayout strip,View target){int oldIndex=-1,newIndex=strip.indexOfChild(target);View old=null;for(int i=0;i<strip.getChildCount();i++)if(strip.getChildAt(i).isSelected()){old=strip.getChildAt(i);oldIndex=i;break;}boolean right=oldIndex<newIndex;for(int i=0;i<strip.getChildCount();i++){View tab=strip.getChildAt(i);if(!(tab.getTag() instanceof FilterTab))continue;FilterTab holder=(FilterTab)tab.getTag();boolean active=tab==target;tab.setSelected(active);holder.label.setSelected(active);holder.label.setTextColor(active?PRIMARY:MUTED);tab.setContentDescription(holder.label.getText()+(active?"，已选择":""));holder.underline.animate().cancel();if(!motionEnabled()){holder.underline.setAlpha(active?1f:0f);holder.underline.setScaleX(active?1f:0f);continue;}if(tab==old){holder.underline.setPivotX(right?holder.underline.getWidth():0);holder.underline.animate().alpha(0f).scaleX(0f).setDuration(150).setInterpolator(standardEase()).start();}else if(active){holder.underline.setPivotX(right?0:holder.underline.getWidth());holder.underline.setAlpha(1f);holder.underline.setScaleX(0f);holder.underline.animate().scaleX(1f).setDuration(150).setInterpolator(standardEase()).start();}else{holder.underline.setAlpha(0f);holder.underline.setScaleX(0f);}}}
  /** 扩展名筛选胶囊。圆角传 24 → 量化 26 → 被 GradientDrawable 夹到 height/2 = 真 pill（token §4）。 */
  TextView roundedDownloadFilter(String label,boolean selected,Runnable action){TextView filter=text(label,12,selected?BG:MUTED);filter.setGravity(Gravity.CENTER);filter.setPadding(dp(14),0,dp(14),0);filter.setBackground(filterRipple(solidShape(selected?PRIMARY:SURFACE,24)));filter.setClickable(true);filter.setFocusable(true);filter.setSelected(selected);filter.setContentDescription(label+(selected?"，已选择":""));filter.setOnClickListener(v->{if(v.isSelected())return;selectRoundedDownloadFilter((LinearLayout)v.getParent(),filter);action.run();});return filter;}
  void selectRoundedDownloadFilter(LinearLayout strip,TextView target){for(int i=0;i<strip.getChildCount();i++){TextView filter=(TextView)strip.getChildAt(i);boolean selected=filter==target;filter.setSelected(selected);filter.setTextColor(selected?BG:MUTED);filter.setBackground(filterRipple(solidShape(selected?PRIMARY:SURFACE,24)));filter.setContentDescription(filter.getText()+(selected?"，已选择":""));if(selected&&motionEnabled()){filter.animate().cancel();filter.setScaleX(.92f);filter.setScaleY(.92f);filter.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(standardEase()).start();}}}
  void transitionDownloadFilter(Runnable update){int token=++downloadFilterGeneration;if(downloadList==null){update.run();return;}downloadList.animate().cancel();if(!motionEnabled()){downloadList.setAlpha(1f);update.run();return;}downloadList.animate().alpha(0f).setDuration(100).setInterpolator(standardEase()).withEndAction(()->{if(token!=downloadFilterGeneration)return;update.run();downloadList.setAlpha(0f);downloadList.animate().alpha(1f).setDuration(150).setInterpolator(standardEase()).start();}).start();}
  void transitionSourceFilter(Runnable update){int token=++sourceFilterGeneration;if(sourceGrid==null){update.run();return;}sourceGrid.animate().cancel();if(!motionEnabled()){sourceGrid.setAlpha(1f);update.run();return;}sourceGrid.animate().alpha(0f).setDuration(100).setInterpolator(standardEase()).withEndAction(()->{if(token!=sourceFilterGeneration)return;update.run();sourceGrid.setAlpha(0f);sourceGrid.animate().alpha(1f).setDuration(150).setInterpolator(standardEase()).start();}).start();}
  public TextView primaryHeader(String title){LinearLayout header=new LinearLayout(this);pageHeaderRow=header;header.setGravity(Gravity.CENTER_VERTICAL);TextView heading=text(title,22,TEXT,700);heading.setPadding(dp(4),0,0,0);header.addView(heading,new LinearLayout.LayoutParams(0,dp(52),1));root.addView(header,new LinearLayout.LayoutParams(-1,dp(56)));return heading;}
  EditText pageSearch(java.util.function.Consumer<String> action){return pageSearch(action,0);}
  /** 2026-09-24 性能审计#2：带去抖的搜索框。下载列表整表重建贵，输入每键只调度一次渲染；debounceMs<=0 表示直通（源列表上游自带 110ms 去抖）。 */
  EditText pageSearch(java.util.function.Consumer<String> action,long debounceMs){FrameLayout box=new FrameLayout(this);box.setBackground(shape(SURFACE,14));EditText input=new EditText(this);input.setSingleLine();input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setHint("搜索");input.setTextSize(14);input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(dp(14),0,dp(52),0);input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);box.addView(input,new FrameLayout.LayoutParams(-1,-1));ImageButton find=iconButton(R.drawable.ic_search,"筛选");FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.END|Gravity.CENTER_VERTICAL);box.addView(find,fp);root.addView(box,new LinearLayout.LayoutParams(-1,dp(48)));Runnable run=()->action.accept(input.getText().toString().trim());find.setOnClickListener(v->run.run());input.setOnEditorActionListener((v,a,e)->{run.run();return true;});input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(debounceMs<=0){run.run();return;}if(searchDebounceRunnable!=null)ui.removeCallbacks(searchDebounceRunnable);searchDebounceRunnable=run;ui.postDelayed(run,debounceMs);}public void afterTextChanged(Editable e){}});return input;}
  int itemColumns(){int width=root!=null&&root.getWidth()>0?root.getWidth():getResources().getDisplayMetrics().widthPixels,height=root!=null&&root.getHeight()>0?root.getHeight():getResources().getDisplayMetrics().heightPixels;return width>height||width>=dp(600)?4:2;}
  GridLayout newItemGrid(int columns){GridLayout grid=new GridLayout(this);grid.setColumnCount(columns);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);return grid;}
  int gridCellWidth(int columns){int width=getResources().getDisplayMetrics().widthPixels-dp(32);if(root!=null&&root.getWidth()>0)width=root.getWidth()-root.getPaddingLeft()-root.getPaddingRight();return Math.max(dp(72),(width-dp(8)*columns)/columns);}
  GridLayout.LayoutParams itemLayout(int index,int columns){GridLayout.LayoutParams gp=new GridLayout.LayoutParams();gp.width=gridCellWidth(columns);gp.height=dpText(104);gp.columnSpec=GridLayout.spec(index%columns);gp.rowSpec=GridLayout.spec(index/columns);gp.setMargins(dp(4),dp(2),dp(4),dp(2));return gp;}// v1.19.2：卡片高 84/90→104——内容下限=库角标14+标题48+meta24+内距11≈97dp，旧值是角标加入前的尺寸（搜索结果文字崩坏根因）
  void applySystemNavigationInsets(int left,int top,int right,int bottom){if(host==null)return;boolean changed=host.getPaddingLeft()!=left||host.getPaddingTop()!=top||host.getPaddingRight()!=right||host.getPaddingBottom()!=bottom;if(changed){host.setPadding(left,top,right,bottom);host.post(this::refreshAdaptiveLayout);host.post(this::reflowNavBall);}}
  void installSystemNavigationInsets(){if(host==null)return;host.setOnApplyWindowInsetsListener((view,insets)->{int left,top,right,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets navigation=insets.getInsets(android.view.WindowInsets.Type.navigationBars()),status=insets.getInsets(android.view.WindowInsets.Type.statusBars()),cutout=insets.getInsets(android.view.WindowInsets.Type.displayCutout());left=Math.max(navigation.left,cutout.left);top=Math.max(status.top,cutout.top);right=Math.max(navigation.right,cutout.right);bottom=Math.max(navigation.bottom,cutout.bottom);}else{left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();if(Build.VERSION.SDK_INT>=28&&insets.getDisplayCutout()!=null){android.view.DisplayCutout cutout=insets.getDisplayCutout();left=Math.max(left,cutout.getSafeInsetLeft());top=Math.max(top,cutout.getSafeInsetTop());right=Math.max(right,cutout.getSafeInsetRight());bottom=Math.max(bottom,cutout.getSafeInsetBottom());}}applySystemNavigationInsets(left,top,right,bottom);if(Build.VERSION.SDK_INT>=30){// v1.19.9 AI 内嵌页防双计：宿主已用 host padding 消费状态栏/导航条/刘海，但原先原样返回 insets，Compose（按窗口报告 insets）再垫一层=顶/底双倍空白；裁剪后再传子级，ime 必须保留（AI 输入框 imePadding 靠它浮到键盘上方）。主 app 其余页面无第二个 insets 消费者，仅 AI 页受益
android.graphics.Insets none=android.graphics.Insets.NONE;android.view.WindowInsets.Builder cleared=new android.view.WindowInsets.Builder(insets).setInsets(android.view.WindowInsets.Type.statusBars(),none).setInsets(android.view.WindowInsets.Type.navigationBars(),none).setInsets(android.view.WindowInsets.Type.displayCutout(),none);cleared.setInsetsIgnoringVisibility(android.view.WindowInsets.Type.displayCutout(),none);return cleared.build();}return insets;});host.requestApplyInsets();}
  void invalidateSearchRenderSurface(){synchronized(searchUiLock){searchRenderEpoch++;searchRenderGrid=null;pendingSearchSession=pendingSearchEpoch=-1;pendingSearchGrid=null;pendingSearchAdds.clear();pendingSearchUpdates.clear();pendingSearchRefresh=false;searchUiPosted=false;}searchWindowPosted=false;}
  /**
   * [DFW-73] 悬浮球六项**全部平级**（用户 2026-10-01 口述）。
   *
   * 病根：公告页原来用 `primaryBase(0)`（= 软件库那一档）当底座，于是 `goToDestination(0)`
   * 开头的 `destination==primaryDestination` 守卫直接把它吞掉——表现就是"在公告页点
   * 悬浮球里的『软件库』，一点反应都没有"。公告必须有独立档位才能和另外五项真正平级。
   */
  static final int DEST_NOTICE=6;
  /** 顶级页返回兜底："再返回一次退出软件"的第二次返回窗口（毫秒）。 */
  static final long EXIT_CONFIRM_WINDOW_MS=2000L;
  void base(){basePage(-1);} void primaryBase(int destination){if(pageKind==1&&destination!=1)retainSourceListPage();basePage(destination);}
  void basePage(int destination){
    // [DFW-71] **在离开设置页的那一刻**记下滚动位置：任何导航离开都会经过这里，
    // 而且此刻 pageKind 还是 4。旧实现在 showSettings() 内部记录，只有在"已经停在设置页时
    // 再调一次 showSettings"才生效 —— 那正是**测试**走的路，不是用户走的路。
    // 用户走的是"进子页 → 返回"，那时 pageKind 已经是子页的值，于是永远记不到，返回必然回顶
    // （用户 2026-10-01："全部按钮都是啊，点击进去后退出，绝对返回到顶部，这是必然事件"）。
    if(pageKind==4&&pageScroll!=null)settingsScrollY=pageScroll.getScrollY();
    clearHomeBrandCiallo();navigationSession++;invalidateSearchRenderSurface();synchronized(sourceSearchLock){sourceSearchSession++;sourceSearchRunning=false;sourceSearchPaused=false;sourceSearchLock.notifyAll();}sourceRenderSession++;dismissBreadcrumbChooser();cancelFolderPullWork();systemBackAction=null;liveGrid=null;itemsGrid=null;sourceGrid=null;sourceHeading=null;sourceFilter=null;sourceCategoryStrip=null;searchDragBar=null;searchScrollFrame=null;searchPauseButton=null;progress=null;status=null;statusRight=null;liveUrls.clear();currentSourceSearchUrls.clear();selectedUrls.clear();selectionChecks.clear();selectionMode=false;selectionBar=null;selectionSummary=null;selectionCopyDescription=null;selectionCategorize=null;selectionRenameFolder=null;selectedSourceUrls.clear();sourceSelectionChecks.clear();visibleSources.clear();sourcePageSources=null;sourceSelectionMode=false;sourceSelectionBar=null;sourceSelectionSummary=null;sourceSelectionRename=null;selectedDownloads.clear();downloadChecks.clear();downloadSelectionMode=false;downloadSelectionBar=null;downloadSelectionSummary=null;downloadHeaderMenuButton=null;downloadsPage=false;downloadLabels.clear();downloadBars.clear();folderPullFrame=null;folderPullScroll=null;folderPullIndicator=null;folderPullLabel=null;folderMoreSpinner=null;pageHeaderRow=null;settingsDownloadPathText=null;settingsSearchInput=null;settingsSearchEmpty=null;settingsSearchSections.clear();profilePresent=false;folderProfilePending=false;homeSearchFocused=false;homeStage=null;homeBrand=null;homeHistory=null;homeRecommendations=null;homeRecommendationsScroll=null;homeRecommendation=null;homeSearchBox=null;homeSearchCategory=null;homeCategoryPicker=null;homeLibsBand=null;if(sourceSearchRunnable!=null)ui.removeCallbacks(sourceSearchRunnable);if(sourceListFilterRunnable!=null)ui.removeCallbacks(sourceListFilterRunnable);sourceListFilterRunnable=null;if(searchDebounceRunnable!=null)ui.removeCallbacks(searchDebounceRunnable);searchDebounceRunnable=null;downloadRenderGeneration++;View previous=pageFrame;root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(dp(16),dp(8),dp(16),dp(16));root.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{int width=r-l;if(width>0&&Math.abs(width-lastLayoutWidth)>dp(12)){lastLayoutWidth=width;ui.post(this::refreshAdaptiveLayout);}});
    selectionAllButton=null;sourceSelectionAllButton=null;downloadSelectionAllButton=null;if(host==null){host=new FrameLayout(this);host.setBackgroundColor(BG);host.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{int width=r-l,height=b-t;if(width>0&&height>0&&(Math.abs(width-lastHostWidth)>dp(2)||Math.abs(height-lastHostHeight)>dp(2))){lastHostWidth=width;lastHostHeight=height;ui.post(this::refreshAdaptiveLayout);ui.post(this::reflowNavBall);}});pageHost=new FrameLayout(this);host.addView(pageHost,new FrameLayout.LayoutParams(-1,-1));toastScroll=new ScrollView(this);toastScroll.setFillViewport(false);toastScroll.setVerticalScrollBarEnabled(false);toastScroll.setVisibility(View.GONE);toastLayer=new LinearLayout(this);toastLayer.setOrientation(LinearLayout.VERTICAL);toastScroll.addView(toastLayer,new ScrollView.LayoutParams(-1,-2));/* [DFW-54] 下载卡的起始位置原来写死 64dp，而顶部通知条占的是
       [状态栏高度+8, 状态栏高度+52] —— 两者会**物理重叠**（有状态栏的机器上必然撞）。
       改成跟着状态栏让位，保证通知条出现时下载卡在它下面。 */
FrameLayout.LayoutParams tp=new FrameLayout.LayoutParams(Math.max(dp(1),Math.min(dp(250),safeContentWidth()-dp(24))),0,Gravity.TOP|Gravity.END);tp.setMargins(dp(12),statusBarInset()+dp(64),dp(12),0);host.addView(toastScroll,tp);setContentView(host);installSystemNavigationInsets();installNavBall();prewarmAiCompose();}
    primaryDestination=destination;if(destination>=0){primaryShell=new LinearLayout(this);composePrimaryShell();pageFrame=primaryShell;}else{primaryShell=null;pageFrame=root;}
    /* [DFW-73 修正] 页面本身必须**不透明**。root 以前没有底色，页面内容又填不满整屏（比如崩溃日志页），
       于是"整页实体位移"的转场里，新页滑进来的过程中下面那截会透出旧页（旧页又被压暗到 55%）
       —— 用户看到的就是"上半部分打开了、下半部分透明"。给页面帧刷上 BG 就彻底没有这个缝。 */
    pageFrame.setBackgroundColor(BG);if(navBall!=null)ui.post(this::refreshNavBall);FrameLayout.LayoutParams pageParams=new FrameLayout.LayoutParams(-1,-1);if(previous!=null&&pageDirection<0)pageHost.addView(pageFrame,0,pageParams);else pageHost.addView(pageFrame,pageParams);if(primaryNavigationSwitch){primaryNavigationSwitch=false;animatePage(previous,pageFrame,0);}else animatePage(previous,pageFrame,pageDirection);pageDirection=1;

  }
  int safeContentWidth(){int padding=host==null?0:host.getPaddingLeft()+host.getPaddingRight(),measured=host==null?0:host.getWidth();if(measured>padding)return measured-padding;int configured=dp(Math.max(1,getResources().getConfiguration().screenWidthDp));return Math.max(dp(1),configured-padding);}
  int safeContentHeight(){int padding=host==null?0:host.getPaddingTop()+host.getPaddingBottom(),measured=host==null?0:host.getHeight();if(measured>padding)return measured-padding;int configured=dp(Math.max(1,getResources().getConfiguration().screenHeightDp));return Math.max(dp(1),configured-padding);}
  boolean wideNavigation(){int width=safeContentWidth();return width>=dp(600)&&width>safeContentHeight();}
  int homeSearchWidth(){int available=homeStage!=null&&homeStage.getWidth()>0?homeStage.getWidth():root!=null&&root.getWidth()>0?root.getWidth()-root.getPaddingLeft()-root.getPaddingRight():Math.max(dp(1),safeContentWidth()-dp(32));return Math.max(dp(1),Math.min(available,dp(640)));}
  int homeSearchCategoryWidth(int searchWidth){return Math.max(dp(120),Math.min(searchWidth,Math.min(dp(136),searchWidth-dp(140))));// v1.6.3 修「全部消失/搜索钮变小」：内部预算=文字30+箭头28+钮44+内边距15≈117dp，下限必须≥120dp；上限 136dp 仍比原始 150 紧凑
  }
  void fitHomeSearchControls(int searchWidth){if(search==null||homeSearchCategory==null)return;int trailing=homeSearchCategoryWidth(searchWidth);search.setPadding(homeSearchFocused?dp(50):dp(16),0,trailing+dp(6),0);ViewGroup.LayoutParams raw=homeSearchCategory.getLayoutParams();if(raw instanceof FrameLayout.LayoutParams){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)raw;p.width=trailing;homeSearchCategory.setLayoutParams(p);}if(homeCategoryPicker!=null)homeCategoryPicker.fitWidth(trailing);}
  /** 底栏分流唯一入口（v1.5.0 从 navItem 抽出：普通点按与液态玻璃胶囊吸附共用同一守卫与清理） */
  void goToDestination(int destination){if(destination==primaryDestination)return;if(destination==4){/* v1.10.0 内嵌改造：AI 为主界面内嵌页（不再跳转 RouteActivity）；DEGRADED=RikkaHub 启动链失败时拦截保主功能 */if(App.DEGRADED){showNotice("AI 模块初始化失败已停用，其他功能不受影响",true);return;}/* v1.22.0 首启权限一次性引导（用户要求）：引导未完成时先走授权链，完毕再进 AI 页 */maybeGuideAiPermissions(this::enterAiPage);return;}primaryNavigationSwitch=true;pageDirection=0;if(pageKind==6){toolBackStack.clear();releaseToolMedia();}if(destination==0)navigateHome();else if(destination==1)showSources();else if(destination==2)showDownloads();else if(destination==5)showTools();else{settingsScrollY=0;showSettings();}}
  /** AI 页进入（原 goToDestination 的 AI 分支主体，抽出供权限引导链回调复用） */
  void enterAiPage(){/* v1.17.1 转场统一：AI 与其他 tab 一致走原位淡切（primaryNavigationSwitch），不再整页推入——旧逻辑整个外壳（连底栏）滑入，观感是「跳到一个一样的新界面」*/primaryNavigationSwitch=true;pageDirection=0;showAiEmbedded();}
  /** v1.10.0 内嵌 AI 页：ComposeView 缓存复用，切走再切回不丢界面状态；页内容自带顶栏与返回处理 */
  android.view.View aiComposeView;
  void showAiEmbedded(){primaryBase(4);pageKind=5;activeSource=null;clearFolderTrail();systemBackAction=null;root.setPadding(0,0,0,0);// v1.11.1 关键修复：ComposeView 必须挂进 root（primaryShell 中权重=1 的内容区，位于底栏上方）；之前挂 pageFrame（=primaryShell）会排到底栏之后——底栏被顶到屏幕中间、上方留出半屏黑底
    if(App.DEGRADED){TextView tv=text("AI 模块初始化失败已停用，其他功能不受影响",14,MUTED);tv.setGravity(Gravity.CENTER);root.addView(tv,new LinearLayout.LayoutParams(-1,-2));return;}
    if(aiComposeView!=null){android.view.View v=aiComposeView();android.view.ViewGroup p=(android.view.ViewGroup)v.getParent();if(p!=null)p.removeView(v);root.addView(v,new LinearLayout.LayoutParams(-1,-1));v.setVisibility(View.VISIBLE);return;}// v1.17.1：预组合挂载时是 INVISIBLE，正式上屏转 VISIBLE
    // v1.19.5：首次打开（8 秒预热未完成就点 AI）时，整个 RikkaHub Compose 图在主线程做首次组合，真机要 1-3 秒，
    // 此前表现为点 AI 后整界面僵死无反馈。改为先同步挂轻量"正在初始化"占位（下一帧即可见），再在下一帧做重组挂载，
    // 完成后原位换装。占位期间的任何离开(pageKind!=5)都会安全丢弃。
    LinearLayout loading=new LinearLayout(this);loading.setOrientation(LinearLayout.VERTICAL);loading.setGravity(Gravity.CENTER);android.widget.ProgressBar aiLoadingBar=new android.widget.ProgressBar(this);aiLoadingBar.setIndeterminate(true);loading.addView(aiLoadingBar,new LinearLayout.LayoutParams(-2,dp(36)));TextView aiLoadingText=text("正在初始化 AI 对话…",13,MUTED);aiLoadingText.setGravity(Gravity.CENTER);loading.addView(aiLoadingText,new LinearLayout.LayoutParams(-2,-2));root.addView(loading,new LinearLayout.LayoutParams(-1,-1));
    LinearLayout aiTarget=root;ui.post(()->{if(pageKind!=5){if(loading.getParent()==aiTarget)aiTarget.removeView(loading);return;}try{android.view.View v=aiComposeView();android.view.ViewGroup p=(android.view.ViewGroup)v.getParent();if(p!=null)p.removeView(v);if(loading.getParent()==aiTarget)aiTarget.removeView(loading);aiTarget.addView(v,new LinearLayout.LayoutParams(-1,-1));v.setVisibility(View.VISIBLE);}catch(Throwable t){android.util.Log.w("MainActivity","showAiEmbedded lazy: "+t.getMessage(),t);if(loading.getParent()==aiTarget)aiTarget.removeView(loading);TextView err=text("AI 界面初始化失败，可返回重试",13,MUTED);err.setGravity(Gravity.CENTER);aiTarget.addView(err,new LinearLayout.LayoutParams(-1,-2));}});
  }
  /**
   * v1.20.0：按目标 ime 值构造派发用 insets（只改 ime，其余类型沿用来源）。
   *
   * [DFW-123] `@RequiresApi(30)` 不是消音，是**如实声明契约**：本方法体里的
   * `WindowInsets.Builder` / `Insets.of` / `build` 要 API 29，`setInsets` / `Type.ime` 要 API 30。
   * 唯一调用点在 `aiComposeView()` 的 `if(Build.VERSION.SDK_INT>=30)` 之内（本文件 `:971` → `:979`），
   * 所以运行期本来就是安全的 —— lint 报它，只是因为 `NewApi` 的 SDK_INT 检查**不跨方法传播**。
   * 加了注解之后 lint 会把检查推到调用点：将来谁在没守卫的地方调它，照样会红，鉴别力保留。
   * 用 `@SuppressLint("NewApi")` 就做不到这一点（那才是消音）。
   */
  @androidx.annotation.RequiresApi(30)
  private static android.view.WindowInsets dfwxImeInsets(android.view.WindowInsets src,int imeBottom){return new android.view.WindowInsets.Builder(src).setInsets(android.view.WindowInsets.Type.ime(),android.graphics.Insets.of(0,0,0,imeBottom)).build();}
  android.view.View aiComposeView(){if(aiComposeView==null){
    // ime 重算包装：ime insets 是窗口绝对值，而 ComposeView 底边悬在 host 底部 padding（导航条）之上，
    // 直接用会让输入框多抬一个导航条高度。重算规则：target = ime.bottom - host.getPaddingBottom()。
    // v1.22.8 根因修复（用户实测：聚焦输入框后上下按钮错位、动画崩坏；旧版就存在、多次打补丁越修越糟）：
    // 旧版在此之上叠了自研 250ms 滑行器（逐帧 requestApplyInsets 重派发）+ stickyBelow 粘滞缓存 + 系统
    // insets 动画计数器。崩坏机制：系统 IME 动画期间每帧派发的 target 都在变，滑行器判定 end != target
    // 便每帧 cancel+重启 ValueAnimator，插值每次只前进约 1/60 → 输入框严重滞后于键盘、键盘到位后再慢补
    // 250ms；below 靠 getLocationOnScreen 测量在布局未稳时会测出 0（粘滞缓存的由来），逐帧重派发又牵动
    // 整棵视图树反复重布局——三者叠加即"按钮错位 + 动画崩坏"。
    // 现在只保留纯几何换算：系统动画逐帧派发 → 每帧用当前 ime 值同步重算 → Compose 的 imePadding 原生
    // 平滑跟随；below 直接取 host.getPaddingBottom()（恒等于 wrap 底边到屏幕底的真实距离：wrap 无论挂
    // host 预热还是挂 root 全尺寸，底边都在 host 内容区底边），不测量无时机问题；导航条 inset 即使随键盘
    // 变化，几何仍然自洽。切勿再叠加任何自研动画/缓存：上游 ChatInput 是标准 imePadding，宿主只需保证
    // 它拿到正确的数值。
    android.view.View compose=me.rerere.rikkahub.dfwx.AiPageHostKt.createRikkaHubEmbedView(this);
    FrameLayout wrap=new FrameLayout(this);
    if(Build.VERSION.SDK_INT>=30){
      wrap.setOnApplyWindowInsetsListener((v,insets)->{
        try{
          android.graphics.Insets ime=insets.getInsets(android.view.WindowInsets.Type.ime());
          int below=host==null?0:host.getPaddingBottom();
          if(ime.bottom<=0||below<=0)return insets;
          int target=Math.max(0,ime.bottom-below);
          if(target==ime.bottom)return insets;
          return dfwxImeInsets(insets,target);
        }catch(Throwable t){android.util.Log.w("MainActivity","aiComposeInsets: "+t.getMessage(),t);}
        return insets;
      });
    }
    wrap.addView(compose,new FrameLayout.LayoutParams(-1,-1));aiComposeView=wrap;}return aiComposeView;}
  /** v1.17.1 预组合：启动 1.2s 后把 AI 界面以 INVISIBLE 挂进 host 一次性完成 Compose 首次组合（Koin/界面树/首帧），点 AI 即现不再有组合延迟；INVISIBLE 不绘制不收事件。组合状态随 Activity 生命周期保留，之后每次进出都秒开 */
  void prewarmAiCompose(){if(App.DEGRADED||aiComposeView!=null)return;ui.postDelayed(()->{try{if(host==null||aiComposeView!=null)return;android.view.View v=aiComposeView();v.setVisibility(View.INVISIBLE);host.addView(v,new FrameLayout.LayoutParams(-1,-1));}catch(Throwable t){android.util.Log.w("MainActivity","prewarmAiCompose: "+t.getMessage(),t);}},8000);}// v1.19.3 ANR 修复：1200→8000ms——挂载即触发整个 RikkaHub Compose 图在主线程组合，落在启动风暴窗口（升级后 JIT 冷启 + ROM 干预）会叠加阻塞诱发黑屏 ANR；8 秒后启动期已过，首开加速目的保留（用户 8 秒内进 AI 页走 aiComposeView() 惰性创建，二者互斥不重复）
  GradientDrawable searchBoxShape(boolean focused){GradientDrawable g=new GradientDrawable();g.setColor(SURFACE);g.setCornerRadius(dp(26));g.setStroke(dp(1),focused?PRIMARY:BORDER);return g;}
  /** v1.22.0 悬浮球取代底栏：内容区恒为 root 全尺寸（球挂 host 层，跨页常驻） */
  void composePrimaryShell(){if(primaryShell==null||root==null)return;primaryShell.removeAllViews();primaryShell.setOrientation(LinearLayout.VERTICAL);primaryShell.addView(root,new LinearLayout.LayoutParams(-1,-1));}
  void refreshAdaptiveLayout(){if(root==null)return;reflowVisibleLayouts();}
  /** [DFW-75] 最近一次页面转场的方向（+1 推入 / -1 弹出 / 0 切档淡入），供 JVM 用例断言方向没被搞反。 */
  int lastPageTransitionDirection;
  void animatePage(View previous,View next,int direction){
    lastPageTransitionDirection=direction;
    // v1.21.0 转场重写（20260924 报告·方向 D 落地，用户反馈"切换动画是透明的"）：推入/返回=sharedAxis
    // 30dp 同轴滑+淡（出 180ms accel、入 300ms emphasized，方向随操作语义对称）；切 tab/原位=fadeThrough
    // （旧页淡出 90ms，新页 92%→1 缩放淡入 210ms）；弱机保底=交叉淡化+新页 96%→1 缩放（仍是纵深淡入，不再纯透明）。
    // 铁律：全部有界 VPA ≤300ms（v1.19.7 弱机事故教训）；600ms 兜底结算幂等。
    next.animate().cancel();next.setTranslationX(0);next.setScaleX(1f);next.setScaleY(1f);next.setAlpha(1f);if(previous==null){next.setEnabled(true);return;}previous.animate().cancel();previous.setTranslationX(0);previous.setScaleX(1f);previous.setScaleY(1f);previous.setAlpha(1f);previous.setEnabled(false);next.setEnabled(false);/** F4:260ms 超时兜底——正常 endAction 与兜底幂等;修复动画被打断后结算丢失导致的旧页残留/新页整页不可点 */ui.postDelayed(()->{if(pageFrame==next)settlePageTransition();},600);if(!motionEnabled()){settlePageTransition();return;}
    android.view.animation.Interpolator emphasized=new android.view.animation.PathInterpolator(0.05f,0.7f,0.1f,1f),accel=new android.view.animation.PathInterpolator(0.3f,0f,0.8f,0.15f);
    /* [DFW-79] 切 tab / 原位刷新用 fadeThrough —— 这两页之间**没有空间关系**，淡入淡出语义是对的，
       不能像推入那样用位移（会让用户以为"层级变了"）。但把缩放从 0.92 收到 0.96、旧页淡出从 90ms 收到 70ms：
       0.92 那档缩放太"缩"，在黑底上像整页在往里塌。 */if(direction==0){next.setAlpha(0f);next.setScaleX(0.96f);next.setScaleY(0.96f);next.post(()->{if(pageFrame!=next)return;previous.animate().alpha(0f).setDuration(100).setInterpolator(standardEase()).start();next.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(emphasized).withEndAction(()->finishPageTransition(previous,next)).start();});return;}
    if(weakDevice||perfLowMotion){next.setAlpha(0f);next.setScaleX(0.96f);next.setScaleY(0.96f);next.post(()->{if(pageFrame!=next)return;previous.animate().alpha(0f).setDuration(150).setInterpolator(standardEase()).start();next.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(200).setInterpolator(standardEase()).withEndAction(()->finishPageTransition(previous,next)).start();});return;}
    // [DFW-73] 子页面推入/返回改成**整页实体位移**（用户 2026-10-01："现在的过渡像碎纸条"）。
    // 病根：旧实现在黑色底上让两页同时"位移+淡出"，两层半透明内容叠在黑底上被看成两条互不相干的纸片。
    // 新做法遵循真正的层级语义——**下面那一页永远不透明地待在原地**，只有盖在上面那一页在动：
    //   推入：新页从右侧整页滑入（不淡入），旧页原地退回并压暗 → 纵深；
    //   返回：上层页向右滑走并淡出，露出下面那页 → 抽纸感。
    /* [DFW-79] 转场重写（用户 2026-10-01）：
       > "我有点不太喜欢那种动画效果了，就是那种有点渐隐式的……每次打开或关闭的时候，
       >  总感觉不够丝滑。我希望你给它改成很有效率感、很顺畅、回馈感不错的那种动画效果。"
    
       病根就是 **alpha**：旧版返回时"上一页滑走 + 淡出"，推入时"下层页淡到 55% + 缩到 0.94"。
       在 OLED 纯黑底上，淡出不是"渐隐"而是"变暗糊掉"——两层半透明内容叠在黑底上，
       观感就是用户说的"不够丝滑"。
    
       新做法 = **两页同时实体位移、谁也不淡、谁也不缩**（iOS 推入式视差）：
         推入：新页从右侧整屏滑入（+100% → 0），旧页向左让出 30%；
         返回：上层页向右整屏滑走（0 → +100%），下层页从 -30% 归位到 0。
       因为两页是**刚性连接**的（一个进多少、另一个就退多少），所以有明确的层级与方向感，
       不会像"碎纸条"。全部只动 translationX，不碰 alpha / scale。
       时长 280/240ms：比旧版 300/240 略快，取"有效率感"；退场 ≤ 入场，符合规范自检项。 */
    int travel=Math.max(dp(120),safeContentWidth());
    int parallax=Math.max(dp(48),(int)(travel*0.30f));
    if(direction<0){
      next.setAlpha(1f);next.setScaleX(1f);next.setScaleY(1f);next.setTranslationX(-parallax);
      previous.setAlpha(1f);previous.setScaleX(1f);previous.setScaleY(1f);previous.setTranslationX(0f);
      previous.post(()->{
        if(pageFrame!=next)return;
        previous.animate().translationX(travel).setDuration(240).setInterpolator(emphasized).withEndAction(()->finishPageTransition(previous,next)).start();
        next.animate().translationX(0f).setDuration(240).setInterpolator(emphasized).start();
      });
      return;
    }
    next.setAlpha(1f);next.setScaleX(1f);next.setScaleY(1f);next.setTranslationX(travel);
    previous.setAlpha(1f);previous.setScaleX(1f);previous.setScaleY(1f);previous.setTranslationX(0f);
    next.post(()->{
      if(pageFrame!=next)return;
      previous.animate().translationX(-parallax).setDuration(280).setInterpolator(emphasized).start();
      next.animate().translationX(0f).setDuration(280).setInterpolator(emphasized).withEndAction(()->finishPageTransition(previous,next)).start();
    });
  }
  void finishPageTransition(View previous,View next){if(pageFrame!=next)return;settlePageTransition();}
  void settlePageTransition(){if(pageHost==null||pageFrame==null)return;for(int i=pageHost.getChildCount()-1;i>=0;i--)pageHost.getChildAt(i).animate().cancel();for(int i=pageHost.getChildCount()-1;i>=0;i--)if(pageHost.getChildAt(i)!=pageFrame)pageHost.removeViewAt(i);pageFrame.setTranslationX(0);pageFrame.setScaleX(1f);pageFrame.setScaleY(1f);pageFrame.setAlpha(1f);pageFrame.setEnabled(true);syncBackCallbackEnabled();}
  boolean homeSearchConfigurationActive(){return pageKind==0&&primaryDestination==0&&homeStage!=null&&homeSearchBox!=null&&homeHistory!=null&&(homeSearchFocused||homeSearchRequested);}
  void restoreHomeSearchForConfiguration(int session,int scrollY,boolean historyOnly){homeSearchRequested=true;homeSearchHistoryOnly=historyOnly;showHomeLanding();showHomeSearchMode(false);if(!historyOnly)refreshSearchUi(session);ScrollView restored=pageScroll;if(restored!=null)restored.post(()->{if(session==searchGeneration&&pageKind==0&&homeSearchFocused)restored.scrollTo(0,Math.max(0,scrollY));});}
  @Override public void onConfigurationChanged(android.content.res.Configuration next){boolean restoreSearch=homeSearchConfigurationActive(),restoreSettings=pageKind==4&&primaryDestination==3;int session=searchGeneration,scrollY=pageScroll==null?0:pageScroll.getScrollY();boolean historyOnly=homeSearchHistoryOnly;int old=BG;super.onConfigurationChanged(next);applySystemColors();if(host!=null)host.requestApplyInsets();suppressUiMotion=true;try{settlePageTransition();if(restoreSettings)showSettings();else if(restoreSearch)restoreHomeSearchForConfiguration(session,scrollY,historyOnly);else if(old!=BG){invalidateRetainedSourceListPage();if(getPreferences(0).getBoolean("accepted",false))restorePageForTheme();}else{reflowNavBall();if(root!=null)root.post(this::reflowVisibleLayouts);}}finally{suppressUiMotion=false;}}
  void reflowVisibleLayouts(){if(root==null)return;int columns=itemColumns();if(itemsGrid!=null)reflowGrid(itemsGrid,columns,false);if(liveGrid!=null){liveColumns=columns;reflowGrid(liveGrid,columns,false);}if(sourceGrid!=null)reflowGrid(sourceGrid,columns,true);if(historyGrid!=null)reflowHistoryGrid();if(searchDragBar!=null)searchDragBar.invalidate();if(homeStage!=null&&homeSearchBox!=null){int width=homeSearchWidth();ViewGroup.LayoutParams raw=homeSearchBox.getLayoutParams();if(raw instanceof ViewGroup.MarginLayoutParams){raw.width=width;homeSearchBox.setLayoutParams(raw);}fitHomeSearchControls(width);if(homeHistory!=null){ViewGroup.LayoutParams historyRaw=homeHistory.getLayoutParams();if(historyRaw instanceof ViewGroup.MarginLayoutParams){historyRaw.width=width;homeHistory.setLayoutParams(historyRaw);}}if(homeLibsBand!=null){ViewGroup.LayoutParams bandRaw=homeLibsBand.getLayoutParams();if(bandRaw instanceof ViewGroup.MarginLayoutParams){bandRaw.width=width;homeLibsBand.setLayoutParams(bandRaw);}}if(homeRecommendationsScroll!=null){ViewGroup.LayoutParams recommendationRaw=homeRecommendationsScroll.getLayoutParams();if(recommendationRaw instanceof ViewGroup.MarginLayoutParams){recommendationRaw.width=width;homeRecommendationsScroll.setLayoutParams(recommendationRaw);}}homeStage.post(()->settleHomeSearchPosition(homeSearchFocused));}if(toastScroll!=null&&host!=null){ViewGroup.LayoutParams raw=toastScroll.getLayoutParams();if(raw instanceof FrameLayout.LayoutParams){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)raw;p.width=Math.max(dp(1),Math.min(dp(286),safeContentWidth()-dp(24)));toastScroll.setLayoutParams(p);}updateToastViewport();}}
  int historyCellWidth(int columns){int available=homeHistory!=null&&homeHistory.getWidth()>0?homeHistory.getWidth():homeSearchWidth();return Math.max(dp(1),(available-dp(6)*columns)/Math.max(1,columns));}
  void reflowHistoryGrid(){int columns=wideNavigation()?2:1,old=historyGrid.getColumnCount(),width=historyCellWidth(columns);if(columns>old)historyGrid.setColumnCount(columns);for(int i=0;i<historyGrid.getChildCount();i++){GridLayout.LayoutParams cell=(GridLayout.LayoutParams)historyGrid.getChildAt(i).getLayoutParams();cell.width=width;cell.columnSpec=GridLayout.spec(i%columns);cell.rowSpec=GridLayout.spec(i/columns);historyGrid.getChildAt(i).setLayoutParams(cell);}if(columns<old)historyGrid.setColumnCount(columns);historyGrid.requestLayout();}
  void reflowGrid(GridLayout grid,int columns,boolean sources){int old=grid.getColumnCount();if(columns>old)grid.setColumnCount(columns);for(int i=0;i<grid.getChildCount();i++){GridLayout.LayoutParams p=itemLayout(i,columns);p.height=sources&&showSourceLinks?ViewGroup.LayoutParams.WRAP_CONTENT:dp(sources?64:104);grid.getChildAt(i).setLayoutParams(p);}if(columns<old)grid.setColumnCount(columns);grid.requestLayout();}
  void clearHomeBrandCiallo(){homeBrandCialloGeneration++;for(View particle:new ArrayList<>(homeBrandCialloViews)){particle.animate().cancel();ViewParent parent=particle.getParent();if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(particle);}homeBrandCialloViews.clear();}
  void showHomeLanding(){primaryBase(0);pageKind=0;activeSource=home;clearFolderTrail();folderRootSources=false;systemBackAction=null;root.setFocusableInTouchMode(true);root.requestFocus();
    // v1.20.0 改版：品牌头整体移除，搜索框钉在页面顶部（不随内容滚动），下方 ScrollView 装库分类竖排流式胶囊+搜索历史/结果——
    // 点搜索直接弹键盘，不再有"落位→吸顶"位移动画
    LinearLayout stage=new LinearLayout(this);stage.setOrientation(LinearLayout.VERTICAL);stage.setGravity(Gravity.CENTER_HORIZONTAL);homeStage=stage;root.addView(stage,new LinearLayout.LayoutParams(-1,0,1));
    int searchWidth=homeSearchWidth();
    // [DFW-70] 首页铃铛已移除——用户 2026-09-30 反馈两个问题：
    // ① 位置放错了（压在搜索框上方，不在"右上角"）；
    // ② **关掉公告它就消失**（因为它只在有未读时出现，看过一次就没了），
    //    用户会以为"公告功能不见了"。
    // 现在公告入口挪到右下角悬浮球展开菜单的第 6 项（设置下面），常驻可见、带红点。
    // 回到首页时按**缓存的**快照立刻恢复红点状态；否则从别处返回首页会先空一下再亮，看着像闪。
    refreshNoticeBell();
    homeScroll=new ScrollView(this);homeScroll.setFillViewport(true);homeScroll.setVerticalScrollBarEnabled(false);homeScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);homeColumn=new LinearLayout(this);homeColumn.setOrientation(LinearLayout.VERTICAL);homeColumn.setGravity(Gravity.CENTER_HORIZONTAL);homeColumn.setPadding(0,0,0,dp(28));homeScroll.addView(homeColumn,new ScrollView.LayoutParams(-1,-2));// v1.22.3 边距统一：左右内边距 2dp→0，库胶囊左缘与搜索框同 16dp（原先多缩 2dp 造成"左边被黑边挤住"观感）
    FrameLayout searchBox=new FrameLayout(this);homeSearchBox=searchBox;searchBox.setClipChildren(true);searchBox.setClipToPadding(true);searchBox.setBackground(searchBoxShape(false));search=new EditText(this);search.setSingleLine();search.setTextColor(TEXT);search.setHintTextColor(MUTED);search.setHint("搜索一下");search.setTextSize(16);search.setBackgroundColor(Color.TRANSPARENT);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);searchBox.addView(search,new FrameLayout.LayoutParams(-1,-1));searchBack=iconButton(R.drawable.ic_back,"返回");searchBack.setVisibility(View.GONE);searchBack.setOnClickListener(v->exitHomeSearchFocus());FrameLayout.LayoutParams backParams=new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.START|Gravity.CENTER_VERTICAL);backParams.setMargins(dp(4),0,0,0);searchBox.addView(searchBack,backParams);loadSourceCategories();List<String> searchCategories=new ArrayList<>();searchCategories.add("全部");searchCategories.addAll(sourceCategories.keySet());if(!searchCategories.contains(sessionSearchCategory))sessionSearchCategory="全部";Runnable submitHomeSearch=()->{if(!homeSearchFocused){enterHomeSearchFocus();search.requestFocus();}else runSearch(search.getText().toString().trim());};SearchCategoryPicker homeCategory=new SearchCategoryPicker(searchCategories,sessionSearchCategory,value->sessionSearchCategory=value,submitHomeSearch);homeCategoryPicker=homeCategory;homeSearchCategory=homeCategory.view();homeSearchCategory.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View view){}public void onViewDetachedFromWindow(View view){homeCategory.dismiss();}});FrameLayout.LayoutParams categoryParams=new FrameLayout.LayoutParams(homeSearchCategoryWidth(searchWidth),dp(52),Gravity.END|Gravity.CENTER_VERTICAL);categoryParams.setMargins(0,0,0,0);searchBox.addView(homeSearchCategory,categoryParams);fitHomeSearchControls(searchWidth);homeStage.addView(searchBox,new LinearLayout.LayoutParams(searchWidth,dp(52)));// v1.6.1（R-B2）：栏高统一 52dp；v1.20.0 钉顶不随内容滚动
    View gap2=new View(this);homeStage.addView(gap2,new LinearLayout.LayoutParams(-1,dp(12)));
    homeStage.addView(homeScroll,new LinearLayout.LayoutParams(-1,0,1));
    // v1.20.0 软件库入口改版：横滚胶囊条 → 竖排流式胶囊（用户反馈左右滑太麻烦）——全部库列在搜索框下方空白区，
    // 随页面上下滚动挑选，搜索框钉在顶部不动；搜索态整块 GONE 让位给历史/结果列表
    if(!libraries.isEmpty()){
      LinearLayout libsFlow=new LinearLayout(this);libsFlow.setOrientation(LinearLayout.VERTICAL);
      int budget=searchWidth-dp(4);LinearLayout row=null;int rowUsed=0;
      for(Models.Source lib:libraries){
        TextView chip=text(lib.title,13,TEXT);chip.setMaxLines(1);chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setTypeface(AppFonts.bold(this));
        GradientDrawable chipBg=solidShape(SURFACE,21);chipBg.setStroke(dp(1),BORDER);chip.setBackground(chipBg);
        chip.setPadding(dp(14),dp(10),dp(14),dp(10));chip.setClickable(true);chip.setFocusable(true);applePressScale(chip);// v1.19.7 库胶囊按压形变
        chip.setContentDescription("打开软件库 "+lib.title);
        chip.setOnClickListener(v->{pageDirection=1;openRecommendedHome(lib);});
        chip.setMaxWidth(budget-dp(28));
        int chipW=(int)Math.ceil(chip.getPaint().measureText(lib.title.toString()))+dp(30);
        if(chipW>budget)chipW=budget;
        if(row==null||rowUsed+chipW+(row.getChildCount()>0?dp(8):0)>budget){row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-2,-2);rowLp.bottomMargin=dp(8);libsFlow.addView(row,rowLp);rowUsed=0;}
        LinearLayout.LayoutParams chipLp=new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,dp(42));
        if(row.getChildCount()>0)chipLp.leftMargin=dp(8);
        row.addView(chip,chipLp);rowUsed+=chipW+(row.getChildCount()>1?dp(8):0);
      }
      homeLibsBand=libsFlow;
      homeColumn.addView(libsFlow,new LinearLayout.LayoutParams(searchWidth,LinearLayout.LayoutParams.WRAP_CONTENT));
    }
    homeHistory=new LinearLayout(this);homeHistory.setOrientation(LinearLayout.VERTICAL);homeHistory.setVisibility(View.GONE);homeColumn.addView(homeHistory,new LinearLayout.LayoutParams(searchWidth,-2));
    String restored; synchronized(globalSearch){restored=globalSearch.query;}if(!restored.isEmpty()){search.setText(restored);search.setSelection(restored.length());}search.setOnFocusChangeListener((v,focused)->{if(focused)enterHomeSearchFocus();});search.setOnClickListener(v->enterHomeSearchFocus());search.setOnEditorActionListener((v,a,e)->{runSearch(search.getText().toString().trim());return true;});if(homeSearchRequested)homeColumn.post(()->showHomeSearchMode(false));}
  void enterHomeSearchFocus(){showHomeSearchMode(true);}
  void settleHomeSearchPosition(boolean focused){if(homeStage==null||homeSearchBox==null)return;clearHomeBrandCiallo();if(homeHistory!=null&&focused)homeHistory.setLayoutParams(new LinearLayout.LayoutParams(homeSearchWidth(),homeHistoryH()));}// v1.20.0：位移落位机制随品牌头退役，只保留搜索历史列表高度校准
  /**
   * 搜索结果区的高度 = **整个滚动区高度**。
   *
   * [DFW-95] 这里原来写的是 `vh - dp(64)`，注释说「64=框52+间距12」——
   * 那个假设是**错的**：搜索框（52dp）和它下面的间距（12dp）是 `homeScroll` 的**兄弟节点**，
   * 本来就在滚动区**外面**（实测：搜索框 top=0..137px、间距 137..169px、`homeScroll` 从 169px 才开始）。
   * 再扣一次 64dp，结果就是**搜索页底部永久留下 64dp 空白**——
   * 任何内容都到不了那里，列表滚到底也补不上。用户 2026-10-02 的原话是
   * 「搜索页面搜索后，底部出现了黑色留白，很丑」。
   *
   * 实测证据（`@GraphicsMode(NATIVE)` 截图 + 逐行扫像素，视口 1078×2338 @2.625）：
   * 改前结果区在 y=2119 结束，下方 218px（83dp）恒为空白；
   * 改后结果区延伸到 y≈2275，只剩导航条 inset 那一条（正常）。
   */
  int homeHistoryH(){int vh=homeScroll!=null&&homeScroll.getHeight()>0?homeScroll.getHeight():dp(500);return Math.max(dp(240),vh);
  }
  void showHomeSearchMode(boolean focusInput){if(homeStage==null)return;homeSearchRequested=true;if(homeSearchFocused){if(focusInput&&search!=null)search.requestFocus();return;}homeSearchFocused=true;systemBackAction=this::exitHomeSearchFocus;syncBackCallbackEnabled();searchBack.setVisibility(View.VISIBLE);fitHomeSearchControls(homeSearchWidth());if(homeSearchBox!=null)homeSearchBox.setBackground(searchBoxShape(true));// v1.20.0：搜索态让位给历史/结果列表。// [DFW-95] 与退出方向对称，同样走交叉淡化（原来也是硬切）
    String query; synchronized(globalSearch){query=globalSearch.query;}if(!homeSearchHistoryOnly&&!query.isEmpty()&&query.equals(search.getText().toString().trim()))renderSearchResults();else renderSearchHistory();if(homeScroll!=null)homeHistory.setLayoutParams(new LinearLayout.LayoutParams(homeSearchWidth(),homeHistoryH()));crossFadeHomeSection(homeLibsBand,homeHistory);if(focusInput&&search!=null){search.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search,0);}}// v1.20.0：搜索框钉顶，点搜索直接弹键盘（显式 showSoftInput——首次聚焦走布局变更路径时系统自动弹会失效），无任何位移动画
  /**
   * [DFW-95] 搜索态 ↔ 软件库主页的**原位交叉淡化**。
   *
   * 用户原话：「从搜索界面，我如果使用了安卓的返回，它没有任何的动画效果，然后直接返回到了我的
   * 软件库主页……我希望你可以修复一下，给它加个，比如什么动画效果吧。渐隐啥的。」
   *
   * 根因：`exitHomeSearchFocus()` 原来是**直接把两个 View 的可见性一开一关**（硬切）。
   *
   * 节奏与曲线**照抄全站现成的 `transitionSourceFilter`**（退 `DUR_EXIT_FAST`=100ms → 换内容 →
   * 进 `DUR_SMALL`=150ms，曲线 `standardEase()`），不另发明数值 —— 用户 2026-09-25 定的规矩是
   * 动效只用 `docs/design/wear-ui-system.md` 里的 token，触摸路径全程 VPA ≤ 300ms。
   *
   * 为什么**先退后进、而不是同时交叉**：两者是同一个 LinearLayout 里的兄弟节点，
   * 同时可见会让列表高度瞬间叠加、滚动位置跳一下。同一时刻只留一个可见就没有这个问题。
   */
  void crossFadeHomeSection(View out,View in){
    if(out==null||in==null||!motionEnabled()){
      if(out!=null){out.animate().cancel();out.setAlpha(1f);out.setVisibility(View.GONE);}
      if(in!=null){in.animate().cancel();in.setVisibility(View.VISIBLE);in.setAlpha(1f);}
      return;
    }
    out.animate().cancel();
    in.animate().cancel();
    in.setVisibility(View.GONE);
    out.animate().alpha(0f).setDuration(DUR_EXIT_FAST).setInterpolator(standardEase()).withEndAction(()->{
      out.setVisibility(View.GONE);
      out.setAlpha(1f);
      in.setAlpha(0f);
      in.setVisibility(View.VISIBLE);
      in.animate().alpha(1f).setDuration(DUR_SMALL).setInterpolator(standardEase()).start();
    }).start();
    /*
     * [DFW-95] **结算兜底，不能省。**
     *
     * 上面靠 `withEndAction` 把 `in` 显示出来。可动画一旦被打断
     * （快速连按返回、Activity 转后台、系统"动画时长 0"），`withEndAction` 就可能不执行 ——
     * 结果是 `in` 永远停在 GONE，用户看到的是**一整页空白**。
     *
     * 这与 `animatePage` 里那条 600ms 兜底是同一个道理（那里的注释写得很清楚：
     * "修复动画被打断后结算丢失导致的旧页残留/新页整页不可点"）。
     * 兜底做成**幂等**的：正常走完时这里什么都不改。
     */
    ui.postDelayed(()->{
      if(in.getVisibility()==View.VISIBLE)return;
      out.animate().cancel();
      in.animate().cancel();
      out.setVisibility(View.GONE);
      out.setAlpha(1f);
      in.setVisibility(View.VISIBLE);
      in.setAlpha(1f);
    },600);
  }

  void exitHomeSearchFocus(){if(!homeSearchFocused){navigateHome();return;}invalidateSearchRenderSurface();homeSearchRequested=false;homeSearchHistoryOnly=true;homeSearchFocused=false;systemBackAction=null;syncBackCallbackEnabled();pageDirection=1;searchBack.setVisibility(View.GONE);fitHomeSearchControls(homeSearchWidth());if(homeSearchBox!=null)homeSearchBox.setBackground(searchBoxShape(false));crossFadeHomeSection(homeHistory,homeLibsBand);search.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);if(homeScroll!=null)homeScroll.smoothScrollTo(0,0);clearHomeBrandCiallo();}
  List<String> searchHistory(){LinkedHashSet<String> unique=new LinkedHashSet<>();try{org.json.JSONArray values=new org.json.JSONArray(getSharedPreferences("search_history",MODE_PRIVATE).getString("items","[]"));for(int i=0;i<values.length();i++){String value=values.optString(i).trim();if(!value.isEmpty())unique.add(value);}}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}return new ArrayList<>(unique);}
  void writeSearchHistory(List<String> history){try{org.json.JSONArray values=new org.json.JSONArray();for(int i=0;i<Math.min(12,history.size());i++)values.put(history.get(i));getSharedPreferences("search_history",MODE_PRIVATE).edit().putString("items",values.toString()).apply();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}}
  void saveSearchHistory(String query){List<String> history=searchHistory();history.remove(query);history.add(0,query);writeSearchHistory(history);}
  void removeSearchHistory(String query){List<String> history=searchHistory();history.remove(query);writeSearchHistory(history);renderSearchHistory();}
  void confirmClearSearchHistory(){AlertDialog prompt=new AlertDialog.Builder(this).setTitle("清空搜索记录？").setMessage("全部搜索记录将被删除。").setNegativeButton("取消",null).setPositiveButton("清空",(dialog,which)->{writeSearchHistory(Collections.emptyList());renderSearchHistory();}).create();showRounded(prompt);}
  void renderSearchHistory(){if(homeHistory==null)return;homeHistory.removeAllViews();historyGrid=null;List<String> history=searchHistory();if(history.isEmpty()){TextView empty=text("暂无任何记录",14,MUTED);empty.setGravity(Gravity.CENTER);homeHistory.addView(empty,new LinearLayout.LayoutParams(-1,-1));return;}LinearLayout historyBody=new LinearLayout(this);historyBody.setOrientation(LinearLayout.VERTICAL);historyBody.setClipChildren(true);LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);TextView heading=text("搜索记录",12,MUTED);titleRow.addView(heading,new LinearLayout.LayoutParams(0,dp(44),1));ImageButton clear=iconButton(R.drawable.ic_delete_record,"清空历史记录");clear.setOnClickListener(v->confirmClearSearchHistory());titleRow.addView(clear,new LinearLayout.LayoutParams(dp(42),dp(44)));historyBody.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(44)));int columns=wideNavigation()?2:1,cellWidth=historyCellWidth(columns);historyGrid=new GridLayout(this);historyGrid.setColumnCount(columns);historyGrid.setClipChildren(true);for(int i=0;i<history.size();i++){String query=history.get(i);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setClipChildren(true);TextView label=text(query,15,TEXT);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setPadding(dp(4),0,dp(4),0);label.setClickable(true);label.setFocusable(true);label.setOnClickListener(v->{search.setText(query);search.setSelection(query.length());runSearch(query);});row.addView(label,new LinearLayout.LayoutParams(0,dp(48),1));ImageButton remove=iconButton(R.drawable.ic_close,"删除搜索记录 "+query);remove.setOnClickListener(v->removeSearchHistory(query));row.addView(remove,new LinearLayout.LayoutParams(dp(42),dp(42)));GridLayout.LayoutParams cell=new GridLayout.LayoutParams();cell.width=cellWidth;cell.height=dp(50);cell.columnSpec=GridLayout.spec(i%columns);cell.rowSpec=GridLayout.spec(i/columns);cell.setMargins(dp(3),0,dp(3),0);historyGrid.addView(row,cell);}historyBody.addView(historyGrid,new LinearLayout.LayoutParams(-1,-2));ScrollView historyScroll=new ScrollView(this);historyScroll.addView(historyBody);homeHistory.addView(draggableList(historyScroll,historyBody,"拖动搜索记录"),new LinearLayout.LayoutParams(-1,0,1));}
  void prefetchHome(){if(homePrefetchStarted||home.url.isEmpty())return;homePrefetchStarted=true;io.execute(()->{try{Models.Folder cached=core.browse(home.url,home.password,false);homeItems=new ArrayList<>(cached.items);}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}try{Models.Folder fresh=core.browse(home.url,home.password,true);homeItems=new ArrayList<>(fresh.items);}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}});}
  TextView recommendedSourceCard(Models.Source source){TextView card=text(source.title,14,BG);card.setGravity(Gravity.CENTER);card.setBackground(filterRipple(solidShape(PRIMARY,16)));card.setClickable(true);card.setFocusable(true);card.setContentDescription(source.title);card.setOnClickListener(v->openRecommendedHome(source));return card;}
  void openRecommendedHome(Models.Source source){pageDirection=1;resetFolderTrail(source);folderRootSources=false;showFolderPage(activeFolderState.source,true);}
  void restorePageForTheme(){if(navBall!=null)navBall.refreshColors();int kind=pageKind;String sourceQuery=sourceFilter==null?"":sourceFilter.getText().toString(),historyQuery=downloadQuery;Models.Source folder=activeSource;if(kind==0)showHomeLanding();else if(kind==1){showSources();if(!sourceQuery.isEmpty())ui.post(()->{if(sourceFilter!=null)sourceFilter.setText(sourceQuery);});}else if(kind==2){showDownloads();if(!historyQuery.isEmpty())ui.post(()->{EditText input=sourceFilter;if(input!=null)input.setText(historyQuery);});}else if(kind==3&&folder!=null){captureActiveFolderState();FolderPageState state=currentFolderState();if(state!=null&&state.folder!=null)restoreFolderPageState(state);else reopenUnrenderedFolderForTheme(folder);}else if(kind==4)showSettings();else if(kind==6)showTools();}
  void reopenUnrenderedFolderForTheme(Models.Source source){if(currentFolderState()==null)resetFolderTrail(source);showFolderPage(source,false);}
  void buildFolderScaffold(Models.Source source){base();pageKind=3;LinearLayout header=new LinearLayout(this);pageHeaderRow=header;header.setGravity(Gravity.CENTER_VERTICAL);ImageButton back=iconButton(R.drawable.ic_back,"返回");back.setOnClickListener(systemBackClick);header.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));header.addView(folderSourceSearchBox(),new LinearLayout.LayoutParams(0,dp(46),1));root.addView(header,new LinearLayout.LayoutParams(-1,dp(52)));root.addView(folderPathView(source),new LinearLayout.LayoutParams(-1,dp(44)));progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);progress.setVisibility(View.GONE);root.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.CENTER_VERTICAL);status=text("",12,MUTED);statusRight=text("",11,MUTED);statusRight.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);searchPauseButton=toolbarTextButton("暂停");searchPauseButton.setVisibility(View.GONE);searchPauseButton.setContentDescription("暂停当前源搜索");searchPauseButton.setOnClickListener(v->toggleCurrentSourceSearchPaused(sourceSearchSession));statusRow.addView(status,new LinearLayout.LayoutParams(0,dp(30),1));statusRow.addView(statusRight,new LinearLayout.LayoutParams(-2,dp(30)));statusRow.addView(searchPauseButton,new LinearLayout.LayoutParams(dp(52),dp(30)));root.addView(statusRow,new LinearLayout.LayoutParams(-1,dp(30)));folderPullScroll=new FolderPullScrollView(this);pageScroll=folderPullScroll;content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);pageScroll.addView(content);folderPullFrame=draggableFolderList(folderPullScroll,content,"拖动目录列表");root.addView(folderPullFrame,new LinearLayout.LayoutParams(-1,0,1));}
  View folderPathView(Models.Source source){HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(true);scroll.setContentDescription("当前路径 "+folderPath(source));LinearLayout path=new LinearLayout(this);path.setGravity(Gravity.CENTER_VERTICAL);path.setPadding(dp(2),0,dp(8),0);Models.Source rootSource=folderTrail.isEmpty()?source:folderTrail.get(0).source;List<String> origin=sourceOriginPath(rootSource);boolean hasOrigin=!origin.isEmpty(),rootCurrent=!hasOrigin&&folderTrail.size()<=1;String rootTitle=rootSource==null||rootSource.title.isEmpty()?"根目录":rootSource.title;TextView rootPath=text("路径",11,rootCurrent?PRIMARY:MUTED);rootPath.setGravity(Gravity.CENTER);rootPath.setMinWidth(dp(44));rootPath.setSelected(rootCurrent);if(rootCurrent)rootPath.setTypeface(AppFonts.bold(this));rootPath.setContentDescription(rootCurrent?"当前位于根目录 "+rootTitle:"路径起点");rootPath.setClickable(hasOrigin||!rootCurrent);rootPath.setFocusable(hasOrigin||!rootCurrent);if(hasOrigin)rootPath.setOnClickListener(v->navigateToOriginSourceList());else if(!rootCurrent)rootPath.setOnClickListener(v->navigateToBreadcrumb(0));rootPath.setLongClickable(!folderTrail.isEmpty());if(!folderTrail.isEmpty())rootPath.setOnLongClickListener(v->{showBreadcrumbChildren(0,v);return true;});path.addView(rootPath,new LinearLayout.LayoutParams(-2,dp(44)));if(hasOrigin){for(int index=0;index<origin.size()-1;index++)addStaticPathPart(path,origin.get(index));addPathPart(path,origin.get(origin.size()-1),folderTrail.size()==1,0);for(int index=1;index<folderTrail.size();index++){FolderPageState state=folderTrail.get(index);addPathPart(path,state.source.title,index==folderTrail.size()-1,index);}}else{for(int index=0;index<folderTrail.size();index++){FolderPageState state=folderTrail.get(index);addPathPart(path,state.source.title,index==folderTrail.size()-1,index);}if(folderTrail.isEmpty()&&source!=null)addPathPart(path,source.title,true,0);}scroll.addView(path,new HorizontalScrollView.LayoutParams(-2,-1));scroll.post(()->scroll.fullScroll(View.FOCUS_RIGHT));return scroll;}
  LinearLayout folderOriginalLink(Models.Source source){boolean composite=compositeSource(source)&&!transientLinkSource(source);String url=source==null?"":preferredLanzouUrl(source.url),password=source==null?"":source.password,label=composite?"自建合集 · "+source.members.size()+"项":"原链接  "+url;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView link=text(label,10,composite?MUTED:PRIMARY);link.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);link.setSingleLine(true);link.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);link.setTextIsSelectable(true);link.setClickable(!composite);link.setFocusable(!composite);link.setContentDescription(composite?label:"打开当前原链接"+(password.isEmpty()?"":"，先复制密码"));if(!composite)link.setOnClickListener(v->openRecognizedLink(url,password));row.addView(link,new LinearLayout.LayoutParams(0,-1,1));if(!composite&&!password.isEmpty()){TextView secret=text("密码："+password,10,MUTED);secret.setPadding(dp(8),0,dp(6),0);secret.setTextIsSelectable(true);secret.setContentDescription("当前源密码");row.addView(secret,new LinearLayout.LayoutParams(-2,-1));}return row;}
  View folderSourceSearchBox(){FrameLayout sourceBox=new FrameLayout(this);sourceBox.setBackground(shape(SURFACE,22));sourceSearch=new EditText(this);sourceSearch.setSingleLine();sourceSearch.setTextColor(TEXT);sourceSearch.setHintTextColor(MUTED);sourceSearch.setHint("搜索当前源");sourceSearch.setContentDescription("搜索当前源");sourceSearch.setTextSize(13);sourceSearch.setBackgroundColor(Color.TRANSPARENT);sourceSearch.setPadding(dp(16),0,dp(48),0);if(!currentSourceQuery.isEmpty()){sourceSearch.setText(currentSourceQuery);sourceSearch.setSelection(currentSourceQuery.length());}sourceSearch.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){queueCurrentSourceSearch(s.toString().trim());}public void afterTextChanged(Editable e){}});sourceBox.addView(sourceSearch,new FrameLayout.LayoutParams(-1,-1));ImageButton sourceFind=iconButton(R.drawable.ic_search,"搜索当前源");sourceFind.setOnClickListener(v->{sourceSearch.requestFocus();queueCurrentSourceSearch(sourceSearch.getText().toString().trim());});FrameLayout.LayoutParams sfp=new FrameLayout.LayoutParams(dp(48),dp(46),Gravity.END|Gravity.CENTER_VERTICAL);sourceBox.addView(sourceFind,sfp);return sourceBox;}
  void addPathPart(LinearLayout path,String title,boolean current,int index){if(title==null||title.isEmpty())return;TextView separator=text("›",11,MUTED);separator.setGravity(Gravity.CENTER);path.addView(separator,new LinearLayout.LayoutParams(dp(18),dp(44)));TextView part=text(title,12,current?PRIMARY:MUTED);part.setSingleLine(true);part.setGravity(Gravity.CENTER);part.setPadding(dp(8),0,dp(8),0);part.setMinWidth(dp(44));part.setClickable(!current);part.setFocusable(!current);part.setSelected(current);part.setLongClickable(true);part.setContentDescription((current?"当前目录 ":"返回到 ")+title+"，长按切换下级目录");part.setOnLongClickListener(v->{showBreadcrumbChildren(index,v);return true;});if(current)part.setTypeface(AppFonts.bold(this));else part.setOnClickListener(v->navigateToBreadcrumb(index));path.addView(part,new LinearLayout.LayoutParams(-2,dp(44)));}
  void addStaticPathPart(LinearLayout path,String title){if(title==null||title.isEmpty())return;TextView separator=text("›",11,MUTED);separator.setGravity(Gravity.CENTER);path.addView(separator,new LinearLayout.LayoutParams(dp(18),dp(44)));TextView part=text(title,12,MUTED);part.setSingleLine(true);part.setGravity(Gravity.CENTER);part.setPadding(dp(8),0,dp(8),0);part.setClickable(true);part.setFocusable(true);part.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));part.setContentDescription("返回来源目录 "+title);part.setOnClickListener(v->navigateToOriginSourceList());path.addView(part,new LinearLayout.LayoutParams(-2,dp(44)));}
  List<String> sourceOriginPath(Models.Source source){List<String> parts=new ArrayList<>();if(source==null)return parts;String raw=source.originPath==null?"":source.originPath.trim();if(!raw.isEmpty())for(String value:raw.split("\\s*(?:/|›)\\s*")){String clean=value.trim();if(!clean.isEmpty()&&(parts.isEmpty()||!parts.get(parts.size()-1).equals(clean)))parts.add(clean);}if(!parts.isEmpty()&&!source.title.isEmpty()&&!parts.get(parts.size()-1).equals(source.title))parts.add(source.title);return parts;}

  void showBreadcrumbChildren(int index,View anchor){if(index<0||index>=folderTrail.size()){showNotice("当前层级暂无可切换目录",false);return;}captureActiveFolderState();FolderPageState parent=folderTrail.get(index);LinkedHashMap<String,Models.Item> unique=new LinkedHashMap<>();for(Models.Item item:parent.folderItems)if(item.folder&&!item.url.isEmpty())unique.putIfAbsent(item.url,item);if(unique.size()<2){showNotice("当前层级不足两个可切换目录",false);return;}dismissBreadcrumbChooser();List<Models.Item> choices=new ArrayList<>(unique.values());LinearLayout options=new LinearLayout(this);options.setOrientation(LinearLayout.VERTICAL);options.setPadding(dp(6),dp(5),dp(6),dp(5));options.setBackground(solidShape(SURFACE,16));for(Models.Item item:choices){TextView option=text(item.title,13,TEXT);option.setSingleLine(true);option.setEllipsize(android.text.TextUtils.TruncateAt.END);option.setPadding(dp(14),0,dp(14),0);option.setClickable(true);option.setFocusable(true);option.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));option.setContentDescription("切换到 "+item.title);option.setOnClickListener(v->{dismissBreadcrumbChooser();switchBreadcrumbChild(index,item);});options.addView(option,new LinearLayout.LayoutParams(-1,dp(46)));}ScrollView list=new ScrollView(this);list.setFillViewport(true);list.setVerticalScrollBarEnabled(choices.size()>5);list.addView(options,new ScrollView.LayoutParams(-1,-2));int width=Math.min(dp(330),Math.max(dp(220),root.getWidth()-dp(32))),height=Math.min(dp(244),choices.size()*dp(46)+dp(10));PopupWindow popup=new PopupWindow(list,width,height,true);breadcrumbChooser=popup;popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);popup.setClippingEnabled(true);popup.setElevation(dp(10));popup.setOnDismissListener(()->{if(breadcrumbChooser==popup)breadcrumbChooser=null;});popup.showAsDropDown(anchor,0,-dp(1));if(motionEnabled()){list.setAlpha(0f);list.setTranslationY(-dp(5));list.animate().alpha(1f).translationY(0f).setDuration(150).setInterpolator(standardEase()).start();}}
  void dismissBreadcrumbChooser(){PopupWindow popup=breadcrumbChooser;breadcrumbChooser=null;if(popup!=null&&popup.isShowing())popup.dismiss();}

  void switchBreadcrumbChild(int parentIndex,Models.Item item){dismissBreadcrumbChooser();if(item==null||parentIndex<0||parentIndex>=folderTrail.size())return;captureActiveFolderState();int childIndex=parentIndex+1;FolderPageState cached=folderTrail.size()>childIndex&&folderTrail.get(childIndex).source.url.equals(item.url)?folderTrail.get(childIndex):null;if(cached!=null){while(folderTrail.size()>childIndex+1)folderTrail.remove(folderTrail.size()-1);pageDirection=1;if(cached.folder!=null)restoreFolderPageState(cached);else{activeFolderState=cached;showFolderPage(cached.source,false);}return;}while(folderTrail.size()>childIndex)folderTrail.remove(folderTrail.size()-1);Models.Source nested=new Models.Source();nested.title=item.title;nested.url=item.url;nested.password=item.password;activeFolderState=new FolderPageState(nested);folderTrail.add(activeFolderState);pageDirection=1;showFolderPage(nested,false);}
  String folderPath(Models.Source source){StringBuilder out=new StringBuilder("路径");Models.Source rootSource=folderTrail.isEmpty()?source:folderTrail.get(0).source;List<String> origin=sourceOriginPath(rootSource);if(!origin.isEmpty()){for(String part:origin)out.append("  ›  ").append(part);for(int i=1;i<folderTrail.size();i++)if(!folderTrail.get(i).source.title.isEmpty())out.append("  ›  ").append(folderTrail.get(i).source.title);}else if(!folderTrail.isEmpty())for(FolderPageState state:folderTrail){if(!state.source.title.isEmpty())out.append("  ›  ").append(state.source.title);}else if(source!=null&&!source.title.isEmpty())out.append("  ›  ").append(source.title);return out.toString();}

  void showFolderParseFailure(Models.Source source,String title,Throwable error){String msg=title+" · "+friendlyError(error);if(source!=null&&!source.url.isEmpty())showRounded(new AlertDialog.Builder(this).setTitle(title).setMessage(msg+"\n\n可尝试内部网页打开。").setNegativeButton("关闭",null).setPositiveButton("内部网页",(d,w)->openWebPage(source.url,source.password,false)).create());else showNotice(msg,true);}
  @android.annotation.SuppressLint("BatteryLife") void maybeRequestBatteryExemption(){if(Build.VERSION.SDK_INT<23)return;android.os.PowerManager manager=(android.os.PowerManager)getSystemService(POWER_SERVICE);if(manager==null||manager.isIgnoringBatteryOptimizations(getPackageName()))return;AlertDialog prompt=new AlertDialog.Builder(this).setTitle("允许后台下载？").setMessage("申请电池优化豁免后，切换到其他应用时下载更不容易被系统暂停。你也可以稍后继续使用前台下载。").setNegativeButton("暂不",null).setPositiveButton("申请豁免",(d,w)->{try{startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}catch(Exception error){showNotice("无法打开电池优化设置",true);}}).create();showRounded(prompt);}
  /**
   * DFW-7（v1.22.12）：让死代码活过来。旧实现有两个问题——
   * ① 全仓零调用点，"用户永远收不到新版本提示"；
   * ② 节流只用进程内 AtomicBoolean：同一天反复冷启会反复打网络。
   * 现在：进程内去重 + 落盘 24h 节流；由 {@link #startMainExperience()} 延迟触发（自动、静默），
   * 设置页另有手动入口（手动必定穿透节流并向用户回话）。
   */
  void maybeCheckForUpdates(){
    if(!STARTUP_UPDATE_CHECKED_IN_PROCESS.compareAndSet(false,true))return;
    if(!updateThrottle().isDue(System.currentTimeMillis()))return;
    ownsStartupUpdateCheck=true;
    checkForUpdates(false);
  }
  /** DFW-29：节流策略已迁到 {@link UpdateThrottle}；此处保留转发，外部调用点不变。 */
  UpdateThrottle updateThrottle;
  UpdateThrottle updateThrottle(){
    if(updateThrottle==null)updateThrottle=UpdateThrottle.forContext(this);
    return updateThrottle;
  }
  long lastAutoUpdateCheckMs(){return updateThrottle().lastCheckAt();}
  void rememberAutoUpdateCheck(long at){updateThrottle().remember(at);}
  /** 手动入口：显示当前版本 + 立即检查；不受 24h 节流限制。 */
  void manualCheckForUpdates(){checkForUpdates(true);}
  void checkForUpdates(boolean manual){if(manual)manualUpdateFeedbackRequested.set(true);if(!updateCheckRunning.compareAndSet(false,true)){if(manual)showUpdateNotice("正在检查更新…",false);return;}if(manual)showUpdateNotice("正在检查更新…",false);checkLanzouPlusUpdate(manual);}
  void finishUpdateCheck(boolean manual,Runnable success,Exception error){runOnUiThread(()->{boolean report=manualUpdateFeedbackRequested.getAndSet(false)||manual;if(ownsStartupUpdateCheck)rememberAutoUpdateCheck(System.currentTimeMillis());ownsStartupUpdateCheck=false;updateCheckRunning.set(false);if(isFinishing()||isDestroyed())return;if(error!=null){if(report)showUpdateNotice("检查更新失败："+friendlyError(error),true);return;}if(success!=null)success.run();else if(report)showUpdateNotice(PRODUCT_NAME+" 已是最新版本",false);});}
  /**
   * [DFWX] DFW-59：更新检查改为**自有服务器优先、GitHub 兜底**。
   *
   * 三层决策：
   *   ① 自有后台可达且给出比当前新的版本 → 用它（国内直连，快且能配 soft/force/off）；
   *   ② 后台不可达、或后台版本不更新 → 退回 GitHub（fail-open：后台挂了不该让用户收不到更新）；
   *   ③ 两边都没有更新 → 返回 null，由 finishUpdateCheck 报"已是最新"。
   *
   * **后台模式为 off 时不得再问 GitHub**：那是"我明确要求别提示这一版"，
   * 若继续退到 GitHub 就会把 off 当成摆设。
   */
  UpdateOffer resolveUpdateOffer()throws Exception{
    // 只拉一次：拉两次不但浪费一个请求，还可能在两次之间后台被改动导致判断自相矛盾。
    RemoteConfigClient.Snapshot snapshot=null;
    try{
      snapshot=RemoteConfigClient.fetch();
    }catch(Exception ignored){
      // fail-open：后台不可达时静默退到 GitHub，不打扰用户
    }
    if(snapshot!=null&&snapshot.reachable&&snapshot.release()!=null){
      RemoteConfigClient.Release release=snapshot.release();
      UpdatePromptPolicy.Mode mode=UpdatePromptPolicy.Mode.parse(release.updateMode);
      // 后台明确 off：尊重它，**不再退 GitHub**（否则 off 形同虚设）。
      if(mode==UpdatePromptPolicy.Mode.OFF)return null;
      UpdateOffer fromServer=UpdateOffer.fromRemote(
          release,
          changelogTextFor(snapshot,release.versionName),
          BuildConfig.OFFICIAL_URL,
          alternativePageUrl());
      if(fromServer!=null&&fromServer.versionCode>BuildConfig.VERSION_CODE)return fromServer;
      /* [DFW-73 修正] 后台**可达且明确回答了"最新版本是哪个"**时，就以它为准，不再打 GitHub 兜底。
         旧实现会继续往下走 GitHub，而 GitHub 的 /releases/latest 在我们把全部旧版本标成预发布之后返回 404
         → UpdateClient 抛「无法获取更新信息」→ 用户点「检查更新」看到的是"检查更新失败"，
         而正确答案明明是"已是最新版本"。后台能回答的问题，不该因为兜底源挂掉而报错。 */
      return null;
    }
    // 后台不可达 → GitHub 兜底。
    UpdateClient.UpdateInfo info=UpdateClient.check(BuildConfig.VERSION_NAME);
    return info==null?null:UpdateOffer.fromGithub(info,BuildConfig.OFFICIAL_URL);
  }

  /** 从后台的更新记录里挑出该版本的改动条目，拼成弹窗里的"更新内容"。 */
  String changelogTextFor(RemoteConfigClient.Snapshot snapshot,String versionName){
    if(snapshot==null||versionName==null)return"";
    // enabled=false 的条目在 RemoteConfigClient 解析时已被过滤掉，这里不必再判。
    for(RemoteConfigClient.Changelog entry:snapshot.changelogs()){
      if(versionName.equals(entry.versionName))return entry.highlights;
    }
    return "";
  }

  void showChangelogCenter(){
    if(isFinishing()||isDestroyed())return;
    if(noticeSnapshot!=null&&noticeSnapshot.reachable){showChangelogDialog("更新记录","",noticeSnapshot.changelogs());return;}
    showNotice("正在读取更新记录…",false);
    io.execute(()->{
      RemoteConfigClient.Snapshot snapshot=null;
      try{snapshot=RemoteConfigClient.fetch();}catch(Exception ignored){}
      final RemoteConfigClient.Snapshot result=snapshot;
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        noticeSnapshot=result;
        showChangelogDialog("更新记录","",result==null?java.util.Collections.<RemoteConfigClient.Changelog>emptyList():result.changelogs());
      });
    });
  }

  /** [DFW-62] 更新记录弹窗：按后台排序（新→旧）逐条列出，**内部滚动**，可一直往下看历史。 */
  void showChangelogDialog(String title,String subtitle,java.util.List<RemoteConfigClient.Changelog> entries){
    if(isFinishing()||isDestroyed())return;
    LinearLayout body=new LinearLayout(this);
    body.setOrientation(LinearLayout.VERTICAL);
    body.setPadding(dp(20),dp(2),dp(20),0);
    if(subtitle!=null&&!subtitle.isEmpty()){
      TextView hint=text(subtitle,12,MUTED);
      hint.setLineSpacing(dp(2),1f);
      hint.setPadding(0,0,0,dp(6));
      body.addView(hint,new LinearLayout.LayoutParams(-1,-2));
    }
    /*
     * [DFW-97] **内置第一条「公测开始」**。用户 2026-10-02：
     * 「更新记录，你现在改成『公测开始』」。
     *
     * 为什么放在客户端而不是只靠后台：
     * ① 后台没配、或者用户此刻网络不通时，更新记录**不该是一片空白** ——
     *    第一条就是"这个软件从哪开始的"，那是最该被看到的一句；
     * ② 后续每次发新版，后台加的新条目会**排在它前面**（后台按新→旧排序），
     *    所以它天然永远是"最早的那一条"，不需要任何人去维护它的位置。
     */
    java.util.List<RemoteConfigClient.Changelog> all=new java.util.ArrayList<>();
    /*
     * [DFW-97 修正] **后台已经有 1.0.0 时不许再加内置那条**。
     *
     * 用户 2026-10-02 截图反馈：「你看看咋多了，应该只有 1.0.0 啊」——
     * 更新记录里出现了两条 v1.0.0（后台一条、内置一条），因为第一版是无条件 add。
     * 现在改成**按版本号去重**：后台有就用后台的，没有才用内置的兜底。
     * 这样既不会重复，也不会出现"断网就一片空白"。
     */
    boolean backendHasBeta=entries!=null&&!entries.isEmpty();
    if(!backendHasBeta){
      all.add(new RemoteConfigClient.Changelog(
        "builtin-1.0.0","1.0.0","2026-10-02",
        "公测开始：东方无限正式开放公测，五大板块全部可用。\n"
        + "软件库：多个源可搜，一次搜完所有源，不用一个个点。\n"
        + "下载：点一下就能下，完成或失败都会明确告诉你，并写清是哪个文件。\n"
        + "AI 对话：内置渠道，看图、长文、工具调用都能用。\n"
        + "工具箱：三十多个小工具，从文件管理到图片处理。\n"
        + "设置：外观、下载、公告都能自己调；新增「反馈与建议」，有问题直接填表。\n"
        + "公告与更新：远程下发，不用重装就能收到通知和新版本提醒。\n"
        + "暗夜模式：全局纯黑底，内置网页也是，晚上看不刺眼。\n"
        + "启动更快：公告弹窗秒弹，不再等一次网络往返。\n"
        + "搜索页底部不再有黑色空白，返回主页有淡出动画。\n"
        + "这是第一个公开测试版，欢迎把遇到的问题告诉我。"));
    }
    if(entries!=null)all.addAll(entries);
    entries=all;
    if(entries==null||entries.isEmpty()){
      TextView empty=text("暂时读不到更新记录（后台没配或网络不通）",13,MUTED);
      empty.setGravity(Gravity.CENTER);
      empty.setPadding(dp(6),dp(20),dp(6),dp(20));
      body.addView(empty,new LinearLayout.LayoutParams(-1,-2));
    }else{
      for(RemoteConfigClient.Changelog entry:entries)body.addView(buildChangelogBlock(entry));
    }
    // 上限 420dp 让弹窗内部滚动：后台的更新记录会一直累积，不定高就会把弹窗撑到屏幕外。
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle(title).setView(limitedDialogScroll(body,420)).setPositiveButton("知道了",null).create();
    showRounded(dialog);
  }

  /** 一条更新记录：版本号 + 日期 一行，下面是改动条目。 */
  View buildChangelogBlock(RemoteConfigClient.Changelog entry){
    LinearLayout block=new LinearLayout(this);
    block.setOrientation(LinearLayout.VERTICAL);
    block.setPadding(dp(14),dp(11),dp(14),dp(12));
    GradientDrawable bg=solidShape(SURFACE2,16);
    bg.setStroke(dp(1),BORDER);
    block.setBackground(bg);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
    lp.bottomMargin=dp(10);
    block.setLayoutParams(lp);
    LinearLayout head=new LinearLayout(this);
    head.setGravity(Gravity.CENTER_VERTICAL);
    TextView version=text(entry.versionName.isEmpty()?"未标版本":"v"+entry.versionName,15,PRIMARY,700);
    version.setSingleLine(true);
    head.addView(version,new LinearLayout.LayoutParams(0,dp(26),1));
    TextView date=text(entry.date,12,MUTED);
    date.setSingleLine(true);
    date.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);
    head.addView(date,new LinearLayout.LayoutParams(-2,dp(26)));
    block.addView(head,new LinearLayout.LayoutParams(-1,dp(26)));
    if(!entry.highlights.isEmpty()){
      /*
       * [DFW-97] **自动编号**。用户 2026-10-02：
       * 「每次更新都应该让普通人能看懂，还用数字列出来」。
       *
       * 为什么在客户端编而不是让后台写「1. 2. 3.」：
       * 后台以后加条目的人（包括我）不用记着编号 —— 少一个会出错的手工步骤。
       * 一行一条，空行自动跳过；编号用全角「、」结尾，中文排版更自然。
       */
      String[] raw=entry.highlights.split("\\r?\\n");
      StringBuilder numbered=new StringBuilder();
      int index=0;
      for(String line:raw){
        String item=line.trim();
        if(item.isEmpty())continue;
        index++;
        if(numbered.length()>0)numbered.append('\n');
        numbered.append(index).append("、").append(item);
      }
      TextView lines=text(numbered.length()==0?entry.highlights:numbered.toString(),13,TEXT);
      lines.setLineSpacing(dp(4),1f);
      lines.setPadding(0,dp(6),0,0);
      block.addView(lines,new LinearLayout.LayoutParams(-1,-2));
    }
    block.setContentDescription("版本 "+(entry.versionName.isEmpty()?"未标":entry.versionName)+"，"+entry.date+"，"+(entry.highlights.isEmpty()?"无改动条目":entry.highlights));
    return block;
  }

  /** 后台可配的第三方下载页（FlowUs 等）；留空则「其他下载方式」里不出现该项。 */
  String alternativePageUrl(){
    try{
      String value=getSharedPreferences(UPDATE_CHECK_PREFS,MODE_PRIVATE).getString("alternative_page","");
      return value==null?"":value.trim();
    }catch(Exception ignored){return"";}
  }

  void checkLanzouPlusUpdate(boolean manual){io.execute(()->{try{UpdateOffer offer=resolveUpdateOffer();finishUpdateCheck(manual,offer==null?null:()->showUpdateOffer(offer,manual),null);}catch(Exception error){finishUpdateCheck(manual,null,error);}});}
  /**
   * [DFWX] DFW-59：更新弹窗的**四按钮布局**（用户明确要求）。
   *
   * 排布（M3 建议对话框最多 3 个动作，所以第 4 个不做成按钮，而是与「取消更新」同排的弱化文本钮）：
   *   ┌────────────────────────────┐
   *   │ 发现新版本 1.0.1            │
   *   │ 更新内容（可滚动）           │
   *   │ 36.6 MB · 下载后校验…       │
   *   │ [      立即更新      ]      │ ← 主按钮（filled）
   *   │ [   其他下载方式    ]      │ ← 次按钮（outlined）
   *   │   取消更新  ·  不再显示     │ ← 弱化文本钮
   *   └────────────────────────────┘
   *
   * **强制更新模式**（后台 updateMode=force）只有前两个按钮，**无取消、无不再显示**
   * ——这是用户的原话要求，由 {@link UpdatePromptPolicy#buttonsFor} 决定，不在这里写 if。
   */
  void showUpdateOffer(UpdateOffer offer,boolean manual){
    if(isFinishing()||isDestroyed())return;
    UpdatePromptPolicy policy=updatePromptPolicy();
    // 用户主动点「检查更新」时无视历史上的「不再显示」——否则手动入口回一句"已是最新"是骗人的。
    if(manual)policy.clearDismissed();
    if(!policy.shouldPrompt(offer.mode,offer.versionCode,BuildConfig.VERSION_CODE))return;
    if(!offer.hasAnyDownload()){showUpdateNotice("发现新版本 "+offer.versionName+"，但下载地址还没准备好",true);return;}
    UpdatePromptPolicy.Buttons buttons=UpdatePromptPolicy.buttonsFor(offer.mode);

    LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    // 底部面板只有**上方**两角是圆的（下边贴着屏幕底），用四角圆会露出下方缝隙。
    panel.setBackground(bottomSheetShape());
    panel.setClipToOutline(true);
    panel.setPadding(dp(22),dp(10),dp(22),dp(14));
    panel.setElevation(dp(12));

    // 拖拽手柄：让"可以滑走"这件事被看见。没有它用户不会想到去滑，功能等于不存在。
    View handle=new View(this);
    handle.setBackground(solidShape(BORDER,2));
    LinearLayout.LayoutParams handleParams=new LinearLayout.LayoutParams(dp(38),dp(4));
    handleParams.gravity=Gravity.CENTER_HORIZONTAL;
    handleParams.bottomMargin=dp(12);
    panel.addView(handle,handleParams);

    panel.addView(text("发现新版本 "+offer.versionName,19,TEXT,700),new LinearLayout.LayoutParams(-1,-2));

    if(!offer.body.isEmpty()){
      TextView notes=text(offer.body,13,MUTED);
      notes.setLineSpacing(dp(3),1f);
      ScrollView scroller=new ScrollView(this);
      scroller.setVerticalScrollBarEnabled(true);
      scroller.addView(notes,new ScrollView.LayoutParams(-1,-2));
      // 更新内容可能很长（后台 changelog 会一直累积），给个上限让它内部滚动，不撑爆屏幕。
      panel.addView(scroller,new LinearLayout.LayoutParams(-1,dp(168)));
    }

    // 措辞必须诚实：此处**还没下载**，不能说"已校验签名"。校验发生在下载之后（三重校验）。
    String meta=offer.sizeText();
    if(offer.installable()&&!meta.isEmpty())meta=meta+" · 下载后校验 sha256、包名与签名";
    if(!meta.isEmpty())panel.addView(text(meta,12,MUTED),new LinearLayout.LayoutParams(-1,dp(34)));

    Button update=updateActionButton("立即更新",true);
    update.setContentDescription("立即更新到 "+offer.versionName);
    update.setOnClickListener(v->startUpdateFromOffer(offer));
    panel.addView(update,new LinearLayout.LayoutParams(-1,dp(50)));

    Button alternatives=updateActionButton("其他下载方式",false);
    alternatives.setContentDescription("其他下载方式，用浏览器打开");
    alternatives.setOnClickListener(v->showAlternativeDownloads(offer));
    LinearLayout.LayoutParams altParams=new LinearLayout.LayoutParams(-1,dp(48));
    altParams.topMargin=dp(8);
    panel.addView(alternatives,altParams);

    if(buttons.cancel||buttons.neverRemind){
      LinearLayout weakRow=new LinearLayout(this);
      weakRow.setGravity(Gravity.CENTER);
      if(buttons.cancel){
        Button cancel=weakTextButton("取消更新");
        cancel.setOnClickListener(v->dismissOfferDialog());
        weakRow.addView(cancel,new LinearLayout.LayoutParams(-2,dp(44)));
      }
      if(buttons.cancel&&buttons.neverRemind)weakRow.addView(text("·",13,MUTED),new LinearLayout.LayoutParams(-2,dp(44)));
      if(buttons.neverRemind){
        Button never=weakTextButton("不再显示");
        never.setContentDescription("不再显示 "+offer.versionName+" 的更新提示（换新版本仍会提示）");
        never.setOnClickListener(v->{
          // 只记这一版：换版本照弹（用户明确要求"只针对该具体版本"）。
          policy.rememberDismissed(offer.versionCode);
          dismissOfferDialog();
        });
        weakRow.addView(never,new LinearLayout.LayoutParams(-2,dp(44)));
      }
      panel.addView(weakRow,new LinearLayout.LayoutParams(-1,dp(46)));
    }

    // 从**底部**升起（用户要求），可下滑/左右滑关闭。
    // **强制更新模式下三个方向与返回键全部关闭**——"不能关"必须是硬的，否则强制就是摆设。
    offerSheet=SlideSheet.create(this,sheetHost(),panel,()->{offerSheet=null;syncBackCallbackEnabled();},motionEnabled(),SlideSheet.Edge.BOTTOM)
      .swipeAway(buttons.cancel)
      .swipeHorizontal(buttons.cancel)
      .dismissOnScrimTap(buttons.cancel)
      .dismissOnBack(buttons.cancel);
    offerSheet.show();
    syncBackCallbackEnabled();
  }

  /** 顶部面板的挂载容器：用 decorView 的 content 区，保证盖住整屏（含状态栏下方）。 */
  ViewGroup sheetHost(){
    View content=findViewById(android.R.id.content);
    return content instanceof ViewGroup?(ViewGroup)content:root;
  }

  /**
   * 底部面板背景：只有**上方**两角是圆的（下边贴着屏幕底，四角圆会露出缝隙）。
   * 圆角顺序 = 左上、右上、右下、左下。
   */
  GradientDrawable bottomSheetShape(){
    GradientDrawable g=new GradientDrawable();
    g.setColor(SURFACE);
    float r=dp(26);
    g.setCornerRadii(new float[]{r,r,r,r,0,0,0,0});
    g.setStroke(dp(1),BORDER);
    return g;
  }

  /** 全宽动作按钮：主按钮 filled、次按钮 outlined（均带按压缩放，走项目既有 applePressScale）。 */
  Button updateActionButton(String label,boolean primary){
    Button b=new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(15);
    b.setTextColor(primary?BG:PRIMARY);
    b.setTypeface(primary?AppFonts.medium(this):AppFonts.normal(this));
    resetButtonChrome(b);
    b.setBackground(filterRipple(primary?solidShape(PRIMARY,14):shape(SURFACE,14)));
    return b;
  }

  /** 弱化文本钮：无底色、无描边，只靠主色文字（用于「取消更新 / 不再显示」）。 */
  Button weakTextButton(String label){
    Button b=toolbarTextButton(label);
    b.setTextSize(13);
    return b;
  }

  void dismissOfferDialog(){
    if(offerSheet!=null){
      offerSheet.dismiss();
      offerSheet=null;
    }
  }

  /** 「其他下载方式」第二层：GitHub 页（标注需科学上网）+ 后台可配的第三方页。 */
  void showAlternativeDownloads(UpdateOffer offer){
    final List<UpdateOffer.Alt> alternates=offer.alternates();
    if(alternates.isEmpty()){showUpdateNotice("暂时没有其他下载方式",true);return;}
    String[] labels=new String[alternates.size()];
    for(int i=0;i<alternates.size();i++)labels[i]=alternates.get(i).label;
    AlertDialog dialog=new AlertDialog.Builder(this)
      .setTitle("其他下载方式")
      .setItems(labels,(d,index)->openInBrowser(alternates.get(index).url,""))
      .setNegativeButton("关闭",null)
      .create();
    showRounded(dialog);
  }

  /** 从归一后的供给启动下载；真正下载/校验/安装仍走 DFW-7 那条已验证的链路。 */
  void startUpdateFromOffer(UpdateOffer offer){
    dismissOfferDialog();
    startLanzouPlusUpdate(new UpdateClient.UpdateInfo(
      offer.versionName,offer.body,offer.primaryUrl,offer.fallbackUrl,offer.digest,offer.size,false));
  }

  /** DFW-59：更新提示的取舍策略（模式判定 + 「不再显示」的按版本记忆）。 */
  UpdatePromptPolicy updatePromptPolicy;
  UpdatePromptPolicy updatePromptPolicy(){
    if(updatePromptPolicy==null)updatePromptPolicy=UpdatePromptPolicy.forContext(this);
    return updatePromptPolicy;
  }
  /**
   * 更新提示面板。
   * DFW-59 是居中 AlertDialog；DFW-65 曾误改成顶部面板；DFW-66 按用户要求改为
   * **从底部升起**（用户原话："a 从底部往上升丝滑动画"）。
   */
  SlideSheet offerSheet;
  /**
   * [DFWX] DFW-60：维护 / 停更拦截页。
   *
   * 用户要求（原话）："只显示公告，直接没有按钮，而且点击空白处也不能关闭，就是纯粹的通知"、
   * "维护时间用单独的 UI 界面显示…甚至我可以写上永久"。
   *
   * 三条必须同时成立：
   *   ① 覆盖整屏、**无任何按钮**、点空白不关；
   *   ② **返回键无效**（canHandleBack/performSystemBack 已拦）；
   *   ③ 有个**不写在界面上的后门**：连点版本号 7 次可自救——否则后台误开就把用户和作者一起锁死。
   * 判定与 fail-open 在 {@link MaintenanceGate}，这里只负责画与吞键。
   */
  MaintenanceGate maintenanceGate;
  MaintenanceGate maintenanceGate(){
    if(maintenanceGate==null)maintenanceGate=new MaintenanceGate();
    return maintenanceGate;
  }
  boolean maintenanceBlocking;
  View maintenanceOverlay;

  /** 启动时静默拉一次后台配置，命中维护才铺拦截页（fail-open 在 MaintenanceGate 里）。 */
  void maybeEnterMaintenance(){
    io.execute(()->{
      RemoteConfigClient.Snapshot snapshot=null;
      try{snapshot=RemoteConfigClient.fetch();}catch(Exception ignored){/* 拉不到就按正常放行 */}
      final RemoteConfigClient.Snapshot result=snapshot;
      // [DFW-73] 后台下发的内置渠道覆盖（地址/令牌/模型/最大输出/开关）：拉到就应用，当次启动即生效。
      if(result!=null)BuiltinAiChannel.applyRemote(result.control());
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        MaintenanceGate.Screen screen=maintenanceGate().decide(result);
        if(screen.block)showMaintenanceOverlay(screen);
      });
    });
  }

  void showMaintenanceOverlay(MaintenanceGate.Screen screen){
    if(maintenanceOverlay!=null)return;
    maintenanceBlocking=true;
    // 让返回键由我们消费（canHandleBack 里已把 maintenanceBlocking 当作"可处理"）。
    syncBackCallbackEnabled();

    FrameLayout overlay=new FrameLayout(this);
    overlay.setBackgroundColor(BG);
    // 点空白处**不做任何事**——但必须可点，否则触摸会穿透到下面的界面。
    overlay.setClickable(true);
    overlay.setFocusable(true);
    overlay.setContentDescription("维护通知");

    LinearLayout column=new LinearLayout(this);
    column.setOrientation(LinearLayout.VERTICAL);
    column.setGravity(Gravity.CENTER);
    column.setPadding(dp(28),dp(28),dp(28),dp(28));

    TextView title=text(screen.title,21,TEXT,700);
    title.setGravity(Gravity.CENTER);
    column.addView(title,new LinearLayout.LayoutParams(-1,-2));

    TextView body=text(screen.body,14,MUTED);
    body.setGravity(Gravity.CENTER);
    body.setLineSpacing(dp(4),1f);
    LinearLayout.LayoutParams bodyParams=new LinearLayout.LayoutParams(-1,-2);
    bodyParams.topMargin=dp(14);
    column.addView(body,bodyParams);

    // 「维护时间」独立区块：留空则整块不出现（用户要求）。原样透传用户填的自由文本（可写"永久"）。
    if(!screen.untilText.isEmpty()){
      LinearLayout untilBox=new LinearLayout(this);
      untilBox.setOrientation(LinearLayout.VERTICAL);
      untilBox.setGravity(Gravity.CENTER);
      untilBox.setBackground(solidShape(SURFACE,16));
      untilBox.setPadding(dp(18),dp(14),dp(18),dp(14));
      TextView untilLabel=text("维护时间",11,MUTED);
      untilLabel.setGravity(Gravity.CENTER);
      untilBox.addView(untilLabel,new LinearLayout.LayoutParams(-1,-2));
      TextView untilValue=text(screen.untilText,15,PRIMARY,500);
      untilValue.setGravity(Gravity.CENTER);
      LinearLayout.LayoutParams valueParams=new LinearLayout.LayoutParams(-1,-2);
      valueParams.topMargin=dp(4);
      untilBox.addView(untilValue,valueParams);
      LinearLayout.LayoutParams boxParams=new LinearLayout.LayoutParams(-1,-2);
      boxParams.topMargin=dp(22);
      column.addView(untilBox,boxParams);
    }

    FrameLayout.LayoutParams columnParams=new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER);
    overlay.addView(column,columnParams);

    /*
     * [DFW-91] 隐藏后门：连点这行文字 7 次放行。**界面上不写任何提示，点击也不给任何反馈**
     * （不弹提示、不震动、不变色）—— 它是应急逃生口，一旦有反馈就会被人发现。
     *
     * 这里**不再显示版本号**：用户要求"版本号只留一处（检查更新右侧）"。
     * 但后门本身必须留着：后台开了维护模式时，这是唯一的自助通道。
     */
    TextView version=text(PRODUCT_NAME,11,MUTED);
    version.setGravity(Gravity.CENTER);
    version.setPadding(dp(12),dp(10),dp(12),dp(10));
    version.setOnClickListener(v->{
      if(maintenanceGate().tapVersion()){
        dismissMaintenanceOverlay();
        showBackdoorNotice();
      }
    });
    FrameLayout.LayoutParams versionParams=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);
    versionParams.bottomMargin=dp(28);
    overlay.addView(version,versionParams);

    maintenanceOverlay=overlay;
    addContentView(overlay,new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
  }

  void dismissMaintenanceOverlay(){
    maintenanceBlocking=false;
    syncBackCallbackEnabled();
    if(maintenanceOverlay!=null){
      removeFromParent(maintenanceOverlay);
      maintenanceOverlay=null;
    }
  }

  /**
   * [DFW-91] 后门被打开后给**用户**看的那段话（用户 2026-10-01 亲自写的文案，一字未改）。
   *
   * 为什么用对话框而不是顶部通知条：通知条正文上限 4 行，这段话 80 多字在窄屏上会被截断，
   * 而**被截掉的正好是最后那句"我无法保障你的安全"** —— 那才是要让人看见的部分。
   */
  static final String BACKDOOR_NOTICE="测试后门已开启，这是我刻意留下的备用测试通道，如果你是用户，还请小心使用，"
      +"因为当我开启了维护模式，那就说明该软件或许出现了什么问题或者是故障与异常，我无法保障你的安全。";

  void showBackdoorNotice(){
    AlertDialog dialog=new AlertDialog.Builder(this)
        .setTitle("测试后门已开启")
        .setMessage(BACKDOOR_NOTICE)
        .setPositiveButton("知道了",null)
        .create();
    showRounded(dialog);
  }

  /**
   * [DFWX] DFW-61：公告中心。
   *
   * 用户要求：公告是"发布式、可叠加"、随时可看、弹窗可选（后台开关）、
   * 未读红点要"专属的、非常丝滑流畅的动画，不是硬切换"。
   * 未读/排序/垃圾回收都在 {@link NoticeCenter}，这里只负责画页面、挂入口、亮红点。
   */
  /** 供测试：首页公告铃铛已移除（DFW-70 挪进悬浮球菜单）。 */
  boolean noticeBellRowGoneForTest(){return true;}

  /**
   * [DFW-71] 设置页的**滚动位置**记忆。
   *
   * 缺陷根因（并行诊断确认）：所有设置子页返回都走 `showSettings()`，
   * 而它是**整页重建**（`primaryBase(3)` → `basePage()` 会 new 一棵全新的视图树、清空约 60 个字段），
   * 新建的 `ScrollView` 初始 `scrollY` 必然是 0。
   * 目录页有 `FolderPageState.scrollY` + `restoreFolderScrollBeforeDraw` 这套恢复机制，
   * **设置页一个都没有** → 返回必然回顶。
   */
  int settingsScrollY;

  /** 供测试：当前记住的设置页滚动位置。 */
  int settingsScrollYForTest(){return settingsScrollY;}

  /** 供测试：当前主目的地。 */
  int primaryDestinationForTest(){return primaryDestination;}

  /**
   * [DFW-71] 分区**展开态**记忆（键 = 分区标题）。
   *
   * 不记的话，返回设置页时展开过的分区又被硬编码默认值收起来，
   * 视觉跳变比单纯回顶更大——用户感觉"突然"就是这两个叠加。
   */
  final Map<String,Boolean> settingsExpansion=new LinkedHashMap<>();

  NoticeCenter noticeCenter;
  NoticeCenter noticeCenter(){
    /* [DFWX] 公告分版本：把本机版本名交给 NoticeCenter，由它在 visible() 里统一过滤。 */
    if(noticeCenter==null)noticeCenter=NoticeCenter.forContext(this,BuildConfig.VERSION_NAME);
    return noticeCenter;
  }
  RemoteConfigClient.Snapshot noticeSnapshot;
  NoticeBadge noticeBadge;

  /** [DFW-103] 本次会话已经弹过哪条公告 —— 防止"缓存先弹一次、网络回来又弹一次"。 */
  String lastPopupNoticeId="";

  /**
   * 启动时拉公告：**先用本地缓存秒弹，再拉网络补新的**。
   *
   * 用户 2026-10-01 反馈「公告弹窗弹出太慢」。根因：启动后要**等一次完整网络往返**才弹
   * （超时是连接 6s + 读取 8s，最坏 14 秒），而且**没有任何本地缓存**，每次都是现拉。
   *
   * 现在：第二次以后打开软件，公告是**零延迟**弹出来的（先弹上次拉到的），
   * 网络回来若有新公告再补弹。网络失败时保留缓存内容，不会把已经显示的公告变没。
   */
  void maybeFetchNotices(){
    RemoteConfigClient.Snapshot cached=RemoteConfigClient.loadCache(this);
    if(cached!=null){
      noticeSnapshot=cached;
      refreshNoticeBell();
      maybePopupNotices();
    }
    io.execute(()->{
      RemoteConfigClient.Raw raw=new RemoteConfigClient.Raw();
      RemoteConfigClient.Snapshot snapshot=null;
      try{snapshot=RemoteConfigClient.fetch(raw);}catch(Exception ignored){}
      final RemoteConfigClient.Snapshot result=snapshot;
      final RemoteConfigClient.Raw collected=raw;
      runOnUiThread(()->{
        if(isFinishing()||isDestroyed())return;
        if(result==null||!result.reachable)return;
        RemoteConfigClient.storeCache(MainActivity.this,collected);
        noticeSnapshot=result;
        refreshNoticeBell();
        maybePopupNotices();
      });
    });
  }

  /** 刷新铃铛与红点。未读为 0 时**整行隐藏**（用户要求"有未读才出现"）。 */
  void refreshNoticeBell(){
    int unread=noticeCenter().unreadCount(noticeSnapshot);
    if(noticeBadge!=null){
      noticeBadge.setMotionEnabled(motionEnabled());
      noticeBadge.applyTheme(ERROR_TOKEN);
      noticeBadge.setCount(unread);
    }
    // [DFW-70] 红点现在挂在**悬浮球菜单的「公告」项**上（首页铃铛已移除）。
    // 菜单收起时也要通知它刷新，否则下次展开看到的还是旧数字。
    if(navBall!=null)navBall.refreshNoticeBadge();
  }

  /**
   * [DFWX] DFW-72：**公告弹窗（Liquid Glass）**。
   *
   * 用户 2026-10-01 真机反馈三条：
   *   ① "你制作的公告适配都没适配好，太丑了"
   *   ② "我点击知道后……应该是直接关掉这个弹窗"（不该跳到公告完整界面）
   *   ③ "公告不该是设置的子页"（见 {@link #showNoticeCenter()}）
   *
   * ## 三条根因（都能在代码里指到，且都不会崩、只会让人觉得丑）
   * ① **几百 dp 的空白**：正文容器高度被写死成 `Math.min(maxBody, dp(420))`，与内容长短无关——
   *    短公告也占 420dp，于是弹窗下半截是一大片空的黑。现在改成
   *    **WRAP_CONTENT + 上限兜底**（{@link MaxHeightScrollView}）：短内容贴合内容，长内容才滚动。
   * ② **「知道了」乱跳**：文案是「知道了」但绑的动作是 `showNoticeCenter()`——文案与行为不一致。
   *    现在一一对应：**「知道了」= 只关窗**；只有**还有未读**时才多出一个「查看全部」。
   *    顺带删掉语义重复的「关闭」（两个按钮都只是关窗，摆两个是噪音）。
   * ③ **丑在"材质"不在"圆角"**：旧版是不透明深灰 + 纯色填充，既没有"透"也没有"边"，
   *    再准的圆角也只是块灰板子。现在走真·玻璃：系统级背景模糊 + 低 alpha 玻璃底 +
   *    顶光 rim/镜面高光（配方见 {@link GlassSurface}，取值来源是 AOSP《Window blurs》
   *    与 Kyant0/AndroidLiquidGlass、chrisbanes/haze、Prismal、iOS 26 规格文档）。
   */
  void showNoticeDialog(RemoteConfigClient.Notice notice,int remainingCount,Runnable onDismissed){
    if(isFinishing()||isDestroyed())return;
    if(notice==null)return;
    final boolean blur=GlassSurface.isBlurEnabled(this);
    final float density=getResources().getDisplayMetrics().density;
    final int radius=dp(28);
    noticeDialogBlurApplied=blur;

    LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    // 面板**内部全透明**：玻璃的底色与模糊由窗口背景负责（圆角也只能写在窗口背景上，
    // 否则模糊会溢成一个直角方块——AOSP 明确要求"带圆角的 ShapeDrawable 作为窗口背景"）。
    // 这里只画"玻璃的边缘与高光"，画了填充就会把模糊盖死、又变回一块灰板子。
    panel.setBackground(GlassSurface.panel(radius,Math.max(1,dp(1))));
    panel.setClipToOutline(true);

    // ── 标题区（左右 24dp：AOSP dialog_padding_material / MDC top_padding）──
    // 可关闭性：紧急公告不给关闭 X（否则"紧急"就成了摆设），其余给。触控区 44dp。
    final boolean closable=!notice.isUrgent();
    LinearLayout titleRow=new LinearLayout(this);
    titleRow.setGravity(Gravity.CENTER_VERTICAL);
    // 右侧：有 X 时留 16dp（X 自己占 44dp 触控区），**没有 X 时必须留满 24dp**——
    // 否则标题的右边界比正文靠外，整块看着是偏的。
    titleRow.setPadding(dp(24),dp(24),closable?dp(16):dp(24),0);
    TextView title=text(notice.title,20,TEXT,700);
    title.setMaxLines(3);
    title.setEllipsize(android.text.TextUtils.TruncateAt.END);
    title.setLineSpacing(dp(2),1f);
    titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
    if(closable){
      ImageButton close=iconButton(R.drawable.ic_close,"关闭公告");
      close.setColorFilter(MUTED);
      close.setOnClickListener(v->dismissNoticeDialog());
      LinearLayout.LayoutParams closeParams=new LinearLayout.LayoutParams(dp(44),dp(44));
      closeParams.leftMargin=dp(8);
      titleRow.addView(close,closeParams);
    }
    panel.addView(titleRow,new LinearLayout.LayoutParams(-1,-2));

    // ── 元信息行：等级标签 + 发布时间 + 还有几条 ──
    // 发布时间是**公告与普通对话框最直观的区分信号**。
    // 等级改用**文字标签**而不是标题旁那颗 6dp 圆点——圆点在 20sp 标题旁边只会被当成噪点。
    LinearLayout metaRow=new LinearLayout(this);
    metaRow.setGravity(Gravity.CENTER_VERTICAL);
    metaRow.setPadding(dp(24),dp(10),dp(24),0);
    boolean hasMeta=false;
    if(notice.isUrgent()||notice.isImportant()){
      boolean urgent=notice.isUrgent();
      TextView levelTag=text(urgent?"紧急":"重要",11,urgent?ERROR_TOKEN:PRIMARY,600);
      levelTag.setGravity(Gravity.CENTER);
      levelTag.setBackground(solidShape(SURFACE,9));
      levelTag.setPadding(dp(8),dp(3),dp(8),dp(3));
      metaRow.addView(levelTag,new LinearLayout.LayoutParams(-2,-2));
      hasMeta=true;
    }
    String noticeDate=noticeDateText(notice);
    if(!noticeDate.isEmpty()){
      TextView date=text(noticeDate,12,MUTED);
      LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(-2,-2);
      if(hasMeta)dateParams.leftMargin=dp(8);
      metaRow.addView(date,dateParams);
      hasMeta=true;
    }
    if(remainingCount>0){
      TextView more=text("还有 "+remainingCount+" 条",12,PRIMARY,600);
      more.setGravity(Gravity.END);
      LinearLayout.LayoutParams moreParams=new LinearLayout.LayoutParams(0,-2,1f);
      if(hasMeta)moreParams.leftMargin=dp(8);
      metaRow.addView(more,moreParams);
      hasMeta=true;
    }
    if(hasMeta)panel.addView(metaRow,new LinearLayout.LayoutParams(-1,-2));

    // ── 正文（只滚正文：标题与按钮永远可见，用户不会迷路）──
    if(!notice.body.isEmpty()){
      // 正文用"TEXT 往底色方向退 14%"而不是 MUTED：玻璃底是半透明的，
      // MUTED 的对比度落在模糊背景上会不够（iOS 26 规格同样要求玻璃上的文字高对比）。
      TextView body=text(notice.body,15,PremiumSurface.over(TEXT,BG,0.14f),500);
      body.setLineSpacing(dp(4),1f);
      MaxHeightScrollView scroller=new MaxHeightScrollView(this);
      scroller.setMaxHeightPx(noticeBodyMaxHeight());
      scroller.setVerticalScrollBarEnabled(false);
      scroller.setClipToPadding(false);
      // **左右各 24dp，和标题/日期/按钮用同一个内边距**。
      // 用户 2026-10-01 真机反馈："文字左右两边都超出去了"——根因就是这个容器一个内边距都没设，
      // 于是正文直接顶到面板圆角上、左边还压住了玻璃的高光描边。四个内容行必须共用一个左边距。
      scroller.setPadding(dp(24),0,dp(24),0);
      scroller.addView(body,new ScrollView.LayoutParams(-1,-2));
      LinearLayout.LayoutParams bodyParams=new LinearLayout.LayoutParams(-1,-2);
      bodyParams.topMargin=dp(14);
      panel.addView(scroller,bodyParams);
    }

    // ── 按钮区：**最多 2 个、右对齐**。公告是单向通知，**禁"取消"**。 ──
    LinearLayout actions=new LinearLayout(this);
    actions.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);
    actions.setPadding(dp(24),dp(20),dp(24),dp(20));
    noticeDialogPrimary=null;
    noticeDialogSecondary=null;
    if(remainingCount>0){
      // 还有未读：次要动作=「知道了」（只关窗），主要动作=「查看全部」（去列表）。
      Button ack=noticeDialogButton("知道了",false);
      ack.setOnClickListener(v->dismissNoticeDialog());
      actions.addView(ack,new LinearLayout.LayoutParams(-2,dp(48)));
      Button all=noticeDialogButton("查看全部",true);
      all.setOnClickListener(v->{dismissNoticeDialog();showNoticeCenter();});
      LinearLayout.LayoutParams allParams=new LinearLayout.LayoutParams(-2,dp(48));
      allParams.leftMargin=dp(10);
      actions.addView(all,allParams);
      noticeDialogSecondary=ack;
      noticeDialogPrimary=all;
    }else{
      // 没有别的公告：只有一个动作，那就让它成为主要动作。
      // **「知道了」就只是关窗**——用户 2026-10-01："点知道后不应该跳到那个公告完整界面。"
      Button ack=noticeDialogButton("知道了",true);
      ack.setOnClickListener(v->dismissNoticeDialog());
      actions.addView(ack,new LinearLayout.LayoutParams(-2,dp(48)));
      noticeDialogPrimary=ack;
    }
    panel.addView(actions,new LinearLayout.LayoutParams(-1,-2));

    // 用**裸 Dialog** 而不是 AlertDialog + showRounded：
    // showRounded 会把面板背景覆盖成 `solidShape(SURFACE,22)`（还会带上 §5 明令禁止的 elevation 阴影），
    // 那样上面精心设的玻璃背景就全白做了。
    android.app.Dialog dialog=new android.app.Dialog(this);
    dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
    dialog.setContentView(panel);
    // 紧急公告不许点空白/返回关闭——否则"紧急"就成了摆设。
    // 另存一份意图供测试断言：Android 37 的 `Dialog` 只暴露 setter，没有读取用的公开方法。
    noticeDialogCancelable=!notice.isUrgent();
    dialog.setCanceledOnTouchOutside(noticeDialogCancelable);
    dialog.setCancelable(noticeDialogCancelable);
    dialog.setOnDismissListener(d->{
      unregisterNoticeBlurListener();
      noticeDialog=null;
      noticeDialogPrimary=null;
      noticeDialogSecondary=null;
      // 弹过即把**这一条**标为已读（"一次性"模式靠它生效；"永久"模式会无视它，见 NoticeCenter）。
      noticeCenter().markRead(notice.id);
      refreshNoticeBell();
      if(onDismissed!=null)onDismissed.run();
    });
    noticeDialog=dialog;
    android.view.Window window=dialog.getWindow();
    if(window!=null){
      applyNoticeWindowGlass(window,blur,radius,density);
      android.view.WindowManager.LayoutParams lp=window.getAttributes();
      lp.gravity=Gravity.CENTER;
      // 宽度：M3 规格 min 280 / max 560，两侧各留 24dp。
      lp.width=Math.max(dp(280),Math.min(dp(560),safeContentWidth()-dp(48)));
      // 高度**交给内容**：旧版把正文写死 420dp，短公告因此撑出一大片空白。
      lp.height=android.view.WindowManager.LayoutParams.WRAP_CONTENT;
      window.setAttributes(lp);
    }
    dialog.show();
    if(window!=null)registerNoticeBlurListener(window,radius);
    // 入场：alpha 0→1 + scale 0.92→1（0.92 是规范 §7.5 fadeThrough 的既有值，不新造数字）。
    if(motionEnabled()){
      panel.setAlpha(0f);
      panel.setScaleX(0.92f);
      panel.setScaleY(0.92f);
      panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300)
          .setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f)).start();
    }
  }

  /**
   * 把窗口变成一块玻璃（AOSP《Window blurs》规定的三步）。
   *
   * 1. 窗口必须 **translucent**，模糊才可见——`setFormat(TRANSLUCENT)` 就是
   *    `windowIsTranslucent=true` 的代码等价物。
   * 2. 圆角只能靠"带圆角的 ShapeDrawable 作为窗口背景"——模糊画在窗口 surface 下面，
   *    背景形状决定它的可见轮廓。
   * 3. 背景模糊（面板自身，80px 档）与 blur behind（整屏景深，20px 档）是两个独立开关。
   */
  void applyNoticeWindowGlass(android.view.Window window,boolean blur,int radius,float density){
    window.setFormat(android.graphics.PixelFormat.TRANSLUCENT);
    // 记一份引用供测试断言"玻璃背景真的装到窗口上了"，而不是只写了个没人调用的类。
    noticeDialogWindowBackground=GlassSurface.windowBackground(radius,GlassSurface.windowFillColor(BG,blur));
    window.setBackgroundDrawable(noticeDialogWindowBackground);
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    window.setDimAmount(GlassSurface.dimAmount(blur));
    if(!blur)return;
    /*
     * [DFW-123] 把"只在 API 31+ 才调模糊 API"这件事**落到本方法里**。
     *
     * 改之前也能不出事，但那是**跨 2 个文件 3 个方法**的保证：
     *   GlassSurface.blurApiAvailable()（GlassSurface.java:92-94）→ isBlurEnabled() 返回 false
     *   → 本文件 :1844 算出 blur=false → 上面那行提前 return → 这两行不可达。
     * 问题是签名里的 `boolean blur` 对**任何新调用方都是敞开的**：谁哪天传个 true 进来，
     * API 26 设备上这两行就是 NoSuchMethodError，而它们**没有 try/catch 兜底**。
     * 加这一行之后运行期语义完全不变（低版本本来就到不了），但方法自身变成"谁调都安全"。
     * **方法签名一个字没动**，调用点数量不变。
     */
    if(Build.VERSION.SDK_INT<31)return;
    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
    window.setBackgroundBlurRadius(GlassSurface.backgroundBlurPx(density));
    android.view.WindowManager.LayoutParams lp=window.getAttributes();
    lp.setBlurBehindRadius(GlassSurface.blurBehindPx(density));
    window.setAttributes(lp);
  }

  /**
   * 监听系统模糊开关。
   *
   * AOSP 明确要求处理"模糊被系统关掉"的情况（省电模式、播放某些视频、开发者选项都会临时关）：
   * 这时按官方指引**提高窗口背景的不透明度并加大 dim**，否则半透明底会失效、文字压在画面内容上读不清。
   */
  void registerNoticeBlurListener(android.view.Window window,int radius){
    unregisterNoticeBlurListener();
    if(!GlassSurface.blurApiAvailable()||window==null)return;
    try{
      android.view.WindowManager manager=(android.view.WindowManager)getSystemService(WINDOW_SERVICE);
      if(manager==null)return;
      final android.view.Window target=window;
      java.util.function.Consumer<Boolean> listener=enabled->{
        if(noticeDialog==null||!noticeDialog.isShowing())return;
        boolean on=enabled!=null&&enabled;
        try{
          target.setBackgroundDrawable(GlassSurface.windowBackground(radius,GlassSurface.windowFillColor(BG,on)));
          target.setDimAmount(GlassSurface.dimAmount(on));
        }catch(Throwable ignored){}
      };
      manager.addCrossWindowBlurEnabledListener(listener);
      noticeBlurListener=listener;
    }catch(Throwable ignored){}
  }

  void unregisterNoticeBlurListener(){
    java.util.function.Consumer<Boolean> listener=noticeBlurListener;
    noticeBlurListener=null;
    if(listener==null)return;
    /*
     * [DFW-123] 与 applyNoticeWindowGlass 同一个病：能不出事，靠的是"别人先挡了一道"。
     *
     * 原来低版本安全，是因为 noticeBlurListener 只在 registerNoticeBlurListener 的
     * `if(!GlassSurface.blurApiAvailable()...)return;` 之后才会被赋值，于是这里恒为 null、
     * 上面那行提前返回。同方法里两行代码，读者只看一处判断不出安全 —— 现在补上本地守卫。
     * 这一条另外还有下面的 catch(Throwable) 兜底，风险本来就低于 applyNoticeWindowGlass，
     * 但两处该用同一套写法。**方法签名一个字没动**，两个调用点（:1974/:2038 附近）行为不变。
     */
    if(Build.VERSION.SDK_INT<31)return;
    try{
      android.view.WindowManager manager=(android.view.WindowManager)getSystemService(WINDOW_SERVICE);
      if(manager!=null)manager.removeCrossWindowBlurEnabledListener(listener);
    }catch(Throwable ignored){}
  }

  /**
   * 正文容器的**最大**高度。
   *
   * 短公告贴合内容（不再有空白），长公告到这里才滚动。给标题/元信息/按钮/边距留出位置，
   * 并且不超过屏幕的 58%——弹窗高过这个比例就不像浮层、像整页了。
   */
  int noticeBodyMaxHeight(){
    int h=safeContentHeight();
    return Math.max(dp(96),Math.min(h-dp(240),Math.round(h*0.58f)));
  }

  /**
   * 高度自适应但**有上限**的正文容器。
   *
   * 这是"几百 dp 空白"的直接解药：旧版给正文容器写死一个高度，内容再短也占满；
   * 这里默认 WRAP_CONTENT 贴合内容，只有真的超长才被截到上限并内部滚动。
   */
  static final class MaxHeightScrollView extends android.widget.ScrollView {
    private int maxHeightPx;
    MaxHeightScrollView(android.content.Context context){super(context);}
    void setMaxHeightPx(int px){maxHeightPx=px;requestLayout();}
    int maxHeightPx(){return maxHeightPx;}
    @Override protected void onMeasure(int widthSpec,int heightSpec){
      super.onMeasure(widthSpec,heightSpec);
      if(maxHeightPx>0&&getMeasuredHeight()>maxHeightPx)setMeasuredDimension(getMeasuredWidth(),maxHeightPx);
    }
  }

  android.app.Dialog noticeDialog;
  /** 本次弹窗的动作按钮（供测试与语义核对：主/次动作必须与文案一一对应）。 */
  Button noticeDialogPrimary,noticeDialogSecondary;
  /** 系统模糊开关监听（弹窗关闭时必须注销，否则泄漏）。 */
  java.util.function.Consumer<Boolean> noticeBlurListener;
  /** 本次弹窗是否真的用上了背景模糊（供测试）。 */
  boolean noticeDialogBlurApplied;
  /** 真正装到窗口上的玻璃背景（供测试断言装配发生了，而不是只写了个没人调用的类）。 */
  android.graphics.drawable.Drawable noticeDialogWindowBackground;
  /** 供测试：当前公告弹窗是否允许点空白/返回关闭。 */
  boolean noticeDialogCancelable;

  void dismissNoticeDialog(){
    if(noticeDialog!=null){
      try{noticeDialog.dismiss();}catch(Exception ignored){}
      noticeDialog=null;
    }
  }

  /** 公告发布日期（yyyy-MM-dd）；拿不到时间戳时返回空串而不是显示 1970。 */
  String noticeDateText(RemoteConfigClient.Notice notice){
    if(notice.createdMs<=0)return"";
    try{
      return new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.CHINA).format(new java.util.Date(notice.createdMs));
    }catch(Exception ignored){return"";}
  }

  /**
   * 公告弹窗的按钮：**真胶囊**（半径 = 高度一半）。
   *
   * 不能用 `solidShape(color,24)`——`solidShape` 的三段量化会把它压成 26，
   * 做出来是个圆角方块而不是胶囊（这是项目里一个反复踩的坑）。
   */
  Button noticeDialogButton(String label,boolean primaryAction){
    Button b=new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    b.setTextSize(13);
    b.setTextColor(primaryAction?BG:TEXT);
    b.setTypeface(AppFonts.medium(this));
    resetButtonChrome(b);
    b.setMinHeight(dp(48));
    b.setMinimumHeight(dp(48));
    b.setPadding(dp(20),0,dp(20),0);
    if(primaryAction){
      b.setBackground(filterRipple(PremiumSurface.pill(PRIMARY,dp(48),0,0,PremiumSurface.HIGHLIGHT)));
    }else{
      b.setBackground(filterRipple(PremiumSurface.pill(SET_HIGH,dp(48),Math.max(1,dp(1)),SET_STROKE,PremiumSurface.HIGHLIGHT)));
    }
    return b;
  }

  /**
   * 启动后按三档模式决定要不要弹公告。
   *
   * - **静默**：不弹，只亮红点（`popupNotices` 已经把它排除）
   * - **一次性**：弹一次，读过不再弹
   * - **永久**：每次打开都弹（无视已读）
   * 同屏可能有"永久"档的多条，**一次只弹一条**，其余靠"查看全部"——
   * 多条一起弹会把用户淹没（NN/g「Stacked Overlays Fight Each Other」）。
   */
  void maybePopupNotices(){
    final List<RemoteConfigClient.Notice> popups=noticeCenter().popupNotices(noticeSnapshot);
    if(popups.isEmpty())return;
    final RemoteConfigClient.Notice first=popups.get(0);
    // [DFW-103] 现在启动会**两次**走到这里（先缓存、后网络）。同一条公告只许弹一次，
    // 否则用户会看到两个叠在一起的对话框。
    if(first.id.equals(lastPopupNoticeId))return;
    lastPopupNoticeId=first.id;
    showNoticeDialog(first,popups.size()-1,null);
  }

  /**
   * [DFW-72] **公告中心：顶级页面**。
   *
   * 用户 2026-10-01："我点击那个公告按钮之后，它就会先打开设置界面，然后再打开公告，
   * 这会导致一个问题，就是说公告变成了子页面，然后设置变成了主页面……我从公告退出来后，
   * 默认是达到了设置界面。但是按理来说不应该这样的。"
   *
   * 旧实现的两个毛病（都在代码里指得到）：
   * - `primaryBase(fromHome?0:3)`：悬浮球进来走 `false` → **先把设置建成底座**，公告再叠上去，
   *   于是在导航栈里公告天然成了设置的子页；
   * - `systemBackAction` 与 `aboutBackBar` 又都写死回 `showSettings()`。
   *
   * 现在：底座用**公告自己的档位**（`DEST_NOTICE`，与"软件库/下载/工具箱/设置/AI"五项完全平级，
   * 不再借用 0），返回动作 = **回到进来之前那一页**。
   */
  void showNoticeCenter(){
    if(isFinishing()||isDestroyed())return;
    // 先记下"从哪来"，再 `primaryBase` 重建页面——顺序反了，记到的就是公告页自己。
    final Runnable back=noticeReturnAction();
    // [DFW-73] 公告是悬浮球六项之一，**进场必须和另外五项长得一模一样**
    // （用户 2026-10-01："点『公告』的切换动画要和其他 5 个一致"）。
    // 另外五项都走 goToDestination 的 `primaryNavigationSwitch=true;pageDirection=0`（原位淡切），
    // 公告以前没设，于是它走的是"从右边推入"的子页动画 —— 那正是它被看成子页的观感来源。
    primaryNavigationSwitch=true;
    pageDirection=0;
    primaryBase(DEST_NOTICE);
    pageKind=10;
    activeSource=null;
    clearFolderTrail();
    systemBackAction=back;
    refreshNavBall();
    // [DFW-66] **不要返回按钮**：公告是顶级页，与软件库/下载/工具箱同级，
    // 只有它带返回箭头就会显得它还是个"子页"（用户 2026-10-01 明确要求去掉）。
    // 退出仍由系统返回键（边缘滑动）负责 —— 语义就是上面那个 `back`。
    LinearLayout body=aboutBodyOnly("公告");
    List<RemoteConfigClient.Notice> notices=noticeCenter().visible(noticeSnapshot);
    if(notices.isEmpty()){
      LinearLayout card=aboutCard();
      TextView empty=text("暂无公告",14,MUTED);
      empty.setGravity(Gravity.CENTER);
      card.addView(empty,new LinearLayout.LayoutParams(-1,dp(96)));
      body.addView(card,aboutCardLp());
    }else{
      for(RemoteConfigClient.Notice notice:notices)body.addView(buildNoticeCard(notice),aboutCardLp());
    }
    // 打开即视为已读：用户进来看了就不该再亮红点。
    // 必须放在建卡之后——卡上的未读圆点要反映"进来之前"的状态。
    noticeCenter().markAllRead(noticeSnapshot);
    refreshNoticeBell();
    ScrollView scroll=new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
    root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  }

  /**
   * 公告页的"从哪来回哪去"动作。
   *
   * 公告是**顶级页**（不占任何页面的"子页"位置），但用户从哪一页点进来、返回就该回哪一页——
   * 而不是像别的目的地那样一律兜底回首页，更不是回设置。
   */
  Runnable noticeReturnAction(){
    int kind=pageKind;
    if(kind==3){
      // 目录页：位置/滚动/已加载列表都在 FolderPageState 里，整体搬走再搬回来。
      captureActiveFolderState();
      final FolderPageState state=currentFolderState();
      if(state!=null&&state.folder!=null){
        final java.util.List<FolderPageState> trail=new ArrayList<>(folderTrail);
        return ()->{folderTrail.clear();folderTrail.addAll(trail);restoreFolderPageState(state);};
      }
      return folderRootSources?this::restoreSourceListOrShow:this::navigateHome;
    }
    // 软件库列表：`primaryBase` 已经把列表快照 retain 下来了，优先回放快照
    // （否则查询词/滚动位置/勾选会全丢——那就不是"回到原来那页"）。
    if(kind==1)return this::restoreSourceListOrShow;
    if(kind==2)return this::showDownloads;
    if(kind==4)return this::showSettings;
    if(kind==5)return this::showAiEmbedded;
    if(kind==6)return this::showTools;
    return this::navigateHome;
  }

  /** 回放软件库列表快照；快照没了才重建（顺序与 `navigateFolderBack` 一致）。 */
  void restoreSourceListOrShow(){
    if(!restoreSourceListPage())showSources();
  }

  /** 公告列表里的一张卡：未读圆点 + 标题 + 置顶/等级标签 + 发布时间 + 正文。 */
  LinearLayout buildNoticeCard(RemoteConfigClient.Notice notice){
    LinearLayout card=aboutCard();
    LinearLayout titleRow=new LinearLayout(this);
    titleRow.setGravity(Gravity.CENTER_VERTICAL);
    // 未读圆点：进来就会全部标为已读，所以这里画的是"进来之前"的状态。
    if(!noticeCenter().isRead(notice.id)){
      View dot=new View(this);
      GradientDrawable dotBg=new GradientDrawable();
      dotBg.setShape(GradientDrawable.OVAL);
      dotBg.setColor(PRIMARY);
      dot.setBackground(dotBg);
      LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(6),dp(6));
      dotParams.rightMargin=dp(8);
      titleRow.addView(dot,dotParams);
    }
    TextView title=text(notice.title,16,TEXT,600);
    titleRow.addView(title,new LinearLayout.LayoutParams(0,-2,1f));
    if(notice.pinned){
      TextView pin=text("置顶",11,PRIMARY,600);
      pin.setGravity(Gravity.CENTER);
      pin.setBackground(solidShape(SURFACE,9));
      pin.setPadding(dp(8),dp(3),dp(8),dp(3));
      titleRow.addView(pin,new LinearLayout.LayoutParams(-2,-2));
    }
    // 等级色（普通/重要/紧急）。**普通不挂标签**——每条都挂等于没有重点。
    if(notice.isUrgent()||notice.isImportant()){
      boolean urgent=notice.isUrgent();
      TextView levelTag=text(urgent?"紧急":"重要",11,urgent?ERROR_TOKEN:PRIMARY,600);
      levelTag.setGravity(Gravity.CENTER);
      levelTag.setBackground(solidShape(SURFACE,9));
      levelTag.setPadding(dp(8),dp(3),dp(8),dp(3));
      LinearLayout.LayoutParams tagParams=new LinearLayout.LayoutParams(-2,-2);
      tagParams.leftMargin=dp(6);
      titleRow.addView(levelTag,tagParams);
    }
    card.addView(titleRow,new LinearLayout.LayoutParams(-1,-2));
    String noticeDate=noticeDateText(notice);
    if(!noticeDate.isEmpty()){
      TextView date=text(noticeDate,12,MUTED);
      LinearLayout.LayoutParams dateParams=new LinearLayout.LayoutParams(-1,-2);
      dateParams.topMargin=dp(6);
      card.addView(date,dateParams);
    }
    if(!notice.body.isEmpty()){
      TextView content=text(notice.body,14,PremiumSurface.over(TEXT,BG,0.16f));
      content.setLineSpacing(dp(3),1f);
      LinearLayout.LayoutParams contentParams=new LinearLayout.LayoutParams(-1,-2);
      contentParams.topMargin=dp(10);
      card.addView(content,contentParams);
    }
    return card;
  }

  void showLanzouPlusUpdate(UpdateClient.UpdateInfo info){showUpdateOffer(UpdateOffer.fromGithub(info,BuildConfig.OFFICIAL_URL),false);}
  void startLanzouPlusUpdate(UpdateClient.UpdateInfo info){if(!ensureDirectStorageAuthorized(()->startLanzouPlusUpdate(info)))return;try{DownloadEntry entry=new DownloadEntry();entry.source=DOWNLOAD_SOURCE_UPDATE;entry.name="dongfang-wuxian-v"+info.version+".apk";entry.state=DOWNLOAD_RUNNING;entry.createdAt=entry.startedAt=System.currentTimeMillis();entry.totalBytes=entry.verifiedTotalBytes=info.size;entry.sourceSizeText=formatSize(info.size);entry.directUrl=info.primaryUrl();entry.resolvedAt=entry.createdAt;entry.autoInstall=true;entry.updateInfo=info;entry.target=createDownloadTarget(entry.name);entry.uriString=entry.target.toString();entry.parentUriString=currentDownloadParentReferenceUri().toString();downloadEntries.add(entry);persistDownloadHistory(entry);updateDownloadUi(entry);downloadLanzouPlusUpdate(info,entry,info.primaryUrl(),true);}catch(Exception error){showNotice("无法创建更新文件："+friendlyError(error),true);}}
  void downloadLanzouPlusUpdate(UpdateClient.UpdateInfo info,DownloadEntry entry,String url,boolean fallback){Uri destination;int generation;SegmentDownloader downloader;synchronized(entry){if(entry.stopRequested)return;destination=entry.target;entry.updateInfo=info;entry.directUrl=url;entry.state=DOWNLOAD_RUNNING;entry.error="";entry.startedAt=System.currentTimeMillis();entry.lastSpeedAt=entry.startedAt;entry.lastSpeedBytes=entry.downloadedBytes;generation=++entry.controlGeneration;downloader=new SegmentDownloader(this);entry.downloader=downloader;}updateDownloadUi(entry);downloader.startDirect(url,destination,new SegmentDownloader.Listener(){public void progress(long done,long total){synchronized(entry){if(entry.transferOwnedBy(generation,downloader)&&!entry.stopRequested)applyDownloadProgress(entry,done,total);}}public void completed(){synchronized(entry){if(entry.downloader!=downloader||generation!=entry.controlGeneration||entry.stopRequested||!entry.state.equals(DOWNLOAD_RUNNING))return;entry.downloader=null;entry.error="校验中";}updateDownloadUi(entry);io.execute(()->{try{verifyUpdateApk(destination,info);synchronized(entry){if(generation!=entry.controlGeneration||entry.stopRequested||!entry.state.equals(DOWNLOAD_RUNNING))return;entry.downloadedBytes=entry.totalBytes=entry.verifiedTotalBytes=info.size;entry.percent=100;entry.state=DOWNLOAD_COMPLETED;entry.error="";entry.completedAt=System.currentTimeMillis();}finishDownloadTarget(entry,true);updateDownloadUi(entry);runOnUiThread(()->{if(generation==entry.controlGeneration&&entry.state.equals(DOWNLOAD_COMPLETED))installEntry(entry);});}catch(Exception error){
  /*
   * [2026-10-03] **两类失败必须分开报**（原来这里把它们拍扁成同一句「更新包校验未通过」，
   * 于是"文件明明下下来了"却被显示成"下载失败"）。
   *
   * 用户原话：
   * > 「我不管上传什么软件，它都可以正常下载。我不在乎它能不能覆盖安装。」
   *
   *   · UpdateNotInstallableException —— 包**完整下载成功**了，只是它不是东方无限 → 下载算**完成**；
   *   · 其它（摘要不一致 / 读取失败）—— 文件真的坏了 → 下载**失败**（这条不能松）。
   *
   * 降级成"外部来源"是关键一步：改完之后它和从外部链接下下来的 APK 走**完全一样**的手续
   * （点安装要先过 `showExternalInstallConfirmation` 那道确认），
   * 既不会自动安装，也不阻止用户自己装 —— **不新增任何漏洞**。
   */
  if(error instanceof UpdateNotInstallableException){
    boolean keep;
    synchronized(entry){
      keep=generation==entry.controlGeneration&&!entry.stopRequested&&entry.state.equals(DOWNLOAD_RUNNING);
      if(keep){entry.downloadedBytes=entry.totalBytes=entry.verifiedTotalBytes=info.size;entry.percent=100;entry.state=DOWNLOAD_COMPLETED;entry.error="";entry.completedAt=System.currentTimeMillis();demoteForeignUpdateToExternal(entry);}
    }
    /*
     * ⚠️ [2026-10-03 对抗性复查] `finishDownloadTarget` **自己会吞异常并改状态**：
     * 改名/落盘失败时它把 `entry.state` 改回 `DOWNLOAD_FAILED`、并写上"下载完成但文件保存失败：…"。
     *
     * 原来这里**不看状态就无条件弹"下载完成"** —— 于是用户会同时看到两条互相矛盾的话：
     * 顶部横幅说"失败"、提示说"完成"。
     *
     * 现在弹之前先看状态：完成 → 说清"为什么不能当更新装"（用异常自带的**具体原因**，
     * 而不是笼统一句"不是东方无限"，因为还可能是"不比当前版本新"）；
     * 没完成 → 如实把保存失败的原因报出来。
     */
    if(keep){
      finishDownloadTarget(entry,true);
      updateDownloadUi(entry);
      if(entry.state.equals(DOWNLOAD_COMPLETED))showNotice("下载完成，但不能作为更新安装："+error.getMessage()+"。文件已保存到下载页，需要的话你可以自己打开安装。",true);
      else showNotice(entry.error.isEmpty()?"文件保存失败，请到下载页重试":entry.error,true);
    }
    return;
  }
  boolean failed;
  synchronized(entry){failed=generation==entry.controlGeneration&&!entry.stopRequested&&entry.state.equals(DOWNLOAD_RUNNING);if(failed){entry.state=DOWNLOAD_FAILED;entry.error="更新包校验未通过："+friendlyError(error);entry.speedBps=0;entry.etaSeconds=-1;}}
  if(failed){
    /*
     * [2026-10-03] **删掉这份坏字节。**
     *
     * 走到这里说明摘要/大小对不上 —— 文件**确实是坏的**（和上面"不是我们的包"那条不同）。
     * 而它现在还是个 `.part`：用户既看不到它、App 也读不了它
     * （`entryFileReadable` 只认"已完成"），但几十兆就那样躺在下载目录里占空间。
     * 更麻烦的是**重试还会去续传这份坏文件**（`resumeTargetAvailable` 对 file: 无条件返回 true），
     * 于是拼出新旧混合的文件、永远校验不过 —— 用户唯一的出路变成"删除文件"再重来。
     * 删掉之后重试才是一次干净的全量下载。
     */
    discardCancelledPartial(entry);
    updateDownloadUi(entry);
    showNotice("更新包校验未通过："+friendlyError(error),true);
  }
}});}public void paused(long done,long total){finishStoppedTransfer(entry,DOWNLOAD_PAUSED,done,total,generation,downloader);}public void cancelled(long done,long total){finishStoppedTransfer(entry,DOWNLOAD_CANCELLED,done,total,generation,downloader);}public void failed(String error){boolean useFallback;synchronized(entry){if(entry.downloader!=downloader)return;entry.downloader=null;if(generation!=entry.controlGeneration||entry.stopRequested)return;useFallback=fallback&&!info.fallbackUrl().isEmpty()&&!info.fallbackUrl().equals(url);if(useFallback){entry.state=DOWNLOAD_RESOLVING;entry.error="切换备用地址";}else{entry.state=DOWNLOAD_FAILED;entry.error=friendlyError(new IOException(error));entry.speedBps=0;entry.etaSeconds=-1;}}updateDownloadUi(entry);if(useFallback){synchronized(entry){if(generation!=entry.controlGeneration||entry.stopRequested||!entry.state.equals(DOWNLOAD_RESOLVING))return;downloadLanzouPlusUpdate(info,entry,info.fallbackUrl(),false);}}}});}
  void verifyUpdateApk(Uri uri,UpdateClient.UpdateInfo info)throws Exception{File temporary=File.createTempFile("verified-update-",".apk",getCacheDir());// DFWX-STAB-001（修审计 H-P1-7）：固定名 verified-update.apk 在手动+自动重试并发校验时互相覆盖，改唯一临时名
  java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");long size=0;try(InputStream input=openUriInput(uri);OutputStream output=new FileOutputStream(temporary)){byte[] buffer=new byte[32768];for(int count;(count=input.read(buffer))>0;){size+=count;if(size>info.size)throw new IOException("更新包大小不一致");digest.update(buffer,0,count);output.write(buffer,0,count);}}try{if(size!=info.size||!("sha256:"+hex(digest.digest())).equals(info.digest))throw new IOException("更新包摘要不一致");int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;android.content.pm.PackageInfo current=getPackageManager().getPackageInfo(getPackageName(),flags),archive=getPackageManager().getPackageArchiveInfo(temporary.getAbsolutePath(),flags);if(archive==null)throw new UpdateNotInstallableException("更新包不是有效的 APK");if(!getPackageName().equals(archive.packageName))throw new UpdateNotInstallableException("更新包包名不一致");if(!signatureDigests(current).equals(signatureDigests(archive)))throw new UpdateNotInstallableException("更新包签名不一致");
      /*
       * [2026-10-03 补] **这条是 DFW-90 定下的安全底线，原来只装在另一条通道上。**
       *
       * DFW-90 的原文（`docs/plan/dfw-90-95-96-103-batch.md:20-24`）：
       * > 「真实版本序号更大 | 确认包真的比用户手上的新 | **留**
       * >   为什么不能一起删：后台填的版本号只决定「要不要提醒更新」，用户真正装上的还是那个包。
       * >   包本身不比当前版本新 → 装完版本没变 → 后台还说有新版本 → **无限循环提示**。
       * >   这是物理限制，不是保守。」
       *
       * 但那条守卫当时只加进了 `verifyArchiveUpdate`（本地归档通道）。
       * **而用户走的是"点更新提示 → 下载"这条通道，它调的是本方法** ——
       * 于是后台只要把序号填得比包大（比如记录 10001、包还是 10000），
       * App 就会：下载 → 本方法放行 → 装上 → 版本没变 → 后台还说有新版本 → **永远提示**。
       * 2026-10-03 用户实测就停在这个状态里，而我当时还告诉他"这是正常的"——**那句话是错的**。
       *
       * 归到 `UpdateNotInstallableException` 而不是 `IOException`：包本身没问题、下载也是成功的，
       * 只是它不能当更新装 —— 所以条目算"已完成"、降级成外部来源，绝不自动安装。
       */
      long currentCode=Build.VERSION.SDK_INT>=28?current.getLongVersionCode():current.versionCode,archiveCode=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;if(archiveCode<=currentCode)throw new UpdateNotInstallableException("这个包并不比当前版本新（包内 "+archive.versionName+"，当前 "+current.versionName+"），装了不会有任何变化");}finally{temporary.delete();}}

  /**
   * [2026-10-03] **「包下载完整，但它不能作为更新安装」** —— 必须和「下载坏了」分开。
   *
   * ## 这个类型覆盖哪几种情况（都是"文件没问题，但装不了"）
   *   · 包名/签名不是东方无限 —— 别人塞的包；
   *   · **包本身不比当前版本新** —— DFW-90 定下的安全底线，见下面 2026-10-03 的补充说明；
   *   · 包解析不出来（摘要已过 ⇒ 文件和服务端一致，是包本身有问题）。
   * 它们的共同点：**下载是成功的**，所以条目要标"已完成"，只是不能当更新装上去。
   *
   * ## 用户报的问题
   * 后台指向别的软件时，文件**完整下载成功**了，界面却显示
   * 「下载失败 · 更新包校验未通过」。用户原话：
   * > 「我不管上传什么软件，它都可以正常下载。我不在乎它能不能覆盖安装，
   * >   毕竟下载别的软件肯定不能覆盖安装，对不对？」
   *
   * **他说得对的地方**：下载和"能不能当更新装"是两件事。文件下下来了就是下下来了，
   * 不该因为"装不上"就被报成"下载失败"。
   *
   * ## 但校验本身不能去掉（这一点不能顺着用户改）
   * 用户的论证是「反正安卓自己会拦」。**这个论证不成立**：
   * 安卓只在**包名相同**时才拒绝覆盖安装；包名**不同**的 APK 会被系统当成
   * **一个全新的 App 装上去**，不报任何错 —— 用户会莫名其妙多出一个陌生软件。
   *
   * 而更新元数据（apkUrl / sha256）是从 `http://39.106.33.135/pb` **明文**读来的
   * （DFW-7 的提交里就写过「明文响应里的 sha256 等于没兜底」）。
   * 能改这条响应的人**就能把 apkUrl 换成任意一个 APK**。所以包名 + 签名校验是
   * **唯一**能拦住"被引导去装一个不是我们的 App"的那道闸 —— 去掉就是真的安全倒退。
   *
   * ## 折中：保校验，只改表现
   * 校验不过时把条目标成**下载完成**、说明原因，但**绝不调 `installEntry`**
   * （不引导用户去装它）。这样"下载能成功"和"不会被骗着装陌生软件"两件事都成立。
   */
  static final class UpdateNotInstallableException extends IOException{
    UpdateNotInstallableException(String message){super(message);}
  }

  /**
   * [2026-10-03] 把一条「从更新通道下到的、但不是我们的包」的下载记录**降级成外部来源**。
   *
   * ## 为什么是"降级"而不是"删掉"或"直接装"
   * 用户说「我不在乎它能不能覆盖安装」—— 对，文件下下来了就该留着。
   * 但**不能就这么把它当更新装上去**：安卓只在包名相同时才拒绝覆盖安装，
   * 包名不同的 APK 会被系统当成一个**全新的 App 装上去**。
   *
   * 所以改成让它走**和外部链接下载完全一样的手续**：
   *   · `source` → `EXTERNAL` ⇒ `DownloadSourcePolicy.requiresInstallConfirmation` 变 true，
   *     用户点安装时会先过 `showExternalInstallConfirmation` 那道确认；
   *   · `expectedUpdateVersion` 清空 ⇒ 不会再走"云端更新"那套校验分支（那条永远过不了）；
   *   · `autoInstall=false` ⇒ 绝不自动装。
   * 结果：**下载成功、文件保留、想装要自己点并确认**。
   *
   * ## ⚠️ 这不是"零代价"（2026-10-03 对抗性复查纠正）
   * 原来这里写的是「不新增任何漏洞」—— **那句话不准确，已改**。真实情况：
   * 降级后文件会被 `finishDownloadTarget` 改成**正式文件名**（不再是 `.part`），
   * 用户看到"下载完成"，并且**经两次确认就能把它装上**。
   * 改之前是"下载失败" + 一个 App 自己都读不了的 `.part`。
   * 所以这是**有意的取舍**：拿"下载体验正确"换了"用户多一次自行确认的机会"。
   * 安全性没有降低到"能被自动装上"（那才是真漏洞），但说"不新增任何漏洞"是绝对化了。
   *
   * 抽成静态方法是为了能直接测（原来这段逻辑内联在一个巨大的 lambda 里，只能靠读代码）。
   */
  static void demoteForeignUpdateToExternal(DownloadEntry entry){
    if(entry==null)return;
    entry.source=DownloadSourcePolicy.EXTERNAL;
    entry.expectedUpdateVersion="";
    entry.updateVerified=false;
    entry.autoInstall=false;
    entry.installConfirmationGranted=false;
  }
  InputStream openUriInput(Uri uri)throws IOException{if("file".equals(uri.getScheme()))return new FileInputStream(new File(uri.getPath()));InputStream input=getContentResolver().openInputStream(uri);if(input==null)throw new IOException("无法读取更新包");return input;}
  @android.annotation.SuppressLint("PackageManagerGetSignatures") Set<String> signatureDigests(android.content.pm.PackageInfo info)throws Exception{android.content.pm.Signature[] values;if(Build.VERSION.SDK_INT>=28&&info.signingInfo!=null)values=info.signingInfo.hasMultipleSigners()?info.signingInfo.getApkContentsSigners():info.signingInfo.getSigningCertificateHistory();else values=info.signatures;Set<String> out=new HashSet<>();if(values!=null)for(android.content.pm.Signature value:values)out.add(hex(java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray())));if(out.isEmpty())throw new IOException("安装包没有签名");return out;}
  static String hex(byte[] bytes){char[] alphabet="0123456789abcdef".toCharArray(),out=new char[bytes.length*2];for(int i=0;i<bytes.length;i++){out[i*2]=alphabet[(bytes[i]>>>4)&15];out[i*2+1]=alphabet[bytes[i]&15];}return new String(out);}
  void clearFolderTrail(){folderTrail.clear();activeFolderState=null;activeFolderProfile=null;restoringFolderState=false;}
  void resetFolderTrail(Models.Source source){clearFolderTrail();Models.Source entry=new Models.Source();copySource(source,entry);activeFolderState=new FolderPageState(entry);folderTrail.add(activeFolderState);}
  FolderPageState currentFolderState(){return folderTrail.isEmpty()?null:folderTrail.get(folderTrail.size()-1);}
  void pinFolderImage(FolderPageState state,String url){if(state==null||url==null||url.isEmpty())return;Bitmap bitmap=imageCache.get(url);if(bitmap!=null)state.pinnedIcons.put(url,bitmap);}
  void captureActiveFolderState(){FolderPageState state=activeFolderState;if(folderProfilePending||state==null||pageKind!=3)return;state.folder=activeFolderProfile;state.folderItems=new ArrayList<>(folderItems);state.current=new ArrayList<>(current);state.query=currentSourceQuery;state.visible=visible;state.nextPage=folderNextPage;state.nextReadyAt=folderNextReadyAt;state.hasMore=folderHasMore;state.scrollY=pageScroll==null?state.scrollY:pageScroll.getScrollY();if(activeFolderProfile!=null)pinFolderImage(state,activeFolderProfile.avatarUrl);for(Models.Item item:folderItems)pinFolderImage(state,item.iconUrl);for(Models.Item item:current)pinFolderImage(state,item.iconUrl);}
  void restoreFolderScrollBeforeDraw(ScrollView scroll,int scrollY){if(scroll==null||scrollY<=0)return;final android.view.ViewTreeObserver observer=scroll.getViewTreeObserver();observer.addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){@Override public boolean onPreDraw(){android.view.ViewTreeObserver current=scroll.getViewTreeObserver();if(current.isAlive())current.removeOnPreDrawListener(this);scroll.scrollTo(0,scrollY);return true;}});}
  void restoreFolderPageState(FolderPageState state){if(state==null||state.folder==null)return;activeFolderState=state;activeSource=state.source;buildFolderScaffold(state.source);systemBackAction=this::navigateFolderBack;restoringFolderState=true;try{renderFolder(state.folder);}finally{restoringFolderState=false;}}
  void navigateToOriginSourceList(){pageDirection=-1;clearFolderTrail();if(!restoreSourceListPage())showSources();}
  void navigateToBreadcrumb(int index){if(index<0||index>=folderTrail.size()-1)return;captureActiveFolderState();while(folderTrail.size()>index+1)folderTrail.remove(folderTrail.size()-1);pageDirection=-1;restoreFolderPageState(folderTrail.get(index));}
  void navigateHome(){if(pageDirection!=0)pageDirection=-1;clearFolderTrail();folderRootSources=false;activeSource=home;showHomeLanding();}
  int sourceListColorSignature(){return Arrays.hashCode(new int[]{BG,SURFACE,PRIMARY,TEXT,MUTED,DIV});}
  void retainSourceListPage(){if(pageKind!=1||pageFrame==null||root==null||primaryShell==null||pageScroll==null||sourceGrid==null||sourceFilter==null){invalidateRetainedSourceListPage();return;}settlePageTransition();sourceListRebuildFallback=null;retainedSourceListPage=new SourceListPageState();}
  boolean sourceListPageStateValid(SourceListPageState state){return state!=null&&state.pageFrame!=null&&state.root!=null&&state.primaryShell!=null&&state.pageScroll!=null&&state.sourceGrid!=null&&state.sourceFilter!=null&&state.sourcePageSources!=null&&state.sourceDataRevision==sourceDataRevision&&state.densityDpi==getResources().getDisplayMetrics().densityDpi&&state.colorSignature==sourceListColorSignature();}
  void invalidateRetainedSourceListPage(){if(retainedSourceListPage!=null)sourceListRebuildFallback=retainedSourceListPage;retainedSourceListPage=null;}
  boolean restoreSourceListPage(){
    SourceListPageState state=retainedSourceListPage;if(!sourceListPageStateValid(state))return false;retainedSourceListPage=null;sourceListRebuildFallback=null;settlePageTransition();navigationSession++;invalidateSearchRenderSurface();synchronized(sourceSearchLock){sourceSearchSession++;sourceSearchRunning=false;sourceSearchPaused=false;sourceSearchLock.notifyAll();}sourceRenderSession++;sourceFilterGeneration++;dismissBreadcrumbChooser();cancelFolderPullWork();if(sourceSearchRunnable!=null)ui.removeCallbacks(sourceSearchRunnable);
    View previous=pageFrame;ViewParent retainedParent=state.pageFrame.getParent();if(retainedParent instanceof ViewGroup)((ViewGroup)retainedParent).removeView(state.pageFrame);root=state.root;primaryShell=state.primaryShell;pageFrame=state.pageFrame;pageHeaderRow=state.pageHeaderRow;pageScroll=state.pageScroll;sourceGrid=state.sourceGrid;sourceHeading=state.sourceHeading;sourceFilter=state.sourceFilter;sourceCategoryStrip=state.sourceCategoryStrip;searchDragBar=state.searchDragBar;sourcePageSources=state.sourcePageSources;activeSourceCategory=state.activeSourceCategory;
    visibleSources.clear();visibleSources.addAll(state.visibleSources);sourceSelectionChecks.clear();sourceSelectionChecks.putAll(state.sourceSelectionChecks);selectedSourceUrls.clear();selectedSourceUrls.addAll(state.selectedSourceUrls);System.arraycopy(state.sourceSelectionKinds,0,sourceSelectionKinds,0,sourceSelectionKinds.length);sourceSelectionMode=state.sourceSelectionMode;sourceSelectionBar=state.sourceSelectionBar;sourceSelectionSummary=state.sourceSelectionSummary;sourceSelectionRename=state.sourceSelectionRename;sourceSelectionAllButton=state.sourceSelectionAllButton;
    liveGrid=null;itemsGrid=null;content=null;sourceSearch=null;progress=null;status=null;statusRight=null;searchPauseButton=null;folderPullFrame=null;folderPullScroll=null;folderPullIndicator=null;folderPullLabel=null;folderMoreSpinner=null;selectionMode=false;selectionBar=null;downloadSelectionMode=false;downloadsPage=false;activeSource=null;folderRootSources=false;clearFolderTrail();/* [DFW-75] 恢复留存态时**不能**把返回目的地清成 null —— 那会让"设置→资源源管理→返回"掉到软件库（用户真机反馈）。目的地由调用方（showSourcesFromSettings/showSources）设定，整页存活期间保持。 */systemBackAction=sourceListFromSettings?this::showSettings:null;primaryDestination=1;pageKind=1;
    FrameLayout.LayoutParams pageParams=new FrameLayout.LayoutParams(-1,-1);pageHost.addView(pageFrame,0,pageParams);/* [DFW-75] 方向判定：悬浮球切档=fadeThrough(0)；**首次进入**=推入(+1)；从源/文件夹返回=弹出(-1)。
       旧实现只有 0 和 -1 两支，从设置点进来会被当成"返回"，播的是返回动画。 */
    boolean entry=sourceListEntry;sourceListEntry=false;
    if(primaryNavigationSwitch){primaryNavigationSwitch=false;animatePage(previous,pageFrame,0);}else animatePage(previous,pageFrame,entry?1:-1);pageDirection=1;if(root!=null)root.post(this::reflowVisibleLayouts);if(navBall!=null)ui.post(this::refreshNavBall);return true;
  }
  void openSourcePage(Models.Source source){source=runtimeLanzouSource(source);retainSourceListPage();pageDirection=1;resetFolderTrail(source);folderRootSources=true;showFolderPage(activeFolderState.source,true);}
  void openNestedFolder(Models.Item item){pageDirection=1;if(activeSource==null)activeSource=home;captureActiveFolderState();Models.Source nested;try{if(compositeSource(activeSource)&&!item.folderId.isEmpty()&&!item.folderId.startsWith("remote:"))nested=core.compositeFolderSource(activeSource,item.folderId,item.title);else{nested=new Models.Source();nested.title=item.title;nested.url=preferredLanzouUrl(item.url);nested.password=item.password;}}catch(Exception error){showNotice(friendlyError(error),true);return;}activeFolderState=new FolderPageState(nested);folderTrail.add(activeFolderState);showFolderPage(nested,true);}
  void showFolderPage(Models.Source source,boolean refresh){source=runtimeLanzouSource(source);String id=sourceKey(source);if(activeFolderState==null||folderTrail.isEmpty()||!sourceKey(folderTrail.get(folderTrail.size()-1).source).equals(id)){activeFolderState=new FolderPageState(source);folderTrail.add(activeFolderState);}activeSource=source;folderAutoExpandInitialRemaining=sessionAutoExpand?sessionAutoExpandInitialPages:0;folderAutoExpandNextRemaining=0;buildFolderScaffold(source);systemBackAction=this::navigateFolderBack;openFolder(source,refresh);}
  void navigateFolderBack(){pageDirection=-1;if(folderTrail.size()>1){captureActiveFolderState();folderTrail.remove(folderTrail.size()-1);/** F2:父级无快照时显式重开该目录,替代静默 return——否则 systemBackAction 已被消费,下一次返回直接退出应用 */FolderPageState parent=folderTrail.get(folderTrail.size()-1);if(parent.folder==null)showFolderPage(parent.source,false);else restoreFolderPageState(parent);return;}if(folderRootSources){if(!restoreSourceListPage())showSources();}else navigateHome();}
  @Override public void shareText(String value,String title){Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_TEXT,value);try{startActivity(Intent.createChooser(send,title));}catch(Exception error){showNotice("没有可用的分享应用",true);}}
  void shareCurrentSource(){if(activeSource==null)return;shareSources(Collections.singletonList(activeSource));}
  void loading(String s){runOnUiThread(()->{progress.setVisibility(View.VISIBLE);progress.setIndeterminate(true);status.setText(s);if(statusRight!=null)statusRight.setText("");});}
  void openFolder(Models.Source source,boolean refresh){String rememberedAccess=directResolver.cachedPassword(source.url);if((source.password==null||source.password.isEmpty())&&!rememberedAccess.isEmpty())source.password=rememberedAccess;final int session=navigationSession;final String expectedId=sourceKey(source);final java.util.concurrent.atomic.AtomicBoolean liveResolved=new java.util.concurrent.atomic.AtomicBoolean(false);final java.util.concurrent.atomic.AtomicBoolean terminal=new java.util.concurrent.atomic.AtomicBoolean(false);final java.util.concurrent.atomic.AtomicBoolean rendered=new java.util.concurrent.atomic.AtomicBoolean(false);if(compositeSource(source)){renderFolder(core.compositeSnapshot(source));hydrateVisibleCompositeMembers(session,expectedId,source);return;}renderFolder(placeholderFolder(source,"正在解析目录","正在连接当前基础链接，完成前先显示占位目录"));loading("正在读取 "+source.title);ui.postDelayed(()->{if(folderRequestCurrent(session,expectedId)&&!terminal.get()&&!liveResolved.get()){folderProfilePending=true;renderFolder(placeholderFolder(source,"目录仍在解析","正在等待当前基础链接返回目录数据，请稍候"));loading("正在等待目录解析");showNotice("目录仍在解析，已显示占位页面",false);}},22000L);io.execute(()->{try{boolean hadCache=core.hasFolderCache(source,1);if(!folderRequestCurrent(session,expectedId))return;Models.Folder first=core.browseSource(source,false);if(directoryErrorFolder(first))throw new IOException("403 Forbidden");rememberAcceptedSourcePassword(source);if(!hadCache)liveResolved.set(true);runOnUiThread(()->{if(folderRequestCurrent(session,expectedId)&&(!liveResolved.get()||!hadCache)){folderProfilePending=false;renderFolder(first);rendered.set(true);
            /* [DFW-38] stale-while-revalidate：有缓存时这一步只是"先给你能看的内容"，
               后台马上还会再刷一次。旧代码在这里 `loading("正在刷新目录")` ——
               于是用户看到的是"内容已经好了，但进度条还在转、还写着正在刷新"，
               弱网/限频下能一直转到 18 秒超时，这就是"慢"的主要观感来源。
               现在直接收起进度条，后台静默替换。 */
            if(hadCache&&progress!=null)progress.setVisibility(View.GONE);}});if(hadCache){Models.Folder updated=core.browseSource(source,true);if(directoryErrorFolder(updated))throw new IOException("403 Forbidden");boolean changed=!core.sameFolder(first,updated);liveResolved.set(true);runOnUiThread(()->{if(folderRequestCurrent(session,expectedId)){folderProfilePending=false;boolean appended=folderItems.size()>updated.items.size()||current.size()>updated.items.size();if((changed||activeFolderProfile==null)&&!appended)renderFolder(updated);else if(progress!=null)progress.setVisibility(View.GONE);}});}terminal.set(true);}catch(LanzouCore.DirectPasswordException error){terminal.set(true);String rejectedPassword=source.password==null?"":source.password;boolean rejected=!rejectedPassword.isEmpty();directResolver.forgetPassword(source.url);forgetRejectedSourcePassword(source.url,rejectedPassword);source.password="";runOnUiThread(()->{if(folderRequestCurrent(session,expectedId)){folderProfilePending=false;if(progress!=null)progress.setVisibility(View.GONE);showLanzouAccessPrompt(source.url,source.title,rejected,accessValue->{source.password=accessValue;openFolder(source,refresh);},null);}});}catch(Exception error){terminal.set(true);runOnUiThread(()->{if(folderRequestCurrent(session,expectedId)){folderProfilePending=true;if(progress!=null)progress.setVisibility(View.GONE);
            /* [DFW-38] 诊断链修复：`friendlyError()` 是给用户看的**兜底话术**，
               它会把 "403 Forbidden" 这类原始信息洗成"操作失败，请稍后重试"，
               而 `directoryParseFailureReason()` 恰好靠 "403"/"限流" 这些**原始关键词**来给专门解释 ——
               于是"403 风控""触发限流"这些真正有用的解释**永远到不了用户眼前**。
               现在两者分开：给用户看的话术用 friendlyError，给判定用的原始消息用 error.getMessage()。 */
            String raw=error.getMessage()==null?"":error.getMessage();
            String message=friendlyError(error);
            recordDirectoryFailure(source.url,raw);
            if(!autoRetriedDirs.contains(expectedId)){autoRetriedDirs.add(expectedId);showNotice("目录解析中断，正在自动重试…",false);ui.postDelayed(()->{if(folderRequestCurrent(session,expectedId))openFolder(source,true);},900);return;}
            autoRetriedDirs.remove(expectedId);
            /* [DFW-38] stale-if-error：屏幕上已经有一份能看的目录时，刷新失败**不许**把它
               换成"目录解析失败"占位页 —— 那等于把"内容旧了"升级成"内容没了"。
               RFC 5861 的 stale-if-error 就是这个语义：出错时继续用旧数据，只提示不覆盖。 */
            if(rendered.get()){showNotice("目录刷新失败，继续显示上次内容："+message,false);return;}
            renderFolder(placeholderFolder(source,"目录解析失败",directoryParseFailureReason(raw)));
            showNotice("目录解析失败："+message+"，回到顶部下拉可重试",true);}});}});io.execute(()->{try{Models.Folder indexed=core.indexedFolderSnapshot(source,indexRetentionMillis());if(!indexed.items.isEmpty())runOnUiThread(()->{if(folderRequestCurrent(session,expectedId)&&!rendered.get()&&!liveResolved.get()&&!terminal.get()){folderProfilePending=false;renderFolder(indexed);rendered.set(true);if(progress!=null)progress.setVisibility(View.GONE);}});}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}});}
  void hydrateVisibleCompositeMembers(int session,String expectedId,Models.Source source){if(!compositeSource(source))return;int rendered=Math.min(Math.max(0,visible),folderItems.size());if(rendered<=0)return;io.execute(()->{try{core.hydrateCompositeSource(source,false,rendered,new Models.Progress(){@Override public boolean isCancelled(){return !folderRequestCurrent(session,expectedId);}@Override public void onProgress(int done,int total,int found,String current){}@Override public void onItemUpdated(Models.Item item){runOnUiThread(()->acceptCompositeFolderUpdate(session,expectedId,item));}});}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});}
  boolean folderRequestCurrent(int session,String id){return activeSource!=null&&(id.equals(sourceKey(activeSource))||id.equals(activeSource.url)||id.equals(preferredLanzouUrl(activeSource.url)));}
  /**
   * [DFW-38] 判断"解析出来的东西其实是一个错误壳页，而不是真目录"。
   *
   * 旧实现把**每个条目的标题/描述**也拼进去做子串匹配。软件库是软件合集，
   * `nginx-1.24.zip` 这种条目名很常见 —— 一个正常目录只要有一个文件名带 `nginx`，
   * 整页就会被判成"目录解析失败"。这是典型的"低概率、高破坏"误杀。
   *
   * 现在两条约束：
   * ① **条目非空就直接放过** —— 拿到真内容了，就不是错误页；
   * ② 只扫文件夹自身的标题/发布者/描述，并且不再匹配裸的 `nginx`
   *    （真 403 页面的标题里一定有 "403"/"Forbidden"，用不着靠 web 服务器名猜）。
   */
  boolean directoryErrorFolder(Models.Folder folder){if(folder==null)return false;if(folder.items!=null&&!folder.items.isEmpty())return false;String value=(folder.title+"\n"+folder.publisher+"\n"+folder.description).toLowerCase(Locale.ROOT);return value.contains("403")||value.contains("forbidden")||value.contains("access denied")||value.contains("429")||value.contains("too many requests")||value.contains("502")||value.contains("503")||value.contains("504")||value.contains("bad gateway")||value.contains("service unavailable")||value.contains("cloudflare")||value.contains("waf");}
  String directoryParseFailureReason(String message){String raw=message==null?"":message.trim(),value=raw.toLowerCase(Locale.ROOT);if(value.contains("403")||value.contains("forbidden")||value.contains("access denied"))return "当前基础链接返回了禁止访问页面（403/Forbidden），通常是该域名临时风控或访问策略变化。";if(value.contains("502")||value.contains("503")||value.contains("504")||value.contains("bad gateway")||value.contains("service unavailable"))return "当前基础链接的上游服务暂不可用（502/503/504），继续使用该域名可能只能得到错误页。";if(value.contains("429")||value.contains("rate")||value.contains("频繁")||value.contains("限流"))return "当前基础链接触发了访问频率限制，短时间内继续请求可能仍会失败。";if(value.contains("timeout")||value.contains("timed out")||value.contains("超时"))return "当前基础链接在限定时间内没有返回可用目录数据，可能是网络延迟、域名不可达或服务阻塞。";if(value.contains("ssl")||value.contains("certificate")||value.contains("reset")||value.contains("refused")||value.contains("unreachable"))return "当前基础链接出现连接或 TLS 异常，当前网络环境可能无法稳定访问该域名。";return "当前基础链接没有返回可用的目录数据，可能是域名策略、临时风控、网络异常或分享状态变化。";}
  void recordDirectoryFailure(String url,String message){try{android.content.SharedPreferences prefs=getSharedPreferences("dfwx_diagnostics",MODE_PRIVATE);org.json.JSONArray items=new org.json.JSONArray(prefs.getString("failures","[]"));items.put(new org.json.JSONObject().put("at",System.currentTimeMillis()).put("url",url==null?"":url).put("error",message==null?"":message));while(items.length()>8)items.remove(0);prefs.edit().putString("failures",items.toString()).apply();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}}
  // T4 关于页大改（用户指令：全部改独立页面不弹窗）：参考致谢 9 项原文全数迁入，关于页与独立参考页共用
  static final String[][] ACK_ITEMS={{"RikkaHub","AI 对话核心参考（AGPL-3.0）· 助手系统/数据模型/生成管线","https://github.com/rikkahub/rikkahub"},{"LanzouPlus","核心引擎（MIT）· 目录浏览/搜索/下载","https://github.com/nekobyran/lanzouplus"},{"AndroidVeil","骨架屏与微光扫过理念（Apache-2.0）","https://github.com/skydoves/AndroidVeil"},{"Material Symbols","Google 官方图标语义体系","https://github.com/google/material-design-icons"},{"Lottie Android","动效与无缝过渡的行业标准参考","https://github.com/airbnb/lottie-android"},{"SmoothBottomBar","导航激活指示器动效参考","https://github.com/ibrahimsn98/SmoothBottomBar"},{"langchain4j","AI 对话流式协议解析参考（Apache-2.0）","https://github.com/langchain4j/langchain4j"},{"openai-java","OpenAI 兼容 API 规范参考（MIT）","https://github.com/TheoKanning/openai-java"},{"UX Planet","高级感配色哲学：《The One Color Decision》","https://uxplanet.org/the-one-color-decision-that-makes-a-ui-look-expensive-d6890efe11ba"}};
  LinearLayout aboutCard(){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);GradientDrawable bg=new GradientDrawable();bg.setColor(SET_LOW);bg.setCornerRadius(dp(20));bg.setStroke(dp(1),SET_STROKE);card.setBackground(bg);card.setPadding(dp(16),dp(14),dp(16),dp(14));return card;}
  LinearLayout.LayoutParams aboutCardLp(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(4),0,dp(10));return lp;}
  TextView aboutHeading(String s){TextView t=text(s,13,PRIMARY);t.setTypeface(AppFonts.bold(this));return t;}
  View aboutDivider(){View d=new View(this);d.setBackgroundColor(SET_STROKE2);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(0,dp(12),0,dp(12));d.setLayoutParams(lp);return d;}
  /** 设置体系下各子页的返回条（默认回设置页）。 */
  LinearLayout aboutBackBar(String title){return aboutBackBar(title,this::showSettings);}

  /**
   * [DFW-72] 可指定返回动作的返回条。
   *
   * a11y 文案是中性的「返回」——页头只有一种返回控件，不在页面上写死"回设置"，
   * 否则读屏会把公告页的返回念成"返回设置"。
   */
  LinearLayout aboutBackBar(String title,Runnable back){primaryHeader(title);ImageButton backBtn=iconButton(R.drawable.ic_back,"返回");backBtn.setOnClickListener(v->{pageDirection=-1;back.run();});pageHeaderRow.addView(backBtn,0,new LinearLayout.LayoutParams(dp(44),dp(48)));LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);return body;}

  /**
   * [DFW-66] **只要页头、不要返回按钮**——顶级页专用。
   *
   * 用户 2026-10-01："公告界面改为单独的界面，所以你可以去掉左上角的返回按钮了，
   * 这样不至于会和其他几个按钮的界面感到混乱。"
   * 公告与软件库/下载/工具箱/设置同级，那些页面都没有返回箭头，只有公告有就会显得它还是个"子页"。
   * 退出仍由系统返回键（边缘滑动）负责，语义在 `systemBackAction` 里。
   */
  LinearLayout aboutBodyOnly(String title){primaryHeader(title);LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);return body;}
  LinearLayout aboutPersonRow(int avatarRes,String name,String role,String desc){LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);ImageView avatar=new ImageView(this);avatar.setImageResource(avatarRes);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setClipToOutline(true);avatar.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,Outline outline){outline.setOval(0,0,view.getWidth(),view.getHeight());}});LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(dp(46),dp(46));ap.setMargins(0,0,dp(12),0);row.addView(avatar,ap);LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);LinearLayout titleLine=new LinearLayout(this);titleLine.setGravity(Gravity.CENTER_VERTICAL);TextView nameView=text(name,15,TEXT);nameView.setTypeface(AppFonts.bold(this));titleLine.addView(nameView,new LinearLayout.LayoutParams(-2,dp(24)));TextView roleView=text(role,11,PRIMARY);roleView.setPadding(dp(8),0,0,0);titleLine.addView(roleView,new LinearLayout.LayoutParams(-2,dp(24)));copy.addView(titleLine,new LinearLayout.LayoutParams(-1,dp(26)));TextView descView=text(desc,12,MUTED);descView.setLineSpacing(dp(2),1f);copy.addView(descView,new LinearLayout.LayoutParams(-1,-2));row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));return row;}
  LinearLayout buildAckListBlock(String intro){LinearLayout block=new LinearLayout(this);block.setOrientation(LinearLayout.VERTICAL);if(intro!=null&&!intro.isEmpty()){TextView note=text(intro,12,MUTED);note.setLineSpacing(dp(2),1f);block.addView(note,new LinearLayout.LayoutParams(-1,-2));block.addView(aboutDivider());}boolean first=true;for(String[] item:ACK_ITEMS){if(!first)block.addView(aboutDivider());first=false;LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setClickable(true);row.setFocusable(true);row.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));applePressScale(row);row.setContentDescription(item[0]+"，打开项目页");TextView nameView=text(item[0],14,TEXT);nameView.setTypeface(AppFonts.bold(this));TextView descView=text(item[1],11,MUTED);descView.setMaxLines(1);descView.setEllipsize(android.text.TextUtils.TruncateAt.END);row.addView(nameView,new LinearLayout.LayoutParams(-1,dp(24)));row.addView(descView,new LinearLayout.LayoutParams(-1,dp(20)));String url=item[2];row.setOnClickListener(v->openInBrowser(url,""));block.addView(row,new LinearLayout.LayoutParams(-1,-2));}return block;}
  void showAcknowledgementsPage(){primaryBase(3);pageKind=8;activeSource=null;clearFolderTrail();systemBackAction=this::showSettings;LinearLayout body=aboutBackBar("参考与致谢");LinearLayout card=aboutCard();card.addView(buildAckListBlock("东方无限参考/使用了以下开源项目与设计研究，感谢开源社区："),new LinearLayout.LayoutParams(-1,-2));body.addView(card,aboutCardLp());ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));}
  void showDirectoryParseRecoveryDialog(Models.Source source,String message){if(getSharedPreferences("directory_parse_recovery_v147",MODE_PRIVATE).getBoolean("dont_remind",false))return;String detail=message==null||message.isEmpty()?"目录暂未解析完成":message,reason=directoryParseFailureReason(detail);String br=System.lineSeparator();LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(8),dp(22),0);TextView body=text(reason+br+br+"应用不会把错误页当成目录显示，会继续保留占位页面。"+br+br+"技术信息："+detail+br+br+"是否现在更换基础链接？",14,TEXT);panel.addView(body,new LinearLayout.LayoutParams(-1,-2));CheckBox never=new CheckBox(this);never.setText("不再提醒");never.setTextColor(TEXT);panel.addView(never,new LinearLayout.LayoutParams(-1,dp(48)));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("目录解析失败").setView(panel).setNegativeButton("稍后",null).setPositiveButton("更换基础链接",(d,w)->showLanzouBaseOriginDialog(source.url)).setNeutralButton("UA 设置",(d,w)->showUaSettingsDialog()).create();dialog.setOnDismissListener(ignored->{if(never.isChecked())getSharedPreferences("directory_parse_recovery_v147",MODE_PRIVATE).edit().putBoolean("dont_remind",true).apply();});showRounded(dialog);}
  Models.Folder placeholderFolder(Models.Source source,String title,String description){Models.Folder folder=new Models.Folder();folder.title=title;folder.publisher="目录信息加载中";folder.url=source==null?"":source.url;folder.password=source==null?"":source.password;folder.avatarUrl="placeholder:directory";String sourceName=source==null||source.title.isEmpty()?"当前目录":source.title;folder.description=sourceName+" · "+description;folder.hasMore=false;folder.nextPageReadyAt=System.currentTimeMillis()+1500;String[] names={"目录标题加载中","项目列表加载中","图标与详情加载中"};String[] descs={"正在拼接分享路径并连接当前基础链接","解析完成后会自动替换为真实文件夹和文件","不会把 403、网关错误或拦截页面当成目录"};for(int i=0;i<names.length;i++){Models.Item item=new Models.Item();item.title=names[i];item.url="placeholder:directory:"+i;item.shareUrl=folder.url;item.folder=true;item.source=sourceName;item.description=descs[i];folder.items.add(item);}return folder;}
  boolean isDirectoryPlaceholder(Models.Folder folder){return folder!=null&&folder.avatarUrl!=null&&folder.avatarUrl.startsWith("placeholder:directory");}
  void applySourcePasswordInMemory(String url,String expectedPassword,String replacement){if(url==null||url.isEmpty())return;List<Models.Source> targets=new ArrayList<>();if(sourcePageSources!=null)targets.addAll(sourcePageSources);if(retainedSourceListPage!=null&&retainedSourceListPage.sourcePageSources!=sourcePageSources)targets.addAll(retainedSourceListPage.sourcePageSources);for(Models.Source source:targets){if(url.equals(source.url)&&(expectedPassword==null||Objects.equals(source.password,expectedPassword)))source.password=replacement;for(Models.SourceMember member:source.members)if(url.equals(member.url)&&(expectedPassword==null||Objects.equals(member.password,expectedPassword)))member.password=replacement;}}
  void rememberAcceptedSourcePassword(Models.Source source){if(source==null||source.url==null||source.url.isEmpty()||source.password==null||source.password.isEmpty())return;String url=source.url,password=source.password;directResolver.rememberPassword(url,password);try{if(core.updateUserSourcePassword(url,null,password))runOnUiThread(()->applySourcePasswordInMemory(url,null,password));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}}
  void forgetRejectedSourcePassword(String url,String rejectedPassword){if(url==null||url.isEmpty()||rejectedPassword==null||rejectedPassword.isEmpty())return;try{if(core.updateUserSourcePassword(url,rejectedPassword,""))runOnUiThread(()->applySourcePasswordInMemory(url,rejectedPassword,""));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}}
  static boolean folderVisuallySame(Models.Folder a,Models.Folder b){// 视觉等价判定:与 sameFolder 的全字段 JSON 对比不同,只比较用户能看到的内容,避免缓存与实时解析的非视觉差异触发整页重建
    if(a==null||b==null)return false;
    if(!Objects.equals(a.title,b.title)||!Objects.equals(a.publisher,b.publisher)||!Objects.equals(a.description,b.description)||!Objects.equals(a.avatarUrl,b.avatarUrl)||a.hasMore!=b.hasMore||a.items.size()!=b.items.size())return false;
    for(int i=0;i<a.items.size();i++){Models.Item x=a.items.get(i),y=b.items.get(i);if(x==null||y==null)return false;if(!Objects.equals(x.url,y.url)||!Objects.equals(x.title,y.title)||!Objects.equals(x.size,y.size)||!Objects.equals(x.time,y.time)||!Objects.equals(x.iconUrl,y.iconUrl)||x.folder!=y.folder||!Objects.equals(x.description,y.description)||!Objects.equals(x.error,y.error))return false;}
    return true;}
  void renderFolder(Models.Folder f){
    if(!restoringFolderState&&f!=null&&activeFolderProfile!=null&&!isDirectoryPlaceholder(activeFolderProfile)&&!isDirectoryPlaceholder(f)&&folderVisuallySame(activeFolderProfile,f)){
      /* [DFW-54] "内容没变就直接 return" 这条早退**必须照样清理下拉/刷新状态**。
         旧版直接 return，跳过了 cancelFolderPullWork() → 指示器永远停在「正在刷新目录」，
         用户看到的是"明明已经加载完了、状态行都写「已全部加载」了，那个转圈还在转"。 */
      cancelFolderPullWork();folderProfilePending=false;if(progress!=null)progress.setVisibility(View.GONE);return;}
    cancelFolderPullWork();if(progress!=null)progress.setVisibility(View.GONE);boolean wasPlaceholder=isDirectoryPlaceholder(activeFolderProfile);content.removeAllViews();activeFolderProfile=f;FolderPageState saved=restoringFolderState?activeFolderState:null;int restoredScroll=0;if(saved!=null){currentSourceQuery=saved.query;folderItems=new ArrayList<>(saved.folderItems);current=new ArrayList<>(saved.current);visible=Math.min(saved.visible,current.size());folderNextPage=saved.nextPage;folderHasMore=saved.hasMore;folderNextReadyAt=saved.nextReadyAt;restoredScroll=saved.scrollY;}else{currentSourceQuery="";folderItems=new ArrayList<>(f.items);if(activeSource!=null&&activeSource.url.equals(home.url))homeItems=new ArrayList<>(folderItems);current=new ArrayList<>(folderItems);visible=listInitialVisible(current.size());folderNextPage=Math.max(2,f.page+1);folderHasMore=f.hasMore;if(folderItems.isEmpty())folderHasMore=false;folderNextReadyAt=Math.max(System.currentTimeMillis(),f.nextPageReadyAt);}invalidateFolderSearchIndex();
    LinearLayout profile=new LinearLayout(this);profile.setOrientation(LinearLayout.VERTICAL);profile.setPadding(0,dp(10),0,dp(10));
    LinearLayout identity=new LinearLayout(this);identity.setGravity(Gravity.TOP);
    ImageView avatar=new ImageView(this);avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setBackground(solidShape(SURFACE,28));avatar.setClipToOutline(true);identity.addView(avatar,new LinearLayout.LayoutParams(dp(56),dp(56)));if(isDirectoryPlaceholder(f)){GradientDrawable ghostBg=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{ThemeEngine.tint(PRIMARY_HI,70),ThemeEngine.tint(PRIMARY_LO,40)});ghostBg.setCornerRadius(dp(28));avatar.setBackground(ghostBg);avatar.setImageResource(R.drawable.ic_folder_open);avatar.setColorFilter(PRIMARY);}else loadImage(f.avatarUrl,avatar);
    LinearLayout meta=new LinearLayout(this);meta.setOrientation(LinearLayout.VERTICAL);meta.setPadding(dp(14),0,0,0);TextView title=text(f.title,22,TEXT);title.setMaxLines(2);selectableLinks(title);meta.addView(title);LinearLayout publisherRow=new LinearLayout(this);publisherRow.setGravity(Gravity.CENTER_VERTICAL);TextView publisher=text(isDirectoryPlaceholder(f)?f.publisher:(f.publisher.isEmpty()?"":"由 "+f.publisher+" 发布"),13,MUTED);publisher.setTextIsSelectable(true);publisherRow.addView(publisher,new LinearLayout.LayoutParams(0,dp(34),1));publisherRow.addView(folderCategoryAction(activeSource),new LinearLayout.LayoutParams(dp(76),dp(34)));meta.addView(publisherRow,new LinearLayout.LayoutParams(-1,dp(34)));identity.addView(meta,new LinearLayout.LayoutParams(0,-2,1));profile.addView(identity,new LinearLayout.LayoutParams(-1,-2));
    if(!f.description.isEmpty()){String compact=f.description.replaceAll("\\n{3,}","\\n\\n");TextView desc=text(compact,13,MUTED);desc.setGravity(Gravity.START);desc.setIncludeFontPadding(false);desc.setLineSpacing(-dp(2),1f);desc.setPadding(0,dp(8),0,dp(4));selectableLinks(desc);profile.addView(desc,new LinearLayout.LayoutParams(-1,-2));}
    LinearLayout tools=new LinearLayout(this);tools.setGravity(Gravity.CENTER_VERTICAL);if(compositeSource(activeSource)&&!transientLinkSource(activeSource)){ImageButton addNode=iconButton(R.drawable.ic_add,"向当前目录添加蓝奏源"),addFolder=iconButton(R.drawable.ic_folder,"在当前目录新建文件夹");addNode.setOnClickListener(v->showAddCompositeNodeDialog());addFolder.setOnClickListener(v->showAddCompositeFolderDialog());tools.addView(addNode,new LinearLayout.LayoutParams(dp(52),dp(48)));tools.addView(addFolder,new LinearLayout.LayoutParams(dp(52),dp(48)));View spacer=new View(this);tools.addView(spacer,new LinearLayout.LayoutParams(0,dp(48),1));}else tools.addView(folderOriginalLink(activeSource),new LinearLayout.LayoutParams(0,dp(48),1));TextView share=text("分享",12,PRIMARY);share.setGravity(Gravity.CENTER);share.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));share.setContentDescription(compositeSource(activeSource)&&!transientLinkSource(activeSource)?"分享自建合集链接":"分享当前源");share.setClickable(true);share.setFocusable(true);share.setOnClickListener(v->shareCurrentSource());tools.addView(share,new LinearLayout.LayoutParams(dp(56),dp(48)));profile.addView(tools,new LinearLayout.LayoutParams(-1,dp(48)));
    content.addView(profile);profilePresent=true;if(isDirectoryPlaceholder(f)){renderSkeletonRows();}else{renderItems();postFolderIconPrefetch(folderItems,Math.min(visible,folderItems.size()),14);}restoreFolderScrollBeforeDraw(pageScroll,restoredScroll);if(saved==null)captureActiveFolderState();if(!isDirectoryPlaceholder(f)&&motionEnabled()){content.setAlpha(0f);content.animate().alpha(1f).setDuration(200).setInterpolator(standardEase()).start();}
  }
  void renderSkeletonRows(){DfwxSkeleton skeleton=new DfwxSkeleton(this,SURFACE2,itemColumns(),6);content.addView(skeleton,new LinearLayout.LayoutParams(-1,dp(216)));folderRenderingRows=false;updateFolderPullAvailability();}

  void scheduleInitialFolderAutoExpand(){/*进入目录不再自动预取下一页:加载只发生一次,后续由滚动接近底部触发,消除打开即多次加载与闪烁*/}
  void queueCurrentSourceSearch(String query){currentSourceQuery=query;int token;synchronized(sourceSearchLock){token=++sourceSearchSession;sourceSearchRunning=false;sourceSearchPaused=false;sourceSearchLock.notifyAll();}sourceSearchPages=0;applyLocalSourceSearch(query);ui.removeCallbacks(sourceSearchRunnable);int points=query.codePointCount(0,query.length());if(query.isEmpty()||activeSource==null||points<2){setDirectoryIndexSearchPaused(false);if(progress!=null)progress.setVisibility(View.GONE);if(searchPauseButton!=null)searchPauseButton.setVisibility(View.GONE);if(statusRight!=null)statusRight.setText(points==1?"仅名称匹配":"");return;}setDirectoryIndexSearchPaused(true);Models.Source source=activeSource;List<Models.Item> local=new ArrayList<>(folderItems);Models.SearchOptions options=searchOptions(1,sessionSearchMaxPages).withRecursiveFolders(true).withModeMask(sessionFileListModeMask);synchronized(sourceSearchLock){if(token!=sourceSearchSession)return;sourceSearchRunning=true;}refreshCurrentSourceSearchUi(token,compositeSource(source)?"合集递归匹配中":options.apiOnly()?"API 搜索中":"缓存优先匹配中");if(options.indexOnly()){searchIndexIo.execute(()->{try{Set<String> ids=Collections.singleton(sourceKey(source));acceptCurrentSourceSearchBatch(token,source,query,core.cachedPartialIndexMatches(query,ids,fuzzyIndexEnabled()));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}finally{runOnUiThread(()->finishCurrentSourceSearch(token,source,query,false));}});return;}if(options.indexEnabled()){io.execute(()->{try{if(options.indexEnabled())acceptCurrentSourceSearchBatch(token,source,query,core.cachedPartialIndexMatches(query,Collections.singleton(sourceKey(source)),fuzzyIndexEnabled()));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}});searchIndexIo.execute(()->{try{acceptCurrentSourceSearchBatch(token,source,query,core.cachedDirectoryMatchesWarm(query,Collections.singleton(sourceKey(source)),true,sessionSearchMaxPages,fuzzyIndexEnabled()));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}});}sourceSearchRunnable=()->io.execute(()->{java.util.concurrent.atomic.AtomicBoolean failed=new java.util.concurrent.atomic.AtomicBoolean();try{List<Models.Item> result=core.search(query,source,local,options,Collections.singleton(sourceKey(source)),new Models.Progress(){@Override public boolean isCancelled(){synchronized(sourceSearchLock){return token!=sourceSearchSession;}}@Override public boolean awaitIfPaused(){synchronized(sourceSearchLock){while(token==sourceSearchSession&&sourceSearchPaused)try{sourceSearchLock.wait();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}return token==sourceSearchSession;}}@Override public void onBatch(List<Models.Item> batch){acceptCurrentSourceSearchBatch(token,source,query,batch);core.mergeSearchResultsIntoIndex(batch,indexRetentionMillis());}@Override public void onItemUpdated(Models.Item item){acceptCurrentSourceSearchUpdate(token,source,query,item);}@Override public void onFailure(String name){failed.set(true);}@Override public void onPage(String name,int page,int pageItems,int sourceFound,int totalPagesSeen){runOnUiThread(()->{if(!currentSourceSearchUiCurrent(token,source,query))return;sourceSearchPages=Math.max(sourceSearchPages,totalPagesSeen);statusRight.setText("第 "+page+" 页 · 累计 "+sourceSearchPages+" 页");});}@Override public void onProgress(int done,int total,int found,String name){runOnUiThread(()->refreshCurrentSourceSearchUi(token,"已找到 "+current.size()));}});acceptCurrentSourceSearchBatch(token,source,query,result);runOnUiThread(()->finishCurrentSourceSearch(token,source,query,failed.get()));}catch(Exception error){runOnUiThread(()->finishCurrentSourceSearch(token,source,query,true));}});ui.postDelayed(sourceSearchRunnable,280);}
  boolean currentSourceSearchUiCurrent(int token,Models.Source source,String query){return token==sourceSearchSession&&pageKind==3&&activeSource!=null&&sourceKey(source).equals(sourceKey(activeSource))&&query.equals(currentSourceQuery)&&itemsGrid!=null&&content!=null&&itemsGrid.getParent()==content;}
  void acceptCurrentSourceSearchBatch(int token,Models.Source source,String query,List<Models.Item> batch){if(batch==null||batch.isEmpty())return;List<Models.Item> copy=new ArrayList<>(batch);runOnUiThread(()->{if(!currentSourceSearchUiCurrent(token,source,query))return;boolean changed=false;for(Models.Item item:copy){int present=indexOfItem(current,item.url);if(present>=0){mergeSearchItem(current.get(present),item);changed=true;}else if(currentSourceSearchUrls.add(item.url)){insertSearchItem(current,item);changed=true;}}if(!changed)return;visible=Math.min(current.size(),Math.max(SEARCH_WINDOW,visible));reconcileFolderSearchGrid();if(searchDragBar!=null)searchDragBar.invalidate();refreshCurrentSourceSearchUi(token,"已找到 "+current.size());updateFolderPullAvailability();captureActiveFolderState();});}
  void acceptCurrentSourceSearchUpdate(int token,Models.Source source,String query,Models.Item item){if(item==null||item.url.isEmpty())return;runOnUiThread(()->{if(!currentSourceSearchUiCurrent(token,source,query))return;int old=indexOfItem(current,item.url);if(old<0)return;replaceSearchItem(current,item);if(replaceItem(folderItems,item))invalidateFolderSearchIndex();if(old<itemsGrid.getChildCount())bindItemCard(itemsGrid.getChildAt(old),item);reconcileFolderSearchGrid();captureActiveFolderState();});}
  void acceptCompositeFolderUpdate(int session,String expectedId,Models.Item item){if(item==null||item.url.isEmpty()||!folderRequestCurrent(session,expectedId)||content==null||itemsGrid==null)return;int folderIndex=indexOfItem(folderItems,item.url);if(folderIndex<0)return;folderItems.set(folderIndex,item);invalidateFolderSearchIndex();String query=currentSourceQuery.toLowerCase(Locale.ROOT);boolean matches=query.isEmpty()||folderMatchRank(item.title.toLowerCase(Locale.ROOT),query,fuzzyFolderListEnabled())>=0;int index=indexOfItem(current,item.url);if(index>=0&&!matches){current.remove(index);selectedUrls.remove(item.url);selectionChecks.remove(item.url);if(index<visible&&index<itemsGrid.getChildCount()){itemsGrid.removeViewAt(index);visible=Math.max(0,visible-1);reflowFolderCards(index);}else visible=Math.min(visible,current.size());}else if(index>=0){current.set(index,item);replaceFolderCard(index,item);}else if(matches){int insert=0;for(int i=0;i<folderIndex;i++)if(indexOfItem(current,folderItems.get(i).url)>=0)insert++;current.add(insert,item);visible=Math.min(current.size(),Math.max(visible+1,query.isEmpty()?listInitialVisible(current.size()):current.size()));if(insert<visible){if(folderEmptyState!=null&&folderEmptyState.getParent()==content)content.removeView(folderEmptyState);folderEmptyState=null;itemsGrid.addView(itemRow(item),insert,itemLayout(insert,itemColumns()));reflowFolderCards(insert+1);}}updateItemCount(Math.min(visible,current.size()));updateFolderPullAvailability();captureActiveFolderState();}
  void replaceFolderCard(int index,Models.Item item){if(index<0||index>=visible||index>=itemsGrid.getChildCount())return;selectionChecks.remove(item.url);itemsGrid.removeViewAt(index);itemsGrid.addView(itemRow(item),index,itemLayout(index,itemColumns()));}
  void reflowFolderCards(int start){int columns=itemColumns();for(int i=Math.max(0,start);i<itemsGrid.getChildCount();i++)itemsGrid.getChildAt(i).setLayoutParams(itemLayout(i,columns));}
  void refreshCurrentSourceSearchUi(int token,String right){if(token!=sourceSearchSession||status==null||statusRight==null||progress==null)return;boolean running,paused;synchronized(sourceSearchLock){running=sourceSearchRunning;paused=sourceSearchPaused;}status.setText((compositeSource(activeSource)?"合集本地匹配":"名称 + API + 分页 + 文件夹")+" · "+current.size()+" 个项目");statusRight.setText(paused?"已暂停":right);if(progress!=null)progress.setVisibility(running?View.VISIBLE:View.GONE);progress.setIndeterminate(running&&!paused);if(paused){progress.setIndeterminate(false);progress.setProgress(0);}if(searchPauseButton!=null){searchPauseButton.setVisibility(running?View.VISIBLE:View.GONE);searchPauseButton.setText(paused?"继续":"暂停");searchPauseButton.setContentDescription(paused?"继续当前源搜索":"暂停当前源搜索");}}
  void toggleCurrentSourceSearchPaused(int token){synchronized(sourceSearchLock){if(token!=sourceSearchSession||!sourceSearchRunning)return;sourceSearchPaused=!sourceSearchPaused;sourceSearchLock.notifyAll();}refreshCurrentSourceSearchUi(token,sourceSearchPaused?"已暂停":"继续搜索");}
  void finishCurrentSourceSearch(int token,Models.Source source,String query,boolean failed){setDirectoryIndexSearchPaused(false);if(!currentSourceSearchUiCurrent(token,source,query))return;synchronized(sourceSearchLock){sourceSearchRunning=false;sourceSearchPaused=false;sourceSearchLock.notifyAll();}progress.setVisibility(View.GONE);if(searchPauseButton!=null)searchPauseButton.setVisibility(View.GONE);status.setText("已完成 · 找到 "+current.size()+" 个项目");statusRight.setText(failed?"部分目录异常":compositeSource(source)?"本地合集":"API + 分页 + 递归");captureActiveFolderState();}
  void invalidateFolderSearchIndex(){folderSearchIndexedSize=-1;folderSearchIndex.clear();folderSearchCandidates.clear();folderSearchPreviousQuery="";}
  void ensureFolderSearchIndex(){if(folderSearchIndexedSize==folderItems.size())return;folderSearchIndex=new ArrayList<>(folderItems.size());for(Models.Item item:folderItems)folderSearchIndex.add(new FolderSearchEntry(item));folderSearchCandidates=new ArrayList<>(folderSearchIndex);folderSearchIndexedSize=folderItems.size();folderSearchPreviousQuery="";}
  void applyLocalSourceSearch(String query){String key=query.toLowerCase(Locale.ROOT);ensureFolderSearchIndex();boolean fuzzy=fuzzyFolderListEnabled();List<FolderSearchEntry> scan=!folderSearchPreviousQuery.isEmpty()&&key.startsWith(folderSearchPreviousQuery)?folderSearchCandidates:folderSearchIndex;List<FolderSearchEntry> candidates=new ArrayList<>();List<Models.Item> filtered=new ArrayList<>();int maxRank=fuzzy?1:0;for(int rank=0;rank<=maxRank;rank++)for(FolderSearchEntry entry:scan)if(key.isEmpty()||folderMatchRank(entry.folded,key,fuzzy)==rank){candidates.add(entry);insertSearchItem(filtered,entry.item);}folderSearchCandidates=candidates;folderSearchPreviousQuery=key;current=filtered;currentSourceSearchUrls.clear();for(Models.Item item:current)currentSourceSearchUrls.add(item.url);visible=listInitialVisible(current.size());if(profilePresent&&content!=null)reconcileFolderSearchGrid();if(status!=null)status.setText(key.isEmpty()?folderItems.size()+" 个项目":(fuzzy?"缓存模糊匹配 ":"缓存名称匹配 ")+filtered.size()+" 个项目");captureActiveFolderState();}
  static int folderMatchRank(String value,String key,boolean fuzzy){if(key==null||key.isEmpty())return 0;String folded=value==null?"":value;if(folded.contains(key))return 0;return fuzzy&&folderFuzzySubsequence(folded,key)?1:-1;}
  static boolean folderFuzzySubsequence(String value,String key){int at=0;for(int i=0;i<key.length();i++){char q=key.charAt(i);if(Character.isWhitespace(q))continue;while(at<value.length()&&value.charAt(at)!=q)at++;if(at>=value.length())return false;at++;}return true;}
  void reconcileFolderSearchGrid(){if(itemsGrid==null||content==null){renderItems();return;}int count=Math.min(visible,current.size()),columns=itemColumns();selectionChecks.clear();if(count==0){if(folderEmptyState==null){folderEmptyState=folderEmptyCard();LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,dp(12),0,dp(10));content.addView(folderEmptyState,Math.max(0,content.indexOfChild(itemsGrid)),params);}}else if(folderEmptyState!=null&&folderEmptyState.getParent()==content){content.removeView(folderEmptyState);folderEmptyState=null;}itemsGrid.setColumnCount(columns);for(int index=0;index<count;index++){Models.Item item=current.get(index);View row=index<itemsGrid.getChildCount()?itemsGrid.getChildAt(index):null;if(!item.url.equals(searchRowUrl(row))){View reuse=null;for(int at=index+1;at<itemsGrid.getChildCount();at++)if(item.url.equals(searchRowUrl(itemsGrid.getChildAt(at)))){reuse=itemsGrid.getChildAt(at);itemsGrid.removeViewAt(at);break;}if(reuse==null)reuse=itemRow(item);itemsGrid.addView(reuse,index,itemLayout(index,columns));row=reuse;}bindItemCard(row,item);row.setLayoutParams(itemLayout(index,columns));}while(itemsGrid.getChildCount()>count)itemsGrid.removeViewAt(itemsGrid.getChildCount()-1);updateItemCount(count);updateFolderPullAvailability();}
  int listInitialVisible(int total){return sessionListInitialCount<=0?Math.max(0,total):Math.min(Math.max(0,total),sessionListInitialCount);}
  int listAppendEnd(int start,int total){return sessionListAppendCount<=0?Math.max(start,total):Math.min(Math.max(start,total),start+sessionListAppendCount);}
  int sourceListDisplayCount(){return normalizeSourceListDisplayCount(sessionSourceListDisplayCount);}
  int adaptiveFolderUiChunk(){return Math.max(4,Runtime.getRuntime().availableProcessors()*2);}
  void appendFolderRowsFrame(GridLayout grid,int session,int token,int cursor,int target,int columns,Runnable completed){if(token!=folderRenderGeneration||session!=navigationSession||grid!=itemsGrid||grid.getParent()!=content)return;int end=Math.min(target,cursor+adaptiveFolderUiChunk());boolean animateEntrance=motionEnabled()&&cursor==0&&target<=listInitialVisible(target)+8;for(int i=cursor;i<end;i++){View row=itemRow(current.get(i));grid.addView(row,itemLayout(i,columns));if(animateEntrance&&i<8){row.setAlpha(0f);row.setTranslationY(dp(8));row.animate().alpha(1f).translationY(0f).setDuration(150).setInterpolator(standardEase()).setStartDelay(i*30L).start();}}if(end<target){grid.postOnAnimation(()->appendFolderRowsFrame(grid,session,token,end,target,columns,completed));return;}folderRenderingRows=false;visible=target;updateItemCount(target);updateFolderPullAvailability();captureActiveFolderState();if(completed!=null)completed.run();}
  void appendFolderRowsAsync(int start,int target,Runnable completed){GridLayout grid=itemsGrid;if(grid==null||target<=start){folderRenderingRows=false;visible=Math.max(visible,target);if(completed!=null)completed.run();return;}folderRenderingRows=true;int token=++folderRenderGeneration,session=navigationSession,columns=itemColumns();appendFolderRowsFrame(grid,session,token,start,target,columns,completed);}
  void renderItems(){
    int keep=profilePresent?1:0;while(content.getChildCount()>keep)content.removeViewAt(keep);folderEmptyState=null;selectionChecks.clear();int n=Math.min(visible,current.size()),columns=itemColumns();liveGrid=null;itemsGrid=newItemGrid(columns);content.addView(itemsGrid,new LinearLayout.LayoutParams(-1,-2));if(n==0){folderEmptyState=folderEmptyCard();LinearLayout.LayoutParams emptyParams=new LinearLayout.LayoutParams(-1,-2);emptyParams.setMargins(0,dp(12),0,dp(10));content.addView(folderEmptyState,Math.max(0,content.indexOfChild(itemsGrid)),emptyParams);folderRenderingRows=false;updateItemCount(0);updateFolderPullAvailability();return;}appendFolderRowsAsync(0,n,()->{if(sessionAutoExpand&&folderPullScroll!=null)folderPullScroll.post(()->{if(content==null||folderPullScroll==null)return;int remaining=content.getHeight()-folderPullScroll.getScrollY()-folderPullScroll.getHeight();if(remaining<=dp(180))maybeAutoExpand();});});
  }
  View folderEmptyCard(){LinearLayout card=new LinearLayout(this);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(16),dp(12),dp(16),dp(12));card.setBackground(solidShape(SURFACE,16));ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_folder);icon.setColorFilter(PRIMARY);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);card.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(12),0,0,0);TextView title=text("暂无文件",15,TEXT);title.setTypeface(AppFonts.bold(this));TextView note=text("当前目录没有可显示的内容",11,MUTED);copy.addView(title,new LinearLayout.LayoutParams(-1,dp(26)));copy.addView(note,new LinearLayout.LayoutParams(-1,dp(24)));card.addView(copy,new LinearLayout.LayoutParams(0,dp(50),1));card.setContentDescription("暂无文件，当前目录没有可显示的内容");return card;}
  boolean folderPullAvailable(){return pageKind==3&&profilePresent&&!folderLoadingMore&&!folderRefreshing&&!folderRenderingRows&&(visible<current.size()||folderHasMore&&currentSourceQuery.isEmpty()&&activeSource!=null);}
  boolean folderRefreshAvailable(){return pageKind==3&&profilePresent&&!folderLoadingMore&&!folderRefreshing&&!folderRenderingRows&&activeSource!=null;}
  void maybeAutoExpand(){if(!sessionAutoExpand||folderLoadingMore||!folderPullAvailable()||folderPullScroll==null||content==null)return;boolean initial=sessionAutoExpandInitialPages==0||folderAutoExpandInitialRemaining>0;if(initial){if(sessionAutoExpandInitialPages>0)folderAutoExpandInitialRemaining--;expandMore();return;}boolean chained=sessionAutoExpandNextPages==0||folderAutoExpandNextRemaining>0;if(chained){if(sessionAutoExpandNextPages>0)folderAutoExpandNextRemaining--;expandMore();return;}int remaining=content.getHeight()-folderPullScroll.getScrollY()-folderPullScroll.getHeight();if(remaining<=dp(180)){folderAutoExpandNextRemaining=sessionAutoExpandNextPages; if(sessionAutoExpandNextPages>0)folderAutoExpandNextRemaining--;expandMore();}}
  void showFolderEndNoticeOnce(){long now=System.currentTimeMillis();if(now<folderEndNoticeUntil)return;folderEndNoticeUntil=now+2500L;showNotice("已全部加载",false);}
  void updateFolderPullAvailability(){if(pageScroll==null||pageKind!=3)return;boolean more=folderPullAvailable(),refresh=folderRefreshAvailable();pageScroll.setContentDescription(folderRefreshing?"目录列表，正在刷新目录":folderLoadingMore?"目录列表，正在加载下一页":refresh&&more?"目录列表，顶部下拉刷新，底部上拉加载下一页":refresh?"目录列表，顶部下拉刷新":more?"目录列表，到底后继续上拉并松手加载下一页":"目录列表，已全部加载");if(folderPullIndicator!=null&&!folderLoadingMore&&!folderRefreshing&&!more&&folderPullScroll!=null&&folderPullScroll.getTranslationY()!=0f)collapseFolderPull(true);pageScroll.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);}
  /**
   * [DFW-54] 跟手：**一个 float 驱动全部**（Ultra-Pull-To-Refresh 的 PtrIndicator 模型）。
   *
   * `pull` 同时决定：环的弧长、整条指示器的透明度、文字的透明度。
   * 不是"到阈值才 if/else 显示"，所以不会出现"啪一下冒出来"的突兀感。
   * 指示器本身在列表**下面**，靠列表位移露出来 —— 透明度只是让它跟着手指"长出来"。
   */
  void updateFolderPull(float distance){if(folderPullIndicator==null||folderMoreSpinner==null||folderPullLabel==null)return;positionFolderPullIndicator(Gravity.BOTTOM);folderPullIndicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);float pull=Math.min(1f,distance/Math.max(1,dp(FOLDER_PULL_THRESHOLD_DP)));folderMoreSpinner.setVisibility(View.VISIBLE);folderMoreSpinner.setRingSpinning(false);folderMoreSpinner.setRingProgress(pull);folderPullIndicator.setAlpha(pull);boolean armed=distance>=dp(FOLDER_PULL_THRESHOLD_DP);folderPullLabel.setText(armed?"松手加载下一页":"继续上拉加载下一页");folderPullLabel.setTextColor(armed?PRIMARY:MUTED);}
  void updateFolderRefreshPull(float distance){if(folderPullIndicator==null||folderMoreSpinner==null||folderPullLabel==null)return;positionFolderPullIndicator(Gravity.TOP);folderPullIndicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);float pull=Math.min(1f,distance/Math.max(1,dp(FOLDER_PULL_THRESHOLD_DP)));folderMoreSpinner.setVisibility(View.VISIBLE);folderMoreSpinner.setRingSpinning(false);folderMoreSpinner.setRingProgress(pull);folderPullIndicator.setAlpha(pull);boolean armed=distance>=dp(FOLDER_PULL_THRESHOLD_DP);folderPullLabel.setText(armed?"松手刷新目录":"继续下拉刷新目录");folderPullLabel.setTextColor(armed?PRIMARY:MUTED);}
  void positionFolderPullIndicator(int gravity){if(folderPullIndicator==null)return;ViewGroup.LayoutParams raw=folderPullIndicator.getLayoutParams();if(raw instanceof FrameLayout.LayoutParams){FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)raw;int target=gravity|Gravity.CENTER_HORIZONTAL;if(params.gravity!=target){params.gravity=target;folderPullIndicator.setLayoutParams(params);}}}
  void resetFolderPullIndicator(){positionFolderPullIndicator(Gravity.BOTTOM);if(folderMoreSpinner!=null){folderMoreSpinner.setRingSpinning(false);folderMoreSpinner.setRingProgress(0f);folderMoreSpinner.setVisibility(View.VISIBLE);}if(folderPullLabel!=null){folderPullLabel.setText("上拉并松手加载下一页");folderPullLabel.setTextColor(MUTED);}/* [DFW-54] 空闲态必须**完全透明**：指示器虽然在列表下面，但只要列表被位移过就会露出来，
       不置 0 就会出现"什么都没在加载，底下还挂着一行东西"。 */if(folderPullIndicator!=null){folderPullIndicator.setAlpha(0f);folderPullIndicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);}}
  void holdFolderPull(){positionFolderPullIndicator(Gravity.BOTTOM);if(folderPullIndicator!=null){folderPullIndicator.setAlpha(1f);folderPullIndicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}if(folderPullScroll!=null)folderPullScroll.animatePullTo(dp(FOLDER_PULL_SETTLE_DP),motionEnabled());}
  void holdFolderRefresh(){positionFolderPullIndicator(Gravity.TOP);if(folderPullIndicator!=null){folderPullIndicator.setAlpha(1f);folderPullIndicator.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);}if(folderPullScroll!=null)folderPullScroll.animateRefreshTo(dp(FOLDER_PULL_SETTLE_DP),motionEnabled());}
  void startMoreWaiting(){holdFolderPull();if(folderMoreSpinner!=null){folderMoreSpinner.setVisibility(View.VISIBLE);folderMoreSpinner.setRingSpinning(motionEnabled());if(!motionEnabled())folderMoreSpinner.setRingProgress(1f);}updateMoreWaiting();updateFolderPullAvailability();}
  /**
   * [DFW-54] 加载下一页时的提示文案。
   *
   * 旧版这里是「剩余 N 秒后加载下一页」的**每秒倒计时**，两个问题：
   * ① 它暴露的是**服务端限速**（`LanzouCore.NEXT_PAGE_FLOOR_MS=3000`，翻页硬地板 3 秒），
   *    用户既不能加速也不能重试 —— 把无法行动的等待做成倒计时，只是把实现细节漏给用户，
   *    而且 3 秒会"被读成 3 秒"（本来不该被感知的时间被强调了出来）；
   * ② 它和旁边**正在转的加载环**自相矛盾：既然在转，为什么还要等？
   *    而且每秒在「剩余 N 秒…」↔「正在加载下一页」之间来回切，文字宽度变化还会让
   *    居中的环左右横跳。
   * 现在只说一句"正在加载下一页"，不再给无法行动的倒计时。
   */
  void updateMoreWaiting(){if(!pendingFolderPullCurrent()||folderPullLabel==null)return;if(!"正在加载下一页".contentEquals(folderPullLabel.getText()))folderPullLabel.setText("正在加载下一页");folderPullLabel.setTextColor(MUTED);}
  void collapseFolderPull(boolean animated){boolean useMotion=animated&&motionEnabled();if(folderPullScroll!=null)folderPullScroll.animatePullTo(0f,useMotion);resetFolderPullIndicator();}
  void clearFolderPullCallbacks(){if(folderLoadRunnable!=null)ui.removeCallbacks(folderLoadRunnable);if(folderPullCountdownRunnable!=null)ui.removeCallbacks(folderPullCountdownRunnable);folderLoadRunnable=null;folderPullCountdownRunnable=null;}
  void clearFolderPullRequest(){folderPullRequestSession=-1;folderPullRequestPage=0;folderPullRequestUrl="";}
  void cancelFolderPullWork(){clearFolderPullCallbacks();folderLoadingMore=false;folderRefreshing=false;clearFolderPullRequest();if(folderPullScroll!=null){folderPullScroll.animate().cancel();folderPullScroll.pullDistance=0f;folderPullScroll.pulling=false;folderPullScroll.refreshPulling=false;folderPullScroll.setTranslationY(0f);}resetFolderPullIndicator();}
  void startFolderRefreshUi(){holdFolderRefresh();if(folderMoreSpinner!=null){folderMoreSpinner.setVisibility(View.VISIBLE);folderMoreSpinner.setRingSpinning(true);}if(folderPullLabel!=null){folderPullLabel.setText("正在刷新目录");folderPullLabel.setTextColor(PRIMARY);}updateFolderPullAvailability();if(pageScroll!=null)pageScroll.announceForAccessibility("正在刷新目录");}
  void finishFolderRefreshUi(){folderRefreshing=false;collapseFolderPull(true);updateFolderPullAvailability();}
  void refreshCurrentFolder(){if(!folderRefreshAvailable()){collapseFolderPull(true);return;}Models.Source source=activeSource;String rememberedAccess=directResolver.cachedPassword(source.url);if((source.password==null||source.password.isEmpty())&&!rememberedAccess.isEmpty())source.password=rememberedAccess;final int session=navigationSession;final String expectedId=sourceKey(source);folderRefreshing=true;startFolderRefreshUi();if(compositeSource(source)){renderFolder(core.compositeSnapshot(source));hydrateVisibleCompositeMembers(session,expectedId,source);if(pageScroll!=null)pageScroll.announceForAccessibility("目录已刷新");return;}io.execute(()->{try{Models.Folder updated=core.browseSource(source,true);rememberAcceptedSourcePassword(source);runOnUiThread(()->{if(folderRequestCurrent(session,expectedId)){folderRefreshing=false;renderFolder(updated);if(pageScroll!=null)pageScroll.announceForAccessibility("目录已刷新");}});}catch(LanzouCore.DirectPasswordException error){String rejectedPassword=source.password==null?"":source.password;boolean rejected=!rejectedPassword.isEmpty();directResolver.forgetPassword(source.url);forgetRejectedSourcePassword(source.url,rejectedPassword);source.password="";runOnUiThread(()->{if(folderRequestCurrent(session,expectedId)){finishFolderRefreshUi();showLanzouAccessPrompt(source.url,source.title,rejected,accessValue->{source.password=accessValue;refreshCurrentFolder();},null);}});}catch(Exception error){runOnUiThread(()->{if(folderRequestCurrent(session,expectedId)){finishFolderRefreshUi();showFolderParseFailure(source,"刷新目录失败",error);}});}});}
  void finishFolderPull(){clearFolderPullCallbacks();clearFolderPullRequest();collapseFolderPull(true);updateFolderPullAvailability();}
  boolean pendingFolderPullCurrent(){return folderLoadingMore&&folderPullRequestSession==navigationSession&&folderPullRequestPage==folderNextPage&&activeSource!=null&&folderPullRequestUrl.equals(sourceKey(activeSource));}
  void expandMore(){if(folderLoadingMore||folderRenderingRows)return;if(!folderPullAvailable()){collapseFolderPull(true);return;}if(visible<current.size()){int start=visible,end=listAppendEnd(start,current.size());appendFolderRowsAsync(start,end,()->{if(activeSource!=null&&compositeSource(activeSource))hydrateVisibleCompositeMembers(navigationSession,sourceKey(activeSource),activeSource);finishFolderPull();if(pageScroll!=null)pageScroll.announceForAccessibility("已显示更多项目");});return;}if(!folderHasMore||activeSource==null||compositeSource(activeSource)||!currentSourceQuery.isEmpty()){collapseFolderPull(true);return;}folderLoadingMore=true;folderPullRequestSession=navigationSession;folderPullRequestPage=folderNextPage;folderPullRequestUrl=sourceKey(activeSource);startMoreWaiting();long wait=Math.max(0,folderNextReadyAt-System.currentTimeMillis());folderLoadRunnable=()->{folderLoadRunnable=null;if(pendingFolderPullCurrent())loadNextFolderPage();else cancelFolderPullWork();};ui.postDelayed(folderLoadRunnable,wait);}
  void loadNextFolderPage(){if(!pendingFolderPullCurrent())return;Models.Source source=activeSource;String rememberedAccess=directResolver.cachedPassword(source.url);if((source.password==null||source.password.isEmpty())&&!rememberedAccess.isEmpty())source.password=rememberedAccess;int session=folderPullRequestSession,page=folderPullRequestPage;String expectedUrl=folderPullRequestUrl;if(folderPullCountdownRunnable!=null)ui.removeCallbacks(folderPullCountdownRunnable);folderPullCountdownRunnable=null;if(folderPullLabel!=null){folderPullLabel.setText("正在加载下一页");folderPullLabel.setTextColor(PRIMARY);}if(pageScroll!=null)pageScroll.announceForAccessibility("正在加载下一页");io.execute(()->{try{Models.Folder next=core.browsePage(source.url,source.password,true,page);rememberAcceptedSourcePassword(source);runOnUiThread(()->{if(folderRequestCurrent(session,expectedUrl)&&folderLoadingMore&&page==folderPullRequestPage)appendFolderPage(next);});}catch(LanzouCore.DirectPasswordException error){String rejectedPassword=source.password==null?"":source.password;boolean rejected=!rejectedPassword.isEmpty();directResolver.forgetPassword(source.url);forgetRejectedSourcePassword(source.url,rejectedPassword);source.password="";runOnUiThread(()->{if(folderRequestCurrent(session,expectedUrl)&&folderLoadingMore&&page==folderPullRequestPage){folderLoadingMore=false;finishFolderPull();showLanzouAccessPrompt(source.url,source.title,rejected,accessValue->{source.password=accessValue;expandMore();},null);}});}catch(Exception error){runOnUiThread(()->{if(folderRequestCurrent(session,expectedUrl)&&folderLoadingMore&&page==folderPullRequestPage){folderLoadingMore=false;finishFolderPull();showFolderParseFailure(source,"加载下一页失败",error);}});}});}
  void appendFolderPage(Models.Folder next){folderLoadingMore=false;LinkedHashSet<String> seen=new LinkedHashSet<>();for(Models.Item item:folderItems)seen.add(item.url);List<Models.Item> added=new ArrayList<>();for(Models.Item item:next.items)if(seen.add(item.url)){folderItems.add(item);added.add(item);}invalidateFolderSearchIndex();if(activeSource!=null&&activeSource.url.equals(home.url))homeItems=new ArrayList<>(folderItems);folderNextPage=Math.max(folderNextPage+1,next.page+1);folderHasMore=next.hasMore;folderNextReadyAt=Math.max(System.currentTimeMillis(),next.nextPageReadyAt);if(currentSourceQuery.isEmpty()){int start=current.size();current.addAll(added);if(!added.isEmpty()&&folderEmptyState!=null&&folderEmptyState.getParent()==content)content.removeView(folderEmptyState);if(!added.isEmpty())folderEmptyState=null;int end=listAppendEnd(start,current.size());appendFolderRowsAsync(start,end,()->finishFolderPageAppend(added));}else{applyLocalSourceSearch(currentSourceQuery);finishFolderPageAppend(added);}}
  void finishFolderPageAppend(List<Models.Item> added){finishFolderPull();captureActiveFolderState();if(pageScroll!=null)pageScroll.announceForAccessibility(added.isEmpty()?"没有新的项目":"已加载 "+added.size()+" 个项目");if(sessionAutoExpand&&!added.isEmpty()&&folderPullScroll!=null)folderPullScroll.post(this::maybeAutoExpand);}
  /**
   * [DFW-54] 目录页状态行。
   *
   * 左：「已显示 N 个项目」—— `current.size()` 是**已加载总数**不是目录总数，所以不能编"共 M 个"。
   * 右：原来是「上拉加载」四个字，用 `PRIMARY`（紫色）—— 紫色在本项目 = 可操作/强调，
   *     但这是个点不动的纯 TextView，用强调色是误导。
   * 现在：本地还有没显示出来的条目时直接报**余量**（`还有 N 个`，纯本地计算、零成本、不撒谎），
   *       否则报"上拉加载"；到底了报"已全部加载"。颜色一律 `MUTED`。
   */
  void updateItemCount(int shown){if(status!=null)status.setText("已显示 "+shown+" 个项目");if(statusRight!=null){int localLeft=Math.max(0,current.size()-shown);boolean more=folderHasMore&&currentSourceQuery.isEmpty();statusRight.setText(localLeft>0?"还有 "+localLeft+" 个":(more?"上拉加载":"已全部加载"));statusRight.setTextColor(MUTED);}}
  void renderSearchResults(){if(homeHistory==null)return;int session=searchGeneration;homeHistory.removeAllViews();progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);homeHistory.addView(progress,new LinearLayout.LayoutParams(-1,dp(3)));LinearLayout statusRow=new LinearLayout(this);statusRow.setGravity(Gravity.CENTER_VERTICAL);status=text("",12,MUTED);statusRight=text("",11,PRIMARY);statusRight.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);searchPauseButton=toolbarTextButton("暂停");searchPauseButton.setContentDescription("暂停全源搜索");searchPauseButton.setOnClickListener(v->toggleSearchPaused(session));statusRow.addView(status,new LinearLayout.LayoutParams(0,dp(30),1));statusRow.addView(statusRight,new LinearLayout.LayoutParams(-2,dp(30)));statusRow.addView(searchPauseButton,new LinearLayout.LayoutParams(dp(52),dp(30)));homeHistory.addView(statusRow,new LinearLayout.LayoutParams(-1,dp(30)));pageScroll=new ScrollView(this){
      // v1.19.4 修复：搜索结果嵌在可滚动 homeScroll 内，外层普通 ScrollView 在 onInterceptTouchEvent 超过
      // touchSlop 后会无条件抢走竖向手势（实测仪表日志 homeScroll intercept=true），而搜索模式下外层已
      // 钉在最大偏移——表现为手指直滑搜索结果完全不动（"直接断"）、窗口化渲染的 maybeAppendSearchWindow
      // 永不触发（底部留白截断）；SearchDragBar/FolderPullScrollView 之所以能用，靠的就是 DOWN 时
      // requestDisallowInterceptTouchEvent(true)。此处对整条手势做同款豁免，让内层接管结果区滑动。
      @Override public boolean dispatchTouchEvent(android.view.MotionEvent e){
        int action=e.getActionMasked();
        if(action==android.view.MotionEvent.ACTION_DOWN){android.view.ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(true);}
        else if(action==android.view.MotionEvent.ACTION_UP||action==android.view.MotionEvent.ACTION_CANCEL){android.view.ViewParent parent=getParent();if(parent!=null)parent.requestDisallowInterceptTouchEvent(false);}
        return super.dispatchTouchEvent(e);
      }};pageScroll.setVerticalScrollBarEnabled(false);// v1.19.5：关原生滚动条——它以半透明胶囊跟手滑动，与右侧自绘 SearchDragBar 同框成"分裂"观感
content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);pageScroll.addView(content);searchScrollFrame=draggableList(pageScroll,content,"拖动搜索结果");homeHistory.addView(searchScrollFrame,new LinearLayout.LayoutParams(-1,0,1));liveColumns=itemColumns();GridLayout grid=newItemGrid(liveColumns);liveGrid=grid;content.addView(grid,new LinearLayout.LayoutParams(-1,-2));int epoch;synchronized(searchUiLock){epoch=++searchRenderEpoch;searchRenderGrid=grid;pendingSearchSession=session;pendingSearchEpoch=epoch;pendingSearchGrid=grid;pendingSearchAdds.clear();pendingSearchUpdates.clear();pendingSearchRefresh=false;searchUiPosted=false;}List<Models.Item> snapshot;synchronized(globalSearch){snapshot=new ArrayList<>(globalSearch.items);}sortSearchItems(snapshot);selectionChecks.clear();liveUrls.clear();liveRows.clear();current=snapshot;for(Models.Item item:snapshot)liveUrls.add(item.url);searchFolderCount=0;while(searchFolderCount<current.size()&&current.get(searchFolderCount).folder)searchFolderCount++;visible=0;searchWindowTarget=SEARCH_WINDOW;searchWindowDirtyFrom=0;searchWindowPosted=false;searchUiTokens=0;searchUiTokenAt=SystemClock.uptimeMillis();searchDragBar.setVisibility(snapshot.isEmpty()?View.GONE:View.VISIBLE);refreshSearchUi(session);scheduleSearchWindow(session,epoch,grid);}
  boolean searchSurfaceCurrent(int session,int epoch,GridLayout grid){return session==searchGeneration&&epoch==searchRenderEpoch&&grid!=null&&grid==searchRenderGrid&&grid==liveGrid&&pageKind==0&&primaryDestination==0&&homeSearchFocused&&homeHistory!=null&&content!=null&&grid.getParent()==content;}
  boolean searchUiCurrent(int session){int epoch=searchRenderEpoch;GridLayout grid=searchRenderGrid;return searchSurfaceCurrent(session,epoch,grid);}
  void appendSearchResultsUi(int session,int epoch,GridLayout grid,List<Models.Item> batch){if(!searchSurfaceCurrent(session,epoch,grid))return;for(Models.Item item:batch)if(liveUrls.add(item.url)){int index=item.folder?searchFolderCount++:current.size();current.add(index,item);searchWindowDirtyFrom=Math.min(searchWindowDirtyFrom,index);}scheduleSearchWindow(session,epoch,grid);if(searchDragBar!=null){searchDragBar.setVisibility(current.isEmpty()?View.GONE:View.VISIBLE);searchDragBar.invalidate();}refreshSearchUi(session);}
  String searchRowUrl(View row){Object tag=row==null?null:row.getTag();return tag instanceof ItemCard?((ItemCard)tag).item.url:"";}
  void scheduleSearchWindow(int session){int epoch=searchRenderEpoch;GridLayout grid=searchRenderGrid;scheduleSearchWindow(session,epoch,grid);}
  void scheduleSearchWindow(int session,int epoch,GridLayout grid){if(!searchSurfaceCurrent(session,epoch,grid)||searchWindowPosted)return;searchWindowPosted=true;grid.postOnAnimation(()->drainSearchWindow(session,epoch,grid));}
  int searchViewAllowance(){if(sessionSearchViewRate==0)return SEARCH_RENDER_CHUNK;long now=SystemClock.uptimeMillis();double burst=Math.max(1d,sessionSearchViewRate/30d);searchUiTokens=Math.min(burst,searchUiTokens+Math.max(0,now-searchUiTokenAt)*sessionSearchViewRate/1000d);searchUiTokenAt=now;int allowed=(int)searchUiTokens;if(allowed>0)searchUiTokens-=allowed;return allowed;}
  void drainSearchWindow(int session,int epoch,GridLayout grid){if(!searchSurfaceCurrent(session,epoch,grid))return;searchWindowPosted=false;int desired=Math.min(searchWindowTarget,current.size()),allowance=searchViewAllowance(),created=0,start=Math.min(searchWindowDirtyFrom,Math.min(desired,grid.getChildCount()));boolean blocked=false;for(int i=start;i<desired;i++){Models.Item item=current.get(i);View at=i<grid.getChildCount()?grid.getChildAt(i):null;if(item.url.equals(searchRowUrl(at)))continue;View row=liveRows.get(item.url);if(row!=null&&row.getParent()==grid){int from=grid.indexOfChild(row);grid.removeViewAt(from);grid.addView(row,i,itemLayout(i,liveColumns));continue;}if(created>=allowance){searchWindowDirtyFrom=i;blocked=true;break;}row=itemRow(item);liveRows.put(item.url,row);grid.addView(row,i,itemLayout(i,liveColumns));created++;}while(grid.getChildCount()>desired){View removed=grid.getChildAt(grid.getChildCount()-1);liveRows.remove(searchRowUrl(removed));grid.removeView(removed);}if(!blocked)searchWindowDirtyFrom=desired;visible=grid.getChildCount();for(int i=start;i<visible;i++)grid.getChildAt(i).setLayoutParams(itemLayout(i,liveColumns));if(searchDragBar!=null)searchDragBar.invalidate();if(blocked){searchWindowPosted=true;ui.postDelayed(()->drainSearchWindow(session,epoch,grid),16);}}
  void maybeAppendSearchWindow(){int session=searchGeneration,epoch=searchRenderEpoch,desired=Math.min(searchWindowTarget,current.size());GridLayout grid=searchRenderGrid;if(!searchSurfaceCurrent(session,epoch,grid)||searchWindowTarget>=current.size()||searchWindowDirtyFrom<desired||grid.getChildCount()!=desired)return;searchWindowTarget=Math.min(current.size(),searchWindowTarget+SEARCH_WINDOW);searchWindowDirtyFrom=Math.min(searchWindowDirtyFrom,visible);scheduleSearchWindow(session,epoch,grid);}
  void refreshSearchUi(int session){if(!searchUiCurrent(session)||status==null||statusRight==null||progress==null)return;String line,right;int percent;boolean running,nameOnly,indexOnly,paused; synchronized(globalSearch){running=globalSearch.running;nameOnly=globalSearch.nameOnly;indexOnly=globalSearch.indexOnly;paused=globalSearch.paused;percent=globalSearch.percent;if(nameOnly)line="名称匹配 "+globalSearch.items.size()+" 个项目";else if(indexOnly)line="仅索引匹配 · 找到 "+globalSearch.items.size()+" 个项目";else line=(running?"正在搜索 "+globalSearch.active+"/"+Math.max(1,globalSearch.total)+" 个源 · ":"")+"已完成 "+globalSearch.done+" · 找到 "+globalSearch.items.size()+(globalSearch.source.isEmpty()?"":" · "+globalSearch.source);right=paused?"已暂停":globalSearch.right.isEmpty()?(running?"实时追加":globalSearch.failures==0?"已完成":"已完成 · "+globalSearch.failures+" 源异常"):globalSearch.right;}progress.setVisibility(running?View.VISIBLE:View.GONE);progress.setIndeterminate(running&&!paused&&percent==0);if(paused||percent>0){progress.setIndeterminate(false);progress.setProgress(percent);}status.setText(line);statusRight.setText(right);if(searchPauseButton!=null){searchPauseButton.setVisibility(running&&!nameOnly&&!indexOnly?View.VISIBLE:View.GONE);searchPauseButton.setText(paused?"继续":"暂停");searchPauseButton.setContentDescription(paused?"继续全源搜索":"暂停全源搜索");}}
  void toggleSearchPaused(int session){synchronized(globalSearch){if(session!=searchGeneration||!globalSearch.running||globalSearch.nameOnly||globalSearch.indexOnly)return;globalSearch.paused=!globalSearch.paused;globalSearch.notifyAll();}refreshSearchUi(session);}
  void acceptSearchBatch(int session,List<Models.Item> batch){if(batch==null||batch.isEmpty()||session!=searchGeneration)return;List<Models.Item> fresh=new ArrayList<>(),updates=new ArrayList<>();synchronized(globalSearch){if(session!=searchGeneration)return;for(Models.Item item:batch){Models.Item present=globalSearch.byUrl.get(item.url);if(present==null){globalSearch.byUrl.put(item.url,item);insertGlobalSearchItem(globalSearch,item);fresh.add(item);}else{mergeSearchItem(present,item);updates.add(present);}}globalSearch.right="已显示 "+globalSearch.items.size();}queueSearchUi(session,fresh,updates);}
  void acceptSearchItemUpdate(int session,Models.Item item){if(item==null||item.url.isEmpty())return;Models.Item present;synchronized(globalSearch){present=globalSearch.byUrl.get(item.url);if(session!=searchGeneration||present==null)return;mergeSearchItem(present,item);}queueSearchUi(session,null,Collections.singletonList(present));}
  static void mergeSearchItem(Models.Item target,Models.Item value){if(!value.title.isEmpty())target.title=value.title;if(!value.shareUrl.isEmpty())target.shareUrl=value.shareUrl;if(!value.size.isEmpty())target.size=value.size;if(!value.time.isEmpty())target.time=value.time;if(!value.iconUrl.isEmpty())target.iconUrl=value.iconUrl;if(!value.source.isEmpty())target.source=value.source;if(!value.password.isEmpty())target.password=value.password;if(!value.description.isEmpty())target.description=value.description;if(!value.folderId.isEmpty())target.folderId=value.folderId;if(!value.error.isEmpty()||target.error.isEmpty())target.error=value.error;if(!target.sourceEntry){target.folder=value.folder;target.sourceEntry=value.sourceEntry;if(!value.sourceId.isEmpty())target.sourceId=value.sourceId;}else if(value.sourceEntry&&!value.sourceId.isEmpty())target.sourceId=value.sourceId;}
  void queueSearchUi(int session,Collection<Models.Item> adds,Collection<Models.Item> updates){boolean post=false;int epoch;GridLayout grid;synchronized(searchUiLock){if(session!=searchGeneration)return;epoch=searchRenderEpoch;grid=searchRenderGrid;if(grid==null)return;if(session!=pendingSearchSession||epoch!=pendingSearchEpoch||grid!=pendingSearchGrid){pendingSearchSession=session;pendingSearchEpoch=epoch;pendingSearchGrid=grid;pendingSearchAdds.clear();pendingSearchUpdates.clear();pendingSearchRefresh=false;searchUiPosted=false;}if(adds!=null)for(Models.Item item:adds)pendingSearchAdds.addLast(item);if(updates!=null)for(Models.Item item:updates)pendingSearchUpdates.put(item.url,item);pendingSearchRefresh=true;if(!searchUiPosted){searchUiPosted=true;post=true;}}if(post)ui.postDelayed(()->drainSearchUi(session,epoch,grid),16);}
  void queueSearchRefresh(int session){queueSearchUi(session,null,null);}
  void drainSearchUi(int session,int epoch,GridLayout grid){List<Models.Item> adds,updates;boolean refresh;synchronized(searchUiLock){if(session!=searchGeneration||session!=pendingSearchSession||epoch!=pendingSearchEpoch||grid!=pendingSearchGrid||epoch!=searchRenderEpoch||grid!=searchRenderGrid)return;adds=new ArrayList<>(pendingSearchAdds);pendingSearchAdds.clear();updates=new ArrayList<>(pendingSearchUpdates.values());pendingSearchUpdates.clear();refresh=pendingSearchRefresh;pendingSearchRefresh=false;searchUiPosted=false;}if(!searchSurfaceCurrent(session,epoch,grid))return;if(!adds.isEmpty())appendSearchResultsUi(session,epoch,grid,adds);if(!updates.isEmpty()&&searchSurfaceCurrent(session,epoch,grid))for(Models.Item item:updates){View row=liveRows.get(item.url);if(row!=null)bindItemCard(row,item);}if(refresh&&adds.isEmpty())refreshSearchUi(session);}
  static int indexOfItem(List<Models.Item> items,String url){for(int i=0;i<items.size();i++)if(url.equals(items.get(i).url))return i;return -1;}
  static boolean replaceItem(List<Models.Item> items,Models.Item item){int index=indexOfItem(items,item.url);if(index<0)return false;items.set(index,item);return true;}
  static int insertSearchItem(List<Models.Item> items,Models.Item item){int index=items.size();if(item.folder){index=0;while(index<items.size()&&items.get(index).folder)index++;}items.add(index,item);return index;}
  static void insertGlobalSearchItem(SearchState state,Models.Item item){state.items.add(item.folder?state.folderCount++:state.items.size(),item);}
  static int replaceSearchItem(List<Models.Item> items,Models.Item item){int old=indexOfItem(items,item.url);if(old<0)return -1;if(items.get(old).folder==item.folder){items.set(old,item);return old;}items.remove(old);return insertSearchItem(items,item);}
  static void sortSearchItems(List<Models.Item> items){items.sort((left,right)->Boolean.compare(right.folder,left.folder));}
  void rebuildSearchGrid(GridLayout grid,List<Models.Item> items){grid.removeAllViews();int columns=itemColumns();grid.setColumnCount(columns);for(int i=0;i<items.size();i++)grid.addView(itemRow(items.get(i)),itemLayout(i,columns));}
  View itemRow(Models.Item x){
    /* [DFW-24] 软件库卡片是全 App 最高频的点击面，原来**连 ripple 都没有**（纯透明底），
       点下去屏幕上没有任何反馈。补 ripple + 与设置行同规格的按压弹簧。 */
    LinearLayout item=new LinearLayout(this);item.setOrientation(LinearLayout.VERTICAL);item.setPadding(dp(7),dp(6),dp(7),dp(5));item.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));item.setClickable(true);item.setFocusable(true);applePressScale(item);
    TextView sourceBadge=text("",10,PRIMARY);sourceBadge.setMaxLines(1);sourceBadge.setEllipsize(android.text.TextUtils.TruncateAt.END);sourceBadge.setPadding(dp(9),0,0,0);sourceBadge.setVisibility(View.GONE);
    LinearLayout top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);icon.setBackground(solidShape(BG,8));icon.setClipToOutline(true);top.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(38)));
    // DFW-13: title container follows fontScale (dp(48) with 15sp x2 lines clipped at large fonts)
    TextView title=text("",15,TEXT,580);title.setMaxLines(2);title.setPadding(dp(9),0,0,0);top.addView(title,new LinearLayout.LayoutParams(0,dpText(48),1));CheckBox check=new CheckBox(this);check.setClickable(false);check.setFocusable(false);top.addView(check,new LinearLayout.LayoutParams(dp(32),dp(44)));
    item.addView(sourceBadge,new LinearLayout.LayoutParams(-1,-2));
    item.addView(top,new LinearLayout.LayoutParams(-1,0,1));TextView meta=text("",11,MUTED);meta.setMaxLines(1);meta.setEllipsize(android.text.TextUtils.TruncateAt.END);item.addView(meta,new LinearLayout.LayoutParams(-1,dpText(24)));ItemCard card=new ItemCard(x,icon,title,meta,sourceBadge,check);item.setTag(card);bindItemCard(item,x);
    item.setOnClickListener(v->{Models.Item value=((ItemCard)v.getTag()).item;if(value.url.startsWith("placeholder:")){showNotice("目录仍在解析，请稍候",false);return;}if(selectionMode){toggleSelection(value);return;}if(value.sourceEntry)openSourceSearchResult(value);else if(value.folder)openNestedFolder(value);else confirmDownload(value);});item.setOnLongClickListener(v->{Models.Item value=((ItemCard)v.getTag()).item;if(value.url.startsWith("placeholder:"))return true;enterSelection();toggleSelection(value);return true;});return item;
  }
  void bindItemCard(View row,Models.Item value){if(!(row.getTag() instanceof ItemCard))return;ItemCard card=(ItemCard)row.getTag();if(card.item!=null&&!card.item.url.equals(value.url))selectionChecks.remove(card.item.url);card.item=value;if(!value.title.contentEquals(card.title.getText()))card.title.setText(value.title);String libName=libraryNameFor(value);boolean hasLib=!libName.isEmpty();card.sourceBadge.setText(libName);// v1.19.6：库名已是「分类·内容」格式，去掉冗余"软件库 ·"前缀card.sourceBadge.setVisibility(hasLib?View.VISIBLE:View.GONE);
    // v1.19.6 类型标清楚：文件夹固定目录图标、不再加载远程图标（远程头图曾让文件夹看起来像软件）；meta 首段标类型
    String detail=!value.error.isEmpty()?"错误："+value.error:value.folder?itemFolderMeta(value):itemFileMeta(value);if(!detail.contentEquals(card.meta.getText()))card.meta.setText(detail);card.meta.setTextColor(value.error.isEmpty()?MUTED:ERROR_TOKEN);card.meta.setContentDescription(value.error.isEmpty()?detail:"当前成员错误："+value.error);card.check.setContentDescription("选择 "+value.title);card.check.setChecked(selectedUrls.contains(value.url));card.check.setVisibility(selectionMode?View.VISIBLE:View.GONE);selectionChecks.put(value.url,card.check);if(card.iconUrl==null||card.folder!=value.folder||!card.iconUrl.equals(value.iconUrl)){card.iconUrl=value.iconUrl;card.folder=value.folder;card.icon.setTag(value.folder?"":value.iconUrl);card.icon.setImageResource(value.folder?R.drawable.ic_folder:R.drawable.ic_file);if(!value.folder&&!value.iconUrl.isEmpty())loadImage(value.iconUrl,card.icon);}}
  static String itemFolderMeta(Models.Item value){String t="文件夹";if(!value.error.isEmpty())return t;return value.time.isEmpty()?t:t+" · "+value.time;}
  static String itemFileMeta(Models.Item value){String kind=fileKindLabel(value.title),rest=(value.size+"  "+value.time).trim();return rest.isEmpty()?kind:kind+" · "+rest;}
  static String fileKindLabel(String title){String t=title==null?"":title.toLowerCase(Locale.ROOT);if(t.endsWith(".apk"))return"安装包";if(t.endsWith(".apks")||t.endsWith(".xapk"))return"安装包合集";if(t.endsWith(".zip")||t.endsWith(".rar")||t.endsWith(".7z"))return"压缩包";return"文件";}
  static String fileArchLabel(String title){String t=title==null?"":title.toLowerCase(Locale.ROOT);if(t.contains("arm64")||t.contains("v8a"))return"（arm64 架构）";if(t.contains("armeabi-v7a")||t.contains("armv7")||t.contains("v7a"))return"（armv7 架构）";if(t.contains("x86_64"))return"（x86_64 架构）";return"";}
  void enterSelection(){
    if(selectionMode)return;selectionMode=true;syncBackCallbackEnabled();selectionSummary=text("已选 0",11,TEXT);selectionAllButton=toolbarTextButton("全选");selectionAllButton.setOnClickListener(v->toggleSelectAllItems());ImageButton copy=iconButton(R.drawable.ic_copy,"复制"),download=iconButton(R.drawable.ic_download,"全部下载"),share=iconButton(R.drawable.ic_share,"分享原链接"),close=iconButton(R.drawable.ic_close,"退出多选");selectionCategorize=iconButton(R.drawable.ic_add,"将所选项目加入源分类");selectionRenameFolder=iconButton(R.drawable.ic_edit,"重命名自建文件夹");selectionRenameFolder.setVisibility(View.GONE);copy.setOnClickListener(v->showSelectionCopyOptions());download.setOnClickListener(v->confirmBulkDownload());share.setOnClickListener(v->shareSelection());selectionCategorize.setOnClickListener(v->chooseItemSelectionCategory());selectionRenameFolder.setOnClickListener(v->renameSelectedCompositeFolder());close.setOnClickListener(v->exitSelection());selectionBar=makeSelectionBar(selectionSummary,selectionAllButton,68,48,new String[]{"复制","下载","分享","加入分类","重命名","退出"},copy,download,share,selectionCategorize,selectionRenameFolder,close);showChecks(selectionChecks,true);updateSelectionSummary();
  }
  void exitSelection(){selectionMode=false;syncBackCallbackEnabled();resetSelection(selectedUrls,selectionChecks,selectionBar);selectionBar=null;selectionSummary=null;selectionAllButton=null;selectionCopyDescription=null;selectionCategorize=null;selectionRenameFolder=null;}
  void toggleSelection(Models.Item item){if(!selectionMode)enterSelection();if(toggleChosen(selectedUrls,item.url,selectionChecks))exitSelection();else updateSelectionSummary();}
  void toggleSelectAllItems(){if(current.isEmpty()){showNotice("当前没有可选择的项目",false);return;}List<String> urls=new ArrayList<>();for(Models.Item item:current)urls.add(item.url);if(allChosen(urls,selectedUrls)){exitSelection();return;}selectedUrls.clear();selectedUrls.addAll(urls);syncChecks(selectedUrls,selectionChecks);updateSelectionSummary();}
  List<Models.Item> selectedItems(){List<Models.Item> items=new ArrayList<>();for(Models.Item item:current)if(selectedUrls.contains(item.url))items.add(item);return items;}
  long parseSize(String value){java.util.regex.Matcher match=SIZE_VALUE.matcher(value==null?"":value);if(!match.find())return 0;double number=Double.parseDouble(match.group(1));String unit=match.group(2).toUpperCase(Locale.ROOT);int power=unit.equals("K")?1:unit.equals("M")?2:unit.equals("G")?3:unit.equals("T")?4:0;return(long)(number*Math.pow(1024,power));}
  String formatSize(long bytes){if(bytes<=0)return"大小未知";String[] units={"B","KB","MB","GB","TB"};double value=bytes;int unit=0;while(value>=1024&&unit<units.length-1){value/=1024;unit++;}return String.format(Locale.ROOT,value>=100?"%.0f %s":"%.1f %s",value,units[unit]);}
  String formatMegabytes(long bytes){return String.format(Locale.ROOT,"%.1f MB",bytes/1048576d);}
  void updateSelectionSummary(){List<Models.Item> chosen=selectedItems();long bytes=0;for(Models.Item item:chosen)if(!item.folder)bytes+=parseSize(item.size);if(selectionSummary!=null)selectionSummary.setText("已选 "+chosen.size()+" · "+formatMegabytes(bytes));if(selectionAllButton!=null)selectionAllButton.setText(!current.isEmpty()&&chosen.size()==current.size()?"取消全选":"全选");if(selectionRenameFolder!=null){boolean show=chosen.size()==1&&localCompositeFolder(chosen.get(0));if(selectionRenameFolder.getVisibility()!=(show?View.VISIBLE:View.GONE)){selectionRenameFolder.setVisibility(show?View.VISIBLE:View.GONE);if(selectionBar!=null&&selectionBar.getTag() instanceof SelectionBarLayout){((SelectionBarLayout)selectionBar.getTag()).available=-1;layoutSelectionBar(selectionBar);}}}}
  boolean localCompositeFolder(Models.Item item){return item!=null&&item.folder&&compositeSource(activeSource)&&!item.folderId.isEmpty()&&!item.folderId.startsWith("remote:");}
  void chooseItemSelectionCategory(){List<Models.Item> chosen=selectedItems();if(chosen.isEmpty())return;showSourceDestinationPicker(Collections.emptyList(),chosen);}
  void addItemSelectionToCategory(List<Models.Item> chosen,String categoryName){List<Models.Item> files=new ArrayList<>(),folders=new ArrayList<>();boolean addCompositeRoot=false;for(Models.Item item:chosen)if(item.folder){if(localCompositeFolder(item))addCompositeRoot=true;else folders.add(item);}else files.add(item);StringBuilder folderRules=new StringBuilder();for(Models.Item item:folders){String url=item.shareUrl.isEmpty()?item.url:item.shareUrl;if(url.isEmpty())continue;if(folderRules.length()>0)folderRules.append('\n');folderRules.append(url);if(!item.password.isEmpty())folderRules.append(' ').append(item.password);}List<Models.SourceMember> software=new ArrayList<>();for(Models.Item item:files){String url=item.shareUrl.isEmpty()?item.url:item.shareUrl;if(url.isEmpty())continue;Models.SourceMember member=new Models.SourceMember();member.url=url;member.password=item.password;member.title=item.title;member.iconUrl=item.iconUrl;member.size=item.size;member.time=item.time;member.description=item.description;software.add(member);}boolean includeRoot=addCompositeRoot;showNotice("正在加入分类",false);io.execute(()->{try{LinkedHashSet<String> ids=new LinkedHashSet<>();int created=0;if(includeRoot&&activeSource!=null&&!activeSource.id.isEmpty())ids.add(activeSource.id);if(folderRules.length()>0){LanzouCore.AddBatchResult result=core.addUserSourcesBatch(folderRules.toString());for(LanzouCore.AddResult row:result.lines)if(row.source!=null)ids.add(sourceKey(row.source));created+=result.added;}if(!software.isEmpty()){String first=software.get(0).title.isEmpty()?"软件":software.get(0).title.replaceFirst("(?i)\\.(?:apk|exe|zip|rar|7z)$","");String title=software.size()==1?first+" 合集":first+" 等 "+software.size()+" 项";String icon=software.get(0).iconUrl;Models.Source composite=core.addCompositeSource(title,activeFolderProfile==null?"":activeFolderProfile.publisher,icon,"由文件列表创建",software);ids.add(sourceKey(composite));created++;}int addedCount=created;runOnUiThread(()->{LinkedHashSet<String> category=sourceCategories.get(categoryName);if(category==null){showNotice("分类已不存在",true);return;}int before=category.size();category.addAll(ids);persistSourceCategories();++sourceDataRevision;exitSelection();showNotice("已加入“"+categoryName+"” · "+(category.size()-before)+" 个源"+(addedCount>0?"，新建 "+addedCount+" 个":""),false);});}catch(Exception error){showNotice("加入分类失败："+friendlyError(error),true);}});}
  void renameSelectedCompositeFolder(){List<Models.Item> chosen=selectedItems();if(chosen.size()!=1||!localCompositeFolder(chosen.get(0)))return;Models.Item folder=chosen.get(0);EditText name=sourceInput("文件夹名称",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);name.setText(folder.title);name.setSelection(name.length());LinearLayout panel=new LinearLayout(this);panel.setPadding(dp(22),dp(8),dp(22),0);panel.addView(name,new LinearLayout.LayoutParams(-1,dp(56)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("重命名文件夹").setView(panel).setNegativeButton("取消",null).setPositiveButton("保存",null).create();prompt.setOnShowListener(ignored->prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("请输入文件夹名称");return;}io.execute(()->{try{Models.Source updated=core.renameCompositeFolder(sourceKey(activeSource),folder.folderId,value);runOnUiThread(()->{prompt.dismiss();exitSelection();refreshCompositeAfterEdit(activeSource,updated,"已重命名为“"+value+"”");});}catch(Exception error){runOnUiThread(()->name.setError(friendlyError(error)));}});}));showRounded(prompt);}
      String selectionLinks(boolean withNames){LinkedHashSet<String> blocks=new LinkedHashSet<>();boolean failed=false;for(Models.Item item:selectedItems()){if(localCompositeFolder(item)){try{for(Models.Item child:core.portableCompositeFolderItems(activeSource,item.folderId))appendPortableSelectionLink(blocks,child,withNames);}catch(Exception error){failed=true;}}else appendPortableSelectionLink(blocks,item,withNames);}if(failed)showNotice("部分内部文件夹已变化，已跳过无法读取的项目",true);return String.join("\n\n",blocks);}
  static void appendPortableSelectionLink(Set<String> blocks,Models.Item item,boolean withNames){if(blocks==null||item==null)return;String url=item.shareUrl.isEmpty()?item.url:item.shareUrl;if(url==null||url.trim().isEmpty())return;StringBuilder value=new StringBuilder();if(withNames&&!item.title.isEmpty())value.append(item.title).append('\n');value.append(url.trim());if(!item.password.isEmpty())value.append("\n密码：").append(item.password);blocks.add(value.toString());}
  void showSelectionCopyOptions(){if(selectedItems().isEmpty())return;AlertDialog options=new AlertDialog.Builder(this).setTitle("复制所选项目").setItems(new String[]{"复制链接","复制简介"},(dialog,index)->{if(index==0)copySelection();else copySelectedDescriptions();}).create();showRounded(options);}
  void copySelectedDescriptions(){List<Models.Item> chosen=selectedItems();if(chosen.isEmpty())return;showNotice("正在读取 "+chosen.size()+" 个项目简介",false);io.execute(()->{StringBuilder out=new StringBuilder();for(Models.Item item:chosen){String value=item.description==null?"":item.description.trim();if(value.isEmpty())try{value=item.folder?core.browse(item.url,item.password,false).description.trim():core.fileDescription(item.shareUrl.isEmpty()?item.url:item.shareUrl).trim();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}if(!value.isEmpty()){if(out.length()>0)out.append("\n\n");out.append(item.title).append("\n").append(value);}}runOnUiThread(()->{if(out.length()==0){showNotice("所选项目没有可复制的简介",false);return;}((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("项目简介",out.toString()));showNotice("已复制 "+chosen.size()+" 个项目的简介",false);});});}
    void copySelection(){try{String links=selectionLinks(false);if(links.isEmpty())return;((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("蓝奏云链接",links));showNotice("已复制可解析链接",false);}catch(Exception error){showNotice("复制失败："+friendlyError(error),true);}}
    void shareSelection(){try{String links=selectionLinks(false);if(!links.isEmpty())shareText(links,"分享所选蓝奏云链接");}catch(Exception error){showNotice("分享失败："+friendlyError(error),true);}}
  void confirmBulkDownload(){List<Models.Item> items=selectedItems();if(items.isEmpty())return;BulkPreparation preparation=startBulkPreparation(items);long bytes=0;for(Models.Item item:items)if(!item.folder)bytes+=parseSize(item.size);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(10),dp(22),dp(6));panel.addView(bulkDownloadPreview(items),new LinearLayout.LayoutParams(-1,dp(112)));TextView summary=text("已知大小 "+formatMegabytes(bytes),13,MUTED);summary.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);panel.addView(summary,new LinearLayout.LayoutParams(-1,dp(38)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("下载所选 "+items.size()+" 个项目？").setView(panel).setNegativeButton("取消",null).setPositiveButton("全部下载",(dialog,which)->prepareBulkDownloads(preparation)).create();showRounded(prompt);}
  View bulkDownloadPreview(List<Models.Item> items){LinearLayout holder=new LinearLayout(this);holder.setGravity(Gravity.CENTER);GridLayout preview=new GridLayout(this);preview.setColumnCount(2);preview.setRowCount(2);int slots=Math.min(4,items.size());for(int i=0;i<slots;i++){FrameLayout tile=new FrameLayout(this);GridLayout.LayoutParams cell=new GridLayout.LayoutParams();cell.width=dp(50);cell.height=dp(50);cell.setMargins(dp(4),dp(3),dp(4),dp(3));if(items.size()>4&&i==3){TextView more=text("…",23,PRIMARY);more.setGravity(Gravity.CENTER);more.setBackground(solidShape(SURFACE,11));more.setContentDescription("还有 "+(items.size()-3)+" 个项目");tile.addView(more,new FrameLayout.LayoutParams(-1,-1));TextView badge=text("+"+(items.size()-3),9,BG);badge.setGravity(Gravity.CENTER);badge.setBackground(solidShape(PRIMARY,9));FrameLayout.LayoutParams badgeParams=new FrameLayout.LayoutParams(dp(28),dp(18),Gravity.END|Gravity.TOP);tile.addView(badge,badgeParams);}else{Models.Item item=items.get(i);ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);icon.setBackground(solidShape(BG,10));icon.setClipToOutline(true);icon.setImageResource(item.folder?R.drawable.ic_folder:R.drawable.ic_file);icon.setContentDescription(item.title);tile.addView(icon,new FrameLayout.LayoutParams(-1,-1));loadImage(item.iconUrl,icon);}preview.addView(tile,cell);}holder.addView(preview,new LinearLayout.LayoutParams(-2,-1));return holder;}
  View batchDownloadPreview(List<DownloadEntry> entries,int side){LinearLayout holder=new LinearLayout(this);holder.setGravity(Gravity.CENTER);GridLayout preview=new GridLayout(this);preview.setColumnCount(2);preview.setRowCount(2);int slots=Math.min(4,entries.size());for(int i=0;i<slots;i++){FrameLayout tile=new FrameLayout(this);GridLayout.LayoutParams cell=new GridLayout.LayoutParams();cell.width=dp(side);cell.height=dp(side);cell.setMargins(dp(1),dp(1),dp(1),dp(1));if(entries.size()>4&&i==3){TextView more=text("+"+(entries.size()-3),Math.max(9,side/3),BG);more.setGravity(Gravity.CENTER);more.setBackground(solidShape(PRIMARY,Math.max(6,side/4)));more.setContentDescription("还有 "+(entries.size()-3)+" 个下载项目");tile.addView(more,new FrameLayout.LayoutParams(-1,-1));}else{DownloadEntry entry=entries.get(i);ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);icon.setBackground(solidShape(BG,Math.max(5,side/5)));icon.setClipToOutline(true);icon.setImageResource(R.drawable.ic_file);icon.setContentDescription(entry.name);tile.addView(icon,new FrameLayout.LayoutParams(-1,-1));loadImage(entry.iconUrl,icon);}preview.addView(tile,cell);}holder.addView(preview,new LinearLayout.LayoutParams(-2,-1));return holder;}
    BulkPreparation startBulkPreparation(List<Models.Item> chosen){BulkPreparation preparation=new BulkPreparation();Models.Source context=activeSource==null?null:runtimeLanzouSource(activeSource);preparation.future=io.submit(()->core.collectFiles(context,chosen,(folders,count,name)->{preparation.progress="已展开 "+folders+" 个文件夹 · "+count+" 个文件\n"+name;runOnUiThread(()->{TextView label=preparation.label;if(label!=null&&preparation.panel!=null&&preparation.panel.getParent()==toastLayer)label.setText(preparation.progress);});}));return preparation;}
  void prepareBulkDownloads(BulkPreparation preparation){LinearLayout panel=toastPanel();TextView label=text(preparation.progress,12,TEXT);label.setMaxLines(2);panel.addView(label,new LinearLayout.LayoutParams(-1,dp(38)));ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setIndeterminate(true);panel.addView(bar,new LinearLayout.LayoutParams(-1,dp(4)));addToastPanel(panel,58);preparation.panel=panel;preparation.label=label;io.execute(()->{try{List<Models.Item> files=preparation.future.get();runOnUiThread(()->{if(panel.getParent()==toastLayer)dismissPanel(panel,null);if(files.isEmpty()){showNotice("所选项目中没有可下载文件",false);return;}exitSelection();startBulkDownloads(files);});}catch(Exception error){Throwable cause=error instanceof ExecutionException&&error.getCause()!=null?error.getCause():error;runOnUiThread(()->{if(panel.getParent()==toastLayer)dismissPanel(panel,null);showNotice("整理失败 · "+friendlyError(cause),true);});}});}
  
  Set<String> searchCategoryIds(String category){if(category.equals("全部"))return null;if(sourceCategories.isEmpty())loadSourceCategories();Set<String> ids=sourceCategories.get(category);return ids==null?Collections.emptySet():new LinkedHashSet<>(ids);}
  boolean apiSearchCapableSource(Models.Source source){if(source==null||singleFileSource(source))return false;if(!compositeSource(source))return source.searchable;for(Models.SourceMember member:source.members)if(member.kind==Models.MEMBER_REMOTE_FOLDER&&member.searchable&&!member.url.isEmpty())return true;return false;}
  Set<String> searchAllowedSourceIds(String category,int mode){Set<String> categoryIds=searchCategoryIds(category);if(mode!=Models.SearchOptions.MODE_API)return categoryIds;LinkedHashSet<String> allowed=new LinkedHashSet<>();try{for(Models.Source source:core.sources())if(apiSearchCapableSource(source)&&(categoryIds==null||categoryIds.contains(LanzouCore.sourceId(source))))allowed.add(LanzouCore.sourceId(source));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}if(apiSearchCapableSource(home)&&!home.url.isEmpty()&&(categoryIds==null||categoryIds.contains(sourceKey(home))))allowed.add(sourceKey(home));return allowed;}
  int searchableSourceTotal(Set<String> ids){try{return core.searchableSourceCount(home,ids);}catch(Exception ignored){return 0;}}
  int searchableSourceTotal(String category,int mode){return searchableSourceTotal(searchAllowedSourceIds(category,mode));}
  int searchableSourceTotal(String category){return searchableSourceTotal(category,sessionSearchMode);}
  int searchableSourceTotal(){return searchableSourceTotal(sessionSearchCategory);}
  /**
   * [DFW-39] 慢源轮换的时间片（纯函数，方便 JVM 用例直接断言）。
   *
   * 旧式是 `requested<=0 || requested>=total ? 0 : batchSeconds*1000`。
   * 其中 `requested<=0` 这一支是**自动模式（默认）** —— 于是默认配置下时间片恒为 0，
   * 而轮换调度器首行就是 `if(switchDelay<=0)return;`，等于**轮换从来没开过**：
   * 慢源占着名额跑到底，其余源无限排队，用户看到的就是"停在 3/50"。
   * 现在自动模式也给时间片；只有"每源一条线程"（requested>=total）时才不需要轮换。
   */
  static long sourceSwitchDelayMillis(int requested,int total,int batchSeconds){
    if(total<=1)return 0L;
    if(requested>0&&requested>=total)return 0L;
    return Math.max(1,batchSeconds)*1000L;
  }
  /**
   * [DFW-96] **全局搜索**时每个源最多翻几页。
   *
   * 用户 2026-10-02 反馈「搜索全部搜完要等太久」。实测根因：
   * 全局搜索原来直接复用 `sessionSearchMaxPages`，而它的默认值 0 在
   * `LanzouCore.pageLimit()`（`LanzouCore.java:393`）里被当成 **1000 页** ——
   * 也就是"每个源翻到底"。再叠上"同域名每页至少间隔 1100ms"（`ORIGIN_PAGE_SLOT_MS`）
   * 和"文件夹递归"，跑满 180 秒的搜索预算（`DEFAULT_SEARCH_BUDGET_MS`）是常态，不是意外。
   *
   * 搜索是"找东西"，不是"备份整个目录"：前 3 页（约 150 条/源）足够命中绝大多数目标。
   * 想深挖某个源时，点进那个源单独翻页**不受此限**（那条路走 `sessionSearchMaxPages`，
   * 界面上叫「单源翻页」）。
   *
   * 注意 DFW-96 卡里的红线是"搜索必须仍然**覆盖全部源**"——
   * 源一个不少，只是每个源不再翻到底。这与红线不冲突。
   */
  static final int SEARCH_MAX_PAGES_GLOBAL=3;

  Models.SearchOptions searchOptions(int total){return searchOptions(total,SEARCH_MAX_PAGES_GLOBAL);}

  Models.SearchOptions searchOptions(int total,int maxPages){int requested=sessionSearchConcurrency<=0?0:Math.min(Math.max(1,total),sessionSearchConcurrency);long switchDelay=sourceSwitchDelayMillis(requested,total,sessionSearchBatchSeconds);return new Models.SearchOptions(requested,switchDelay,true,maxPages).withRecursiveFolders(sessionSearchRecursiveFolders).withModeMask(sessionSearchModeMask).withFuzzyMatching(fuzzyDirectoryEnabled());}
  String concurrencyText(int value,int total){return value<=0?"搜索线程数：自动（设备自适应 · 共 "+total+" 源）":"搜索线程数："+Math.min(value,Math.max(0,total))+" / "+total;}
  String concurrencyDescription(int value,int total){return concurrencyText(value,total)+"；自动模式按 CPU、内存与运行时网络压力动态开窗，不一次性激活全部源";}
  static int validSearchViewRate(int value){switch(value){case 0:case 50:case 100:case 200:case 500:case 1000:case 2000:return value;default:return 100;}}
  static int normalizeSourceListDisplayCount(int count){return count<=0?0:Math.max(SOURCE_LIST_MIN_DISPLAY,count);}
  String searchViewRateText(int value){return "搜索视图创建上限："+(value==0?"无限":value+" 项/秒");}
  void loadSearchSettings(){android.content.SharedPreferences p=getSharedPreferences("search_settings-v1",MODE_PRIVATE);sessionSearchConcurrency=Math.max(0,p.getInt("threads",0));sessionSearchBatchSeconds=Math.max(0,p.getInt("yield_seconds",15));sessionSearchMaxPages=Math.max(0,p.getInt("max_pages",0));int storedViewRate=p.getInt("view_rate",100);if(!p.getBoolean("view_rate_fast_default_v146",false)&&storedViewRate==100){storedViewRate=0;p.edit().putInt("view_rate",0).putBoolean("view_rate_fast_default_v146",true).apply();}sessionSearchViewRate=validSearchViewRate(storedViewRate);sessionSearchRecursiveFolders=p.getBoolean("recursive",false);int defaultFileMask=Models.SearchOptions.MASK_API|Models.SearchOptions.MASK_DIRECTORY,defaultSearchMask=Models.SearchOptions.MASK_DIRECTORY;int legacyMode=Math.max(Models.SearchOptions.MODE_MIXED,Math.min(Models.SearchOptions.MODE_INDEX,p.getInt("mode",Models.SearchOptions.MODE_DIRECTORY)));boolean legacyIndex=p.contains("index_enabled")&&p.getBoolean("index_enabled",false);int migratedSearchMask=Models.SearchOptions.maskForMode(legacyMode);if(legacyIndex)migratedSearchMask|=Models.SearchOptions.MASK_INDEX;sessionFileListModeMask=Models.SearchOptions.normalizeModeMask(p.getInt("file_list_mode_mask",defaultFileMask));sessionSearchModeMask=Models.SearchOptions.normalizeModeMask(p.contains("mode_mask")?p.getInt("mode_mask",defaultSearchMask):(p.contains("mode")||p.contains("index_enabled")?migratedSearchMask:defaultSearchMask));sessionSearchMode=Models.SearchOptions.modeForMask(sessionSearchModeMask);sessionIndexEnabled=anyIndexSelected();sessionSearchFuzzyMatching=p.getBoolean("fuzzy",false);sessionSearchFuzzyMask=sessionSearchFuzzyMatching?FUZZY_ALL:0;sessionBackgroundIndex=p.getBoolean("background_index",true);sessionIndexThreads=Math.max(0,p.getInt("index_threads",0));sessionIndexRetentionHours=Math.max(0,p.getInt("index_retention_hours",24));sessionAutoExpand=p.contains("auto_expand")?p.getBoolean("auto_expand",true):true;sessionAutoExpandInitialPages=Math.max(0,p.getInt("auto_expand_initial",1));sessionAutoExpandNextPages=Math.max(0,p.getInt("auto_expand_next",1));sessionListInitialCount=50;sessionListAppendCount=50;sessionSourceListDisplayCount=normalizeSourceListDisplayCount(p.getInt("source_list_display_count",SOURCE_LIST_MIN_DISPLAY));directLanzouListOpen=p.getBoolean("lanzou_direct",false);showSourceLinks=p.getBoolean("show_source_links",false);sessionBatchDownloadSingleItem=p.getBoolean("batch_download_single_item",true);sessionOpenWebExternal=p.getBoolean("web_open_external",false);sessionUaPreset=LanzouCore.normalizeUserAgentPreset(p.getInt("ua_preset",LanzouCore.UA_PRESET_MOBILE_CHROME));sessionUaScopeMask=LanzouCore.normalizeUserAgentScopeMask(p.getInt("ua_scope_mask",LanzouCore.UA_SCOPE_ALL));sessionUaFileListPreset=LanzouCore.normalizeUserAgentPreset(p.getInt("ua_file_list_preset",sessionUaPreset));sessionUaDirectorySearchPreset=LanzouCore.normalizeUserAgentPreset(p.getInt("ua_directory_search_preset",sessionUaPreset));sessionUaApiSearchPreset=LanzouCore.normalizeUserAgentPreset(p.getInt("ua_api_search_preset",sessionUaPreset));sessionUaDirectPreset=LanzouCore.normalizeUserAgentPreset(p.getInt("ua_direct_preset",sessionUaPreset));sessionCustomUserAgent=LanzouCore.normalizeCustomUserAgent(p.getString("ua_custom",""));sessionLanzouBaseOrigin=loadLanzouBaseOrigin(p);sessionLanzouTimeoutFailover=p.getBoolean("lanzou_timeout_failover",true);perfTier=Math.max(0,Math.min(2,p.getInt("perf_tier",1)));perfLowMotion=p.getBoolean("perf_low_motion",false);applyUserAgentSettings();applyLanzouRoutingSettings();}
  String loadLanzouBaseOrigin(android.content.SharedPreferences p){// v1.0.1 起：旧版本默认 nekobyran 域名自动迁移到 oreojiang，避免用户旧配置导致目录解析失败。
    // 键名 2026-09-30 由 heiyao_origin_v101 改为 dfwx_origin_v101（品牌清理 DFW-57）；
    // **同时读取旧键**，避免任何已写过旧键的设备被重复迁移、进而覆盖用户自定义的域名。
    if(!p.getBoolean("dfwx_origin_v101",false)&&!p.getBoolean("heiyao_origin_v101",false)){p.edit().putBoolean("dfwx_origin_v101",true).putString("lanzou_base_origin","https://oreojiang.lanzout.com").apply();return "https://oreojiang.lanzout.com";}
    String raw=p.getString("lanzou_base_origin","https://oreojiang.lanzout.com"),normalized=normalizeLanzouBaseOrigin(raw);return normalized;}
  void persistSearchSettings(){applyUserAgentSettings();applyLanzouRoutingSettings();sessionFileListModeMask=Models.SearchOptions.normalizeModeMask(sessionFileListModeMask);sessionSearchModeMask=Models.SearchOptions.normalizeModeMask(sessionSearchModeMask);sessionSearchMode=Models.SearchOptions.modeForMask(sessionSearchModeMask);sessionIndexEnabled=anyIndexSelected();sessionSearchFuzzyMask=sessionSearchFuzzyMatching?FUZZY_ALL:0;getSharedPreferences("search_settings-v1",MODE_PRIVATE).edit().putInt("threads",sessionSearchConcurrency).putInt("yield_seconds",sessionSearchBatchSeconds).putInt("max_pages",sessionSearchMaxPages).putInt("view_rate",sessionSearchViewRate).putBoolean("view_rate_fast_default_v146",true).putBoolean("recursive",sessionSearchRecursiveFolders).putInt("mode",sessionSearchMode).putInt("mode_mask",sessionSearchModeMask).putInt("file_list_mode_mask",sessionFileListModeMask).putBoolean("background_index",sessionBackgroundIndex).putBoolean("index_enabled",sessionIndexEnabled).putInt("index_threads",sessionIndexThreads).putInt("index_retention_hours",sessionIndexRetentionHours).putBoolean("fuzzy",sessionSearchFuzzyMatching).putInt("fuzzy_mask",sessionSearchFuzzyMask).putBoolean("auto_expand",sessionAutoExpand).putInt("auto_expand_initial",sessionAutoExpandInitialPages).putInt("auto_expand_next",sessionAutoExpandNextPages).putInt("source_list_display_count",sessionSourceListDisplayCount).putBoolean("lanzou_direct",directLanzouListOpen).putBoolean("show_source_links",showSourceLinks).putBoolean("batch_download_single_item",sessionBatchDownloadSingleItem).putBoolean("web_open_external",sessionOpenWebExternal).putBoolean("lanzoux_default_migrated",true).putBoolean("lanzoux_default_migrated_v143",true).putBoolean("nekobyran_lanzouw_default_migrated_v145",true).putBoolean("nekobyran_lanzoux_default_migrated_v150",true).putBoolean("lanzou_timeout_failover",sessionLanzouTimeoutFailover).putString("lanzou_base_origin",sessionLanzouBaseOrigin).putInt("ua_preset",sessionUaDirectPreset).putInt("ua_scope_mask",LanzouCore.normalizeUserAgentScopeMask(sessionUaScopeMask)).putInt("ua_file_list_preset",sessionUaFileListPreset).putInt("ua_directory_search_preset",sessionUaDirectorySearchPreset).putInt("ua_api_search_preset",sessionUaApiSearchPreset).putInt("ua_direct_preset",sessionUaDirectPreset).putString("ua_custom",sessionCustomUserAgent).putInt("perf_tier",perfTier).putBoolean("perf_low_motion",perfLowMotion).apply();}
  String batchTimeoutText(int seconds){return "慢源让位时间："+(seconds==0?"无限":seconds+" 秒");}
  String batchTimeoutDescription(int seconds){return seconds==0?"慢源让位时间无限，不因公平调度主动让位":"慢源连续活跃 "+seconds+" 秒后保留状态排到队尾，下个源立即补位，不取消且不丢结果";}
  int batchTimeoutProgress(int seconds){return seconds==0?60:Math.max(0,Math.min(59,seconds-1));}
  int batchTimeoutSeconds(int progress){return progress>=60?0:progress+1;}
  static int settingIndex(int[] values,int selected,int fallback){for(int i=0;i<values.length;i++)if(values[i]==selected)return i;return fallback;}
  LinearLayout sliderTicks(int[] values){LinearLayout row=new LinearLayout(this);for(int value:values){TextView tick=text(value==0?"无限":String.valueOf(value),9,MUTED);tick.setGravity(Gravity.CENTER);row.addView(tick,new LinearLayout.LayoutParams(0,dp(24),1));}return row;}
  String autoExpandPagesText(String label,int pages){return label+"："+(pages==0?"无限":pages+" 页");}
  LinearLayout buildAutoExpandPageSettings(){int[] values={1,2,3,5,10,0};LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);TextView initial=text(autoExpandPagesText("初始提前展开",sessionAutoExpandInitialPages),14,TEXT),next=text(autoExpandPagesText("后续触发展开",sessionAutoExpandNextPages),14,TEXT);LumaSlider initialBar=new LumaSlider(this),nextBar=new LumaSlider(this);initialBar.setMax(values.length-1);nextBar.setMax(values.length-1);initialBar.setProgressValue(settingIndex(values,sessionAutoExpandInitialPages,2),false);nextBar.setProgressValue(settingIndex(values,sessionAutoExpandNextPages,0),false);initialBar.setOnChangeListener((value,user)->{sessionAutoExpandInitialPages=values[Math.max(0,Math.min(values.length-1,value))];initial.setText(autoExpandPagesText("初始提前展开",sessionAutoExpandInitialPages));if(user)persistSearchSettings();});nextBar.setOnChangeListener((value,user)->{sessionAutoExpandNextPages=values[Math.max(0,Math.min(values.length-1,value))];next.setText(autoExpandPagesText("后续触发展开",sessionAutoExpandNextPages));if(user)persistSearchSettings();});panel.addView(initial,new LinearLayout.LayoutParams(-1,dp(30)));panel.addView(initialBar,new LinearLayout.LayoutParams(-1,dp(56)));panel.addView(next,new LinearLayout.LayoutParams(-1,dp(30)));panel.addView(nextBar,new LinearLayout.LayoutParams(-1,dp(56)));panel.addView(sliderTicks(values),new LinearLayout.LayoutParams(-1,dp(22)));return panel;}

  int[] indexThreadChoices(){int adaptive=LanzouCore.adaptiveNetworkWorkers(Integer.MAX_VALUE),boosted=(int)Math.min(Integer.MAX_VALUE,(long)adaptive*2L);TreeSet<Integer> choices=new TreeSet<>();Collections.addAll(choices,1,2,4,Math.max(4,adaptive/2),adaptive,Math.max(adaptive,boosted));if(sessionIndexThreads>0)choices.add(sessionIndexThreads);int[] out=new int[choices.size()+1];int at=0;for(int value:choices)out[at++]=value;out[at]=0;return out;}
  LinearLayout buildIndexSliderSettings(){int[] threads=indexThreadChoices(),retention={1,6,12,24,72,168,720,0};LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);TextView threadLabel=text(indexThreadText(),14,TEXT),retentionLabel=text(indexRetentionText(),14,TEXT);LumaSlider threadBar=new LumaSlider(this),retentionBar=new LumaSlider(this);threadBar.setMax(threads.length-1);threadBar.setProgressValue(settingIndex(threads,sessionIndexThreads,Math.min(1,threads.length-1)),false);threadBar.setContentDescription("调整后台索引线程数，最右为不设手动上限并按设备 CPU 与内存自动调度");retentionBar.setMax(retention.length-1);retentionBar.setProgressValue(settingIndex(retention,sessionIndexRetentionHours,3),false);retentionBar.setContentDescription("调整单个索引最高保留时间，最右为无限");threadBar.setOnChangeListener((value,user)->{sessionIndexThreads=threads[Math.max(0,Math.min(threads.length-1,value))];threadLabel.setText(indexThreadText());if(user)persistSearchSettings();});retentionBar.setOnChangeListener((value,user)->{sessionIndexRetentionHours=retention[Math.max(0,Math.min(retention.length-1,value))];retentionLabel.setText(indexRetentionText());if(user){persistSearchSettings();refreshIndexProgressCard();}});panel.addView(threadLabel,new LinearLayout.LayoutParams(-1,dp(34)));panel.addView(threadBar,new LinearLayout.LayoutParams(-1,dp(56)));panel.addView(sliderTicks(threads),new LinearLayout.LayoutParams(-1,dp(24)));panel.addView(retentionLabel,new LinearLayout.LayoutParams(-1,dp(34)));panel.addView(retentionBar,new LinearLayout.LayoutParams(-1,dp(56)));panel.addView(sliderTicks(retention),new LinearLayout.LayoutParams(-1,dp(24)));return panel;}
  String indexThreadText(){return sessionIndexThreads==0?"索引线程：自动适配（当前建议 "+LanzouCore.adaptiveNetworkWorkers(Integer.MAX_VALUE)+"）":"索引线程："+sessionIndexThreads;}
  String indexRetentionText(){if(sessionIndexRetentionHours==0)return"单个索引最高保留：无限";if(sessionIndexRetentionHours%24==0)return"单个索引最高保留："+(sessionIndexRetentionHours/24)+" 天";return"单个索引最高保留："+sessionIndexRetentionHours+" 小时";}
  long indexRetentionMillis(){return sessionIndexRetentionHours<=0?0L:TimeUnit.HOURS.toMillis(sessionIndexRetentionHours);}

    LinearLayout buildAdbInstallThreadSettings(){int adaptiveLimit=Math.max(1,LanzouCore.adaptiveNetworkWorkers(Integer.MAX_VALUE)),stored=installParallelism(),threads=stored==0?0:Math.min(stored,adaptiveLimit);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);TextView label=text(threads==0?"同时安装：自动适配":"同时安装："+threads,14,TEXT);LumaSlider bar=new LumaSlider(this);bar.setMax(adaptiveLimit);bar.setProgressValue(threads==0?adaptiveLimit:threads-1,false);bar.setContentDescription("调整静默安装线程数，最右为按设备 CPU 与内存自动适配");bar.setOnChangeListener((position,user)->{int selected=position==adaptiveLimit?0:position+1;label.setText(selected==0?"同时安装：自动适配":"同时安装："+selected);if(user)setInstallParallelism(selected);});panel.addView(label,new LinearLayout.LayoutParams(-1,dp(34)));panel.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));return panel;}

  String sourceListDisplayCountText(int count){int normalized=normalizeSourceListDisplayCount(count);return "列表源页展示项目数："+(normalized==0?"无限":normalized+" 项");}
  LinearLayout buildListDisplaySettings(){int[] values={SOURCE_LIST_MIN_DISPLAY,50,100,200,500,1000,0};LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);TextView label=text(sourceListDisplayCountText(sessionSourceListDisplayCount),14,TEXT);LumaSlider bar=new LumaSlider(this);bar.setMax(values.length-1);bar.setProgressValue(settingIndex(values,normalizeSourceListDisplayCount(sessionSourceListDisplayCount),0),false);bar.setContentDescription("只调整底栏“更多/软件”列表源页每次展示的源项目数，不影响文件列表页；最小 32 项，最右为无限");bar.setOnChangeListener((value,user)->{sessionSourceListDisplayCount=normalizeSourceListDisplayCount(values[Math.max(0,Math.min(values.length-1,value))]);label.setText(sourceListDisplayCountText(sessionSourceListDisplayCount));if(user){persistSearchSettings();if(pageKind==1&&sourceGrid!=null&&sourcePageSources!=null&&sourceHeading!=null)renderSources(sourceGrid,sourcePageSources,sourceFilter==null?"":sourceFilter.getText().toString().trim(),sourceHeading);}});panel.addView(label,new LinearLayout.LayoutParams(-1,dp(34)));panel.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));panel.addView(sliderTicks(values),new LinearLayout.LayoutParams(-1,dp(24)));return panel;}

  void updateSearchConcurrencyLimit(String category,int mode,int[] total,int[] selectedConcurrency,LumaSlider concurrencyBar,TextView concurrencyLabel){total[0]=searchableSourceTotal(category,mode);if(total[0]>0&&selectedConcurrency[0]>total[0])selectedConcurrency[0]=0;int value=total[0]==0?0:selectedConcurrency[0];concurrencyBar.setMax(Math.max(0,total[0]));concurrencyBar.setProgressValue(total[0]==0?0:(value<=0?total[0]:value-1),false);concurrencyBar.setEnabled(total[0]>0);concurrencyLabel.setText(concurrencyText(value,total[0]));concurrencyLabel.setContentDescription(concurrencyDescription(value,total[0]));}
  View settingsSection(int iconRes,String title,String summary,boolean expanded,View... children){
    LinearLayout section=new LinearLayout(this);section.setOrientation(LinearLayout.VERTICAL);
    GradientDrawable surface=new GradientDrawable();surface.setColor(SET_LOW);surface.setCornerRadius(dp(20));surface.setStroke(dp(1),SET_STROKE);// v7:卡 20dp 圆角+s-low 面+1px stroke
    section.setBackground(surface);section.setClipToOutline(true);
    LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(14),dp(6),dp(10),dp(6));
    header.setClickable(true);header.setFocusable(true);header.setBackground(settingsPress());applePressScale(header);
    ImageView badge=new ImageView(this);badge.setImageResource(iconRes);badge.setColorFilter(SET_PRIMARY_HI);
    GradientDrawable badgeBg=new GradientDrawable();badgeBg.setColor(SET_SEL);badgeBg.setCornerRadius(dp(10));badgeBg.setStroke(dp(1),SET_SEL_STROKE);// v7:32dp 图标盒,sel 底+主色 28% 描边
    badge.setBackground(badgeBg);badge.setPadding(dp(7),dp(7),dp(7),dp(7));badge.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams badgeLp=new LinearLayout.LayoutParams(dp(32),dp(32));badgeLp.setMargins(0,0,dp(10),0);header.addView(badge,badgeLp);
    LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);
    TextView heading=text(title,15,TEXT);heading.setTypeface(AppFonts.bold(this));
    copy.addView(heading,new LinearLayout.LayoutParams(-1,dp(28)));
    /* [DFW-24] 副标题原来是 SET_T3（白 40% = 禁用档），比同页行内 hint 的 SET_T2（66%）低 26 个点，
       同一屏里"次级说明"出现两种亮度。统一到 SET_T2。 */
    TextView caption=text(summary,11,SET_T2);caption.setSingleLine(true);caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
    copy.addView(caption,new LinearLayout.LayoutParams(-1,dp(22)));
    header.addView(copy,new LinearLayout.LayoutParams(0,dp(52),1));
    ImageView arrow=new ImageView(this);arrow.setId(R.id.dfwx_section_arrow);arrow.setImageResource(R.drawable.ic_expand);arrow.setColorFilter(PRIMARY);arrow.setPadding(dp(8),dp(8),dp(8),dp(8));
    header.addView(arrow,new LinearLayout.LayoutParams(dp(44),dp(52)));
    section.addView(header,new LinearLayout.LayoutParams(-1,dp(64)));
    LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(4),0,dp(4),dp(6));
    boolean first=true;
    for(View child:children)if(child!=null){removeFromParent(child);
      if(!first){View divider=new View(this);divider.setBackgroundColor(SET_STROKE2);LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(-1,dp(1));dl.setMargins(dp(50),0,dp(8),0);content.addView(divider,dl);}// v7:行间分隔线 left 50(对齐标题文字) right 8
      first=false;content.addView(child,new LinearLayout.LayoutParams(-1,-2));}
    section.addView(content,new LinearLayout.LayoutParams(-1,-2));
    // [DFW-71] 首次进设置用硬编码默认值；之后一律用**用户上次的展开态**，
    // 否则返回设置页时展开过的分区又被收起来，视觉跳变比单纯回顶更大。
    boolean open=settingsExpansion.containsKey(title)?settingsExpansion.get(title):expanded;
    header.setTag(R.id.dfwx_section_title,title);
    syncSectionChrome(section,open);
    header.setOnClickListener(v->{boolean willOpen=content.getVisibility()!=View.VISIBLE;settingsExpansion.put(title,willOpen);animateSection(section,content,arrow,willOpen);});
    section.setTag(open);// T5-S §7:记录默认展开态,搜索清空后恢复
    return section;}
  /**
   * [DFW-24] 分区的展开态必须**三件一起改**：内容可见性、箭头朝向、读屏描述。
   *
   * 旧实现只有"点分区头"这一条路会同时改这三件；而设置页的**内联搜索**会把命中的分区内容
   * 强制 `setVisibility(VISIBLE)`，箭头却还指着"收起"、读屏还念"已收起" ——
   * 这是本页唯一的逻辑可见 bug（用户看不到箭头，读屏用户会被直接误导）。
   */
  void syncSectionChrome(LinearLayout section,boolean open){
    if(section==null)return;
    if(section.getChildCount()>1){
      View content=section.getChildAt(1);
      if(content!=null)content.setVisibility(open?View.VISIBLE:View.GONE);
    }
    View arrow=section.findViewById(R.id.dfwx_section_arrow);
    if(arrow!=null)arrow.setRotation(open?180f:0f);
    if(section.getChildCount()>0){
      View header=section.getChildAt(0);
      Object titleTag=header==null?null:header.getTag(R.id.dfwx_section_title);
      if(header!=null&&titleTag instanceof String){
        String title=(String)titleTag;
        header.setContentDescription(title+"，"+(open?"已展开":"已收起")+"，点击"+(open?"收起":"展开"));
      }
    }
  }

  public void animateSection(LinearLayout section,LinearLayout content,ImageView arrow,boolean open){// v1.22.4 展开收起重做（用户报"挺奇怪"）：三个怪感根因——①sceneRoot 只包 section 自身，下方兄弟卡片瞬跳露空隙，上移到 body 让全部卡片一起平滑滑动；②展开 Fade 让内容"边缩边透明消失"，去掉后内容 alpha 恒 1 随高度被 20dp 圆角裁剪揭示；③收起 Fade 收短到 120ms 只负责内容淡出。时长 240→300ms M3 emphasized；chevron 旋转从 VPA 改 ROTATION 弹簧 800/.75 与按压同一语言。
    if(!motionEnabled()){syncSectionChrome(section,open);return;}
    android.view.ViewGroup sceneRoot=(android.view.ViewGroup)section.getParent();
    android.transition.TransitionSet set=new android.transition.TransitionSet().addTransition(new android.transition.ChangeBounds()).setDuration(300).setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f));
    if(!open)set.addTransition(new android.transition.Fade(android.transition.Fade.OUT).setDuration(100));
    android.transition.TransitionManager.beginDelayedTransition(sceneRoot,set);content.setVisibility(open?View.VISIBLE:View.GONE);
    syncSectionChrome(section,open);
    SpringAnimation rotate=(SpringAnimation)arrow.getTag();
    if(rotate==null){rotate=new SpringAnimation(arrow,DynamicAnimation.ROTATION,open?180f:0f);arrow.setTag(rotate);}
    rotate.getSpring().setStiffness(800f).setDampingRatio(0.75f);rotate.animateToFinalPosition(open?180f:0f);}
  void addSettingsSection(LinearLayout body,View section){LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.setMargins(0,0,0,dp(10));body.addView(section,params);settingsSearchSections.add(section);}
  View buildSettingsSearch(){FrameLayout box=new FrameLayout(this);box.setBackground(shape(SURFACE,14));box.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);box.setFocusableInTouchMode(true);box.setFocusable(true);EditText input=new EditText(this);input.setSingleLine();input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setHint("搜索设置项");input.setTextSize(14);input.setBackgroundColor(Color.TRANSPARENT);input.setPadding(dp(14),0,dp(52),0);input.setImeOptions(EditorInfo.IME_ACTION_DONE);box.addView(input,new FrameLayout.LayoutParams(-1,-1));ImageButton clear=iconButton(R.drawable.ic_close,"清除搜索");clear.setVisibility(View.GONE);FrameLayout.LayoutParams fp=new FrameLayout.LayoutParams(dp(44),dp(44),Gravity.END|Gravity.CENTER_VERTICAL);box.addView(clear,fp);settingsSearchInput=input;Runnable run=()->{String q=input.getText().toString().trim();clear.setVisibility(q.isEmpty()?View.GONE:View.VISIBLE);applySettingsFilter(q);};clear.setOnClickListener(v->{input.setText("");input.clearFocus();});input.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){if(searchDebounceRunnable!=null)ui.removeCallbacks(searchDebounceRunnable);searchDebounceRunnable=run;ui.postDelayed(run,150);}public void afterTextChanged(Editable e){}});return box;}
  void applySettingsFilter(String query){if(settingsSearchSections.isEmpty())return;String q=query.trim();boolean searching=!q.isEmpty();int visibleSections=0;for(View sectionObj:settingsSearchSections){LinearLayout section=(LinearLayout)sectionObj;LinearLayout content=(LinearLayout)section.getChildAt(section.getChildCount()>1?1:0);if(!searching){section.setVisibility(View.VISIBLE);syncSectionChrome(section,Boolean.TRUE.equals(section.getTag()));for(int r=0;r<content.getChildCount();r++)content.getChildAt(r).setVisibility(View.VISIBLE);continue;}int hits=0;for(int r=0;r<content.getChildCount();r++){View row=content.getChildAt(r);StringBuilder sb=new StringBuilder();collectSettingsText(row,sb);boolean hit=sb.length()>0&&sb.toString().contains(q);row.setVisibility(hit?View.VISIBLE:View.GONE);if(hit)hits++;}section.setVisibility(hits>0?View.VISIBLE:View.GONE);if(hits>0){syncSectionChrome(section,true);visibleSections++;}}if(settingsSearchEmpty!=null)settingsSearchEmpty.setVisibility(searching&&visibleSections==0?View.VISIBLE:View.GONE);}
  void collectSettingsText(View view,StringBuilder sb){if(view instanceof TextView){String t=((TextView)view).getText().toString();if(!t.isEmpty()){if(sb.length()>0)sb.append(' ');sb.append(t);}}else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)collectSettingsText(group.getChildAt(i),sb);}else{CharSequence cd=view.getContentDescription();if(cd!=null&&cd.length()>0){if(sb.length()>0)sb.append(' ');sb.append(cd);}}}
  /**
   * [DFW-73] 崩溃日志页。
   *
   * 用户 2026-10-01 真机反馈："我点击之后，动画效果卡一下，我点击后按理来说会像其他的一样直接进去。
   * 但是我点击后能看到上半部分打开的样子，以及下半部分透明。"
   *
   * 两个原因都在这一页上：
   *  1. **卡一下**：`buildCrashReport()`（可能很长）和 `ensureCrashFolder()`（文件 IO）原来是在
   *     **转场那一帧**同步跑的，把整页推入动画的主线程整个占住 —— 点下去先僵一下，然后才动。
   *     现在先只上"壳子"，正文挪到下一帧再填：转场先跑起来，用户看到的就是"直接进去"。
   *  2. **下半部分透明**：页面帧以前没有底色（见 `basePage()` 的说明），新页滑进来的过程中
   *     下面那截会透出旧页（旧页还被压暗到 55%）。
   */
  void showCrashLogPage(){
    primaryBase(3);pageKind=9;activeSource=null;clearFolderTrail();systemBackAction=this::showSettings;
    LinearLayout body=aboutBackBar("崩溃日志");
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);
    scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
    root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    // v1.22.11 修"打开后瞬间闪一下、顶上 4 个按钮被滚走"（用户 2026-09-29 第二轮反馈）：
    // 正文设了 setTextIsSelectable(true)，它同时是"触摸模式下可获焦"的视图；页面铺开后系统在触摸模式里
    // 自动把焦点交给子树里第一个触摸可获焦的视图（就是那块长正文），ScrollView 随即把它滚进可视区，
    // 实测 scrollY 0→756，于是顶部操作卡被顶出屏幕——表现为"闪了一下就看不到按钮了"。
    // 修法：让滚动容器自己当触摸模式焦点锚点（FOCUS_BEFORE_DESCENDANTS 抢在子树之前），焦点落在容器上就不会产生任何滚动。
    // 正文延后一帧再加，所以这里先把锚点抢好，后加的子树抢不走它。
    scroll.setFocusableInTouchMode(true);
    scroll.setDescendantFocusability(ViewGroup.FOCUS_BEFORE_DESCENDANTS);
    scroll.post(()->{if(pageFrame!=null&&scroll.getParent()!=null){scroll.requestFocus();scroll.scrollTo(0,0);}});
    final int session=navigationSession;
    ui.post(()->{if(pageKind!=9||session!=navigationSession||isFinishing()||isDestroyed())return;fillCrashLogBody(body);});
  }

  /** 崩溃日志页的正文（重活：报告生成 + 目录检查 + 三张卡）。只在壳子上屏之后调用。 */
  void fillCrashLogBody(LinearLayout body){
    String report=buildCrashReport();
    boolean folderReady=storageAccessGranted()&&ensureCrashFolder();
    // v1.22.10（用户反馈："按钮什么的从最底部改到最顶部"）：操作卡置于正文卡之上，长日志也不用手滑到底。
    // 顺序=保存/分享/复制（高频在前）、清除（破坏性在后，需二次确认）。
    LinearLayout opsCard=aboutCard();
    if(!report.isEmpty()){final String content=report;
      opsCard.addView(settingsAction(R.drawable.ic_download,"保存为文件",v->saveCrashReport(content)),new LinearLayout.LayoutParams(-1,-2));
      addCardDivider(opsCard);
      opsCard.addView(settingsAction(R.drawable.ic_share,"分享报告文件",v->shareCrashReport(content)),new LinearLayout.LayoutParams(-1,-2));
      addCardDivider(opsCard);
      opsCard.addView(settingsAction(R.drawable.ic_copy,"复制全部日志",v->copyPlainText(content)),new LinearLayout.LayoutParams(-1,-2));
      addCardDivider(opsCard);}
    // "打开文件夹"常驻：用户 2026-09-29 反馈在 MT 管理器里找不到目录，直接把入口放在这一页，
    // 点一下就跳到 Download/东方无限/崩溃日志，不用自己一层层翻。
    opsCard.addView(settingsAction(R.drawable.ic_folder_open,"打开崩溃日志文件夹",v->openCrashFolder()),new LinearLayout.LayoutParams(-1,-2));
    if(!report.isEmpty()){addCardDivider(opsCard);
      opsCard.addView(settingsAction(R.drawable.ic_close,"清除崩溃记录",v->confirmClearCrashLog()),new LinearLayout.LayoutParams(-1,-2));}
    body.addView(opsCard,aboutCardLp());
    body.addView(crashStorageNote(folderReady),aboutCardLp());
    LinearLayout logCard=aboutCard();TextView logView;
    if(report.isEmpty()){logView=text("暂无崩溃记录，应用运行正常",13,MUTED);}else{logView=text(report,11,TEXT);logView.setTypeface(android.graphics.Typeface.MONOSPACE);logView.setTextIsSelectable(true);}
    logCard.addView(logView,new LinearLayout.LayoutParams(-1,-2));body.addView(logCard,aboutCardLp());
  }
  /**
   * 崩溃日志存哪、现在能不能写：把**绝对路径**直接摆在页面上（v1.22.11）。
   * 用户 2026-09-29 反馈"在 MT 管理器里没有找到东方无限文件夹"——与其让用户去猜，
   * 不如把真实路径和"是否已建好"直接写在这一页，并给一个一键打开入口。
   */
  LinearLayout crashStorageNote(boolean folderReady){LinearLayout card=aboutCard();
    java.io.File folder=App.publicCrashFolder();
    String absolute=folder==null?crashFolderLabel():folder.getAbsolutePath();
    TextView title=text(folderReady?"文件夹已就绪":"文件夹尚未创建（缺少存储权限）",13,folderReady?TEXT:ERROR_TOKEN);
    title.setTypeface(AppFonts.medium(this));
    TextView path=text(absolute,11,folderReady?PRIMARY:MUTED);
    path.setTextIsSelectable(true);
    path.setPadding(0,dp(3),0,dp(3));
    TextView detail=text(folderReady
      ?"crash.log 与导出的报告都在这里。MT 管理器路径：内部存储 → Download → 东方无限 → 崩溃日志。"
      :"点上面的「保存为文件」会申请「管理所有文件」权限；授权后应用会自动建好这个文件夹。",11,MUTED);
    detail.setLineSpacing(dp(2),1f);
    card.addView(title,new LinearLayout.LayoutParams(-1,dp(24)));
    card.addView(path,new LinearLayout.LayoutParams(-1,-2));
    card.addView(detail,new LinearLayout.LayoutParams(-1,-2));
    return card;}
  /**
   * 直接用文件管理器打开崩溃日志目录（用户找不到文件夹时点这里）。
   *
   * ## [DFW-97 修正] 为什么改成"先问系统谁能处理，能处理才跳"
   *
   * 用户 2026-10-02 截图反馈：**点一下按钮连弹两条提示** ——
   * 先「已复制路径：…」，紧接着「没有可用的文件管理器，路径是：…」。
   *
   * 根因：原实现是**异常驱动**的两级 try/catch：
   * ① 先 `startActivity(ACTION_VIEW + 目录 URI)`；vivo 的系统没接这个 intent → 抛；
   * ② 落到兜底里，先弹「已复制路径」，再 `startActivity(CATEGORY_APP_FILES)`；
   *    **这个较新的分类入口 vivo 也没注册** → 又抛 → 弹第二条。
   * 于是用户看到"两条提示、而且第二条说没有文件管理器"——
   * 明明手机里有文件管理器，只是不认这两个入口。
   *
   * 现在改成**能力探测**：用 `resolveActivity` 先问系统"谁能处理"，
   * 按兼容性从新到旧依次试（目录 URI → 系统下载界面 → 文件管理器分类 → 任意能看目录的），
   * **只在真的一个都没有时**才提示，且只提示一次（并附上路径，因为路径已经复制好了）。
   */
  void openCrashFolder(){
    if(!storageAccessGranted()){requestManageAllFilesAccess("打开崩溃日志文件夹需要“管理所有文件”权限。",this::openCrashFolder,false);return;}
    if(!ensureCrashFolder()){showNotice("文件夹创建失败，请确认已授予“管理所有文件”权限",true);return;}
    if(openCrashFolderWithBestApp())return;
    // 一个都没有：把路径复制好，只提示一次，说清楚"手动进去也能到"。
    Toast.makeText(this,"路径已复制："+crashFolderLabel()+"（手机里没有可用的文件管理器，可在任意文件管理 App 里粘贴打开）",Toast.LENGTH_LONG).show();
  }

  /**
   * 依次尝试各代文件管理器入口，**能处理才跳**；成功跳转返回 true。
   *
   * 顺序按"兼容性从旧到新"排，而不是反过来：
   * 越老的入口被越多机器注册，先试它成功率最高、也最不容易出现"跳过去是空页面"。
   */
  boolean openCrashFolderWithBestApp(){
    java.util.List<Intent> candidates=new java.util.ArrayList<>();
    // ① 系统"下载"界面（Android 8+ 标配，绝大多数机器都有；能直接看到 东方无限 这个文件夹）
    candidates.add(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));
    // ② 文件管理器分类入口（较新，部分国产 ROM 没注册 —— 这次踩的就是它）
    Intent files=new Intent(Intent.ACTION_MAIN);files.addCategory(Intent.CATEGORY_APP_FILES);candidates.add(files);
    // ③ DocumentsUI 目录 URI（标准做法，但需要对方声明接受 vnd.android.document/directory）
    android.net.Uri uri=new android.net.Uri.Builder().scheme("content")
      .authority("com.android.externalstorage.documents").appendPath("document")
      .appendPath("primary:"+crashFolderLabel()).build();
    Intent view=new Intent(Intent.ACTION_VIEW);view.setDataAndType(uri,"vnd.android.document/directory");
    view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);candidates.add(view);
    // ④ 兜底：让用户自己挑一个能打开"文件夹"的 App
    Intent pick=new Intent(Intent.ACTION_GET_CONTENT);pick.setType("vnd.android.document/directory");
    candidates.add(pick);

    // 先复制路径：无论最后跳到哪个 App，用户都能直接粘贴，不用手打一长串。
    try{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
      if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("dfwx_crash_folder",crashFolderLabel()));}catch(Exception ignored){}

    for(Intent candidate:candidates){
      try{
        if(candidate.resolveActivity(getPackageManager())==null)continue;
        startActivity(candidate);
        return true;
      }catch(Exception ignored){
        // 这一个不行就试下一个，不打扰用户
      }
    }
    return false;
  }
  /** 卡片内分隔线（与 settingsAction 行左对齐，缩进 50dp）。 */
  void addCardDivider(LinearLayout card){View divider=new View(this);divider.setBackgroundColor(SET_STROKE2);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(dp(50),0,dp(8),0);card.addView(divider,lp);}
  /** v1.22.10：清除崩溃记录改为二次确认（破坏性操作，误触会丢掉唯一一份现场）。清除范围含公共目录副本。 */
  /**
   * DFW-16：一键清掉下载历史里保存的**分享密码**（以及由密码构成的可直接打开链接）。
   *
   * 背景：蓝奏分享链接常带提取码，历史记录把密码一起落盘（`entry.password`）以便重试；
   * 但它明文留存在本机，且此前**没有任何用户可见的清理入口**。
   * 这里只清密码字段，**不动下载记录本身**（卡片红线：不删除用户的下载历史）。
   * 已完成的下载不再需要密码，所以清掉不影响正常使用。
   */
  void clearStoredSharePasswords(){
    int cleared=0;
    for(DownloadEntry entry:downloadEntries){
      synchronized(entry){
        if(entry.password!=null&&!entry.password.isEmpty()){entry.password="";cleared++;}
        // 密码也可能被拼进 shareUrl 的查询串里，一并剥掉 pwd= 参数
        if(entry.shareUrl!=null&&entry.shareUrl.contains("pwd=")){
          String cleaned=entry.shareUrl.replaceAll("(?i)([?&])pwd=[^&]*","$1").replaceAll("[?&]$","");
          if(!cleaned.equals(entry.shareUrl)){entry.shareUrl=cleaned;cleared++;}
        }
      }
    }
    if(cleared>0)persistDownloadHistory();
    showNotice(cleared>0?("已清除 "+cleared+" 条记录里的分享密码"):"历史里没有保存的分享密码",false);
  }
  /** 二次确认后清除（破坏性操作）。 */
  void confirmClearStoredSharePasswords(){
    int count=0;
    for(DownloadEntry entry:downloadEntries)synchronized(entry){if(entry.password!=null&&!entry.password.isEmpty())count++;}
    if(count==0){showNotice("历史里没有保存的分享密码",false);return;}
    AlertDialog dialog=new AlertDialog.Builder(this).setTitle("清除分享密码？")
      .setMessage("会清掉 "+count+" 条下载记录里保存的提取码。\n\n不影响下载记录和已下载的文件，但之后重试这些任务需要重新输入密码。")
      .setNegativeButton("取消",null)
      .setPositiveButton("清除",(d,w)->clearStoredSharePasswords()).create();
    dialog.setCanceledOnTouchOutside(false);showRounded(dialog);
  }
  void confirmClearCrashLog(){AlertDialog dialog=new AlertDialog.Builder(this).setTitle("清除崩溃记录？").setMessage("会删掉最近一次崩溃的堆栈、crash.log 历史，以及 "+crashFolderLabel()+" 里已导出的报告文件。清除后无法恢复。").setNegativeButton("取消",null).setPositiveButton("清除",(d,w)->{clearCrashArtifacts();showNotice("崩溃记录已清除",false);pageDirection=0;showCrashLogPage();}).create();dialog.setCanceledOnTouchOutside(false);showRounded(dialog);}
  void clearCrashArtifacts(){me.rerere.rikkahub.utils.CrashHandler.INSTANCE.clearCrashed(this);deleteQuietly(privateCrashLogFile());DfLog.clear();try{if(storageAccessGranted()){deleteQuietly(App.publicCrashLogFile());for(java.io.File file:crashReportFiles())deleteQuietly(file);}}catch(Exception ignored){android.util.Log.w("MainActivity","MainActivity Exception: "+ignored.getMessage(),ignored);}}
  /** 崩溃报告正文：最近一次堆栈 + crash.log 历史 + 环境诊断。环境段统一取 App.diagnostics()，避免两处各写各的。 */
  String buildCrashReport(){StringBuilder report=new StringBuilder();String latest=me.rerere.rikkahub.utils.CrashHandler.INSTANCE.getStackTrace(this);if(latest!=null&&!latest.trim().isEmpty())report.append("── 最近一次崩溃（最新） ──\n").append(latest.trim()).append("\n\n");String history=crashLogTail();if(!history.isEmpty())report.append("── crash.log 历史（最多最近 12K） ──\n").append(history).append("\n\n");if(report.length()==0&&DfLog.readForExport().isEmpty())return "";report.append("── 环境诊断 ──\n").append(App.diagnostics());
    /*
     * [DFW-88] **把下载解析日志也带进报告。**
     *
     * 用户连续 5 个版本报「下载一直显示解析中」，排查全靠猜 ——
     * 后来在解析链路加了埋点写 `Download/东方无限/崩溃日志/download.log`，
     * 注释里写着"用户在「崩溃日志」页导出时能一并取走"。
     *
     * **但那句话一直没兑现**：`download.log` 只写不读，全仓库除了写它的那几行，
     * 再没有任何地方碰过它 —— 用户根本没有入口把它取出来发给我。
     * 埋点加了却拿不到，等于没加。
     *
     * 现在接进报告：导出崩溃报告 = 连解析日志一起拿走，用户只要点一次「导出」。
     */
    try{
      java.io.File downloadLog=new java.io.File(android.os.Environment.getExternalStorageDirectory(),"Download/东方无限/崩溃日志/download.log");
      if(downloadLog.isFile()&&downloadLog.length()>0){
        report.append("\n── 下载解析日志（DFW-88 定位用） ──\n");
        report.append(CrashLogStore.readTail(downloadLog));
        report.append("\n");
      }else{
        report.append("\n── 下载解析日志 ──\n（还没有记录：说明本次没有走过解析流程，或者解析根本没开始）\n");
      }
    }catch(Throwable ignored){
      // 报告本身绝不能因为附加上下文而失败
    }
    /*
     * [DFW-101] **统一事件日志整段带走。**
     *
     * 这是"任何不合理问题都留下现场"的最后一步：前面那些机制负责**记**，
     * 这里负责让用户**拿得到**。否则又是一次"埋点加了却取不出来"（DFW-88 的教训）。
     * 上限 24K，避免报告大到分享/剪贴板放不下。
     */
    try{
      String events=DfLog.readForExport();
      report.append("\n── 应用事件日志（DFW-101 全量现场，最多最近 24K） ──\n");
      if(events.isEmpty()){
        report.append("（还没有记录）\n");
      }else{
        report.append(events);
        if(!events.endsWith("\n"))report.append("\n");
      }
    }catch(Throwable ignored){
      // 报告本身绝不能因为附加上下文而失败
    }
    return report.toString();}
  /** 报告文件名用纯 ASCII（时间 + 版本号）：中文名在分享/保存链路上会被截断（同 v1.22.8 发版资产名事故根因）。 */
  /** DFW-29：实现已迁到 {@link CrashLogStore}；此处保留转发，外部调用点不变。 */
  String crashReportFileName(){return CrashLogStore.reportFileName(BuildConfig.VERSION_NAME);}
  /**
   * 崩溃报告落盘目录（v1.22.10）：优先公共目录 `Download/东方无限/崩溃日志/`，
   * 用户能在 MT 管理器/文件管理里直接翻到，也可用数据线拷走；没有权限时回退应用外部私有目录，
   * 保证"至少存得下"，不留白。
   */
  java.io.File crashReportFolder(){java.io.File folder=App.publicCrashFolder();if(folder!=null&&(folder.isDirectory()||folder.mkdirs()))return folder;java.io.File fallback=getExternalFilesDir(null);return fallback!=null?fallback:getFilesDir();}
  /** 把报告写入公共目录，同时刷新固定名 "dfwx-crash-latest.txt"（清除时一并删除）。失败返回 null。 */
  java.io.File writeCrashReportFile(String content){return CrashLogStore.writeReport(crashReportFolder(),BuildConfig.VERSION_NAME,content);}
  void writeBytesQuietly(java.io.File file,byte[] bytes){CrashLogStore.writeBytesQuietly(file,bytes);}
  /** 固定留一份"最近一次"的报告，方便用户下次直接拿，文件名保持 ASCII。 */
  java.io.File crashLogExportFile(){java.io.File folder=App.publicCrashFolder();return folder==null?null:new java.io.File(folder,"dfwx-crash-latest.txt");}
  void deleteQuietly(java.io.File file){CrashLogStore.deleteQuietly(file);}
  /** 公共目录里所有历史报告（只有 dfwx-crash-* 前缀，避免误删用户自己的文件）。 */
  java.util.List<java.io.File> crashReportFiles(){return CrashLogStore.reportFiles(App.publicCrashFolder());}
  /** 崩溃日志目录：Download/东方无限/崩溃日志（用户 2026-09-29 指定，名字要好找）。 */
  String crashFolderLabel(){return "Download/东方无限/崩溃日志";}
  /** 写公共目录需要"管理所有文件"权限；没授权就先问，授权后自动续跑（用户 2026-09-29 要求"要能在我手机里找到"）。 */
  boolean ensureCrashReportWritable(Runnable retry){if(storageAccessGranted())return true;requestManageAllFilesAccess("崩溃日志会保存到「"+crashFolderLabel()+"」。要读写这个公共文件夹，需要“管理所有文件”权限——授权后应用会自动把文件夹建好，你在 MT 管理器里就能看到它。",retry,false);return false;}
  void saveCrashReport(String content){if(!ensureCrashReportWritable(()->saveCrashReport(content)))return;io.execute(()->{java.io.File file=writeCrashReportFile(content);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(file==null){showNotice("保存失败，请改用复制",true);return;}String path=file.getAbsolutePath();try{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("dfwx_crash_path",path));}catch(Exception ignored){}showNotice("已保存到 "+crashFolderLabel()+"\n文件名："+file.getName(),true);});});}
  void shareCrashReport(String content){if(!ensureCrashReportWritable(()->shareCrashReport(content)))return;io.execute(()->{java.io.File file=writeCrashReportFile(content);runOnUiThread(()->{if(isFinishing()||isDestroyed())return;if(file==null){showNotice("生成文件失败，请改用复制",true);return;}try{
        // v1.22.10：报告现在落在公共目录，走通用 "shared" 路径（按真实路径授权，保持只读），
        // 原来的 "crash" 私有目录分支已废（用户找不到那份文件，分享出去也没意义）。
        String token=android.util.Base64.encodeToString(file.getAbsolutePath().getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);
        android.net.Uri uri=new android.net.Uri.Builder().scheme("content").authority(getPackageName()+".downloads").appendPath("shared").appendPath(token).build();Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_STREAM,uri);send.putExtra(Intent.EXTRA_SUBJECT,"东方无限 崩溃报告 "+BuildConfig.VERSION_NAME);send.putExtra(Intent.EXTRA_TEXT,content.length()>4000?content.substring(0,4000):content);send.setClipData(android.content.ClipData.newRawUri(file.getName(),uri));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(send,"分享崩溃报告"));}catch(Exception error){showNotice("没有可用的分享应用，请改用复制",true);}});});}
  /** 应用外部私有副本（无需权限，权威副本；崩溃在授权之前也能留下现场）。 */
  java.io.File privateCrashLogFile(){java.io.File dir=getExternalFilesDir(null);return dir==null?null:new java.io.File(dir,"crash.log");}
  java.io.File crashLogFile(){return privateCrashLogFile();}
  /** 展示优先读公共目录那份（用户手边的那份），没有就回退私有副本。 */
  java.io.File crashLogFileForReading(){try{java.io.File shared=App.publicCrashLogFile();if(shared!=null&&shared.exists()&&shared.length()>0)return shared;}catch(Exception ignored){android.util.Log.w("MainActivity","MainActivity Exception: "+ignored.getMessage(),ignored);}return privateCrashLogFile();}
  String crashLogTail(){return CrashLogStore.readTail(crashLogFileForReading());}
  void copyPlainText(String value){try{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard!=null)clipboard.setPrimaryClip(android.content.ClipData.newPlainText("dfwx_crash",value));showNotice("已复制，粘贴发给开发者即可",false);}catch(Exception e){showNotice("复制失败",true);}}
  /** 2026-09-24 性能审计#5：设置页动态行值就地刷新注册表——弹窗保存后只刷新受影响行文本，
      不再整页重建 200+ View，顺带保留滚动位置（原实现每保存一个弹窗就把页面顶回顶部）。 */
  final java.util.List<Runnable> settingsRefreshers=new ArrayList<>();
  void refreshSettingsInPlace(){for(Runnable r:settingsRefreshers)r.run();}
  /* T5-S §6 性能三档：0=节能 1=均衡(默认) 2=高性能。档位只做预设展开写入现有键，不改现有键语义；高性能安全上限=现状默认值，不做任何上调（限频红线硬编码，调高无效） */
  int perfTier=1;boolean perfLowMotion=false;
  EditText settingsSearchInput;TextView settingsSearchEmpty;final java.util.ArrayList<View> settingsSearchSections=new java.util.ArrayList<>();// T5-S §7:设置项内联搜索
  LinearLayout perfChipRow,perfMotionRow;TextView perfTierSummary;
  LinearLayout buildRecursiveFoldersRow(){final LinearLayout row=settingsSwitchRow(R.drawable.ic_folder,"文件夹递归",sessionSearchRecursiveFolders,checked->{if(perfTier==0){showNotice("节能模式下文件夹递归保持关闭",false);return;}sessionSearchRecursiveFolders=checked;persistSearchSettings();});settingsRefreshers.add(()->{View toggle=(View)row.getTag();if(toggle!=null){toggle.setEnabled(perfTier!=0);row.setAlpha(perfTier!=0?1f:.45f);}});return row;}
  String perfTierDescription(int tier){if(tier==2)return"下载最快，请求也最密，更容易被蓝奏云限速。App 会自动把控节奏，一般不用管。";if(tier==1)return"平时就选这个。下载稳定，耗电正常，下面的细项一般不用再调。";return"减少后台请求与自动翻页，发热和流量更低，适合电量紧张时。";}
  String perfTierFacts(){int transfers=downloadTransferParallelism();String motion=perfTier==0?(perfLowMotion?"低":"全开"):"全开";return"同时下载 "+(transfers==0?"自动":transfers)+" ｜ 全局每源 "+SEARCH_MAX_PAGES_GLOBAL+" 页 ｜ 单源翻页 "+(sessionSearchMaxPages==0?"无限":sessionSearchMaxPages+" 页")+" ｜ 预翻页 "+sessionAutoExpandInitialPages+" 页 ｜ 动效 "+motion;}
  void applyPerfTier(int tier){perfTier=Math.max(0,Math.min(2,tier));if(perfTier==0){// §6 节能列：砍链长（单源 5 页）、砍并发（下载 2）、砍后台（索引 1 线程/6h/不自动更新）、降动效；0 会被滑条读成"无限"，所以"不预翻"走总开关
      sessionSearchBatchSeconds=30;sessionSearchMaxPages=5;sessionSearchViewRate=200;sessionSourceListDisplayCount=SOURCE_LIST_MIN_DISPLAY;sessionAutoExpand=false;sessionAutoExpandInitialPages=1;sessionAutoExpandNextPages=1;sessionSearchRecursiveFolders=false;sessionBackgroundIndex=false;sessionIndexThreads=1;sessionIndexRetentionHours=6;setSourceProbeParallelism(0);setDirectResolveParallelism(0);setDownloadTransferParallelism(2);setInstallParallelism(1);perfLowMotion=true;}else{sessionSearchBatchSeconds=15;sessionSearchMaxPages=0;sessionSearchViewRate=0;sessionSourceListDisplayCount=perfTier==2?0:SOURCE_LIST_MIN_DISPLAY;sessionAutoExpand=true;sessionAutoExpandInitialPages=1;sessionAutoExpandNextPages=1;sessionBackgroundIndex=true;sessionIndexThreads=0;sessionIndexRetentionHours=perfTier==2?720:24;setSourceProbeParallelism(0);setDirectResolveParallelism(0);setDownloadTransferParallelism(0);setInstallParallelism(0);perfLowMotion=false;}
    persistSearchSettings();refreshSettingsInPlace();updatePerfTierUi();}
  void updatePerfTierUi(){if(perfChipRow!=null)for(int i=0;i<perfChipRow.getChildCount();i++){View child=perfChipRow.getChildAt(i);if(!(child instanceof TextView))continue;boolean on=(Integer)child.getTag()==perfTier;GradientDrawable bg=new GradientDrawable();bg.setCornerRadius(dp(12));// v7 形态3:芯片 on=sel 底+主色 42% 描边
      if(on){bg.setColor(SET_SEL);bg.setStroke(dp(1),0x6BA78BFA);((TextView)child).setTextColor(SET_PRIMARY_HI);}else{bg.setColor(Color.TRANSPARENT);bg.setStroke(dp(1),SET_STROKE);((TextView)child).setTextColor(SET_T2);}child.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(ThemeEngine.tint(0xFFFFFFFF,26)),bg,null));((TextView)child).setContentDescription(perfTierLabel((Integer)child.getTag())+"模式，"+((Integer)child.getTag()==perfTier?"当前已选择":"点击选择"));}
    if(perfTierSummary!=null)perfTierSummary.setText(perfTierDescription(perfTier)+"\n"+perfTierFacts());
    if(perfMotionRow!=null)perfMotionRow.setVisibility(perfTier==0?View.VISIBLE:View.GONE);}
  String perfTierLabel(int tier){return tier==0?"节能":tier==1?"均衡":"高性能";}
  /**
   * [DFW-73] **初始界面**（用户 2026-10-01 口述：悬浮球六项全部平级之后，要能指定开软件时停在哪一页）。
   *
   * 用户只给了三个选项：**软件库 / AI 对话 / 工具箱**（下载、设置、公告没给，不要自行添加）。
   * 非法值一律回落 0=软件库，保证老用户升级后行为不变。
   */
  int startDestinationPreference(){int stored=getPreferences(0).getInt("start_destination",0);return stored==4||stored==5?stored:0;}
  void setStartDestinationPreference(int value){getPreferences(0).edit().putInt("start_destination",value==4||value==5?value:0).apply();}
  LinearLayout startDestinationChipRow;
  /** 初始界面三选一芯片行（视觉与「性能模式」三档芯片一致：选中=主色实底 + 反白字）。 */
  LinearLayout buildStartDestinationRow(){
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(10),dp(8),dp(10),dp(4));panel.setContentDescription("初始界面");
    TextView title=text("初始界面",15,TEXT);title.setTypeface(AppFonts.bold(this));
    panel.addView(title,new LinearLayout.LayoutParams(-1,dp(30)));
    TextView hint=text("打开软件时默认停在哪个界面",12,SET_T2);
    panel.addView(hint,new LinearLayout.LayoutParams(-1,dp(24)));
    LinearLayout chips=new LinearLayout(this);chips.setOrientation(LinearLayout.HORIZONTAL);
    String[] names={"软件库","AI 对话","工具箱"};
    int[] values={0,4,5};
    startDestinationChipRow=chips;
    for(int i=0;i<values.length;i++){
      final int value=values[i];
      TextView chip=text(names[i],14,TEXT);
      chip.setGravity(Gravity.CENTER);
      chip.setTag(value);
      chip.setClickable(true);
      chip.setFocusable(true);
      chip.setOnClickListener(v->{setStartDestinationPreference(value);syncStartDestinationChips();showNotice("初始界面已设为"+names[value==4?1:value==5?2:0],false);});
      chips.addView(chip,new LinearLayout.LayoutParams(0,dp(46),1));
      if(i<values.length-1)chips.addView(new View(this),new LinearLayout.LayoutParams(dp(8),1));
    }
    panel.addView(chips,new LinearLayout.LayoutParams(-1,dp(46)));
    syncStartDestinationChips();
    return panel;
  }
  void syncStartDestinationChips(){
    if(startDestinationChipRow==null)return;
    int current=startDestinationPreference();
    for(int i=0;i<startDestinationChipRow.getChildCount();i++){
      View child=startDestinationChipRow.getChildAt(i);
      if(!(child instanceof TextView))continue;
      TextView chip=(TextView)child;
      Object tag=chip.getTag();
      boolean selected=tag instanceof Integer&&((Integer)tag).intValue()==current;
      chip.setSelected(selected);
      chip.setTextColor(selected?BG:TEXT);
      chip.setBackground(filterRipple(solidShape(selected?PRIMARY:SURFACE2,16)));
      chip.setContentDescription(chip.getText()+(selected?"，已选择":""));
    }
  }
  LinearLayout buildPerfTierCard(){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(10),dp(10),dp(12));perfChipRow=new LinearLayout(this);perfChipRow.setOrientation(LinearLayout.HORIZONTAL);String[] names={"节能","均衡","高性能"};
    for(int i=0;i<3;i++){final int tier=i;TextView chip=text(names[i],14,TEXT);chip.setGravity(Gravity.CENTER);chip.setTag(i);chip.setClickable(true);chip.setFocusable(true);chip.setOnClickListener(v->applyPerfTier(tier));LinearLayout.LayoutParams chipLp=new LinearLayout.LayoutParams(0,dp(46),1);perfChipRow.addView(chip,chipLp);if(i<2){View gap=new View(this);perfChipRow.addView(gap,new LinearLayout.LayoutParams(dp(8),1));}}
    card.addView(perfChipRow,new LinearLayout.LayoutParams(-1,dp(46)));
    perfTierSummary=text("",12,SET_T2);perfTierSummary.setLineSpacing(dp(2),1f);perfTierSummary.setPadding(dp(2),dp(10),dp(2),0);card.addView(perfTierSummary,new LinearLayout.LayoutParams(-1,-2));
    perfMotionRow=settingsSwitchRow(R.drawable.ic_expand,"低动效（减少界面动画）",perfLowMotion,checked->{perfLowMotion=checked;persistSearchSettings();updatePerfTierUi();});LinearLayout.LayoutParams motionLp=new LinearLayout.LayoutParams(-1,-2);motionLp.setMargins(0,dp(6),0,0);card.addView(perfMotionRow,motionLp);
    updatePerfTierUi();return card;}
  void showSettings(){
    // [DFW-71] 必须在 primaryBase 拆掉旧视图树**之前**存位置，否则就读不到了。
    primaryBase(3);pageKind=4;settingsRefreshers.clear();settingsSearchSections.clear();activeSource=null;clearFolderTrail();systemBackAction=null;primaryHeader("设置");LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(0,dp(4),0,dp(18));/* v1.22.3 边距统一：左右 8dp→0，设置卡片与 header/首页同 16dp 基准（原 24dp 比首页多缩 8dp，造成"边距不齐像被挤住"观感） */LinearLayout.LayoutParams integrityPayLp=new LinearLayout.LayoutParams(-1,-2);integrityPayLp.bottomMargin=dp(10);body.addView(new IntegrityPayButton(this,ThemeEngine.active(this),getResources().getDisplayMetrics().density,Support.unlocked(this),motionEnabled(),this::openSupportActivity),integrityPayLp);applePressScale(integrityPayLp==null?null:(View)body.getChildAt(body.getChildCount()-1));/* v1.23 T5-S 四分区重排（v7 设计稿）：常用默认展开；性能模式收起（细项过渡态，三档芯片随后替换）；高级收起（网络兼容+ADB）；数据与关于收起 */addSettingsSection(body,settingsSection(R.drawable.ic_download,"常用","下载、搜索与列表浏览",true,buildStartDestinationRow(),buildDownloadPathPanel(),buildSearchModeRow(),buildRecursiveFoldersRow(),settingsSwitchRow(R.drawable.ic_expand,"浏览时自动加载更多",sessionAutoExpand,checked->{sessionAutoExpand=checked;persistSearchSettings();if(checked&&folderPullScroll!=null)folderPullScroll.post(this::maybeAutoExpand);})));addSettingsSection(body,settingsSection(R.drawable.ic_bolt,"性能模式","下载与动效的整体节奏",false,buildPerfTierCard(),buildSearchSettingsPanel(),buildDownloadSettingsPanel(),buildListDisplaySettings(),buildAutoExpandPageSettings(),buildIndexSliderSettings(),buildAdbInstallThreadSettings(),buildIndexProgressCard()));addSettingsSection(body,settingsSection(R.drawable.ic_sliders,"高级","网络、兼容与 ADB 安装",false,buildUaSettingsRow(),settingsSwitchRow(R.drawable.ic_open_with,"蓝奏链接直接解析打开",directLanzouListOpen,checked->{directLanzouListOpen=checked;persistSearchSettings();}),buildLanzouBaseOriginRow(),settingsSwitchRow(R.drawable.ic_refresh,"基础链接超时自动切换",sessionLanzouTimeoutFailover,checked->{sessionLanzouTimeoutFailover=checked;persistSearchSettings();}),settingsSwitchRow(R.drawable.ic_share,"网页外部打开",sessionOpenWebExternal,checked->{sessionOpenWebExternal=checked;persistSearchSettings();}),settingsSwitchRow(R.drawable.ic_file,"列表源展示链接",showSourceLinks,checked->{showSourceLinks=checked;persistSearchSettings();}),buildAdbPermissionCard()));addSettingsSection(body,settingsSection(R.drawable.ic_database,"数据与关于","下载显示与资源管理",false,settingsSwitchRow(R.drawable.ic_nav_grid,"批量下载逐项显示",sessionBatchDownloadSingleItem,checked->{sessionBatchDownloadSingleItem=checked;persistSearchSettings();if(pageKind==2)renderDownloads(downloadQuery);}),buildSourceSettingsPanel(),settingsAction(R.drawable.ic_notifications,"公告",v->showNoticeCenter()),settingsAction(R.drawable.ic_sources,"资源源管理",v->showSourcesFromSettings()),settingsAction(R.drawable.ic_refresh,"开源项目主页",v->openInBrowser(DFWX_REPOSITORY,""))));
  // T5-S §8:底部关于区——崩溃日志/参考致谢/关于移出"数据与关于"单独收底,三行顺序按用户指定(崩溃日志→参考致谢→关于);诚信付费按钮保持在设置页顶部
  LinearLayout footer=new LinearLayout(this);footer.setOrientation(LinearLayout.VERTICAL);GradientDrawable footerBg=new GradientDrawable();footerBg.setColor(SET_LOW);footerBg.setCornerRadius(dp(20));footerBg.setStroke(dp(1),SET_STROKE);footer.setBackground(footerBg);footer.setClipToOutline(true);footer.setTag(true);
  LinearLayout footerContent=new LinearLayout(this);footerContent.setOrientation(LinearLayout.VERTICAL);footerContent.setPadding(dp(4),0,dp(4),dp(6));
  footerContent.addView(settingsAction(R.drawable.ic_file,"崩溃日志",v->showCrashLogPage()),new LinearLayout.LayoutParams(-1,-2));
  View footerDivider=new View(this);footerDivider.setBackgroundColor(SET_STROKE2);LinearLayout.LayoutParams fdLp=new LinearLayout.LayoutParams(-1,dp(1));fdLp.setMargins(dp(50),0,dp(8),0);footerContent.addView(footerDivider,fdLp);
  /* 图标：ic_expand 是个"向下箭头"，表达的是"可展开"，拿来当"参考与致谢"的图标完全不搭
     （用户 2026-10-01 反馈："那个图标我感觉并不适合参考与致谢"）。改成心形：致谢就是"谢谢"。
     复用工具箱那颗 Phosphor 心，不再复制一份同形状的资源。 */
  footerContent.addView(settingsAction(R.drawable.ic_tool_heart,"参考与致谢",v->showAcknowledgementsPage()),new LinearLayout.LayoutParams(-1,-2));
  View footerDividerAck=new View(this);footerDividerAck.setBackgroundColor(SET_STROKE2);LinearLayout.LayoutParams fdAckLp=new LinearLayout.LayoutParams(-1,dp(1));fdAckLp.setMargins(dp(50),0,dp(8),0);footerContent.addView(footerDividerAck,fdAckLp);
  /* [DFW-78] 「更新记录」从「数据与关于」分区搬到页脚，位置按用户指定：**参考与致谢的下面**。
     它本来就是"关于这个软件"的东西，和崩溃日志/参考致谢/关于放一起才对。 */
  footerContent.addView(settingsAction(R.drawable.ic_history,"更新记录",v->showChangelogCenter()),new LinearLayout.LayoutParams(-1,-2));
  View footerDivider2=new View(this);footerDivider2.setBackgroundColor(SET_STROKE2);LinearLayout.LayoutParams fd2Lp=new LinearLayout.LayoutParams(-1,dp(1));fd2Lp.setMargins(dp(50),0,dp(8),0);footerContent.addView(footerDivider2,fd2Lp);
  /*
   * [DFW-97] 「反馈与建议」放在「检查更新」**上方**（用户 2026-10-02 指定）。
   * 位置理由：反馈是"我想说点什么"（高频、主动），检查更新是"我怀疑有问题"（低频、被动），
   * 主动的放上面更顺手。
   */
  footerContent.addView(settingsAction(R.drawable.ic_tool_heart,"反馈与建议",v->startActivity(new Intent(this,FeedbackPage.class))),new LinearLayout.LayoutParams(-1,-2));
  View footerDividerFeedback=new View(this);footerDividerFeedback.setBackgroundColor(SET_STROKE2);LinearLayout.LayoutParams fdFbLp=new LinearLayout.LayoutParams(-1,dp(1));fdFbLp.setMargins(dp(50),0,dp(8),0);footerContent.addView(footerDividerFeedback,fdFbLp);
  footerContent.addView(settingsAction(R.drawable.ic_refresh,"检查更新",BuildConfig.VERSION_NAME,v->manualCheckForUpdates()),new LinearLayout.LayoutParams(-1,-2));
  View footerDivider3=new View(this);footerDivider3.setBackgroundColor(SET_STROKE2);LinearLayout.LayoutParams fd3Lp=new LinearLayout.LayoutParams(-1,dp(1));fd3Lp.setMargins(dp(50),0,dp(8),0);footerDivider3.setLayoutParams(fd3Lp);footerContent.addView(footerDivider3);
  footerContent.addView(settingsAction(R.drawable.ic_tool_info,"关于"+PRODUCT_NAME,v->showAboutPage()),new LinearLayout.LayoutParams(-1,-2));
  footer.addView(footerContent,new LinearLayout.LayoutParams(-1,-2));settingsSearchSections.add(footer);body.addView(footer,new LinearLayout.LayoutParams(-1,-2));
  LinearLayout.LayoutParams emptyLp=new LinearLayout.LayoutParams(-1,-2);emptyLp.setMargins(0,dp(40),0,0);settingsSearchEmpty=text("没有匹配的设置项",13,MUTED);settingsSearchEmpty.setGravity(Gravity.CENTER);settingsSearchEmpty.setVisibility(View.GONE);body.addView(settingsSearchEmpty,emptyLp);root.addView(buildSettingsSearch(),new LinearLayout.LayoutParams(-1,dp(48)));ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setContentDescription("设置");scroll.addView(body,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
    // [DFW-71] 让设置页也持有 pageScroll（原来只有目录/源列表/下载页持有），
    // 否则离开时无从读取 scrollY，也就无法恢复。
    pageScroll=scroll;
    // 用现成的 OnPreDrawListener 恢复：必须在首次绘制**之前**跳到位，否则会先闪一下顶部。
    if(settingsScrollY>0)restoreFolderScrollBeforeDraw(scroll,settingsScrollY);
    refreshIndexProgressCard();}
  Uri toolImageUri;int toolQuality=70;String toolImageInfoText="";int toolTargetSizeKb=0;// v1.13.0 精修20：>0 时启用目标体积二分模式
  final java.util.ArrayDeque<String> toolBackStack=new java.util.ArrayDeque<>();
  ToolHost toolHost;
  Runnable torchCleanup,levelCleanup;java.lang.ref.WeakReference<View> levelViewRef;android.media.AudioTrack noiseTrack;android.speech.tts.TextToSpeech ttsEngine;
  /** 工具箱入口：重建整页（列表 or 工具屏）。toolBackStack 空 → 列表页。 */
  void showTools(){primaryBase(5);pageKind=6;activeSource=null;clearFolderTrail();systemBackAction=null;if(toolBackStack.isEmpty())toolHostRenderList();else toolHostRenderTool(toolBackStack.peek());}
  void toolHostRenderList(){if(toolHost==null)toolHost=new ToolHost(this);toolHost.renderList();}
  void toolHostRenderTool(String id){if(toolHost==null)toolHost=new ToolHost(this);toolHost.renderTool(id);}
  /** 打开工具后/返回列表时整页重建：由 ToolHost.openTool 与 popToolBack 调用。 */
  void rebuildToolStackTop(){primaryBase(5);pageKind=6;if(toolBackStack.isEmpty()||"__screen_test__".equals(toolBackStack.peek())){toolBackStack.clear();toolHostRenderList();return;}toolHostRenderTool(toolBackStack.peek());}
  /** 工具屏返回：弹栈到列表页或上一级（返回键由 performSystemBack 统一分发）。 */
  public void popToolBack(){releaseToolMedia();/** 复审3:若当前 content view 是全屏 overlay（屏幕检测），先恢复主视图框架 */if(host!=null&&host.getParent()==null){setContentView(host);installSystemNavigationInsets();}if(toolBackStack.isEmpty()){toolBackStack.clear();showTools();return;}toolBackStack.pop();pageKind=6;primaryBase(5);if(toolBackStack.isEmpty())toolHostRenderList();else toolHostRenderTool(toolBackStack.peek());}
  /** 打开工具：入栈 + 整页重建（ToolHost 与返回键共用）。 */
  public void openTool(String id){if(id==null||id.isEmpty())return;toolBackStack.push(id);pageDirection=1;primaryBase(5);pageKind=6;toolHostRenderTool(id);}
  //—— 手电筒（CameraManager torch，离开工具页/销毁时经 releaseToolMedia 关闭）——
  public boolean startTorch(){
    if(Build.VERSION.SDK_INT<23){showNotice("需要 Android 6.0 以上",true);return false;}
    try{
      android.hardware.camera2.CameraManager manager=(android.hardware.camera2.CameraManager)getSystemService(CAMERA_SERVICE);
      if(manager==null){showNotice("相机服务不可用",true);return false;}
      String cameraId=null;for(String id:manager.getCameraIdList()){android.hardware.camera2.CameraCharacteristics characteristics=manager.getCameraCharacteristics(id);Boolean flash=characteristics.get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);Integer facing=characteristics.get(android.hardware.camera2.CameraCharacteristics.LENS_FACING);if(Boolean.TRUE.equals(flash)&&(facing==null||facing.intValue()==android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)){cameraId=id;break;}}
      if(cameraId==null){showNotice("没有可用的闪光灯",true);return false;}
      final String torchId=cameraId;
      manager.setTorchMode(torchId,true);
      torchCleanup=()->{try{manager.setTorchMode(torchId,false);}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}};
      return true;
    }catch(Exception error){showNotice("手电筒开启失败："+friendlyError(error),true);return false;}
  }
  public void stopTorch(){if(torchCleanup!=null){try{torchCleanup.run();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}torchCleanup=null;}}
  public String toolBytes(long value){if(value<1024)return value+" B";if(value<1024*1024)return String.format(java.util.Locale.CHINA,"%.1f KB",value/1024f);return String.format(java.util.Locale.CHINA,"%.2f MB",value/1024f/1024f);}
  void runImageCompress(Uri uri){
    try{
      toolImageUri=uri;
      android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;
      java.io.InputStream probe=getContentResolver().openInputStream(uri);android.graphics.BitmapFactory.decodeStream(probe,null,bounds);if(probe!=null)try{probe.close();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}
      if(bounds.outWidth<=0){toolImageInfoText="图片读取失败";ui.post(()->{refreshToolImageInfo();showNotice("图片读取失败",true);});return;}
      int sample=1;while((long)bounds.outWidth*bounds.outHeight/(sample*sample)>4096*4096L/4)sample*=2;
      android.graphics.BitmapFactory.Options opts=new android.graphics.BitmapFactory.Options();opts.inSampleSize=sample;
      java.io.InputStream in=getContentResolver().openInputStream(uri);android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(in,null,opts);if(in!=null)try{in.close();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}
      if(bitmap==null){toolImageInfoText="图片解码失败";ui.post(()->{refreshToolImageInfo();showNotice("图片解码失败",true);});return;}
      // v1.13.0 精修20：EXIF 方向修正——相机原图带旋转标记，解码会横竖颠倒；按标记转正后再压缩
      int exifRotation=0;String[] exifTags={"Orientation"};
      try{java.io.InputStream exIn=getContentResolver().openInputStream(uri);
        if(exIn!=null){android.media.ExifInterface exif=new android.media.ExifInterface(exIn);int o=exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);
          if(o==android.media.ExifInterface.ORIENTATION_ROTATE_90)exifRotation=90;
          else if(o==android.media.ExifInterface.ORIENTATION_ROTATE_180)exifRotation=180;
          else if(o==android.media.ExifInterface.ORIENTATION_ROTATE_270)exifRotation=270;
          exIn.close();}
      }catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}
      if(exifRotation!=0){android.graphics.Matrix m=new android.graphics.Matrix();m.postRotate(exifRotation);android.graphics.Bitmap rotated=android.graphics.Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);if(rotated!=bitmap)bitmap.recycle();bitmap=rotated;}
      if(bitmap==null){toolImageInfoText="图片解码失败";ui.post(()->{refreshToolImageInfo();showNotice("图片解码失败",true);});return;}
      long original=0;try{android.content.res.AssetFileDescriptor fd=getContentResolver().openAssetFileDescriptor(uri,"r");if(fd!=null){original=fd.getLength();fd.close();}}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}
      java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();
      int usedQuality=toolQuality;
      if(toolTargetSizeKb>0){
        // v1.13.0 精修20：目标体积模式——质量二分（6 轮内逼近目标 KB 上限）
        int lo=10,hi=95;usedQuality=70;byte[] best=null;
        while(lo<=hi){
          int q=(lo+hi)/2;buffer.reset();bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,q,buffer);
          int sizeKb=buffer.size()/1024;
          if(sizeKb<=toolTargetSizeKb){best=buffer.toByteArray();usedQuality=q;lo=q+1;}else hi=q-1;
        }
        byte[] bytesFinal=best!=null?best:buffer.toByteArray();
        String saved=saveToolImage(bytesFinal);
        bitmap.recycle();
        final String infoT="原始 "+toolBytes(original)+" → 压缩后 "+toolBytes(bytesFinal.length)+"（目标 "+toolTargetSizeKb+"KB，质量 "+usedQuality+"%，"+bitmapRotateNote(exifRotation)+bounds.outWidth+"×"+bounds.outHeight+"）\n已保存："+saved;
        toolImageInfoText=infoT;ui.post(()->{refreshToolImageInfo();showNotice("压缩完成",false);});
        return;
      }
      bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG,toolQuality,buffer);
      byte[] bytes=buffer.toByteArray();String saved=saveToolImage(bytes);
      bitmap.recycle();
      final String info="原始 "+toolBytes(original)+" → 压缩后 "+toolBytes(bytes.length)+"（质量 "+toolQuality+"%，"+bitmapRotateNote(exifRotation)+bounds.outWidth+"×"+bounds.outHeight+"）\n已保存："+saved;
      toolImageInfoText=info;ui.post(()->{refreshToolImageInfo();showNotice("压缩完成",false);});
    }catch(Exception error){ui.post(()->showNotice("压缩失败："+friendlyError(error),true));}
  }
  String bitmapRotateNote(int rotation){return rotation==0?"":"已按 EXIF 转正 "+rotation+"°，";}
  String saveToolImage(byte[] bytes){return saveToolImage(bytes,false);}
  String saveToolImage(byte[] bytes,boolean png){
    String name="dfwx_"+System.currentTimeMillis()+(png?".png":".jpg");
    if(Build.VERSION.SDK_INT>=29){
      android.content.ContentValues values=new android.content.ContentValues();
      values.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,name);
      values.put(android.provider.MediaStore.Images.Media.MIME_TYPE,png?"image/png":"image/jpeg");
      values.put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,android.os.Environment.DIRECTORY_PICTURES+"/东方无限");
      Uri target=getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);
      if(target!=null){try(java.io.OutputStream out=getContentResolver().openOutputStream(target)){out.write(bytes);}catch(Exception error){return"保存失败";}return"相册/Pictures/东方无限/"+name;}
      return"保存失败";
    }
    try{
      java.io.File dir=new java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES),"东方无限");
      if(!dir.exists())dir.mkdirs();
      java.io.File file=new java.io.File(dir,name);
      try(java.io.FileOutputStream out=new java.io.FileOutputStream(file)){out.write(bytes);}
      return file.getAbsolutePath();
    }catch(Exception error){java.io.File fallback=new java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES),name);try(java.io.FileOutputStream out=new java.io.FileOutputStream(fallback)){out.write(bytes);}catch(Exception ignored){return"保存失败";}return fallback.getAbsolutePath();}
  }
  void refreshToolImageInfo(){if(pageKind==6&&toolHost!=null&&toolHost.toolBody!=null){TextView meta=toolHost.toolBody.findViewWithTag("img-info");if(meta!=null)meta.setText(toolImageInfoText);}}
  public String toolImageInfoText(){return toolImageInfoText==null||toolImageInfoText.isEmpty()?"未选择图片":toolImageInfoText;}
  /** 简易画板：手指绘制，撤销/重做/橡皮擦/笔宽，存 PNG 到相册 */
  private TextView sketchChip(LinearLayout parent,String label,boolean selected){
    TextView chip=text(label,12,selected?BG:TEXT);
    chip.setBackground(filterRipple(solidShape(selected?PRIMARY:SURFACE,14)));
    chip.setPadding(dp(14),0,dp(14),0);chip.setMinHeight(dp(34));chip.setGravity(Gravity.CENTER);
    parent.addView(chip,new LinearLayout.LayoutParams(-2,dp(34)));
    return chip;}
  private void styleSketchChip(TextView chip,boolean selected){
    chip.setBackground(filterRipple(solidShape(selected?PRIMARY:SURFACE,14)));
    chip.setTextColor(selected?BG:TEXT);}
  public void toolHostSketch(LinearLayout body){
    SketchView sketch=new SketchView(this);sketch.setBackground(solidShape(SURFACE,16));sketch.setMinimumHeight(dp(300));
    body.addView(sketch,new LinearLayout.LayoutParams(-1,dp(320)));
    LinearLayout mode=new LinearLayout(this);mode.setGravity(Gravity.CENTER_VERTICAL);mode.setPadding(0,dp(10),0,0);
    LinearLayout.LayoutParams mlp=new LinearLayout.LayoutParams(-2,-2);mlp.rightMargin=dp(12);mode.addView(text("模式",12,TEXT()),mlp);
    TextView[] modeChips=new TextView[2];String[] mlabels={"画笔","橡皮擦"};
    for(int i=0;i<2;i++){final int idx=i;modeChips[i]=sketchChip(mode,mlabels[i],idx==0);modeChips[i].setOnClickListener(v->{sketch.setEraser(idx==1);for(int j=0;j<2;j++)styleSketchChip(modeChips[j],idx==j);});}
    body.addView(mode,new LinearLayout.LayoutParams(-1,dp(40)));
    LinearLayout widthRow=new LinearLayout(this);widthRow.setGravity(Gravity.CENTER_VERTICAL);widthRow.setPadding(0,dp(8),0,0);
    LinearLayout.LayoutParams wlp=new LinearLayout.LayoutParams(-2,-2);wlp.rightMargin=dp(12);widthRow.addView(text("笔宽",12,TEXT()),wlp);
    float[] widths={6f,12f,20f};String[] wlabels={"细","中","粗"};
    TextView[] wchips=new TextView[3];
    for(int i=0;i<3;i++){final int idx=i;wchips[i]=sketchChip(widthRow,wlabels[i],idx==0);wchips[i].setOnClickListener(v->{sketch.setPaintWidth(widths[idx]);for(int j=0;j<3;j++)styleSketchChip(wchips[j],j==idx);});}
    LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(10),0,0);
    int[] palette={0xFFFFFFFF,0xFF000000,0xFFEF4444,0xFFF59E0B,0xFF10B981,0xFF3B82F6,0xFF8B5CF6};
    for(int color:palette){View dot=new View(this);GradientDrawable circle=solidShape(color,14);circle.setStroke(dp(1),DIV);dot.setBackground(circle);dot.setOnClickListener(v->{sketch.setPaintColor(color);styleSketchChip(modeChips[0],true);styleSketchChip(modeChips[1],false);});actions.addView(dot,new LinearLayout.LayoutParams(dp(30),dp(30)));}
    LinearLayout.LayoutParams dotParams=new LinearLayout.LayoutParams(dp(30),dp(30));dotParams.setMargins(dp(6),0,dp(6),0);
    for(int i=1;i<actions.getChildCount();i++)actions.getChildAt(i).setLayoutParams(dotParams);
    body.addView(actions,new LinearLayout.LayoutParams(-1,dp(44)));
    LinearLayout run=new LinearLayout(this);run.setGravity(Gravity.CENTER_VERTICAL);run.setPadding(0,dp(8),0,0);
    Button undo=new Button(this);undo.setText("撤销");undo.setTextColor(MUTED);undo.setTextSize(12);undo.setAllCaps(false);undo.setBackground(filterRipple(solidShape(SURFACE,14)));undo.setMinWidth(0);undo.setMinimumWidth(0);undo.setMinHeight(dp(44));
    undo.setOnClickListener(v->sketch.undo());
    run.addView(undo,new LinearLayout.LayoutParams(-2,dp(44)));
    Button redo=new Button(this);redo.setText("重做");redo.setTextColor(MUTED);redo.setTextSize(12);redo.setAllCaps(false);redo.setBackground(filterRipple(solidShape(SURFACE,14)));redo.setMinWidth(0);redo.setMinimumWidth(0);redo.setMinHeight(dp(44));
    LinearLayout.LayoutParams redoParams=new LinearLayout.LayoutParams(-2,dp(44));redoParams.setMargins(dp(8),0,0,0);redo.setLayoutParams(redoParams);
    redo.setOnClickListener(v->sketch.redo());
    run.addView(redo,redoParams);
    Button clear=new Button(this);clear.setText("清空");clear.setTextColor(MUTED);clear.setTextSize(12);clear.setAllCaps(false);clear.setBackground(filterRipple(solidShape(SURFACE,14)));clear.setMinWidth(0);clear.setMinimumWidth(0);clear.setMinHeight(dp(44));
    LinearLayout.LayoutParams clearParams=new LinearLayout.LayoutParams(-2,dp(44));clearParams.setMargins(dp(8),0,0,0);clear.setLayoutParams(clearParams);
    clear.setOnClickListener(v->sketch.clearAll());
    run.addView(clear,clearParams);
    Button save=new Button(this);save.setText("保存到相册");save.setTextColor(BG);save.setTextSize(12);save.setAllCaps(false);save.setBackground(filterRipple(solidShape(PRIMARY,14)));save.setMinWidth(0);save.setMinimumWidth(0);save.setMinHeight(dp(44));
    LinearLayout.LayoutParams saveParams=new LinearLayout.LayoutParams(-2,dp(44));saveParams.setMargins(dp(8),0,0,0);save.setLayoutParams(saveParams);
    save.setOnClickListener(v->{byte[] png=sketch.exportPng();if(png==null){showNotice("画布为空",true);return;}String saved=saveToolImage(png,true);showNotice("已保存："+saved,false);});
    run.addView(save,saveParams);
    body.addView(run,new LinearLayout.LayoutParams(-1,dp(44)));
  }
  static final class SketchView extends View{
    static final class Stroke{final int color;final float width;final android.graphics.Path path;Stroke(int c,float w,android.graphics.Path p){color=c;width=w;path=p;}}
    final List<Stroke> strokes=new ArrayList<>();
    final java.util.ArrayDeque<List<Stroke>> undoStack=new java.util.ArrayDeque<>();
    final java.util.ArrayDeque<List<Stroke>> redoStack=new java.util.ArrayDeque<>();
    final android.graphics.Paint stroke=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    android.graphics.Path current;int paintColor=0xFFFFFFFF;float paintWidth=6f;boolean eraser=false;
    SketchView(android.content.Context context){super(context);stroke.setStyle(android.graphics.Paint.Style.STROKE);stroke.setStrokeCap(android.graphics.Paint.Cap.ROUND);stroke.setStrokeJoin(android.graphics.Paint.Join.ROUND);stroke.setStrokeWidth(paintWidth);setContentDescription("自由绘制画布，可手指画线");}
    int surfaceColor(){return ThemeEngine.active(getContext()).surface;}
    void snapshot(){undoStack.push(new ArrayList<>(strokes));while(undoStack.size()>80)undoStack.removeLast();redoStack.clear();}
    void setPaintColor(int color){paintColor=color;eraser=false;invalidate();}
    void setPaintWidth(float w){paintWidth=w;invalidate();}
    void setEraser(boolean e){eraser=e;invalidate();}
    void undo(){if(strokes.isEmpty())return;redoStack.push(new ArrayList<>(strokes));strokes.remove(strokes.size()-1);invalidate();}
    void redo(){if(redoStack.isEmpty())return;undoStack.push(new ArrayList<>(strokes));strokes.addAll(redoStack.pollLast());invalidate();}
    void clearAll(){if(strokes.isEmpty()&&current==null)return;snapshot();strokes.clear();current=null;invalidate();}
    void setFixedSize(int width,int height){invalidate();}
    @Override protected void onDraw(android.graphics.Canvas c){super.onDraw(c);
      c.drawColor(surfaceColor());
      for(Stroke s:strokes){stroke.setColor(s.color);stroke.setStrokeWidth(s.width);c.drawPath(s.path,stroke);}
      if(current!=null){stroke.setColor(eraser?surfaceColor():paintColor);stroke.setStrokeWidth(eraser?paintWidth*3f+12f:paintWidth);c.drawPath(current,stroke);}
    }
    @Override public boolean onTouchEvent(android.view.MotionEvent event){float x=event.getX(),y=event.getY();switch(event.getAction()){
      case android.view.MotionEvent.ACTION_DOWN:{current=new android.graphics.Path();current.moveTo(x,y);break;}
      case android.view.MotionEvent.ACTION_MOVE:current.lineTo(x,y);break;
      case android.view.MotionEvent.ACTION_UP:{current.lineTo(x,y);snapshot();strokes.add(new Stroke(eraser?surfaceColor():paintColor,eraser?paintWidth*3f+12f:paintWidth,current));current=null;invalidate();return true;}
      default:return false;}invalidate();return true;}
    byte[] exportPng(){int w=getWidth(),h=getHeight();if(w<=0||h<=0)return null;
      Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);android.graphics.Canvas c=new android.graphics.Canvas(out);c.drawColor(surfaceColor());
      android.graphics.Paint p=new android.graphics.Paint(stroke);
      for(Stroke s:strokes){p.setColor(s.color);p.setStrokeWidth(s.width);c.drawPath(s.path,p);}
      java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream();out.compress(Bitmap.CompressFormat.PNG,100,buffer);return buffer.toByteArray();}
  }
  /** 直尺：按屏幕物理宽度标定厘米刻度 */
  public void toolHostRuler(LinearLayout body){
    RulerView ruler=new RulerView(this);
    body.addView(ruler,new LinearLayout.LayoutParams(-1,dp(220)));
    android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
    double physicalWidthMm=metrics.widthPixels/metrics.xdpi*25.4;
    TextView info=text("当前屏幕可见宽度 ≈ "+String.format(java.util.Locale.US,"%.1f",physicalWidthMm)+" mm（按设备 X DPI 标定，不同机型有±2%误差）",12,MUTED);
    info.setPadding(0,dp(10),0,0);body.addView(info,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);actions.setPadding(0,dp(8),0,0);
    Button invert=new Button(this);invert.setText("白底/黑底");invert.setTextColor(PRIMARY);invert.setTextSize(12);invert.setAllCaps(false);invert.setBackground(filterRipple(solidShape(SURFACE,14)));invert.setMinWidth(0);invert.setMinimumWidth(0);invert.setMinHeight(dp(44));
    invert.setOnClickListener(v->{ruler.invert();});
    actions.addView(invert,new LinearLayout.LayoutParams(-2,dp(44)));
    body.addView(actions,new LinearLayout.LayoutParams(-1,dp(44)));
  }
  static final class RulerView extends View{
    boolean dark=true;
    RulerView(android.content.Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void invert(){dark=!dark;invalidate();}
    @Override protected void onDraw(android.graphics.Canvas c){
      super.onDraw(c);
      android.util.DisplayMetrics metrics=getResources().getDisplayMetrics();
      float pxPerCm=metrics.xdpi/2.54f;
      ThemeEngine.Design d=ThemeEngine.active(getContext());
      android.graphics.Paint bgPaint=new android.graphics.Paint();bgPaint.setColor(dark?d.surface:d.text);c.drawPaint(bgPaint);
      android.graphics.Paint line=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);line.setColor(dark?d.text:d.surface);line.setStrokeWidth(dp(1));
      android.graphics.Paint label=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);label.setColor(d.primary);label.setTextSize(dp(11));
      int count=(int)(getWidth()/pxPerCm)+1;
      for(int i=0;i<=count;i++){
        float x=i*pxPerCm;
        c.drawLine(x,0,x,dp(24),line);
        for(int tick=1;tick<5;tick++){float tx=x+pxPerCm*tick/5;if(tx>getWidth())break;c.drawLine(tx,0,tx,dp(10),line);}
        if(i>0)c.drawText(String.valueOf(i),x-dp(4),dp(44),label);
      }
      c.drawLine(0,dp(2),getWidth(),dp(2),line);
    }
    public int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
  }
  /** 水平仪气泡视图：中心十字 + 气泡随重力偏移 */
  static final class LevelView extends View{
    float bubbleX,bubbleY;
    final android.graphics.Paint ring=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    final android.graphics.Paint bubble=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
    final android.graphics.Paint cross=new android.graphics.Paint();
    LevelView(android.content.Context context){super(context);ThemeEngine.Design token=ThemeEngine.active(context);ring.setColor(token.surface2);ring.setStyle(android.graphics.Paint.Style.FILL);bubble.setColor(token.primary);cross.setColor(token.muted);cross.setStrokeWidth(dp(1));setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void setBubble(float gx,float gy){
      float max=getWidth()/2f-dp(20);
      float nx=clamp(gx/9.81f),ny=clamp(gy/9.81f);
      bubbleX=nx*max;bubbleY=ny*max;invalidate();
    }
    static float clamp(float v){return v>1?1:v<-1?-1:v;}
    @Override protected void onDraw(android.graphics.Canvas c){
      super.onDraw(c);
      float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-dp(8);
      c.drawCircle(cx,cy,r,ring);
      c.drawLine(cx-r,cy,cx+r,cy,cross);c.drawLine(cx,cy-r,cx,cy+r,cross);
      c.drawCircle(cx+bubbleX,cy+bubbleY,dp(14),bubble);
    }
    public int dp(int v){return(int)(v*getResources().getDisplayMetrics().density+.5f);}
  }
  /** 水平仪：重力传感器气泡 */
  public void toolHostLevel(LinearLayout body){
    LevelView level=new LevelView(this);
    body.addView(level,new LinearLayout.LayoutParams(-1,dp(260)));
    TextView readout=text("将手机放平，气泡居中即水平",12,MUTED);readout.setGravity(Gravity.CENTER);readout.setPadding(0,dp(10),0,0);
    body.addView(readout,new LinearLayout.LayoutParams(-1,-2));
    levelViewRef=new java.lang.ref.WeakReference<>(level);
    android.hardware.SensorManager sensors=(android.hardware.SensorManager)getSystemService(SENSOR_SERVICE);
    android.hardware.Sensor gravity=sensors==null?null:sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GRAVITY);
    if(gravity==null){readout.setText("此设备没有重力传感器");return;}
    android.hardware.SensorEventListener listener=new android.hardware.SensorEventListener(){
      public void onSensorChanged(android.hardware.SensorEvent event){
        float x=event.values[0],y=event.values[1];
        level.setBubble(x,y);
        double tilt=Math.sqrt(x*(double)x+y*(double)y);
        readout.setText(String.format(java.util.Locale.US,"倾角 %.1f°",Math.toDegrees(Math.atan2(tilt,9.81))));
      }
      public void onAccuracyChanged(android.hardware.Sensor sensor,int accuracy){}
    };
    sensors.registerListener(listener,gravity,android.hardware.SensorManager.SENSOR_DELAY_UI);
    levelCleanup=()->sensors.unregisterListener(listener);// 独立清理槽（v1.2.2：不再复用手电筒槽位，修复互相覆盖）
  }
  public void pickToolImage(){try{Intent intent=new Intent(Intent.ACTION_GET_CONTENT);intent.setType("image/*");startActivityForResult(Intent.createChooser(intent,"选择图片"),TOOL_PICK_IMAGE);}catch(Exception error){showNotice("没有可用的图片选择器",true);}}
  // v1.18.0 取色器专用选图：带回调（压缩流的 TOOL_PICK_IMAGE 回流硬编码 runImageCompress，不能复用）
  public void pickToolColorImage(Runnable onPicked){pendingToolColorImagePick=onPicked;try{Intent intent=new Intent(Intent.ACTION_GET_CONTENT);intent.setType("image/*");startActivityForResult(Intent.createChooser(intent,"选择图片"),TOOL_PICK_IMAGE2);}catch(Exception error){pendingToolColorImagePick=null;showNotice("没有可用的图片选择器",true);}}
  // v1.18.0 分贝仪麦克风权限：授予后回调启动测量
  public void requestToolMicPermission(Runnable onGranted){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){pendingMicAction=onGranted;requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},TOOL_MIC_PERMISSION);return;}onGranted.run();}
  /** v1.22.0 AI 首启权限一次性引导（用户要求）：未引导过且存在未授权限时，串行走系统弹窗；无论同意/拒绝都放行进 AI 页，功能入口再按需提示 */
  void maybeGuideAiPermissions(Runnable proceed){
    if(AiPermissionGate.guided(this)){proceed.run();return;}
    java.util.List<String> pending=AiPermissionGate.pending(this);
    if(pending.isEmpty()){AiPermissionGate.markGuided(this);proceed.run();return;}
    showAiPermissionIntro(pending,proceed);
  }
  /** 引导说明对话框：一次说清"接下来会连续弹 N 个系统授权"，让用户有心理预期，再逐个请求 */
  void showAiPermissionIntro(java.util.List<String> pending,Runnable proceed){
    LinearLayout body=new LinearLayout(this);body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(4),dp(4),dp(4),dp(2));
    TextView lead=text("AI 对话需要以下权限才能完整使用，接下来会依次弹出系统授权。同意后即可满血使用，之后不再打扰。",14,TEXT);
    lead.setLineSpacing(dp(4),1f);
    body.addView(lead,new LinearLayout.LayoutParams(-1,-2));
    for(String permission:pending){
      String[] info=AiPermissionGate.rationale(permission);
      LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
      TextView name=text("· "+info[0],14,PRIMARY);
      name.setTypeface(AppFonts.bold(this));
      row.addView(name,new LinearLayout.LayoutParams(-2,-2));
      TextView why=text(info[1],12,MUTED);
      why.setPadding(dp(8),dp(3),0,0);
      row.addView(why,new LinearLayout.LayoutParams(0,-2,1));
      LinearLayout.LayoutParams rowLp=new LinearLayout.LayoutParams(-1,-2);rowLp.topMargin=dp(10);
      body.addView(row,rowLp);
    }
    AlertDialog intro=new AlertDialog.Builder(this).setTitle("AI 权限授权").setView(body)
        .setPositiveButton("开始授权",(dialog,which)->requestNextAiPermission(new java.util.ArrayList<>(pending),0,proceed))
        .setNegativeButton("暂不",(dialog,which)->{AiPermissionGate.markGuided(this);proceed.run();}).create();
    showRounded(intro);
  }
  /** 串行请求：一个一个弹（系统不支持一次多弹同组时更稳），全部走完才 markGuided + 放行 */
  void requestNextAiPermission(java.util.List<String> pending,int index,Runnable proceed){
    if(index>=pending.size()){AiPermissionGate.markGuided(this);proceed.run();return;}
    pendingAiPermissions=pending;pendingAiIndex=index;pendingAiProceed=proceed;
    requestPermissions(new String[]{pending.get(index)},AI_PERMISSION);
  }
  public void runImageCompressPending(){if(toolImageUri!=null)runImageCompress(toolImageUri);}
  public void startScreenTest(){toolBackStack.push("__screen_test__");showScreenTestOverlay();}
  void showScreenTestOverlay(){
    // v1.13.0 精修22：加 25%/75% 灰阶（查灰阶断层/banding 更细），提示行显示当前颜色名
    final String[] names={"黑","白","红","绿","蓝","50% 灰","25% 灰","75% 灰"};
    final int[] colors={0xFF000000,0xFFFFFFFF,0xFFFF0000,0xFF00FF00,0xFF0000FF,0xFF808080,0xFF404040,0xFFC0C0C0};
    final int[] idx={0};
    final FrameLayout overlay=new FrameLayout(this);overlay.setBackgroundColor(colors[0]);
    TextView hint=text("点击切换颜色 · 返回退出（当前："+names[0]+"）",12,Color.argb(150,255,255,255));hint.setGravity(Gravity.CENTER);
    FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hp.bottomMargin=dp(48);
    overlay.addView(hint,hp);
    overlay.setOnClickListener(v->{idx[0]=(idx[0]+1)%colors.length;overlay.setBackgroundColor(colors[idx[0]]);hint.setText("点击切换颜色 · 返回退出（当前："+names[idx[0]]+"）");});
    setContentView(overlay);
    // 复审3:恢复主视图后必须经 primaryBase(5) 完整重建 shell/root/pageFrame——手工拼 composePrimaryShell 会与 basePage 的 root 重建顺序脱节
    systemBackAction=()->{systemBackAction=null;toolBackStack.poll();pageDirection=-1;popToolBack();};
  }
  void enterPlaceholderMotion(View panel){if(!motionEnabled())return;panel.setAlpha(0f);panel.setTranslationY(dp(14));panel.animate().alpha(1f).translationY(0).setDuration(200).setInterpolator(standardEase()).start();}
  View placeholderPanel(int icon,String title,String subtitle){LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.CENTER);GradientDrawable panelBg=new GradientDrawable();panelBg.setColor(SURFACE);panelBg.setCornerRadius(dp(24));panelBg.setStroke(dp(1),ThemeEngine.tint(PRIMARY,64));panel.setBackground(panelBg);panel.setPadding(dp(26),dp(32),dp(26),dp(32));ImageView image=new ImageView(this);image.setImageResource(icon);image.setColorFilter(PRIMARY);image.setBackground(solidShape(ThemeEngine.tint(PRIMARY,26),22));image.setPadding(dp(14),dp(14),dp(14),dp(14));panel.addView(image,new LinearLayout.LayoutParams(dp(64),dp(64)));TextView heading=text(title,18,TEXT);heading.setTypeface(AppFonts.bold(this));heading.setGravity(Gravity.CENTER);heading.setPadding(0,dp(16),0,0);panel.addView(heading,new LinearLayout.LayoutParams(-2,-2));TextView caption=text(subtitle,13,MUTED);caption.setGravity(Gravity.CENTER);caption.setPadding(0,dp(8),0,0);caption.setLineSpacing(dp(3),1f);panel.addView(caption,new LinearLayout.LayoutParams(-2,-2));LinearLayout wrap=new LinearLayout(this);wrap.setOrientation(LinearLayout.VERTICAL);wrap.setGravity(Gravity.CENTER);wrap.setPadding(dp(8),0,dp(8),dp(8));wrap.addView(panel,new LinearLayout.LayoutParams(-2,-2));return wrap;}

  boolean silentInstallPreference(){return getSharedPreferences("adb_install_settings-v1",MODE_PRIVATE).getBoolean("silent_install",false);}
  void setSilentInstallPreference(boolean enabled){getSharedPreferences("adb_install_settings-v1",MODE_PRIVATE).edit().putBoolean("silent_install",enabled).apply();}
    int installParallelism(){int value=getSharedPreferences("adb_install_settings-v1",MODE_PRIVATE).getInt("parallel_installs",DEFAULT_INSTALL_PARALLELISM);return Math.max(0,value);}
    void setInstallParallelism(int value){getSharedPreferences("adb_install_settings-v1",MODE_PRIVATE).edit().putInt("parallel_installs",Math.max(0,value)).apply();}
  void onAdbShellStateChanged(AdbShellManager.Snapshot snapshot){runOnUiThread(()->{boolean ready=snapshot.ready();if(adbPermissionState!=null){adbPermissionState.setText(snapshot.title+"\n"+snapshot.detail);adbPermissionState.setTextColor(ready?PRIMARY:MUTED);}if(adbSilentInstallSwitch!=null){adbSilentInstallSwitch.setEnabled(ready);adbSilentInstallSwitch.setChecked(ready&&silentInstallPreference());}});}
  LinearLayout buildAdbPermissionCard(){
    AdbShellManager.Snapshot snapshot=adbShell.snapshot();boolean ready=snapshot.ready();
    LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(10),dp(10),dp(10));GradientDrawable background=solidShape(SET_HIGH,16);background.setStroke(dp(1),ready?PRIMARY:SET_STROKE);card.setBackground(background);
    LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("ADB Shell 权限",16,TEXT);heading.addView(title,new LinearLayout.LayoutParams(0,dp(34),1));Button manage=toolbarTextButton(ready?"管理":"申请");manage.setOnClickListener(v->showAdbPermissionDialog());heading.addView(manage,new LinearLayout.LayoutParams(dp(64),dp(42)));card.addView(heading,new LinearLayout.LayoutParams(-1,dp(44)));
    adbPermissionState=text(snapshot.title+"\n"+snapshot.detail,11,ready?PRIMARY:MUTED);adbPermissionState.setMaxLines(3);adbPermissionState.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);adbPermissionState.setClickable(true);adbPermissionState.setFocusable(true);adbPermissionState.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));adbPermissionState.setOnClickListener(v->showAdbPermissionDialog());adbPermissionState.setContentDescription("ADB Shell 状态："+snapshot.title+"。"+snapshot.detail+"。点击申请或管理");card.addView(adbPermissionState,new LinearLayout.LayoutParams(-1,dp(58)));
    LinearLayout oneClick=settingsRowShell();ImageView installIcon=settingsLeadingIcon(R.drawable.ic_install);oneClick.addView(installIcon,settingsIconBox());TextView installTitle=settingsRowTitle("静默一键安装");oneClick.addView(installTitle,new LinearLayout.LayoutParams(0,-1,1));adbSilentInstallSwitch=new LumaSwitch(this);adbSilentInstallSwitch.setChecked(ready&&silentInstallPreference());adbSilentInstallSwitch.setEnabled(ready);adbSilentInstallSwitch.setContentDescription("无安装界面静默安装下载的 APK");adbSilentInstallSwitch.setOnCheckedChangeListener((button,value)->{if(!adbShell.ready()){if(value)showAdbPermissionDialog();if(button.isChecked())button.setChecked(false);return;}setSilentInstallPreference(value);oneClick.setContentDescription("静默一键安装，"+(value?"已开启":"已关闭"));});oneClick.addView(adbSilentInstallSwitch,new LinearLayout.LayoutParams(-2,dp(48)));oneClick.setClickable(true);oneClick.setFocusable(true);oneClick.setBackground(settingsPress());applePressScale(oneClick);oneClick.setOnClickListener(v->{if(!adbShell.ready()){showAdbPermissionDialog();return;}adbSilentInstallSwitch.setChecked(!adbSilentInstallSwitch.isChecked());});oneClick.setContentDescription("静默一键安装，"+(adbSilentInstallSwitch.isChecked()?"已开启":"已关闭"));card.addView(oneClick,new LinearLayout.LayoutParams(-1,settingsRowHeight()));
    card.setContentDescription("ADB Shell 权限与静默安装设置；安装线程在统一调节区设置");return card;
  }
  void showAdbPermissionDialog(){AdbShellManager.Snapshot snapshot=adbShell.snapshot();String message=snapshot.detail+"\n\n自动申请会优先连接已运行的 Shizuku / Sui；若服务未运行，Android 11 及以上会打开无线调试设置。USB 调试需要电脑先启动 Shizuku。所有方式都需要用户在系统或 Shizuku 中确认，应用不会绕过授权。";AlertDialog dialog=new AlertDialog.Builder(this).setTitle("ADB Shell · "+snapshot.title).setMessage(message).setNegativeButton("关闭",null).setNeutralButton("调试方式",(d,w)->showAdbDebugMethods()).setPositiveButton(snapshot.ready()?"刷新状态":"自动申请",null).create();dialog.setOnShowListener(ignored->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{if(snapshot.ready()){adbShell.refresh();dialog.dismiss();return;}if(adbShell.requestPermission()){showNotice("已发起 Shizuku 权限申请",false);dialog.dismiss();return;}dialog.dismiss();if(snapshot.state==AdbShellManager.State.NOT_INSTALLED)openShizukuInstall();else if(snapshot.state==AdbShellManager.State.NOT_RUNNING){if(!openShizukuManager()&&Build.VERSION.SDK_INT>=30)openWirelessDebugSettings();else showNotice("请在 Shizuku 中用无线调试、USB 调试或 root 启动服务",false);}else showNotice("请在 Shizuku 的应用管理中允许本应用",true);}));showRounded(dialog);}
  void showAdbDebugMethods(){String[] methods={"打开 Shizuku","无线调试（Android 11+）","USB 调试 / 开发者选项","安装或更新 Shizuku","刷新权限状态"};AlertDialog picker=new AlertDialog.Builder(this).setTitle("选择 ADB Shell 获取方式").setItems(methods,(dialog,index)->{switch(index){case 0:if(!openShizukuManager())openShizukuInstall();break;case 1:openWirelessDebugSettings();break;case 2:openDeveloperSettings();break;case 3:openShizukuInstall();break;case 4:adbShell.refresh();break;default:break;}}).setNegativeButton("取消",null).create();showRounded(picker);}
  boolean openShizukuManager(){try{Intent launch=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(launch==null)return false;startActivity(launch);return true;}catch(Exception error){return false;}}
  void openShizukuInstall(){try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/")));}catch(Exception error){showNotice("无法打开 Shizuku 官方下载页",true);}}
  void openWirelessDebugSettings(){if(Build.VERSION.SDK_INT<30){showNotice("无线调试需要 Android 11 或更高版本",true);openDeveloperSettings();return;}try{startActivity(new Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS"));}catch(Exception error){openDeveloperSettings();}}
  void openDeveloperSettings(){try{startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));}catch(Exception error){showNotice("无法打开开发者选项",true);}}
  LinearLayout buildSearchSettingsPanel(){
    int[] total={0},selectedConcurrency={Math.max(0,sessionSearchConcurrency)};LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(0,dp(2),0,dp(12));TextView concurrencyLabel=text("",14,TEXT);LumaSlider concurrencyBar=new LumaSlider(this);updateSearchConcurrencyLimit("全部",sessionSearchMode,total,selectedConcurrency,concurrencyBar,concurrencyLabel);
    TextView delayLabel=text(batchTimeoutText(sessionSearchBatchSeconds),14,TEXT);delayLabel.setContentDescription(batchTimeoutDescription(sessionSearchBatchSeconds));LumaSlider delayBar=new LumaSlider(this);delayBar.setMax(60);delayBar.setProgressValue(batchTimeoutProgress(sessionSearchBatchSeconds),false);delayBar.setContentDescription("调整慢源让位时间");concurrencyBar.setContentDescription("调整同时活跃源数");int[] pageValues={1,3,5,10,20,50,100,0};int pageIndex=settingIndex(pageValues,sessionSearchMaxPages,pageValues.length-1);TextView pageLabel=text("单源最高展开页数："+(sessionSearchMaxPages==0?"无限":sessionSearchMaxPages),14,TEXT);LumaSlider pageBar=new LumaSlider(this);pageBar.setMax(pageValues.length-1);pageBar.setProgressValue(pageIndex,false);pageBar.setContentDescription("调整单源最高展开页数");LinearLayout pageTicks=sliderTicks(pageValues);int[] viewRates={50,100,200,500,1000,2000,0};int viewRateIndex=settingIndex(viewRates,sessionSearchViewRate,0);TextView viewRateLabel=text(searchViewRateText(sessionSearchViewRate),14,TEXT);LumaSlider viewRateBar=new LumaSlider(this);viewRateBar.setMax(viewRates.length-1);viewRateBar.setProgressValue(viewRateIndex,false);viewRateBar.setContentDescription("调整搜索视图创建上限");LinearLayout viewRateTicks=sliderTicks(viewRates);
    panel.addView(concurrencyLabel,new LinearLayout.LayoutParams(-1,dp(34)));panel.addView(concurrencyBar,new LinearLayout.LayoutParams(-1,dp(56)));panel.addView(delayLabel,new LinearLayout.LayoutParams(-1,dp(34)));panel.addView(delayBar,new LinearLayout.LayoutParams(-1,dp(56)));panel.addView(pageLabel,new LinearLayout.LayoutParams(-1,dp(34)));panel.addView(pageBar,new LinearLayout.LayoutParams(-1,dp(56)));panel.addView(pageTicks,new LinearLayout.LayoutParams(-1,dp(24)));panel.addView(viewRateLabel,new LinearLayout.LayoutParams(-1,dp(34)));panel.addView(viewRateBar,new LinearLayout.LayoutParams(-1,dp(56)));panel.addView(viewRateTicks,new LinearLayout.LayoutParams(-1,dp(24)));
    concurrencyBar.setOnChangeListener((value,user)->{selectedConcurrency[0]=total[0]==0?0:(value>=total[0]?0:value+1);int active=selectedConcurrency[0];concurrencyLabel.setText(concurrencyText(active,total[0]));concurrencyLabel.setContentDescription(concurrencyDescription(active,total[0]));sessionSearchConcurrency=active;if(user)persistSearchSettings();});delayBar.setOnChangeListener((value,user)->{int seconds=batchTimeoutSeconds(value);sessionSearchBatchSeconds=seconds;delayLabel.setText(batchTimeoutText(seconds));delayLabel.setContentDescription(batchTimeoutDescription(seconds));if(user)persistSearchSettings();});pageBar.setOnChangeListener((value,user)->{sessionSearchMaxPages=pageValues[Math.max(0,Math.min(pageValues.length-1,value))];pageLabel.setText("单源最高展开页数："+(sessionSearchMaxPages==0?"无限":sessionSearchMaxPages));pageLabel.setContentDescription("单源最高展开页数，当前 "+(sessionSearchMaxPages==0?"无限":sessionSearchMaxPages));if(user)persistSearchSettings();});viewRateBar.setOnChangeListener((value,user)->{sessionSearchViewRate=viewRates[Math.max(0,Math.min(viewRates.length-1,value))];viewRateLabel.setText(searchViewRateText(sessionSearchViewRate));viewRateLabel.setContentDescription(searchViewRateText(sessionSearchViewRate));if(user)persistSearchSettings();});return panel;
  }
  boolean fileListApiSelected(){return (sessionFileListModeMask&Models.SearchOptions.MASK_API)!=0;}
  boolean fileListDirectorySelected(){return (sessionFileListModeMask&Models.SearchOptions.MASK_DIRECTORY)!=0;}
  boolean fileListIndexSelected(){return (sessionFileListModeMask&Models.SearchOptions.MASK_INDEX)!=0;}
  boolean searchApiSelected(){return (sessionSearchModeMask&Models.SearchOptions.MASK_API)!=0;}
  boolean searchDirectorySelected(){return (sessionSearchModeMask&Models.SearchOptions.MASK_DIRECTORY)!=0;}
  boolean searchIndexSelected(){return (sessionSearchModeMask&Models.SearchOptions.MASK_INDEX)!=0;}
  boolean anyIndexSelected(){return fileListIndexSelected()||searchIndexSelected();}
  boolean fuzzyFolderListEnabled(){return sessionSearchFuzzyMatching;}
  boolean fuzzyDirectoryEnabled(){return sessionSearchFuzzyMatching;}
  boolean fuzzyIndexEnabled(){return sessionSearchFuzzyMatching;}
  String maskLabel(int mask,String[] labels,int[] values,String empty){List<String> parts=new ArrayList<>();for(int i=0;i<labels.length;i++)if((mask&values[i])!=0)parts.add(labels[i]);return parts.isEmpty()?empty:String.join("、",parts);}
  String backendMaskLabel(int mask){return maskLabel(mask,new String[]{"API","目录","索引"},new int[]{Models.SearchOptions.MASK_API,Models.SearchOptions.MASK_DIRECTORY,Models.SearchOptions.MASK_INDEX},"目录");}
        void applyUserAgentSettings(){LanzouCore.setUserAgentPolicy(sessionUaFileListPreset,sessionUaDirectorySearchPreset,sessionUaApiSearchPreset,sessionUaDirectPreset,sessionCustomUserAgent,sessionUaScopeMask);}
  void applyLanzouRoutingSettings(){LanzouCore.setBaseOriginPolicy(sessionLanzouBaseOrigin,sessionLanzouTimeoutFailover);}
  String uaSettingsLabel(){String base="文件列表 "+LanzouCore.uaPresetLabel(sessionUaFileListPreset)+" · 目录 "+LanzouCore.uaPresetLabel(sessionUaDirectorySearchPreset)+" · API "+LanzouCore.uaPresetLabel(sessionUaApiSearchPreset)+" · 直链 "+LanzouCore.uaPresetLabel(sessionUaDirectPreset);return sessionCustomUserAgent.isEmpty()?base:base+" · 自定义 UA";}
  TextView uaDropdownView(String value){TextView choice=text(value,14,TEXT);choice.setGravity(Gravity.CENTER_VERTICAL|Gravity.END);choice.setSingleLine(true);choice.setEllipsize(android.text.TextUtils.TruncateAt.END);choice.setPadding(dp(14),0,dp(14),0);choice.setBackground(filterRipple(shape(SURFACE,24)));choice.setClickable(true);choice.setFocusable(true);choice.setCompoundDrawablesWithIntrinsicBounds(0,0,android.R.drawable.arrow_down_float,0);choice.setCompoundDrawablePadding(dp(8));return choice;}
  int uaPresetPosition(int[] presets,int value){int normalized=LanzouCore.normalizeUserAgentPreset(value);for(int i=0;i<presets.length;i++)if(presets[i]==normalized)return i;return 0;}
  View uaScopePresetRow(String label,int[] presets,String[] presetLabels,int[] selected,int slot){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(4),0,dp(4));TextView title=text(label,15,TEXT);title.setGravity(Gravity.CENTER_VERTICAL);row.addView(title,new LinearLayout.LayoutParams(dp(94),dp(52)));TextView choice=uaDropdownView(presetLabels[uaPresetPosition(presets,selected[slot])]);choice.setContentDescription(label+" UA 下拉栏，当前 "+choice.getText());choice.setOnClickListener(v->showUaPresetPicker(label,choice,presets,presetLabels,selected,slot));row.addView(choice,new LinearLayout.LayoutParams(0,dp(52),1));return row;}
  void showUaPresetPicker(String label,TextView target,int[] presets,String[] presetLabels,int[] selected,int slot){AlertDialog prompt=new AlertDialog.Builder(this).setTitle(label+" UA").setItems(presetLabels,(dialog,which)->{int index=Math.max(0,Math.min(presets.length-1,which));selected[slot]=presets[index];target.setText(presetLabels[index]);target.setContentDescription(label+" UA 下拉栏，当前 "+presetLabels[index]);}).create();showRounded(prompt);}
  LinearLayout buildUaSettingsRow(){LinearLayout row=settingsRowShell();row.setBackground(settingsPress());applePressScale(row);row.addView(settingsLeadingIcon(R.drawable.ic_open_with),settingsIconBox());TextView title=settingsRowTitle("UA 设置");row.addView(title,new LinearLayout.LayoutParams(0,-1,1));TextView value=text(uaSettingsLabel(),13,PRIMARY);value.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);value.setPadding(dp(12),0,dp(4),0);row.addView(value,new LinearLayout.LayoutParams(-2,-1));row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->showUaSettingsDialog());settingsRefreshers.add(()->value.setText(uaSettingsLabel()));row.setContentDescription("UA 设置，当前 "+uaSettingsLabel()+"。点击选择预设和使用范围");return row;}
  void showUaSettingsDialog(){int[] presets={LanzouCore.UA_PRESET_MOBILE_CHROME,LanzouCore.UA_PRESET_MOBILE_HUAWEI,LanzouCore.UA_PRESET_DESKTOP_CHROME,LanzouCore.UA_PRESET_DESKTOP_EDGE,LanzouCore.UA_PRESET_MOBILE_FIREFOX};String[] presetLabels=new String[presets.length];for(int i=0;i<presets.length;i++)presetLabels[i]=LanzouCore.uaPresetLabel(presets[i]);int[] selected={sessionUaFileListPreset,sessionUaDirectorySearchPreset,sessionUaApiSearchPreset,sessionUaDirectPreset};LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(8),dp(18),0);TextView hint=text("每个使用范围单独选择 UA；填写自定义 UA 后会覆盖所有预设，用于处理蓝奏 WAF 误判，不自动破解真人滑块验证。",12,MUTED);hint.setPadding(0,0,0,dp(8));panel.addView(hint,new LinearLayout.LayoutParams(-1,-2));TextView range=text("UA 使用范围",13,MUTED);range.setPadding(0,dp(8),0,0);panel.addView(range,new LinearLayout.LayoutParams(-1,dp(36)));panel.addView(uaScopePresetRow("文件列表",presets,presetLabels,selected,0),new LinearLayout.LayoutParams(-1,dp(60)));panel.addView(uaScopePresetRow("目录搜索",presets,presetLabels,selected,1),new LinearLayout.LayoutParams(-1,dp(60)));panel.addView(uaScopePresetRow("API 搜索",presets,presetLabels,selected,2),new LinearLayout.LayoutParams(-1,dp(60)));panel.addView(uaScopePresetRow("直链解析",presets,presetLabels,selected,3),new LinearLayout.LayoutParams(-1,dp(60)));TextView customTitle=text("自定义 UA（留空使用上方预设）",13,MUTED);customTitle.setPadding(0,dp(8),0,0);panel.addView(customTitle,new LinearLayout.LayoutParams(-1,dp(36)));EditText custom=sourceInput("Mozilla/5.0 ...",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);custom.setSingleLine(false);custom.setMinLines(2);custom.setMaxLines(3);custom.setText(sessionCustomUserAgent);panel.addView(custom,new LinearLayout.LayoutParams(-1,dp(96)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("UA 设置").setView(panel).setNegativeButton("取消",null).setPositiveButton("保存",null).create();prompt.setOnShowListener(v->prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{String customUa=LanzouCore.normalizeCustomUserAgent(custom.getText().toString());if(!customUa.isEmpty()&&customUa.length()<12){showNotice("自定义 UA 过短",true);return;}sessionUaFileListPreset=LanzouCore.normalizeUserAgentPreset(selected[0]);sessionUaDirectorySearchPreset=LanzouCore.normalizeUserAgentPreset(selected[1]);sessionUaApiSearchPreset=LanzouCore.normalizeUserAgentPreset(selected[2]);sessionUaDirectPreset=LanzouCore.normalizeUserAgentPreset(selected[3]);sessionCustomUserAgent=customUa;sessionUaPreset=sessionUaDirectPreset;persistSearchSettings();prompt.dismiss();showNotice("UA 已切换："+uaSettingsLabel(),false);if(pageKind==4)refreshSettingsInPlace();}));showRounded(prompt);}
    String searchModeLabel(){return "文件列表页 "+backendMaskLabel(sessionFileListModeMask)+" · 搜索 "+backendMaskLabel(sessionSearchModeMask)+(sessionSearchFuzzyMatching?" · 模糊":"");}
  LinearLayout buildSearchModeRow(){LinearLayout row=settingsRowShell();row.setBackground(settingsPress());applePressScale(row);row.addView(settingsLeadingIcon(R.drawable.ic_search),settingsIconBox());TextView title=settingsRowTitle("搜索模式");row.addView(title,new LinearLayout.LayoutParams(0,-1,1));TextView value=text(searchModeLabel(),13,PRIMARY);value.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);value.setPadding(dp(12),0,dp(4),0);row.addView(value,new LinearLayout.LayoutParams(-2,-1));row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->showSearchModeDialog());settingsRefreshers.add(()->value.setText(searchModeLabel()));row.setContentDescription("搜索模式，当前 "+searchModeLabel()+"，点击选择");return row;}
  int checkMask(CheckBox[] boxes,int[] masks){int mask=0;for(int i=0;i<boxes.length;i++)if(boxes[i].isChecked())mask|=masks[i];return mask;}
  boolean anyChecked(CheckBox[] boxes){for(CheckBox box:boxes)if(box.isChecked())return true;return false;}
  void guardModeColumn(CheckBox[] boxes,CheckBox changed){if(!anyChecked(boxes)){changed.setChecked(true);showNotice("每列至少保留一种搜索方式",false);}}
  void showSearchModeDialog(){String[] labels={"API","目录","索引"};int[] masks={Models.SearchOptions.MASK_API,Models.SearchOptions.MASK_DIRECTORY,Models.SearchOptions.MASK_INDEX};CheckBox[] fileChecks=new CheckBox[3],searchChecks=new CheckBox[3];LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(18),dp(6),dp(18),0);LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);TextView h0=text("模式",12,MUTED);TextView h1=text("文件列表页",12,MUTED);h1.setGravity(Gravity.CENTER);TextView h2=text("搜索",12,MUTED);h2.setGravity(Gravity.CENTER);header.addView(h0,new LinearLayout.LayoutParams(0,dp(34),1));header.addView(h1,new LinearLayout.LayoutParams(dp(104),dp(34)));header.addView(h2,new LinearLayout.LayoutParams(dp(86),dp(34)));panel.addView(header);for(int i=0;i<labels.length;i++){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView label=text(labels[i],15,TEXT);row.addView(label,new LinearLayout.LayoutParams(0,dp(46),1));CheckBox file=new CheckBox(this);file.setGravity(Gravity.CENTER);file.setChecked((sessionFileListModeMask&masks[i])!=0);CheckBox search=new CheckBox(this);search.setGravity(Gravity.CENTER);search.setChecked((sessionSearchModeMask&masks[i])!=0);fileChecks[i]=file;searchChecks[i]=search;row.addView(file,new LinearLayout.LayoutParams(dp(104),dp(46)));row.addView(search,new LinearLayout.LayoutParams(dp(86),dp(46)));panel.addView(row);file.setOnCheckedChangeListener((button,value)->{if(!value)guardModeColumn(fileChecks,(CheckBox)button);});search.setOnCheckedChangeListener((button,value)->{if(!value)guardModeColumn(searchChecks,(CheckBox)button);});}
    LinearLayout fuzzyRow=new LinearLayout(this);fuzzyRow.setGravity(Gravity.CENTER_VERTICAL);fuzzyRow.setPadding(0,dp(8),0,0);TextView fuzzyTitle=text("模糊匹配",17,TEXT);fuzzyTitle.setTypeface(AppFonts.bold(this));TextView fuzzyHint=text("文件列表页、搜索、列表源",11,MUTED);LinearLayout fuzzyText=new LinearLayout(this);fuzzyText.setOrientation(LinearLayout.VERTICAL);fuzzyText.addView(fuzzyTitle,new LinearLayout.LayoutParams(-1,dp(28)));fuzzyText.addView(fuzzyHint,new LinearLayout.LayoutParams(-1,dp(24)));CheckBox fuzzySwitch=new CheckBox(this);fuzzySwitch.setChecked(sessionSearchFuzzyMatching);fuzzyRow.addView(fuzzySwitch,new LinearLayout.LayoutParams(dp(82),dp(58)));fuzzyRow.addView(fuzzyText,new LinearLayout.LayoutParams(0,dp(58),1));fuzzyRow.setClickable(true);fuzzyRow.setOnClickListener(v->fuzzySwitch.setChecked(!fuzzySwitch.isChecked()));panel.addView(fuzzyRow);AlertDialog prompt=new AlertDialog.Builder(this).setTitle("搜索模式").setView(panel).setNegativeButton("取消",null).setPositiveButton("保存",(dialog,which)->{sessionFileListModeMask=Models.SearchOptions.normalizeModeMask(checkMask(fileChecks,masks));sessionSearchModeMask=Models.SearchOptions.normalizeModeMask(checkMask(searchChecks,masks));sessionSearchMode=Models.SearchOptions.modeForMask(sessionSearchModeMask);sessionSearchFuzzyMatching=fuzzySwitch.isChecked();sessionSearchFuzzyMask=sessionSearchFuzzyMatching?FUZZY_ALL:0;sessionIndexEnabled=anyIndexSelected();persistSearchSettings();if(pageKind==4){refreshSettingsInPlace();refreshIndexProgressCard();}}).create();showRounded(prompt);}
  LinearLayout buildIndexProgressCard(){LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(14),dp(10),dp(10),dp(10));card.setBackground(solidShape(SET_HIGH,16));LinearLayout heading=new LinearLayout(this);heading.setGravity(Gravity.CENTER_VERTICAL);TextView title=text("搜索索引",16,TEXT);title.setTypeface(AppFonts.bold(this));heading.addView(title,new LinearLayout.LayoutParams(0,dp(42),1));Button update=toolbarTextButton("立即更新");update.setOnClickListener(v->requestDirectoryIndexUpdate(true,true));heading.addView(update,new LinearLayout.LayoutParams(dp(82),dp(42)));indexControlButton=toolbarTextButton(directoryIndexUserPaused?"继续":"暂停");indexControlButton.setOnClickListener(v->toggleDirectoryIndexPaused());heading.addView(indexControlButton,new LinearLayout.LayoutParams(dp(64),dp(42)));card.addView(heading,new LinearLayout.LayoutParams(-1,dp(44)));indexProgressText=text("正在读取索引状态…",13,TEXT);indexCurrentText=text("智能区域更新会续接未完成部分",11,MUTED);indexUpdatedText=text("更新时间：读取中",11,MUTED);indexProgressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);indexProgressBar.setMax(100);card.addView(indexProgressText,new LinearLayout.LayoutParams(-1,dp(30)));card.addView(indexProgressBar,new LinearLayout.LayoutParams(-1,dp(7)));card.addView(indexCurrentText,new LinearLayout.LayoutParams(-1,dp(28)));card.addView(indexUpdatedText,new LinearLayout.LayoutParams(-1,dp(28)));card.addView(settingsSwitchRow(R.drawable.ic_search,"自动更新索引",sessionBackgroundIndex,checked->{sessionBackgroundIndex=checked;persistSearchSettings();if(checked)requestDirectoryIndexUpdate(false,false);}),new LinearLayout.LayoutParams(-1,settingsRowHeight()));card.setContentDescription("索引进度、更新时间、开关与手动更新");scheduleIndexProgressPolling();return card;}

  boolean directoryIndexPaused(){synchronized(directoryIndexPauseLock){return directoryIndexUserPaused||directoryIndexSearchPaused;}}
    void toggleDirectoryIndexPaused(){boolean resume; synchronized(directoryIndexPauseLock){resume=directoryIndexUserPaused;directoryIndexUserPaused=!directoryIndexUserPaused;if(resume){directoryIndexResumePending=directoryIndexRunning.get();directoryIndexPauseLock.notifyAll();}else{directoryIndexResumePending=false;}}if(!resume&&core!=null)core.cancelBackgroundIndex();syncIndexControlButton();refreshIndexProgressCard();if(resume){if(directoryIndexRunning.get())directoryIndexResumePending=true;else requestDirectoryIndexUpdate(false,true);}}
  void setDirectoryIndexSearchPaused(boolean paused){synchronized(directoryIndexPauseLock){if(directoryIndexSearchPaused==paused)return;directoryIndexSearchPaused=paused;if(!paused)directoryIndexPauseLock.notifyAll();}syncIndexControlButton();}
  void syncIndexControlButton(){runOnUiThread(()->{if(indexControlButton!=null)indexControlButton.setText(directoryIndexUserPaused?"继续":"暂停");});}

  static final String DIRECTORY_INDEX_SCHEDULE_PREFS="directory_index_schedule_v1",DIRECTORY_INDEX_LAST_ATTEMPT_AT="last_attempt_at",DIRECTORY_INDEX_LAST_SUCCESS_AT="last_success_at";
  static boolean sameLocalCalendarDay(long firstMillis,long secondMillis,TimeZone zone){if(firstMillis<=0||secondMillis<=0)return false;TimeZone local=zone==null?TimeZone.getDefault():zone;Calendar first=Calendar.getInstance(local),second=Calendar.getInstance(local);first.setTimeInMillis(firstMillis);second.setTimeInMillis(secondMillis);return first.get(Calendar.ERA)==second.get(Calendar.ERA)&&first.get(Calendar.YEAR)==second.get(Calendar.YEAR)&&first.get(Calendar.DAY_OF_YEAR)==second.get(Calendar.DAY_OF_YEAR);}
  static boolean shouldRunAutomaticDirectoryIndex(long now,long lastAttempt,long lastSuccess,TimeZone zone){return !sameLocalCalendarDay(now,lastAttempt,zone)&&!sameLocalCalendarDay(now,lastSuccess,zone);}
  synchronized boolean claimAutomaticDirectoryIndexAttempt(long now){android.content.SharedPreferences prefs=getSharedPreferences(DIRECTORY_INDEX_SCHEDULE_PREFS,MODE_PRIVATE);if(!shouldRunAutomaticDirectoryIndex(now,prefs.getLong(DIRECTORY_INDEX_LAST_ATTEMPT_AT,0),prefs.getLong(DIRECTORY_INDEX_LAST_SUCCESS_AT,0),TimeZone.getDefault()))return false;return prefs.edit().putLong(DIRECTORY_INDEX_LAST_ATTEMPT_AT,now).commit();}
  void recordDirectoryIndexSuccess(long now){getSharedPreferences(DIRECTORY_INDEX_SCHEDULE_PREFS,MODE_PRIVATE).edit().putLong(DIRECTORY_INDEX_LAST_SUCCESS_AT,now).commit();}
  void clearSearchRuntimeCachesForNewIndex(){try{getSharedPreferences("daily_search_cache_v1",MODE_PRIVATE).edit().clear().commit();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}}
    void requestDirectoryIndexUpdate(boolean forceRefresh,boolean userRequested){if(core==null){if(userRequested)showNotice("索引暂不可用",true);return;}core.setDirectoryCachingEnabled(true);if(forceRefresh)clearSearchRuntimeCachesForNewIndex();if(userRequested){synchronized(directoryIndexPauseLock){directoryIndexUserPaused=false;directoryIndexPauseLock.notifyAll();}syncIndexControlButton();}boolean resumeIncomplete=false;if(!userRequested&&!forceRefresh)try{resumeIncomplete=!core.directoryIndexSnapshot(indexRetentionMillis()).complete();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}if(!directoryIndexRunning.compareAndSet(false,true)){if(userRequested){synchronized(directoryIndexPauseLock){if(!directoryIndexUserPaused)directoryIndexResumePending=true;}showNotice("索引正在更新",false);}return;}if(!userRequested&&!resumeIncomplete&&!claimAutomaticDirectoryIndexAttempt(System.currentTimeMillis())){directoryIndexRunning.set(false);return;}if(!userRequested&&resumeIncomplete)getSharedPreferences(DIRECTORY_INDEX_SCHEDULE_PREFS,MODE_PRIVATE).edit().putLong(DIRECTORY_INDEX_LAST_ATTEMPT_AT,System.currentTimeMillis()).commit();if(userRequested)showNotice(forceRefresh?"正在重建新索引":"正在更新索引",false);io.execute(()->{try{core.buildDirectoryIndex(sessionIndexThreads,indexRetentionMillis(),forceRefresh,sessionSearchRecursiveFolders,userRequested,(done,total,source,success)->runOnUiThread(()->applyIndexProgress(done,total,source,success)));LanzouCore.IndexSnapshot snapshot=core.directoryIndexSnapshot(indexRetentionMillis());boolean complete=snapshot.complete();if(complete)recordDirectoryIndexSuccess(System.currentTimeMillis());runOnUiThread(()->{applyIndexSnapshot(snapshot);if(userRequested)showNotice(complete?"索引已更新":"索引已保存进度，会继续续建",!complete);});}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}catch(Exception error){runOnUiThread(()->{if(userRequested)showNotice("索引进度已保存，会继续续建",true);refreshIndexProgressCard();});}finally{boolean resumePending;directoryIndexRunning.set(false);synchronized(directoryIndexPauseLock){resumePending=directoryIndexResumePending&&!directoryIndexUserPaused;directoryIndexResumePending=false;}if(resumePending)ui.post(()->requestDirectoryIndexUpdate(false,true));}});}
  void applyIndexProgress(int done,int total,String source,boolean success){if(indexProgressBar!=null){indexProgressBar.setIndeterminate(total<=0);indexProgressBar.setProgress(total<=0?0:done*100/Math.max(1,total));}if(indexProgressText!=null)indexProgressText.setText((success?"已建立 ":"正在建立 ")+done+" / "+total+" · "+(total<=0?0:done*100/Math.max(1,total))+"%");if(indexCurrentText!=null&&!source.isEmpty())indexCurrentText.setText((directoryIndexPaused()?"已暂停：":(success?"已更新：":"正在更新："))+source);if(indexUpdatedText!=null&&success)indexUpdatedText.setText("更新时间："+downloadMoment(System.currentTimeMillis()));if(!success&&directoryIndexRunning.get())scheduleIndexProgressPolling();}
  void applyIndexSnapshot(LanzouCore.IndexSnapshot snapshot){applyIndexProgress(snapshot.done,snapshot.total,snapshot.source,true);if(indexProgressText!=null&&!anyIndexSelected()&&!directoryIndexRunning.get())indexProgressText.setText("搜索模式未选择索引 · 索引仍会更新");if(indexCurrentText!=null&&snapshot.source.isEmpty())indexCurrentText.setText(snapshot.complete()?"索引已是最新":"智能区域更新会续接未完成或过期部分");if(indexUpdatedText!=null)indexUpdatedText.setText("更新时间："+(snapshot.updatedAt<=0?"尚未建立":downloadMoment(snapshot.updatedAt))+" · "+indexRetentionText().replace("单个索引最高保留：","保留 "));}
  void scheduleIndexProgressPolling(){if(!directoryIndexRunning.get()||indexProgressText==null){indexProgressPollPosted.set(false);return;}if(!indexProgressPollPosted.compareAndSet(false,true))return;ui.postDelayed(()->{indexProgressPollPosted.set(false);if(directoryIndexRunning.get()&&indexProgressText!=null){refreshIndexProgressCard();scheduleIndexProgressPolling();}},1000);} 
  void refreshIndexProgressCard(){if(core==null)return;io.execute(()->{try{LanzouCore.IndexSnapshot snapshot=core.directoryIndexSnapshot(indexRetentionMillis());runOnUiThread(()->{applyIndexSnapshot(snapshot);if(directoryIndexRunning.get())scheduleIndexProgressPolling();});}catch(Exception ignored){runOnUiThread(()->{if(indexProgressText!=null)indexProgressText.setText("索引状态暂不可读");if(directoryIndexRunning.get())scheduleIndexProgressPolling();});}});}


  void checkStartupLanzouBaseOrigin(){
    if(core==null)return;final String selected=sessionLanzouBaseOrigin,reference=firstNonEmpty(home.url,selected);io.execute(()->{try{LanzouCore.BaseOriginProbe result=core.probeStartupBaseOrigin(selected,reference);if(result.available)return;runOnUiThread(()->{if(isFinishing()||!selected.equalsIgnoreCase(sessionLanzouBaseOrigin))return;String reason=result.challenge?"当前基础链接返回 UA 验证页":"当前基础链接无法连接或已超时";AlertDialog prompt=new AlertDialog.Builder(this).setTitle("基础链接连接异常").setMessage(reason+"：\n"+selected+"\n\n建议立即测速并更换可用基础链接。").setNegativeButton("稍后",null).setPositiveButton("修改基础链接",(dialog,which)->showLanzouBaseOriginDialog(reference)).create();showRounded(prompt);});}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});
  }

  LinearLayout buildLanzouBaseOriginRow(){LinearLayout row=settingsRowShell();row.setBackground(settingsPress());applePressScale(row);row.addView(settingsLeadingIcon(R.drawable.ic_home),settingsIconBox());TextView title=settingsRowTitle("蓝奏基础链接");row.addView(title,new LinearLayout.LayoutParams(0,-1,1));TextView value=text(lanzouBaseOriginLabel(),13,PRIMARY);value.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);value.setPadding(dp(12),0,dp(4),0);row.addView(value,new LinearLayout.LayoutParams(-2,-1));row.setClickable(true);row.setFocusable(true);row.setOnClickListener(v->showLanzouBaseOriginDialog());settingsRefreshers.add(()->value.setText(lanzouBaseOriginLabel()));row.setContentDescription("蓝奏基础链接，当前 "+lanzouBaseOriginLabel());return row;}
  int dailySearchDay(){Calendar now=Calendar.getInstance();return now.get(Calendar.YEAR)*400+now.get(Calendar.DAY_OF_YEAR);}
  String dailySearchSignature(String query,Set<String> allowed,Models.SearchOptions options){List<String> ids=allowed==null?Collections.emptyList():new ArrayList<>(allowed);Collections.sort(ids);return query.trim().toLowerCase(Locale.ROOT)+"\n"+String.join("\n",ids)+"\n"+options.modeMask+"/"+options.recursiveFolders+"/"+options.maxPages+"/"+options.fuzzyMatching+"/"+fuzzyIndexEnabled()+"/"+fuzzyFolderListEnabled();}
  String dailySearchCacheKey(String signature){return "q_"+Integer.toHexString(signature.hashCode());}
  List<Models.Item> readDailySearchCache(String query,Set<String> allowed,Models.SearchOptions options){List<Models.Item> out=new ArrayList<>();try{android.content.SharedPreferences prefs=getSharedPreferences("daily_search_cache_v1",MODE_PRIVATE);int day=dailySearchDay();if(prefs.getInt("day",-1)!=day){prefs.edit().clear().putInt("day",day).commit();return out;}String signature=dailySearchSignature(query,allowed,options),raw=prefs.getString(dailySearchCacheKey(signature),"");if(raw.isEmpty())return out;org.json.JSONObject root=new org.json.JSONObject(raw);if(!signature.equals(root.optString("signature")))return out;org.json.JSONArray values=root.optJSONArray("items");if(values==null)return out;for(int i=0;i<values.length();i++){org.json.JSONObject value=values.getJSONObject(i);Models.Item item=new Models.Item();item.title=value.optString("title");item.url=value.optString("url");item.shareUrl=value.optString("share",item.url);item.size=value.optString("size");item.time=value.optString("time");item.iconUrl=value.optString("icon");item.source=value.optString("source");item.sourceId=value.optString("sourceId");item.password=value.optString("password");item.description=value.optString("description");item.folder=value.optBoolean("folder");item.sourceEntry=value.optBoolean("sourceEntry");if(!item.url.isEmpty())out.add(item);}}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}return out;}
  void writeDailySearchCache(String query,Set<String> allowed,Models.SearchOptions options,List<Models.Item> items){try{String signature=dailySearchSignature(query,allowed,options);org.json.JSONArray values=new org.json.JSONArray();for(Models.Item item:items)values.put(new org.json.JSONObject().put("title",item.title).put("url",item.url).put("share",item.shareUrl).put("size",item.size).put("time",item.time).put("icon",item.iconUrl).put("source",item.source).put("sourceId",item.sourceId).put("password",item.password).put("description",item.description).put("folder",item.folder).put("sourceEntry",item.sourceEntry));org.json.JSONObject root=new org.json.JSONObject().put("signature",signature).put("items",values);android.content.SharedPreferences prefs=getSharedPreferences("daily_search_cache_v1",MODE_PRIVATE);int day=dailySearchDay();android.content.SharedPreferences.Editor edit=prefs.edit();if(prefs.getInt("day",-1)!=day)edit.clear().putInt("day",day);edit.putString(dailySearchCacheKey(signature),root.toString()).commit();if(core!=null)core.mergeSearchResultsIntoIndex(items,indexRetentionMillis());}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}}
  void runSearch(String q){
    if(q.isEmpty())return;homeSearchHistoryOnly=false;homeSearchRequested=true;if(!homeSearchFocused)showHomeSearchMode(false);saveSearchHistory(q);if(search!=null){if(!q.equals(search.getText().toString())){search.setText(q);search.setSelection(q.length());}search.clearFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0);}if(selectionMode)exitSelection();clearFolderTrail();folderRootSources=false;activeSource=home;systemBackAction=this::exitHomeSearchFocus;final int session=++searchGeneration,points=q.codePointCount(0,q.length());final String category=sessionSearchCategory;final int searchMode=sessionSearchMode,searchModeMask=sessionSearchModeMask;final Set<String> sourceNameScope=searchCategoryIds(category);final List<Models.Item> homeSnapshot=new ArrayList<>(homeItems);synchronized(globalSearch){globalSearch.query=q;globalSearch.source="";globalSearch.right=points<2?"仅名称匹配":searchMode==Models.SearchOptions.MODE_API?"API 搜索":searchMode==Models.SearchOptions.MODE_INDEX?"仅索引匹配":"目录缓存优先";globalSearch.active=0;globalSearch.done=0;globalSearch.total=0;globalSearch.pages=0;globalSearch.percent=0;globalSearch.failures=0;globalSearch.folderCount=0;globalSearch.running=points>=2;globalSearch.nameOnly=points<2;globalSearch.indexOnly=points>=2&&searchMode==Models.SearchOptions.MODE_INDEX;globalSearch.paused=false;globalSearch.items.clear();globalSearch.byUrl.clear();globalSearch.notifyAll();}renderSearchResults();if(points>=2)setDirectoryIndexSearchPaused(true);
    io.execute(()->{try{
      Set<String> allowedSources=searchAllowedSourceIds(category,searchMode);int sourceTotal=points<2?0:searchableSourceTotal(allowedSources);Models.SearchOptions options=searchOptions(sourceTotal).withModeMask(searchModeMask);synchronized(globalSearch){if(session!=searchGeneration)return;globalSearch.total=sourceTotal;}queueSearchRefresh(session);if(points>=2&&options.indexOnly()){searchIndexIo.execute(()->{try{acceptSearchBatch(session,core.cachedPartialIndexMatches(q,allowedSources,fuzzyIndexEnabled()));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}finally{finishSearchJob(session);}});return;}if(points>=2){acceptSearchBatch(session,readDailySearchCache(q,allowedSources,options));if(options.indexEnabled())acceptSearchBatch(session,core.cachedPartialIndexMatches(q,allowedSources,fuzzyIndexEnabled()));}searchIndexIo.execute(()->{try{acceptSearchBatch(session,core.sourceNameItems(q,sourceNameScope));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}});if(points>=2&&options.indexEnabled())searchIndexIo.execute(()->{try{acceptSearchBatch(session,core.cachedDirectoryMatchesWarm(q,allowedSources,sessionSearchRecursiveFolders,sessionSearchMaxPages,fuzzyIndexEnabled()));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}});List<Models.Item> homeLocal=allowedSources==null||allowedSources.contains(sourceKey(home))?homeSnapshot:new ArrayList<>();String homeId=sourceKey(home);for(Models.Item item:homeLocal){if(item.source.isEmpty())item.source=home.title;if(item.sourceId.isEmpty())item.sourceId=homeId;}if(points<2){try{homeLocal.addAll(core.localSourceItems(allowedSources));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}String key=q.toLowerCase(Locale.ROOT);List<Models.Item> matches=new ArrayList<>();for(Models.Item item:homeLocal)if(folderMatchRank(item.title.toLowerCase(Locale.ROOT),key,sessionSearchFuzzyMatching)>=0)matches.add(item);acceptSearchBatch(session,matches);queueSearchRefresh(session);return;}List<Models.Item> result=core.search(q,home,homeLocal,options,allowedSources,new Models.Progress(){
        @Override public boolean isCancelled(){return session!=searchGeneration;}
        @Override public boolean awaitIfPaused(){synchronized(globalSearch){while(session==searchGeneration&&globalSearch.paused)try{globalSearch.wait();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();return false;}return session==searchGeneration;}}
        @Override public void onBatch(List<Models.Item> batch){acceptSearchBatch(session,batch);core.mergeSearchResultsIntoIndex(batch,indexRetentionMillis());}
        @Override public void onItemUpdated(Models.Item item){acceptSearchItemUpdate(session,item);}
        @Override public void onActivity(int active,int total,String source){synchronized(globalSearch){if(session!=searchGeneration)return;globalSearch.active=active;globalSearch.total=total;if(source!=null&&!source.isEmpty())globalSearch.source=source;}queueSearchRefresh(session);}
        @Override public void onWorkProgress(int doneUnits,int totalUnits){synchronized(globalSearch){if(session!=searchGeneration)return;int value=totalUnits<=0?0:doneUnits*100/Math.max(1,totalUnits);globalSearch.percent=Math.max(globalSearch.percent,Math.min(99,value));}queueSearchRefresh(session);}
        @Override public void onFailure(String source){synchronized(globalSearch){if(session==searchGeneration)globalSearch.failures++;}}
        @Override public void onIndexSource(String sourceId,String source){if(!options.indexEnabled())return;try{LanzouCore.IndexSnapshot snapshot=core.markSearchSourceIndexed(sourceId,source,indexRetentionMillis());runOnUiThread(()->{if(session==searchGeneration)applyIndexSnapshot(snapshot);});}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}}
        @Override public void onPage(String source,int page,int pageItems,int sourceFound,int totalPagesSeen){synchronized(globalSearch){if(session!=searchGeneration)return;globalSearch.source=source;globalSearch.pages=totalPagesSeen;globalSearch.right="第 "+page+" 页 · 累计 "+totalPagesSeen+" 页";}queueSearchRefresh(session);}
        @Override public void onProgress(int done,int total,int found,String source){synchronized(globalSearch){if(session!=searchGeneration)return;globalSearch.done=done;globalSearch.total=total;globalSearch.source=source;globalSearch.percent=Math.max(globalSearch.percent,done*100/Math.max(1,total));globalSearch.right="已显示 "+globalSearch.items.size();}queueSearchRefresh(session);}
      });
      acceptSearchBatch(session,result);List<Models.Item> dailySnapshot;synchronized(globalSearch){dailySnapshot=new ArrayList<>(globalSearch.items);}writeDailySearchCache(q,allowedSources,options,dailySnapshot);finishSearchJob(session);
    }catch(Exception e){if(session==searchGeneration){synchronized(globalSearch){globalSearch.failures++;}finishSearchJob(session);showNotice(friendlyError(e),true);}}});
  }
  void finishSearchJob(int session){setDirectoryIndexSearchPaused(false);synchronized(globalSearch){if(session!=searchGeneration)return;globalSearch.active=0;globalSearch.running=false;globalSearch.paused=false;globalSearch.percent=100;globalSearch.right=globalSearch.failures==0?"已完成":"已完成 · "+globalSearch.failures+" 源异常";globalSearch.notifyAll();}queueSearchRefresh(session);}
  void openSearchSourcePage(Models.Source source){source=runtimeLanzouSource(source);pageDirection=1;resetFolderTrail(source);folderRootSources=false;showFolderPage(activeFolderState.source,true);}
  void openSourceSearchResult(Models.Item item){try{Models.Source source=core.sourceById(item.sourceId);if(source==null){if(item.folder)openNestedFolder(item);else confirmDownload(item);return;}if(singleFileSource(source))confirmDownload(singleFileItem(source));else openSearchSourcePage(source);}catch(Exception error){showNotice("无法打开软件源："+friendlyError(error),true);}}
  TextView sourceTag(String value){TextView tag=text(value,10,PRIMARY);tag.setGravity(Gravity.CENTER);tag.setPadding(dp(7),0,dp(7),0);tag.setBackground(shape(ThemeEngine.tint(PRIMARY,darkMode?64:24),8));return tag;}
  List<String> sourceCategoryMemberships(Models.Source source){List<String> out=new ArrayList<>();if(!"全部".equals(activeSourceCategory))return out;String id=sourceKey(source),stable=LanzouCore.sourceId(source);for(Map.Entry<String,LinkedHashSet<String>> entry:sourceCategories.entrySet())if(entry.getValue().contains(id)||entry.getValue().contains(stable))out.add(entry.getKey());return out;}
    String sourceSearchCorpus(Models.Source source){StringBuilder out=new StringBuilder(source.title).append(' ').append(source.url).append(' ').append(source.password).append(' ').append(source.publisher).append(' ').append(source.description);for(Models.SourceMember member:source.members)out.append(' ').append(member.title).append(' ').append(member.url).append(' ').append(member.password).append(' ').append(member.description);return out.toString();}
  LinkedHashMap<String,String> buildSourceSearchCorpora(List<Models.Source> sources){LinkedHashMap<String,String> out=new LinkedHashMap<>();for(Models.Source source:sources)out.put(sourceKey(source),sourceSearchCorpus(source).toLowerCase(Locale.ROOT));return out;}
  String cachedSourceSearchCorpus(Models.Source source){if(sourceSearchCorporaRevision!=sourceDataRevision){sourceSearchCorpora.clear();sourceSearchCorporaRevision=sourceDataRevision;}String id=sourceKey(source),value=sourceSearchCorpora.get(id);if(value==null){value=sourceSearchCorpus(source).toLowerCase(Locale.ROOT);sourceSearchCorpora.put(id,value);}return value;}
  void queueSourceListFilter(List<Models.Source> all,String query,TextView heading){if(sourceListFilterRunnable!=null)ui.removeCallbacks(sourceListFilterRunnable);int session=navigationSession;String value=query==null?"":query.trim();Runnable job=()->{sourceListFilterRunnable=null;if(session==navigationSession&&sourceGrid!=null&&all==sourcePageSources&&heading==sourceHeading)renderSources(sourceGrid,all,value,heading);};sourceListFilterRunnable=job;ui.postDelayed(job,110L);}
  static String sourceInputLine(String url,String password){String link=url==null?"":url.trim(),pwd=password==null?"":password.trim();return link.isEmpty()?"":link+(pwd.isEmpty()?"":" "+pwd);}
  void appendSourceInputLines(LinkedHashSet<String> lines,Models.Source source){source=runtimeLanzouSource(source);if(!compositeSource(source)||transientLinkSource(source)){String line=sourceInputLine(source.url,source.password);if(!line.isEmpty())lines.add(line);return;}if(!source.nodeId.isEmpty()){try{for(Models.Item item:core.portableCompositeFolderItems(source,source.nodeId)){String line=sourceInputLine(firstNonEmpty(item.shareUrl,item.url),item.password);if(!line.isEmpty())lines.add(line);}}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}return;}for(Models.SourceMember member:source.members){String line=sourceInputLine(member.url,member.password);if(!line.isEmpty())lines.add(line);}}
  String sourceShareText(Models.Source source){LinkedHashSet<String> lines=new LinkedHashSet<>();appendSourceInputLines(lines,source);return String.join("\n",lines);}
  View sourceRow(Models.Source x){
    String id=sourceKey(x);boolean composite=compositeSource(x),collection=collectionSource(x);/* [DFW-24] 源列表行同样是高频点击面，原来也没有任何按压反馈。 */
    LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.VERTICAL);row.setPadding(dp(8),dp(4),dp(8),dp(4));row.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));row.setClickable(true);row.setFocusable(true);applePressScale(row);
    LinearLayout titleRow=new LinearLayout(this);titleRow.setGravity(Gravity.CENTER_VERTICAL);ImageView kindIcon=new ImageView(this);kindIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);kindIcon.setImageResource(collection?R.drawable.ic_folder:R.drawable.ic_file);kindIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);if(collection)kindIcon.setColorFilter(PRIMARY);else{String softwareIcon=!x.members.isEmpty()?x.members.get(0).iconUrl:x.avatarUrl;if(!softwareIcon.isEmpty())loadImage(softwareIcon,kindIcon);}titleRow.addView(kindIcon,new LinearLayout.LayoutParams(dp(22),dp(22)));TextView name=text(x.title,13,TEXT);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(8),0,0,0);titleRow.addView(name,new LinearLayout.LayoutParams(0,dp(32),1));CheckBox check=new CheckBox(this);check.setClickable(false);check.setFocusable(false);check.setContentDescription("选择 "+x.title);check.setChecked(selectedSourceUrls.contains(id));check.setVisibility(sourceSelectionMode?View.VISIBLE:View.GONE);titleRow.addView(check,new LinearLayout.LayoutParams(dp(32),dp(32)));sourceSelectionChecks.put(id,check);row.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(32)));
    if(showSourceLinks&&!x.url.isEmpty()){String shownUrl=preferredLanzouUrl(x.url);TextView link=text(shownUrl,9,MUTED);link.setMinLines(2);link.setHorizontallyScrolling(false);link.setIncludeFontPadding(false);link.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);link.setPadding(dp(30),dp(2),dp(4),dp(2));row.addView(link,new LinearLayout.LayoutParams(-1,-2));}
    boolean testing=testingSourceUrls.contains(id),single=singleFileSource(x);LinearLayout tags=new LinearLayout(this);tags.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);if(composite)tags.addView(sourceTag("自建合集 · "+x.members.size()+"项"),new LinearLayout.LayoutParams(-2,dp(22)));else if(single)tags.addView(sourceTag("单文件"),new LinearLayout.LayoutParams(-2,dp(22)));if(x.childDirectory){LinearLayout.LayoutParams childParams=new LinearLayout.LayoutParams(-2,dp(22));if(tags.getChildCount()>0)childParams.setMargins(dp(5),0,0,0);tags.addView(sourceTag("子目录"),childParams);}if(testing){LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-2,dp(22));if(tags.getChildCount()>0)tp.setMargins(dp(5),0,0,0);tags.addView(sourceTag("测试中…"),tp);}else if(!x.error.isEmpty()){TextView issue=text("错误："+x.error,10,ERROR_TOKEN);issue.setSingleLine(true);issue.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams ep=new LinearLayout.LayoutParams(0,dp(22),1);if(tags.getChildCount()>0)ep.setMargins(dp(5),0,0,0);tags.addView(issue,ep);}else if(!composite&&!single){LinearLayout.LayoutParams capabilityParams=new LinearLayout.LayoutParams(-2,dp(22));if(tags.getChildCount()>0)capabilityParams.setMargins(dp(5),0,0,0);tags.addView(sourceTag(x.searchable?"可搜索":"仅浏览"),capabilityParams);}if(!composite&&!x.password.isEmpty()){LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-2,dp(22));pp.setMargins(dp(5),0,0,0);tags.addView(sourceTag("有密码"),pp);}List<String> memberships=sourceCategoryMemberships(x);HorizontalScrollView categoryScroll=new HorizontalScrollView(this);categoryScroll.setTag(sourceCategoryTagKey(x));categoryScroll.setHorizontalScrollBarEnabled(false);categoryScroll.setFillViewport(false);categoryScroll.setPadding(dp(5),0,0,0);LinearLayout categoryTags=new LinearLayout(this);categoryTags.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);for(String membership:memberships){LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,dp(22));cp.setMargins(0,0,dp(4),0);categoryTags.addView(sourceTag(membership),cp);}categoryScroll.addView(categoryTags,new HorizontalScrollView.LayoutParams(-2,dp(22)));categoryScroll.setVisibility(memberships.isEmpty()?View.GONE:View.VISIBLE);tags.addView(categoryScroll,new LinearLayout.LayoutParams(0,dp(22),1));row.addView(tags,new LinearLayout.LayoutParams(-1,dp(22)));
    String state=testing?"测试中":!x.error.isEmpty()?"错误："+x.error:single?"单文件":composite?"自建合集，"+x.members.size()+" 项":(x.searchable?"可搜索":"仅浏览");if(!x.password.isEmpty())state+=",有密码";if(x.childDirectory)state="子目录，"+state;row.setContentDescription(x.title+"，"+state+(showSourceLinks?"，链接 "+preferredLanzouUrl(x.url):""));row.setOnClickListener(v->{if(sourceSelectionMode)toggleSourceSelection(x);else if(single)confirmDownload(singleFileItem(x));else openSourcePage(x);});row.setOnLongClickListener(v->{if(!sourceSelectionMode)enterSourceSelection();toggleSourceSelection(x);return true;});return row;
  }
  View folderCategoryAction(Models.Source source){LinearLayout action=new LinearLayout(this);action.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);action.setPadding(dp(5),0,dp(3),0);action.setClickable(true);action.setFocusable(true);action.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));action.setContentDescription("将当前列表源加入分类");ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_add);icon.setColorFilter(PRIMARY);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);action.addView(icon,new LinearLayout.LayoutParams(dp(20),dp(20)));TextView label=text("添加",12,PRIMARY);label.setGravity(Gravity.CENTER);label.setPadding(dp(5),0,0,0);action.addView(label,new LinearLayout.LayoutParams(-2,dp(34)));action.setOnClickListener(v->chooseCurrentSourceCategory(source));return action;}
  void chooseCurrentSourceCategory(Models.Source source){if(source==null||source.url.isEmpty()&&source.id.isEmpty()){showNotice("当前页面无法加入分类",true);return;}showCurrentSourceDestinationPicker(source);}
  void showCurrentSourceDestinationPicker(Models.Source source){showSourceDestinationPicker(Collections.singletonList(source),Collections.emptyList());}
  View sourceDestinationRow(SourceDestination destination,Map<String,SourceDestination> selected,Runnable enter,Runnable changed,boolean alreadyAdded){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(4),0,dp(4),0);row.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));if(alreadyAdded){TextView checked=text("✓",19,PRIMARY);checked.setGravity(Gravity.CENTER);checked.setContentDescription("已添加到 "+destination.path);row.addView(checked,new LinearLayout.LayoutParams(dp(44),dp(48)));}else{CheckBox pick=new CheckBox(this);pick.setChecked(selected.containsKey(destination.key()));pick.setContentDescription("选择路径 "+destination.path);pick.setOnCheckedChangeListener((button,value)->{if(value)selected.put(destination.key(),destination);else selected.remove(destination.key());changed.run();});row.addView(pick,new LinearLayout.LayoutParams(dp(44),dp(48)));}ImageView folder=new ImageView(this);folder.setImageResource(R.drawable.ic_folder);folder.setColorFilter(PRIMARY);folder.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);row.addView(folder,new LinearLayout.LayoutParams(dp(24),dp(24)));TextView name=text(destination.path.substring(destination.path.lastIndexOf('›')+1).trim(),13,TEXT);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);name.setPadding(dp(10),0,dp(6),0);row.addView(name,new LinearLayout.LayoutParams(0,dp(48),1));ImageButton open=iconButton(R.drawable.ic_expand,"进入 "+name.getText());open.setRotation(-90f);open.setOnClickListener(v->enter.run());row.addView(open,new LinearLayout.LayoutParams(dp(42),dp(48)));name.setClickable(true);name.setOnClickListener(v->enter.run());row.setContentDescription("路径 "+destination.path+(alreadyAdded?"，已添加":"，可勾选或进入"));return row;}
  boolean destinationAlreadyContainsSources(SourceDestination destination,List<Models.Source> sources){if(!destination.categoryOnly()||destination.category.equals("全部")||sources==null||sources.isEmpty())return false;Set<String> members=sourceCategories.get(destination.category);if(members==null)return false;for(Models.Source source:sources)if(!members.contains(sourceKey(source))&&!members.contains(LanzouCore.sourceId(source)))return false;return true;}
  void showSourceDestinationPicker(List<Models.Source> sourceInput,List<Models.Item> itemInput){
    loadSourceCategories();
    List<Models.Source> available;
    try{available=core.sources();}catch(Exception error){showNotice("无法读取分类目录",true);return;}
    List<Models.Source> sources=new ArrayList<>(sourceInput==null?Collections.emptyList():sourceInput);
    List<Models.Item> items=new ArrayList<>(itemInput==null?Collections.emptyList():itemInput);
    LinkedHashMap<String,SourceDestination> selected=new LinkedHashMap<>();
    ArrayList<SourceDestination> stack=new ArrayList<>();
    SourceDestination[] current={null};
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(10),dp(4),dp(10),0);
    LinearLayout locationBar=new LinearLayout(this);locationBar.setGravity(Gravity.CENTER_VERTICAL);
    ImageButton back=iconButton(R.drawable.ic_back,"返回上一级");ImageButton createFolder=iconButton(R.drawable.ic_add,"在当前路径创建文件夹");
    TextView path=text("当前路径：分类",13,TEXT);path.setSingleLine(true);path.setEllipsize(android.text.TextUtils.TruncateAt.START);
    locationBar.addView(back,new LinearLayout.LayoutParams(dp(44),dp(44)));locationBar.addView(path,new LinearLayout.LayoutParams(0,dp(44),1));locationBar.addView(createFolder,new LinearLayout.LayoutParams(dp(44),dp(44)));panel.addView(locationBar,new LinearLayout.LayoutParams(-1,dp(44)));
    TextView summary=text("已选 0 个路径",12,PRIMARY);summary.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);panel.addView(summary,new LinearLayout.LayoutParams(-1,dp(34)));
    LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(true);scroll.addView(choices,new ScrollView.LayoutParams(-1,-2));
    panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(330),Math.max(dp(180),getResources().getDisplayMetrics().heightPixels-dp(350)))));
    AlertDialog prompt=new AlertDialog.Builder(this).setTitle("批量选择加入路径").setView(panel).setNegativeButton("取消",null).setPositiveButton("添加到所选路径",null).create();
    Button[] submit={null};Runnable[] update={null},render={null};
    update[0]=()->{summary.setText("已选 "+selected.size()+" 个路径");summary.setContentDescription("提交前摘要，已选 "+selected.size()+" 个路径");if(submit[0]!=null)submit[0].setEnabled(!selected.isEmpty());};
    render[0]=()->{
      choices.removeAllViews();SourceDestination here=current[0];path.setText("当前路径："+(here==null?"分类":here.path));back.setEnabled(here!=null);createFolder.setEnabled(here!=null&&!here.categoryOnly());
      LinkedHashMap<String,SourceDestination> level=new LinkedHashMap<>();
      if(here==null){
        level.putIfAbsent("全部",new SourceDestination("全部",null,"","全部"));
        for(String category:sourceCategories.keySet()){String dedupe=category.toLowerCase(Locale.ROOT);level.putIfAbsent(dedupe,new SourceDestination(category,null,"",category));}
      }else if(here.categoryOnly()){
        Set<String> ids=here.category.equals("全部")?null:sourceCategories.get(here.category);
        for(Models.Source source:available)if(compositeSource(source)&&(ids==null||ids.contains(sourceKey(source)))){
          String title=source.title.isEmpty()?"自建目录":source.title;
          SourceDestination destination=new SourceDestination(here.category,source,"",here.category+" › "+title);
          level.putIfAbsent(destination.key(),destination);
        }
      }else{
        for(Models.SourceMember member:here.composite.members)if(member.kind==Models.MEMBER_FOLDER&&member.parentId.equals(here.nodeId)&&!member.title.isEmpty()){
          SourceDestination destination=new SourceDestination(here.category,here.composite,member.id,here.path+" › "+member.title);
          level.putIfAbsent(destination.key(),destination);
        }
      }
      for(SourceDestination destination:level.values()){
        Runnable enter=()->{stack.add(current[0]);current[0]=destination;render[0].run();};
        boolean alreadyAdded=destinationAlreadyContainsSources(destination,sources);choices.addView(sourceDestinationRow(destination,selected,enter,update[0],alreadyAdded),new LinearLayout.LayoutParams(-1,dp(50)));
      }
      if(level.isEmpty()){TextView empty=text("此位置没有下级自建目录",12,MUTED);empty.setGravity(Gravity.CENTER);choices.addView(empty,new LinearLayout.LayoutParams(-1,dp(72)));}
      scroll.scrollTo(0,0);update[0].run();
    };
    back.setOnClickListener(v->{if(current[0]==null)return;current[0]=stack.isEmpty()?null:stack.remove(stack.size()-1);render[0].run();});createFolder.setOnClickListener(v->{SourceDestination here=current[0];if(here==null||here.categoryOnly())return;showCreateDestinationFolderDialog(here,updated->{copySource(updated,here.composite);render[0].run();});});
    prompt.setOnShowListener(ignored->{submit[0]=prompt.getButton(AlertDialog.BUTTON_POSITIVE);submit[0].setOnClickListener(v->{if(selected.isEmpty())return;List<SourceDestination> destinations=new ArrayList<>(selected.values());prompt.dismiss();applySelectedDestinations(sources,items,destinations);});render[0].run();});
    showRounded(prompt);
  }
  void showCreateDestinationFolderDialog(SourceDestination parent,java.util.function.Consumer<Models.Source> completed){EditText name=sourceInput("文件夹名称",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);LinearLayout panel=new LinearLayout(this);panel.setPadding(dp(22),dp(8),dp(22),0);panel.addView(name,new LinearLayout.LayoutParams(-1,dp(56)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("在当前路径创建文件夹").setView(panel).setNegativeButton("取消",null).setPositiveButton("创建",null).create();prompt.setOnShowListener(v->{Button create=prompt.getButton(AlertDialog.BUTTON_POSITIVE);create.setOnClickListener(x->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("请输入文件夹名称");return;}create.setEnabled(false);io.execute(()->{try{Models.Source updated=core.addCompositeFolder(parent.composite.id,parent.nodeId,value);runOnUiThread(()->{prompt.dismiss();completed.accept(updated);showNotice("文件夹已创建",false);});}catch(Exception error){runOnUiThread(()->{create.setEnabled(true);name.setError(friendlyError(error));});}});});});showRounded(prompt);}
  void applySelectedDestinations(List<Models.Source> rawSources,List<Models.Item> rawItems,List<SourceDestination> destinations){if(destinations==null||destinations.isEmpty())return;showNotice("正在加入 "+destinations.size()+" 个路径",false);List<Models.Source> sources=new ArrayList<>(rawSources==null?Collections.emptyList():rawSources);List<Models.Item> items=new ArrayList<>(rawItems==null?Collections.emptyList():rawItems);io.execute(()->{LinkedHashMap<String,String[]> links=new LinkedHashMap<>();LinkedHashSet<String> sourceIds=new LinkedHashSet<>();boolean categoryTarget=false;for(SourceDestination destination:destinations)categoryTarget|=destination.categoryOnly();int addedDirectories=0,duplicates=0,failed=0;try{List<Models.Source> known=core.sources();LinkedHashMap<String,Models.Source> knownById=new LinkedHashMap<>(),knownByUrl=new LinkedHashMap<>();for(Models.Source source:known){knownById.put(sourceKey(source),source);if(!source.url.isEmpty())knownByUrl.put(source.url,source);}for(Models.Source source:sources){String requestedId=source.kind==Models.SOURCE_COMPOSITE&&!source.id.isEmpty()?source.id:sourceKey(source);Models.Source knownSource=knownById.get(requestedId);if(knownSource!=null)sourceIds.add(sourceKey(knownSource));else if(categoryTarget&&!source.url.isEmpty())try{Models.Source stored=core.addUserSource(source.url,source.password);sourceIds.add(sourceKey(stored));knownById.put(sourceKey(stored),stored);knownByUrl.put(stored.url,stored);}catch(Exception error){failed++;}if(!source.url.isEmpty())links.putIfAbsent(source.url+'\n'+source.password,new String[]{source.url,source.password});}for(Models.Item item:items){if(item.sourceEntry)try{Models.Source source=core.sourceById(item.sourceId);if(source!=null){sourceIds.add(sourceKey(source));if(!source.url.isEmpty())links.putIfAbsent(source.url+'\n'+source.password,new String[]{source.url,source.password});continue;}}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}String url=item.shareUrl.isEmpty()?item.url:item.shareUrl;if(url.isEmpty()||!url.toLowerCase(Locale.ROOT).startsWith("http"))continue;links.putIfAbsent(url+'\n'+item.password,new String[]{url,item.password});if(categoryTarget){Models.Source knownSource=knownByUrl.get(url);if(knownSource!=null)sourceIds.add(sourceKey(knownSource));else try{Models.Source stored=core.addUserSource(url,item.password);sourceIds.add(sourceKey(stored));knownByUrl.put(stored.url,stored);}catch(Exception error){failed++;}}}for(SourceDestination destination:destinations)if(!destination.categoryOnly())for(String[] link:links.values())try{core.addCompositeNode(destination.composite.id,destination.nodeId,link[0],link[1]);addedDirectories++;}catch(Exception error){String message=error.getMessage()==null?"":error.getMessage();if(message.contains("已在当前文件夹"))duplicates++;else failed++;}List<Models.Source> updated=core.sources();int directoryCount=addedDirectories,duplicateCount=duplicates,failureCount=failed;runOnUiThread(()->{int categoryAdds=0;for(SourceDestination destination:destinations)if(destination.categoryOnly()){Set<String> category=sourceCategories.get(destination.category);if(category==null)continue;int before=category.size();category.addAll(sourceIds);categoryAdds+=category.size()-before;}if(categoryAdds>0)persistSourceCategories();++sourceDataRevision;if(selectionMode)exitSelection();if(sourceSelectionMode)exitSourceSelection();if(sourcePageSources!=null){sourcePageSources.clear();sourcePageSources.addAll(updated);if(sourceGrid!=null&&sourceHeading!=null){renderSourceCategories(sourcePageSources);renderSources(sourceGrid,sourcePageSources,sourceFilter==null?"":sourceFilter.getText().toString().trim(),sourceHeading);}}showNotice("已处理 "+destinations.size()+" 个路径 · 分类新增 "+categoryAdds+" · 目录新增 "+directoryCount+(duplicateCount>0?" · 重复 "+duplicateCount:"")+(failureCount>0?" · 失败 "+failureCount:""),failureCount>0);});}catch(Exception error){showNotice("批量加入失败："+friendlyError(error),true);}});}
  void addCurrentSourceToCategory(Models.Source source,String categoryName){showNotice("正在加入“"+categoryName+"”",false);io.execute(()->{try{String id=source.kind==Models.SOURCE_COMPOSITE&&!source.id.isEmpty()?source.id:sourceKey(source);boolean present=false;for(Models.Source candidate:core.sources())if(sourceKey(candidate).equals(id)){present=true;break;}if(!present){Models.Source stored=core.addUserSource(source.url,source.password);id=sourceKey(stored);}String sourceId=id;runOnUiThread(()->{LinkedHashSet<String> category=sourceCategories.get(categoryName);if(category==null){showNotice("分类已不存在",true);return;}boolean added=category.add(sourceId);if(added){persistSourceCategories();++sourceDataRevision;}showNotice(added?"已加入“"+categoryName+"”":"当前源已在“"+categoryName+"”",false);});}catch(Exception error){runOnUiThread(()->showNotice("加入分类失败："+friendlyError(error),true));}});}
  String sourceCategoryTagKey(Models.Source source){return "source-category-tags:"+sourceKey(source);}
  void replaceSourceCategoryTags(HorizontalScrollView scroll,Models.Source source){List<String> memberships=sourceCategoryMemberships(source);scroll.removeAllViews();LinearLayout tags=new LinearLayout(this);tags.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);for(String membership:memberships){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(22));p.setMargins(0,0,dp(4),0);tags.addView(sourceTag(membership),p);}scroll.addView(tags,new HorizontalScrollView.LayoutParams(-2,dp(22)));scroll.setVisibility(memberships.isEmpty()?View.GONE:View.VISIBLE);}
  void refreshSourcesAfterCategoryChange(Collection<Models.Source> changed){if(changed==null||changed.isEmpty()||sourceGrid==null||sourceGrid.getParent()==null)return;for(Models.Source source:changed){View tagged=sourceGrid.findViewWithTag(sourceCategoryTagKey(source));if(!(tagged instanceof HorizontalScrollView))continue;HorizontalScrollView tags=(HorizontalScrollView)tagged;Runnable replace=()->{replaceSourceCategoryTags(tags,source);if(tags.getVisibility()==View.VISIBLE&&motionEnabled()){tags.setAlpha(0f);tags.animate().alpha(1f).setDuration(100).setInterpolator(standardEase()).start();}else tags.setAlpha(1f);};tags.animate().cancel();if(motionEnabled()&&tags.getVisibility()==View.VISIBLE)tags.animate().alpha(0f).setDuration(100).setInterpolator(standardEase()).withEndAction(replace).start();else replace.run();}}
  void renameSelectedCompositeSource(){List<Models.Source> chosen=selectedSources();if(chosen.size()!=1||!compositeSource(chosen.get(0)))return;Models.Source source=chosen.get(0);EditText name=sourceInput("合集名称",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);name.setText(source.title);name.setSelection(name.length());LinearLayout panel=new LinearLayout(this);panel.setPadding(dp(22),dp(8),dp(22),0);panel.addView(name,new LinearLayout.LayoutParams(-1,dp(56)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("重命名自建合集").setView(panel).setNegativeButton("取消",null).setPositiveButton("保存",null).create();prompt.setOnShowListener(ignored->{Button save=prompt.getButton(AlertDialog.BUTTON_POSITIVE);save.setOnClickListener(v->{String title=name.getText().toString().trim();if(title.isEmpty()){name.setError("请输入合集名称");return;}save.setEnabled(false);io.execute(()->{try{Models.Source updated=core.updateCompositeMetadata(sourceKey(source),title,source.publisher,source.description);runOnUiThread(()->{copySource(updated,source);prompt.dismiss();exitSourceSelection();renderSources(sourceGrid,sourcePageSources,sourceFilter==null?"":sourceFilter.getText().toString().trim(),sourceHeading);showNotice("已重命名为“"+title+"”",false);});}catch(Exception error){runOnUiThread(()->{save.setEnabled(true);name.setError(friendlyError(error));});}});});});showRounded(prompt);}
  void renderSources(GridLayout grid,List<Models.Source> all,String query,TextView heading){
    int token=++sourceRenderSession;grid.removeAllViews();sourceSelectionChecks.clear();visibleSources.clear();int columns=itemColumns();grid.setColumnCount(columns);String key=query.toLowerCase(Locale.ROOT);Set<String> category=sourceCategories.get(activeSourceCategory);if(!activeSourceCategory.equals("全部")&&category==null)activeSourceCategory="全部";for(Models.Source source:all)if((activeSourceCategory.equals("全部")||category.contains(sourceKey(source)))&&(key.isEmpty()||folderMatchRank(cachedSourceSearchCorpus(source),key,sessionSearchFuzzyMatching)>=0))visibleSources.add(source);heading.setText("软件 · "+activeSourceCategory+" · "+visibleSources.size()+(key.isEmpty()?"":"/"+all.size()));renderSourceChunk(grid,new ArrayList<>(visibleSources),0,columns,token);if(sourceSelectionMode)updateSourceSelectionSummary();
  }
  void renderSourceChunk(GridLayout grid,List<Models.Source> sources,int start,int columns,int token){if(token!=sourceRenderSession||grid!=sourceGrid||grid.getParent()==null)return;int active=itemColumns();if(active!=columns)reflowGrid(grid,active,true);int batch=sourceListDisplayCount(),end=batch<=0?sources.size():Math.min(start+batch,sources.size());for(int i=start;i<end;i++){Models.Source source=sources.get(i);View row=sourceRow(source);GridLayout.LayoutParams gp=new GridLayout.LayoutParams();gp.width=gridCellWidth(active);gp.height=showSourceLinks?ViewGroup.LayoutParams.WRAP_CONTENT:dp(64);gp.columnSpec=GridLayout.spec(i%active);gp.rowSpec=GridLayout.spec(i/active);gp.setMargins(dp(4),dp(1),dp(4),dp(1));grid.addView(row,gp);}if(sourceSelectionMode)updateSourceSelectionSummary();grid.postOnAnimation(()->fillSourceViewport(token,sources));}
  void fillSourceViewport(int token,List<Models.Source> sources){if(token!=sourceRenderSession||sourceGrid==null||pageScroll==null||pageKind!=1)return;int rendered=sourceGrid.getChildCount();if(rendered>=sources.size())return;int required=pageScroll.getHeight()+dp(240);if(sourceGrid.getHeight()<required)renderSourceChunk(sourceGrid,sources,rendered,itemColumns(),token);}
  void maybeLoadMoreSources(){if(pageKind!=1||sourceGrid==null||pageScroll==null||sourceGrid.getChildCount()>=visibleSources.size())return;if(pageScroll.getScrollY()+pageScroll.getHeight()+dp(320)<sourceGrid.getHeight())return;renderSourceChunk(sourceGrid,new ArrayList<>(visibleSources),sourceGrid.getChildCount(),itemColumns(),sourceRenderSession);}
  /**
   * [DFW-71] 资源源管理是不是**从设置页进来的**——决定它的返回目标是设置页还是首页。
   *
   * 缺陷根因（并行诊断确认，且**必然发生而非偶发**）：
   * 设置里的「资源源管理」调的是 `showSources()`，它把 `systemBackAction` 清成 null、目的地设成 1；
   * 返回时落进 `performSystemBack()` 的兜底 `if(primaryDestination>0) navigateHome()` → **软件库**。
   * 用户的原话"有时候会突然回到软件库界面"，"有时候"其实取决于他点的是哪个按钮。
   */
  boolean sourceListFromSettings;

  /** [DFW-71] 从设置页进入资源源管理：返回时回**设置页**。 */
  /**
   * [DFW-75] 「本次是**首次进入**源列表，不是从某个源/文件夹返回」。
   *
   * 与 `sourceListFromSettings` 是两件事，不能合并：
   *  - `sourceListFromSettings` 决定**返回去哪**（设置 or 软件库），整页存活期间都不该被清掉；
   *  - 本标志只决定**转场方向**（首次进入=推入 +1，从文件夹返回=弹出 -1），用掉即清。
   *  之前两者都没有，恢复留存态时一律按 `-1`（弹出）播，于是用户从设置点进「资源源管理」
   *  看到的是**返回动画**——这就是"点进去后加载动画有问题"。
   */
  boolean sourceListEntry;
  void showSourcesFromSettings(){sourceListFromSettings=true;sourceListEntry=true;openSourceList();}

  void showSources(){sourceListFromSettings=false;sourceListEntry=true;openSourceList();}

  /** 真正的开页逻辑；两个入口共用，**标志位由调用方设置**（不能在内部清，否则会互相覆盖）。 */
  void openSourceList(){if(restoreSourceListPage())return;SourceListPageState fallback=retainedSourceListPage!=null?retainedSourceListPage:sourceListRebuildFallback;retainedSourceListPage=null;sourceListRebuildFallback=null;showSources(fallback);}
  void showSources(SourceListPageState fallback){
    String restoredQuery=fallback==null?"":fallback.query,restoredCategory=fallback==null?activeSourceCategory:fallback.activeSourceCategory;boolean restoreSelection=fallback!=null&&fallback.sourceSelectionMode;primaryBase(1);pageKind=1;final int viewSession=navigationSession;clearFolderTrail();activeSource=null;systemBackAction=sourceListFromSettings?this::showSettings:null;loadSourceCategories();if(restoredCategory.equals("全部")||sourceCategories.containsKey(restoredCategory))activeSourceCategory=restoredCategory;if(fallback!=null){selectedSourceUrls.addAll(fallback.selectedSourceUrls);System.arraycopy(fallback.sourceSelectionKinds,0,sourceSelectionKinds,0,sourceSelectionKinds.length);}primaryHeader("更多");List<Models.Source> all=new ArrayList<>();sourcePageSources=all;sourceGrid=new GridLayout(this);sourceGrid.setColumnCount(itemColumns());sourceGrid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);ImageButton importRules=iconButton(R.drawable.ic_folder,"导入源规则"),exportRules=iconButton(R.drawable.ic_share,"导出源规则"),add=iconButton(R.drawable.ic_add,"添加蓝奏源");pageHeaderRow.addView(importRules,new LinearLayout.LayoutParams(dp(44),dp(48)));pageHeaderRow.addView(exportRules,new LinearLayout.LayoutParams(dp(44),dp(48)));pageHeaderRow.addView(add,new LinearLayout.LayoutParams(dp(44),dp(48)));sourceHeading=text("软件 · 正在读取",12,MUTED);sourceHeading.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);sourceHeading.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);root.addView(sourceHeading,new LinearLayout.LayoutParams(-1,dp(32)));sourceFilter=pageSearch(query->queueSourceListFilter(all,query,sourceHeading));if(!restoredQuery.isEmpty()){sourceFilter.setText(restoredQuery);sourceFilter.setSelection(restoredQuery.length());}addSourceCategoryBar(all);importRules.setOnClickListener(v->showImportRulesMenu());exportRules.setOnClickListener(v->exportSourceRules());add.setOnClickListener(v->addSourceDialog(all,sourceGrid,sourceFilter,sourceHeading,activeSourceCategory));pageScroll=new ScrollView(this);pageScroll.addView(sourceGrid);root.addView(draggableList(pageScroll,sourceGrid,"拖动软件列表"),new LinearLayout.LayoutParams(-1,0,1));
    if(fallback!=null&&fallback.sourceDataRevision==sourceDataRevision&&fallback.sourcePageSources!=null&&!fallback.sourcePageSources.isEmpty()){all.addAll(fallback.sourcePageSources);renderSources(sourceGrid,all,restoredQuery,sourceHeading);if(restoreSelection){enterSourceSelection();System.arraycopy(fallback.sourceSelectionKinds,0,sourceSelectionKinds,0,sourceSelectionKinds.length);updateSourceSelectionSummary();}}else loadSourcePageSnapshot(viewSession,all,sourceGrid,sourceFilter,sourceHeading);
  }
    void loadSourcePageSnapshot(int viewSession,List<Models.Source> all,GridLayout grid,EditText filter,TextView heading){final int loadRevision=sourceDataRevision;io.execute(()->{try{List<Models.Source> sources=core.sources();LinkedHashMap<String,String> corpora=buildSourceSearchCorpora(sources);LinkedHashMap<String,LinkedHashSet<String>> categories=migrateSourceCategoryIds(sources);runOnUiThread(()->{if(viewSession!=navigationSession||grid!=sourceGrid||all!=sourcePageSources)return;if(loadRevision!=sourceDataRevision){loadSourcePageSnapshot(viewSession,all,grid,filter,heading);return;}sourceCategories.clear();sourceCategories.putAll(categories);sourceSearchCorpora.clear();sourceSearchCorpora.putAll(corpora);sourceSearchCorporaRevision=sourceDataRevision;if(!activeSourceCategory.equals("全部")&&!sourceCategories.containsKey(activeSourceCategory))activeSourceCategory="全部";all.clear();all.addAll(sources);renderSourceCategories(all);renderSources(grid,all,filter.getText().toString().trim(),heading);});}catch(Exception error){runOnUiThread(()->{if(viewSession!=navigationSession||grid!=sourceGrid||all!=sourcePageSources)return;if(loadRevision!=sourceDataRevision){loadSourcePageSnapshot(viewSession,all,grid,filter,heading);return;}MainActivity.this.error(error);});}});}
  LinkedHashMap<String,LinkedHashSet<String>> migrateSourceCategoryIds(List<Models.Source> known){LinkedHashMap<String,String> aliases=new LinkedHashMap<>();for(Models.Source source:known){String key=sourceKey(source);if(!source.id.isEmpty())aliases.put(source.id,key);if(!source.url.isEmpty())aliases.put(source.url,key);}LinkedHashMap<String,LinkedHashSet<String>> result=new LinkedHashMap<>();boolean migrated=false;try{org.json.JSONArray groups=new org.json.JSONArray(getSharedPreferences("source_categories",MODE_PRIVATE).getString("items","[]"));for(int i=0;i<groups.length();i++){org.json.JSONObject group=groups.optJSONObject(i);if(group==null)continue;String name=group.optString("name").trim();if(name.isEmpty()||name.equals("全部")||result.containsKey(name))continue;LinkedHashSet<String> ids=new LinkedHashSet<>();org.json.JSONArray values=group.optJSONArray("ids");if(values==null){values=group.optJSONArray("urls");migrated|=values!=null;}if(values!=null)for(int j=0;j<values.length();j++){String stored=values.optString(j);if(stored.isEmpty())continue;String resolved=aliases.getOrDefault(stored,stored);migrated|=!resolved.equals(stored);ids.add(resolved);}result.put(name,ids);}}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}if(migrated){org.json.JSONArray groups=new org.json.JSONArray();try{for(Map.Entry<String,LinkedHashSet<String>> group:result.entrySet())groups.put(new org.json.JSONObject().put("name",group.getKey()).put("ids",new org.json.JSONArray(group.getValue())));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}getSharedPreferences("source_categories",MODE_PRIVATE).edit().putString("items",groups.toString()).apply();}return result;}
  void loadSourceCategories(){sourceCategories.clear();sourceCategories.putAll(migrateSourceCategoryIds(Collections.emptyList()));if(!activeSourceCategory.equals("全部")&&!sourceCategories.containsKey(activeSourceCategory))activeSourceCategory="全部";}
  void persistSourceCategories(){org.json.JSONArray groups=new org.json.JSONArray();try{for(Map.Entry<String,LinkedHashSet<String>> group:sourceCategories.entrySet())groups.put(new org.json.JSONObject().put("name",group.getKey()).put("ids",new org.json.JSONArray(group.getValue())));}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}getSharedPreferences("source_categories",MODE_PRIVATE).edit().putString("items",groups.toString()).apply();}
  void addSourceCategoryBar(List<Models.Source> all){HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(false);sourceCategoryStrip=new LinearLayout(this);sourceCategoryStrip.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);scroll.addView(sourceCategoryStrip,new HorizontalScrollView.LayoutParams(-2,-1));root.addView(scroll,new LinearLayout.LayoutParams(-1,dp(48)));renderSourceCategories(all);}
  void renderSourceCategories(List<Models.Source> all){if(sourceCategoryStrip==null)return;sourceCategoryStrip.removeAllViews();addSourceCategoryChip("全部",all);for(String name:sourceCategories.keySet())addSourceCategoryChip(name,all);}
  void addSourceCategoryChip(String name,List<Models.Source> all){boolean selected=name.equals(activeSourceCategory);View chip=underlineFilter(name,selected,()->{if(sourceSelectionMode)exitSourceSelection();activeSourceCategory=name;transitionSourceFilter(()->renderSources(sourceGrid,all,sourceFilter.getText().toString().trim(),sourceHeading));});if(!name.equals("全部"))chip.setOnLongClickListener(v->{showSourceCategorySelection(name,all);return true;});LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,dp(38));p.setMargins(0,dp(4),dp(6),dp(4));sourceCategoryStrip.addView(chip,p);}
  void showSourceCategorySelection(String initial,List<Models.Source> all){if(sourceCategories.isEmpty()){showNotice("暂无可管理的分类",false);return;}List<String> names=new ArrayList<>(sourceCategories.keySet());LinkedHashSet<String> chosen=new LinkedHashSet<>();if(names.contains(initial))chosen.add(initial);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(4),dp(16),dp(4));TextView summary=text("已选 "+chosen.size(),13,MUTED);panel.addView(summary,new LinearLayout.LayoutParams(-1,dp(38)));LinearLayout choices=new LinearLayout(this);choices.setOrientation(LinearLayout.VERTICAL);View[] renameAction={null};Runnable refresh=()->{summary.setText("已选 "+chosen.size());if(renameAction[0]!=null)renameAction[0].setVisibility(chosen.size()==1?View.VISIBLE:View.GONE);};for(String name:names){CheckBox option=new CheckBox(this);option.setText(name);option.setTextColor(TEXT);option.setChecked(chosen.contains(name));option.setContentDescription("选择分类 "+name);option.setOnCheckedChangeListener((button,checked)->{if(checked)chosen.add(name);else chosen.remove(name);refresh.run();});choices.addView(option,new LinearLayout.LayoutParams(-1,dp(46)));}ScrollView list=new ScrollView(this);list.setVerticalScrollBarEnabled(names.size()>5);list.addView(choices);panel.addView(list,new LinearLayout.LayoutParams(-1,Math.min(dp(230),names.size()*dp(46))));LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);View rename=settingsAction(R.drawable.ic_edit,"重命名",v->{if(chosen.size()==1)showRenameSourceCategory(chosen.iterator().next(),all);}),share=settingsAction(R.drawable.ic_share,"分享",v->{List<Models.Source> selected=categorySources(chosen,all);if(selected.isEmpty())showNotice("所选分类没有源",false);else shareSources(selected);}),delete=settingsAction(R.drawable.ic_delete_record,"删除",v->{if(chosen.isEmpty())return;confirmDeleteSourceCategories(new LinkedHashSet<>(chosen),all);});renameAction[0]=rename;actions.addView(rename,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(share,new LinearLayout.LayoutParams(0,dp(48),1));actions.addView(delete,new LinearLayout.LayoutParams(0,dp(48),1));panel.addView(actions,new LinearLayout.LayoutParams(-1,dp(48)));refresh.run();AlertDialog prompt=new AlertDialog.Builder(this).setTitle("管理分类").setView(panel).setNegativeButton("关闭",null).create();showRounded(prompt);}
  List<Models.Source> categorySources(Collection<String> categories,List<Models.Source> all){LinkedHashSet<String> ids=new LinkedHashSet<>();for(String name:categories){Set<String> values=sourceCategories.get(name);if(values!=null)ids.addAll(values);}List<Models.Source> out=new ArrayList<>();for(Models.Source source:all)if(ids.contains(sourceKey(source)))out.add(source);return out;}
  void showRenameSourceCategory(String old,List<Models.Source> all){EditText name=sourceInput("分类名称",android.text.InputType.TYPE_CLASS_TEXT);name.setText(old);name.setSelection(name.length());LinearLayout panel=new LinearLayout(this);panel.setPadding(dp(22),dp(8),dp(22),0);panel.addView(name,new LinearLayout.LayoutParams(-1,dp(56)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("重命名分类").setView(panel).setNegativeButton("取消",null).setPositiveButton("保存",null).create();prompt.setOnShowListener(ignored->prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()||value.equals("全部")){name.setError("请输入有效分类名称");return;}if(!value.equals(old)&&sourceCategories.containsKey(value)){name.setError("该分类已存在");return;}if(value.equals(old)){prompt.dismiss();return;}LinkedHashMap<String,LinkedHashSet<String>> renamed=new LinkedHashMap<>();for(Map.Entry<String,LinkedHashSet<String>> entry:sourceCategories.entrySet())renamed.put(entry.getKey().equals(old)?value:entry.getKey(),entry.getValue());sourceCategories.clear();sourceCategories.putAll(renamed);if(activeSourceCategory.equals(old))activeSourceCategory=value;if(sessionSearchCategory.equals(old))sessionSearchCategory=value;persistSourceCategories();persistSearchSettings();prompt.dismiss();renderSourceCategories(all);renderSources(sourceGrid,all,sourceFilter.getText().toString().trim(),sourceHeading);showNotice("已重命名为“"+value+"”",false);}));showRounded(prompt);}
  void confirmDeleteSourceCategories(Set<String> names,List<Models.Source> all){if(names.isEmpty())return;AlertDialog prompt=new AlertDialog.Builder(this).setTitle("删除 "+names.size()+" 个分类？").setMessage("只删除分类，源仍保留在“全部”。").setNegativeButton("取消",null).setPositiveButton("删除",(dialog,which)->{for(String name:names)sourceCategories.remove(name);if(names.contains(activeSourceCategory))activeSourceCategory="全部";if(names.contains(sessionSearchCategory))sessionSearchCategory="全部";persistSourceCategories();persistSearchSettings();renderSourceCategories(all);renderSources(sourceGrid,all,sourceFilter.getText().toString().trim(),sourceHeading);showNotice("已删除 "+names.size()+" 个分类",false);}).create();showRounded(prompt);}
  void chooseSourceCategory(){List<Models.Source> chosen=selectedSources();if(chosen.isEmpty())return;LinkedHashMap<String,Models.Source> unique=new LinkedHashMap<>();for(Models.Source source:chosen)unique.putIfAbsent(LanzouCore.sourceId(source),source);showSourceDestinationPicker(new ArrayList<>(unique.values()),Collections.emptyList());}
  void confirmRemoveSelectedSources(){List<Models.Source> chosen=selectedSources();if(chosen.isEmpty())return;boolean all=activeSourceCategory.equals("全部");String message=all?"将从全部源中移除 "+chosen.size()+" 个源，并同步移出所有分类。移除后不再参与全源搜索。":"将从“"+activeSourceCategory+"”移出 "+chosen.size()+" 个源，源仍保留在“全部”。";AlertDialog prompt=new AlertDialog.Builder(this).setTitle(all?"确认移除源？":"确认移出分类？").setMessage(message).setNegativeButton("取消",null).setPositiveButton("移除",(d,w)->removeSelectedSources(chosen,all)).create();showRounded(prompt);}
  void removeSelectedSources(List<Models.Source> chosen,boolean all){Set<String> ids=new LinkedHashSet<>();for(Models.Source source:chosen)ids.add(sourceKey(source));if(!all){Set<String> category=sourceCategories.get(activeSourceCategory);if(category!=null)category.removeAll(ids);persistSourceCategories();exitSourceSelection();renderSourceCategories(sourcePageSources);renderSources(sourceGrid,sourcePageSources,sourceFilter.getText().toString().trim(),sourceHeading);showNotice("已移出 "+chosen.size()+" 个源",false);return;}io.execute(()->{try{core.removeSources(ids);List<Models.Source> updated=core.sources();runOnUiThread(()->{++sourceDataRevision;for(Set<String> category:sourceCategories.values())category.removeAll(ids);persistSourceCategories();if(sourceGrid!=null){sourcePageSources.clear();sourcePageSources.addAll(updated);exitSourceSelection();renderSourceCategories(sourcePageSources);renderSources(sourceGrid,sourcePageSources,sourceFilter.getText().toString().trim(),sourceHeading);}showNotice("已从全部源移除 "+chosen.size()+" 个源",false);});}catch(Exception error){runOnUiThread(()->showNotice(friendlyError(error),true));}});}
  void enterSourceSelection(){if(sourceSelectionMode)return;sourceSelectionMode=true;Arrays.fill(sourceSelectionKinds,true);sourceSelectionSummary=text("已选 0",11,TEXT);sourceSelectionAllButton=toolbarTextButton("按类全选");sourceSelectionAllButton.setOnClickListener(v->showSourceTypeSelectionDialog());ImageButton copy=iconButton(R.drawable.ic_copy,"复制所选源链接"),share=iconButton(R.drawable.ic_share,"分享所选源链接"),categorize=iconButton(R.drawable.ic_add,"加入分类"),retest=iconButton(R.drawable.ic_search,"重新测试所选源"),remove=iconButton(R.drawable.ic_delete_record,"移出当前分类"),close=iconButton(R.drawable.ic_close,"退出源多选");sourceSelectionRename=iconButton(R.drawable.ic_edit,"重命名自建合集");sourceSelectionRename.setVisibility(View.GONE);copy.setOnClickListener(v->copySelectedSources());share.setOnClickListener(v->shareSelectedSources());categorize.setOnClickListener(v->chooseSourceCategory());sourceSelectionRename.setOnClickListener(v->renameSelectedCompositeSource());retest.setOnClickListener(v->confirmRetestSelectedSources());remove.setOnClickListener(v->confirmRemoveSelectedSources());close.setOnClickListener(v->exitSourceSelection());sourceSelectionBar=makeSelectionBar(sourceSelectionSummary,sourceSelectionAllButton,82,32,new String[]{"复制","分享","加入分类","重命名","重测","移出","退出"},copy,share,categorize,sourceSelectionRename,retest,remove,close);showChecks(sourceSelectionChecks,true);updateSourceSelectionSummary();}
  void toggleSourceSelection(Models.Source source){if(!sourceSelectionMode)enterSourceSelection();if(toggleChosen(selectedSourceUrls,LanzouCore.sourceId(source),sourceSelectionChecks))exitSourceSelection();else updateSourceSelectionSummary();}
  void showSourceTypeSelectionDialog(){if(visibleSources.isEmpty()){showNotice("当前没有可选择的源",false);return;}String[] labels={"列表源","自建目录","子目录","软件源"};boolean[] checked=sourceSelectionKinds.clone();AlertDialog picker=new AlertDialog.Builder(this).setTitle("按类型全选").setMultiChoiceItems(labels,checked,(dialog,index,value)->checked[index]=value).setNegativeButton("取消",null).setNeutralButton("清空当前",null).setPositiveButton("应用全选",null).create();picker.setOnShowListener(ignored->{picker.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{Arrays.fill(checked,false);for(int i=0;i<checked.length;i++)picker.getListView().setItemChecked(i,false);applySourceTypeSelection(checked);picker.dismiss();});picker.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{boolean any=false;for(boolean value:checked)any|=value;if(!any){showNotice("请至少勾选一种来源类型，或使用清空当前",false);return;}applySourceTypeSelection(checked);picker.dismiss();});});showRounded(picker);}
  void applySourceTypeSelection(boolean[] enabled){List<Models.Source> currentVisible=new ArrayList<>(visibleSources);List<String> visibleIds=new ArrayList<>();for(Models.Source source:currentVisible)visibleIds.add(sourceKey(source));selectedSourceUrls.removeAll(visibleIds);for(int i=0;i<sourceSelectionKinds.length;i++)sourceSelectionKinds[i]=enabled!=null&&i<enabled.length&&enabled[i];for(Models.Source source:currentVisible)if(sourceMatchesSelectionKinds(source,sourceSelectionKinds))selectedSourceUrls.add(sourceKey(source));syncChecks(selectedSourceUrls,sourceSelectionChecks);if(selectedSourceUrls.isEmpty())exitSourceSelection();else updateSourceSelectionSummary();}
  List<Models.Source> selectedSources(){List<Models.Source> chosen=new ArrayList<>();if(sourcePageSources!=null)for(Models.Source source:sourcePageSources)if(selectedSourceUrls.contains(sourceKey(source)))chosen.add(source);return chosen;}
  void updateSourceSelectionSummary(){List<Models.Source> chosen=selectedSources();if(sourceSelectionSummary!=null)sourceSelectionSummary.setText("已选 "+selectedSourceUrls.size());if(sourceSelectionAllButton!=null)sourceSelectionAllButton.setText("按类全选");if(sourceSelectionRename!=null){boolean show=chosen.size()==1&&compositeSource(chosen.get(0));if(sourceSelectionRename.getVisibility()!=(show?View.VISIBLE:View.GONE)){sourceSelectionRename.setVisibility(show?View.VISIBLE:View.GONE);if(sourceSelectionBar!=null&&sourceSelectionBar.getTag() instanceof SelectionBarLayout){((SelectionBarLayout)sourceSelectionBar.getTag()).available=-1;layoutSelectionBar(sourceSelectionBar);}}}}
  String selectedSourcesText(List<Models.Source> chosen){LinkedHashSet<String> lines=new LinkedHashSet<>();for(Models.Source source:chosen)appendSourceInputLines(lines,source);return String.join("\n",lines);}
  void copySelectedSources(){List<Models.Source> chosen=selectedSources();if(chosen.isEmpty())return;((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("蓝奏云源",selectedSourcesText(chosen)));showNotice("已复制 "+chosen.size()+" 个源",false);}
  void shareSelectedSources(){List<Models.Source> chosen=selectedSources();if(chosen.isEmpty())return;shareSources(chosen);}
  void shareSources(List<Models.Source> chosen){if(chosen==null||chosen.isEmpty())return;AlertDialog prompt=new AlertDialog.Builder(this).setTitle("选择分享方式").setItems(new String[]{"分享文字","分享可导入的源文件"},(dialog,index)->{if(index==0)shareText(selectedSourcesText(chosen),"分享蓝奏云源");else shareImportableSources(chosen);}).setNegativeButton("取消",null).create();showRounded(prompt);}
  void shareImportableSources(List<Models.Source> chosen){LinkedHashSet<String> ids=new LinkedHashSet<>();for(Models.Source source:chosen)ids.add(source.kind==Models.SOURCE_COMPOSITE&&!source.id.isEmpty()?source.id:sourceKey(source));io.execute(()->{try{String json=core.exportSourceRules(ids);runOnUiThread(()->shareRulesJson(json));}catch(Exception error){showNotice("生成可导入源失败："+friendlyError(error),true);}});}
  void shareRulesJson(String json){Intent send=new Intent(Intent.ACTION_SEND);send.setType("application/json");send.putExtra(Intent.EXTRA_TEXT,json);send.putExtra(Intent.EXTRA_TITLE,PRODUCT_NAME+" 可导入源.json");try{startActivity(Intent.createChooser(send,"分享可导入的源文件"));}catch(Exception error){showNotice("没有可用的分享应用",true);}}
  void confirmRetestSelectedSources(){List<Models.Source> chosen=selectedSources();chosen.removeIf(source->testingSourceUrls.contains(sourceKey(source)));if(chosen.isEmpty()){showNotice("所选源正在测试中",false);return;}int units=0;for(Models.Source source:chosen)units+=compositeSource(source)?Math.max(1,LanzouCore.realMemberCount(source)):1;int totalUnits=Math.max(1,units);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(4),dp(22),0);TextView note=text("将重新检测连通性、标题、搜索能力与最佳 UA。内部真实源与普通源进入同一并发队列；失败会保留原节点和分类。",12,MUTED);note.setMaxLines(3);TextView concurrency=text("并发测试：全部（"+totalUnits+" 项）",13,TEXT);LumaSlider bar=new LumaSlider(this);bar.setMax(totalUnits-1);bar.setProgressValue(totalUnits-1,false);bar.setEnabled(totalUnits>1);bar.setContentDescription("调整本次重新测试并发数");bar.setOnChangeListener((value,user)->{int selected=value+1;concurrency.setText(selected>=totalUnits?"并发测试：全部（"+totalUnits+" 项）":"并发测试："+selected+" / "+totalUnits);});panel.addView(note,new LinearLayout.LayoutParams(-1,dp(58)));panel.addView(concurrency,new LinearLayout.LayoutParams(-1,dp(34)));panel.addView(bar,new LinearLayout.LayoutParams(-1,dp(56)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("是否重新测试 "+chosen.size()+" 个源？").setView(panel).setNegativeButton("取消",null).setPositiveButton("测试",(dialog,which)->{int selected=bar.getProgress()+1;retestSelectedSources(chosen,selected>=totalUnits?0:selected);}).create();showRounded(prompt);}
  void retestSelectedSources(List<Models.Source> chosen,int concurrency){if(chosen.isEmpty())return;for(Models.Source source:chosen)testingSourceUrls.add(sourceKey(source));if(sourceGrid!=null&&sourcePageSources!=null)renderSources(sourceGrid,sourcePageSources,sourceFilter==null?"":sourceFilter.getText().toString().trim(),sourceHeading);int[] success={0},failed={0},skipped={0};LinearLayout panel=toastPanel();TextView label=text("准备测试 "+chosen.size()+" 个源",11,TEXT);label.setMaxLines(2);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);bar.setProgress(0);panel.addView(label,new LinearLayout.LayoutParams(-1,dp(42)));panel.addView(bar,new LinearLayout.LayoutParams(-1,dp(4)));addToastPanel(panel,62);io.execute(()->{try{core.retestSources(chosen,concurrency,new Models.SourceTestProgress(){@Override public void onMember(String sourceId,String memberTitle,int done,int total,boolean ok){String owner=sourceId;for(Models.Source source:chosen)if(sourceKey(source).equals(sourceId)){owner=source.title;break;}String ownerTitle=owner;runOnUiThread(()->{if(panel.getParent()!=toastLayer)return;bar.setProgress(done*100/Math.max(1,total));label.setText(ownerTitle+" · "+memberTitle+" · "+(ok?"通过":"失败")+"\n"+done+" / "+total);});}@Override public void onResult(Models.SourceTestResult result,int done,int total){runOnUiThread(()->{testingSourceUrls.remove(result.originalUrl);if(!result.applied)skipped[0]++;else{++sourceDataRevision;if(result.success)success[0]++;else failed[0]++;if(sourcePageSources!=null)for(Models.Source source:sourcePageSources)if(sourceKey(source).equals(result.originalUrl)){copySource(result.source,source);break;}}if(sourcePageSources!=null&&sourceGrid!=null&&sourceHeading!=null)renderSources(sourceGrid,sourcePageSources,sourceFilter==null?"":sourceFilter.getText().toString().trim(),sourceHeading);if(done==total){if(panel.getParent()==toastLayer)dismissPanel(panel,null);String message="测试完成："+success[0]+" 成功，"+failed[0]+" 错误"+(skipped[0]>0?"，"+skipped[0]+" 已跳过（源已变更）":"");showNotice(message,failed[0]>0);}});}});}catch(InterruptedException interrupted){Thread.currentThread().interrupt();runOnUiThread(()->{if(panel.getParent()==toastLayer)dismissPanel(panel,null);});finishRetestFailure(chosen,"重新测试已中断");}catch(Exception error){runOnUiThread(()->{if(panel.getParent()==toastLayer)dismissPanel(panel,null);});finishRetestFailure(chosen,"重新测试失败："+friendlyError(error));}});}
  void finishRetestFailure(List<Models.Source> chosen,String message){runOnUiThread(()->{for(Models.Source source:chosen)testingSourceUrls.remove(sourceKey(source));if(sourceGrid!=null&&sourcePageSources!=null)renderSources(sourceGrid,sourcePageSources,sourceFilter==null?"":sourceFilter.getText().toString().trim(),sourceHeading);showNotice(message,true);});}
  void exitSourceSelection(){sourceSelectionMode=false;resetSelection(selectedSourceUrls,sourceSelectionChecks,sourceSelectionBar);sourceSelectionBar=null;sourceSelectionSummary=null;sourceSelectionAllButton=null;sourceSelectionRename=null;}
  EditText sourceInput(String hint,int inputType){EditText input=new EditText(this);input.setSingleLine(true);input.setHint(hint);input.setContentDescription(hint);input.setTextColor(TEXT);input.setHintTextColor(MUTED);input.setTextSize(14);input.setInputType(inputType);input.setImeOptions(EditorInfo.IME_ACTION_NEXT|EditorInfo.IME_FLAG_NO_EXTRACT_UI);return input;}
  void setAddSourceMode(int mode,LinearLayout sourcePanel,LinearLayout compositePanel,LinearLayout categoryPanel,Button confirm){sourcePanel.setVisibility(mode==0?View.VISIBLE:View.GONE);compositePanel.setVisibility(mode==1?View.VISIBLE:View.GONE);categoryPanel.setVisibility(mode==2?View.VISIBLE:View.GONE);confirm.setText(mode==0?"识别并添加":"创建");}
  void setViewTreeEnabled(View view,boolean enabled){view.setEnabled(enabled);if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)setViewTreeEnabled(group.getChildAt(i),enabled);}}
  void showAddCompositeFolderDialog(){if(!compositeSource(activeSource))return;EditText name=sourceInput("文件夹名称",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);LinearLayout panel=new LinearLayout(this);panel.setPadding(dp(22),dp(8),dp(22),0);panel.addView(name,new LinearLayout.LayoutParams(-1,dp(56)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("新建文件夹").setView(panel).setNegativeButton("取消",null).setPositiveButton("创建",null).create();prompt.setOnShowListener(x->{Button confirm=prompt.getButton(AlertDialog.BUTTON_POSITIVE);confirm.setOnClickListener(v->{String value=name.getText().toString().trim();if(value.isEmpty()){name.setError("请输入文件夹名称");return;}confirm.setEnabled(false);Models.Source target=activeSource;io.execute(()->{try{Models.Source updated=core.addCompositeFolder(sourceKey(target),target.nodeId,value);runOnUiThread(()->{prompt.dismiss();refreshCompositeAfterEdit(target,updated,"已创建文件夹");});}catch(Exception error){runOnUiThread(()->{confirm.setEnabled(true);name.setError(friendlyError(error));});}});});});showRounded(prompt);}
  void showAddCompositeNodeDialog(){if(!compositeSource(activeSource))return;LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(4),dp(22),0);EditText link=sourceInput("蓝奏单软件或合集链接",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI),password=sourceInput("密码（没有可不填）",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);panel.addView(link,new LinearLayout.LayoutParams(-1,dp(56)));panel.addView(password,new LinearLayout.LayoutParams(-1,dp(56)));TextView note=text("单软件会成为项目；合集或列表会自动成为可进入的文件夹。",11,MUTED);note.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);panel.addView(note,new LinearLayout.LayoutParams(-1,dp(44)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("添加到当前目录").setView(panel).setNegativeButton("取消",null).setPositiveButton("识别并添加",null).create();prompt.setOnShowListener(x->{Button confirm=prompt.getButton(AlertDialog.BUTTON_POSITIVE);confirm.setOnClickListener(v->{String url=link.getText().toString().trim();if(url.isEmpty()){link.setError("请输入蓝奏链接");return;}confirm.setEnabled(false);confirm.setText("识别中…");Models.Source target=activeSource;io.execute(()->{try{Models.Source updated=core.addCompositeNode(sourceKey(target),target.nodeId,url,password.getText().toString().trim());runOnUiThread(()->{prompt.dismiss();refreshCompositeAfterEdit(target,updated,"已添加到当前目录");});}catch(Exception error){runOnUiThread(()->{confirm.setEnabled(true);confirm.setText("识别并添加");link.setError(friendlyError(error));});}});});});showRounded(prompt);}
  void refreshCompositeAfterEdit(Models.Source target,Models.Source updated,String message){if(target==null||updated==null)return;for(FolderPageState state:folderTrail)if(compositeSource(state.source)&&state.source.id.equals(updated.id)){String node=state.source.nodeId,title=state.source.title;copySource(updated,state.source);state.source.nodeId=node;state.source.title=title;state.folder=null;}if(activeSource!=null&&sourceKey(activeSource).equals(sourceKey(target))){String title=activeSource.title;copySource(updated,activeSource);activeSource.title=title;if(activeFolderState!=null){copySource(activeSource,activeFolderState.source);activeFolderState.folder=null;}pageDirection=0;showFolderPage(activeSource,true);}showNotice(message,false);}
  String addBatchSummary(LanzouCore.AddBatchResult result,boolean recursive){int rootAdded=Math.max(0,result.added-result.childAdded),rootDuplicates=Math.max(0,result.duplicates-result.childDuplicates);String value="添加完成："+rootAdded+" 新增，"+rootDuplicates+" 重复，"+result.failed+" 失败";if(recursive)value+="；子目录 "+result.childAdded+" 新增，"+result.childDuplicates+" 重复，"+result.childEmpty+" 空目录，"+result.childFailed+" 异常";return value;}
  void showSourceAddFailures(LanzouCore.AddBatchResult result,String summary){StringBuilder report=new StringBuilder(summary==null?"批量添加完成":summary);int failures=0;for(LanzouCore.AddResult line:result.lines)if(!line.added&&!line.duplicate){failures++;report.append("\n\n");if(line.line>0)report.append("第 ").append(line.line).append(" 行\n");report.append(line.url.isEmpty()?"未识别到有效链接":line.url).append("\n原因：").append(line.message.isEmpty()?"暂时无法识别":line.message);}if(result.childFailed>0)report.append("\n\n递归子目录异常：").append(result.childFailed).append(" 项");String details=report.toString();TextView content=text(details,13,TEXT);content.setTextIsSelectable(true);content.setLineSpacing(dp(3),1f);content.setPadding(dp(22),dp(10),dp(22),dp(12));AlertDialog dialog=new AlertDialog.Builder(this).setTitle("添加失败详情 · "+failures+" 项").setView(limitedDialogScroll(content,460)).setNegativeButton("关闭",null).setNeutralButton("复制全部",(ignored,which)->{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard!=null)clipboard.setPrimaryClip(ClipData.newPlainText("添加源失败详情",details));showNotice("失败详情已复制",false);}).create();showRounded(dialog);}
  void addSourceDialog(List<Models.Source> all,GridLayout grid,EditText filter,TextView heading,String targetCategory){
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(4),dp(22),dp(8));RadioGroup modes=new RadioGroup(this);modes.setOrientation(RadioGroup.HORIZONTAL);modes.setContentDescription("添加类型");RadioButton sourceMode=new RadioButton(this),compositeMode=new RadioButton(this),categoryMode=new RadioButton(this);sourceMode.setId(View.generateViewId());compositeMode.setId(View.generateViewId());categoryMode.setId(View.generateViewId());sourceMode.setText("源");sourceMode.setContentDescription("添加蓝奏源");compositeMode.setText("合集");compositeMode.setContentDescription("创建自建合集");categoryMode.setText("分类");categoryMode.setContentDescription("创建源分类");modes.addView(sourceMode,new RadioGroup.LayoutParams(0,dp(48),1));modes.addView(compositeMode,new RadioGroup.LayoutParams(0,dp(48),1));modes.addView(categoryMode,new RadioGroup.LayoutParams(0,dp(48),1));sourceMode.setChecked(true);panel.addView(modes,new LinearLayout.LayoutParams(-1,dp(48)));
    LinearLayout officialPanel=new LinearLayout(this);officialPanel.setOrientation(LinearLayout.VERTICAL);LinearLayout sourceInputHeader=new LinearLayout(this);sourceInputHeader.setGravity(Gravity.CENTER_VERTICAL);TextView sourceInputHint=text("智能识别链接与密码；建议一行一链接便于核对",12,MUTED);ImageButton pasteSources=iconButton(R.drawable.ic_copy,"从剪贴板填入源链接");sourceInputHeader.addView(sourceInputHint,new LinearLayout.LayoutParams(0,dp(42),1));sourceInputHeader.addView(pasteSources,new LinearLayout.LayoutParams(dp(42),dp(42)));officialPanel.addView(sourceInputHeader,new LinearLayout.LayoutParams(-1,dp(42)));EditText link=sourceInput("链接、说明、密码/提取码/pwd 可混排",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_VARIATION_URI);link.setSingleLine(false);link.setMinLines(4);link.setMaxLines(8);link.setGravity(Gravity.TOP|Gravity.START);link.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);officialPanel.addView(link,new LinearLayout.LayoutParams(-1,dp(128)));CheckBox recursiveChildren=new CheckBox(this);recursiveChildren.setText("自动递归添加内部非空列表源");recursiveChildren.setTextColor(TEXT);recursiveChildren.setContentDescription("自动递归添加内部非空列表源");officialPanel.addView(recursiveChildren,new LinearLayout.LayoutParams(-1,dp(48)));TextView addProgress=text("准备并发识别",12,PRIMARY);addProgress.setSingleLine(true);addProgress.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);addProgress.setVisibility(View.GONE);ProgressBar addProgressBar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);addProgressBar.setMax(100);addProgressBar.setVisibility(View.GONE);officialPanel.addView(addProgress,new LinearLayout.LayoutParams(-1,dp(30)));officialPanel.addView(addProgressBar,new LinearLayout.LayoutParams(-1,dp(4)));pasteSources.setOnClickListener(v->{ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);ClipData data=clipboard==null?null:clipboard.getPrimaryClip();if(data==null||data.getItemCount()==0){link.setError("剪贴板没有文本");return;}CharSequence value=data.getItemAt(0).coerceToText(this);if(value==null||value.toString().trim().isEmpty()){link.setError("剪贴板没有文本");return;}link.setText(value.toString().trim());link.setSelection(link.length());});panel.addView(officialPanel,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout compositePanel=new LinearLayout(this);compositePanel.setOrientation(LinearLayout.VERTICAL);compositePanel.setVisibility(View.GONE);EditText title=sourceInput("合集标题",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES),publisher=sourceInput("发布者（可不填）",android.text.InputType.TYPE_CLASS_TEXT),avatar=sourceInput("头像或图标 URL（可不填）",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI),description=sourceInput("简介（可不填）",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);description.setSingleLine(false);description.setMinLines(2);description.setGravity(Gravity.TOP|Gravity.START);compositePanel.addView(title,new LinearLayout.LayoutParams(-1,dp(54)));compositePanel.addView(publisher,new LinearLayout.LayoutParams(-1,dp(54)));compositePanel.addView(avatar,new LinearLayout.LayoutParams(-1,dp(54)));compositePanel.addView(description,new LinearLayout.LayoutParams(-1,dp(76)));TextView memberLabel=text("先创建空目录；进入后可新建文件夹并添加单软件成员或合集源。",12,MUTED);memberLabel.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);compositePanel.addView(memberLabel,new LinearLayout.LayoutParams(-1,dp(48)));panel.addView(compositePanel,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout categoryPanel=new LinearLayout(this);categoryPanel.setOrientation(LinearLayout.VERTICAL);categoryPanel.setVisibility(View.GONE);EditText categoryName=sourceInput("分类名称",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);categoryName.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);categoryPanel.addView(categoryName,new LinearLayout.LayoutParams(-1,dp(54)));panel.addView(categoryPanel,new LinearLayout.LayoutParams(-1,-2));TextView errorText=text("",12,ERROR_TOKEN);errorText.setVisibility(View.GONE);errorText.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);errorText.setContentDescription("添加错误");panel.addView(errorText,new LinearLayout.LayoutParams(-1,dp(34)));ScrollView scroll=new ScrollView(this);scroll.addView(panel);AlertDialog prompt=new AlertDialog.Builder(this).setTitle("添加").setView(scroll).setNegativeButton("取消",null).setPositiveButton("识别并添加",null).create();
    panel.setFocusableInTouchMode(true);panel.requestFocus();prompt.setOnShowListener(ignored->{Window window=prompt.getWindow();if(window!=null)window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);Button confirm=prompt.getButton(AlertDialog.BUTTON_POSITIVE);confirm.requestFocus();modes.setOnCheckedChangeListener((group,checked)->{int mode=checked==compositeMode.getId()?1:checked==categoryMode.getId()?2:0;setAddSourceMode(mode,officialPanel,compositePanel,categoryPanel,confirm);errorText.setVisibility(View.GONE);});confirm.setOnClickListener(v->{int mode=categoryMode.isChecked()?2:compositeMode.isChecked()?1:0;if(mode==2){String name=categoryName.getText().toString().trim();if(name.isEmpty()){categoryName.setError("请输入分类名称");return;}if(name.equals("全部")||sourceCategories.containsKey(name)){categoryName.setError("该分类已存在");return;}if(name.codePointCount(0,name.length())>20){categoryName.setError("分类名称不能超过 20 个字");return;}sourceCategories.put(name,new LinkedHashSet<>());persistSourceCategories();activeSourceCategory=name;prompt.dismiss();renderSourceCategories(all);renderSources(grid,all,filter.getText().toString().trim(),heading);showNotice("已创建分类“"+name+"”",false);return;}boolean composite=mode==1,discoverChildren=!composite&&recursiveChildren.isChecked();String raw=link.getText().toString().trim();if(!composite&&raw.isEmpty()){link.setError("请输入包含蓝奏链接的文本");return;}if(composite&&title.getText().toString().trim().isEmpty()){title.setError("请输入合集标题");return;}confirm.setEnabled(false);confirm.setText(composite?"创建中…":discoverChildren?"识别并递归中…":"批量识别中…");setViewTreeEnabled(panel,false);errorText.setVisibility(View.GONE);if(!composite){addProgress.setText("正在启动并发识别…");addProgress.setVisibility(View.VISIBLE);addProgressBar.setIndeterminate(true);addProgressBar.setVisibility(View.VISIBLE);}io.execute(()->{try{Models.Source added=null;LanzouCore.AddBatchResult batch=null;if(composite)added=core.addCompositeSource(title.getText().toString().trim(),publisher.getText().toString().trim(),avatar.getText().toString().trim(),description.getText().toString().trim(),Collections.emptyList());else batch=core.addUserSourcesBatch(raw,sourceProbeParallelism(),discoverChildren,(done,total,url,ok)->runOnUiThread(()->{if(prompt.isShowing()){addProgressBar.setIndeterminate(false);addProgressBar.setProgress(done*100/Math.max(1,total));addProgress.setText((ok?"已识别 ":"识别失败 ")+done+" / "+total+" · "+url);}}));Models.Source created=added;LanzouCore.AddBatchResult result=batch;List<Models.Source> updated=core.sources();runOnUiThread(()->{++sourceDataRevision;prompt.dismiss();all.clear();all.addAll(updated);if(!targetCategory.equals("全部")&&sourceCategories.containsKey(targetCategory)){LinkedHashSet<String> category=sourceCategories.get(targetCategory);if(created!=null)category.add(sourceKey(created));if(result!=null)for(LanzouCore.AddResult line:result.lines)if(line.source!=null)category.add(sourceKey(line.source));persistSourceCategories();}renderSourceCategories(all);if(grid.getParent()!=null)renderSources(grid,all,filter.getText().toString().trim(),heading);if(created!=null)showNotice("已创建目录 "+created.title,false);else{String summary=addBatchSummary(result,discoverChildren);if(result.failed>0||result.childFailed>0)showSourceAddFailures(result,summary);else showNotice(summary,false);}});}catch(Exception error){runOnUiThread(()->{setViewTreeEnabled(panel,true);confirm.setEnabled(true);setAddSourceMode(composite?1:0,officialPanel,compositePanel,categoryPanel,confirm);addProgress.setVisibility(View.GONE);addProgressBar.setVisibility(View.GONE);errorText.setText(friendlyError(error));errorText.setVisibility(View.VISIBLE);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));});}});});});showRounded(prompt);
  }
  void showImportRulesMenu(){String[] actions={"从文件导入","从 HTTPS 链接获取"};AlertDialog menu=new AlertDialog.Builder(this).setTitle("导入源规则").setItems(actions,(d,index)->{if(index==0)pickSourceRulesFile();else showImportRulesLinkDialog();}).create();showRounded(menu);}
  void pickSourceRulesFile(){try{Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("text/*");startActivityForResult(intent,IMPORT_RULES);}catch(Exception error){showNotice("系统没有可用的文件选择器",true);}}
  void exportSourceRules(){try{Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("text/plain");intent.putExtra(Intent.EXTRA_TITLE,PRODUCT_NAME+"-sources.rules");startActivityForResult(intent,EXPORT_RULES);}catch(Exception error){showNotice("系统没有可用的文件保存器",true);}}
  void showImportRulesLinkDialog(){
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(8),dp(22),0);
    EditText link=new EditText(this);link.setSingleLine(true);link.setMaxLines(1);link.setHint("https://…");link.setTextColor(TEXT);link.setHintTextColor(MUTED);link.setTextSize(15);link.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);link.setImeOptions(EditorInfo.IME_ACTION_DONE|EditorInfo.IME_FLAG_NO_EXTRACT_UI);link.setPadding(dp(16),0,dp(16),0);link.setBackground(shape(BG,16));link.setContentDescription("HTTPS 源规则链接");panel.addView(link,new LinearLayout.LayoutParams(-1,dp(56)));
    int errorColor=ERROR_TOKEN;TextView errorText=text("",12,errorColor);errorText.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);errorText.setVisibility(View.INVISIBLE);errorText.setMaxLines(1);errorText.setEllipsize(android.text.TextUtils.TruncateAt.END);panel.addView(errorText,new LinearLayout.LayoutParams(-1,dp(26)));
    java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();Future<?>[] request={null};AlertDialog prompt=new AlertDialog.Builder(this).setTitle("从链接获取规则").setView(panel).setNegativeButton("取消",null).setPositiveButton("获取并合并",null).create();prompt.setCanceledOnTouchOutside(false);prompt.setOnDismissListener(ignored->{cancelled.set(true);if(request[0]!=null&&!request[0].isDone())request[0].cancel(true);});
    prompt.setOnShowListener(ignored->{Window window=prompt.getWindow();if(window!=null)window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);Button confirm=prompt.getButton(AlertDialog.BUTTON_POSITIVE),cancel=prompt.getButton(AlertDialog.BUTTON_NEGATIVE);Runnable submit=()->{String url=link.getText().toString().trim();Uri parsed=Uri.parse(url);if(url.isEmpty()||!"https".equalsIgnoreCase(parsed.getScheme())||parsed.getHost()==null){errorText.setText("请输入有效的 HTTPS 规则链接");errorText.setVisibility(View.VISIBLE);link.requestFocus();return;}errorText.setVisibility(View.INVISIBLE);confirm.setEnabled(false);confirm.setText("获取中…");link.setEnabled(false);cancelled.set(false);request[0]=io.submit(()->{try{String rules=core.fetchSourceRules(url);if(cancelled.get())return;LanzouCore.ImportResult result=core.importSourceRules(rules);if(cancelled.get())return;List<Models.Source> updated=core.sources();runOnUiThread(()->{if(cancelled.get()||!prompt.isShowing())return;prompt.dismiss();finishRuleImport(result,updated);});}catch(Exception failure){runOnUiThread(()->{if(cancelled.get()||!prompt.isShowing())return;confirm.setEnabled(true);confirm.setText("获取并合并");link.setEnabled(true);errorText.setText(friendlyError(failure));errorText.setVisibility(View.VISIBLE);link.requestFocus();});}});};confirm.setOnClickListener(v->submit.run());cancel.setOnClickListener(v->{cancelled.set(true);if(request[0]!=null)request[0].cancel(true);prompt.dismiss();});link.setOnEditorActionListener((v,action,event)->{if(action!=EditorInfo.IME_ACTION_DONE)return false;submit.run();return true;});link.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence value,int start,int count,int after){}public void onTextChanged(CharSequence value,int start,int before,int count){errorText.setVisibility(View.INVISIBLE);}public void afterTextChanged(Editable value){}});link.requestFocus();link.post(()->{android.view.inputmethod.InputMethodManager keyboard=(android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);if(keyboard!=null)keyboard.showSoftInput(link,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);});});showRounded(prompt);
  }
  void importSourceRules(String rules){showNotice("正在合并源规则",false);io.execute(()->{try{LanzouCore.ImportResult result=core.importSourceRules(rules);List<Models.Source> updated=core.sources();runOnUiThread(()->finishRuleImport(result,updated));}catch(Exception error){showNotice("导入失败："+friendlyError(error),true);}});}
  void finishRuleImport(LanzouCore.ImportResult result,List<Models.Source> updated){++sourceDataRevision;if(pageKind==1&&sourceGrid!=null&&sourcePageSources!=null){sourcePageSources.clear();sourcePageSources.addAll(updated);renderSources(sourceGrid,sourcePageSources,sourceFilter==null?"":sourceFilter.getText().toString().trim(),sourceHeading);}showNotice("已新增 "+result.added+"，跳过重复 "+result.duplicates+(result.invalid>0?"，无效 "+result.invalid:""),result.invalid>0);}
  String readTextUri(Uri uri)throws IOException{try(InputStream input=getContentResolver().openInputStream(uri);ByteArrayOutputStream output=new ByteArrayOutputStream()){if(input==null)throw new IOException("无法读取规则文件");byte[] buffer=new byte[4096];int total=0;for(int count;(count=input.read(buffer))>0;){total+=count;if(total>512*1024)throw new IOException("规则文件过大");output.write(buffer,0,count);}return output.toString("UTF-8");}}
  void writeTextUri(Uri uri,String text)throws IOException{try(OutputStream output=getContentResolver().openOutputStream(uri,"wt")){if(output==null)throw new IOException("无法写入规则文件");output.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
  /** 2026-09-24 性能审计#2：下载列表分帧渲染——先同步收集可见行规格(纯数据,便宜)，再按目录页
      appendFolderRowsFrame 同款模式逐帧建行；generation/session 双守卫丢弃换页后的残帧。 */
  /**
   * 下载页空态：图标 + 标题 + 下一步提示。
   *
   * 为什么值得单独一个方法：改前空态是 `text("暂无下载记录",14,MUTED)` 一行灰字居中，
   * 没有图标、没有引导。空态在"还没用起来"时占满整屏，是「质感」最容易被感知的地方。
   *
   * 高度全部走 dpText（DFW-13 适老化，规矩见 :728-737）：这是纯文字容器，字体放大时必须跟着长。
   */
  View downloadEmptyState(int icon,String title,String hint){
    LinearLayout box=new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setGravity(Gravity.CENTER);
    ImageView art=new ImageView(this);
    art.setImageResource(icon);
    art.setColorFilter(MUTED);
    /* 半透明：图标是"视觉锚点"不是"内容"，压暗一档让它退到标题后面去。 */
    art.setAlpha(.5f);
    art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    LinearLayout.LayoutParams artLp=new LinearLayout.LayoutParams(dp(56),dp(56));
    artLp.bottomMargin=dp(16);
    box.addView(art,artLp);
    TextView headline=text(title,15,TEXT,600);
    headline.setGravity(Gravity.CENTER);
    box.addView(headline,new LinearLayout.LayoutParams(-2,dpText(24)));
    TextView sub=text(hint,12,MUTED);
    sub.setGravity(Gravity.CENTER);
    LinearLayout.LayoutParams subLp=new LinearLayout.LayoutParams(-2,dpText(20));
    subLp.topMargin=dp(8);
    box.addView(sub,subLp);
    box.setContentDescription(title+"。"+hint);
    return box;
  }
  Runnable searchDebounceRunnable;int downloadRenderGeneration;
  void renderDownloads(String query){
    if(downloadList==null)return;downloadQuery=query;refreshDownloadHeaderMenu();downloadList.removeAllViews();downloadLabels.clear();downloadBars.clear();downloadActions.clear();selectionHiddenViews.clear();downloadRows.clear();downloadChecks.clear();batchDownloadLabels.clear();batchDownloadBars.clear();batchDownloadRows.clear();batchDownloadChecks.clear();downloadRenderGeneration++;
    java.util.List<Object> specs=new ArrayList<>();Set<String> renderedBatches=new HashSet<>();
    for(DownloadEntry entry:downloadEntries)if(downloadMatchesCurrentView(entry)){if(sessionBatchDownloadSingleItem&&!entry.batchId.isEmpty()){if(renderedBatches.add(entry.batchId)){List<DownloadEntry> batch=downloadBatchEntries(entry.batchId);boolean any=false;for(DownloadEntry child:batch)if(downloadMatchesCurrentView(child)){any=true;break;}if(any)specs.add(new DownloadRowSpec(entry.batchId,batch));}}else specs.add(entry);}
    if(specs.isEmpty()){
      /* 空态（2026-10-03 重做）：改前是一行 14sp 灰字居中 —— 一整屏纯黑中央一行灰字，
         没有图标、没有下一步引导，就是块"白板"。空态恰恰是"还没用起来"时占满整屏的那一屏，
         用户对界面「质感」的抱怨最容易落在这里。现在三件：图标 + 标题 + 下一步该做什么。
         两种文案**刻意分开**（"没有记录"和"被筛掉了"是两件事，下一步动作也不同）——
         改前这点做对了，保留。 */
      boolean noEntries=downloadEntries.isEmpty();
      View empty=downloadEmptyState(noEntries?R.drawable.ic_download:R.drawable.ic_search,
          noEntries?"暂无下载记录":"没有匹配的下载记录",
          noEntries?"在资源页选一个文件就能开始下载，长按可以批量选":"换个关键词，或把上面的筛选切回「全部」");
      /* 空态文案垂直居中：优先用列表区实测高度，量不到才退回估算。
         旧写法把 chrome 写死 286dp，而实测 chrome 只有 225dp（含操作行），文案因此偏上约 30dp。
         [2026-10-03 重排] 顶部是 4 条横杠 + 每段 8dp 间距 + 列表前 10dp：
           页头 56 + 8 + 搜索 48 + 8 + 状态条 44 + 8 + 扩展名条 44 + 10 = **226dp**。
         注意它现在**不再随"有没有在跑的任务"变化** —— 旧的操作行会上下跳 44dp，
         这正是本轮把它整行搬进 ⋯ 菜单的原因之一。正常路径走 pageScroll 实测高度，这个值只是首帧兜底。 */
      int chrome=dp(226);
      int measured=pageScroll==null?0:pageScroll.getHeight();
      int viewport=measured>dp(240)?measured:Math.max(dp(240),getResources().getDisplayMetrics().heightPixels-chrome);
      downloadList.addView(empty,new LinearLayout.LayoutParams(-1,viewport));
      /* 首帧建树时 pageScroll 还没量过高度（getHeight()=0），只能先按估算摆；量好之后按真实可用高补齐，
         换设备/换字体才都能真居中。generation 一变（重新渲染过）这条就作废。 */
      final int token=downloadRenderGeneration;
      downloadList.post(()->{
        if(token!=downloadRenderGeneration||empty.getParent()==null)return;
        int height=pageScroll==null?0:pageScroll.getHeight();
        if(height<=0||empty.getHeight()==height)return;
        ViewGroup.LayoutParams params=empty.getLayoutParams();
        params.height=height;
        empty.setLayoutParams(params);
      });
      if(downloadSelectionMode){selectedDownloads.retainAll(downloadChecks.keySet());if(selectedDownloads.isEmpty())exitDownloadSelection();else updateDownloadSelectionSummary();}
      return;
    }
    renderDownloadsFrame(downloadRenderGeneration,navigationSession,specs,0);
  }
  void renderDownloadsFrame(int token,int session,java.util.List<Object> specs,int cursor){
    if(token!=downloadRenderGeneration||session!=navigationSession||downloadList==null||downloadList.getParent()==null)return;
    int end=Math.min(specs.size(),cursor+adaptiveFolderUiChunk());
    for(int i=cursor;i<end;i++){Object spec=specs.get(i);if(spec instanceof DownloadEntry)addDownloadRow((DownloadEntry)spec);else{DownloadRowSpec batch=(DownloadRowSpec)spec;addBatchDownloadRow(batch.batchId,batch.entries);}}
    if(end<specs.size()){downloadList.postOnAnimation(()->renderDownloadsFrame(token,session,specs,end));return;}
    if(downloadSelectionMode){selectedDownloads.retainAll(downloadChecks.keySet());if(selectedDownloads.isEmpty())exitDownloadSelection();else updateDownloadSelectionSummary();}
  }
  /** 一行下载列表的渲染规格：单条=DownloadEntry，批量=batchId+子项列表 */
  static final class DownloadRowSpec{final String batchId;final List<DownloadEntry> entries;DownloadRowSpec(String batchId,List<DownloadEntry> entries){this.batchId=batchId;this.entries=entries;}}
  List<DownloadEntry> downloadBatchEntries(String batchId){List<DownloadEntry> out=new ArrayList<>();if(batchId==null||batchId.isEmpty())return out;for(DownloadEntry entry:downloadEntries)if(batchId.equals(entry.batchId))out.add(entry);return out;}
  String batchDownloadTitle(List<DownloadEntry> entries){for(DownloadEntry entry:entries)if(!entry.batchTitle.isEmpty())return entry.batchTitle;return "批量下载 · "+entries.size()+" 项";}
  int batchDownloadPercent(List<DownloadEntry> entries){if(entries.isEmpty())return 0;long total=0;for(DownloadEntry entry:entries)total+=Math.max(0,Math.min(100,entry.percent));return(int)(total/entries.size());}
  String batchDownloadMetrics(List<DownloadEntry> entries){int done=0,active=0,failed=0,paused=0;long speed=0,remaining=0;for(DownloadEntry entry:entries){if(entry.state.equals(DOWNLOAD_COMPLETED))done++;else if(entry.state.equals(DOWNLOAD_FAILED)||entry.state.equals(DOWNLOAD_CANCELLED))failed++;else if(entry.state.equals(DOWNLOAD_PAUSED))paused++;else active++;if(entry.state.equals(DOWNLOAD_RUNNING)){speed+=Math.max(0,entry.speedBps);if(entry.totalBytes>0)remaining+=Math.max(0,entry.totalBytes-entry.downloadedBytes);}}String summary="共 "+entries.size()+" 项 · 完成 "+done+" · 进行中 "+active+(paused>0?" · 暂停 "+paused:"")+(failed>0?" · 异常 "+failed:"");if(active>0)return summary+"\n"+formatSpeed(speed)+" · "+formatEta(speed>0&&remaining>0?Math.max(0,remaining/Math.max(1,speed)):-1);return summary+"\n点击查看内部下载项目";}
  boolean batchDownloadFullySelected(List<DownloadEntry> entries){return !entries.isEmpty()&&selectedDownloads.containsAll(entries);}
  void syncBatchDownloadChecks(){for(Map.Entry<String,CheckBox> value:batchDownloadChecks.entrySet()){List<DownloadEntry> entries=downloadBatchEntries(value.getKey());CheckBox check=value.getValue();check.setVisibility(downloadSelectionMode?View.VISIBLE:View.GONE);check.setChecked(batchDownloadFullySelected(entries));}}
  void toggleBatchDownloadSelection(String batchId){List<DownloadEntry> entries=downloadBatchEntries(batchId);if(entries.isEmpty())return;if(batchDownloadFullySelected(entries))selectedDownloads.removeAll(entries);else selectedDownloads.addAll(entries);syncChecks(selectedDownloads,downloadChecks);syncBatchDownloadChecks();if(selectedDownloads.isEmpty())exitDownloadSelection();else updateDownloadSelectionSummary();}
  /** [DFW-113 2026-10-03] 批量卡片：与单条卡片用**同一套卡片语言**（面/描边/圆角/间距/高度）。
      改前它也是裸行，且里面同样是 24+44+5=73dp 塞进 70dp 容器（进度条被裁）。 */
  void addBatchDownloadRow(String batchId,List<DownloadEntry> entries){
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(6),dp(4),dp(6));row.setClickable(true);row.setFocusable(true);
    GradientDrawable cardBg=solidShape(SURFACE,16);
    cardBg.setStroke(dp(1),BORDER);
    row.setBackground(filterRipple(cardBg));
    row.addView(batchDownloadPreview(entries,20),new LinearLayout.LayoutParams(dp(40),dp(40)));
    LinearLayout middle=new LinearLayout(this);middle.setOrientation(LinearLayout.VERTICAL);middle.setPadding(dp(11),0,dp(8),0);
    TextView name=text(batchDownloadTitle(entries),14,TEXT,560);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    TextView label=text(batchDownloadMetrics(entries),11,MUTED);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    ProgressBar bar=downloadProgressBar();bar.setProgress(batchDownloadPercent(entries));
    middle.addView(name,new LinearLayout.LayoutParams(-1,dpText(22)));
    middle.addView(label,new LinearLayout.LayoutParams(-1,dpText(32)));
    middle.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));
    row.addView(middle,new LinearLayout.LayoutParams(0,dpText(60),1));
    ImageButton actions=rowIconButton(R.drawable.ic_pause,"控制批量下载任务");actions.setOnClickListener(v->showBatchDownloadActions(batchId));
    row.addView(actions,rowIconButtonLp());
    ImageButton details=rowIconButton(R.drawable.ic_expand,"查看批量下载详情");details.setRotation(-90f);details.setOnClickListener(v->showBatchDownloadDetails(batchId));
    row.addView(details,rowIconButtonLp());
    actions.setVisibility(downloadSelectionMode?View.GONE:View.VISIBLE);
    details.setVisibility(downloadSelectionMode?View.GONE:View.VISIBLE);
    selectionHiddenViews.add(actions);
    selectionHiddenViews.add(details);
    CheckBox check=new CheckBox(this);check.setClickable(false);check.setChecked(batchDownloadFullySelected(entries));
    check.setVisibility(downloadSelectionMode?View.VISIBLE:View.GONE);
    row.addView(check,new LinearLayout.LayoutParams(dp(32),dpText(44)));
    row.setContentDescription(batchDownloadTitle(entries)+"，"+batchDownloadMetrics(entries).replace('\n','，'));
    row.setOnClickListener(v->{if(downloadSelectionMode)toggleBatchDownloadSelection(batchId);else showBatchDownloadDetails(batchId);});
    row.setOnLongClickListener(v->{enterDownloadSelection();toggleBatchDownloadSelection(batchId);return true;});
    for(DownloadEntry entry:entries){downloadRows.put(entry,row);CheckBox hidden=new CheckBox(this);hidden.setVisibility(View.GONE);downloadChecks.put(entry,hidden);}
    batchDownloadLabels.put(batchId,label);batchDownloadBars.put(batchId,bar);batchDownloadRows.put(batchId,row);batchDownloadChecks.put(batchId,check);
    LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,dpText(76));
    cardLp.setMargins(dp(4),0,dp(4),dp(6));
    downloadList.addView(row,cardLp);
  }
  void updateBatchDownloadRow(String batchId){List<DownloadEntry> entries=downloadBatchEntries(batchId);TextView label=batchDownloadLabels.get(batchId);ProgressBar bar=batchDownloadBars.get(batchId);View row=batchDownloadRows.get(batchId);if(label!=null&&label.getParent()!=null)label.setText(batchDownloadMetrics(entries));if(bar!=null&&bar.getParent()!=null)bar.setProgress(batchDownloadPercent(entries));if(row!=null&&row.getParent()!=null)row.setContentDescription(batchDownloadTitle(entries)+"，"+batchDownloadMetrics(entries).replace('\n','，'));}
    void showBatchDownloadActions(String batchId){List<DownloadEntry> entries=downloadBatchEntries(batchId);if(entries.isEmpty())return;AlertDialog menu=new AlertDialog.Builder(this).setTitle(batchDownloadTitle(entries)).setItems(new String[]{"暂停/继续未完成项","取消未完成项","删除任务记录","删除本地文件与记录"},(dialog,which)->confirmDownloadEntriesAction(new ArrayList<>(downloadBatchEntries(batchId)),which)).setNegativeButton("关闭",null).create();showRounded(menu);}
    void confirmDownloadEntriesAction(List<DownloadEntry> entries,int action){if(entries==null||entries.isEmpty())return;if(action==0){applyDownloadEntriesAction(entries,action);return;}String title=action==1?"取消下载任务？":action==2?"删除下载记录？":"删除本地文件？";String message=action==1?"将取消 "+entries.size()+" 个未完成下载。":action==2?"将删除 "+entries.size()+" 条下载记录，本地文件会保留。":"删除成功或文件已不存在时会同步清理对应下载记录；删除失败的记录会保留。";AlertDialog prompt=new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消",null).setPositiveButton(action==1?"取消下载":action==2?"删除记录":"删除文件",(dialog,which)->applyDownloadEntriesAction(entries,action)).create();showRounded(prompt);}
  void applyDownloadEntriesAction(List<DownloadEntry> entries,int action){List<DownloadEntry> chosen=new ArrayList<>(entries);if(action==0){boolean pause=false;for(DownloadEntry entry:chosen)if(isDownloadActive(entry)){pause=true;break;}for(DownloadEntry entry:chosen)if(pause){if(isDownloadActive(entry))pauseDownload(entry);}else if(entry.state.equals(DOWNLOAD_PAUSED))resumeDownload(entry);showNotice(pause?"已暂停批量下载":"已继续批量下载",false);return;}if(action==1){for(DownloadEntry entry:chosen)if(isDownloadActive(entry)||entry.state.equals(DOWNLOAD_PAUSED))cancelDownload(entry);showNotice("已取消批量下载",false);return;}if(action==2){downloadEntries.removeAll(chosen);persistDownloadHistory();if(downloadsPage){renderDownloadFilters();renderDownloads(downloadQuery);}showNotice("已删除 "+chosen.size()+" 条下载记录",false);return;}List<DownloadEntry> removed=new ArrayList<>();int deleted=0,missing=0,failed=0;for(DownloadEntry entry:chosen){if(isDownloadActive(entry)||entry.state.equals(DOWNLOAD_PAUSED))cancelDownload(entry);int result=deleteDownloadedFileNow(entry);if(result<0)failed++;else{removed.add(entry);if(result==DELETE_OK)deleted++;else missing++;}}downloadEntries.removeAll(removed);persistDownloadHistory();if(downloadsPage){renderDownloadFilters();renderDownloads(downloadQuery);}showNotice("已删文件 "+deleted+" · 清理缺失 "+missing+(failed>0?" · 失败保留 "+failed:""),failed>0);}
  void withBatchDownloadSelection(Collection<DownloadEntry> entries,Runnable action){selectedDownloads.clear();selectedDownloads.addAll(entries);try{action.run();}finally{selectedDownloads.clear();}}
  ImageButton batchDetailAction(int icon,String description,Runnable action){ImageButton button=iconButton(icon,description);button.setOnClickListener(v->action.run());return button;}
  void showBatchDownloadDetails(String batchId){List<DownloadEntry> entries=downloadBatchEntries(batchId);if(entries.isEmpty())return;LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(14),dp(6),dp(14),dp(8));final String[] state={"全部"},extension={"全部"};final LinkedHashSet<DownloadEntry> chosen=new LinkedHashSet<>();final boolean[] selecting={false};LinearLayout stateStrip=new LinearLayout(this),extensionStrip=new LinearLayout(this),itemList=new LinearLayout(this);itemList.setOrientation(LinearLayout.VERTICAL);HorizontalScrollView stateScroll=new HorizontalScrollView(this);stateScroll.setHorizontalScrollBarEnabled(false);stateScroll.addView(stateStrip,new HorizontalScrollView.LayoutParams(-2,dp(38)));panel.addView(stateScroll,new LinearLayout.LayoutParams(-1,dp(38)));LinearLayout extensionRow=new LinearLayout(this);extensionRow.setGravity(Gravity.CENTER_VERTICAL);HorizontalScrollView extensionScroll=new HorizontalScrollView(this);extensionScroll.setHorizontalScrollBarEnabled(false);extensionScroll.addView(extensionStrip,new HorizontalScrollView.LayoutParams(-2,dp(38)));extensionRow.addView(extensionScroll,new LinearLayout.LayoutParams(0,dp(38),1));ImageButton pauseAll=iconButton(R.drawable.ic_pause,"暂停批量下载全部未完成项目"),cancelAll=iconButton(R.drawable.ic_close,"取消批量下载全部未完成项目"),deleteRecords=iconButton(R.drawable.ic_delete_record,"删除批量下载全部记录"),deleteFiles=iconButton(R.drawable.ic_delete_file,"删除批量下载全部文件");pauseAll.setOnClickListener(v->confirmDownloadEntriesAction(new ArrayList<>(entries),0));cancelAll.setOnClickListener(v->confirmDownloadEntriesAction(new ArrayList<>(entries),1));deleteRecords.setOnClickListener(v->confirmDownloadEntriesAction(new ArrayList<>(entries),2));deleteFiles.setOnClickListener(v->confirmDownloadEntriesAction(new ArrayList<>(entries),3));for(ImageButton button:new ImageButton[]{pauseAll,cancelAll,deleteRecords,deleteFiles})extensionRow.addView(button,new LinearLayout.LayoutParams(dp(38),dp(38)));panel.addView(extensionRow,new LinearLayout.LayoutParams(-1,dp(38)));LinearLayout selectionActions=new LinearLayout(this);selectionActions.setOrientation(LinearLayout.VERTICAL);selectionActions.setVisibility(View.GONE);LinearLayout actionTop=new LinearLayout(this),actionBottom=new LinearLayout(this);actionTop.setGravity(Gravity.CENTER_VERTICAL);actionBottom.setGravity(Gravity.CENTER_VERTICAL);ImageButton directory=batchDetailAction(R.drawable.ic_folder,"打开保存路径",()->openDownloadDirectory()),more=batchDetailAction(R.drawable.ic_open_with,"更多方式打开所选文件",()->withBatchDownloadSelection(chosen,this::openSelectedWithMore)),copy=batchDetailAction(R.drawable.ic_copy,"复制所选原链接",()->withBatchDownloadSelection(chosen,this::copySelectedDownloadLinks)),shareFiles=batchDetailAction(R.drawable.ic_share_file,"分享所选本地文件",()->withBatchDownloadSelection(chosen,this::shareSelectedDownloadedFiles)),location=batchDetailAction(R.drawable.ic_folder_open,"打开所选文件所在路径",()->withBatchDownloadSelection(chosen,this::openSelectedDownloadLocations)),install=batchDetailAction(R.drawable.ic_install,"安装所选 APK",()->withBatchDownloadSelection(chosen,this::installSelectedEntries)),pause=batchDetailAction(R.drawable.ic_pause,"暂停或继续所选未完成下载任务",()->confirmDownloadEntriesAction(new ArrayList<>(chosen),0)),cancel=batchDetailAction(R.drawable.ic_close,"取消所选未完成下载任务",()->confirmDownloadEntriesAction(new ArrayList<>(chosen),1)),records=batchDetailAction(R.drawable.ic_delete_record,"删除所选记录",()->confirmDownloadEntriesAction(new ArrayList<>(chosen),2)),files=batchDetailAction(R.drawable.ic_delete_file,"删除所选本地文件",()->confirmDownloadEntriesAction(new ArrayList<>(chosen),3)),shareLinks=batchDetailAction(R.drawable.ic_share,"分享所选原链接",()->withBatchDownloadSelection(chosen,this::shareSelectedDownloadLinks)),close=batchDetailAction(R.drawable.ic_close,"退出批量下载内部多选",()->{selecting[0]=false;chosen.clear();});ImageButton[] top={directory,more,copy,shareFiles,location,install},bottom={pause,cancel,records,files,shareLinks,close};for(ImageButton action:top)actionTop.addView(action,new LinearLayout.LayoutParams(0,dp(44),1));for(ImageButton action:bottom)actionBottom.addView(action,new LinearLayout.LayoutParams(0,dp(44),1));selectionActions.addView(actionTop,new LinearLayout.LayoutParams(-1,dp(44)));selectionActions.addView(actionBottom,new LinearLayout.LayoutParams(-1,dp(44)));selectionActions.setContentDescription("批量下载内部多选操作，随列表滚动");panel.addView(itemList,new LinearLayout.LayoutParams(-1,-2));panel.addView(selectionActions,new LinearLayout.LayoutParams(-1,dp(88)));final Runnable[] refresh={null};refresh[0]=()->{stateStrip.removeAllViews();extensionStrip.removeAllViews();for(String label:new String[]{"全部","保存中","解析中","下载中","下载完成","下载失败"}){View tab=underlineFilter(label,label.equals(state[0]),()->{state[0]=label;refresh[0].run();});stateStrip.addView(tab,new LinearLayout.LayoutParams(-2,dp(38)));}for(String label:new String[]{"全部","安装包","应用程序","压缩包","文本","视频","音乐","其他"}){TextView tab=roundedDownloadFilter(label,label.equals(extension[0]),()->{extension[0]=label;refresh[0].run();});extensionStrip.addView(tab,new LinearLayout.LayoutParams(-2,dp(34)));}selectionActions.setVisibility(selecting[0]?View.VISIBLE:View.GONE);itemList.removeAllViews();for(DownloadEntry entry:entries){if(!matchesDownloadStateValue(entry,state[0])||!matchesDownloadExtensionValue(entry,extension[0]))continue;LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);CheckBox check=new CheckBox(this);check.setClickable(false);check.setVisibility(selecting[0]?View.VISIBLE:View.GONE);check.setChecked(chosen.contains(entry));row.addView(check,new LinearLayout.LayoutParams(dp(44),dp(44)));ImageView icon=new ImageView(this);icon.setImageResource(R.drawable.ic_file);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);loadImage(entry.iconUrl,icon);row.addView(icon,new LinearLayout.LayoutParams(dp(36),dp(36)));LinearLayout copyView=new LinearLayout(this);copyView.setOrientation(LinearLayout.VERTICAL);copyView.setPadding(dp(10),0,0,0);TextView title=text(entry.name,13,TEXT);title.setSingleLine(true);title.setEllipsize(android.text.TextUtils.TruncateAt.END);TextView status=text(downloadMetrics(entry).replace("\n"," · "),10,MUTED);status.setMaxLines(2);copyView.addView(title,new LinearLayout.LayoutParams(-1,dp(24)));copyView.addView(status,new LinearLayout.LayoutParams(-1,dp(34)));row.addView(copyView,new LinearLayout.LayoutParams(0,dp(62),1));row.setOnClickListener(v->{if(selecting[0]){if(chosen.contains(entry))chosen.remove(entry);else chosen.add(entry);refresh[0].run();}else if(entry.state.equals(DOWNLOAD_FAILED))requestRetryDownload(entry);else if(entry.state.equals(DOWNLOAD_PAUSED))resumeDownload(entry);else if(entry.state.equals(DOWNLOAD_COMPLETED))installEntry(entry);});row.setOnLongClickListener(v->{selecting[0]=true;chosen.add(entry);refresh[0].run();return true;});itemList.addView(row,new LinearLayout.LayoutParams(-1,dp(66)));}if(itemList.getChildCount()==0){TextView empty=text("没有匹配的批量下载项目",12,MUTED);empty.setGravity(Gravity.CENTER);itemList.addView(empty,new LinearLayout.LayoutParams(-1,dp(72)));}};close.setOnClickListener(v->{selecting[0]=false;chosen.clear();refresh[0].run();});AlertDialog prompt=new AlertDialog.Builder(this).setTitle(batchDownloadTitle(entries)).setView(limitedDialogScroll(panel,480)).setNegativeButton("关闭",null).create();refresh[0].run();showRounded(prompt);}

  boolean downloadMatchesCurrentView(DownloadEntry entry){String key=downloadQuery.toLowerCase(Locale.ROOT);return(key.isEmpty()||(entry.name+" "+entry.state+" "+entry.error+" "+entry.shareUrl).toLowerCase(Locale.ROOT).contains(key))&&(downloadStateFilter.equals("全部")||matchesDownloadState(entry))&&(downloadExtensionFilter.equals("全部")||matchesDownloadExtension(entry));}
  boolean matchesDownloadState(DownloadEntry entry){return matchesDownloadStateValue(entry,downloadStateFilter);} boolean matchesDownloadStateValue(DownloadEntry entry,String filter){if(filter.equals("全部"))return true;if(filter.equals("解析中"))return entry.state.equals(DOWNLOAD_RESOLVING);if(filter.equals("下载中"))return entry.state.equals(DOWNLOAD_WAITING)||entry.state.equals(DOWNLOAD_RUNNING)||entry.state.equals(DOWNLOAD_PAUSED);if(filter.equals("下载完成"))return entry.state.equals(DOWNLOAD_COMPLETED);return entry.state.equals(DOWNLOAD_FAILED)||entry.state.equals(DOWNLOAD_CANCELLED);}
  String downloadExtension(DownloadEntry entry){String name=entry.name.toLowerCase(Locale.ROOT);int dot=name.lastIndexOf('.');return dot>=0&&dot<name.length()-1?name.substring(dot+1).toUpperCase(Locale.ROOT):"无后缀";}
  String downloadExtensionCategory(DownloadEntry entry){switch(downloadExtension(entry)){case"APK":case"APKS":case"APKM":case"XAPK":case"AAB":return"安装包";case"EXE":case"MSI":case"MSIX":case"APPX":case"APPXBUNDLE":case"DMG":case"PKG":case"DEB":case"RPM":case"APPIMAGE":case"JAR":return"应用程序";case"ZIP":case"RAR":case"7Z":case"TAR":case"GZ":case"BZ2":case"XZ":case"ZST":case"TGZ":case"TBZ":case"CAB":case"ISO":return"压缩包";case"TXT":case"MD":case"LOG":case"CSV":case"JSON":case"XML":case"YAML":case"YML":case"INI":case"CONF":case"RTF":case"PDF":case"DOC":case"DOCX":case"ODT":case"XLS":case"XLSX":case"PPT":case"PPTX":case"EPUB":case"MOBI":return"文本";case"MP4":case"MKV":case"AVI":case"MOV":case"WMV":case"FLV":case"WEBM":case"M4V":case"TS":case"3GP":case"MPEG":case"MPG":return"视频";case"MP3":case"FLAC":case"WAV":case"AAC":case"M4A":case"OGG":case"OPUS":case"WMA":case"APE":case"MID":case"MIDI":return"音乐";default:return"其他";}}
  boolean matchesDownloadExtension(DownloadEntry entry){return matchesDownloadExtensionValue(entry,downloadExtensionFilter);} boolean matchesDownloadExtensionValue(DownloadEntry entry,String filter){return filter.equals("全部")||downloadExtensionCategory(entry).equals(filter);}
  void renderDownloadFilters(){
    if(downloadFilterStrip==null||downloadExtensionStrip==null)return;downloadFilterStrip.removeAllViews();downloadExtensionStrip.removeAllViews();for(String label:new String[]{"全部","解析中","下载中","下载完成","下载失败"}){View filter=underlineFilter(label,label.equals(downloadStateFilter),()->{downloadStateFilter=label;transitionDownloadFilter(()->renderDownloads(downloadQuery));},5);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dpText(44));params.setMargins(0,0,dp(4),0);downloadFilterStrip.addView(filter,params);}for(String label:new String[]{"全部","安装包","应用程序","压缩包","文本","视频","音乐","其他"}){TextView filter=roundedDownloadFilter(label,label.equals(downloadExtensionFilter),()->{downloadExtensionFilter=label;transitionDownloadFilter(()->renderDownloads(downloadQuery));});LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dpText(44));params.setMargins(0,0,dp(6),0);downloadExtensionStrip.addView(filter,params);}
    /* [2026-10-03 修正注释] 上面两行 v1.22.3 注释里的数字**三个版本互相矛盾，且全部与代码不符**：
         · 这里说「6 标签 / 14dp 时 426dp / 改 6dp ≈ 320dp」；
         · `underlineFilter` 的 Javadoc 说「6 个标签 / 下载页传 7dp / 落在 322dp」；
         · 而实际代码是 **5 个标签、传 5dp**（见上一行的 label 数组与末参数）。
       2026-10-03 按今天的代码重算（360dp、fontScale 1.0）：可用 328dp；
       5 个标签共 16 个汉字 × 12dp = 192 + 内边距 2×5×5 = 50 + 右间距 4×5 = 20 + strip 内边距 12 = **274dp，余 54dp**。
       退回 14dp 是 364dp > 328dp —— **补丁本身仍然必要**，但 426/322/320 这些数一个都算不出来。
       另外「被屏幕右缘裁切」这个失败模式**今天已不可能发生**：本行外层是 HorizontalScrollView
       （见 `downloadStateFilterRow`），且 strip 上已无任何 setMinimumWidth，超宽时是**横滚**不是裁切。
       所以这个 5dp 的真实价值是**可发现性**（5 个标签默认全部可见，不用横滑才发现有「下载失败」），
       不是可用性。将来调这个数，按"默认全可见"这个目标调，别再背 426dp。
       [2026-10-03 重做] 标签高度同时由 dp(38) 提到 dpText(44)：token §4:128 要求胶囊高 44–48dp。 */
  }
  /** v1.22.3 扩展名胶囊行：整行独占（原先与 4 个全局操作按钮挤同一行 → 360dp 窄屏上留给胶囊仅约 176dp，
   *  「应用程序」等被按钮边界截断，视觉上像"按钮被边距挡住"）。改为胶囊整行可横滚，按钮移到独立行。 */
  /**
   * 扩展名胶囊行。
   *
   * [2026-10-03] 加**右缘渐隐**。这一行 8 个胶囊在 360dp 上根本放不下（实测算下来约 470dp），
   * 必然有胶囊被屏幕边缘**切成一半** —— 静态看就是"这个胶囊坏了"，用户 2026-10-03 的
   * 「按钮都被黑色遮住了…错位了」里有一部分就是它。
   * 加一层 BG→透明的渐变之后，切边变成「右边还有，可以滑」的暗示，这是通行做法。
   *
   * 只加在这一行，不加在状态筛选行：那一行 5 个标签在 360dp 内排得下（实测 274dp ≤ 328dp），
   * 不会溢出，加渐隐反而会平白压暗一块。
   */
  LinearLayout downloadExtensionChipRow(LinearLayout strip){
    LinearLayout row=new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    HorizontalScrollView scroll=new HorizontalScrollView(this);
    scroll.setHorizontalScrollBarEnabled(false);
    scroll.setFillViewport(false);
    strip.setOrientation(LinearLayout.HORIZONTAL);
    /* [DFW-130 2026-10-03] 这一句是必须的，不是可选的美化。
       实测（Robolectric 几何 dump，360dp）：
         strip 高 154px(44dp)、上下内边距各 7px(2dp) ⇒ 内容区只有 140px
         胶囊高 154px(44dp) —— 它本来就该占满整行
       没有 CENTER_VERTICAL 时胶囊靠上对齐，落在 y=7..161，**底部 7px 被父容器裁掉**
       （用户截图原话：「按钮底部都被东西给裁剪遮住了」）。
       加上之后居中：childTop = 2 + (140-154)/2 = 0，正好 0..154 铺满，不裁也不溢出。
       状态筛选那行一直没这问题，就是因为它本来就有 CENTER_VERTICAL —— 两行现在一致了。 */
    strip.setGravity(Gravity.CENTER_VERTICAL);
    strip.setPadding(0,dp(2),dp(8),dp(2));
    scroll.addView(strip,new HorizontalScrollView.LayoutParams(-2,dpText(44)));
    FrameLayout box=new FrameLayout(this);
    box.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
    View fade=new View(this);
    fade.setBackground(new android.graphics.drawable.GradientDrawable(
        android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
        new int[]{0x00000000,BG}));
    fade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    box.addView(fade,new FrameLayout.LayoutParams(dp(32),-1,Gravity.END));
    row.addView(box,new LinearLayout.LayoutParams(-1,dpText(44)));
    return row;
  }
  /** 给刚加进容器的视图补一个上边距（用于页面纵向节奏）。非 LinearLayout 参数则不动。 */
  void applyTopGap(View v,int dpValue){
    if(v==null||!(v.getLayoutParams() instanceof LinearLayout.LayoutParams))return;
    LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)v.getLayoutParams();
    lp.topMargin=dp(dpValue);
    v.setLayoutParams(lp);
  }
  /**
   * 下载页「更多操作」菜单（页头 ⋮）。
   *
   * ## [2026-10-03 重排] 它现在同时承担原来的「全局操作行」
   * 「暂停全部 / 继续全部」「取消全部」从列表上方**独立一行**搬到这里，原因是那一行有三个毛病：
   *   ① 只在有任务时出现 → 列表跟着上下跳 44dp；
   *   ② 只有两颗胶囊还右对齐 → 左边一大片空，看着像掉在那儿（用户：「错位了」）；
   *   ③ 它让顶部在有任务时多出**第 5 条横杠**（236dp）。
   * 搬进来之后顶部**恒定 4 条**，不再随任务增减跳动，而且这两个动作本来就不是高频操作。
   *
   * 暂停/取消两项**只在真有对象时才出现** —— 没有在跑的任务却摆一颗点了没反应的按钮是坏设计。
   */
  void showDownloadMaintenanceMenu(){
    LinearLayout panel=new LinearLayout(this);
    panel.setOrientation(LinearLayout.VERTICAL);
    panel.setPadding(dp(6),dp(4),dp(6),dp(4));
    final AlertDialog[] menu={null};
    boolean hasActive=false,hasPaused=false;
    for(DownloadEntry entry:downloadEntries){if(isDownloadActive(entry))hasActive=true;else if(entry.state.equals(DOWNLOAD_PAUSED))hasPaused=true;}
    if(hasActive||hasPaused){
      if(hasPaused)panel.addView(settingsAction(R.drawable.ic_play,"继续全部",v->{if(menu[0]!=null)menu[0].dismiss();resumeAllPaused();}),new LinearLayout.LayoutParams(-1,-2));
      else panel.addView(settingsAction(R.drawable.ic_pause,"暂停全部",v->{if(menu[0]!=null)menu[0].dismiss();togglePauseAllActive();}),new LinearLayout.LayoutParams(-1,-2));
      if(hasActive)panel.addView(settingsAction(R.drawable.ic_close,"取消全部",v->{if(menu[0]!=null)menu[0].dismiss();cancelAllActive();}),new LinearLayout.LayoutParams(-1,-2));
    }
    panel.addView(settingsAction(R.drawable.ic_delete_record,"删除全部记录",v->{if(menu[0]!=null)menu[0].dismiss();confirmDeleteAllDownloadRecords();}),new LinearLayout.LayoutParams(-1,-2));
    panel.addView(settingsAction(R.drawable.ic_delete_file,"删除全部文件",v->{if(menu[0]!=null)menu[0].dismiss();confirmDeleteAllDownloadedFiles();}),new LinearLayout.LayoutParams(-1,-2));
    /* DFW-16：分享密码此前明文留在历史里且没有清理入口，这里给一个（只清密码，不动记录）。
       语义修正：这一项原来用 ic_copy（复制图标），与「清除密码」毫无关系；改用钥匙图标 ic_key。 */
    panel.addView(settingsAction(R.drawable.ic_key,"清除保存的分享密码",v->{if(menu[0]!=null)menu[0].dismiss();confirmClearStoredSharePasswords();}),new LinearLayout.LayoutParams(-1,-2));
    TextView note=text("删除记录只清历史，本地文件保留；删除文件会同时清理对应记录。",11,MUTED);
    note.setPadding(dp(12),dp(6),dp(12),dp(2));
    panel.addView(note,new LinearLayout.LayoutParams(-1,-2));
    menu[0]=new AlertDialog.Builder(this).setTitle("更多操作").setView(panel).setNegativeButton("关闭",null).create();
    showRounded(menu[0]);
  }
  /**
   * 刷新下载页页头 ⋯ 的可见性。
   *
   * 改前这里管的是列表上方那条**全局操作行**（暂停全部/取消全部/更多）。2026-10-03 重排后
   * 那一行整行搬进了 ⋯ 菜单（见 `showDownloadMaintenanceMenu`），所以这里只剩一件事：
   * **空历史时把 ⋯ 藏起来**。
   *
   * 为什么空历史要藏：本项目一贯「不摆一颗点不动的按钮」。菜单里的项（暂停/取消全部、
   * 删除全部记录/文件、清除分享密码）在零记录时全是空操作。
   */
  void refreshDownloadHeaderMenu(){
    if(downloadHeaderMenuButton!=null)downloadHeaderMenuButton.setVisibility(downloadEntries.isEmpty()?View.GONE:View.VISIBLE);
  }
  HorizontalScrollView downloadStateFilterRow(LinearLayout strip){HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);scroll.setFillViewport(false);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setGravity(Gravity.CENTER_VERTICAL);strip.setPadding(dp(6),dp(2),dp(6),dp(2));scroll.addView(strip,new HorizontalScrollView.LayoutParams(-2,dpText(44)));return scroll;}
  // v1.22.3 窄屏修复：删 setMinimumWidth(屏宽-32dp) + CENTER_HORIZONTAL——该组合在内容超宽时把最右标签推到屏幕外（实测「下载完成」被裁成"下载完/"），
  // 改左对齐 + wrap_content：6 标签在 360dp 内排满，仍保留横向滚动兜底（大字体/超窄屏时不裁切）
  void showDownloads(){
    primaryBase(2);pageKind=2;clearFolderTrail();activeSource=null;downloadsPage=true;systemBackAction=null;
    /* 页头宽度分配（2026-10-03 实测，不是观感）：标题与路径原来**都是 weight=1**，各分 144dp，
       而路径内部固定件就占 105dp（内边距 21 + 图标 25 + 「保存路径」文字标签 51 + 左内边距 8），
       留给路径字符串只剩 **39dp ≈ 4 个 9sp 字** —— 真机截图里的「…ad/东方无限」是几何必然，不是偶发。
       两处修正：
         ① 标题改 wrap_content —— 它只需要约 92dp，却占着 144dp；
         ② 去掉「保存路径」这个文字标签 —— 文件夹图标已经表达了含义，contentDescription 也念得出来。
       两者合计把路径从 39dp 抬到约 140dp（≈ 12 个字符），配合 START 截断保留末级目录，这才是用户要看的。
       标题补 singleLine + END 截断：它现在会随字体放大变宽，大字体下必须截断，而不是换行后被 52dp 盒裁掉。 */
    TextView heading=primaryHeader("下载历史");
    if(heading!=null){heading.setSingleLine(true);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams headingLp=(LinearLayout.LayoutParams)heading.getLayoutParams();headingLp.width=LinearLayout.LayoutParams.WRAP_CONTENT;headingLp.weight=0;heading.setLayoutParams(headingLp);}
    String fullPath=downloadDisplayPath();LinearLayout path=new LinearLayout(this);path.setGravity(Gravity.CENTER_VERTICAL);path.setPadding(dp(6),0,dp(8),0);path.setContentDescription("保存路径 "+fullPath+"，点击用文件管理器打开，长按重新选择");path.setClickable(true);path.setFocusable(true);ImageView pathIcon=new ImageView(this);pathIcon.setImageResource(R.drawable.ic_folder);pathIcon.setColorFilter(PRIMARY);pathIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);path.addView(pathIcon,new LinearLayout.LayoutParams(dp(22),dp(22)));downloadPathText=text(fullPath,11,MUTED);downloadPathText.setSingleLine(true);downloadPathText.setEllipsize(android.text.TextUtils.TruncateAt.START);downloadPathText.setPadding(dp(8),0,0,0);path.addView(downloadPathText,new LinearLayout.LayoutParams(0,dpText(44),1));path.setOnClickListener(downloadDirectoryClick);path.setOnLongClickListener(v->{chooseDownloadDirectory();return true;});LinearLayout.LayoutParams pathParams=new LinearLayout.LayoutParams(0,dpText(44),1);pathParams.setMargins(dp(4),dp(2),0,dp(2));pageHeaderRow.addView(path,pathParams);/* [DFW-113 2026-10-03] 「更多」从列表上方的操作行**搬进页头**，做成 ⋮ 图标。
        为什么必须搬（2026-10-03 实测几何，不是观感）：操作行里三颗胶囊的前两颗是「暂停全部/取消全部」，
        它们只在 hasWork（有进行中或已暂停的任务）时才 VISIBLE；于是**历史里有记录、但没有在跑的任务**时，
        整排只剩一颗「更多」孤零零贴在最右边（实测 chip0=GONE chip1=GONE chip2=VISIBLE），
        用户 2026-10-03 真机截图反馈「『更多』两个字的按钮都已经崩坏了，位置都错误了」——就是它。
        搬进页头之后：
          ① 不存在「操作行只剩一颗胶囊」这个形态了；
          ② 没有在跑的任务时**整排操作行可以直接隐藏**，省下 dpText(44) 一行高度还给列表
             （上面已经有页头 + 搜索框 + 两条筛选条 = 4 行固定高度，列表是 weight=1 唯一被压缩的那个）。
        图标沿用 ic_dots_three（原先「更多」胶囊用的就是它，语义不变，只是换了位置与形态）。 */
      downloadHeaderMenuButton=iconButton(R.drawable.ic_dots_three,"更多操作");
      downloadHeaderMenuButton.setOnClickListener(v->showDownloadMaintenanceMenu());
      /* 48×48：token §4:128 触控区 ≥48dp。改前是 40×48，宽度不达。 */
      pageHeaderRow.addView(downloadHeaderMenuButton,new LinearLayout.LayoutParams(dp(48),dp(48)));
      sourceFilter=pageSearch(this::renderDownloads,200);
      downloadFilterStrip=new LinearLayout(this);downloadExtensionStrip=new LinearLayout(this);
      /* [2026-10-03 重排] 顶部纵向节奏。
         改前页头/搜索框/状态条/扩展名条**首尾相接、间距为 0**（实测 y 224→392→546→700），
         四条横杠糊成一坨，用户看到的"挤"和"错位"就是这个。
         现在每条之间留 8dp，列表与上一条之间留 10dp。
         `pageSearch` 是**共享方法**（别的页面也在用），所以不改它，只调它刚加进来的那个 box 的外边距。 */
      applyTopGap(root.getChildAt(root.getChildCount()-1),8);
      LinearLayout.LayoutParams stateStripLp=new LinearLayout.LayoutParams(-1,dpText(44));
      stateStripLp.topMargin=dp(8);
      root.addView(downloadStateFilterRow(downloadFilterStrip),stateStripLp);
      LinearLayout.LayoutParams extStripLp=new LinearLayout.LayoutParams(-1,dpText(44));
      extStripLp.topMargin=dp(8);
      root.addView(downloadExtensionChipRow(downloadExtensionStrip),extStripLp);
      /* [2026-10-03 重排] 「暂停全部 / 取消全部」那一行**整行搬进页头 ⋯ 菜单**（showDownloadMaintenanceMenu）。
         原来那一行三个毛病：① 只在有任务时出现，列表会跟着跳 44dp；
         ② 只有两颗胶囊还右对齐，左边一大片空，看着像掉在那儿（用户：「错位了」）；
         ③ 它让顶部在有任务时多出**第 5 条横杠**（236dp）。
         搬走之后顶部**恒定 4 条**，不再随任务增减跳动。 */
      pageScroll=new ScrollView(this);downloadList=new LinearLayout(this);downloadList.setOrientation(LinearLayout.VERTICAL);pageScroll.addView(downloadList);
      LinearLayout.LayoutParams listLp=new LinearLayout.LayoutParams(-1,0,1);
      listLp.topMargin=dp(10);
      root.addView(draggableList(pageScroll,downloadList,"拖动下载历史"),listLp);renderDownloadFilters();renderDownloads("");
  }
  /** 下载进度条统一样式：轨道回归色板 BORDER，填充 PRIMARY。
      默认样式（progressBarStyleHorizontal）的轨道是中性灰 #363636，是整页唯一的冷灰像素
      （2026-10-02 截图逐点采样确认：填充 #A78BFA = PRIMARY，轨道 #363636 不在色板里）。
      换 BORDER 后亮度几乎不变（横向分隔观感保留），但整页回到同一套紫调色板。 */
  ProgressBar downloadProgressBar(){
    ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
    bar.setMax(100);
    bar.setProgressTintList(android.content.res.ColorStateList.valueOf(PRIMARY));
    bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(BORDER));
    return bar;
  }
  /**
   * [DFW-113 2026-10-03 重做] 下载卡片：从「裸行」升级为**真正的卡片**。
   *
   * 改前的三个问题（都是实测/读码确认的，不是观感）：
   *  1. **没有卡片面**：行只有 padding，没有背景 —— 列表里 N 条记录糊成一片，
   *     条目之间没有边界，这是"看起来廉价"的最大单一原因。
   *  2. **图标底色是 BG**：`solidShape(BG,9)` 在 BG 页面上等于**隐形**，
   *     图标直接浮在背景上，42dp 的方形区域看起来是空的。
   *  3. **内容溢出容器（真 bug）**：`middle` 高度给的是 dp(70)，
   *     而它里面三件是 `dp(24) + dp(44) + dp(5) = dp(73)` —— **超出 3dp**。
   *     线性布局会把最后一件（进度条）压在边界上，进度条下沿被裁。
   *     这个溢出在大字体下更明显（子项不随字体缩放，但密度/字体组合会放大观感差异）。
   *
   * 改后：卡片面 = SURFACE + 1dp BORDER 描边 + Card 档圆角（走 solidShape 的 14→20 档），
   * 图标底 = SURFACE2（在 SURFACE 卡面上可辨识），内容高度 22+32+5=59 ≤ 60 装得下，
   * 卡片之间留 6dp 间距让"一条记录 = 一个对象"读得出来。
   */
  /**
   * 行内图标按钮：**48dp 触控区 + 22dp glyph**（token §4:128 触控区 ≥48dp、§7:160 glyph 20–22dp）。
   *
   * ## 为什么必须单独一个方法（2026-10-03 实测的坑）
   * `iconButton`（816）里写了 `setMinimumWidth/Height(dp(44))`，**但那个最小值在固定尺寸下不起作用**：
   * 调用方给的是 `new LinearLayout.LayoutParams(dp(38),dp(42))`，固定尺寸会以
   * `MeasureSpec.EXACTLY` 测量，`View.getDefaultSize` 在 EXACTLY 分支**直接取 specSize、忽略 minimum**
   * ⇒ 实际就是 38×42dp，不达 48dp。
   *
   * 所以触控区**只能由调用方给的 LayoutParams 决定**。这里把「48dp 视图 + 13dp 内边距 = 22dp glyph」
   * 钉成一个方法，避免每个调用点各写一个数、又慢慢漂回 38×42。
   */
  static final int ROW_ICON_BUTTON_DP=48;
  ImageButton rowIconButton(int icon,String description){
    ImageButton b=iconButton(icon,description);
    b.setPadding(dp(13),dp(13),dp(13),dp(13));
    return b;
  }
  LinearLayout.LayoutParams rowIconButtonLp(){return new LinearLayout.LayoutParams(dp(ROW_ICON_BUTTON_DP),dp(ROW_ICON_BUTTON_DP));}
  void addDownloadRow(DownloadEntry entry){
    LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(8),dp(6),dp(4),dp(6));row.setClickable(true);row.setFocusable(true);
    /* 卡片面：SURFACE 填充 + BORDER 描边。用 filterRipple 包一层，点卡片时涟漪落在卡片范围里。
       圆角走 solidShape 的量化档：传 16 → 实际渲染 20dp = token §4「Card = 20dp」。 */
    GradientDrawable cardBg=solidShape(SURFACE,16);
    cardBg.setStroke(dp(1),BORDER);
    row.setBackground(filterRipple(cardBg));
    ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);
    /* 图标底：传 24 → 量化成 26dp，而盒子是 40dp，GradientDrawable 会把圆角夹到半宽 = 20dp
       ⇒ **正好是圆形**，满足 token §4:127「按钮 pill/circle → 图标底 circle」。
       改前传 11 → 量化成 8dp，是个小圆角方块，不是圆形。 */
    icon.setBackground(solidShape(SURFACE2,24));icon.setClipToOutline(true);icon.setImageResource(R.drawable.ic_file);
    row.addView(icon,new LinearLayout.LayoutParams(dp(40),dp(40)));loadImage(entry.iconUrl,icon);
    LinearLayout middle=new LinearLayout(this);middle.setOrientation(LinearLayout.VERTICAL);middle.setPadding(dp(11),0,dp(8),0);
    TextView name=text(entry.name,14,TEXT,560);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);
    TextView label=text(downloadMetrics(entry),11,MUTED);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    ProgressBar bar=downloadProgressBar();bar.setIndeterminate(entry.state.equals(DOWNLOAD_RESOLVING));bar.setProgress(entry.percent);
    /* [2026-10-03] 已完成 / 排队中**不显示进度条**。
       改前这两种状态也画一条横杠：已完成时是满格的一条实线，紧贴在文字下方，
       **读起来就是一条下划线**（看截图的第一反应就是「这条线是什么」）；
       排队中时是 0% 的空槽，同样只是给卡片添了一条无意义的细线。
       真正需要它的只有「正在跑 / 暂停 / 失败」——那时进度才有信息量。 */
    if(entry.state.equals(DOWNLOAD_COMPLETED)||entry.state.equals(DOWNLOAD_WAITING))bar.setVisibility(View.GONE);
    /* 高度全部走 dpText()（DFW-13 适老化，规矩见 :728-737）：
       这些盒子**就是为了装文字**的，用 dp() 的话系统字体一放大、文字变大盒子不变 → 被裁。
       改前是 dp(22)/dp(32)/dp(60)/dp(76)，而**同一页**的操作行 :4251 早就用了 dpText(44) ——
       一页两套规矩，卡片那半边是漏改的。 */
    middle.addView(name,new LinearLayout.LayoutParams(-1,dpText(22)));
    middle.addView(label,new LinearLayout.LayoutParams(-1,dpText(32)));
    middle.addView(bar,new LinearLayout.LayoutParams(-1,dp(5)));
    row.addView(middle,new LinearLayout.LayoutParams(0,dpText(60),1));
    LinearLayout actions=new LinearLayout(this);actions.setGravity(Gravity.CENTER_VERTICAL);bindDownloadActions(entry,actions);
    /* 多选时动作按钮让位给复选框（理由见 selectionHiddenViews 的注释）。 */
    actions.setVisibility(downloadSelectionMode?View.GONE:View.VISIBLE);
    selectionHiddenViews.add(actions);
    row.addView(actions,new LinearLayout.LayoutParams(-2,dpText(44)));
    CheckBox check=new CheckBox(this);check.setClickable(false);check.setChecked(selectedDownloads.contains(entry));
    check.setVisibility(downloadSelectionMode?View.VISIBLE:View.GONE);
    row.addView(check,new LinearLayout.LayoutParams(dp(32),dpText(44)));
    downloadChecks.put(entry,check);downloadLabels.put(entry,label);downloadBars.put(entry,bar);downloadActions.put(entry,actions);downloadRows.put(entry,row);bindDownloadRowDescription(entry,row);
    /* 行点击：**所有状态都要有反馈**。改前只处理 失败/暂停/完成，
       等待中、解析中、已取消这三种点了毫无反应（无 toast、无动画、无任何提示），
       用户会以为"点了没用 / 界面坏了"。 */
    row.setOnClickListener(v->{if(downloadSelectionMode)toggleDownloadSelection(entry);else if(entry.state.equals(DOWNLOAD_FAILED))requestRetryDownload(entry);else if(entry.state.equals(DOWNLOAD_PAUSED))resumeDownload(entry);else if(entry.state.equals(DOWNLOAD_COMPLETED))installEntry(entry);else if(entry.state.equals(DOWNLOAD_CANCELLED))showNotice("这条记录已取消，右侧可以删除记录",false);else if(entry.state.equals(DOWNLOAD_WAITING))showNotice("排队等待中，轮到它就会自动开始",false);else if(entry.state.equals(DOWNLOAD_RESOLVING))showNotice("正在解析下载地址，稍等一下",false);});
    row.setOnLongClickListener(v->{enterDownloadSelection();toggleDownloadSelection(entry);return true;});
    /* 卡片间距 6dp：让"一条记录 = 一个对象"读得出来；左右各 4dp 让卡片不贴屏幕边。
       高度 dpText(76)：卡片里装的是文字（名字+指标），必须随字体缩放，否则大字体下被裁。 */
    LinearLayout.LayoutParams cardLp=new LinearLayout.LayoutParams(-1,dpText(76));
    cardLp.setMargins(dp(4),0,dp(4),dp(6));
    downloadList.addView(row,cardLp);
  }
  /**
   * 行内动作按钮。
   *
   * ## 尺寸（2026-10-03 重做）
   * 全部改成 {@link #rowIconButton} 的 **48×48dp**。改前是 `dp(38)×dp(42)` —— 触控区不达
   * token §4:128 要求的 48dp，而 `iconButton` 里的 `setMinimumWidth/Height(dp(44))` **救不了**
   * （固定 LayoutParams 走 MeasureSpec.EXACTLY，会忽略 minimum，理由见 {@link #rowIconButton}）。
   *
   * ## 层级（token §6：靠颜色做层级，不靠尺寸）
   * 「删除本地文件」是**不可逆**动作（真的删掉已下载的文件），用 `ERROR_TOKEN` 区分；
   * 「仅删除记录」只清理列表、保留文件，所以留品牌色。
   * 改前两者**同尺寸同色**，用户分不出哪个更重 —— 这是"界面看起来没设计"的典型症状。
   *
   * 「取消下载」**刻意不染红**：它可恢复（重新下载即可），染红会让整屏在下载期间一直有红色噪音。
   * 真正不可逆的才配红，这是本页的取舍。
   */
  void bindDownloadActions(DownloadEntry entry,LinearLayout actions){actions.removeAllViews();if(entry.state.equals(DOWNLOAD_COMPLETED)){ImageButton deleteFile=rowIconButton(R.drawable.ic_delete_file,"删除本地文件并同步删除下载记录"),deleteRecord=rowIconButton(R.drawable.ic_delete_record,"仅删除下载记录并保留本地文件");deleteFile.setColorFilter(ERROR_TOKEN);deleteFile.setOnClickListener(v->confirmDeleteFile(entry));deleteRecord.setOnClickListener(v->removeDownloadRecord(entry));actions.addView(deleteFile,rowIconButtonLp());actions.addView(deleteRecord,rowIconButtonLp());return;}if(isDownloadActive(entry)||entry.state.equals(DOWNLOAD_PAUSED)){ImageButton control=rowIconButton(entry.state.equals(DOWNLOAD_PAUSED)?R.drawable.ic_play:R.drawable.ic_pause,(entry.state.equals(DOWNLOAD_PAUSED)?"继续下载 ":"暂停下载 ")+entry.name);control.setOnClickListener(v->{if(entry.state.equals(DOWNLOAD_PAUSED))resumeDownload(entry);else pauseDownload(entry);});ImageButton cancel=rowIconButton(R.drawable.ic_close,"取消下载并清除断点 "+entry.name);cancel.setOnClickListener(v->cancelDownload(entry));actions.addView(control,rowIconButtonLp());actions.addView(cancel,rowIconButtonLp());return;}
    /* [DFW-25] 失败 / 已取消：以前这两种状态**一个行内按钮都没有** ——
       失败只能靠点整行重试（界面上没有任何提示说可以点），取消后想清掉记录只能长按多选
       或走全局「全部删除记录」。三种状态的可行动作数分别是 2 / 2 / 0，这正是本卡要治的"状态不统一"。
       现在补上与其它状态对等的行内出口：失败=重试 + 删记录，已取消=删记录。 */
    if(entry.state.equals(DOWNLOAD_FAILED)||entry.state.equals(DOWNLOAD_CANCELLED)){
      if(entry.state.equals(DOWNLOAD_FAILED)){
        ImageButton retry=rowIconButton(R.drawable.ic_refresh,"重新下载 "+entry.name);
        retry.setOnClickListener(v->requestRetryDownload(entry));
        actions.addView(retry,rowIconButtonLp());
      }
      ImageButton deleteRecord=rowIconButton(R.drawable.ic_delete_record,"仅删除下载记录并保留本地文件");
      deleteRecord.setOnClickListener(v->removeDownloadRecord(entry));
      actions.addView(deleteRecord,rowIconButtonLp());
    }}
  void bindDownloadRowDescription(DownloadEntry entry,View row){boolean retryable=entry.state.equals(DOWNLOAD_FAILED);row.setContentDescription(retryable?"下载失败，点击重新下载，右侧可重试或删除记录 "+entry.name:entry.state.equals(DOWNLOAD_CANCELLED)?"下载已取消，断点已清理，右侧可删除记录，长按可多选":entry.state.equals(DOWNLOAD_PAUSED)?"已暂停，点击继续 "+entry.name:entry.name+"，"+entry.state+"，长按可多选");}
  String formatSpeed(long bytesPerSecond){return bytesPerSecond<=0?"测速中":formatSize(bytesPerSecond)+"/s";}
  String formatEta(long seconds){if(seconds<0)return"剩余时间计算中";if(seconds<60)return"剩余 "+seconds+" 秒";if(seconds<3600)return"剩余 "+(seconds/60)+" 分 "+(seconds%60)+" 秒";return"剩余 "+(seconds/3600)+" 小时 "+((seconds%3600)/60)+" 分";}
  String downloadMoment(long time){return time<=0?"时间未知":android.text.format.DateFormat.format("yyyy-MM-dd HH:mm",time).toString();}
  String downloadMetrics(DownloadEntry entry){String done=formatSize(entry.downloadedBytes),total=entry.totalBytes>0?formatSize(entry.totalBytes):(entry.sourceSizeText.isEmpty()?"大小未知":entry.sourceSizeText),size=done+" / "+total;if(entry.state.equals(DOWNLOAD_RUNNING))return"下载中 · "+entry.percent+"% · "+size+"\n"+formatSpeed(entry.speedBps)+" · "+formatEta(entry.etaSeconds);if(entry.state.equals(DOWNLOAD_PAUSED))return"已暂停 · "+entry.percent+"% · "+size+"\n再次点击继续";if(entry.state.equals(DOWNLOAD_COMPLETED))return"下载完成 · "+size+"\n完成 "+downloadMoment(entry.completedAt);if(entry.state.equals(DOWNLOAD_FAILED)||entry.state.equals(DOWNLOAD_CANCELLED))return(entry.state.equals(DOWNLOAD_CANCELLED)?"已取消":"下载失败")+(entry.error.isEmpty()?"":" · "+entry.error)+" · "+size+"\n创建 "+downloadMoment(entry.createdAt);if(entry.state.equals(DOWNLOAD_RESOLVING))return"解析中 · "+total+"\n创建 "+downloadMoment(entry.createdAt);return entry.state+" · "+size+"\n创建 "+downloadMoment(entry.createdAt);}
    int downloadTransferParallelism(){int value=getSharedPreferences("download_settings-v1",MODE_PRIVATE).getInt("parallel_transfers",DEFAULT_TRANSFER_PARALLELISM);return Math.max(0,value);}
    void setDownloadTransferParallelism(int value){getSharedPreferences("download_settings-v1",MODE_PRIVATE).edit().putInt("parallel_transfers",Math.max(0,value)).apply();}
  int directResolveParallelism(){int value=getSharedPreferences("download_settings-v1",MODE_PRIVATE).getInt("parallel_resolves",0);return Math.max(0,value);}
  boolean validLanzouAccess(String value){if(value==null||value.isEmpty()||value.length()>64)return false;for(int i=0;i<value.length();i++)if(Character.isISOControl(value.charAt(i)))return false;return true;}
  void showLanzouAccessPrompt(String shareUrl,boolean rejectedPrevious,java.util.function.Consumer<String> onSubmit,Runnable onCancel){showLanzouAccessPrompt(shareUrl,"",rejectedPrevious,onSubmit,onCancel);}
  void showLanzouAccessPrompt(String shareUrl,String title,boolean rejectedPrevious,java.util.function.Consumer<String> onSubmit,Runnable onCancel){String url=shareUrl==null?"":shareUrl.trim();String label=title==null?"":title.trim();if(url.isEmpty()){if(onCancel!=null)onCancel.run();return;}runOnUiThread(()->{EditText input=new EditText(this);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);input.setHint("访问密码");LinearLayout shell=new LinearLayout(this);shell.setOrientation(LinearLayout.VERTICAL);shell.setPadding(dp(22),dp(8),dp(22),0);TextView context=text((label.isEmpty()?"需要密码的链接":"需要密码的项目："+label)+"\n"+url,12,MUTED);context.setTextIsSelectable(true);context.setPadding(0,0,0,dp(10));shell.addView(context,new LinearLayout.LayoutParams(-1,-2));shell.addView(input,new LinearLayout.LayoutParams(-1,-2));boolean[] handled={false};AlertDialog dialog=new AlertDialog.Builder(this).setTitle(rejectedPrevious?"密码错误，请重试":"需要访问密码").setView(shell).setNegativeButton("取消",(d,w)->{handled[0]=true;if(onCancel!=null)onCancel.run();}).setPositiveButton("确认",null).create();dialog.setOnCancelListener(d->{if(!handled[0]){handled[0]=true;if(onCancel!=null)onCancel.run();}});dialog.setOnShowListener(d->{Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE);positive.setOnClickListener(v->{String value=input.getText().toString().trim();if(!validLanzouAccess(value)){input.setError(value.isEmpty()?"请输入访问密码":"访问密码格式无效");return;}handled[0]=true;dialog.dismiss();onSubmit.accept(value);});input.requestFocus();if(dialog.getWindow()!=null)dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);});showRounded(dialog);});}
  void setDirectResolveParallelism(int value){int selected=Math.max(0,value);getSharedPreferences("download_settings-v1",MODE_PRIVATE).edit().putInt("parallel_resolves",selected).apply();if(directResolver!=null)directResolver.setParallelism(selected);}
  int sourceProbeParallelism(){int value=getSharedPreferences("source_settings-v1",MODE_PRIVATE).getInt("probe_parallelism",DEFAULT_SOURCE_PROBE_PARALLELISM);return Math.max(0,value);}
  void setSourceProbeParallelism(int value){getSharedPreferences("source_settings-v1",MODE_PRIVATE).edit().putInt("probe_parallelism",Math.max(0,value)).apply();}
  LinearLayout buildDownloadSettingsPanel(){
    int adaptiveSourceLimit=Math.max(1,LanzouCore.adaptiveSourceWorkers(0,Integer.MAX_VALUE)),adaptiveNetworkLimit=Math.max(1,LanzouCore.adaptiveNetworkWorkers(Integer.MAX_VALUE)),adaptiveTransferLimit=Math.max(1,TransferCoordinator.adaptiveUnlimitedLimit()),storedProbes=sourceProbeParallelism(),storedResolves=directResolveParallelism(),storedTransfers=downloadTransferParallelism(),probes=storedProbes==0?0:Math.min(storedProbes,adaptiveSourceLimit),resolves=storedResolves==0?0:Math.min(storedResolves,adaptiveNetworkLimit),transfers=storedTransfers==0?0:Math.min(storedTransfers,adaptiveTransferLimit);LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(0,dp(2),0,dp(12));TextView probeLabel=text(probes==0?"识别并发：自动适配":"识别并发："+probes,14,TEXT);LumaSlider probeBar=new LumaSlider(this);probeBar.setMax(adaptiveSourceLimit);probeBar.setProgressValue(probes==0?adaptiveSourceLimit:probes-1,false);probeBar.setContentDescription("调整添加源识别并发数，最右为按设备 CPU 与内存自动适配");probeBar.setOnChangeListener((position,user)->{int selected=position==adaptiveSourceLimit?0:position+1;probeLabel.setText(selected==0?"识别并发：自动适配":"识别并发："+selected);if(user)setSourceProbeParallelism(selected);});panel.addView(probeLabel,new LinearLayout.LayoutParams(-1,dp(42)));panel.addView(probeBar,new LinearLayout.LayoutParams(-1,dp(56)));TextView resolveLabel=text(resolves==0?"解析并发：自动极速":"解析并发："+resolves,14,TEXT);LumaSlider resolveBar=new LumaSlider(this);resolveBar.setMax(adaptiveNetworkLimit);resolveBar.setProgressValue(resolves==0?adaptiveNetworkLimit:resolves-1,false);resolveBar.setContentDescription("调整直链解析并发数，最右为自动极速；手动值可覆盖");resolveBar.setOnChangeListener((position,user)->{int selected=position==adaptiveNetworkLimit?0:position+1;resolveLabel.setText(selected==0?"解析并发：自动极速":"解析并发："+selected);if(user)setDirectResolveParallelism(selected);});panel.addView(resolveLabel,new LinearLayout.LayoutParams(-1,dp(42)));panel.addView(resolveBar,new LinearLayout.LayoutParams(-1,dp(56)));TextView transferLabel=text(transfers==0?"同时下载：自动适配":"同时下载："+transfers,14,TEXT);LumaSlider transferBar=new LumaSlider(this);transferBar.setMax(adaptiveTransferLimit);transferBar.setProgressValue(transfers==0?adaptiveTransferLimit:transfers-1,false);transferBar.setContentDescription("调整批量任务同时下载数，最右为按设备内存自动适配");transferBar.setOnChangeListener((position,user)->{int selected=position==adaptiveTransferLimit?0:position+1;transferLabel.setText(selected==0?"同时下载：自动适配":"同时下载："+selected);if(user)setDownloadTransferParallelism(selected);});panel.addView(transferLabel,new LinearLayout.LayoutParams(-1,dp(42)));panel.addView(transferBar,new LinearLayout.LayoutParams(-1,dp(56)));return panel;
  }
  LinearLayout buildDownloadPathPanel(){LinearLayout path=settingsRowShell();path.setBackground(settingsPress());applePressScale(path);ImageView folder=settingsLeadingIcon(R.drawable.ic_folder);path.addView(folder,settingsIconBox());TextView pathTitle=settingsRowTitle("下载路径");path.addView(pathTitle,new LinearLayout.LayoutParams(-2,-1));settingsDownloadPathText=text(downloadDisplayPath(),11,PRIMARY);settingsDownloadPathText.setSingleLine(true);settingsDownloadPathText.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);settingsDownloadPathText.setEllipsize(android.text.TextUtils.TruncateAt.START);path.addView(settingsDownloadPathText,new LinearLayout.LayoutParams(0,-1,1));path.setClickable(true);path.setFocusable(true);path.setLongClickable(true);path.setContentDescription("更改下载路径，当前 "+downloadDisplayPath());path.setOnClickListener(v->chooseDownloadDirectory());path.setOnLongClickListener(v->{chooseDownloadDirectory();return true;});return path;}
  LinearLayout buildPrivacyPolicyPanel(){LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(6),0,dp(6),dp(12));String policy="本软件不会建立自有云端账号系统。源规则、搜索记录、下载记录与保存路径等设置保存在本机。\n\n搜索、解析、图标读取、更新检查与下载会访问对应蓝奏云分享地址、软件官网或开源发布服务。下载文件仅保存在系统 Download 或用户所选目录。\n\nADB Shell 与静默安装为可选能力：应用仅在用户通过 Shizuku / Sui 明确授权后，将用户选择的 APK 文件描述符交给隔离的 shell/root 进程执行系统 pm install；不会上传 APK，也不会在未授权时伪造静默安装。\n\n软件仅在安装、目录访问、后台下载等对应功能时申请 Android 权限；拒绝权限不会上传本机文件。";TextView content=text(policy,13,TEXT);content.setGravity(Gravity.START);content.setTextIsSelectable(true);content.setLineSpacing(dp(3),1f);selectableLinks(content);panel.addView(content,new LinearLayout.LayoutParams(-1,-2));return panel;}
  LinearLayout buildSourceSettingsPanel(){LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(0,dp(2),0,dp(12));LinearLayout.LayoutParams groupParams=new LinearLayout.LayoutParams(-1,settingsRowHeight());groupParams.setMargins(0,dp(4),0,0);panel.addView(recoveryGroup(),groupParams);return panel;}
  // T4 关于页（用户指令：独立页面不弹窗；名单/感谢语/介绍/参考/隐私/版本号，顺序按 drafts-about-page.md 结构图；感谢语用户原文一字不改）
  void showAboutPage(){primaryBase(3);pageKind=7;activeSource=null;clearFolderTrail();systemBackAction=this::showSettings;LinearLayout body=aboutBackBar("关于");
    LinearLayout peopleCard=aboutCard();
    peopleCard.addView(aboutPersonRow(R.drawable.avatar_dongfang,"东方","制作者","一个人全程主导了这款软件的全部设计思路与开发实践，开发过程中使用Zcode+GLM和DeepSeek Harness+DeepSeek作为主要开发工具。"),new LinearLayout.LayoutParams(-1,-2));
    peopleCard.addView(aboutDivider());
    peopleCard.addView(aboutPersonRow(R.drawable.avatar_chenyu,"晨宇","辅助开发者","曾多次协助东方，在开发环境、理论知识方面给予指导帮助，在 AI 渠道方面提供建议和支持"),new LinearLayout.LayoutParams(-1,-2));
    peopleCard.addView(aboutDivider());
    peopleCard.addView(aboutPersonRow(R.drawable.avatar_wanyi,"晚意借北风","嗷呜小屋作者","提供嗷呜小屋软件库资源，提供极多的设计思路和灵感与建议，给予东方极大引流帮助，提供了绝对的精神动力"),new LinearLayout.LayoutParams(-1,-2));
    body.addView(peopleCard,aboutCardLp());
    LinearLayout thanksCard=aboutCard();TextView thanksText=text("感谢各位朋友的支持与帮助，因为有你们，我才可以更好的将我的想法实现出来。并帮助更多的人，有你们在，吾道不孤。",13,SET_T2);thanksText.setLineSpacing(dp(3),1f);thanksCard.addView(thanksText,new LinearLayout.LayoutParams(-1,-2));
    body.addView(thanksCard,aboutCardLp());
    LinearLayout introCard=aboutCard();introCard.addView(aboutHeading("软件介绍"),new LinearLayout.LayoutParams(-1,dp(24)));TextView introText=text("东方无限把找资源和日常工具放进了同一个应用。\n\n你可以浏览、搜索多个网盘分享站的公开目录，把文件直接下载到手机；下载完的安装包可以直接安装；还内置了一批常用的小工具和 AI 对话，遇到不懂的问题可以直接问。\n\n没有广告，不强制付费，也不收集你的隐私，所有数据都只保存在你自己的设备上。",13,TEXT);introText.setLineSpacing(dp(3),1f);introCard.addView(introText,new LinearLayout.LayoutParams(-1,-2));introCard.addView(aboutDivider());TextView opensrc=text("东方无限以 AGPL-3.0 协议开源 · 查看源码",12,PRIMARY);opensrc.setClickable(true);opensrc.setFocusable(true);opensrc.setOnClickListener(v->openInBrowser(DFWX_REPOSITORY,""));introCard.addView(opensrc,new LinearLayout.LayoutParams(-1,-2));
    body.addView(introCard,aboutCardLp());
    LinearLayout privacyCard=aboutCard();privacyCard.addView(aboutHeading("隐私政策"),new LinearLayout.LayoutParams(-1,dp(24)));privacyCard.addView(aboutDivider());privacyCard.addView(buildPrivacyPolicyPanel(),new LinearLayout.LayoutParams(-1,-2));
    body.addView(privacyCard,aboutCardLp());
    ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.addView(body,new ScrollView.LayoutParams(-1,-2));root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));}
  /**
   * [DFW-78] **诚信付费：站内页**。
   *
   * 用户 2026-10-01 原话（第二次投诉这一页的动画）：
   * > "点击诚信付费按钮后的动画效果和关闭那个页面的动画效果太磨叽且不自然不流畅，重置，
   * >  用和打开关于东方无限页面一样的动画效果。"
   *
   * 脚手架**完全照 `showAboutPage()`**：`primaryBase(3)` 把设置当底座 + 自己的 `pageKind` +
   * `systemBackAction=this::showSettings` + `aboutBackBar("诚信付费")`。于是返回箭头、系统返回、
   * 进/出转场（`animatePage` 推入 +1 / 弹出 -1）全都自动与关于页一致 —— 这正是"和关于东方无限
   * 一样"唯一可能的实现方式。
   *
   * pageKind 取 **11**（不是 10）：grep 确认 0..10 已被占用，10 是公告页（`showNoticeCenter`）。
   *
   * 红线不变：不付费也能完整使用；本页任何位置都有「暂时不支持，继续使用」退出路径。
   * 文案、图标、收款码资源、金额、免责说明逐字搬自 `SupportActivity`，一个字都没改。
   */
  static final int PAGE_KIND_SUPPORT=11;
  /** 站内付费页的内容容器：未解锁的支持页与已解锁的感谢页在**同一页里换内容**，不再跳窗口。 */
  LinearLayout supportBody;
  boolean supportThankYouMode;
  /** [BRAND-001] 解锁进行中：防连点导致整页重建途中被再次触发。 */
  boolean supportUnlocking;
  /** [BRAND-001] 解锁流程真正被执行的次数（供测试证明"连点只进一次"）。 */
  int supportUnlockInvocations;
  /** 解锁心形的确认微弹时长（ms）：沿用站内那条 220ms 配方，上限由规范 §2.1「动效 VPA ≤300ms」钉死。 */
  static final long SUPPORT_UNLOCK_MS=220;
  /**
   * 回弹曲线：站内 `NavBall.PRESS_UP` 那条「弱过冲回弹 (0.2,0.9,0.3,1.05)」（规范 §2 允许 spatial 受控 overshoot）。
   *
   * **必须懒建、不能写成 static final**：`PathInterpolator` 的构造会碰 `android.graphics.Path`，
   * 在 Robolectric 沙箱里类初始化阶段构造它会撞上 `ConfigurationRegistry.instance == null`
   * （表现为整个测试类 `ExceptionInInitializerError`，而且**取决于测试类执行顺序**——典型的偶发假红）。
   */
  PathInterpolator supportPressUp;
  PathInterpolator supportPressUp(){
    if(supportPressUp==null)supportPressUp=new PathInterpolator(0.2f,0.9f,0.3f,1.05f);
    return supportPressUp;
  }

  /**
   * 站内**唯一**的标准缓动曲线 = `PathInterpolator(0.2, 0, 0, 1)`（M3 emphasized 减速段）。
   *
   * ## 为什么需要这个方法（[DFW-86] 帧级取证的结论）
   * 代码里一直写着"站内其它动效一律 `PathInterpolator(0.2,0,0,1)`"（见 animatePullOffsetTo 上方注释），
   * 但**从来没有任何机制保证它**。`ViewPropertyAnimator` 在**不设曲线**时用的是安卓默认的
   * `AccelerateDecelerateInterpolator` —— 那是**另一条曲线**：两头慢、中间快，
   * 与"快速起步、长尾减速"的 M3 emphasized 手感**正好相反**。
   *
   * 2026-10-01 取证：`MainActivity` 里 37 条动画语句中 **22 条没设曲线**，
   * 也就是全站**同时存在两个缓动族**。用户连续三轮反馈"割裂/不丝滑"，
   * 根因就在这里 —— 不是某一处动画参数写错了，而是**同一屏里的两个元素可能走在不同的曲线上**。
   * （典型：抽屉里的箭头用标准曲线、旁边的文字用默认曲线，一起动就会"打架"。）
   *
   * ## 为什么必须懒建、不能写成 static final
   * 与 [supportPressUp] 同一个坑：`PathInterpolator` 的构造会碰 `android.graphics.Path`，
   * 在类初始化阶段创建会在 Robolectric 下抛 `ExceptionInInitializerError`
   * （`ConfigurationRegistry.instance == null`）。这里踩过一次，别再改回去。
   */
  /**
   * 动效时长刻度 —— **只许用这五档**，全部取自规范 `docs/design/wear-ui-system.md` 第 68 行列出的
   * MDC `motion_duration` 官方刻度。
   *
   * ## 为什么要有这组常量（[DFW-86] 取证结论）
   * 收敛前 `MainActivity` 里有 **15 个不同时长**：70/80/90/120/130/140/160/170/190/200/220/240/250/280/300。
   * 其中**只有 200/250/300 三档在官方刻度上**，其余 12 档都是随手写的。
   * 规范早就写好了刻度（`short1 50 / short2 100 / short3 150 / short4 200 / medium1 250 / medium2 300 …`），
   * 代码却没用 —— 这跟缓动曲线是同一类问题：**规范写了一套，代码另跑一套**。
   *
   * 收敛原则：**取最近的官方刻度**（最大变动 ±30ms），不重新发明数值。
   *
   * ## 外部依据（不是我们拍的）
   * - Material 3 官方《Easing and duration》：
   *   "An Enter transition has a **long** duration of 500ms · An Exit transition has a **short** duration of 200ms"
   *   —— 入场明显长于退场，方向与规范第 89 行的自检项「入场 ≥ 退场」一致。
   * - Emil Kowalski：UI 动画**一律 300ms 以内**，超出需要理由。
   * - Norton Design System：`duration-open 250ms` / `duration-close 200ms`（同样是开慢关快）。
   *
   * 五档语义（新增动画时按语义挑，不要写别的数）：
   *  - [DUR_EXIT_FAST] 100 —— 列表/网格交叉淡入淡出的**退场**半程、瞬时反馈
   *  - [DUR_SMALL]     150 —— 常规退场、小元素位移
   *  - [DUR_BASE]      200 —— 常规入场、小元素入场
   *  - [DUR_MEDIUM]    250 —— 中等容器入场
   *  - [DUR_LARGE]     300 —— 大容器 / 页面级过渡
   */
  static final int DUR_EXIT_FAST=100,DUR_SMALL=150,DUR_BASE=200,DUR_MEDIUM=250,DUR_LARGE=300;

  /**
   * **页面转场专用例外**：推入 280ms / 返回 240ms。
   *
   * 为什么不并进上面五档：这两个数是**上一轮按真机反馈刻意调过的** ——
   * 当时 300ms 被判定"太慢"，`PageTransitionMotionJvmTest` 里专门有一条
   * `assertFalse("推入不许再回到 300ms")` 守着这个决定。
   *
   * [DFW-86] 收敛时长刻度时，机械地把 280→300、240→250 套上去，
   * **把这个决定覆盖掉了** —— 测试立刻变红。这是对的：
   * 一条统一的规则**不该碾过一个有测试守护的刻意决定**。
   * 所以这里把它们**显式登记为例外**，而不是偷偷放宽规则或改掉测试。
   *
   * 它们**故意保持字面量**（不抽成常量）：`PageTransitionMotionJvmTest` 里那三条断言
   * 查的就是 `.setDuration(280)` / `.setDuration(240)` / 不许回到 300 这几个字面量，
   * 那是这个决定的**权威守卫**。抽成常量会逼着我去改那条守卫 ——
   * 而「改一条已有的守卫测试」正是最容易制造假绿的动作。**不动它。**
   */

  PathInterpolator standardEase;
  PathInterpolator standardEase(){
    if(standardEase==null)standardEase=new PathInterpolator(0.2f,0f,0f,1f);
    return standardEase;
  }

  void showSupportPage(){
    primaryBase(3);
    pageKind=PAGE_KIND_SUPPORT;
    activeSource=null;
    clearFolderTrail();
    systemBackAction=this::showSettings;
    LinearLayout body=aboutBackBar("诚信付费");
    supportBody=body;
    supportUnlocking=false;
    supportUnlockInvocations=0;
    // v1.5.1 用户定调：无论是否已解锁，进来永远先看到赞助码页；点下方解锁按钮才切到爱心感谢页
    renderSupportPage();
    ScrollView scroll=new ScrollView(this);
    scroll.setFillViewport(true);
    scroll.addView(body,new ScrollView.LayoutParams(-1,-2));
    root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  }

  /** 未解锁：支持页（内容逐字搬自 `SupportActivity.renderSupportPage`，卡片换成站内 `aboutCard`）。 */
  void renderSupportPage(){
    supportThankYouMode=false;
    supportBody.removeAllViews();
    // 大标题（用户 2026-10-01 口述：标题下那行「诚信付费 ￥5 · 一次付清 · 承诺永久更新」删掉，
    // ￥5 只留在中段价格区可见 —— 价格区本身保留不动）
    TextView title=text("支持 "+PRODUCT_NAME,24,TEXT);
    title.setTypeface(AppFonts.bold(this));
    title.setIncludeFontPadding(false);
    LinearLayout.LayoutParams titleLp=new LinearLayout.LayoutParams(-2,dp(40));
    titleLp.setMargins(0,dp(6),0,0);
    supportBody.addView(title,titleLp);
    // 开发者信（诚意区，第一人称，v1.5.0 用户定调：委婉、少小字）——成本与坚持 + 学生分层委婉化 + 感谢
    LinearLayout letterCard=aboutCard();
    TextView letter=new TextView(this);
    letter.setText("这个应用没有广告，也不强制付费。\n维护和更新都需要成本，我想高质量地一直做下去。\n还在读书、暂时没有收入的朋友，点击下方按钮直接使用即可；\n如果力所能及，这 5 元会成为我继续更新的动力和底气。\n谢谢你的支持。");
    letter.setTextColor(TEXT);
    letter.setTextSize(14);
    letter.setLineSpacing(dp(4),1f);
    letter.setTypeface(AppFonts.normal(this));
    letterCard.addView(letter,new LinearLayout.LayoutParams(-1,-2));
    letterCard.setContentDescription("开发者的话：这个应用没有广告，也不强制付费；还在读书的朋友可以直接使用。");
    supportBody.addView(letterCard,aboutCardLp());
    // 权益行（用户 2026-10-01 口述：原来 3 条 emoji 短语全部删掉，只留这一条长句；原话照抄，仅补句末句号）
    LinearLayout perks=aboutCard();
    // [BRAND-001] 内边距与开发者信同基准（原来 14/6，与信的 16/14 不在一个节奏上）
    perks.setPadding(dp(16),dp(8),dp(16),dp(8));
    perks.addView(benefitRow(R.drawable.ic_trophy,"每周自费续 1 万次 DeepSeek v4.1，自动刷新，专供诚信付费的朋友，请诚信付费。"),new LinearLayout.LayoutParams(-1,-2));
    supportBody.addView(perks,aboutCardLp());
    // [BRAND-001] 价格区从"裸行"升级成一张卡：上面是开发者信、中间是权益、这里是价格，
    // 三块同一节奏才像一页设计过的产品页；￥5 仍然在**中段**一眼可见（用户 2026-10-01 的要求）。
    LinearLayout priceCard=aboutCard();
    LinearLayout price=new LinearLayout(this);
    price.setGravity(Gravity.CENTER_VERTICAL);
    TextView amount=text("￥5",30,PRIMARY);
    amount.setTypeface(AppFonts.bold(this));
    price.addView(amount,new LinearLayout.LayoutParams(-2,-2));
    LinearLayout priceCol=new LinearLayout(this);
    priceCol.setOrientation(LinearLayout.VERTICAL);
    TextView priceNote1=text("诚信付费 · 一次付清",14,TEXT);
    priceNote1.setTypeface(AppFonts.bold(this));
    TextView priceNote2=text("承诺永久更新 · 绝不停更",11,MUTED);
    priceCol.addView(priceNote1,new LinearLayout.LayoutParams(-2,-2));
    LinearLayout.LayoutParams note2Lp=new LinearLayout.LayoutParams(-2,-2);
    note2Lp.topMargin=dp(2);
    priceCol.addView(priceNote2,note2Lp);
    LinearLayout.LayoutParams priceColLp=new LinearLayout.LayoutParams(-2,-2);
    priceColLp.leftMargin=dp(12);
    price.addView(priceCol,priceColLp);
    priceCard.addView(price,new LinearLayout.LayoutParams(-1,-2));
    supportBody.addView(priceCard,aboutCardLp());
    // 收款区：微信单卡全宽（用户仅收款微信；码图撑满卡宽，消除两侧留白）
    supportBody.addView(codeCard("微信收款码",R.drawable.pay_wechat,"微信扫码 · 付 5 元"),aboutCardLp());
    // 主 CTA：第一人称动词句，零验证解锁（v1.5.1 删「复制金额」小按钮——重复无用，减小字）
    Button confirm=new Button(this);
    confirm.setText("诚信付费，解锁全部权限");
    confirm.setAllCaps(false);
    confirm.setTextSize(15);
    confirm.setTypeface(AppFonts.bold(this));
    confirm.setTextColor(BG);
    // 真胶囊：`solidShape` 的半径量化会把 24 压成 26（做出来是圆角方块而不是胶囊），
    // 所以直接走 PremiumSurface.pill（半径 = 高度一半）。高度也统一到 56dp。
    confirm.setBackground(filterRipple(PremiumSurface.pill(PRIMARY,dp(56),0,0,PremiumSurface.HIGHLIGHT)));
    confirm.setContentDescription("诚信付费，解锁内置 AI 使用权限；不付费也可以完整使用其它功能");
    // [BRAND-001] 防连点：解锁会整页重建，连点两次会在重建途中再触发一次，表现为按钮闪一下/白屏一帧。
    // 解锁是本地幂等写，但重建不是幂等的。
    confirm.setOnClickListener(v->{
      if(supportUnlocking)return;
      supportUnlocking=true;
      confirm.setEnabled(false);
      confirm.setAlpha(0.6f);
      unlockNow();
    });
    LinearLayout.LayoutParams ctaParams=new LinearLayout.LayoutParams(-1,dp(56));
    ctaParams.setMargins(0,dp(6),0,0);
    supportBody.addView(confirm,ctaParams);
    // [DFW-43] 主 CTA 也要"按下去有反应"（规范 §3：主反馈 = scale + 提亮，ripple 只是辅助）
    applePressScale(confirm);
    // 辅助链接：暂时不支持（降级路径永远存在）
    TextView skip=text("暂时不支持，继续使用",13,PRIMARY);
    skip.setGravity(Gravity.CENTER);
    skip.setClickable(true);
    skip.setFocusable(true);
    // [BRAND-001] 降级路径原来是一个**没有任何按压反馈**的裸 TextView：点下去毫无回应，
    // 而这恰恰是"不付费也能走"的唯一出口，最不该让人怀疑自己点没点到。
    skip.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));
    skip.setContentDescription("暂时不支持，继续使用（不付费也能完整使用其它功能）");
    // [DFW-78] 退出方向必须与返回箭头一致（弹出 -1），否则这条降级出口的转场方向会反。
    skip.setOnClickListener(v->{pageDirection=-1;showSettings();});
    // [DFW-43] 降级出口是最不该让人怀疑"点没点到"的控件，同样补上按压形变（红线：这条出口必须能走）
    applePressScale(skip);
    LinearLayout.LayoutParams skipLp=new LinearLayout.LayoutParams(-1,dp(44));
    skipLp.topMargin=dp(6);
    supportBody.addView(skip,skipLp);
    // 底部小字：诚实说明本地标记
    TextView footnote=text("解锁记录保存在本机 · 不付费也可以完整使用",11,MUTED);
    footnote.setGravity(Gravity.CENTER);
    footnote.setPadding(0,dp(8),0,0);
    supportBody.addView(footnote,new LinearLayout.LayoutParams(-1,-2));
  }

  /** 已解锁：感谢页（状态页，同一站内页里换内容；返回箭头由页头常驻，不必重建）。 */
  void renderThankYou(){
    supportThankYouMode=true;
    supportBody.removeAllViews();
    LinearLayout page=new LinearLayout(this);
    page.setOrientation(LinearLayout.VERTICAL);
    page.setGravity(Gravity.CENTER);
    TextView heart=text("❤",56,PRIMARY);
    heart.setGravity(Gravity.CENTER);
    page.addView(heart,new LinearLayout.LayoutParams(-1,dp(96)));
    TextView title=text("已解锁 · 谢谢你",24,TEXT);
    title.setTypeface(AppFonts.bold(this));
    title.setGravity(Gravity.CENTER);
    title.setPadding(0,dp(12),0,0);
    page.addView(title,new LinearLayout.LayoutParams(-1,dp(44)));
    // 诚信徽章（研究 M3：支持后即时反馈=动效+徽章；静态徽章，无循环动画，不画蛇添足）
    TextView badge=text("诚信支持者",12,PRIMARY);
    badge.setTypeface(AppFonts.bold(this));
    GradientDrawable badgeBg=solidShape(ThemeEngine.tint(PRIMARY,28),20);
    badge.setBackground(badgeBg);
    badge.setPadding(dp(14),dp(5),dp(14),dp(5));
    LinearLayout badgeWrap=new LinearLayout(this);
    badgeWrap.setGravity(Gravity.CENTER);
    badgeWrap.addView(badge,new LinearLayout.LayoutParams(-2,dp(28)));
    LinearLayout.LayoutParams badgeLp=new LinearLayout.LayoutParams(-1,-2);
    badgeLp.topMargin=dp(10);
    page.addView(badgeWrap,badgeLp);
    long paidAt=Support.paidAt(this);
    String date=paidAt>0?android.text.format.DateFormat.getDateFormat(this).format(new java.util.Date(paidAt)):"";
    TextView detail=text(date.isEmpty()?"内置 AI 使用权限已开放":"解锁于 "+date+" · 内置 AI 使用权限已开放",13,MUTED);
    detail.setGravity(Gravity.CENTER);
    detail.setPadding(0,dp(10),0,0);
    page.addView(detail,new LinearLayout.LayoutParams(-1,dp(30)));
    TextView footnote=text("本软件承诺永久更新 · 绝不停更\n这份支持会变成继续更新的底气",11,MUTED);
    footnote.setGravity(Gravity.CENTER);
    footnote.setPadding(0,dp(12),0,0);
    page.addView(footnote,new LinearLayout.LayoutParams(-1,-2));
    LinearLayout.LayoutParams pageLp=new LinearLayout.LayoutParams(-1,-2);
    pageLp.topMargin=dp(18);
    supportBody.addView(page,pageLp);
    playUnlockAnimation(heart);
  }

  /** 权益行：图标 + 一句说明（用户 2026-10-01 口述要求）。图标走 Phosphor 矢量（ic_trophy），与文字同色；
   *  行高由原来的固定 38dp 改 WRAP_CONTENT + 上下内距 —— 换成一条长句后会折行，定高会把第二行裁掉。 */
  LinearLayout benefitRow(int iconRes,String phrase){
    LinearLayout row=new LinearLayout(this);
    row.setGravity(Gravity.CENTER_VERTICAL);
    row.setPadding(0,dp(9),0,dp(9));
    ImageView icon=new ImageView(this);
    icon.setImageResource(iconRes);
    icon.setColorFilter(PRIMARY);
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    row.addView(icon,new LinearLayout.LayoutParams(dp(20),dp(20)));
    TextView label=text(phrase,13,TEXT);
    label.setLineSpacing(dp(3),1f);
    label.setPadding(dp(10),0,0,0);
    row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
    return row;
  }

  /** 收款码卡片：白底浮起（深色页面上收款码必须白底才可扫）；码图 adjustViewBounds 撑满卡宽、
   *  高度按原图比例自适应（v1.4.2 消除 FIT_CENTER 定高造成的两侧大留白），卡片 padding 归零 +
   *  clipToOutline 让码图边缘贴合卡片圆角 */
  LinearLayout codeCard(String label,int drawableRes,String hint){
    LinearLayout card=new LinearLayout(this);
    card.setOrientation(LinearLayout.VERTICAL);
    // [DFW-43] 圆角从 16 收敛到 **Card token 20dp**（规范 §4：信息卡片 radius 20dp 唯一值）——
    // 本页原来同一页有 20（开发者信/权益/价格卡）与 16（收款码卡）两种卡片圆角，不自洽。
    GradientDrawable bg=solidShape(Color.WHITE,20);
    bg.setStroke(dp(1),BORDER);
    card.setBackground(bg);
    card.setElevation(dp(2));
    card.setClipToOutline(true);
    ImageView code=new ImageView(this);
    code.setImageResource(drawableRes);
    code.setScaleType(ImageView.ScaleType.FIT_CENTER);
    code.setAdjustViewBounds(true);
    code.setContentDescription(label+"，扫码支付 ￥5 元");
    card.addView(code,new LinearLayout.LayoutParams(-1,-2));
    TextView name=text(label,12,Color.DKGRAY);
    name.setGravity(Gravity.CENTER);
    name.setPadding(dp(6),dp(10),dp(6),dp(12));
    card.addView(name,new LinearLayout.LayoutParams(-1,-2));
    return card;
  }

  /** 零验证解锁：唯一按钮，付费者与暂无收入者同一入口（文案已委婉分层，不再设独立免费链接） */
  void unlockNow(){
    supportUnlockInvocations++;
    Support.unlock(this);
    renderThankYou();
    showNotice("已解锁内置 AI 使用权限 · 谢谢你",false);
  }

  /** 解锁反馈：克制的单次缩放+淡入（无循环；motionEnabled 门控在系统动画关闭时跳过）。
   *
   *  **360ms → 220ms**：360ms 破了规范 §2.1「页面转场/动效 VPA ≤300ms」铁律（历史事故复盘定的红线）。
   *  取 220ms 的理由：这是站内**唯一被真机长期验证过**的"缩放回弹"时长——`NavBall.PRESS_UP` 与
   *  v1.5.0 按压缩放的注释都是同一条「松手 220ms 弱过冲回弹 (0.2,0.9,0.3,1.05)」。本页心形出现
   *  本来就是一次"确认微弹"（规范 §7.5），用同一条曲线、同一条时长，观感与站内一致。 */
  void playUnlockAnimation(View target){
    if(!motionEnabled())return;
    target.setScaleX(0.6f);
    target.setScaleY(0.6f);
    target.setAlpha(0f);
    target.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(SUPPORT_UNLOCK_MS).setInterpolator(supportPressUp()).start();
  }

  void restoreSettingsDefaults(){AlertDialog prompt=new AlertDialog.Builder(this).setTitle("恢复默认设置？").setMessage("将恢复搜索线程数、让位时间、自动翻页、蓝奏云链接打开方式、识别、解析与下载并发、静默安装与下载后自动安装开关、安装线程数及下载路径。源列表和下载记录不会删除。").setNegativeButton("取消",null).setPositiveButton("恢复",(dialog,which)->performSettingsDefaults()).create();showRounded(prompt);}
  void performSettingsDefaults(){sessionSearchConcurrency=0;sessionSearchBatchSeconds=15;sessionSearchMaxPages=0;sessionSearchViewRate=0;sessionSearchRecursiveFolders=false;sessionSearchFuzzyMask=0;sessionSearchFuzzyMatching=false;sessionFileListModeMask=Models.SearchOptions.MASK_API|Models.SearchOptions.MASK_DIRECTORY;sessionSearchModeMask=Models.SearchOptions.MASK_DIRECTORY;sessionSearchMode=Models.SearchOptions.MODE_DIRECTORY;perfTier=1;perfLowMotion=false;sessionBackgroundIndex=true;sessionIndexEnabled=false;sessionIndexThreads=0;sessionIndexRetentionHours=24;sessionListInitialCount=50;sessionListAppendCount=50;sessionSourceListDisplayCount=SOURCE_LIST_MIN_DISPLAY;core.setDirectoryCachingEnabled(true);sessionAutoExpand=true;sessionAutoExpandInitialPages=1;sessionAutoExpandNextPages=1;directLanzouListOpen=false;showSourceLinks=false;sessionOpenWebExternal=false;sessionBatchDownloadSingleItem=true;sessionLanzouBaseOrigin="https://oreojiang.lanzout.com";sessionLanzouTimeoutFailover=true;sessionUaPreset=LanzouCore.UA_PRESET_MOBILE_CHROME;sessionUaScopeMask=LanzouCore.UA_SCOPE_ALL;sessionUaFileListPreset=sessionUaPreset;sessionUaDirectorySearchPreset=sessionUaPreset;sessionUaApiSearchPreset=sessionUaPreset;sessionUaDirectPreset=sessionUaPreset;sessionCustomUserAgent="";applyUserAgentSettings();sessionSearchCategory="全部";persistSearchSettings();setSourceProbeParallelism(DEFAULT_SOURCE_PROBE_PARALLELISM);setDirectResolveParallelism(0);setDownloadTransferParallelism(DEFAULT_TRANSFER_PARALLELISM);setSilentInstallPreference(false);setInstallParallelism(DEFAULT_INSTALL_PARALLELISM);getSharedPreferences("download_destination",MODE_PRIVATE).edit().remove("tree").remove("path").apply();showNotice("已恢复默认设置",false);pageDirection=0;showSettings();}
  void confirmRestoreOfficialSources(){AlertDialog prompt=new AlertDialog.Builder(this).setTitle("确认恢复官方源？").setMessage("只恢复发行包内置官方源；用户添加的源和合集成员会保留。").setNegativeButton("取消",null).setPositiveButton("恢复",(dialog,which)->restoreOfficialSources()).create();showRounded(prompt);}
  void restoreOfficialSources(){if(!BuildConfig.IS_FULL)return;io.execute(()->{try{List<Models.Source> restored=core.restoreOfficialSources();runOnUiThread(()->{++sourceDataRevision;if(sourcePageSources!=null){sourcePageSources.clear();sourcePageSources.addAll(restored);if(sourceGrid!=null&&sourceHeading!=null)renderSources(sourceGrid,sourcePageSources,sourceFilter==null?"":sourceFilter.getText().toString().trim(),sourceHeading);}showNotice("已恢复官方源 · 当前 "+restored.size()+" 个源",false);});}catch(Exception error){showNotice("恢复官方源失败："+friendlyError(error),true);}});}
  void confirmResetAllSources(){AlertDialog prompt=new AlertDialog.Builder(this).setTitle("重置全部源列表？").setMessage("这会删除全部用户源、自建合集、官方合集用户增量与源分类，并恢复当前版本发行基线。下载记录不会删除。").setNegativeButton("取消",null).setPositiveButton("重置",(dialog,which)->io.execute(()->{try{List<Models.Source> reset=core.resetAllSources();runOnUiThread(()->{sourceCategories.clear();activeSourceCategory="全部";persistSourceCategories();selectedSourceUrls.clear();++sourceDataRevision;if(sourcePageSources!=null){sourcePageSources.clear();sourcePageSources.addAll(reset);if(sourceGrid!=null&&sourceHeading!=null){renderSourceCategories(sourcePageSources);renderSources(sourceGrid,sourcePageSources,sourceFilter==null?"":sourceFilter.getText().toString().trim(),sourceHeading);}}showNotice("源列表已重置 · 当前 "+reset.size()+" 个源",false);});}catch(Exception error){showNotice("重置源列表失败："+friendlyError(error),true);}})).create();showRounded(prompt);}
  /** 多选模式下把每行的动作按钮整体让位给复选框（见 `selectionHiddenViews` 注释）。 */
  void setSelectionActionsHidden(boolean hidden){
    for(View v:selectionHiddenViews){
      if(v!=null)v.setVisibility(hidden?View.GONE:View.VISIBLE);
    }
  }
  void enterDownloadSelection(){if(downloadSelectionMode)return;downloadSelectionMode=true;setSelectionActionsHidden(true);
    /* [2026-10-03] 悬浮球让位。球默认贴右下角距底 36dp，而选择条从底部升起 160dp ——
       实测球 x=1015..1211 / y=2478..2674 与选择条 x=56..1204 / y=2240..2800 相交，
       把最后一列上下两排的图标**全盖掉了**（用户：「按钮都被黑色遮住了，甚至覆盖住其他按钮」）。
       让位方式与理由见 NavBall.setSuppressed。 */
    if(navBall!=null)navBall.setSuppressed(true);downloadSelectionSummary=text("已选 0",11,TEXT);downloadSelectionAllButton=toolbarTextButton("全选");downloadSelectionAllButton.setOnClickListener(v->toggleSelectAllDownloads());ImageButton directory=iconButton(R.drawable.ic_folder,"打开保存路径"),more=iconButton(R.drawable.ic_open_with,"更多方式打开所选文件"),copyLink=iconButton(R.drawable.ic_copy,"复制所选原链接"),shareFiles=iconButton(R.drawable.ic_share_file,"分享所选本地文件"),location=iconButton(R.drawable.ic_folder_open,"打开所选文件所在路径"),install=iconButton(R.drawable.ic_install,"静默安装所选 APK"),pause=iconButton(R.drawable.ic_pause,"暂停或继续所选未完成下载任务"),cancel=iconButton(R.drawable.ic_close,"取消所选未完成下载任务"),deleteRecords=iconButton(R.drawable.ic_delete_record,"删除所选记录"),deleteFiles=iconButton(R.drawable.ic_delete_file,"删除所选本地文件"),shareLinks=iconButton(R.drawable.ic_share,"分享所选原链接"),close=iconButton(R.drawable.ic_close,"退出多选");directory.setOnClickListener(downloadDirectoryClick);more.setOnClickListener(v->openSelectedWithMore());copyLink.setOnClickListener(v->copySelectedDownloadLinks());shareFiles.setOnClickListener(v->shareSelectedDownloadedFiles());location.setOnClickListener(v->openSelectedDownloadLocations());install.setOnClickListener(v->installSelectedEntries());pause.setOnClickListener(v->togglePauseSelectedDownloads());cancel.setOnClickListener(v->cancelSelectedDownloads());deleteRecords.setOnClickListener(v->confirmDeleteSelectedRecords());deleteFiles.setOnClickListener(v->confirmDeleteSelectedFiles());shareLinks.setOnClickListener(v->shareSelectedDownloadLinks());close.setOnClickListener(v->exitDownloadSelection());downloadSelectionBar=makeSelectionBar(downloadSelectionSummary,downloadSelectionAllButton,56,48,new String[]{"打开路径","更多打开","复制链接","分享文件","文件位置","安装","暂停继续","取消任务","删除记录","删除文件","分享链接","退出"},directory,more,copyLink,shareFiles,location,install,pause,cancel,deleteRecords,deleteFiles,shareLinks,close);showChecks(downloadChecks,true);updateDownloadSelectionSummary();}
  void toggleDownloadSelection(DownloadEntry entry){if(!downloadSelectionMode)enterDownloadSelection();if(toggleChosen(selectedDownloads,entry,downloadChecks))exitDownloadSelection();else updateDownloadSelectionSummary();}
  void toggleSelectAllDownloads(){List<DownloadEntry> visibleEntries=new ArrayList<>(downloadChecks.keySet());if(visibleEntries.isEmpty()){showNotice("当前没有可选择的下载记录",false);return;}if(allChosen(visibleEntries,selectedDownloads)){exitDownloadSelection();return;}selectedDownloads.clear();selectedDownloads.addAll(visibleEntries);syncChecks(selectedDownloads,downloadChecks);updateDownloadSelectionSummary();}
  void updateDownloadSelectionSummary(){syncBatchDownloadChecks();if(downloadSelectionSummary!=null)downloadSelectionSummary.setText("已选 "+selectedDownloads.size());if(downloadSelectionAllButton!=null)downloadSelectionAllButton.setText(allChosen(downloadChecks.keySet(),selectedDownloads)?"取消全选":"全选");}
  void exitDownloadSelection(){downloadSelectionMode=false;setSelectionActionsHidden(false);
    if(navBall!=null)navBall.setSuppressed(false);syncBatchDownloadChecks();resetSelection(selectedDownloads,downloadChecks,downloadSelectionBar);downloadSelectionBar=null;downloadSelectionSummary=null;downloadSelectionAllButton=null;}
  void shareSelectedDownloadLinks(){StringBuilder links=new StringBuilder();for(DownloadEntry entry:selectedDownloads)if(!entry.shareUrl.isEmpty()){if(links.length()>0)links.append("\n\n");links.append(entry.shareUrl);if(!entry.password.isEmpty())links.append("\n密码：").append(entry.password);}if(links.length()==0){showNotice("所选记录没有蓝奏链接",false);return;}shareText(links.toString(),"分享蓝奏云链接");}
  void copySelectedDownloadLinks(){StringBuilder links=new StringBuilder();int copied=0;for(DownloadEntry entry:selectedDownloads)if(!entry.shareUrl.isEmpty()){if(links.length()>0)links.append("\n\n");links.append(entry.shareUrl);if(!entry.password.isEmpty())links.append("\n密码：").append(entry.password);copied++;}if(copied==0){showNotice("所选记录没有蓝奏链接",false);return;}((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("蓝奏云原链接",links));showNotice("已复制 "+copied+" 条原链接",false);}
  List<DownloadEntry> selectedReadyDownloads(){List<DownloadEntry> ready=new ArrayList<>();int skipped=0;for(DownloadEntry entry:selectedDownloads){if(entryFileReadable(entry))ready.add(entry);else skipped++;}if(ready.isEmpty()){showNotice("所选记录没有可用的本地文件",false);return ready;}if(skipped>0)showNotice("已跳过 "+skipped+" 个未完成或已缺失文件",false);return ready;}
  void openSelectedWithMore(){if(selectedDownloads.size()!=1){showNotice("请选择一个文件使用更多方式打开",false);return;}openWithMore(selectedDownloads.iterator().next());}
  void shareSelectedDownloadedFiles(){List<DownloadEntry> ready=selectedReadyDownloads();if(ready.isEmpty())return;ArrayList<Uri> uris=new ArrayList<>();boolean allApk=true;for(DownloadEntry entry:ready){uris.add(externalUri(entry));if(!entry.name.toLowerCase(Locale.ROOT).endsWith(".apk"))allApk=false;}String mime=allApk?"application/vnd.android.package-archive":"application/octet-stream";Intent send=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE);send.setType(mime);if(uris.size()==1)send.putExtra(Intent.EXTRA_STREAM,uris.get(0));else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);ClipData clip=ClipData.newRawUri("本地文件",uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));send.setClipData(clip);send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(Intent.createChooser(send,"分享所选本地文件"));}catch(Exception error){showNotice("没有可用的分享应用",true);}}
  void openSelectedDownloadLocations(){if(selectedDownloads.isEmpty())return;LinkedHashMap<String,Uri> locations=new LinkedHashMap<>();for(DownloadEntry entry:selectedDownloads){Uri directory=directoryUriForEntry(entry);if(directory!=null)locations.put(directory.toString(),directory);}if(locations.isEmpty()){showNotice("无法识别所选文件所在路径",true);return;}if(locations.size()==1){openDirectoryUri(locations.values().iterator().next());return;}List<Uri> values=new ArrayList<>(locations.values());String[] labels=new String[values.size()];for(int i=0;i<values.size();i++)labels[i]=directoryDisplayPath(values.get(i));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("选择要打开的所在路径").setItems(labels,(dialog,index)->openDirectoryUri(values.get(index))).create();showRounded(prompt);}
  void confirmDeleteAllDownloadRecords(){List<DownloadEntry> chosen=new ArrayList<>(downloadEntries);if(chosen.isEmpty()){showNotice("暂无下载记录",false);return;}AlertDialog prompt=new AlertDialog.Builder(this).setTitle("全部删除记录？").setMessage("将删除全部 "+chosen.size()+" 条下载记录，本地文件会保留。进行中的任务会先取消。").setNegativeButton("取消",null).setPositiveButton("全部删除记录",(d,w)->{for(DownloadEntry entry:chosen)if(isDownloadActive(entry)||entry.state.equals(DOWNLOAD_PAUSED))cancelDownload(entry);downloadEntries.removeAll(chosen);persistDownloadHistory();renderDownloadFilters();renderDownloads(downloadQuery);showNotice("全部下载记录已删除",false);}).create();showRounded(prompt);}
  void confirmDeleteAllDownloadedFiles(){List<DownloadEntry> chosen=new ArrayList<>(downloadEntries);if(chosen.isEmpty()){showNotice("暂无下载记录",false);return;}AlertDialog prompt=new AlertDialog.Builder(this).setTitle("全部删除文件？").setMessage("将删除全部下载文件；删除成功或文件已不存在时同步清理记录，失败记录保留。").setNegativeButton("取消",null).setPositiveButton("全部删除文件",(d,w)->{List<DownloadEntry> removed=new ArrayList<>();int deleted=0,missing=0,failed=0;for(DownloadEntry entry:chosen){if(isDownloadActive(entry)||entry.state.equals(DOWNLOAD_PAUSED))cancelDownload(entry);int result=deleteDownloadedFileNow(entry);if(result<0)failed++;else{removed.add(entry);if(result==DELETE_OK)deleted++;else missing++;}}downloadEntries.removeAll(removed);persistDownloadHistory();renderDownloadFilters();renderDownloads(downloadQuery);showNotice("已删文件 "+deleted+" · 清理缺失 "+missing+(failed>0?" · 失败保留 "+failed:""),failed>0);}).create();showRounded(prompt);}
  void confirmDeleteSelectedRecords(){List<DownloadEntry> chosen=new ArrayList<>(selectedDownloads);if(chosen.isEmpty())return;AlertDialog prompt=new AlertDialog.Builder(this).setTitle("删除所选记录？").setMessage("将删除 "+chosen.size()+" 条下载记录，本地文件会保留。").setNegativeButton("取消",null).setPositiveButton("删除记录",(d,w)->{downloadEntries.removeAll(chosen);persistDownloadHistory();exitDownloadSelection();renderDownloadFilters();renderDownloads(downloadQuery);showNotice("已删除 "+chosen.size()+" 条记录",false);}).create();showRounded(prompt);}
  void confirmDeleteSelectedFiles(){List<DownloadEntry> chosen=new ArrayList<>(selectedDownloads);if(chosen.isEmpty())return;AlertDialog prompt=new AlertDialog.Builder(this).setTitle("删除所选本地文件？").setMessage("删除成功或文件已不存在时，会同时清理对应下载记录；删除失败的记录会保留。").setNegativeButton("取消",null).setPositiveButton("删除文件",(d,w)->{List<DownloadEntry> removed=new ArrayList<>();int deleted=0,missing=0,failed=0;for(DownloadEntry entry:chosen){int result=deleteDownloadedFileNow(entry);if(result<0)failed++;else{removed.add(entry);if(result==DELETE_OK)deleted++;else missing++;}}if(!removed.isEmpty()){downloadEntries.removeAll(removed);persistDownloadHistory();}exitDownloadSelection();renderDownloadFilters();renderDownloads(downloadQuery);showNotice("已删文件 "+deleted+" · 清理缺失 "+missing+(failed>0?" · 失败保留 "+failed:""),failed>0);}).create();showRounded(prompt);}
  Uri entryUri(DownloadEntry entry){if(entry.target==null&&!entry.uriString.isEmpty())entry.target=Uri.parse(entry.uriString);return entry.target;}
  Uri externalUri(DownloadEntry entry){if(entry!=null&&entry.updateVerified&&entry.verifiedUpdateUri!=null)return entry.verifiedUpdateUri;Uri uri=entryUri(entry);if(uri!=null&&"file".equals(uri.getScheme())&&uri.getPath()!=null){String token=android.util.Base64.encodeToString(new File(uri.getPath()).getAbsolutePath().getBytes(java.nio.charset.StandardCharsets.UTF_8),android.util.Base64.URL_SAFE|android.util.Base64.NO_WRAP|android.util.Base64.NO_PADDING);return new Uri.Builder().scheme("content").authority(getPackageName()+".downloads").appendPath("shared").appendPath(token).build();}return uri;}
  boolean entryFileReadable(DownloadEntry entry){if(entry==null||!entry.state.equals("已完成"))return false;Uri uri=entryUri(entry);if(uri==null)return false;if("file".equals(uri.getScheme()))return uri.getPath()!=null&&new File(uri.getPath()).isFile();if(!"content".equals(uri.getScheme()))return false;try(android.os.ParcelFileDescriptor ignored=getContentResolver().openFileDescriptor(uri,"r")){return ignored!=null;}catch(Exception error){return false;}}
  boolean readyFile(DownloadEntry entry){if(!entry.state.equals("已完成")){showNotice("该任务尚未下载完成",false);return false;}if(!entryFileReadable(entry)){showNotice("本地文件已不存在",false);return false;}return true;}
  void installEntry(DownloadEntry entry){
    if(!readyFile(entry))return;if(!entry.name.toLowerCase(Locale.ROOT).endsWith(".apk")){showNotice("该文件不是 APK，无法安装",false);return;}
    if(DownloadSourcePolicy.requiresInstallConfirmation(entry.source)&&!entry.installConfirmationGranted){showExternalInstallConfirmation(entry);return;}
    if(!entry.expectedUpdateVersion.isEmpty()&&!entry.updateVerified){verifyCloudUpdateEntry(entry);return;}
    if(DownloadSourcePolicy.allowsSilentInstall(entry.source)&&silentInstallPreference()&&adbShell.ready()){silentInstallEntries(Collections.singletonList(entry));return;}
    installEntryWithSystemInstaller(entry);
  }
  void showExternalInstallConfirmation(DownloadEntry entry){
    if(entry==null||isFinishing()||isDestroyed())return;
    String host="未知来源";try{java.net.URL url=new java.net.URL(entry.directUrl);if(url.getHost()!=null&&!url.getHost().isEmpty())host=url.getHost();}catch(Exception ignored){}
    AlertDialog prompt=new AlertDialog.Builder(this).setTitle("确认安装外部 APK？").setMessage("文件："+entry.name+"\n来源："+host+"\n\n该文件来自网页外部下载，不属于应用内置更新。请确认你信任来源并了解安装风险。确认后只打开系统安装器，不会静默安装。").setNegativeButton("取消",null).setPositiveButton("确认并打开安装器",(dialog,which)->{entry.installConfirmationGranted=true;installEntryWithSystemInstaller(entry);}).create();showRounded(prompt);
  }
  void verifyCloudUpdateEntry(DownloadEntry entry){synchronized(entry){if(entry.updateVerificationRunning)return;entry.updateVerificationRunning=true;}showNotice("正在校验更新包…",false);io.execute(()->{try{Uri verified=verifyArchiveUpdate(entryUri(entry),entry.expectedUpdateVersion);synchronized(entry){entry.verifiedUpdateUri=verified;entry.updateVerified=true;entry.updateVerificationRunning=false;}runOnUiThread(()->installEntry(entry));}catch(Exception error){synchronized(entry){entry.verifiedUpdateUri=null;entry.updateVerified=false;entry.updateVerificationRunning=false;}showNotice("更新包校验未通过："+friendlyError(error),true);}});}
  Uri verifyArchiveUpdate(Uri uri,String expectedVersion)throws Exception{if(uri==null)throw new IOException("无法读取更新包");String version=expectedVersion==null?"":expectedVersion.trim();
    /*
     * [DFW-90] 这里原来要求版本名必须严格是 `x.y.z`，否则整个更新被拒。
     * 但后台的版本名是**人手填的自由文本**（控制台没有格式校验），
     * 用户填个 `1.0.24-beta` 就会让所有用户收不到更新，而报错只有一句"版本信息无效"。
     * 去掉第 ④ 条校验之后，version 只剩一个用途：拼临时文件名 —— 所以只要非空，
     * 并且**过滤掉文件名里不能有的字符**就够了。
     */
    if(version.isEmpty())throw new IOException("更新包版本信息无效");File root=new File(getFilesDir(),"verified-updates");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("无法创建更新校验目录");long now=System.currentTimeMillis();File[] stale=root.listFiles();if(stale!=null)for(File file:stale)if(file.getName().startsWith("candidate-")||now-file.lastModified()>48L*60*60*1000)file.delete();String token=version.replaceAll("[^0-9A-Za-z._-]","_")+'-'+Long.toHexString(System.nanoTime());File temporary=new File(root,"candidate-"+token+".apk"),verified=new File(root,"verified-"+token+".apk");long size=0;try{try(InputStream input=openUriInput(uri);OutputStream output=new FileOutputStream(temporary)){byte[] buffer=new byte[32768];for(int count;(count=input.read(buffer))>0;){size+=count;if(size>512L*1024*1024)throw new IOException("更新包大小异常");output.write(buffer,0,count);}}int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;android.content.pm.PackageInfo current=getPackageManager().getPackageInfo(getPackageName(),flags),archive=getPackageManager().getPackageArchiveInfo(temporary.getAbsolutePath(),flags);if(archive==null||!getPackageName().equals(archive.packageName))throw new IOException("更新包包名不一致");if(!signatureDigests(current).equals(signatureDigests(archive)))throw new IOException("更新包签名不一致");long currentCode=Build.VERSION.SDK_INT>=28?current.getLongVersionCode():current.versionCode,archiveCode=Build.VERSION.SDK_INT>=28?archive.getLongVersionCode():archive.versionCode;/*
       * [DFW-90] **这条是安全底线，必须留。**
       * 后台填的版本号只决定"要不要提醒更新"；用户真正装上的还是这个包。
       * 如果包本身不比当前版本新，装完版本没变、后台却说有新版本 → 无限循环提示。
       * 报错改成大白话：原来那句技术黑话用户根本看不懂，也说不清该怎么办。
       */
      if(archiveCode<=currentCode)throw new IOException("这个安装包并不比当前版本新（包内 "+archive.versionName+"，当前 "+current.versionName+"），已取消安装");if(!temporary.renameTo(verified))throw new IOException("无法封存已校验更新包");return new Uri.Builder().scheme("content").authority(getPackageName()+".downloads").appendPath("verified").appendPath(verified.getName()).build();}finally{if(temporary.isFile())temporary.delete();}}
  /**
   * [DFW-131 2026-10-03] 这个下载项是不是一个**可安装的 APK**？
   *
   * 判据是**文件名后缀**，不是 `entry.autoInstall` 那个标志 ——
   * 后者在普通下载路径上被写死成 false（`requestItemDownload(item,false)`），
   * 拿它当判据的结果就是「用户下完 APK 什么都不会发生」。
   *
   * 抽成静态纯函数是为了能直接测：它决定了「下完 APK 到底弹不弹安装界面」。
   */
  static boolean isApkEntry(DownloadEntry entry){
    return entry!=null&&entry.name!=null&&entry.name.toLowerCase(Locale.ROOT).endsWith(".apk");
  }
  void autoInstallCompletedEntry(DownloadEntry entry){if(!readyFile(entry)||!entry.name.toLowerCase(Locale.ROOT).endsWith(".apk"))return;if(!DownloadSourcePolicy.allowsAutomaticInstall(entry.source)){installEntry(entry);return;}if(silentInstallPreference()&&adbShell.ready()){silentInstallEntries(Collections.singletonList(entry));return;}installEntryWithSystemInstaller(entry);}
  void installEntryWithSystemInstaller(DownloadEntry entry){
    if(!readyFile(entry))return;if(!entry.name.toLowerCase(Locale.ROOT).endsWith(".apk")){showNotice("该文件不是 APK，无法安装",false);return;}
    if(DownloadSourcePolicy.requiresInstallConfirmation(entry.source)&&!entry.installConfirmationGranted){showExternalInstallConfirmation(entry);return;}
    if(!entry.expectedUpdateVersion.isEmpty()&&!entry.updateVerified){verifyCloudUpdateEntry(entry);return;}
    if(Build.VERSION.SDK_INT>=26&&!getPackageManager().canRequestPackageInstalls()){confirmUnknownInstallPermission(entry);return;}
    try{Intent install=new Intent(Intent.ACTION_VIEW);install.setDataAndType(externalUri(entry),"application/vnd.android.package-archive");install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(install);}catch(Exception error){showNotice("无法启动安装器："+error.getMessage(),true);}
  }
  void installSelectedEntries(){List<DownloadEntry> apks=new ArrayList<>();for(DownloadEntry entry:selectedDownloads)if(entryFileReadable(entry)&&entry.name.toLowerCase(Locale.ROOT).endsWith(".apk"))apks.add(entry);if(apks.isEmpty()){showNotice("所选项目中没有可安装的 APK",false);return;}for(DownloadEntry entry:apks)if(DownloadSourcePolicy.requiresInstallConfirmation(entry.source)){showNotice("外部或来源不明的 APK 必须逐个确认后使用系统安装器："+entry.name,true);return;}if(apks.size()==1){installEntry(apks.get(0));return;}for(DownloadEntry entry:apks)if(!entry.expectedUpdateVersion.isEmpty()&&!entry.updateVerified){showNotice("更新包必须先单独校验安装："+entry.name,true);return;}if(!silentInstallPreference()||!adbShell.ready()){showNotice("批量无界面安装需要先授权 ADB Shell 并开启静默一键安装",true);showAdbPermissionDialog();return;}silentInstallEntries(apks);}
  void silentInstallEntries(List<DownloadEntry> entries){
    if(entries==null||entries.isEmpty())return;for(DownloadEntry entry:entries)if(DownloadSourcePolicy.requiresInstallConfirmation(entry.source)){showNotice("外部或来源不明的 APK 禁止 Shell 静默安装，请逐个确认后使用系统安装器："+entry.name,true);return;}for(DownloadEntry entry:entries)if(!entry.expectedUpdateVersion.isEmpty()&&!entry.updateVerified){showNotice("更新包尚未通过安全校验："+entry.name,true);return;}boolean single=entries.size()==1;if(!adbShell.ready()){if(single){installEntryWithSystemInstaller(entries.get(0));return;}showNotice("批量无界面安装需要已连接的 ADB Shell",true);showAdbPermissionDialog();return;}
        int configured=installParallelism(),adaptiveLimit=Math.max(1,LanzouCore.adaptiveNetworkWorkers(entries.size())),effective=configured==0?adaptiveLimit:Math.max(1,Math.min(configured,adaptiveLimit));ExecutorService installers=Executors.newFixedThreadPool(effective);java.util.concurrent.atomic.AtomicInteger remaining=new java.util.concurrent.atomic.AtomicInteger(entries.size()),completed=new java.util.concurrent.atomic.AtomicInteger(),succeeded=new java.util.concurrent.atomic.AtomicInteger(),failed=new java.util.concurrent.atomic.AtomicInteger();ConcurrentLinkedQueue<String> errors=new ConcurrentLinkedQueue<>();showNotice("Shell 安装进度 0/"+entries.size()+" · "+(configured==0?"自动适配 "+effective+" 线程":effective+" 线程"),false);
    for(DownloadEntry entry:entries)installers.execute(()->{AdbShellManager.InstallResult result=adbShell.install(getContentResolver(),externalUri(entry),entry.totalBytes>0?entry.totalBytes:entry.verifiedTotalBytes);if(!result.success&&single&&!adbShell.ready()){completed.incrementAndGet();remaining.decrementAndGet();installers.shutdown();runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){showNotice("Shell 安装已中断，改用系统安装器",false);installEntryWithSystemInstaller(entry);}});return;}if(result.success)succeeded.incrementAndGet();else{failed.incrementAndGet();errors.add(entry.name+"："+firstNonEmpty(result.message,"安装失败"));}int done=completed.incrementAndGet(),left=remaining.decrementAndGet();if(left>0)showNotice("Shell 安装进度 "+done+"/"+entries.size()+" · 成功 "+succeeded.get()+" · 失败 "+failed.get(),false);else{installers.shutdown();runOnUiThread(()->{if(isFinishing()||isDestroyed())return;int ok=succeeded.get(),bad=failed.get();showNotice("Shell 安装进度 "+done+"/"+entries.size()+" · 成功 "+ok+" · 失败 "+bad,bad>0);if(bad==0){if(downloadSelectionMode)exitDownloadSelection();}else showSilentInstallReport(ok,bad,errors);});}});
  }
  void showSilentInstallReport(int succeeded,int failed,Collection<String> errors){StringBuilder message=new StringBuilder("成功 ").append(succeeded).append(" · 失败 ").append(failed);int shown=0;for(String error:errors){if(shown++>=5){message.append("\n…其余失败项已省略");break;}message.append("\n").append(error);}AlertDialog report=new AlertDialog.Builder(this).setTitle("静默安装未全部完成").setMessage(message.toString()).setNegativeButton("关闭",null).setPositiveButton("检查权限",(dialog,which)->showAdbPermissionDialog()).create();showRounded(report);}
  @android.annotation.SuppressLint("InlinedApi") void confirmUnknownInstallPermission(DownloadEntry entry){AlertDialog prompt=new AlertDialog.Builder(this).setTitle("允许安装此应用？").setMessage("Android 需要先允许「"+PRODUCT_NAME+"」安装未知来源应用。授权后会自动继续打开安装器。").setNegativeButton("取消",null).setPositiveButton("前往授权",(d,w)->{try{pendingInstallEntry=entry;startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+getPackageName())));}catch(Exception error){pendingInstallEntry=null;showNotice("无法打开安装权限设置",true);}}).create();showRounded(prompt);}
  void downloadMenu(DownloadEntry entry){String[] actions={"跳转 Download 目录","安装","删除记录","删除文件","分享文件","分享蓝奏云链接","更多方式"};AlertDialog menu=new AlertDialog.Builder(this).setTitle(entry.name).setItems(actions,(dialog,index)->{switch(index){case 0:openDownloadDirectory();break;case 1:installEntry(entry);break;case 2:removeDownloadRecord(entry);break;case 3:confirmDeleteFile(entry);break;case 4:shareDownloadedFile(entry);break;case 5:shareDownloadLink(entry);break;case 6:openWithMore(entry);break;default:break;}}).create();showRounded(menu);}
  String downloadMimeType(DownloadEntry entry){Uri uri=entryUri(entry);if(uri!=null&&"content".equals(uri.getScheme()))try{String value=getContentResolver().getType(uri);if(value!=null&&!value.trim().isEmpty())return value;}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}return entry.name.toLowerCase(Locale.ROOT).endsWith(".apk")?"application/vnd.android.package-archive":"application/octet-stream";}
  void openWithMore(DownloadEntry entry){if(!readyFile(entry))return;if(!entry.expectedUpdateVersion.isEmpty()){installEntry(entry);return;}if(entry.name.toLowerCase(Locale.ROOT).endsWith(".apk")&&DownloadSourcePolicy.requiresInstallConfirmation(entry.source)){installEntry(entry);return;}try{Uri uri=externalUri(entry);Intent view=new Intent(Intent.ACTION_VIEW);view.setDataAndType(uri,downloadMimeType(entry));view.setClipData(ClipData.newRawUri(entry.name,uri));view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(view,"更多方式打开"));}catch(Exception error){showNotice("没有更多可用方式",true);}}
  String legacyDownloadTreePath(String raw){if(raw==null||raw.trim().isEmpty())return "";try{Uri uri=Uri.parse(raw);String id=android.provider.DocumentsContract.getTreeDocumentId(uri);if(id.startsWith("raw:"))return id.substring(4);int split=id.indexOf(':');String volume=split<0?id:id.substring(0,split),relative=split<0?"":id.substring(split+1);String root=volume.equalsIgnoreCase("primary")?Environment.getExternalStorageDirectory().getAbsolutePath():volume.equalsIgnoreCase("home")?new File(Environment.getExternalStorageDirectory(),"Documents").getAbsolutePath():"/storage/"+volume;return relative.isEmpty()?root:new File(root,relative).getPath();}catch(Exception ignored){return "";}}
  /** v1.4.2：默认下载目录 = Download/东方无限（用户指定：手机上出现可见的「东方无限」文件夹，所有下载都放里面；createDownloadTarget 会自动 mkdirs） */
  File defaultDownloadDirectory(){return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),"东方无限");}
  /** 授权成功后立刻建好默认下载目录（失败静默，首次下载时 createDownloadTarget 会重试） */
  void ensureDefaultDownloadFolder(){try{File d=defaultDownloadDirectory();if(!d.isDirectory())d.mkdirs();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}ensureCrashFolder();}
  /**
   * 下载完成/拿到权限后就把 `Download/东方无限/崩溃日志/` 建好（v1.22.10）。
   * 用户 2026-09-29 反馈"在 MT 管理器里都没找到那个文件夹"——空目录也要先出现，用户才知道东西会往哪儿放。
   */
  /**
   * 建好公共崩溃目录 `Download/东方无限/崩溃日志/` 并回读确认（v1.22.11）。
   * 用户反馈"授予权限后 MT 管理器里并没有看到东方无限文件夹"——旧实现只调 `mkdirs()` 不看结果、
   * 失败也静默吞掉，于是"没建成功"和"建成功了"在界面上完全一样，用户无从判断。现在返回真实结果。
   * 另外放一个空 `crash.log`：部分文件管理器不显示空目录，占位文件能让目录立刻可见。
   */
  boolean ensureCrashFolder(){
    try{
      java.io.File folder=App.publicCrashFolder();
      if(folder==null)return false;
      if(!folder.isDirectory()&&!folder.mkdirs()&&!folder.isDirectory())return false;
      java.io.File log=new java.io.File(folder,"crash.log");
      if(!log.exists())log.createNewFile();
      boolean ready=folder.isDirectory();
      if(ready)crashFolderEnsured=true;
      return ready;
    }catch(Exception error){
      android.util.Log.w("MainActivity","ensureCrashFolder failed: "+error.getMessage(),error);
      return false;
    }
  }
  File selectedDownloadDirectory(){android.content.SharedPreferences p=getSharedPreferences("download_destination",MODE_PRIVATE);String raw=p.getString("path","");if(raw==null)raw="";if(raw.trim().isEmpty()){String legacy=p.getString("tree","");String migrated=legacyDownloadTreePath(legacy);android.content.SharedPreferences.Editor edit=p.edit().remove("tree");if(!migrated.isEmpty()){raw=migrated;edit.putString("path",migrated);}edit.apply();}File directory=raw.trim().isEmpty()?defaultDownloadDirectory():new File(raw.trim());try{return directory.getCanonicalFile();}catch(Exception ignored){return directory.getAbsoluteFile();}}
  /**
   * 是否已拿到"管理所有文件"级别权限。v1.22.10：整段包 try/catch —— JVM 单测（Robolectric）里
   * `Environment.isExternalStorageManager()` 会直接抛 ArrayIndexOutOfBounds，启动路径一旦调用就会带崩全部界面测试。
   * 权限查询失败一律当作"没授权"处理，宁可少做一次静默建目录，也不能让应用起不来。
   */
  boolean storageAccessGranted(){try{if(Build.VERSION.SDK_INT>=30)return Environment.isExternalStorageManager();return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;}catch(Throwable error){return false;}}
  void runPendingStorageAccessActions(){ArrayList<Runnable> actions=new ArrayList<>();while(!pendingStorageAccessActions.isEmpty())actions.add(pendingStorageAccessActions.removeFirst());for(Runnable action:actions)ui.post(action);}
  void requestManageAllFilesAccess(String detail,Runnable action,boolean startup){runOnUiThread(()->{if(storageAccessGranted()){ensureDefaultDownloadFolder();if(action!=null)ui.post(action);if(startup)ui.post(this::maybeRequestBatteryExemption);return;}if(action!=null)pendingStorageAccessActions.addLast(action);if(Build.VERSION.SDK_INT<30){if(startup)requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},STARTUP_STORAGE_PERMISSION);else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,Manifest.permission.WRITE_EXTERNAL_STORAGE},DIRECT_STORAGE_PERMISSION);return;}if(storageAccessPromptShowing)return;storageAccessPromptShowing=true;String reason=detail==null||detail.trim().isEmpty()?"下载、更新、删除本地文件和自定义保存路径需要管理所有文件权限。":detail.trim();AlertDialog prompt=new AlertDialog.Builder(this).setTitle("允许管理所有文件？").setMessage(reason+"\n\n授权后应用会直接使用真实文件路径，不使用 SAF 目录授权。若暂不授权，下次启动仍会再次询问。").setNegativeButton("暂不",(d,w)->{storageAccessPromptShowing=false;pendingStorageAccessActions.clear();if(startup)ui.post(this::maybeRequestBatteryExemption);}).setPositiveButton("前往授权",(d,w)->{storageAccessPromptShowing=false;manageAllFilesSettingsPending=true;manageAllFilesStartupFlow=startup;try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+getPackageName())));}catch(Exception first){try{startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}catch(Exception second){manageAllFilesSettingsPending=false;manageAllFilesStartupFlow=false;pendingStorageAccessActions.clear();showNotice("无法打开管理所有文件权限设置",true);if(startup)ui.post(this::maybeRequestBatteryExemption);}}}).create();prompt.setCanceledOnTouchOutside(false);prompt.setOnCancelListener(d->{storageAccessPromptShowing=false;pendingStorageAccessActions.clear();if(startup)ui.post(this::maybeRequestBatteryExemption);});showRounded(prompt);});}
  /** 首启权限引导是否已经问过（只问一次，之后不再打扰）。 */
  boolean askedStartupPermissions;

  /**
   * [DFW-97] **首启只问两件事：存储 + 通知。** 其余（录音/相机/电池优化）用到再问。
   *
   * 用户 2026-10-02：「一开软件就要『存储权限』和『通知权限』，其他的按照需要打开。」
   *
   * 顺序有讲究 —— **先存储后通知**：
   * 存储是这个 App 的命脉（没它下载直接失败），用户如果只肯给一个，应该是它；
   * 通知只是"看得见进度"，晚一步问，即使用户拒绝也不影响核心功能。
   *
   * 存储走的是现成的 `requestManageAllFilesAccess(..., startup=true)`：
   * Android 11+ 要跳系统设置页给「管理所有文件」，11 以下才是普通运行时权限。
   */
  void requestStartupPermissions(){
    if(isFinishing()||isDestroyed())return;
    if(!storageAccessGranted()){
      requestManageAllFilesAccess(
          "下载软件需要把文件保存到「下载/东方无限」。授权后下载才能正常开始。",
          this::requestStartupNotificationPermission,true);
      return;
    }
    requestStartupNotificationPermission();
  }

  /**
   * 通知权限（Android 13+ 才是运行时权限，低版本系统直接跳过）。
   *
   * 为什么值得单独问：下载进度、公告、更新提醒都靠通知；
   * 用户拒绝了也不影响下载本身，只是"看不到进度"，所以**拒绝不阻断任何流程**。
   */
  void requestStartupNotificationPermission(){
    if(Build.VERSION.SDK_INT<33)return;
    if(checkSelfPermission("android.permission.POST_NOTIFICATIONS")==PackageManager.PERMISSION_GRANTED)return;
    try{
      requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},STARTUP_NOTIFICATION_PERMISSION);
    }catch(Exception ignored){
      // 极少数定制系统会在这里抛，权限只是"看不到进度"，不该影响启动
    }
  }

  boolean ensureDirectStorageAuthorized(Runnable retry){if(storageAccessGranted())return true;requestManageAllFilesAccess("当前操作需要读取和写入下载目录。",retry,false);return false;}
  void requestDownloadStorageRecovery(DownloadEntry entry,String detail,Runnable action){requestManageAllFilesAccess(detail,action,false);}
  boolean downloadStorageFailure(String message){String value=message==null?"":message.toLowerCase(Locale.ROOT);return value.contains("permission")||value.contains("eacces")||value.contains("operation not permitted")||value.contains("无法写入")||value.contains("不可写")||value.contains("保存路径")||value.contains("存储权限")||value.contains("管理所有文件")||value.contains("read-only file system");}
  File normalizeDownloadDirectory(String raw)throws IOException{String value=raw==null?"":raw.trim();File directory=value.isEmpty()?defaultDownloadDirectory():new File(value);if(!directory.isAbsolute())throw new IOException("请输入绝对路径，例如 /storage/emulated/0/Download/东方无限");File canonical=directory.getCanonicalFile();String path=canonical.getPath().replace('\\','/'),lower=path.toLowerCase(Locale.ROOT);if(!(path.startsWith("/storage/")||path.equals("/sdcard")||path.startsWith("/sdcard/")))throw new IOException("保存路径必须位于共享存储 /storage 下");if(lower.matches(".*/android/(?:data|obb)(?:/.*)?"))throw new IOException("Android/data 与 Android/obb 不可作为下载目录");if(!canonical.isDirectory()&&!canonical.mkdirs())throw new IOException("无法创建该目录");if(!canonical.isDirectory()||!canonical.canWrite())throw new IOException("该目录不可写");File probe=File.createTempFile(".lanzouplus-write-",".tmp",canonical);if(!probe.delete())probe.deleteOnExit();return canonical;}
  void persistDownloadDirectory(File directory){getSharedPreferences("download_destination",MODE_PRIVATE).edit().remove("tree").putString("path",directory.getAbsolutePath()).apply();String path=directory.getAbsolutePath();if(downloadPathText!=null){downloadPathText.setText(path);View parent=(View)downloadPathText.getParent();if(parent!=null)parent.setContentDescription("保存路径 "+path+"，点击查看，长按重新选择");}if(settingsDownloadPathText!=null){settingsDownloadPathText.setText(path);View parent=(View)settingsDownloadPathText.getParent();if(parent!=null)parent.setContentDescription("更改下载保存路径，当前 "+path);}}
  void chooseDownloadDirectory(){if(!ensureDirectStorageAuthorized(this::chooseDownloadDirectory))return;EditText input=sourceInput("/storage/emulated/0/Download/东方无限",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setText(selectedDownloadDirectory().getAbsolutePath());input.setSelection(input.length());LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(22),dp(8),dp(22),0);panel.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));TextView hint=text("使用真实文件路径；Android 11+ 由“管理所有文件”权限提供读写能力。",12,MUTED);panel.addView(hint,new LinearLayout.LayoutParams(-1,dp(42)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("下载保存路径").setView(panel).setNegativeButton("取消",null).setNeutralButton("恢复默认",null).setPositiveButton("保存",null).create();prompt.setOnShowListener(v->{prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{try{File directory=normalizeDownloadDirectory(input.getText().toString());persistDownloadDirectory(directory);prompt.dismiss();showNotice("保存路径已更新",false);}catch(Exception error){input.setError(friendlyError(error));}});prompt.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x->{try{File directory=normalizeDownloadDirectory(defaultDownloadDirectory().getAbsolutePath());persistDownloadDirectory(directory);prompt.dismiss();showNotice("已恢复默认下载目录（Download/东方无限）",false);}catch(Exception error){input.setError(friendlyError(error));}});});showRounded(prompt);}
  Uri currentDownloadParentUri(){return Uri.fromFile(selectedDownloadDirectory());}
  Uri currentDownloadParentReferenceUri(){return currentDownloadParentUri();}
  File legacyDocumentFile(Uri uri){if(uri==null)return null;if("file".equals(uri.getScheme())&&uri.getPath()!=null)return new File(uri.getPath());if(!"content".equals(uri.getScheme()))return null;try{String id=android.provider.DocumentsContract.getDocumentId(uri);if(id.startsWith("raw:"))return new File(id.substring(4));int split=id.indexOf(':');String volume=split<0?id:id.substring(0,split),relative=split<0?"":id.substring(split+1);String root=volume.equalsIgnoreCase("primary")?Environment.getExternalStorageDirectory().getAbsolutePath():volume.equalsIgnoreCase("home")?new File(Environment.getExternalStorageDirectory(),"Documents").getAbsolutePath():"/storage/"+volume;return relative.isEmpty()?new File(root):new File(root,relative);}catch(Exception ignored){return null;}}
  File directoryFileForEntry(DownloadEntry entry){if(entry!=null&&!entry.parentUriString.isEmpty())try{File parent=legacyDocumentFile(Uri.parse(entry.parentUriString));if(parent!=null)return parent;}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}Uri uri=entryUri(entry);File file=legacyDocumentFile(uri);if(file!=null){File parent=file.isDirectory()?file:file.getParentFile();if(parent!=null)return parent;}return selectedDownloadDirectory();}
  Uri directoryUriForEntry(DownloadEntry entry){return Uri.fromFile(directoryFileForEntry(entry));}
  String directoryDisplayPath(Uri target){File file=legacyDocumentFile(target);return file==null?selectedDownloadDirectory().getAbsolutePath():file.getAbsolutePath();}
  String downloadDisplayPath(){return selectedDownloadDirectory().getAbsolutePath();}
  void openDirectoryUri(Uri target){File directory=legacyDocumentFile(target);if(directory==null)directory=selectedDownloadDirectory();File defaultDir=defaultDownloadDirectory();try{if(directory.getCanonicalFile().equals(defaultDir.getCanonicalFile())){startActivity(new Intent(android.app.DownloadManager.ACTION_VIEW_DOWNLOADS));return;}}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}String path=directory.getAbsolutePath();((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("下载保存路径",path));showNotice("自定义保存路径已复制："+path,false);try{Intent files=new Intent(Intent.ACTION_MAIN);files.addCategory(Intent.CATEGORY_APP_FILES);startActivity(files);}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}}
  void openDownloadDirectory(){openDirectoryUri(Uri.fromFile(selectedDownloadDirectory()));}  void removeDownloadRecord(DownloadEntry entry){if(entry==null)return;AlertDialog prompt=new AlertDialog.Builder(this).setTitle("删除下载记录？").setMessage(entry.name+"\n\n只删除这条记录，本地文件会保留。").setNegativeButton("取消",null).setPositiveButton("删除记录",(dialog,which)->deleteDownloadRecordNow(entry)).create();showRounded(prompt);}
  void deleteDownloadRecordNow(DownloadEntry entry){downloadEntries.remove(entry);persistDownloadHistory();if(downloadsPage){renderDownloadFilters();renderDownloads(downloadQuery);}showNotice("已删除下载记录，文件保留",false);}
  void confirmDeleteFile(DownloadEntry entry){AlertDialog prompt=new AlertDialog.Builder(this).setTitle("删除本地文件？").setMessage(entry.name+"\n\n删除成功或文件已不存在时，会同时清理这条下载记录。").setNegativeButton("取消",null).setPositiveButton("删除",(dialog,which)->deleteDownloadedFile(entry)).create();showRounded(prompt);}
  int deleteDownloadedFileNow(DownloadEntry entry){Uri uri=entryUri(entry);if(uri==null)return DELETE_MISSING;if(Build.VERSION.SDK_INT>=30&&!Environment.isExternalStorageManager())return DELETE_PERMISSION;try{File direct=legacyDocumentFile(uri);if(direct!=null){if(!direct.exists())return DELETE_MISSING;if(!direct.delete())return DELETE_FAILED;}else if("content".equals(uri.getScheme())){if(getContentResolver().delete(uri,null,null)<=0)try(android.os.ParcelFileDescriptor remaining=getContentResolver().openFileDescriptor(uri,"r")){return remaining==null?DELETE_MISSING:DELETE_FAILED;}catch(FileNotFoundException missing){return DELETE_MISSING;}}else return DELETE_FAILED;entry.target=null;entry.uriString="";return DELETE_OK;}catch(SecurityException denied){return DELETE_PERMISSION;}catch(FileNotFoundException missing){return DELETE_MISSING;}catch(Exception error){return DELETE_FAILED;}}
  void deleteDownloadedFile(DownloadEntry entry){int result=deleteDownloadedFileNow(entry);if(result==DELETE_PERMISSION){requestDownloadStorageRecovery(entry,"当前自定义目录没有可用的删除权限。",()->deleteDownloadedFile(entry));return;}if(result==DELETE_FAILED){showNotice("文件删除失败，下载记录已保留",true);return;}downloadEntries.remove(entry);persistDownloadHistory();if(downloadsPage){renderDownloadFilters();renderDownloads(downloadQuery);}showNotice(result==DELETE_OK?"本地文件与下载记录已删除":"文件已不存在，下载记录已清理",false);}
  void shareDownloadedFile(DownloadEntry entry){if(!readyFile(entry))return;try{Uri uri=externalUri(entry);Intent send=new Intent(Intent.ACTION_SEND);send.setType(downloadMimeType(entry));send.putExtra(Intent.EXTRA_STREAM,uri);send.setClipData(ClipData.newRawUri(entry.name,uri));send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(send,"分享 "+entry.name));}catch(Exception error){showNotice("无法分享本地文件",true);}}
  void shareDownloadLink(DownloadEntry entry){if(entry.shareUrl.isEmpty()){showNotice("该记录没有蓝奏云链接",false);return;}shareText(entry.shareUrl+(entry.password.isEmpty()?"":"\n密码："+entry.password),"分享蓝奏云链接");}

  void confirmDownload(Models.Item item){
    directResolver.prewarm(firstNonEmpty(item.shareUrl,item.url));
    LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setGravity(Gravity.START);panel.setPadding(dp(22),dp(18),dp(22),dp(8));
    TextView heading=text("是否确认下载？",22,TEXT);heading.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);panel.addView(heading,new LinearLayout.LayoutParams(-1,dp(48)));
    LinearLayout info=new LinearLayout(this);info.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.CENTER_CROP);icon.setBackground(solidShape(BG,12));icon.setClipToOutline(true);icon.setImageResource(R.drawable.ic_file);info.addView(icon,new LinearLayout.LayoutParams(dp(58),dp(58)));loadImage(item.iconUrl,icon);
    LinearLayout meta=new LinearLayout(this);meta.setOrientation(LinearLayout.VERTICAL);meta.setPadding(dp(14),0,0,0);TextView name=text(item.title,15,TEXT);name.setMaxLines(2);name.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);selectableLinks(name);meta.addView(name,new LinearLayout.LayoutParams(-1,dp(44)));
    TextView itemMeta=text((item.size.isEmpty()?"大小未知":item.size)+" · "+(item.time.isEmpty()?"日期未知":item.time),12,MUTED);itemMeta.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);meta.addView(itemMeta,new LinearLayout.LayoutParams(-1,dp(24)));
    String sourceName=item.source.trim();if(sourceName.isEmpty())sourceName="蓝奏云分享";boolean sourceLink=!item.sourceId.trim().isEmpty();TextView provenance=text("来源："+sourceName,11,sourceLink?PRIMARY:MUTED);provenance.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);provenance.setMaxLines(2);provenance.setEllipsize(android.text.TextUtils.TruncateAt.END);provenance.setTextIsSelectable(!sourceLink);provenance.setContentDescription(sourceLink?"打开来源 "+sourceName:"来源 "+sourceName);if(sourceLink){provenance.setClickable(true);provenance.setFocusable(true);provenance.setTypeface(AppFonts.bold(this));provenance.setPaintFlags(provenance.getPaintFlags()|Paint.UNDERLINE_TEXT_FLAG);provenance.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));}meta.addView(provenance,new LinearLayout.LayoutParams(-1,dp(24)));
    info.addView(meta,new LinearLayout.LayoutParams(0,-2,1));panel.addView(info,new LinearLayout.LayoutParams(-1,dp(92)));
    // v1.19.6：简介缺失时先亮出文件类型与架构（安卓机器人图标=APK 自带，改不了，但类型要一眼看懂）
    String known=item.description.trim();String kindLine="类型："+fileKindLabel(item.title)+fileArchLabel(item.title)+(item.title.toLowerCase(Locale.ROOT).matches(".*\\.(zip|rar|7z)")?"，不是应用，下载后需解压":"");TextView description=text(known.isEmpty()?kindLine:"简介："+known,12,MUTED);description.setGravity(Gravity.START);description.setMaxLines(4);description.setEllipsize(android.text.TextUtils.TruncateAt.END);description.setPadding(0,dp(5),0,dp(5));selectableLinks(description);panel.addView(description,new LinearLayout.LayoutParams(-1,-2));
    String original=preferredLanzouUrl(item.shareUrl.isEmpty()?item.url:item.shareUrl);TextView originalLink=text("原链接："+original,12,PRIMARY);originalLink.setMaxLines(2);originalLink.setPadding(0,dp(5),0,dp(3));originalLink.setTextIsSelectable(true);originalLink.setContentDescription("打开原蓝奏云链接"+(item.password.isEmpty()?"":"，先复制密码"));originalLink.setOnClickListener(v->openRecognizedLink(original,item.password));panel.addView(originalLink,new LinearLayout.LayoutParams(-1,-2));
    AlertDialog prompt=new AlertDialog.Builder(this).setView(panel).setNegativeButton("取消",null).setPositiveButton("下载",(dialog,which)->requestItemDownload(item,false)).create();if(sourceLink)provenance.setOnClickListener(v->openSearchResultSource(item,prompt,provenance));showRounded(prompt);
    if(known.isEmpty())io.execute(()->{try{String value=core.fileDescription(original).trim();runOnUiThread(()->{if(prompt.isShowing())setSelectableLinks(description,value.isEmpty()?kindLine:"简介："+value);});}catch(Exception ignored){runOnUiThread(()->{if(prompt.isShowing())setSelectableLinks(description,kindLine);});}});
  }
  void openSearchResultSource(Models.Item item,AlertDialog prompt,TextView link){String id=item.sourceId.trim();if(id.isEmpty()){showNotice("该结果没有可打开的来源",false);return;}link.setEnabled(false);io.execute(()->{try{Models.Source source=core.sourceById(id);runOnUiThread(()->{if(source==null){if(prompt.isShowing())link.setEnabled(true);showNotice("来源已不存在",true);return;}prompt.dismiss();openSearchSourcePage(source);});}catch(Exception error){runOnUiThread(()->{if(prompt.isShowing())link.setEnabled(true);showNotice("无法打开来源："+friendlyError(error),true);});}});}
  FrameLayout draggableFolderList(FolderPullScrollView scroll,View body,String description){FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(SURFACE);/* [DFW-54] 指示器：从"全宽 64dp 的 SURFACE 通栏 + 系统 ProgressBar"改成
       居中的 48dp 胶囊 + 自绘加载环。两个观感问题的根因：
       ① 通栏是 SURFACE、列表是 BG，露出时必然出现一条颜色不同的横带（"断层"）；
       ② 系统 ProgressBar 的颜色/粗细/节奏都不受我们控制，而且 determinate↔indeterminate
          切换会重建 drawable，松手瞬间形态突变。
       胶囊 + 自绘环后，指示器和 NoticeBanner / 下载卡是同一套"浮起的圆角面"语言。 */
/* [DFW-54] 指示器：**和列表同底色、无边框、无阴影、无圆角**，就一行"环 + 字"。
       两条踩过的坑写在这里，别再犯：
       ① 上一版给它加了 `setElevation`。FrameLayout 里 elevation 会改**绘制层级**，
          结果它被顶到 ScrollView **上面**，永远浮在列表上盖住内容 —— 指示器必须留在列表**下面**，
          靠列表位移把它"让"出来，绝不能靠层级浮上去；
       ② 上一版是 SURFACE2 胶囊 + 描边。露出来时和列表底色不同，就是一条突兀的横带。
          现在底色用 BG（= 列表底色），露出来只是"列表里多了一行"，没有色带。 */
folderPullIndicator=new LinearLayout(this);folderPullIndicator.setGravity(Gravity.CENTER);folderPullIndicator.setBackgroundColor(BG);folderPullIndicator.setAlpha(0f);folderMoreSpinner=new DfwxLoadingRing(this,PRIMARY,2f,motionEnabled());folderMoreSpinner.setRingProgress(0f);folderPullIndicator.addView(folderMoreSpinner,new LinearLayout.LayoutParams(dp(18),dp(18)));folderPullLabel=text("上拉并松手加载下一页",12,MUTED);folderPullLabel.setGravity(Gravity.CENTER_VERTICAL);folderPullLabel.setPadding(dp(9),0,0,0);folderPullLabel.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);folderPullIndicator.addView(folderPullLabel,new LinearLayout.LayoutParams(-2,dp(56)));FrameLayout.LayoutParams indicator=new FrameLayout.LayoutParams(-1,dp(56),Gravity.BOTTOM);frame.addView(folderPullIndicator,indicator);frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));SearchDragBar bar=new SearchDragBar(scroll,body,description);FrameLayout.LayoutParams drag=new FrameLayout.LayoutParams(dp(24),-1,Gravity.END);frame.addView(bar,drag);searchDragBar=bar;Runnable update=()->{boolean scrollable=body.getHeight()>Math.round(scroll.getHeight()*1.15f)+dp(2);bar.setVisibility(scrollable?View.VISIBLE:View.GONE);if(scrollable)bar.bumpActivity();bar.invalidate();};// v1.6.1（R-B3）：内容不足 1.15 屏强制隐藏，防边缘闪烁
scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->bar.bumpActivity());body.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->update.run());frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->update.run());resetFolderPullIndicator();return frame;}
  FrameLayout draggableList(ScrollView scroll,View body,String description){FrameLayout frame=new FrameLayout(this);frame.addView(scroll,new FrameLayout.LayoutParams(-1,-1));SearchDragBar bar=new SearchDragBar(scroll,body,description);FrameLayout.LayoutParams drag=new FrameLayout.LayoutParams(dp(24),-1,Gravity.END);frame.addView(bar,drag);searchDragBar=bar;Runnable update=()->{boolean scrollable=body.getHeight()>Math.round(scroll.getHeight()*1.15f)+dp(2);bar.setVisibility(scrollable?View.VISIBLE:View.GONE);if(scrollable)bar.bumpActivity();bar.invalidate();};// v1.6.1（R-B3）：内容不足 1.15 屏强制隐藏，防边缘闪烁
scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{bar.bumpActivity();maybeLoadMoreSources();if(y+scroll.getHeight()+dp(240)>=body.getHeight())maybeAppendSearchWindow();});body.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->update.run());frame.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->update.run());return frame;}

  int webUrlEnd(CharSequence text,int start,int end){return LinkPolicy.webUrlEnd(text,start,end);}
  void selectableLinks(TextView view){CharSequence raw=view.getText();android.text.SpannableString linked=new android.text.SpannableString(raw);java.util.regex.Matcher matches=WEB_URL_CJK.matcher(raw);while(matches.find()){int start=matches.start(),end=webUrlEnd(raw,start,matches.end());if(end<=start)continue;String url=raw.subSequence(start,end).toString();linked.setSpan(new android.text.style.ClickableSpan(){@Override public void onClick(View widget){openRecognizedLink(url,"");}@Override public void updateDrawState(android.text.TextPaint paint){paint.setColor(PRIMARY);paint.setUnderlineText(true);}},start,end,android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}view.setText(linked);view.setTextIsSelectable(true);view.setMovementMethod(LinkMovementMethod.getInstance());view.setLinksClickable(true);}
  void setSelectableLinks(TextView view,String value){view.setText(value);selectableLinks(view);}
  String normalizedWebUrl(String raw){return LinkPolicy.normalizedWebUrl(raw);}
  boolean isProductReleaseHost(String host){return LinkPolicy.isProductReleaseHost(host);}
  boolean isRealLanzouHost(String host){return LinkPolicy.isRealLanzouHost(host);}
  boolean isLanzouUrl(String value){return LinkPolicy.isLanzouUrl(value);}
        String normalizeLanzouBaseOrigin(String raw){String input=raw==null?"":raw.trim();if(input.matches("(?i)^[a-z0-9-]{2,24}$"))input=input+".lanzout.com";String value=normalizedWebUrl(input);try{Uri uri=Uri.parse(value);String scheme=uri.getScheme(),host=uri.getHost();if(host==null||scheme==null||!(scheme.equalsIgnoreCase("http")||scheme.equalsIgnoreCase("https"))||!isRealLanzouHost(host))return "https://oreojiang.lanzout.com";return scheme.toLowerCase(Locale.ROOT)+"://"+host.toLowerCase(Locale.ROOT)+(uri.getPort()>0?":"+uri.getPort():"");}catch(Exception ignored){return "https://oreojiang.lanzout.com";}}
  String preferredLanzouUrl(String raw){return LinkPolicy.preferredLanzouUrl(raw);}
  String lanzouBaseOriginLabel(){try{String host=Uri.parse(sessionLanzouBaseOrigin).getHost();return host==null?sessionLanzouBaseOrigin:host.replaceFirst("^www\\.","");}catch(Exception ignored){return sessionLanzouBaseOrigin;}}
  String baseOriginDialogLabel(String origin,int index,String status){String host=Uri.parse(origin).getHost();StringBuilder label=new StringBuilder(host==null?origin:host);if(index==0)label.append("（默认）");if(origin.equalsIgnoreCase(sessionLanzouBaseOrigin))label.append("（当前）");if(status!=null&&!status.isEmpty())label.append(" · ").append(status);return label.toString();}
  void showLanzouBaseOriginDialog(){showLanzouBaseOriginDialog(null);}
  void showLanzouBaseOriginDialog(String referenceUrl){String[] origins=LanzouCore.baseOrigins(),labels=new String[origins.length+1];for(int i=0;i<origins.length;i++)labels[i]=baseOriginDialogLabel(origins[i],i,"测试中…");labels[origins.length]="自定义基础链接…";android.widget.ArrayAdapter<String> adapter=new android.widget.ArrayAdapter<>(this,android.R.layout.simple_list_item_single_choice,labels);AlertDialog prompt=new AlertDialog.Builder(this).setTitle("基础链接域名 · 自动测速").setSingleChoiceItems(adapter,-1,(dialog,which)->{dialog.dismiss();if(which>=origins.length){showCustomLanzouBaseOriginDialog();return;}sessionLanzouBaseOrigin=origins[which];persistSearchSettings();showNotice("列表基础链接切换为 "+lanzouBaseOriginLabel(),false);if(pageKind==4)refreshSettingsInPlace();}).setNegativeButton("取消",null).create();showRounded(prompt);String reference=firstNonEmpty(referenceUrl,activeSource==null?"":activeSource.url);reference=firstNonEmpty(reference,home.url);final String probeReference=reference;Future<?>[] probeJob={null};probeJob[0]=io.submit(()->{try{core.probeBaseOrigins(probeReference,(result,done,total)->runOnUiThread(()->{if(!prompt.isShowing())return;for(int i=0;i<origins.length;i++)if(origins[i].equals(result.origin)){String state=result.available?"可用":result.challenge?"UA 验证":"不可用";labels[i]=baseOriginDialogLabel(origins[i],i,state+" · "+result.latencyMillis+" ms");adapter.notifyDataSetChanged();break;}}));}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});prompt.setOnDismissListener(ignored->{Future<?> job=probeJob[0];if(job!=null&&!job.isDone())job.cancel(true);});}
void showCustomLanzouBaseOriginDialog(){EditText input=sourceInput("输入 oreojiang 或 https://oreojiang.lanzout.com",android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setText(sessionLanzouBaseOrigin);input.setSelection(input.length());LinearLayout panel=new LinearLayout(this);panel.setPadding(dp(22),dp(8),dp(22),0);panel.addView(input,new LinearLayout.LayoutParams(-1,dp(56)));AlertDialog prompt=new AlertDialog.Builder(this).setTitle("自定义基础链接").setView(panel).setNegativeButton("取消",null).setPositiveButton("保存",null).create();prompt.setOnShowListener(v->prompt.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{String raw=input.getText().toString();String normalized=normalizeLanzouBaseOrigin(raw);if(normalized.equals("https://oreojiang.lanzout.com")&&!raw.toLowerCase(Locale.ROOT).matches(".*(?:lanzou|^[a-z0-9-]{2,24}$).*")){input.setError("请输入蓝奏域名或前缀，例如 oreojiang");return;}sessionLanzouBaseOrigin=normalized;persistSearchSettings();prompt.dismiss();showNotice("列表基础链接切换为 "+lanzouBaseOriginLabel(),false);if(pageKind==4)refreshSettingsInPlace();}));showRounded(prompt);}
  void openRecognizedLink(String raw,String password){String normalized=normalizedWebUrl(raw);if(normalized.isEmpty()){showNotice("链接为空",false);return;}if(isLanzouUrl(normalized)){normalized=preferredLanzouUrl(normalized);if(directLanzouListOpen)openLanzouList(normalized,password);else showLanzouOpenDialog(normalized,password);return;}openWebLink(normalized,password);}
  void showLanzouOpenDialog(String url,String password){AlertDialog prompt=new AlertDialog.Builder(this).setTitle("打开蓝奏云链接").setMessage(url).setNegativeButton("浏览器打开",(dialog,which)->openWebLink(url,password)).setPositiveButton("列表解析打开",(dialog,which)->openLanzouList(url,password)).create();showRounded(prompt);}

  void openWebLink(String url,String password){if(sessionOpenWebExternal)openInBrowser(url,password);else openWebPage(url,password,false);}
  void openWebPage(String url,String password,boolean external){if(external){openInBrowser(url,password);return;}try{String target=normalizedWebUrl(url);if(isLanzouUrl(target))target=preferredLanzouUrl(target);Intent i=new Intent(this,LanzouWebActivity.class);i.putExtra(LanzouWebActivity.EXTRA_URL,target);if(password!=null&&!password.isEmpty())((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("蓝奏云密码",password));startActivity(i);}catch(Exception error){showNotice("无法打开网页",true);}}
  void startWebDirectDownload(String url,String name){String value=url==null?"":url.trim();String reason=DownloadUrlPolicy.rejectionReason(value);if(!reason.isEmpty()){showNotice("网页下载地址不受支持："+reason,true);return;}String title=name==null||name.trim().isEmpty()?android.webkit.URLUtil.guessFileName(value,null,null):name;beginExternalDownload(value,title);}
  void beginExternalDownload(String url,String name){beginExternalDownloadGated(url,name);}
  void beginExternalDownloadGated(String url,String name){if(!ensureDirectStorageAuthorized(()->beginExternalDownloadGated(url,name)))return;try{DownloadEntry entry=newExternalDownloadEntry(url,name);downloadEntries.add(entry);if(downloadsPage)renderDownloadFilters();persistDownloadHistory(entry);updateDownloadUi(entry);startEntryDownload(entry,entry.directUrl);}catch(Exception error){String message=friendlyError(error);if(downloadStorageFailure(message)){requestManageAllFilesAccess("无法在当前下载目录创建文件："+message,()->beginExternalDownloadGated(url,name),false);return;}showNotice("无法创建 Download 文件："+message,true);}}
  DownloadEntry newExternalDownloadEntry(String url,String name)throws Exception{String value=url==null?"":url.trim(),reason=DownloadUrlPolicy.rejectionReason(value);if(!reason.isEmpty())throw new IOException("外部下载地址不受支持："+reason);DownloadEntry entry=new DownloadEntry();entry.source=DOWNLOAD_SOURCE_EXTERNAL;entry.name=safeName(name==null||name.trim().isEmpty()?android.webkit.URLUtil.guessFileName(value,null,null):name);entry.state=DOWNLOAD_WAITING;entry.shareUrl=value;entry.directUrl=value;entry.createdAt=System.currentTimeMillis();entry.target=createDownloadTarget(entry.name);entry.uriString=entry.target.toString();entry.parentUriString=currentDownloadParentReferenceUri().toString();return entry;}
  void openInBrowser(String url,String password){try{String target=normalizedWebUrl(url);if(isLanzouUrl(target))target=preferredLanzouUrl(target);Uri uri=Uri.parse(target);String scheme=uri.getScheme();if(uri.getHost()==null||scheme==null||!(scheme.equalsIgnoreCase("http")||scheme.equalsIgnoreCase("https")||scheme.equalsIgnoreCase("ftp")))throw new IllegalArgumentException();if(password!=null&&!password.isEmpty()){((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("蓝奏云密码",password));showNotice("密码已复制",false);}startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception error){showNotice("无法打开链接",true);}}
  void openLanzouList(String url,String rawPassword){String normalized=preferredLanzouUrl(normalizedWebUrl(url)),password=rawPassword==null?"":rawPassword;Models.Source directory=new Models.Source();directory.title="蓝奏云链接";directory.url=normalized;directory.password=password;if(normalized.contains("#lanzou-folder=")){openSourcePage(directory);return;}Models.Source page=new Models.Source();page.id="link:"+Integer.toHexString((normalized+'\n'+password).hashCode());page.kind=Models.SOURCE_COMPOSITE;page.title="蓝奏云链接";page.url=normalized;page.password=password;page.description="正在后台解析分享内容";openSourcePage(page);int request=navigationSession;io.execute(()->{try{Models.SourceMember member=core.probeSingleSource(normalized,password);member.id="item";member.parentId="";member.lightweight=member.metadataLoaded=member.iconLoaded=member.detailsLoaded=true;Models.Source source=new Models.Source();source.id=page.id;source.kind=Models.SOURCE_COMPOSITE;source.title=member.title.isEmpty()?"蓝奏云分享":member.title;source.url=normalized;source.password=password;source.avatarUrl=member.iconUrl;source.description=member.description;source.members.add(member);runOnUiThread(()->{if(request!=navigationSession||isFinishing())return;if(content==null){openSourcePage(source);return;}applyResolvedLanzouList(request,normalized,source);});}catch(Exception notSingle){runOnUiThread(()->applyResolvedLanzouDirectory(request,normalized,directory));}});}
  void applyResolvedLanzouList(int request,String normalized,Models.Source source){if(request!=navigationSession||activeSource==null||!normalized.equals(activeSource.url)||content==null)return;FolderPageState state=currentFolderState();if(state==null)return;copySource(source,state.source);activeFolderState=state;activeSource=state.source;renderFolder(core.compositeSnapshot(activeSource));}
  void applyResolvedLanzouDirectory(int request,String normalized,Models.Source directory){if(request!=navigationSession||activeSource==null||!normalized.equals(activeSource.url)||content==null)return;FolderPageState state=currentFolderState();if(state==null)return;copySource(directory,state.source);activeFolderState=state;activeSource=state.source;openFolder(activeSource,true);}

  /**
   * 软件库里点一个条目 = 下载它。
   *
   * [DFW-97] **默认下载完自动跳安装界面**。用户 2026-10-02：
   * > 「默认改为下载好自动跳转安装界面。」
   *
   * 历史：DFW-93 曾经删掉过"下载完成后自动安装"，那是当时用户的决定；
   * 现在用户明确要回来，所以这里传 `true`。
   *
   * 安全性没有降低：真正决定"能不能自动装"的是
   * {@link DownloadSourcePolicy#allowsAutomaticInstall} —— 它按**来源**判断，
   * 蓝奏云（LANZOU）放行，**外部链接与来源不明的历史记录仍然 fail-closed 需要确认**
   * （见 `DownloadSourcePolicy.requiresInstallConfirmation`）。
   * 也就是说"自动安装"只对可信来源生效，不是无差别放开。
   */
  void beginDownload(Models.Item item){
    beginDownload(item,null,true);
  }
    void requestItemDownload(Models.Item item,boolean autoInstall){beginDownload(item,null,autoInstall);}
    void requestVerifiedUpdateDownload(Models.Item item,String version){String expected=version==null?"":version.trim();if(expected.isEmpty()){showNotice("更新版本信息无效",true);return;}beginVerifiedUpdateDownload(item,expected);}
  void startBulkDownloads(List<Models.Item> items){startBulkDownloadsGated(items);}
  void startBulkDownloadsGated(List<Models.Item> items){if(!ensureDirectStorageAuthorized(()->startBulkDownloadsGated(items)))return;
    List<Models.Item> accepted=new ArrayList<>();List<DownloadEntry> entries=new ArrayList<>();int failed=0;
    String batchId="batch:"+UUID.randomUUID();String batchTitle="批量下载 · "+items.size()+" 项";for(Models.Item item:items)try{DownloadEntry entry=newDownloadEntry(item,null,false);entry.batchId=batchId;entry.batchTitle=batchTitle;entries.add(entry);accepted.add(item);}catch(Exception error){failed++;}
    if(entries.isEmpty()){showNotice("无法创建 Download 文件",true);return;}
    publishNewDownloadEntries(entries);TransferCoordinator<BatchResolved> queue=new TransferCoordinator<>(downloadTransferParallelism(),(next,completed)->{DownloadEntry entry=next.entry;entry.cancelPending=null;entry.nextBatch=completed;startResolvedTransfer(entry,next.cached);});
    for(int i=0;i<entries.size();i++)resolveBulkEntry(entries.get(i),accepted.get(i),queue);
    if(failed>0)showNotice("有 "+failed+" 个任务无法创建保存文件",true);
  }
  DownloadEntry newDownloadEntry(Models.Item item,Runnable nextBatch,boolean autoInstall)throws Exception{DownloadEntry entry=new DownloadEntry();entry.name=safeName(item.title);entry.state=DOWNLOAD_RESOLVING;entry.iconUrl=item.iconUrl;entry.shareUrl=preferredLanzouUrl(firstNonEmpty(item.shareUrl,item.url));entry.password=item.password;entry.sourceSizeText=item.size;entry.totalBytes=parseSize(item.size);entry.createdAt=System.currentTimeMillis();entry.nextBatch=nextBatch;boolean automatic=false&&entry.name.toLowerCase(Locale.ROOT).endsWith(".apk");entry.autoInstall=autoInstall||automatic;entry.autoInstallFromPreference=!autoInstall&&automatic;entry.target=createDownloadTarget(entry.name);entry.uriString=entry.target.toString();entry.parentUriString=currentDownloadParentReferenceUri().toString();return entry;}
  void publishNewDownloadEntries(List<DownloadEntry> entries){downloadEntries.addAll(entries);persistDownloadHistory();if(downloadsPage){renderDownloadFilters();renderDownloads(downloadQuery);}}
  void resolveBulkEntry(DownloadEntry entry,Models.Item item,TransferCoordinator<BatchResolved> queue){int generation=++entry.controlGeneration;DirectLinkResolver.Ticket ticket=directResolver.resolve(entry.shareUrl,true,new DirectLinkResolver.PasswordCallback(){public void passwordRequired(boolean rejectedPrevious){showLanzouAccessPrompt(entry.shareUrl,item.title,rejectedPrevious,accessValue->directResolver.providePassword(entry.shareUrl,accessValue),()->directResolver.cancelPasswordRequest(entry.shareUrl));}public void resolved(String directUrl,long resolvedAt,boolean cached){synchronized(entry){if(generation!=entry.controlGeneration||entry.stopRequested||!entry.state.equals(DOWNLOAD_RESOLVING))return;entry.resolverTicket=null;entry.directUrl=directUrl;entry.resolvedAt=resolvedAt;entry.error="";entry.state=DOWNLOAD_WAITING;}updateDownloadUi(entry);enqueueBatchTransfer(queue,entry,cached);}public void failed(String error){String message=friendlyError(new IOException(error));synchronized(entry){if(generation!=entry.controlGeneration||entry.stopRequested||!entry.state.equals(DOWNLOAD_RESOLVING))return;entry.resolverTicket=null;entry.state=DOWNLOAD_FAILED;entry.error=message;entry.speedBps=0;entry.etaSeconds=-1;}updateDownloadUi(entry);}});synchronized(entry){if(generation==entry.controlGeneration&&!entry.stopRequested&&entry.state.equals(DOWNLOAD_RESOLVING))entry.resolverTicket=ticket;else ticket.cancel();}}
  void enqueueBatchTransfer(TransferCoordinator<BatchResolved> queue,DownloadEntry entry,boolean cached){BatchResolved task=new BatchResolved(entry,cached);entry.cancelPending=()->queue.remove(task);runOnUiThread(()->{if(!entry.stopRequested&&entry.state.equals(DOWNLOAD_WAITING))queue.enqueue(task);});}
  void beginDownload(Models.Item item,Runnable nextBatch,boolean autoInstall){beginDownloadGated(item,nextBatch,autoInstall);}
  void beginDownloadGated(Models.Item item,Runnable nextBatch,boolean autoInstall){if(!ensureDirectStorageAuthorized(()->beginDownloadGated(item,nextBatch,autoInstall)))return;
    try{
      DownloadEntry entry=newDownloadEntry(item,nextBatch,autoInstall);downloadEntries.add(entry);if(downloadsPage)renderDownloadFilters();persistDownloadHistory(entry);updateDownloadUi(entry);
      /*
       * [DFW-97] **点下载必须立刻有反馈。**
       *
       * 用户 2026-10-02：「软件库下载软件点击后，居然会没有任何反馈」。
       * 原来点下去是"静默开始"—— 从点击到真正落盘之间要经过解析直链等好几步，
       * 中间可能几百毫秒到几秒，用户完全不知道点没点成功。
       * 现在点下去就报一句「（名字）下载中…」，把"我收到了"这件事立刻说清楚。
       */
      showNotice(entry.name+" 下载中…",false);
      startEntryDownload(entry,entry.shareUrl);
                }catch(Exception error){String message=friendlyError(error);if(downloadStorageFailure(message)){requestManageAllFilesAccess("无法在当前下载目录创建文件："+message,()->beginDownloadGated(item,nextBatch,autoInstall),false);return;}if(nextBatch!=null)runOnUiThread(nextBatch);showNotice("无法创建 Download 文件："+message,true);}
  }
  void requestRetryDownload(DownloadEntry entry){if(entry==null)return;requestRetryDownloadGated(entry);}
  void requestRetryDownloadGated(DownloadEntry entry){if(!ensureDirectStorageAuthorized(()->requestRetryDownloadGated(entry)))return;if(entry.state.equals(DOWNLOAD_CANCELLED)){showNotice("任务已取消且断点已清理，请从资源页重新下载",false);return;}if(entry.updateInfo==null&&entry.shareUrl.isEmpty()){showNotice("该记录缺少蓝奏云链接，无法重试",true);return;}if(Build.VERSION.SDK_INT<29&&checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){synchronized(entry){pendingRetryDownload=new PendingRetry(entry);}requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},STORAGE_PERMISSION);return;}if(entry.updateInfo!=null)retryLanzouPlusUpdate(entry);else retryDownload(entry);}
  void retryDownload(DownloadEntry entry){try{Uri fallbackParent=currentDownloadParentReferenceUri();ensureRetryTarget(entry,fallbackParent);entry.state=DOWNLOAD_RESOLVING;entry.error="";entry.speedBps=0;entry.etaSeconds=-1;entry.savedPercent=-1;entry.savedState="";entry.nextBatch=null;entry.cancelPending=null;entry.resolverTicket=null;entry.downloader=null;entry.batchAdvanced=false;entry.stopRequested=false;entry.resumeAfterPause=false;entry.directFallbackTried=false;entry.controlGeneration++;persistDownloadHistory(entry);updateDownloadUi(entry);startEntryDownload(entry,entry.shareUrl);}catch(Exception error){String message=friendlyError(error);failDownload(entry,message);if(downloadStorageFailure(message)){requestDownloadStorageRecovery(entry,"无法继续写入当前自定义下载目录："+message,()->requestRetryDownload(entry));return;}}}
  void retryLanzouPlusUpdate(DownloadEntry entry){try{UpdateClient.UpdateInfo info=entry.updateInfo;if(info==null)throw new IOException("更新信息已失效");ensureRetryTarget(entry,currentDownloadParentReferenceUri());entry.error="";entry.speedBps=0;entry.etaSeconds=-1;entry.stopRequested=false;entry.resumeAfterPause=false;String url=entry.directUrl.isEmpty()?info.primaryUrl():entry.directUrl;downloadLanzouPlusUpdate(info,entry,url,url.equals(info.primaryUrl()));}catch(Exception error){String message=friendlyError(error);failDownload(entry,message);if(downloadStorageFailure(message)){requestDownloadStorageRecovery(entry,"无法继续写入当前自定义下载目录："+message,()->requestRetryDownload(entry));return;}}}
  void ensureRetryTarget(DownloadEntry entry,Uri fallbackParent)throws Exception{if(entryUri(entry)!=null&&resumeTargetAvailable(entry))return;entry.target=createDownloadTarget(entry.name);entry.uriString=entry.target.toString();entry.parentUriString=fallbackParent.toString();entry.downloadedBytes=entry.verifiedTotalBytes=0;entry.percent=0;}
  boolean resumeTargetAvailable(DownloadEntry entry){Uri uri=entryUri(entry);if(uri==null)return false;if("file".equals(uri.getScheme()))return true;try(android.os.ParcelFileDescriptor ignored=getContentResolver().openFileDescriptor(uri,"rw")){return ignored!=null;}catch(Exception missing){return false;}}
  void startEntryDownload(DownloadEntry entry,String url){if(entry==null)return;if(DOWNLOAD_SOURCE_EXTERNAL.equals(entry.source)){entry.directUrl=url;entry.shareUrl=url;startExternalTransfer(entry);return;}if(DOWNLOAD_SOURCE_LEGACY.equals(entry.source)&&!isLanzouUrl(url)){failDownload(entry,"历史记录来源未知，无法安全重试");return;}entry.shareUrl=url;resolveEntryDownload(entry,false);}
  void startExternalTransfer(DownloadEntry entry){if(entry==null)return;String policyError=DownloadUrlPolicy.rejectionReason(entry.directUrl);if(!policyError.isEmpty()){failDownload(entry,"外部下载地址不受支持："+policyError);return;}int generation;SegmentDownloader downloader; synchronized(entry){if(entry.stopRequested)return;entry.state=DOWNLOAD_RUNNING;entry.startedAt=System.currentTimeMillis();entry.lastSpeedAt=entry.startedAt;entry.lastSpeedBytes=entry.downloadedBytes;generation=++entry.controlGeneration;downloader=new SegmentDownloader(this);entry.downloader=downloader;}updateDownloadUi(entry);downloader.startExternal(entry.directUrl,entry.target,entry.verifiedTotalBytes,new SegmentDownloader.Listener(){public void progress(long done,long total){synchronized(entry){if(entry.transferOwnedBy(generation,downloader)&&!entry.stopRequested)applyDownloadProgress(entry,done,total);}}public void completed(){synchronized(entry){if(entry.downloader!=downloader||generation!=entry.controlGeneration||entry.stopRequested||!entry.state.equals(DOWNLOAD_RUNNING))return;entry.downloader=null;entry.downloadedBytes=entry.totalBytes=Math.max(entry.totalBytes,entry.downloadedBytes);entry.percent=100;entry.state=DOWNLOAD_COMPLETED;entry.error="";entry.completedAt=System.currentTimeMillis();}finishDownloadTarget(entry,true);updateDownloadUi(entry);}public void paused(long done,long total){finishStoppedTransfer(entry,DOWNLOAD_PAUSED,done,total,generation,downloader);}public void cancelled(long done,long total){finishStoppedTransfer(entry,DOWNLOAD_CANCELLED,done,total,generation,downloader);}public void failed(String error){synchronized(entry){if(entry.downloader!=downloader)return;entry.downloader=null;if(generation!=entry.controlGeneration||entry.stopRequested)return;entry.state=DOWNLOAD_FAILED;entry.error=friendlyError(new IOException(error));entry.speedBps=0;entry.etaSeconds=-1;}updateDownloadUi(entry);}});}
  /**
   * [DFW-125 2026-10-03] **真正的**解析看门狗：一个独立的定时器，不依赖任何其它调用。
   *
   * ## 它修的是什么（实测根因，不是推测）
   * DFW-101 原本把看门狗写在 `updateDownloadUi()` 里，靠"这个函数被反复调用"来计时。
   * 但 `updateDownloadUi()` 的**全部调用点**（2474/2494/5112/5113/5116/5117/5118/5154/5156/5166/5167/5218）
   * 里**没有任何周期性定时器** —— 条目进入「解析中」时只调用一次（就在 `resolveEntryDownload` 开头）。
   * 之后要有第二次调用，只能是：① 传输进度回调（要解析**成功**才有）；② 解析失败回调（要解析器**失败**才有）。
   * 所以**恰恰在解析器卡住不回调时，看门狗永远不会触发** —— 它在它唯一要防的场景里是死的。
   * 用户 2026-10-03 真机反馈「所有东西都没法下载，一直显示解析中」，就是这个死法。
   *
   * ## 现在的做法
   * 进入解析中时**排一个 25 秒的延时任务**，到点自己检查：还在解析中就判失败，并给出人话原因。
   * 它不依赖任何其它代码路径，所以不可能再被绕过。
   *
   * ## 为什么是"失败"而不是"继续等"
   * 无限转圈对用户是最坏的形态：不知道在等什么、不知道还要多久、也不知道该不该重试。
   * 失败 + 一句明确的话 + 一个「重试」按钮，用户至少知道发生了什么。
   */
  void armResolveWatchdog(DownloadEntry entry,int generation){
    ui.postDelayed(()->{
      DirectLinkResolver.Ticket pending=null;
      synchronized(entry){
        /* 三重校验，避免误杀：换过一次解析（generation 变了）、已被取消、或已经不在解析中，都不动手。 */
        if(generation!=entry.controlGeneration||entry.stopRequested)return;
        if(!DOWNLOAD_RESOLVING.equals(entry.state))return;
        /* **在等用户输密码时不算超时** —— 那是「等用户」，不是「解析器卡住」。
           重新排一次，别把用户正在打字的任务打死。见 DirectLinkResolver.isAwaitingPassword。 */
        if(directResolver.isAwaitingPassword(entry.shareUrl)){armResolveWatchdog(entry,generation);return;}
        pending=entry.resolverTicket;
        entry.resolverTicket=null;
        entry.state=DOWNLOAD_FAILED;
        entry.error="解析超时（"+RESOLVE_WATCHDOG_MS/1000+"秒没有回应）。解析这一步没有返回任何结果，通常是链接失效、需要密码、或网络被拦。";
        entry.speedBps=0;entry.etaSeconds=-1;
      }
      /* 把还在飞的请求真正取消掉 —— 否则它可能在超时之后才回来，覆盖掉这次失败。 */
      if(pending!=null){try{pending.cancel();}catch(Throwable ignored){}}
      persistDownloadHistory(entry);
      advanceBatch(entry);
      updateDownloadUi(entry);
    },RESOLVE_WATCHDOG_MS);
  }
  void resolveEntryDownload(DownloadEntry entry,boolean forceFresh){if(forceFresh)directResolver.invalidate(entry.shareUrl,entry.directUrl);boolean waiting=!directResolver.hasFresh(entry.shareUrl);if(waiting){entry.state=DOWNLOAD_RESOLVING;updateDownloadUi(entry);}int generation=++entry.controlGeneration;
    if(waiting)armResolveWatchdog(entry,generation);DirectLinkResolver.Ticket ticket=directResolver.resolve(entry.shareUrl,true,new DirectLinkResolver.PasswordCallback(){public void passwordRequired(boolean rejectedPrevious){showLanzouAccessPrompt(entry.shareUrl,entry.name,rejectedPrevious,accessValue->directResolver.providePassword(entry.shareUrl,accessValue),()->directResolver.cancelPasswordRequest(entry.shareUrl));}public void resolved(String directUrl,long resolvedAt,boolean cached){synchronized(entry){if(generation!=entry.controlGeneration||entry.stopRequested||!entry.state.equals(DOWNLOAD_RESOLVING))return;entry.resolverTicket=null;entry.directUrl=directUrl;entry.resolvedAt=resolvedAt;entry.error="";}persistDownloadHistory(entry);startResolvedTransfer(entry,cached);}public void failed(String error){String message=friendlyError(new IOException(error));synchronized(entry){if(generation!=entry.controlGeneration||entry.stopRequested||!entry.state.equals(DOWNLOAD_RESOLVING))return;entry.resolverTicket=null;entry.state=DOWNLOAD_FAILED;entry.error=message;entry.speedBps=0;entry.etaSeconds=-1;}advanceBatch(entry);updateDownloadUi(entry);}});synchronized(entry){if(generation==entry.controlGeneration&&!entry.stopRequested&&entry.state.equals(DOWNLOAD_RESOLVING))entry.resolverTicket=ticket;else ticket.cancel();}}
  void startResolvedTransfer(DownloadEntry entry,boolean reusedCached){synchronized(entry){if(entry.stopRequested){advanceBatch(entry);return;}entry.cancelPending=null;entry.state=DOWNLOAD_RUNNING;entry.startedAt=System.currentTimeMillis();entry.completedAt=0;entry.lastSpeedAt=entry.startedAt;entry.lastSpeedBytes=entry.downloadedBytes;int generation=entry.controlGeneration;SegmentDownloader downloader=new SegmentDownloader(this);entry.downloader=downloader;updateDownloadUi(entry);downloader.startResolved(entry.directUrl,entry.target,entry.verifiedTotalBytes,new SegmentDownloader.Listener(){public void progress(long done,long total){synchronized(entry){if(entry.transferOwnedBy(generation,downloader)&&!entry.stopRequested)applyDownloadProgress(entry,done,total);}}public void completed(){synchronized(entry){if(entry.downloader!=downloader||generation!=entry.controlGeneration||entry.stopRequested||!entry.state.equals(DOWNLOAD_RUNNING))return;entry.downloader=null;entry.downloadedBytes=entry.totalBytes=Math.max(entry.totalBytes,entry.downloadedBytes);entry.percent=100;entry.state=DOWNLOAD_COMPLETED;entry.error="";entry.speedBps=0;entry.etaSeconds=0;entry.completedAt=System.currentTimeMillis();}advanceBatch(entry);finishDownloadTarget(entry,true);updateDownloadUi(entry);if(entry.autoInstall)runOnUiThread(()->{if(generation==entry.controlGeneration&&entry.state.equals(DOWNLOAD_COMPLETED)){if(entry.autoInstallFromPreference)autoInstallCompletedEntry(entry);else installEntry(entry);}});
/* [DFW-131 2026-10-03] 普通下载的 APK 也要**自动弹出安装界面**。
   用户原话：「无论是更新软件，还是下载其他apk，下载好了之后，我的安装界面会直接自动提出来，
   就是说会直接自动开始安装，然后让我点击授权，而不是一直在那个下载界面停着」。

   为什么以前不弹：唯一的下发点 `requestItemDownload(item,false)` 把 `autoInstall` **写死成 false**，
   于是上面那句 `if(entry.autoInstall)` 永远不成立，安装界面永远不出现。
   现在判据改成**文件类型**（是不是 .apk），而不是那个从没被置真的标志 ——
   用户关心的是「我下的是个安装包」，不是「这条链路内部把哪个 boolean 设成了什么」。

   走 `autoInstallCompletedEntry` 而不是直接 `installEntry`：它自己会
   ① 再确认一次是 .apk 且文件已就绪；② 尊重来源策略（外来更新包仍要用户确认）；
   ③ 若用户开了 Shizuku 静默安装且服务可用则静默装，否则回退系统安装器。 */
else if(isApkEntry(entry))runOnUiThread(()->{if(generation==entry.controlGeneration&&entry.state.equals(DOWNLOAD_COMPLETED))autoInstallCompletedEntry(entry);});}public void paused(long done,long total){finishStoppedTransfer(entry,DOWNLOAD_PAUSED,done,total,generation,downloader);}public void cancelled(long done,long total){finishStoppedTransfer(entry,DOWNLOAD_CANCELLED,done,total,generation,downloader);}public void failed(String error){boolean retryFresh;synchronized(entry){if(entry.downloader!=downloader)return;entry.downloader=null;retryFresh=reusedCached&&!entry.directFallbackTried&&!entry.stopRequested;if(retryFresh){entry.directFallbackTried=true;entry.state=DOWNLOAD_RESOLVING;entry.error="直链已失效，正在重新解析";}else if(entry.stopRequested)return;else{entry.state=DOWNLOAD_FAILED;entry.error=friendlyError(new IOException(error));entry.speedBps=0;entry.etaSeconds=-1;}}if(retryFresh){updateDownloadUi(entry);synchronized(entry){if(generation!=entry.controlGeneration||entry.stopRequested||!entry.state.equals(DOWNLOAD_RESOLVING))return;directResolver.invalidate(entry.shareUrl,entry.directUrl);resolveEntryDownload(entry,true);}return;}advanceBatch(entry);updateDownloadUi(entry);}});}}
  void finishStoppedTransfer(DownloadEntry entry,String state,long done,long total,int generation,SegmentDownloader owner){boolean resume,cancelled;int currentGeneration;synchronized(entry){/* DFWX-STAB-001：终态回调先做 ownership 校验（锁内）——
        cancelled 只看 owner（cancelDownload 已 ++generation，自身回调必须放行），paused/其余看 generation+owner 双匹配；
        旧 generation 或已被 retry 替换的 owner 一律忽略，不覆盖新状态 */boolean owned=state.equals(DOWNLOAD_CANCELLED)?entry.transferOwnerIs(owner):entry.transferOwnedBy(generation,owner);if(!owned)return;entry.downloader=null;entry.downloadedBytes=Math.max(entry.downloadedBytes,done);if(total>0)entry.totalBytes=entry.verifiedTotalBytes=total;entry.percent=entry.totalBytes>0?(int)Math.min(100,entry.downloadedBytes*100/entry.totalBytes):entry.percent;entry.speedBps=0;entry.etaSeconds=-1;cancelled=entry.state.equals(DOWNLOAD_CANCELLED)||state.equals(DOWNLOAD_CANCELLED);entry.state=cancelled?DOWNLOAD_CANCELLED:state;entry.error="";resume=!cancelled&&state.equals(DOWNLOAD_PAUSED)&&entry.resumeAfterPause;entry.resumeAfterPause=false;currentGeneration=entry.controlGeneration;}if(cancelled)discardCancelledPartial(entry);advanceBatch(entry);updateDownloadUi(entry);if(resume)runOnUiThread(()->{if(currentGeneration==entry.controlGeneration&&entry.state.equals(DOWNLOAD_PAUSED))resumeDownload(entry);});}
  void applyDownloadProgress(DownloadEntry entry,long done,long total){long now=System.currentTimeMillis(),elapsed=now-entry.lastSpeedAt;if(elapsed>=400&&done>=entry.lastSpeedBytes){long instant=(done-entry.lastSpeedBytes)*1000/Math.max(1,elapsed);entry.speedBps=entry.speedBps<=0?instant:(entry.speedBps*2+instant)/3;entry.lastSpeedAt=now;entry.lastSpeedBytes=done;}entry.downloadedBytes=done;entry.totalBytes=entry.verifiedTotalBytes=total;entry.percent=(int)(done*100/Math.max(1,total));entry.etaSeconds=entry.speedBps>0?Math.max(0,(total-done)/entry.speedBps):-1;if(!entry.stopRequested)entry.state=DOWNLOAD_RUNNING;updateDownloadUi(entry);}
  void failDownload(DownloadEntry entry,String message){entry.state=DOWNLOAD_FAILED;entry.error=message;entry.speedBps=0;entry.etaSeconds=-1;updateDownloadUi(entry);}
  /**
   * [DFW-102] 下载结果通知：从顶部滑下来的通知条，**带文件名**。
   *
   * 用户原话：「你可以加入下载完毕或下载失败的通知，而且写明白啥东西下载好了，
   * 别只写个下载完成。用你推荐的那个从上往下显示的通知吧。」
   *
   * 为什么挂在这里（而不是每个失败出口各写一遍）：
   * 下载的状态出口有七八个（外部直链、解析后传输、更新包、重试、续传收尾……），
   * 分散写必然漏。`drainDownloadUi` 是**所有状态变化的唯一汇聚点**，
   * 而且它只在 `updateDownloadUi` 主动上报时才处理条目 —— 启动时从历史记录恢复的
   * 旧条目**不会**流经这里（`uiState` 在恢复时已同步），所以不会每次开软件重播旧通知。
   *
   * 批量下载合并成一条：十个文件就是十条通知会把屏幕刷爆，
   * 所以同一批次内显示「已下载 N 个文件 · 最新：xxx」，用户仍能看清是什么东西。
   */
  void noteDownloadOutcome(DownloadEntry entry){
    if(entry==null)return;
    final String name=(entry.name==null||entry.name.trim().isEmpty())?"文件":entry.name.trim();
    final boolean completed=DOWNLOAD_COMPLETED.equals(entry.state),failed=DOWNLOAD_FAILED.equals(entry.state);
    if(!completed&&!failed)return;
    int sameBatch=0;
    if(!entry.batchId.isEmpty())for(DownloadEntry other:downloadEntries)
      if(entry.batchId.equals(other.batchId)&&entry.state.equals(other.state))sameBatch++;
    if(completed){
      showTopBanner(sameBatch>1?"已下载 "+sameBatch+" 个文件 · 最新："+name:"已下载 · "+name,3200,R.drawable.ic_download);
      return;
    }
    String reason=entry.error==null?"":entry.error.trim();
    if(reason.length()>60)reason=reason.substring(0,60)+"…";
    String head=sameBatch>1?"下载失败 "+sameBatch+" 个 · 最新："+name:"下载失败 · "+name;
    showTopBanner(reason.isEmpty()?head:head+"："+reason,5200,R.drawable.ic_close);
  }
  void advanceBatch(DownloadEntry entry){Runnable next=entry.nextBatch;if(next==null)return;synchronized(entry){if(entry.batchAdvanced)return;entry.batchAdvanced=true;}runOnUiThread(next);}
  boolean isDownloadActive(DownloadEntry entry){return entry!=null&&(entry.state.equals(DOWNLOAD_RESOLVING)||entry.state.equals(DOWNLOAD_WAITING)||entry.state.equals(DOWNLOAD_RUNNING));}
  boolean isDownloadPausable(DownloadEntry entry){return isDownloadActive(entry);}
  void pauseDownload(DownloadEntry entry){synchronized(entry){if(!isDownloadPausable(entry))return;SegmentDownloader downloader=entry.downloader;if(downloader!=null&&!downloader.pause())return;entry.resumeAfterPause=false;entry.stopRequested=true;if(entry.state.equals(DOWNLOAD_RESOLVING)){entry.controlGeneration++;DirectLinkResolver.Ticket ticket=entry.resolverTicket;entry.resolverTicket=null;if(ticket!=null)ticket.cancel();}else if(entry.state.equals(DOWNLOAD_WAITING)){Runnable cancel=entry.cancelPending;entry.cancelPending=null;if(cancel!=null)cancel.run();}entry.state=DOWNLOAD_PAUSED;entry.error="";entry.speedBps=0;entry.etaSeconds=-1;}updateDownloadUi(entry);}
  void resumeDownload(DownloadEntry entry){if(entry==null)return;synchronized(entry){if(!entry.state.equals(DOWNLOAD_PAUSED))return;if(entry.downloader!=null){entry.resumeAfterPause=true;return;}entry.stopRequested=false;requestRetryDownload(entry);}}
  void cancelDownload(DownloadEntry entry){if(entry==null)return;boolean awaitTransfer;synchronized(entry){if(entry.state.equals(DOWNLOAD_COMPLETED)||entry.state.equals(DOWNLOAD_CANCELLED))return;SegmentDownloader downloader=entry.downloader;awaitTransfer=downloader!=null;if(downloader!=null&&!downloader.cancel()&&!entry.state.equals(DOWNLOAD_PAUSED))return;entry.controlGeneration++;entry.resumeAfterPause=false;entry.stopRequested=true;if(entry.state.equals(DOWNLOAD_RESOLVING)){DirectLinkResolver.Ticket ticket=entry.resolverTicket;entry.resolverTicket=null;if(ticket!=null)ticket.cancel();}else if(entry.state.equals(DOWNLOAD_WAITING)){Runnable cancel=entry.cancelPending;entry.cancelPending=null;if(cancel!=null)cancel.run();}entry.state=DOWNLOAD_CANCELLED;entry.error="";entry.speedBps=0;entry.etaSeconds=-1;}if(awaitTransfer)updateDownloadUi(entry);else io.execute(()->{discardCancelledPartial(entry);advanceBatch(entry);updateDownloadUi(entry);});}
  void discardCancelledPartial(DownloadEntry entry){Uri partial;synchronized(entry){partial=entryUri(entry);entry.target=null;entry.uriString="";entry.downloadedBytes=0;entry.verifiedTotalBytes=0;entry.percent=0;entry.speedBps=0;entry.etaSeconds=-1;entry.lastSpeedAt=0;entry.lastSpeedBytes=0;}if(partial==null)return;try{if("file".equals(partial.getScheme())){String path=partial.getPath();if(path!=null)new File(path).delete();}else if("content".equals(partial.getScheme()))getContentResolver().delete(partial,null,null);}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}}
  void togglePauseSelectedDownloads(){boolean pause=false;for(DownloadEntry entry:selectedDownloads)if(isDownloadActive(entry)){pause=true;break;}for(DownloadEntry entry:new ArrayList<>(selectedDownloads))if(pause){if(isDownloadActive(entry))pauseDownload(entry);}else if(entry.state.equals(DOWNLOAD_PAUSED))resumeDownload(entry);}
  void cancelSelectedDownloads(){for(DownloadEntry entry:new ArrayList<>(selectedDownloads))if(isDownloadActive(entry)||entry.state.equals(DOWNLOAD_PAUSED))cancelDownload(entry);}
  void togglePauseAllActive(){boolean pause=false;for(DownloadEntry entry:downloadEntries)if(isDownloadActive(entry)){pause=true;break;}for(DownloadEntry entry:downloadEntries)if(pause){if(isDownloadActive(entry))pauseDownload(entry);}else if(entry.state.equals(DOWNLOAD_PAUSED))resumeDownload(entry);}
  void resumeAllPaused(){for(DownloadEntry entry:downloadEntries)if(entry.state.equals(DOWNLOAD_PAUSED))resumeDownload(entry);}
  void cancelAllActive(){for(DownloadEntry entry:downloadEntries)if(isDownloadActive(entry)||entry.state.equals(DOWNLOAD_PAUSED))cancelDownload(entry);}

  Uri createDownloadTarget(String name)throws Exception{if(!storageAccessGranted())throw new SecurityException(Build.VERSION.SDK_INT>=30?"需要管理所有文件权限":"需要存储权限");File directory=selectedDownloadDirectory();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建下载目录");if(!directory.canWrite())throw new IOException("当前保存路径不可写");return Uri.fromFile(uniqueFile(directory,name+".part"));}
    String downloadTargetMime(String name){return name!=null&&name.toLowerCase(Locale.ROOT).endsWith(".apk")?"application/vnd.android.package-archive":"application/octet-stream";}
  void retryFinalizeDownload(DownloadEntry entry){io.execute(()->{synchronized(entry){if(entryUri(entry)==null)return;entry.state=DOWNLOAD_COMPLETED;entry.error="";}finishDownloadTarget(entry,true);updateDownloadUi(entry);if(entry.autoInstall&&entry.state.equals(DOWNLOAD_COMPLETED))runOnUiThread(()->autoInstallCompletedEntry(entry));});}
  void finishDownloadTarget(DownloadEntry entry,boolean success){if(!success)return;ensureCrashFolder();Uri uri=entryUri(entry);if(uri==null)return;try{if("file".equals(uri.getScheme())){File part=new File(uri.getPath());if(part.getName().endsWith(".part")){File target=uniqueFile(part.getParentFile(),entry.name);if(!part.renameTo(target))throw new IOException("无法完成下载文件");uri=Uri.fromFile(target);}}else if("content".equals(uri.getScheme())){if(Build.VERSION.SDK_INT>=29&&"media".equals(uri.getAuthority())){ContentValues values=new ContentValues();values.put(MediaStore.MediaColumns.DISPLAY_NAME,entry.name);values.put(MediaStore.MediaColumns.IS_PENDING,0);if(getContentResolver().update(uri,values,null,null)<=0)throw new IOException("无法完成旧版下载文件");}else{File legacy=legacyDocumentFile(uri);if(legacy==null)throw new IOException("旧版目录授权记录无法转换为真实文件路径，请重新下载");File target=legacy;if(legacy.getName().endsWith(".part")){target=uniqueFile(legacy.getParentFile(),entry.name);if(!legacy.renameTo(target))throw new IOException("无法完成旧版下载文件");}uri=Uri.fromFile(target);}}else throw new IOException("未知下载目标");entry.target=uri;entry.uriString=uri.toString();persistDownloadHistory(entry);}catch(Exception error){entry.state=DOWNLOAD_FAILED;entry.error="下载完成但文件保存失败："+friendlyError(error);updateDownloadUi(entry);if(error instanceof SecurityException||downloadStorageFailure(entry.error))requestDownloadStorageRecovery(entry,"文件已经下载完成，但当前路径缺少文件管理权限："+friendlyError(error),()->retryFinalizeDownload(entry));}}
  static File uniqueFile(File directory,String name){File file=new File(directory,name);if(!file.exists())return file;int dot=name.lastIndexOf('.');String base=dot>0?name.substring(0,dot):name,ext=dot>0?name.substring(dot):"";for(int i=1;;i++){file=new File(directory,base+" ("+i+")"+ext);if(!file.exists())return file;}}
  static String safeName(String name){String value=name.replaceAll("[\\/:*?\"<>|]","_").trim();return value.isEmpty()?"download.bin":value;}

  //—— 工具屏媒体资源：噪音 / TTS（退出工具页或销毁时释放）——
  /** v1.15.0 精修31：白/棕噪音统一入口（白=原始白噪声，棕=积分低通） */
  public void startNoise(boolean white){
    stopNoise();
    int sampleRate=44100,len=sampleRate*4;// 4 秒循环
    byte[] buffer=new byte[len];
    double last=0;
    for(int i=0;i<len;i++){double w=Math.random()*2-1;
      if(white){buffer[i]=(byte)(int)(w*127);continue;}
      last=(last+0.02*w)/1.02;double brown=last*3.5;if(brown>1)brown=1;if(brown<-1)brown=-1;buffer[i]=(byte)(int)(brown*127);}
    int minBuf=android.media.AudioTrack.getMinBufferSize(sampleRate,android.media.AudioFormat.CHANNEL_OUT_MONO,android.media.AudioFormat.ENCODING_PCM_8BIT);
    noiseTrack=new android.media.AudioTrack(android.media.AudioManager.STREAM_MUSIC,sampleRate,android.media.AudioFormat.CHANNEL_OUT_MONO,android.media.AudioFormat.ENCODING_PCM_8BIT,Math.max(minBuf,len),android.media.AudioTrack.MODE_STATIC);
    noiseTrack.write(buffer,0,len);
    noiseTrack.setLoopPoints(0,len,-1);
    noiseTrack.play();
  }
  public void stopNoise(){if(noiseTrack!=null){try{noiseTrack.stop();noiseTrack.release();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}noiseTrack=null;}}
  public void speakTts(String value){
    if(value==null||value.trim().isEmpty()){showNotice("先输入文字",true);return;}
    if(ttsEngine==null){ttsEngine=new android.speech.tts.TextToSpeech(this,status->{if(status==android.speech.tts.TextToSpeech.SUCCESS){ttsEngine.setLanguage(Locale.SIMPLIFIED_CHINESE);ttsEngine.setSpeechRate(ttsRate);ttsEngine.speak(value,android.speech.tts.TextToSpeech.QUEUE_FLUSH,null,"dfwx");}else showNotice("TTS 初始化失败",true);});return;}
    ttsEngine.setSpeechRate(ttsRate);
    ttsEngine.speak(value,android.speech.tts.TextToSpeech.QUEUE_FLUSH,null,"dfwx");
  }
  float ttsRate=1f;// v1.15.0 精修32：语速记忆
  public void setTtsRate(float rate){
    ttsRate=rate;
    if(ttsEngine!=null)ttsEngine.setSpeechRate(rate);
  }
  public void stopTts(){if(ttsEngine!=null)ttsEngine.stop();}
  void releaseToolMedia(){
    stopNoise();stopTts();
    if(ttsEngine!=null){try{ttsEngine.shutdown();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}ttsEngine=null;}
    if(torchCleanup!=null){try{torchCleanup.run();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}torchCleanup=null;}
    if(levelCleanup!=null){try{levelCleanup.run();}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}levelCleanup=null;}// v1.2.2:水平仪独立清理槽
    levelViewRef=null;
  }
  @android.annotation.SuppressLint("WrongConstant") @Override protected void onActivityResult(int request,int result,Intent data){
    super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null)return;Uri uri=data.getData();if(uri==null)return;
    if(request==IMPORT_RULES){io.execute(()->{try{importSourceRules(readTextUri(uri));}catch(Exception error){showNotice("导入失败："+friendlyError(error),true);}});return;}
    if(request==EXPORT_RULES)io.execute(()->{try{writeTextUri(uri,core.exportSourceRules());showNotice("源规则已导出",false);}catch(Exception error){showNotice("导出失败："+friendlyError(error),true);}});
    if(request==TOOL_PICK_IMAGE)io.execute(()->runImageCompress(uri));
    if(request==TOOL_PICK_IMAGE2){Runnable r=pendingToolColorImagePick;pendingToolColorImagePick=null;if(r!=null)io.execute(r);}
  }

        @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==TOOL_MIC_PERMISSION){Runnable r=pendingMicAction;pendingMicAction=null;if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&r!=null)r.run();else showNotice("未获得麦克风权限，无法测量噪音",true);return;}if(request==AI_PERMISSION){java.util.List<String> list=pendingAiPermissions;int index=pendingAiIndex;Runnable proceed=pendingAiProceed;pendingAiPermissions=null;pendingAiProceed=null;if(list==null||proceed==null)return;requestNextAiPermission(list,index+1,proceed);return;}if(request==STARTUP_STORAGE_PERMISSION){if(storageAccessGranted())runPendingStorageAccessActions();else{pendingStorageAccessActions.clear();showNotice("未授予存储权限，下次启动会再次询问",false);}ui.post(this::maybeRequestBatteryExemption);return;}if(request==DIRECT_STORAGE_PERMISSION){if(storageAccessGranted())runPendingStorageAccessActions();else{pendingStorageAccessActions.clear();showNotice("未获得存储权限，无法访问下载目录",true);}return;}if(request!=STORAGE_PERMISSION)return;boolean granted=results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED;if(pendingPermissionBatch!=null){List<Models.Item> items=pendingPermissionBatch;pendingPermissionBatch=null;if(granted)startBulkDownloads(items);else showNotice("未获得存储权限，无法批量写入 Download",true);return;}if(pendingPermissionDownload!=null){Models.Item item=pendingPermissionDownload;boolean auto=pendingPermissionAutoInstall;String expected=pendingPermissionUpdateVersion;pendingPermissionDownload=null;pendingPermissionAutoInstall=false;pendingPermissionUpdateVersion="";if(granted){if(expected.isEmpty())beginDownload(item,null,auto);else beginVerifiedUpdateDownload(item,expected);}else showNotice("未获得存储权限，无法写入 Download",true);return;}if(pendingRetryDownload!=null){PendingRetry pending=pendingRetryDownload;pendingRetryDownload=null;DownloadEntry entry=pending.entry;boolean current;synchronized(entry){current=entry.controlGeneration==pending.generation&&entry.state.equals(pending.state);}if(granted&&current)requestRetryDownload(entry);else if(!granted)showNotice("未获得存储权限，无法重试下载",true);}}

  void beginVerifiedUpdateDownload(Models.Item item,String expectedVersion){beginVerifiedUpdateDownloadGated(item,expectedVersion);}
  void beginVerifiedUpdateDownloadGated(Models.Item item,String expectedVersion){if(!ensureDirectStorageAuthorized(()->beginVerifiedUpdateDownloadGated(item,expectedVersion)))return;try{DownloadEntry entry=newDownloadEntry(item,null,true);entry.expectedUpdateVersion=expectedVersion;downloadEntries.add(entry);if(downloadsPage)renderDownloadFilters();persistDownloadHistory(entry);updateDownloadUi(entry);startEntryDownload(entry,entry.shareUrl);}catch(Exception error){String message=friendlyError(error);if(downloadStorageFailure(message)){requestManageAllFilesAccess("无法创建更新文件："+message,()->beginVerifiedUpdateDownloadGated(item,expectedVersion),false);return;}showNotice("无法创建更新文件："+message,true);}}
  int toastContentHeight(){if(toastLayer==null)return 0;int total=toastLayer.getPaddingTop()+toastLayer.getPaddingBottom();for(int i=0;i<toastLayer.getChildCount();i++){View child=toastLayer.getChildAt(i);if(child.getVisibility()==View.GONE)continue;ViewGroup.LayoutParams raw=child.getLayoutParams();int height=raw==null?child.getMeasuredHeight():raw.height;if(height<=0)height=child.getMeasuredHeight();total+=Math.max(0,height);if(raw instanceof LinearLayout.LayoutParams){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)raw;total+=lp.topMargin+lp.bottomMargin;}}return total;}
  void updateToastViewport(){if(toastScroll==null||toastLayer==null)return;int contentHeight=toastContentHeight();ViewGroup.LayoutParams raw=toastScroll.getLayoutParams();if(raw instanceof FrameLayout.LayoutParams){FrameLayout.LayoutParams params=(FrameLayout.LayoutParams)raw;int height=Math.min(dp(204),contentHeight);if(params.height!=height){params.height=height;toastScroll.setLayoutParams(params);}}toastScroll.setVisibility(contentHeight>0?View.VISIBLE:View.GONE);}
  static boolean showWebDownloadHistoryDialog(Activity host){MainActivity owner=ACTIVE_OWNER!=null?ACTIVE_OWNER:ACTIVE_INSTANCE.get();if(owner==null||host==null)return false;owner.showWebDownloadHistoryDialogNow(host);return true;}
  void showWebDownloadHistoryDialogNow(Activity host){final String[] batchPage={""};final AlertDialog[] dialogRef={null};LinearLayout panel=new LinearLayout(host);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(14),dp(4),dp(14),dp(8));LinearLayout header=new LinearLayout(host);header.setGravity(Gravity.CENTER_VERTICAL);TextView title=textFor(host,"下载历史",16,TEXT);title.setTypeface(AppFonts.bold(this));Button closeOrBack=new Button(host);closeOrBack.setTextColor(PRIMARY);closeOrBack.setTextSize(12);closeOrBack.setAllCaps(false);closeOrBack.setMinWidth(0);closeOrBack.setMinimumWidth(0);closeOrBack.setMinHeight(dp(44));closeOrBack.setMinimumHeight(dp(44));closeOrBack.setPadding(dp(10),0,dp(10),0);closeOrBack.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));header.addView(title,new LinearLayout.LayoutParams(0,dp(44),1));header.addView(closeOrBack,new LinearLayout.LayoutParams(dp(72),dp(44)));panel.addView(header,new LinearLayout.LayoutParams(-1,dp(44)));ScrollView scroll=new ScrollView(host);LinearLayout list=new LinearLayout(host);list.setOrientation(LinearLayout.VERTICAL);scroll.addView(list,new ScrollView.LayoutParams(-1,-2));panel.addView(scroll,new LinearLayout.LayoutParams(-1,Math.min(dp(520),Math.max(dp(220),host.getResources().getDisplayMetrics().heightPixels-dp(220)))));final Runnable[] render={null};render[0]=()->{list.removeAllViews();String batch=batchPage[0];boolean root=batch.isEmpty();closeOrBack.setText(root?"关闭":"返回");if(root){title.setText("下载历史 · "+downloadEntries.size());LinkedHashSet<String> batches=new LinkedHashSet<>();for(DownloadEntry entry:downloadEntries){if(!entry.batchId.isEmpty()){if(!batches.add(entry.batchId))continue;List<DownloadEntry> entries=downloadBatchEntries(entry.batchId);list.addView(webHistoryRow(host,batchDownloadTitle(entries)+"\n"+batchDownloadMetrics(entries),()->{batchPage[0]=entry.batchId;render[0].run();}),new LinearLayout.LayoutParams(-1,dp(72)));}else list.addView(webHistoryRow(host,entry.name+"\n"+downloadMetrics(entry),()->handleDownloadHistoryClick(entry)),new LinearLayout.LayoutParams(-1,dp(72)));}if(list.getChildCount()==0){TextView empty=textFor(host,"暂无下载历史",14,MUTED);empty.setGravity(Gravity.CENTER);list.addView(empty,new LinearLayout.LayoutParams(-1,dp(180)));}}else{List<DownloadEntry> entries=downloadBatchEntries(batch);title.setText(batchDownloadTitle(entries));for(DownloadEntry entry:entries)list.addView(webHistoryRow(host,entry.name+"\n"+downloadMetrics(entry),()->handleDownloadHistoryClick(entry)),new LinearLayout.LayoutParams(-1,dp(72)));if(entries.isEmpty())list.addView(textFor(host,"批量项目已不存在",13,MUTED),new LinearLayout.LayoutParams(-1,dp(72)));}};closeOrBack.setOnClickListener(v->{if(!batchPage[0].isEmpty()){batchPage[0]="";render[0].run();}else if(dialogRef[0]!=null)dialogRef[0].dismiss();});AlertDialog dialog=new AlertDialog.Builder(host).setView(panel).create();dialogRef[0]=dialog;render[0].run();dialog.show();Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));WindowManager.LayoutParams p=window.getAttributes();p.gravity=Gravity.CENTER;p.width=Math.min(dp(600),host.getResources().getDisplayMetrics().widthPixels-dp(28));p.height=WindowManager.LayoutParams.WRAP_CONTENT;window.setAttributes(p);}View content=dialog.findViewById(android.R.id.content),box=content;if(content instanceof ViewGroup&&((ViewGroup)content).getChildCount()>0)box=((ViewGroup)content).getChildAt(0);if(box!=null){box.setBackground(solidShape(SURFACE,22));box.setClipToOutline(true);box.setElevation(dp(10));}}
  TextView textFor(Context context,String value,int sp,int color){TextView view=new TextView(context);view.setText(value);view.setTextColor(color);view.setTextSize(sp);view.setGravity(Gravity.CENTER_VERTICAL);view.setTypeface(AppFonts.normal(context));return view;}
  TextView webHistoryRow(Context context,String value,Runnable click){TextView row=textFor(context,value,12,TEXT);row.setMaxLines(3);row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setPadding(dp(12),0,dp(12),0);row.setClickable(true);row.setFocusable(true);row.setBackground(filterRipple(new ColorDrawable(Color.TRANSPARENT)));row.setOnClickListener(v->click.run());return row;}
  void handleDownloadHistoryClick(DownloadEntry entry){if(entry==null)return;if(entry.state.equals(DOWNLOAD_FAILED))requestRetryDownload(entry);else if(entry.state.equals(DOWNLOAD_PAUSED))resumeDownload(entry);else if(entry.state.equals(DOWNLOAD_COMPLETED))installEntry(entry);else if(isDownloadActive(entry))pauseDownload(entry);}
  LinearLayout toastPanel(){LinearLayout panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(10),dp(8),dp(10),dp(8));panel.setBackground(solidShape(SURFACE,16));panel.setElevation(dp(6));return panel;}
  void addToastPanel(LinearLayout panel,int height){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(height));lp.setMargins(0,0,0,dp(5));toastLayer.addView(panel,lp);updateToastViewport();swipePanel(panel,()->dismissPanel(panel,null),null);toastScroll.post(()->{if(motionEnabled())toastScroll.smoothScrollTo(0,toastLayer.getHeight());else toastScroll.scrollTo(0,toastLayer.getHeight());});/* [DFW-54] 入场语言统一：旧版是**从左边横着滑进来**（translationX -260dp，210ms），
       全站只有下载卡是横向入场 —— 顶部通知条、弹窗、页面转场全是纵向，所以看起来"割裂"。
       现在改成和顶部通知条同一方向：从上往下落 8dp + 淡入，240ms，用站内统一的
       PathInterpolator(0.2,0,0,1)（= M3 emphasized 减速段）。
       退场 160ms 且方向相反 —— 规范自检项要求**入场时长 ≥ 退场时长**，
       旧版退场 230ms > 入场 210ms，是"进得急、走得慢"的廉价感来源。 */
if(motionEnabled()){panel.setAlpha(0f);panel.setTranslationY(-dp(8));panel.animate().alpha(1f).translationY(0).setDuration(250).setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f)).start();}else{panel.setAlpha(1f);panel.setTranslationY(0);}}
   void resetSwipePanel(View view){if(motionEnabled())view.animate().translationX(0).translationY(0).alpha(1f).setDuration(150).setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f)).start();else{view.setTranslationX(0);view.setTranslationY(0);view.setAlpha(1f);}}
   @android.annotation.SuppressLint("ClickableViewAccessibility") void swipePanel(View panel,Runnable dismiss,Runnable tap){swipePanel(panel,dismiss,tap,null);}
   @android.annotation.SuppressLint("ClickableViewAccessibility") void swipePanel(View panel,Runnable dismiss,Runnable tap,Runnable hold){panel.setClickable(true);panel.setOnClickListener(tap==null?null:view->tap.run());panel.setLongClickable(hold!=null);panel.setOnLongClickListener(view->{if(hold==null)return false;hold.run();return true;});panel.setOnTouchListener(new View.OnTouchListener(){float startX,startY;int gesture;boolean held;final Runnable longAction=()->{if(gesture==0&&hold!=null)held=panel.performLongClick();};public boolean onTouch(View view,MotionEvent event){switch(event.getActionMasked()){case MotionEvent.ACTION_DOWN:startX=event.getRawX();startY=event.getRawY();gesture=0;held=false;view.animate().cancel();view.postDelayed(longAction,ViewConfiguration.getLongPressTimeout());view.getParent().requestDisallowInterceptTouchEvent(true);return true;case MotionEvent.ACTION_MOVE:float dx=event.getRawX()-startX,dy=event.getRawY()-startY;if(gesture==0&&Math.max(Math.abs(dx),Math.abs(dy))>dp(5)){gesture=Math.abs(dx)>=Math.abs(dy)?1:2;view.removeCallbacks(longAction);}if(gesture==2){view.getParent().requestDisallowInterceptTouchEvent(false);return false;}float shift=Math.max(0,dx);view.setTranslationX(shift);view.setAlpha(Math.max(.35f,1f-shift/Math.max(1f,dp(260))));return true;case MotionEvent.ACTION_UP:view.removeCallbacks(longAction);view.getParent().requestDisallowInterceptTouchEvent(false);if(held){resetSwipePanel(view);return true;}float distance=Math.max(0,event.getRawX()-startX);if(gesture==1&&distance>dp(72)){dismiss.run();return true;}resetSwipePanel(view);if(gesture==0)view.performClick();return true;default:view.removeCallbacks(longAction);view.getParent().requestDisallowInterceptTouchEvent(false);resetSwipePanel(view);return true;}}});}
  void dismissPanel(View panel,Runnable after){Runnable remove=()->{if(panel.getParent()==toastLayer)toastLayer.removeView(panel);updateToastViewport();if(after!=null)after.run();};if(!motionEnabled()){remove.run();return;}panel.animate().cancel();panel.animate().alpha(0f).translationY(-dp(8)).setDuration(150).setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f)).withEndAction(remove).start();}
  void updateDownloadUi(DownloadEntry entry){
    persistDownloadHistory(entry);boolean post=false;synchronized(dirtyDownloadUi){dirtyDownloadUi.add(entry);if(!downloadUiFramePosted){downloadUiFramePosted=true;post=true;}}if(post)ui.postDelayed(this::drainDownloadUi,downloadUiIntervalMs());
  }
  long downloadUiIntervalMs(){int active=0;for(DownloadEntry entry:downloadEntries)if(isDownloadActive(entry))active++;return active>=64?500L:active>=24?250L:DOWNLOAD_UI_INTERVAL_MS;}
  void drainDownloadUi(){if(isFinishing()||isDestroyed())return;// DFWX-STAB-001：onDestroy close 链期间组件回调排入的晚到 UI 任务一律丢弃
    List<DownloadEntry> changed;synchronized(dirtyDownloadUi){changed=new ArrayList<>(dirtyDownloadUi);dirtyDownloadUi.clear();downloadUiFramePosted=false;}refreshDownloadHeaderMenu();boolean rebuild=false;for(DownloadEntry entry:changed){boolean stateChanged=!entry.state.equals(entry.uiState);entry.uiState=entry.state;if(stateChanged)noteDownloadOutcome(entry);boolean parsing=entry.state.equals(DOWNLOAD_RESOLVING);if(downloadsPage){if(!entry.batchId.isEmpty()){if(stateChanged)rebuild=true;else updateBatchDownloadRow(entry.batchId);}else{boolean shown=downloadLabels.containsKey(entry),matches=downloadMatchesCurrentView(entry);if(shown!=matches)rebuild=true;else if(shown){TextView label=downloadLabels.get(entry);if(label!=null&&label.getParent()!=null)label.setText(downloadMetrics(entry));ProgressBar bar=downloadBars.get(entry);if(bar!=null&&bar.getParent()!=null){bar.setIndeterminate(parsing);if(!parsing){bar.setIndeterminate(false);bar.setProgress(entry.percent);}}if(stateChanged){LinearLayout actions=downloadActions.get(entry);if(actions!=null&&actions.getParent()!=null)bindDownloadActions(entry,actions);View row=downloadRows.get(entry);if(row!=null&&row.getParent()!=null)bindDownloadRowDescription(entry,row);}}}}if(rebuild)renderDownloads(downloadQuery);}}
  /**
   * [DFWX] DFW-66：**更新链路专用的顶部通知条**（用户要求"像手机收到的消息一样从顶部滑下来"）。
   *
   * 与 `showNotice` 的关系：`showNotice` 是**全站 227 处共用的旧底座**（飘在顶部的小黑胶囊，用户嫌丑）。
   * 按用户 2026-09-30 的要求，**先只把"更新"这条链路换掉做测试**，
   * 验证满意后再决定要不要全站推广——所以这里是**新增**一条路径，不是直接改底座。
   */
  NoticeBanner updateNoticeBanner;

  void showUpdateNotice(String message,boolean longLived){
    final String msg=(message==null||message.trim().isEmpty())?"操作未完成":message;
    // [DFW-73] 与 showNotice 共用同一个构造点（唯一差异是图标），避免两处样式各自漂移。
    runOnUiThread(()->showTopBanner(msg,longLived?5000:2500,R.drawable.ic_refresh));
  }

  int statusBarInset(){
    if(Build.VERSION.SDK_INT>=30){
      android.view.WindowInsets insets=getWindow()==null?null:getWindow().getDecorView().getRootWindowInsets();
      if(insets!=null){
        int top=insets.getInsets(android.view.WindowInsets.Type.statusBars()).top;
        if(top>0)return top;
      }
    }
    int id=getResources().getIdentifier("status_bar_height","dimen","android");
    return id>0?getResources().getDimensionPixelSize(id):dp(24);
  }

  /**
   * [DFW-73] **全站提示统一成"顶部通知条"**（用户 2026-10-01 真机截图反馈）。
   *
   * 用户原话：
   * > "图一图二分别展现了它的样子和它滑入的时候那个特别烂的动画效果。我不是告诉你了，
   * >  让你全部都用图三的那个顶部的样式吗？那个顶部的那种出现方式、那个样子，
   * >  才是我们应该把所有弹窗都支持的。后续不管弹出什么，都是用这个弹出来的。"
   *
   * 图三 = DFW-66 给"更新链路"单独做的那条 `NoticeBanner`（从屏幕顶部像消息通知一样滑下来、
   * 无遮罩、可上/左/右滑走、文字多寡自适应）。当时刻意只换了更新一条链路做试点
   * （见 `showUpdateNotice` 的注释），**现在用户明确要求全站推广**，所以 `showNotice`
   * 这个 227 处共用的底座直接换成同一条。
   *
   * 旧底座（飘在右上角的 250dp 小黑胶囊 + 从**左边**横滑进来 + 淡入）两个毛病：
   *  - 从左侧横滑 + 半透明淡入，跟"通知"的语义完全不搭，用户看到的就是"割裂感"；
   *  - 宽度只有 250dp 且贴右上角，会压住下面的内容（截图里正好压住设置页的搜索框）。
   */
  public void showNotice(String message,boolean longLived){
    final String msg=(message==null||message.trim().isEmpty())?"操作未完成":message;// v1.5.1：空文字通知只剩边框的兜底
    runOnUiThread(()->showTopBanner(msg,longLived?5000:2500,R.drawable.ic_notifications));
  }

  /** 当前正在显示的顶部通知条。全站共用：同一时刻只留一条，避免叠罗汉。 */
  NoticeBanner activeNoticeBanner;

  /**
   * [DFW-73] 顶部通知条的**唯一构造点**（`showNotice` 与 `showUpdateNotice` 共用）。
   *
   * 之所以抽出来：两处原本各写一份卡片构造，改样式时必然只改一处 —— 那正是"两个组件看着不一样"的来源。
   * 图标是唯一的差异（普通提示=铃铛、更新=刷新）。
   */
  void showTopBanner(String message,int durationMs,int iconRes){
    if(isFinishing()||isDestroyed())return;
    final String msg=(message==null||message.trim().isEmpty())?"操作未完成":message;
    /*
     * [DFW-101] 记面包屑（**纯内存，不落盘**）。
     *
     * 放在这里而不是 `showNotice` 里：这个方法是**顶部通知条唯一的构造点**
     * （`showNotice`/`showUpdateNotice`/下载完成/错误提示四条路径都汇到这里，
     * 见本方法上方的注释与 DFW-73）。所以这一行等于自动记下"应用对用户说过的每一句话"，
     * 不需要在 139 个调用点上各插一次。
     *
     * 用户回头报"卡住了/没反应"时，这条时间线能立刻区分
     * 「什么都没提示」和「提示了但用户没看懂」——那是两种完全不同的故障。
     * 只进内存是因为提示太频繁，落盘会把真正的事故挤出去。
     *
     * [Lead 修正 2026-10-02] 事件名用 `notice` 而不是 `banner`：
     * 埋点位置确实在"通知条构造点"，但**日志里该记的是发生了什么、不是用什么控件显示的**——
     * 哪天通知条换成别的控件，`banner` 这个名字就变成假话了。
     * 另外测试侧三处（`DfLogWiringJvmTest.kt:119/130`、`DfLogJvmTest.kt:296`）
     * 都按 `notice` 写，代码这一处是唯一的离群值。
     */
    DfLog.breadcrumb("ui","notice","msg",msg);
    final ViewGroup container=sheetHost();
    if(container==null)return;
    // 先把旧条摘下来再 dismiss：dismiss 是带动画的异步过程，它的 onDismissed 回调会在几百毫秒后
    // 才跑；如果那时才去清引用，会把**这一条新的**清掉（旧条自己都不知道新条已经上来了）。
    NoticeBanner previous=activeNoticeBanner;
    activeNoticeBanner=null;
    if(previous!=null)previous.dismiss();
    LinearLayout card=new LinearLayout(this);
    card.setOrientation(LinearLayout.HORIZONTAL);
    card.setGravity(Gravity.CENTER_VERTICAL);
    card.setBackground(solidShape(SURFACE,18));
    card.setElevation(dp(10));
    card.setPadding(dp(14),dp(12),dp(14),dp(12));
    ImageView icon=new ImageView(this);
    icon.setImageResource(iconRes);
    icon.setColorFilter(PRIMARY);
    icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    card.addView(icon,new LinearLayout.LayoutParams(dp(20),dp(20)));
    TextView label=text(msg,14,TEXT);
    // 文字多寡都要适配：容器 wrap_content 自动长高，正文最多 4 行、超出省略号；
    // 内边距**不随行数变**，否则一行和四行看着会像两个不同的组件。
    label.setMaxLines(NoticeBanner.maxLines());
    label.setEllipsize(android.text.TextUtils.TruncateAt.END);
    label.setLineSpacing(dp(2),1f);
    LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(0,-2,1);
    labelParams.leftMargin=dp(10);
    card.addView(label,labelParams);
    FrameLayout.LayoutParams cardParams=new FrameLayout.LayoutParams(-1,-2);
    cardParams.leftMargin=dp(12);
    cardParams.rightMargin=dp(12);
    card.setLayoutParams(cardParams);
    final NoticeBanner[] holder=new NoticeBanner[1];
    holder[0]=NoticeBanner.create(this,container,card,
        ()->{if(activeNoticeBanner==holder[0])activeNoticeBanner=null;},
        motionEnabled(),statusBarInset()+dp(8),durationMs);
    activeNoticeBanner=holder[0];
    activeNoticeBanner.show();
  }
  void postFolderIconPrefetch(List<Models.Item> items,int start,int limit){if(start>=items.size()||limit<=0)return;int session=navigationSession;List<Models.Item> snapshot=new ArrayList<>(items);ui.postDelayed(()->{if(session!=navigationSession||pageKind!=3)return;for(int i=start;i<Math.min(start+limit,snapshot.size());i++)requestImage(snapshot.get(i).iconUrl,null);},180);}
  void loadImage(String url,ImageView view){if(restoringFolderState){loadCachedFolderImage(url,view);return;}requestImage(url,view);}
  void loadCachedFolderImage(String url,ImageView view){if(view==null||url==null||url.isEmpty())return;view.setTag(url);Bitmap bitmap=activeFolderState==null?null:activeFolderState.pinnedIcons.get(url);if(bitmap==null)bitmap=imageCache.get(url);if(bitmap!=null&&url.equals(view.getTag())){view.setImageBitmap(bitmap);return;}synchronized(imageLock){List<java.lang.ref.WeakReference<ImageView>> waiters=imageWaiters.get(url);if(waiters!=null)waiters.add(new java.lang.ref.WeakReference<>(view));}}
  /** 远程图标统一解码入口（2026-09-24 性能审计#6，官方两段式 inSampleSize）：先解边界算采样率再解真身，
      目标 144px=48dp@3x 覆盖全部图标展示尺寸；典型 512px 原图内存降 8-16 倍，弱机 GC 压力同步下降。 */
  Bitmap decodeIcon(byte[] data){BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(data,0,data.length,opts);opts.inSampleSize=calculateIconInSampleSize(opts,144,144);opts.inJustDecodeBounds=false;return BitmapFactory.decodeByteArray(data,0,data.length,opts);}
  /** 官方算法：取保持宽高都不小于目标尺寸的最大 2 的幂采样率（developer.android.com load-bitmap） */
  static int calculateIconInSampleSize(BitmapFactory.Options options,int reqWidth,int reqHeight){int height=options.outHeight,width=options.outWidth,inSampleSize=1;if(height>reqHeight||width>reqWidth){int halfHeight=height/2,halfWidth=width/2;while(halfHeight/inSampleSize>=reqHeight&&halfWidth/inSampleSize>=reqWidth)inSampleSize*=2;}return inSampleSize;}
  static byte[] readAllBytes(InputStream in) throws Exception{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[16384];int read;while((read=in.read(buffer))!=-1)out.write(buffer,0,read);return out.toByteArray();}
  void requestImage(String url,ImageView view){if(url==null||url.isEmpty())return;if(view!=null)view.setTag(url);Bitmap cached=imageCache.get(url);if(cached!=null){if(view!=null&&url.equals(view.getTag()))view.setImageBitmap(cached);return;}
    // DFW-40：最近失败过的图标在 TTL 内直接跳过，不再发起网络请求（负缓存），
    // 否则弱网下每次滚动回来都要重新等一次 3 秒超时。
    if(imageRecentlyFailed(url))return;
    boolean start=false;synchronized(imageLock){List<java.lang.ref.WeakReference<ImageView>> waiters=imageWaiters.get(url);if(waiters==null){waiters=new ArrayList<>();imageWaiters.put(url,waiters);start=true;}if(view!=null)waiters.add(new java.lang.ref.WeakReference<>(view));}if(!start)return;imageIo.execute(()->{HttpURLConnection c=null;Bitmap bitmap=null;try{c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(3000);c.setReadTimeout(5000);c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 15; Mobile) AppleWebKit/537.36 Chrome/138 Mobile Safari/537.36");c.setRequestProperty("Referer","https://www.lanzouw.com/");try(InputStream in=c.getInputStream()){bitmap=decodeIcon(readAllBytes(in));if(bitmap!=null)imageCache.put(url,bitmap);}}catch(Exception ignored){android.util.Log.w("MainActivity", "MainActivity Exception: "+ignored.getMessage(), ignored);}finally{if(c!=null)c.disconnect();}
      // DFW-40：解不出图（404/超时/格式异常）也记一笔，避免反复重试同一个坏 URL
      if(bitmap==null)rememberImageFailure(url);List<java.lang.ref.WeakReference<ImageView>> targets;synchronized(imageLock){targets=imageWaiters.remove(url);}if(bitmap!=null&&targets!=null)queueImageDelivery(url,bitmap,targets);});}
  void queueImageDelivery(String url,Bitmap bitmap,List<java.lang.ref.WeakReference<ImageView>> targets){boolean post=false;synchronized(imageLock){imageDeliveries.addLast(new ImageDelivery(url,bitmap,targets));if(!imageDeliveryPosted){imageDeliveryPosted=true;post=true;}}if(post)ui.post(this::drainImageDeliveries);}
  void drainImageDeliveries(){List<ImageDelivery> ready=new ArrayList<>();boolean more;synchronized(imageLock){while(ready.size()<IMAGE_UI_CHUNK&&!imageDeliveries.isEmpty())ready.add(imageDeliveries.removeFirst());more=!imageDeliveries.isEmpty();if(!more)imageDeliveryPosted=false;}for(ImageDelivery delivery:ready)for(java.lang.ref.WeakReference<ImageView> reference:delivery.targets){ImageView target=reference.get();if(target!=null&&delivery.url.equals(target.getTag()))target.setImageBitmap(delivery.bitmap);}if(more)ui.postDelayed(this::drainImageDeliveries,16);}
  void error(Exception e){runOnUiThread(()->{if(progress!=null)progress.setVisibility(View.GONE);showNotice(friendlyError(e),true);});}
}





