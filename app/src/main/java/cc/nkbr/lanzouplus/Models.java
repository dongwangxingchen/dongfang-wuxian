package cc.nkbr.lanzouplus;

import java.util.*;

final class Models {
  static final byte SOURCE_OFFICIAL=0,SOURCE_COMPOSITE=1,SOURCE_SINGLE=2;
  static final byte MEMBER_UNKNOWN=0,MEMBER_FILE=1,MEMBER_DIRECTORY=2,MEMBER_FOLDER=3,MEMBER_REMOTE_FOLDER=4;
  static final class Item {
    String title="", url="", shareUrl="", size="", time="", iconUrl="", source="", password="", description="", folderId="", sourceId="", error="";
    boolean folder, sourceEntry;
  }
  static final class Folder {
    String title="", publisher="", avatarUrl="", description="", url="", password="", folderId="";
    int page=1, apiFolderCount, failedMembers;
    boolean hasMore, remoteSearch;
    long nextPageReadyAt;
    final List<Item> items=new ArrayList<>();
  }
  static final class SourceSearch {
    boolean remoteAvailable, remoteUsed;
    final List<Item> items=new ArrayList<>();
  }
  static final class SourceMember {
    String id="", parentId="", title="", url="", password="", iconUrl="", size="", time="", description="", error="";
    byte kind=MEMBER_UNKNOWN;
    boolean searchable, lightweight, metadataLoaded, iconLoaded, detailsLoaded;
    long refreshedAt;
  }
  static final class Source {
    String id="", nodeId="", title="", url="", password="", error="", publisher="", avatarUrl="", description="", originPath="", originUrl="";
    byte kind=SOURCE_OFFICIAL;
    boolean searchable, overlay, metadataOverride, childDirectory;
    final List<SourceMember> members=new ArrayList<>();
  }
  static final class SourceTestResult {
    final Source source;
    final String originalUrl;
    final boolean success, userSource, applied;
    SourceTestResult(Source source,String originalUrl,boolean success,boolean userSource,boolean applied){this.source=source;this.originalUrl=originalUrl;this.success=success;this.userSource=userSource;this.applied=applied;}
  }
  interface SourceTestProgress {
    void onResult(SourceTestResult result,int done,int total);
    default void onMember(String sourceId,String memberTitle,int done,int total,boolean success) {}
  }
  /** Per-search tuning. LanzouCore consumes a clamped copy for every run. */
  static final class SearchOptions {
    static final int MODE_MIXED=0,MODE_API=1,MODE_DIRECTORY=2,MODE_INDEX=3;
    static final int MASK_API=1,MASK_DIRECTORY=2,MASK_INDEX=4,MASK_ALL=MASK_API|MASK_DIRECTORY|MASK_INDEX;
    int concurrency=0;
    /** Fair active-source time slice; 0 keeps an active source until it finishes. */
    long sourceSwitchDelayMillis=0L;
    boolean untilLastPage=true;
    /** Search every nested folder discovered below a source. Session-only UI option. */
    boolean recursiveFolders;
    /** Directory fuzzy match: exact contains first, then ordered subsequence. API search ignores it. */
    boolean fuzzyMatching;
    /** Legacy single-mode view; modeMask is authoritative for multi-select search backends. */
    int mode=MODE_MIXED,modeMask=MASK_ALL;
    int maxPages;

    SearchOptions() {}
    SearchOptions(int concurrency,long sourceSwitchDelayMillis,boolean untilLastPage){
      this(concurrency,sourceSwitchDelayMillis,untilLastPage,0);
    }

    SearchOptions(int concurrency,long sourceSwitchDelayMillis,boolean untilLastPage,int maxPages){
      this.concurrency=concurrency;
      this.sourceSwitchDelayMillis=sourceSwitchDelayMillis;
      this.untilLastPage=untilLastPage;
      this.maxPages=maxPages;
    }

    SearchOptions withRecursiveFolders(boolean value){
      recursiveFolders=value;
      return this;
    }

    SearchOptions withMode(int value){
      mode=value<MODE_MIXED||value>MODE_INDEX?MODE_MIXED:value;
      modeMask=maskForMode(mode);
      return this;
    }

    SearchOptions withModeMask(int value){
      modeMask=normalizeModeMask(value);
      mode=modeForMask(modeMask);
      return this;
    }

    SearchOptions withFuzzyMatching(boolean value){
      fuzzyMatching=value;
      return this;
    }

    static int normalizeModeMask(int value){int mask=value&MASK_ALL;return mask==0?MASK_DIRECTORY:mask;}
    static int maskForMode(int value){switch(value){case MODE_API:return MASK_API;case MODE_DIRECTORY:return MASK_DIRECTORY;case MODE_INDEX:return MASK_INDEX;default:return MASK_ALL;}}
    static int modeForMask(int mask){mask=normalizeModeMask(mask);return mask==MASK_API?MODE_API:mask==MASK_DIRECTORY?MODE_DIRECTORY:mask==MASK_INDEX?MODE_INDEX:MODE_MIXED;}
    boolean apiEnabled(){return (modeMask&MASK_API)!=0;}
    boolean directoryEnabled(){return (modeMask&MASK_DIRECTORY)!=0;}
    boolean indexEnabled(){return (modeMask&MASK_INDEX)!=0;}
    boolean apiOnly(){return modeMask==MASK_API;}
    boolean directoryOnly(){return modeMask==MASK_DIRECTORY;}
    /** Never admits network work; callers return only persisted search and directory indexes. */
    boolean indexOnly(){return modeMask==MASK_INDEX;}

    SearchOptions normalized(){
      long sourceSlice=sourceSwitchDelayMillis==0?0L:Math.max(1000L,Math.min(60000L,sourceSwitchDelayMillis));
      int mask=normalizeModeMask(modeMask);
      return new SearchOptions(Math.max(0,concurrency),sourceSlice,untilLastPage,Math.max(0,Math.min(1000,maxPages))).withRecursiveFolders(recursiveFolders).withModeMask(mask).withFuzzyMatching((mask&MASK_DIRECTORY)!=0&&fuzzyMatching);
    }
  }
  /**
   * [DFW-36 2026-10-04] 一个源失败时，**为什么失败**。
   *
   * 为什么需要它：在这之前，失败只上报了「哪个源」（`onFailure(String)`），
   * 没有上报原因。用户看到「已完成 · 3 源异常：xxx、yyy、zzz」时，
   * 唯一能做的就是**去删源** —— 而这正是历史上踩过的坑：
   * `lessons.md` 2026-09-26 记录过一次「源失效」误判翻案，
   * 当时 84 个源被判失效，真实原因却是默认 UA 被 CDN 拉黑 + 解锁请求少发字段。
   * 源是好的，差点被删掉。
   *
   * 所以这个枚举的每一档都带**用户下一步该做什么**，而不是给源下判决书。
   * 判定逻辑见 `LanzouCore.classifyFailure(Throwable)`，那里写清了每档的依据。
   */
  enum FailureKind {
    /** 需要访问密码，或密码不对。用户能做：填/改密码后重试。 */
    PASSWORD("需要密码","这个源要密码，或者密码不对。填对密码再试一次。"),
    /** 被蓝奏限频。用户能做：等一会儿再试。 */
    RATE_LIMIT("被限频","访问太快被蓝奏拦了。等几分钟再试，不用管它。"),
    /** 超时。用户能做：重试。 */
    TIMEOUT("超时","连接超时了。网络慢或者源站忙，重试一下。"),
    /** 网络不通 / 证书问题。用户能做：换网络。 */
    NETWORK("网络异常","网络或证书出了问题。换个网络（比如切流量）再试。"),
    /** 用户自己取消的。用户能做：无。 */
    CANCELLED("已取消","你自己取消的，不是出错。"),
    /** 分享确实没了 / 文件不存在。用户能做：换别的源。 */
    GONE("分享已失效","这个分享确实被删了或不存在了。换一个源找找。"),
    /** 页面结构变了（我们能修）。用户能做：反馈给我们。 */
    PARSE("页面结构变了","蓝奏的页面格式变了，这是**我们这边要修的**，不是你的问题。"),
    /** 搜索预算用完（还没扫完就被截断）。用户能做：重试。 */
    BUDGET("搜索超时截断","这次搜索时间用完了，这个源还没扫完。重试一下就行。"),
    /** 其他。用户能做：反馈给我们。 */
    UNKNOWN("未知问题","出了个我们没归类的问题。反馈给我们，我们去看。");

    /** 短标签，用于列表里一行显示。 */
    public final String label;
    /** 一句话说清「这是怎么回事 + 你该做什么」。刻意不写「源已失效」。 */
    public final String advice;

    FailureKind(String label,String advice){this.label=label;this.advice=advice;}
  }
  interface Progress {
    void onProgress(int done,int total,int found,String current);
    /** Logical sources currently scheduled; independent from the bounded HTTP worker count. */
    default void onActivity(int active,int total,String current) {}
    /** Completed API/directory work units; used for smooth progress without redefining source completion. */
    default void onWorkProgress(int doneUnits,int totalUnits) {}

    /**
     * Called from a search worker as soon as one source has produced new,
     * de-duplicated items. Implementations that touch views must marshal the
     * callback to the Android main thread.
     */
    default void onBatch(List<Item> batch) {}
    default void onItemUpdated(Item item) {}
    /** totalPagesSeen is global to one search and is monotonic across workers. */
    default void onPage(String source,int page,int pageItems,int sourceFound,int totalPagesSeen) {}
    default boolean isCancelled(){return false;}
    /** Waits at a source/page boundary; false means the search was superseded. */
    default boolean awaitIfPaused(){return !isCancelled();}
    default void onFailure(String current) {}
    /**
     * [DFW-36 2026-10-04] `onFailure` 的增强版：除了「哪个源」，还带「为什么」。
     *
     * 保留 `onFailure` 不动，是因为它已经被 `MainActivity` 之外的实现者用过；
     * 这里是**新增**的 default 方法，不破坏任何现有实现。
     * 两个都会触发，实现方按需覆写。
     */
    default void onSourceFailure(String current,FailureKind kind) {}
    /** A full directory scan for this logical source completed and may advance the persisted index region. */
    default void onIndexSource(String sourceId,String current) {}
  }
  interface FolderProgress {
    void onProgress(int folders,int files,String current);
  }
}
