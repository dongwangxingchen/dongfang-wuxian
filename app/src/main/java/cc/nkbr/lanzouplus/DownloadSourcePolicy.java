package cc.nkbr.lanzouplus;

/** Trust and installation rules for persisted download entries. */
final class DownloadSourcePolicy {
  static final String LANZOU = "lanzou";
  static final String EXTERNAL = "external";
  static final String UPDATE = "update";
  static final String LEGACY = "legacy";

  private DownloadSourcePolicy() {}

  static String normalize(String raw) {
    if (EXTERNAL.equals(raw) || UPDATE.equals(raw) || LANZOU.equals(raw)) return raw;
    return LEGACY;
  }

  static boolean isExternal(String source) {
    return EXTERNAL.equals(source);
  }

  /** Unknown/legacy records are fail-closed because their original trust boundary is unknown. */
  static boolean requiresInstallConfirmation(String source) {
    return EXTERNAL.equals(source) || LEGACY.equals(source);
  }

  static boolean allowsAutomaticInstall(String source) {
    return !requiresInstallConfirmation(source);
  }

  static boolean allowsSilentInstall(String source) {
    return !requiresInstallConfirmation(source);
  }
}
