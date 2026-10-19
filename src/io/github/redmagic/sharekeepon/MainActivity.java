package io.github.redmagic.sharekeepon;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Switch moduleSwitch, shareSwitch;
    private Spinner timeoutSpinner;
    private boolean updating, ready, bound, lastShareState;
    private int sequence, foreground, secondary, cardColor;
    private final ServiceConnection connection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) { request(null, false); }
        @Override public void onServiceDisconnected(ComponentName name) { ready = false; shareSwitch.setEnabled(false); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        final boolean dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        foreground = Color.parseColor(dark ? "#F1F0F7" : "#242535");
        secondary = Color.parseColor(dark ? "#B0ACBD" : "#737583");
        cardColor = Color.parseColor(dark ? "#202027" : "#FFFFFF");
        getWindow().setDecorFitsSystemWindows(false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.parseColor(dark ? "#141419" : "#F6F6F9"));
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(18), dp(26), dp(18), dp(36));
        scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            body.setPadding(dp(18) + bars.left, dp(26) + bars.top, dp(18) + bars.right, dp(36) + bars.bottom);
            WindowInsetsController controller = view.getWindowInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(
                    dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            return insets;
        });
        body.addView(text("红魔互传常开", 26, foreground, true));
        space(body, 5); body.addView(text("v" + ModuleInfo.VERSION, 12, secondary, false));
        space(body, 18);
        LinearLayout controls = card(body);
        moduleSwitch = makeSwitch("启用模块", controls);
        shareSwitch = makeSwitch("打开红魔互传", controls);
        shareSwitch.setEnabled(false);
        moduleSwitch.setChecked(ModuleConfig.enabled(this));
        moduleSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (updating) return;
            if (!ModuleConfig.setEnabled(this, enabled)) { setChecked(moduleSwitch, !enabled); toast("设置保存失败"); return; }
            if (ready) request("config", enabled);
            else toast("请在 LSPosed / Vector 中启用模块并重启");
        });
        shareSwitch.setOnCheckedChangeListener((button, enabled) -> { if (!updating) request("share", enabled); });
        space(body, 14);
        LinearLayout timing = card(body);
        timing.addView(text("自动关闭时间", 17, foreground, true));
        space(timing, 8);
        timeoutSpinner = new Spinner(this);
        timeoutSpinner.setContentDescription("自动关闭时间");
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, TimeoutPolicy.labels());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeoutSpinner.setAdapter(adapter);
        timeoutSpinner.setSelection(TimeoutPolicy.index(ModuleConfig.timeoutMinutes(this)));
        timing.addView(timeoutSpinner, new LinearLayout.LayoutParams(-1, dp(48)));
        timeoutSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                int minutes = TimeoutPolicy.minutesAt(position);
                if (updating || minutes == ModuleConfig.timeoutMinutes(MainActivity.this)) return;
                if (!ModuleConfig.setTimeoutMinutes(MainActivity.this, minutes)) {
                    selectTimeout(ModuleConfig.timeoutMinutes(MainActivity.this)); toast("设置保存失败"); return;
                }
                if (ready) request("config", false);
                else toast("已保存，模块生效后使用此时间");
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        space(body, 14);
        LinearLayout updates = card(body);
        updates.addView(text("版本与更新", 17, foreground, true));
        updates.addView(text("当前版本 v" + ModuleInfo.VERSION + " · 在 GitHub 发布页查看新版安装包和更新说明。", 12, secondary, false));
        space(updates, 10);
        Button update = button("查看更新（GitHub）"); updates.addView(update);
        update.setOnClickListener(view -> openProject(true));
        Button project = button("项目主页"); updates.addView(project);
        project.setOnClickListener(view -> openProject(false));
        setContentView(scroll);
    }
    private void openProject(boolean releases) {
        String url = releases ? ModuleInfo.UPDATE_URL : ModuleInfo.PROJECT_URL;
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (ActivityNotFoundException error) {
            new AlertDialog.Builder(this).setTitle("无法打开浏览器").setMessage(url)
                .setPositiveButton("复制链接", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("GitHub", url));
                    Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("关闭", null).show();
        }
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
        super.onResume(); setChecked(moduleSwitch, ModuleConfig.enabled(this));
        selectTimeout(ModuleConfig.timeoutMinutes(this)); request(null, false);
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
            ready = false; shareSwitch.setEnabled(false); setChecked(shareSwitch, lastShareState);
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
                    boolean sameVersion = result != null && result.getInt("versionCode") == ModuleInfo.VERSION_CODE;
                    ready = getResultCode() == Activity.RESULT_OK && sameVersion
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
                        if (control) toast(result == null ? "请在框架中启用模块并重启"
                                : !sameVersion ? "模块仍加载旧版，请重启手机" : "当前互传版本尚未适配");
                    }
                }
            }, handler, 0, null, null);
        } catch (RuntimeException e) {
            handler.removeCallbacks(timeout); ready = false; shareSwitch.setEnabled(false);
            setChecked(shareSwitch, lastShareState);
        }
    }
    private void setChecked(Switch view, boolean value) { updating = true; view.setChecked(value); updating = false; }
    private void selectTimeout(int minutes) {
        updating = true; timeoutSpinner.setSelection(TimeoutPolicy.index(minutes)); updating = false;
    }
    private Switch makeSwitch(String title, LinearLayout parent) {
        Switch control = new Switch(this); control.setText(title); control.setTextSize(17); control.setTextColor(foreground);
        control.setShowText(false); control.setSwitchPadding(dp(20));
        parent.addView(control, new LinearLayout.LayoutParams(-1, dp(72))); return control;
    }
    // Match Tile Studio's card padding, radius, native buttons and typography.
    private LinearLayout card(LinearLayout parent) {
        LinearLayout result = new LinearLayout(this); result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(17), dp(16), dp(17), dp(16));
        GradientDrawable background = new GradientDrawable(); background.setColor(cardColor); background.setCornerRadius(dp(22));
        result.setBackground(background); parent.addView(result, new LinearLayout.LayoutParams(-1, -2)); return result;
    }
    private TextView text(String value, int size, int color, boolean bold) {
        TextView result = new TextView(this); result.setText(value); result.setTextSize(size); result.setTextColor(color);
        if (bold) result.setTypeface(Typeface.DEFAULT, Typeface.BOLD); return result;
    }
    private Button button(String title) { Button result = new Button(this); result.setText(title); result.setTextColor(foreground); result.setAllCaps(false); return result; }
    private void space(LinearLayout parent, int height) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(height))); }
    private void toast(String text) { Toast.makeText(this, text, Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
