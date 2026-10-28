package local.codex.xiaoaiallowlist;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Button;
import android.widget.ScrollView;
import android.content.pm.PackageInfo;
import android.os.Build;

public final class MainActivity extends Activity {
  private LinearLayout layout;
  private TextView compatibility;
  private void text(String value, int size) {
    TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
    int gap = (int)(12 * getResources().getDisplayMetrics().density);
    view.setPadding(0, gap, 0, gap); layout.addView(view);
  }
  @Override public void onCreate(Bundle state) {
    super.onCreate(state);
    final SharedPreferences settings = getSharedPreferences(SettingsProvider.PREFS, 0);
    layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL);
    int pad = (int)(24 * getResources().getDisplayMetrics().density);
    layout.setPadding(pad, pad, pad, pad);ScrollView scroll=new ScrollView(this);scroll.addView(layout);setContentView(scroll);
    text("小爱名单适配", 23); text("0.20 · 语音与文字", 15);
    compatibility=new TextView(this);compatibility.setTextSize(16);layout.addView(compatibility);
    Button refresh=new Button(this);refresh.setText("刷新兼容状态");refresh.setOnClickListener(v->refreshStatus());layout.addView(refresh);
    Switch enabled = new Switch(this); enabled.setText("启用适配"); enabled.setTextSize(18);
    enabled.setChecked(settings.getBoolean("enabled", true));
    enabled.setOnCheckedChangeListener((button, checked) -> settings.edit().putBoolean("enabled", checked).apply());
    layout.addView(enabled);
    Switch voice = new Switch(this);voice.setText("适配语音");voice.setTextSize(18);
    voice.setChecked(settings.getBoolean("voice_enabled",true));
    voice.setOnCheckedChangeListener((button,checked)->settings.edit().putBoolean("voice_enabled",checked).apply());layout.addView(voice);
    text("让小爱打开并操作应用，也支持当前应用。", 17);
    text("其他手机需先安装并启用 LSPosed，作用域选择小爱同学，重启小爱后刷新状态。手机还需具备官方智控能力。",15);
    refreshStatus();
  }
  @Override protected void onResume(){super.onResume();if(compatibility!=null)refreshStatus();}
  private void refreshStatus(){
    String device=Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE;
    try{
      PackageInfo host=getPackageManager().getPackageInfo("com.miui.voiceassist",0);
      Bundle s=getContentResolver().call(SettingsProvider.URI,"get",null,null);
      String state="等待小爱启动后检测。";
      if(s!=null&&"0.20".equals(s.getString("runtimeModuleVersion"))&&host.getLongVersionCode()==s.getLong("hostVersionCode",-1)){
        String status=s.getString("compatibility","");
        if("incompatible".equals(status)||"probe-error".equals(status))state="当前小爱接口不匹配，适配未启用。请提供下方小爱版本以继续适配。";
        else if(s.getBoolean("textReady")&&s.getBoolean("voiceReady")&&s.getBoolean("windowReady"))state="interfaces-matched-untested".equals(status)?"接口匹配并已加载；此小爱版本尚未实测。":"已匹配适配版本，文字、语音和窗口适配均已加载。";
        else state="接口检测通过，部分功能尚未加载；请重启小爱后刷新。";
      }
      compatibility.setText(device+"\n小爱 "+host.versionName+"（"+host.getLongVersionCode()+"）\n"+state);
    }catch(android.content.pm.PackageManager.NameNotFoundException e){compatibility.setText(device+"\n未发现小爱同学，当前无法启用。");}
    catch(Throwable e){compatibility.setText(device+"\n状态读取失败，请重启模块后刷新。");}
  }
}








