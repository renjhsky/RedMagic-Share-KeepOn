package io.github.redmagic.sharekeepon;

/** The same preset policy is used by the settings UI and the hooked process. */
public final class TimeoutPolicy {
    public static final int DEFAULT_MINUTES = 0;
    public static final long STOCK_MILLIS = 600000L;
    private static final int[] MINUTES = {1, 5, 10, 30, 60, 0};
    private static final String[] LABELS = {"1分钟", "5分钟", "10分钟", "30分钟", "60分钟", "常开"};
    private TimeoutPolicy() {}
    public static boolean valid(int minutes) {
        for (int value : MINUTES) if (value == minutes) return true;
        return false;
    }
    public static int normalize(int minutes) { return valid(minutes) ? minutes : DEFAULT_MINUTES; }
    public static int index(int minutes) {
        int value = normalize(minutes);
        for (int i = 0; i < MINUTES.length; i++) if (MINUTES[i] == value) return i;
        throw new AssertionError();
    }
    public static int minutesAt(int position) { return MINUTES[position]; }
    public static String[] labels() { return LABELS.clone(); }
    public static long millis(int minutes) { return (long) normalize(minutes) * 60000L; }
    public static long remainingMillis(int minutes, long startedAt, long now) {
        long duration = millis(minutes);
        if (duration == 0L || startedAt <= 0L) return 0L;
        // Match the stock clock-reset behavior, without its hard-coded 10-minute cap.
        long elapsed = now <= startedAt ? 0L : now - startedAt;
        if (elapsed < 0L || elapsed >= duration) return 0L;
        return duration - elapsed;
    }
    public static String summary(int minutes) {
        int value = normalize(minutes);
        return value == 0 ? "开启后可被附近的设备发现，将保持开启直至手动关闭"
                : "开启后可被附近的设备发现，" + value + "分钟后将自动关闭";
    }
}
