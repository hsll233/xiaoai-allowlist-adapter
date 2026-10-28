package local.codex.xiaoaiallowlist;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Binder;
import android.content.SharedPreferences;

public final class SettingsProvider extends ContentProvider {
  public static final Uri URI = Uri.parse("content://local.codex.xiaoaiallowlist.settings");
  public static final String PREFS = "allowlist_settings";
  @Override public boolean onCreate() { return true; }
  @Override public Bundle call(String method, String arg, Bundle extras) {
    if("report".equals(method)){
      boolean host=false;String[] packages=getContext().getPackageManager().getPackagesForUid(Binder.getCallingUid());
      if(packages!=null)for(String p:packages)host|="com.miui.voiceassist".equals(p);
      if(!host)throw new SecurityException("Host process only");
      if(extras==null)throw new IllegalArgumentException("Missing report");
      SharedPreferences.Editor edit=getContext().getSharedPreferences(PREFS,0).edit();
      for(String key:new String[]{"runtimeModuleVersion","compatibility","hostVersionName","missingBindings"}){
        String value=extras.getString(key,"");edit.putString("runtime_"+key,value.substring(0,Math.min(4096,value.length())));
      }
      edit.putLong("runtime_hostVersionCode",extras.getLong("hostVersionCode",-1));
      edit.putLong("runtime_reportTime",System.currentTimeMillis());
      for(String key:new String[]{"textReady","windowReady","voiceReady"})edit.putBoolean("runtime_"+key,extras.getBoolean(key,false));
      edit.apply();return new Bundle();
    }
    if (!"get".equals(method)) throw new UnsupportedOperationException("Read only");
    Bundle result = new Bundle();
    result.putBoolean("enabled", getContext().getSharedPreferences(PREFS, 0).getBoolean("enabled", true));
    result.putString("version", "0.20");
    result.putString("mode", "client-window-membership-and-primary-text-voice-entry");
    result.putBoolean("automaticTextEntry",true);
    result.putBoolean("allApps",true);
    result.putString("appScope","all-enabled-installed");
    result.putBoolean("automaticVoiceEntry",getContext().getSharedPreferences(PREFS,0).getBoolean("voice_enabled",true));
    result.putBoolean("nativeTaskAdviceResume",false);
    result.putString("taskAdviceMode","new-official-task-after-final-action-advice");
    result.putBoolean("automaticTaskAdviceRestart",true);
    result.putString("voiceScope","ordinary-main-engine-single-turn");
    SharedPreferences runtime=getContext().getSharedPreferences(PREFS,0);
    for(String key:new String[]{"runtimeModuleVersion","compatibility","hostVersionName","missingBindings"})result.putString(key,runtime.getString("runtime_"+key,""));
    result.putLong("hostVersionCode",runtime.getLong("runtime_hostVersionCode",-1));
    result.putLong("reportTime",runtime.getLong("runtime_reportTime",0));
    for(String key:new String[]{"textReady","windowReady","voiceReady"})result.putBoolean(key,runtime.getBoolean("runtime_"+key,false));
    return result;
  }
  @Override public Cursor query(Uri u, String[] p, String s, String[] a, String o) { return null; }
  @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd.local.codex.allowlist-settings"; }
  @Override public Uri insert(Uri u, ContentValues v) { throw new UnsupportedOperationException("Read only"); }
  @Override public int delete(Uri u, String s, String[] a) { throw new UnsupportedOperationException("Read only"); }
  @Override public int update(Uri u, ContentValues v, String s, String[] a) { throw new UnsupportedOperationException("Read only"); }
}








