package local.codex.xiaoaiallowlist;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.util.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** One primary text task, with an optional native startup step in that task's INIT batch. */
public final class AutoEntry implements IXposedHookLoadPackage {
  private static Context app;private static ClassLoader loader;private static TargetCatalog catalog;
  private static boolean installed;private static Pending current;
  private static final LinkedHashMap<String,Pending> requests=new LinkedHashMap<String,Pending>();
  private static final class Pending {
    final String id;final IntentPolicy.Target target;final Object replacement,launch;
    final long created=SystemClock.elapsedRealtime();boolean prepared;
    Pending(String id,IntentPolicy.Target target,Object replacement,Object launch){this.id=id;this.target=target;this.replacement=replacement;this.launch=launch;}
  }
  private static Object call(Object o,String m,Object...args){return NativeLaunchPlan.call(o,m,args);}
  private static Object api(String m,Object...args){return NativeLaunchPlan.api(loader,m,args);}
  private static String string(Object o){return NativeLaunchPlan.string(o);}
  private static void log(String event){Log.i("XiaoAiAllowList","{\"autoEntry\":true,\"event\":\""+event+"\"}");}
  private static void error(String event,Throwable e){Throwable cause=e;for(int i=0;i<4&&cause.getCause()!=null;i++)cause=cause.getCause();Log.e("XiaoAiAllowList","{\"autoEntry\":true,\"event\":\""+event+"\",\"type\":\""+e.getClass().getSimpleName()+"\",\"cause\":\""+cause.getClass().getSimpleName()+"\"}");}
  private static boolean enabled(){try{Bundle b=app.getContentResolver().call(SettingsProvider.URI,"get",null,null);return b!=null&&b.getBoolean("enabled",false);}catch(Throwable e){return false;}}
  private static boolean activeTask(){
    Object manager=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.j",loader),"a");
    Object state=call(manager,"getCurrentState");int priority=((Number)call(state,"getPriority")).intValue();
    return priority>=0&&priority<5;
  }
  static synchronized boolean sameAdviceApp(String query,String originalLabel){
    if(catalog==null)return false;
    IntentPolicy.Target original=null;
    for(IntentPolicy.Target t:catalog.all())if(t.label.equals(originalLabel)){
      if(original!=null&&!original.packageName.equals(t.packageName))return false;original=t;
    }
    if(original==null)return false;
    IntentPolicy.Target named=IntentPolicy.select(query,catalog.all(),original);
    return named==null||original.packageName.equals(named.packageName);
  }
  private static synchronized Object promote(Object event){
    if(!enabled())return event;
    try{
      if(!"Nlp.Request".equals(call(event,"getFullName")))return event;
      String id=string(call(event,"getId")),query=string(call(call(event,"getPayload"),"getQuery"));
      if(id.isEmpty()||query.isEmpty())return event;
      Pending prior=requests.get(id);if(prior!=null&&SystemClock.elapsedRealtime()-prior.created<180000L)return prior.replacement;
      if(activeTask())return event;
      if(!IntentPolicy.action(query))return event;
      IntentPolicy.Target foreground=catalog.foreground(event);
      IntentPolicy.Target target=IntentPolicy.select(query,catalog.all(),foreground);
      if(target==null)return event;
      boolean needsLaunch=foreground==null||!target.packageName.equals(foreground.packageName);
      Object launch=needsLaunch?NativeLaunchPlan.buildValidated(app,loader,target,id):null;
      Object payload=XposedHelpers.newInstance(XposedHelpers.findClass("com.xiaomi.ai.api.Application$ResumeUIAgent",loader));
      call(payload,"setRelevantDialogId",id);call(payload,"setRelevantSkillName","llmControlCopilotSkill");call(payload,"setRelevantQuery",TaskPrompt.forGoal(query));call(payload,"setAppName",target.label);call(payload,"setIsFirstFlow",true);
      Object replacement=api("buildEvent",payload,call(event,"getContexts"),id);
      Pending pending=new Pending(id,target,replacement,launch);
      call(XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.e",loader),"a"),"setCurrentRid",id);
      current=pending;requests.put(id,pending);while(requests.size()>32)requests.remove(requests.keySet().iterator().next());
      log(needsLaunch?"promotedOriginalTextWithNativeStartup":"promotedOriginalTextInForeground");return replacement;
    }catch(Throwable e){error("autoEntryError",e);return event;}
  }
  private static boolean matches(Pending pending,Object instruction){return pending.id.equals(string(call(instruction,"getDialogId")));}
  private static synchronized Object prepare(Object instruction){
    Pending pending=current;
    if(pending==null||pending.prepared||pending.launch==null||!enabled()||SystemClock.elapsedRealtime()-pending.created>15000L)return instruction;
    try{
      if(!"General.Push".equals(call(instruction,"getFullName")))return instruction;
      Object payload=call(instruction,"getPayload"),parsed=api("readInstructions",call(payload,"getInstructions"));
      if(!(parsed instanceof List))return instruction;
      List<?> children=(List<?>)parsed;int at=-1;Object init=null;boolean hasLaunch=false;
      boolean bound=matches(pending,instruction)||pending.id.equals(string(call(payload,"getDialogId")));
      for(int i=0;i<children.size();i++){
        Object child=children.get(i);String name=String.valueOf(call(child,"getFullName"));
        if("Launcher.LaunchApp".equals(name))hasLaunch=true;
        if("Application.UIAgentPhase".equals(name)&&"INIT".equals(String.valueOf(call(call(child,"getPayload"),"getUiAgentPhaseStatus")))){at=i;init=child;}
      }
      if(init==null)return instruction;
      bound=bound||matches(pending,init)||pending.id.equals(string(call(call(init,"getPayload"),"getDialogId")));
      if(!bound){log("startupPushUnbound");return instruction;}
      if(hasLaunch){pending.prepared=true;log("nativeStartupAlreadyPresent");return instruction;}
      String dialog=string(call(init,"getDialogId"));if(dialog.isEmpty())dialog=pending.id;
      String transaction=string(call(call(init,"getHeader"),"getTransactionId"));
      Object launch=NativeLaunchPlan.forDialog(loader,pending.launch,dialog,transaction);
      List<Object> patched=new ArrayList<Object>(children);patched.add(at+1,launch);
      Object replacement=api("readInstruction",api("toJsonString",instruction));call(call(replacement,"getPayload"),"setInstructions",api("writeInstructions",patched));
      pending.prepared=true;log("insertedNativeStartupSameTask");return replacement;
    }catch(Throwable e){error("nativeStartupError",e);return instruction;}
  }
  @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp){
    if(!"com.miui.voiceassist".equals(lp.packageName)||!lp.packageName.equals(lp.processName))return;
    XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){
      if(installed)return;
      try{
        app=(Context)p.args[0];if(!HostCompatibility.allow(app,lp.classLoader)){log("autoEntryVersionMismatch");return;}
        loader=lp.classLoader;catalog=new TargetCatalog(app,loader);
        XposedHelpers.findAndHookMethod("ul.j",loader,"postEvent",XposedHelpers.findClass("com.xiaomi.ai.api.common.Event",loader),new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){p.args[0]=promote(p.args[0]);}});
        XposedBridge.hookAllMethods(XposedHelpers.findClass("xl.f",loader),"interceptInstruction",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){if(p.args.length>0)p.args[0]=prepare(p.args[0]);}});
        installed=true;log("loadedAutomaticTextEntryWithNativeStartup");HostCompatibility.component(app,"text",true);
        Set<XC_MethodHook.Unhook> ready=XposedBridge.hookAllMethods(p.thisObject.getClass(),"onCreate",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam q){if(!q.hasThrowable()){VoiceEntry.install(app,lp);new Thread(new Runnable(){@Override public void run(){catalog.audit();}},"XiaoAiCatalogAudit").start();}}});
        if(ready.isEmpty())new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable(){@Override public void run(){VoiceEntry.install(app,lp);}},2500L);
      }catch(Throwable e){HostCompatibility.component(app,"text",false);error("autoEntryInitError",e);}
    }});
  }
}

