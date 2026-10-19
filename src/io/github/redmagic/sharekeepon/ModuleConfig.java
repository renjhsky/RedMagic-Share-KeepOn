package io.github.redmagic.sharekeepon;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

public final class ModuleConfig {
    public static final Uri URI = Uri.parse("content://" + ModuleInfo.SETTINGS_AUTHORITY + "/config");
    private ModuleConfig() {}
    public static boolean enabled(Context context) {
        return context.getSharedPreferences("module_config", Context.MODE_PRIVATE).getBoolean("enabled", true);
    }
    public static int timeoutMinutes(Context context) {
        return TimeoutPolicy.normalize(context.getSharedPreferences("module_config", Context.MODE_PRIVATE)
                .getInt("timeout_minutes", TimeoutPolicy.DEFAULT_MINUTES));
    }
    public static boolean setTimeoutMinutes(Context context, int minutes) {
        if (!TimeoutPolicy.valid(minutes)) return false;
        boolean saved = context.getSharedPreferences("module_config", Context.MODE_PRIVATE)
                .edit().putInt("timeout_minutes", minutes).commit();
        if (saved) context.getContentResolver().notifyChange(URI, null);
        return saved;
    }
    public static boolean setEnabled(Context context, boolean enabled) {
        boolean saved = context.getSharedPreferences("module_config", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", enabled).commit();
        if (saved) context.getContentResolver().notifyChange(URI, null);
        return saved;
    }
    public static final class State {
        public final boolean enabled;
        public final int timeoutMinutes;
        State(boolean enabled, int minutes) { this.enabled = enabled; this.timeoutMinutes = TimeoutPolicy.normalize(minutes); }
    }
    public static State readStateFromModule(Context target) {
        try {
            Bundle value = target.getContentResolver().call(URI, "get", null, null);
            return new State(value != null && value.getBoolean("enabled", false),
                    value == null ? TimeoutPolicy.DEFAULT_MINUTES : value.getInt("timeout_minutes", TimeoutPolicy.DEFAULT_MINUTES));
        } catch (RuntimeException e) { return new State(false, TimeoutPolicy.DEFAULT_MINUTES); }
    }
}
