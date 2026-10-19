import android.content.Intent;
import android.content.ContentValues;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.ScrollView;
import io.github.redmagic.sharekeepon.MainActivity;
import io.github.redmagic.sharekeepon.ModuleConfig;
import io.github.redmagic.sharekeepon.ModuleConfigProvider;
import io.github.redmagic.sharekeepon.ModuleInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=35, manifest=Config.NONE, qualifiers="zh-rCN")
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
public class StartupRegressionTest {
    @Before public void clearSettings() {
        RuntimeEnvironment.getApplication().getSharedPreferences("module_config",0).edit().clear().commit();
    }
    private <T extends View> List<T> find(View view,Class<T> type) {
        List<T> result=new ArrayList<>();
        if(type.isInstance(view)) result.add(type.cast(view));
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++)
            result.addAll(find(((ViewGroup)view).getChildAt(i),type));
        return result;
    }
    private boolean contains(View view,String value) {
        for(TextView text:find(view,TextView.class)) if(text.getText().toString().contains(value)) return true;
        return false;
    }
    @Test public void twoSwitchesPersistAcrossRecreation() {
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.SECONDS);
        MainActivity a=c.get(); View root=a.getWindow().getDecorView();
        List<Switch> switches=find(root,Switch.class);
        assertEquals(2,switches.size());
        assertEquals("启用模块",switches.get(0).getText().toString());
        assertEquals("打开红魔互传",switches.get(1).getText().toString());
        assertFalse(contains(root,"复制检测结果")); assertFalse(contains(root,"首次使用"));
        assertFalse(switches.get(1).isEnabled());
        assertTrue(switches.get(0).isChecked());
        switches.get(0).setChecked(false); assertFalse(ModuleConfig.enabled(a));
        Bundle saved=new Bundle(); c.saveInstanceState(saved).pause().stop().destroy();
        c=Robolectric.buildActivity(MainActivity.class).create(saved).start().resume().visible();
        assertFalse(find(c.get().getWindow().getDecorView(),Switch.class).get(0).isChecked());
        c.pause().stop().destroy();
    }
    @Test @Config(qualifiers="zh-rCN-night") public void nightModeStartsWithTwoSwitches() {
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.SECONDS);
        assertEquals(2,find(c.get().getWindow().getDecorView(),Switch.class).size());
        c.pause().stop().destroy();
    }
    @Test public void updateEntryOpensLatestRelease() {
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
        View root=c.get().getWindow().getDecorView();
        assertTrue(contains(root,"版本与更新"));
        Button update=null,project=null;
        for(Button button:find(root,Button.class)) {
            if("查看更新（GitHub）".equals(button.getText().toString())) update=button;
            if("项目主页".equals(button.getText().toString())) project=button;
        }
        assertNotNull(update); assertNotNull(project); update.performClick();
        Intent intent=Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertNotNull(intent); assertEquals(Intent.ACTION_VIEW,intent.getAction());
        assertEquals(ModuleInfo.UPDATE_URL,intent.getDataString());
        assertTrue(intent.hasCategory(Intent.CATEGORY_BROWSABLE));
        project.performClick();intent=Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertEquals(ModuleInfo.PROJECT_URL,intent.getDataString());
        c.pause().stop().destroy();
    }
    @Test public void remoteConfigurationIsReadOnly() {
        ModuleConfigProvider provider=Robolectric.buildContentProvider(ModuleConfigProvider.class).create().get();
        assertTrue(provider.call("get",null,null).getBoolean("enabled"));
        assertTrue(ModuleConfig.setEnabled(RuntimeEnvironment.getApplication(),false));
        assertFalse(provider.call("get",null,null).getBoolean("enabled"));
        assertTrue(ModuleConfig.setTimeoutMinutes(RuntimeEnvironment.getApplication(),30));
        assertEquals(30,provider.call("get",null,null).getInt("timeout_minutes"));
        try {provider.call("set",null,new Bundle());fail("remote write allowed");} catch(SecurityException expected) {}
        try {provider.update(ModuleConfig.URI,new ContentValues(),null,null);fail("update allowed");} catch(SecurityException expected) {}
        try {provider.insert(ModuleConfig.URI,new ContentValues());fail("insert allowed");} catch(SecurityException expected) {}
        try {provider.delete(ModuleConfig.URI,null,null);fail("delete allowed");} catch(SecurityException expected) {}
    }
    @Test public void lostBackendDoesNotLeaveShareSwitchOn() throws Exception {
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.SECONDS);
        java.lang.reflect.Field ready=MainActivity.class.getDeclaredField("ready");ready.setAccessible(true);ready.setBoolean(c.get(),true);
        Switch share=find(c.get().getWindow().getDecorView(),Switch.class).get(1);
        share.setEnabled(true);share.setChecked(true);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.SECONDS);
        assertFalse(share.isChecked());assertFalse(share.isEnabled());
        c.pause().stop().destroy();
    }
    @Test public void sixPresetsPersistAfterRestart() {
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.SECONDS);
        Spinner picker=find(c.get().getWindow().getDecorView(),Spinner.class).get(0);
        assertEquals(6,picker.getCount()); assertEquals("常开",picker.getSelectedItem());
        int[] minutes={1,5,10,30,60,0};String[] labels={"1分钟","5分钟","10分钟","30分钟","60分钟","常开"};
        for(int i=0;i<minutes.length;i++) {
            assertEquals(labels[i],picker.getItemAtPosition(i));picker.setSelection(i);
            Shadows.shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.SECONDS);
            assertEquals(minutes[i],ModuleConfig.timeoutMinutes(c.get()));
        }
        picker.setSelection(4);Shadows.shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.SECONDS);
        c.pause().stop().destroy();
        c=Robolectric.buildActivity(MainActivity.class).setup();
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(5,TimeUnit.SECONDS);
        assertEquals("60分钟",find(c.get().getWindow().getDecorView(),Spinner.class).get(0).getSelectedItem());
        c.pause().stop().destroy();
    }
    @Test public void invalidTimeoutCannotOverwriteSavedPreset() {
        assertTrue(ModuleConfig.setTimeoutMinutes(RuntimeEnvironment.getApplication(),5));
        assertFalse(ModuleConfig.setTimeoutMinutes(RuntimeEnvironment.getApplication(),2));
        assertFalse(ModuleConfig.setTimeoutMinutes(RuntimeEnvironment.getApplication(),-1));
        assertEquals(5,ModuleConfig.timeoutMinutes(RuntimeEnvironment.getApplication()));
    }
    @Test @Config(qualifiers="zh-rCN-w640dp-h320dp-land") public void landscapeCanScrollToUpdates() {
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class).setup();
        View root=c.get().getWindow().getDecorView();
        assertEquals(1,find(root,ScrollView.class).size());
        assertEquals(2,find(root,Switch.class).size());assertEquals(1,find(root,Spinner.class).size());
        assertTrue(contains(root,"查看更新（GitHub）"));c.pause().stop().destroy();
    }
}
