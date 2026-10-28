package local.codex.xiaoaiallowlist;
import android.app.Application;
import android.content.*;
import android.content.pm.PackageInfo;
import android.os.*;
import android.util.Log;
import java.util.*;

final class HostCompatibility {
  static final String MODULE_VERSION="0.20";
  private static Boolean allowed;
  private static Bundle report;
  private static final String[][] REQUIRED={
    {"F","yy.e","a"},{"F","yy.j","a"},{"F","yy.i","a"},{"F","yy.g","r"},{"F","gz.a","a"},
    {"M","yy.e","getWhiteAppList"},{"M","yy.e","getRelevantDialogId"},{"M","yy.e","getCurrentAgentSkillName"},
    {"M","yy.e","getAppName"},{"M","yy.e","getCurrentContextInfo"},{"M","yy.e","getCurrentRid"},{"M","yy.e","setCurrentRid","java.lang.String"},
    {"M","yy.g","onWindowInfosChanged","java.util.List"},{"M","yy.d","getDisableWhiteListCheck"},
    {"M","yy.j","getCurrentState"},{"M","gz.d","getPriority"},{"M","gz.a","getSTATE_INIT"},{"M","gz.a","getSTATE_STOP"},
    {"M","yy.i","onClientPause","java.lang.String"},{"M","yy.i","onClientStop","java.lang.String","java.lang.String","boolean","boolean"},
    {"M","yy.i","reportResumeUIAgent","java.lang.String","java.lang.String","boolean","java.lang.String","java.lang.String","java.util.List"},
    {"F","com.xiaomi.voiceassist.baselibrary.uiagent.b","a"},
    {"M","com.xiaomi.voiceassist.baselibrary.uiagent.b","is_uiagent_takeover_state$uiagent_release"},
    {"M","com.xiaomi.voiceassist.baselibrary.uiagent.b","set_uiagent_takeover_state$uiagent_release","boolean"},
    {"M","ul.j","postEvent","com.xiaomi.ai.api.common.Event"},
    {"M","xl.f","interceptInstruction","com.xiaomi.ai.api.common.Instruction"},
    {"M","y00.r0","C0","com.xiaomi.ai.api.common.Event"},{"N","y00.r0","getEnginePrefix","0"},
    {"M","z10.a","c","com.xiaomi.ai.api.common.Instruction"},
    {"M","i10.g","isInContinuousDialog"},
    {"K","t20.c"},{"M","t20.c","setTextToProcess","java.lang.String"},{"K","t20.f"},{"K","y00.x0$d"},
    {"M","y00.x0$d","setEventLevel","int"},{"M","y00.x0$d","needNlp"},{"M","y00.x0$d","needTts"},
    {"M","y00.x0$d","setNlpRequest","t20.c"},{"M","y00.x0$d","setTtsRequest","t20.f"},
    {"M","y00.h","getInstance"},{"N","y00.h","sendEvent","1"},
    {"M","com.xiaomi.voiceassist.engine.context.b","getAllAppContext","int"},
    {"N","cc0.j0","parseLaunchAppData","1"},
    {"N","com.xiaomi.ai.api.common.APIUtils","buildEvent","3"},
    {"M","com.xiaomi.ai.api.common.APIUtils","readEvent","java.lang.String"},
    {"M","com.xiaomi.ai.api.common.APIUtils","readInstruction","java.lang.String"},
    {"N","com.xiaomi.ai.api.common.APIUtils","toJsonString","1"},
    {"C","com.xiaomi.ai.api.Application$ResumeUIAgent"},{"C","com.xiaomi.ai.api.Application$AppDetailV1"},
    {"C","com.xiaomi.ai.api.Template$AndroidIntent"},{"C","com.xiaomi.ai.api.Launcher$LaunchApp"},
    {"C","com.xiaomi.ai.api.Launcher$LaunchTargetType"},{"C","com.xiaomi.ai.api.Nlp$Request"}
  };
  static synchronized boolean allow(Context context,ClassLoader loader){
    if(allowed!=null)return allowed;
    report=new Bundle();report.putString("runtimeModuleVersion",MODULE_VERSION);
    try{
      PackageInfo host=context.getPackageManager().getPackageInfo("com.miui.voiceassist",0);
      long code=host.getLongVersionCode();
      report.putLong("hostVersionCode",code);report.putString("hostVersionName",host.versionName);
      List<String> missing=SignatureProbe.issues(loader,REQUIRED);
      allowed=missing.isEmpty();report.putString("compatibility",allowed?(code==507013034L?"profile-matched":"interfaces-matched-untested"):"incompatible");
      report.putString("missingBindings",join(missing));
      Log.i("XiaoAiAllowList","{\"event\":\"hostCompatibilityProbe\",\"allowed\":"+allowed+",\"knownVersion\":"+(code==507013034L)+",\"missingCount\":"+missing.size()+"}");
    }catch(Throwable e){allowed=false;report.putString("compatibility","probe-error");report.putString("missingBindings",e.getClass().getSimpleName());}
    if("com.miui.voiceassist".equals(Application.getProcessName()))publish(context);
    return allowed;
  }
  private static String join(List<String> items){StringBuilder s=new StringBuilder();for(String v:items){if(s.length()>0)s.append(';');s.append(v);if(s.length()>3000)break;}return s.toString();}
  private static void publish(Context c){try{c.getContentResolver().call(SettingsProvider.URI,"report",null,new Bundle(report));}catch(Throwable e){Log.w("XiaoAiAllowList","Compatibility report unavailable: "+e.getClass().getSimpleName());}}
  static synchronized void component(Context c,String name,boolean ready){
    if(report==null||!"com.miui.voiceassist".equals(Application.getProcessName()))return;
    report.putBoolean(name+"Ready",ready);publish(c);
  }
}
