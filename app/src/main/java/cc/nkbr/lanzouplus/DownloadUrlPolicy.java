package cc.nkbr.lanzouplus;

import java.net.InetAddress;
import java.net.URL;

/** Policy for direct downloads initiated by a web/external action. */
final class DownloadUrlPolicy {
  private DownloadUrlPolicy() {}

  static boolean isAllowedExternal(String raw) {
    return rejectionReason(raw).isEmpty();
  }

  static String rejectionReason(String raw) {
    if (raw == null || raw.trim().isEmpty()) return "下载地址为空";
    try {
      return rejectionReason(new URL(raw.trim()));
    } catch (Exception error) {
      return "下载地址无效";
    }
  }

  static boolean isAllowedExternal(URL url) {
    return rejectionReason(url).isEmpty();
  }

  static String rejectionReason(URL url) {
    if (url == null) return "下载地址为空";
    if (!"https".equalsIgnoreCase(url.getProtocol())) return "仅支持 HTTPS 下载地址";
    if (url.getUserInfo() != null) return "不允许携带账号或密码";
    String host = url.getHost();
    if (host == null || host.trim().isEmpty()) return "下载地址缺少主机名";
    if (isPrivateOrLocalHost(host)) return "不允许访问本机或内网地址";
    return "";
  }

  /** Resolve the host on the worker thread immediately before connecting. */
  static String resolvedHostRejectionReason(URL url) {
    String basic = rejectionReason(url);
    if (!basic.isEmpty()) return basic;
    String host = url.getHost();
    try {
      for (InetAddress address : InetAddress.getAllByName(host)) {
        if (isPrivateAddress(address)) return "不允许访问本机或内网地址";
      }
      return "";
    } catch (Exception error) {
      return "无法验证下载地址";
    }
  }

  private static boolean isPrivateOrLocalHost(String rawHost) {
    String host = rawHost == null ? "" : rawHost.trim().toLowerCase(java.util.Locale.ROOT);
    while (host.startsWith("[") && host.endsWith("]") && host.length() > 2) {
      host = host.substring(1, host.length() - 1);
    }
    while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
    if (host.isEmpty()
        || host.equals("localhost")
        || host.endsWith(".localhost")
        || host.endsWith(".local")
        || host.endsWith(".internal")
        || host.equals("home.arpa")) return true;
    if (!looksLikeLiteralAddress(host)) return false;
    try {
      InetAddress[] addresses = InetAddress.getAllByName(host);
      for (InetAddress address : addresses) if (isPrivateAddress(address)) return true;
    } catch (Exception ignored) {
      // A literal that cannot be parsed will fail at connection time; do not turn it into a bypass.
      return true;
    }
    return false;
  }

  private static boolean isPrivateAddress(InetAddress address) {
    if (address == null) return true;
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) return true;
    byte[] bytes = address.getAddress();
    if (bytes.length == 4) return isPrivateIpv4(bytes, 0);
    if (bytes.length == 16) {
      int first = bytes[0] & 0xff;
      int second = bytes[1] & 0xff;
      if ((first & 0xfe) == 0xfc || (first == 0xfe && (second & 0xc0) == 0x80)) return true;
      boolean mapped = true;
      for (int i = 0; i < 10; i++) if (bytes[i] != 0) mapped = false;
      if (mapped && (bytes[10] & 0xff) == 0xff && (bytes[11] & 0xff) == 0xff) {
        return isPrivateIpv4(bytes, 12);
      }
    }
    return false;
  }

  private static boolean isPrivateIpv4(byte[] bytes, int offset) {
    int first = bytes[offset] & 0xff;
    int second = bytes[offset + 1] & 0xff;
    int third = bytes[offset + 2] & 0xff;
    if (first == 0 || first == 10 || first == 127 || first >= 240) return true;
    if (first == 169 && second == 254) return true;
    if (first == 172 && second >= 16 && second <= 31) return true;
    if (first == 192 && second == 168) return true;
    if (first == 100 && second >= 64 && second <= 127) return true;
    if (first == 198 && (second == 18 || second == 19)) return true;
    return first == 192 && second == 0 && third == 0;
  }

  private static boolean looksLikeLiteralAddress(String host) {
    if (host.indexOf(':') >= 0) return true;
    boolean digitOrDot = true;
    for (int i = 0; i < host.length(); i++) {
      char value = host.charAt(i);
      if ((value < '0' || value > '9') && value != '.') {
        digitOrDot = false;
        break;
      }
    }
    return digitOrDot && host.indexOf('.') >= 0;
  }
}
