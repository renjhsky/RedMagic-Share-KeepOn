package io.github.redmagic.sharekeepon;

public final class ModuleInfo {
    private ModuleInfo() {}
    public static final String VERSION = "1.1";
    public static final int VERSION_CODE = 110;
    public static final String APPLICATION_ID = "io.github.redmagic.sharekeepon";
    public static final String SETTINGS_AUTHORITY = APPLICATION_ID + ".settings";
    public static final String PERMISSION_CONTROL = APPLICATION_ID + ".permission.CONTROL";
    public static final String ACTION_CONTROL = APPLICATION_ID + ".CONTROL_V1";
    public static final String UPDATE_URL = "https://github.com/renjhsky/RedMagic-Share-KeepOn/releases/latest";
    public static final String PROJECT_URL = "https://github.com/renjhsky/RedMagic-Share-KeepOn";
    public static final String TARGET = "com.zte.cn.zteshare";
    public static final String TARGET_VERSION = "16.0.000.000.2607131724";
    public static final String TARGET_DEX_SHA256 = "a65f3ebc49931f25e3d04da3e3c94743f996384b341db0a93bc02e45a8b1d840";
    public static final String ACTION_PROBE = "io.github.redmagic.sharekeepon.PROBE_V1";
    public static final String STATUS_CLASS = TARGET + ".ZteShareStatus";
    public static final String CALLBACK_CLASS = STATUS_CLASS + "$b";
    public static final String SETTINGS_CLASS = TARGET + ".activity.SettingsActivity";
    // Resource names in the provided APK's resources.arsc are obfuscated.
    // The ID is used only after the exact classes.dex fingerprint matches.
    public static final int SUMMARY_VIEW_ID = 0x7f09034c;
}
