package cc.nkbr.lanzouplus;

import android.app.Application;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * DFWX-STAB-001：下载回调 ownership / generation 防竞态判定回归。
 *
 * DownloadEntry.transferOwnedBy / transferOwnerIs 定义了 SegmentDownloader
 * 回调落地前（须持 entry 锁）的过期判定规则：
 * - 进度/完成/失败回调：generation 与 downloader 双重匹配才算当前所有者；
 * - paused 终态回调：与 transferOwnedBy 同规则（cancel 已 ++generation，必须拒）；
 * - cancelled 终态回调：仅 owner 匹配（cancelDownload 会先 ++generation，
 *   cancelled 回调的旧 generation 永远过期，必须放行才能清理 partial 并推进批次）。
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class DownloadEntryTransferTest {

  private static Application context() {
    return RuntimeEnvironment.getApplication();
  }

  private static MainActivity.DownloadEntry entry() {
    return new MainActivity.DownloadEntry();
  }

  private static SegmentDownloader downloader() {
    return new SegmentDownloader(context());
  }

  @Test
  public void currentOwnerCallbacksPassOwnershipCheck() {
    MainActivity.DownloadEntry entry = entry();
    entry.state = MainActivity.DOWNLOAD_RUNNING;
    SegmentDownloader owner = downloader();
    entry.downloader = owner;
    int generation = entry.controlGeneration;
    assertTrue(entry.transferOwnedBy(generation, owner));
    assertTrue(entry.transferOwnerIs(owner));
  }

  @Test
  public void cancelledCallbackSurvivesGenerationBumpButNotOwnerSwap() {
    // cancelDownload 会 ++controlGeneration 后等待 cancelled 终态
    MainActivity.DownloadEntry entry = entry();
    SegmentDownloader owner = downloader();
    entry.downloader = owner;
    int generation = entry.controlGeneration;
    entry.controlGeneration++; // 模拟 cancelDownload
    assertFalse("cancel 后旧 paused/completed 回调必须被拒",
        entry.transferOwnedBy(generation, owner));
    assertTrue("cancelled 回调只看 owner，cancel 自身的 ++ 不得拒掉自己",
        entry.transferOwnerIs(owner));
  }

  @Test
  public void retryReplacesOwnerAndRejectsEveryLegacyCallback() {
    // pause -> resume(retry)：retryDownload 会 ++generation 并替换 downloader
    MainActivity.DownloadEntry entry = entry();
    entry.state = MainActivity.DOWNLOAD_RUNNING;
    SegmentDownloader oldOwner = downloader();
    entry.downloader = oldOwner;
    entry.stopRequested = true; // pauseDownload 置位
    int generation = entry.controlGeneration;

    // resume(retry)：generation++，换新 downloader，stopRequested 复位
    entry.controlGeneration++;
    SegmentDownloader newOwner = downloader();
    entry.downloader = newOwner;
    entry.stopRequested = false;

    assertFalse("旧 paused 回调不得把新下载覆盖为 PAUSED",
        entry.transferOwnedBy(generation, oldOwner));
    assertFalse("旧 cancelled 回调不得清理新下载的 partial",
        entry.transferOwnerIs(oldOwner));
    assertTrue("新回调正常放行", entry.transferOwnedBy(entry.controlGeneration, newOwner));
  }

  @Test
  public void pausedCallbackLandsAfterPauseRequest() {
    // pauseDownload 置 stopRequested=true 但不动 generation/downloader，
    // paused 终态回调必须仍能落地（用于更新已下载字节并落 PAUSED 态）
    MainActivity.DownloadEntry entry = entry();
    entry.state = MainActivity.DOWNLOAD_RUNNING;
    SegmentDownloader owner = downloader();
    entry.downloader = owner;
    int generation = entry.controlGeneration;
    entry.stopRequested = true; // pauseDownload 置位
    assertTrue(entry.transferOwnedBy(generation, owner));
  }
}
