package io.github.redmagic.sharekeepon;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.MenuItem;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;
import android.widget.Toolbar;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Switch moduleSwitch, shareSwitch;
    private boolean updating, ready, bound, lastShareState;
    private int sequence;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) { request(null, false); }
        @Override public void onServiceDisconnected(ComponentName name) { ready = false; shareSwitch.setEnabled(false); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        int foreground = Color.parseColor(dark ? "#F1F0F7" : "#252331");
        int secondary = Color.parseColor(dark ? "#B0ACBD" : "#73707F");
        int card = Color.parseColor(dark ? "#202027" : "#FFFFFF");
        getWindow().setDecorFitsSystemWindows(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(dark ? "#141419" : "#F6F6FA"));
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            root.setPadding(dp(16) + bars.left, dp(8) + bars.top, dp(16) + bars.right, dp(16) + bars.bottom);
            WindowInsetsController controller = view.getWindowInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(
                    dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            return insets;
        });
        Toolbar bar = new Toolbar(this);
        bar.setTitle("红魔互传常开"); bar.setTitleTextColor(foreground);
        bar.setSubtitle("1.0"); bar.setSubtitleTextColor(secondary);
        MenuItem update = bar.getMenu().add("检查更新");
        update.setIcon(android.R.drawable.ic_popup_sync); update.getIcon().setTint(foreground);
        update.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
        bar.setOnMenuItemClickListener(item -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(ModuleInfo.UPDATE_URL))); }
            catch (RuntimeException e) { toast("未找到可用的浏览器"); }
            return true;
        });
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(72)));
        LinearLayout controls = new LinearLayout(this); controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(20), dp(4), dp(20), dp(4));
        GradientDrawable background = new GradientDrawable(); background.setColor(card); background.setCornerRadius(dp(22));
        controls.setBackground(background);
        LinearLayout.LayoutParams block = new LinearLayout.LayoutParams(-1, -2); block.topMargin = dp(18);
        root.addView(controls, block);
        moduleSwitch = makeSwitch("启用模块", foreground, controls);
        shareSwitch = makeSwitch("打开红魔互传", foreground, controls);
        shareSwitch.setEnabled(false);
        moduleSwitch.setChecked(ModuleConfig.enabled(this));
        moduleSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (updating) return;
            if (!ModuleConfig.setEnabled(this, enabled)) { setChecked(moduleSwitch, !enabled); toast("设置保存失败"); return; }
            if (ready) request("config", enabled);
            else toast("请在 LSPosed / Vector 中启用模块并重启");
        });
        shareSwitch.setOnCheckedChangeListener((button, enabled) -> { if (!updating) request("share", enabled); });
        setContentView(root);
    }
    @Override protected void onStart() {
        super.onStart();
        try {
            Intent service = new Intent().setComponent(new ComponentName(ModuleInfo.TARGET,
                    ModuleInfo.TARGET + ".ZTEShareManagerService"));
            bound = bindService(service, connection, Context.BIND_AUTO_CREATE);
        } catch (RuntimeException e) { bound = false; }
    }
    @Override protected void onResume() {
        super.onResume(); setChecked(moduleSwitch, ModuleConfig.enabled(this)); request(null, false);
    }
    @Override protected void onStop() {
        if (bound) { unbindService(connection); bound = false; }
        super.onStop();
    }
    @Override protected void onDestroy() { sequence++; handler.removeCallbacksAndMessages(null); super.onDestroy(); }
    private void request(String operation, boolean value) {
        final int ticket = ++sequence;
        final boolean control = operation != null;
        if (control) shareSwitch.setEnabled(false);
        final Runnable timeout = () -> {
            if (ticket != sequence || isDestroyed()) return;
            ready = false; shareSwitch.setEnabled(false);
            setChecked(shareSwitch, lastShareState);
            if (control) toast("模块未应答，请检查框架作用域并重启");
        };
        handler.postDelayed(timeout, 4000);
        Intent intent = new Intent(control ? ModuleInfo.ACTION_CONTROL : ModuleInfo.ACTION_PROBE)
                .setPackage(ModuleInfo.TARGET).addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
        if (control) intent.putExtra("operation", operation).putExtra("value", value);
        try {
            sendOrderedBroadcast(intent, null, new BroadcastReceiver() {
                @Override public void onReceive(Context context, Intent ignored) {
                    if (ticket != sequence || isFinishing() || isDestroyed()) return;
                    handler.removeCallbacks(timeout);
                    Bundle result = getResultExtras(false);
                    ready = getResultCode() == Activity.RESULT_OK && result != null
                            && result.getInt("hooks") == 3 && result.getBoolean("matched");
                    shareSwitch.setEnabled(ready);
                    if (ready) {
                        lastShareState = result.getInt("switch") == 1;
                        setChecked(shareSwitch, lastShareState);
                        setChecked(moduleSwitch, result.getBoolean("enabled"));
                        String error = result.getString("error", "");
                        if (!error.isEmpty() && control) toast(error);
                    } else {
                        setChecked(shareSwitch, lastShareState);
                        if (control) toast(result == null ? "请在框架中启用模块并重启" : "当前互传版本尚未适配");
                    }
                }
            }, handler, 0, null, null);
        } catch (RuntimeException e) {
            handler.removeCallbacks(timeout); ready = false; shareSwitch.setEnabled(false);
            setChecked(shareSwitch, lastShareState);
        }
    }
    private void setChecked(Switch view, boolean value) { updating = true; view.setChecked(value); updating = false; }
    private Switch makeSwitch(String title, int color, LinearLayout parent) {
        Switch control = new Switch(this); control.setText(title); control.setTextSize(17); control.setTextColor(color);
        control.setShowText(false); control.setSwitchPadding(dp(20));
        parent.addView(control, new LinearLayout.LayoutParams(-1, dp(80))); return control;
    }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
