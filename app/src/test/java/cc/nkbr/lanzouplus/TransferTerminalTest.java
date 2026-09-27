package cc.nkbr.lanzouplus;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * DFWX-STAB-001：下载暂停/恢复/取消终态线性化回归。
 * TransferTerminal 是 SegmentDownloader 停止语义的单点线性化器：
 * claim 只发生一次；cancel(2) 覆盖 pause(1)；终态后不再接受请求。
 */
public class TransferTerminalTest {

  @Test
  public void pauseThenClaimYieldsPauseOutcome() {
    TransferTerminal terminal = new TransferTerminal();
    assertTrue(terminal.request(1));
    assertEquals(1, terminal.stopMode());
    assertEquals(1, terminal.claim());
  }

  @Test
  public void cancelOverridesPendingPause() {
    TransferTerminal terminal = new TransferTerminal();
    assertTrue(terminal.request(1));
    assertTrue(terminal.request(2));
    assertEquals(2, terminal.claim());
  }

  @Test
  public void pauseCannotDowngradeCancel() {
    TransferTerminal terminal = new TransferTerminal();
    assertTrue(terminal.request(2));
    assertTrue(terminal.request(1)); // 请求被受理，但不得改写已生效的 stop
    assertEquals("pause 不得降级已生效的 cancel", 2, terminal.stopMode());
    assertEquals(2, terminal.claim());
  }

  @Test
  public void claimHappensExactlyOnce() {
    TransferTerminal terminal = new TransferTerminal();
    assertTrue(terminal.request(1));
    assertEquals(1, terminal.claim());
    assertEquals(-1, terminal.claim());
    assertFalse("终态后不再接受停止请求", terminal.request(2));
  }

  @Test
  public void cleanCompletionClaimsZero() {
    TransferTerminal terminal = new TransferTerminal();
    assertEquals(0, terminal.claim());
    assertEquals(-1, terminal.claim());
  }
}
