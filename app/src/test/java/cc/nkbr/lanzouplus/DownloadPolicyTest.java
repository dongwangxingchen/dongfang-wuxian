package cc.nkbr.lanzouplus;

import java.net.URL;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** DFWX-SEC-001: external download and installation policy regressions. */
public class DownloadPolicyTest {

  @Test
  public void externalUrlRequiresHttpsAndRejectsCredentials() {
    assertEquals("下载地址为空", DownloadUrlPolicy.rejectionReason(""));
    assertEquals("仅支持 HTTPS 下载地址", DownloadUrlPolicy.rejectionReason("http://example.com/app.apk"));
    assertEquals("仅支持 HTTPS 下载地址", DownloadUrlPolicy.rejectionReason("ftp://example.com/app.apk"));
    assertEquals("不允许携带账号或密码", DownloadUrlPolicy.rejectionReason("https://user:secret@example.com/app.apk"));
    assertTrue(DownloadUrlPolicy.isAllowedExternal("https://downloads.example.com/app.apk"));
  }

  @Test
  public void externalUrlRejectsLocalAndPrivateLiterals() {
    assertEquals("不允许访问本机或内网地址", DownloadUrlPolicy.rejectionReason("https://localhost/app.apk"));
    assertEquals("不允许访问本机或内网地址", DownloadUrlPolicy.rejectionReason("https://127.0.0.1/app.apk"));
    assertEquals("不允许访问本机或内网地址", DownloadUrlPolicy.rejectionReason("https://10.0.0.8/app.apk"));
    assertEquals("不允许访问本机或内网地址", DownloadUrlPolicy.rejectionReason("https://172.16.0.8/app.apk"));
    assertEquals("不允许访问本机或内网地址", DownloadUrlPolicy.rejectionReason("https://192.168.1.8/app.apk"));
    assertEquals("不允许访问本机或内网地址", DownloadUrlPolicy.rejectionReason("https://[::1]/app.apk"));
    assertEquals("不允许访问本机或内网地址", DownloadUrlPolicy.rejectionReason("https://[fd00::1]/app.apk"));
    assertEquals("不允许访问本机或内网地址", DownloadUrlPolicy.rejectionReason("https://[::ffff:192.168.1.8]/app.apk"));
    assertEquals("不允许访问本机或内网地址", DownloadUrlPolicy.rejectionReason("https://100.64.0.8/app.apk"));
    assertEquals("不允许访问本机或内网地址", DownloadUrlPolicy.rejectionReason("https://printer.local/app.apk"));
  }

  @Test
  public void resolvedHostPolicyFailsClosedForPrivateAddress() throws Exception {
    assertEquals("不允许访问本机或内网地址",
        DownloadUrlPolicy.resolvedHostRejectionReason(new URL("https://127.0.0.1/app.apk")));
    assertEquals("无法验证下载地址",
        DownloadUrlPolicy.resolvedHostRejectionReason(new URL("https://host.invalid/app.apk")));
  }

  @Test
  public void sourcePolicyFailsClosedForLegacyRecords() {
    assertEquals(DownloadSourcePolicy.LEGACY, DownloadSourcePolicy.normalize(null));
    assertEquals(DownloadSourcePolicy.LEGACY, DownloadSourcePolicy.normalize("unknown"));
    assertTrue(DownloadSourcePolicy.requiresInstallConfirmation(DownloadSourcePolicy.EXTERNAL));
    assertTrue(DownloadSourcePolicy.requiresInstallConfirmation(DownloadSourcePolicy.LEGACY));
    assertFalse(DownloadSourcePolicy.allowsAutomaticInstall(DownloadSourcePolicy.EXTERNAL));
    assertFalse(DownloadSourcePolicy.allowsSilentInstall(DownloadSourcePolicy.LEGACY));
    assertFalse(DownloadSourcePolicy.requiresInstallConfirmation(DownloadSourcePolicy.LANZOU));
    assertFalse(DownloadSourcePolicy.requiresInstallConfirmation(DownloadSourcePolicy.UPDATE));
    assertTrue(DownloadSourcePolicy.allowsSilentInstall(DownloadSourcePolicy.UPDATE));
  }
}
