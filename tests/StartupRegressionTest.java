import android.app.Application;
import android.content.Intent;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toolbar;
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
        Toolbar bar=find(c.get().getWindow().getDecorView(),Toolbar.class).get(0);
        assertEquals(1,bar.getMenu().size());
        assertEquals("检查更新",bar.getMenu().getItem(0).getTitle().toString());
        assertTrue(bar.getMenu().performIdentifierAction(bar.getMenu().getItem(0).getItemId(),0));
        Intent intent=Shadows.shadowOf(c.get()).getNextStartedActivity();
        assertNotNull(intent); assertEquals(Intent.ACTION_VIEW,intent.getAction());
        assertEquals(ModuleInfo.UPDATE_URL,intent.getDataString());
        c.pause().stop().destroy();
    }
    @Test public void remoteConfigurationIsReadOnly() {
        ModuleConfigProvider provider=Robolectric.buildContentProvider(ModuleConfigProvider.class).create().get();
        assertTrue(provider.call("get",null,null).getBoolean("enabled"));
        assertTrue(ModuleConfig.setEnabled(RuntimeEnvironment.getApplication(),false));
        assertFalse(provider.call("get",null,null).getBoolean("enabled"));
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
}
