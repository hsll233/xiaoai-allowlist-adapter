package local.codex.xiaoaiallowlist;

import android.content.*;
import android.content.pm.*;
import android.content.res.*;
import android.os.SystemClock;
import android.util.Log;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.*;
import de.robv.android.xposed.XposedHelpers;

final class TargetCatalog {
  private final Context app;private final ClassLoader loader;
  private List<IntentPolicy.Target> cache=new ArrayList<IntentPolicy.Target>();private long loaded;
  TargetCatalog(Context app,ClassLoader loader){
    this.app=app;this.loader=loader;
    BroadcastReceiver changed=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){invalidate();}};
    IntentFilter packages=new IntentFilter();packages.addAction(Intent.ACTION_PACKAGE_ADDED);packages.addAction(Intent.ACTION_PACKAGE_REMOVED);packages.addAction(Intent.ACTION_PACKAGE_CHANGED);packages.addDataScheme("package");
    ReceiverCompat.privateReceiver(app,changed,packages);
    ReceiverCompat.privateReceiver(app,changed,new IntentFilter(Intent.ACTION_LOCALE_CHANGED));
  }
  private synchronized void invalidate(){loaded=0;}
  private void localizedNames(ApplicationInfo info,ActivityInfo activity,Set<String> aliases){
    try{
      Context foreign=app.createPackageContext(info.packageName,Context.CONTEXT_IGNORE_SECURITY);
      for(Locale locale:new Locale[]{Locale.SIMPLIFIED_CHINESE,Locale.US}){
        Configuration configuration=new Configuration(foreign.getResources().getConfiguration());configuration.setLocale(locale);
        Resources resources=foreign.createConfigurationContext(configuration).getResources();
        for(int id:new int[]{info.labelRes,activity==null?0:activity.labelRes})if(id!=0)try{aliases.add(resources.getText(id).toString());}catch(Resources.NotFoundException ignored){}
      }
    }catch(Exception ignored){}
  }
  private Object call(Object o,String method){return XposedHelpers.callMethod(o,method);}
  private Object optional(Object o){if(o==null||o instanceof String)return o;try{return Boolean.TRUE.equals(call(o,"isPresent"))?call(o,"get"):null;}catch(Throwable e){return o;}}
  synchronized List<IntentPolicy.Target> all(){
    long now=SystemClock.elapsedRealtime();if(loaded!=0&&now-loaded<60000L)return cache;
    List<IntentPolicy.Target> found=new ArrayList<IntentPolicy.Target>();
    PackageManager pm=app.getPackageManager();
    Map<String,List<String>> activities=new HashMap<String,List<String>>();
    for(String category:new String[]{Intent.CATEGORY_LAUNCHER,Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_INFO})
      for(ResolveInfo r:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
        if(r==null||r.activityInfo==null||!r.activityInfo.enabled)continue;
        String pkg=r.activityInfo.packageName;List<String> names=activities.get(pkg);if(names==null){names=new ArrayList<String>();activities.put(pkg,names);}names.add(String.valueOf(r.loadLabel(pm)));
      }
    for(ApplicationInfo info:pm.getInstalledApplications(0)){
      if(info==null||!info.enabled)continue;
      try{
      String pkg=info.packageName,label=String.valueOf(pm.getApplicationLabel(info));
      Set<String> aliases=new LinkedHashSet<String>();aliases.add(pkg);
      List<String> activityNames=activities.get(pkg);if(activityNames!=null)aliases.addAll(activityNames);
      Intent launch=AppAccess.launch(app,pkg);ActivityInfo activity=launch==null?null:pm.getActivityInfo(launch.getComponent(),0);
      if(activity!=null){aliases.add(String.valueOf(activity.loadLabel(pm)));localizedNames(info,activity,aliases);}
      if("com.google.android.youtube".equals(pkg)){aliases.add("YouTube");aliases.add("油管");aliases.add("优兔");}
      if("com.google.android.apps.youtube.music".equals(pkg)){aliases.add("YouTube Music");aliases.add("油管音乐");}
      if("com.discord".equals(pkg))aliases.add("迪斯科德");
      found.add(new IntentPolicy.Target(pkg,label,launch!=null,aliases.toArray(new String[aliases.size()])));
      }catch(PackageManager.NameNotFoundException removedDuringScan){}
    }
    cache=Collections.unmodifiableList(found);loaded=SystemClock.elapsedRealtime();return cache;
  }
  IntentPolicy.Target foreground(){
    try{
      Object c=XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.xiaomi.voiceassist.engine.context.b",loader),"getAllAppContext",0);
      Object p=optional(call(call(c,"getPayload"),"getForegroundApp"));
      String pkg=p instanceof String?(String)p:String.valueOf(call(p,"getPkgName"));
      for(IntentPolicy.Target t:all())if(t.packageName.equals(pkg)&&!pkg.equals(app.getPackageName()))return t;
    }catch(Throwable ignored){}
    return null;
  }
  IntentPolicy.Target foreground(Object event){
    try{
      Object contexts=XposedHelpers.callMethod(event,"getContexts");
      if(contexts instanceof List)for(Object context:(List<?>)contexts){
        if(!"Application.AppDetailV1".equals(call(context,"getFullName")))continue;
        Object payload=call(context,"getPayload"),value=optional(call(payload,"getForegroundApp"));
        String pkg=value instanceof String?(String)value:"";
        for(IntentPolicy.Target t:all())if(t.packageName.equals(pkg)&&!pkg.equals(app.getPackageName()))return t;
      }
    }catch(Throwable ignored){}
    return foreground();
  }
  void audit(){
    try{
      long started=SystemClock.elapsedRealtime();List<IntentPolicy.Target> targets=all();int launchable=0,named=0,packageNamed=0,current=0,templates=0;JSONArray launchFailures=new JSONArray();Set<String> legacy=new HashSet<String>();
      for(ResolveInfo r:app.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),0))if(r!=null&&r.activityInfo!=null&&r.activityInfo.enabled&&r.activityInfo.applicationInfo.enabled)legacy.add(r.activityInfo.packageName);
      for(IntentPolicy.Target t:targets){
        if(t.launchable){launchable++;IntentPolicy.Target byName=IntentPolicy.select("打开"+t.label+"，进入我的页面",targets,null);if(byName!=null&&t.packageName.equals(byName.packageName))named++;IntentPolicy.Target byPackage=IntentPolicy.select("打开"+t.packageName+"，进入我的页面",targets,null);if(byPackage!=null&&t.packageName.equals(byPackage.packageName))packageNamed++;
          try{NativeLaunchPlan.buildValidated(app,loader,t,"codex-readonly-catalog-audit");templates++;}catch(Throwable invalidTemplate){launchFailures.put(t.packageName);}
        }
        if(IntentPolicy.select("在当前应用进入我的页面",targets,t)==t)current++;
      }
      JSONObject report=new JSONObject();report.put("event","allInstalledCatalogAudit");report.put("enabledInstalled",targets.size());report.put("launchable",launchable);report.put("foregroundOnly",targets.size()-launchable);report.put("nameRoundTrip",named);report.put("packageRoundTrip",packageNamed);report.put("currentRoundTrip",current);report.put("legacyLauncherPackages",legacy.size());report.put("elapsedMs",SystemClock.elapsedRealtime()-started);
      for(IntentPolicy.Target sample:targets)if(!sample.packageName.equals(app.getPackageName())){
        Object detail=XposedHelpers.newInstance(XposedHelpers.findClass("com.xiaomi.ai.api.Application$AppDetailV1",loader));
        XposedHelpers.callMethod(detail,"setForegroundApp",sample.packageName);
        List<Object> context=new ArrayList<Object>();context.add(NativeLaunchPlan.api(loader,"buildContext",detail));
        Object payload=XposedHelpers.newInstance(XposedHelpers.findClass("com.xiaomi.ai.api.Nlp$Request",loader));
        XposedHelpers.callMethod(payload,"setQuery","游戏里帮我签到");
        Object event=NativeLaunchPlan.api(loader,"buildEvent",payload,context,"codex-game-foreground-readonly");
        IntentPolicy.Target actual=foreground(event);
        report.put("gamePhraseNativeEventSelfCheck",actual!=null&&sample.packageName.equals(actual.packageName)&&IntentPolicy.select("游戏里帮我签到",targets,actual)==actual);
        break;
      }
      report.put("nativeLaunchTemplates",templates);report.put("nativeLaunchFailures",launchFailures);Log.i("XiaoAiAllowList",report.toString());
    }catch(Throwable e){Log.e("XiaoAiAllowList","{\"event\":\"catalogAuditError\",\"type\":\""+e.getClass().getSimpleName()+"\"}");}
  }
}
