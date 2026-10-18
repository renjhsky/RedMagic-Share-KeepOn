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
    public static boolean setEnabled(Context context, boolean enabled) {
        boolean saved = context.getSharedPreferences("module_config", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled", enabled).commit();
        if (saved) context.getContentResolver().notifyChange(URI, null);
        return saved;
    }
    public static boolean readFromModule(Context target) {
        try {
            Bundle value = target.getContentResolver().call(URI, "get", null, null);
            return value != null && value.getBoolean("enabled", false);
        } catch (RuntimeException e) { return false; }
    }
}
