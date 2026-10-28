package local.codex.xiaoaiallowlist;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Explicitly armed single-request routing experiments. No local planning or clicks. */
public final class ContextExperiment implements IXposedHookLoadPackage {
  private static final String ACTION="local.codex.xiaoaiallowlist.ARM_CONTEXT";
  private static final String SKILL="llmControlCopilotSkill";
  private static Context app;
  private static ClassLoader loader;
  private static long expires;
  private static String active="", input="nlp";
  private static String mode="skill-page", targetPackage="", targetLabel="";
  private static String appHint="target";
  private static Boolean decoupling;
  private static boolean promoted;
  private static boolean prepareLaunch, launchInserted;
  private static long promotedAt;
  private static boolean installed;
  private static void log(String event) { Log.i("XiaoAiAllowList","{\"experiment\":\""+mode+"\",\"event\":\""+event+"\"}"); }
  private static boolean enabled() {
    try { Bundle s=app.getContentResolver().call(SettingsProvider.URI,"get",null,null); return s!=null&&s.getBoolean("enabled",false); }
    catch(Throwable e){return false;}
  }
  private static Object call(Object o,String m,Object...args){return XposedHelpers.callMethod(o,m,args);}
  private static Object api(String m,Object...args){return XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.xiaomi.ai.api.common.APIUtils",loader),m,args);}
  private static synchronized void arm(Intent intent) {
    active=""; expires=0; promoted=false; targetPackage="";targetLabel="";
    prepareLaunch=false;launchInserted=false;promotedAt=0;
    String command=intent.getStringExtra("command");
    if(("skill-page".equals(command)||"direct-entry".equals(command))&&enabled()){
      mode=command;
      prepareLaunch="direct-entry".equals(mode)&&intent.getBooleanExtra("prepare_launch",false);
      appHint=intent.getStringExtra("app_hint");if(!"omit".equals(appHint)&&!"foreground".equals(appHint))appHint="target";
      decoupling=intent.hasExtra("decoupling")?Boolean.valueOf(intent.getBooleanExtra("decoupling",false)):null;
      input="voice".equals(intent.getStringExtra("input"))?"voice":"nlp";
      if("direct-entry".equals(mode)){
        input="nlp";
        try{
          String pkg=intent.getStringExtra("target_pkg");
          android.content.pm.ApplicationInfo info=app.getPackageManager().getApplicationInfo(pkg,0);
          if(!info.enabled||app.getPackageManager().getLaunchIntentForPackage(pkg)==null)throw new IllegalArgumentException();
          targetPackage=pkg;targetLabel=String.valueOf(info.loadLabel(app.getPackageManager()));
          if(targetLabel.isEmpty())throw new IllegalArgumentException();
        }catch(Throwable e){log("targetValidationFailed");return;}
        if(prepareLaunch&&!validateNativeLaunchTemplate())return;
      }
      expires=SystemClock.elapsedRealtime()+180000L; log("armed180Seconds");
    } else log("disarmed");
  }
  private static Object newContext(String cls){return api("buildContext",XposedHelpers.newInstance(XposedHelpers.findClass(cls,loader)));}
  private static Object patched(Object original,String name) {
    Object cloned=api("readContext",api("toJsonString",original));
    Object payload=call(cloned,"getPayload");
    if("Dialog.DialogState".equals(name)){
      call(payload,"setUserInSkillPage",true); call(payload,"setUserInSkillId",SKILL);
    }else call(payload,"setSkillId",SKILL);
    return cloned;
  }
  private static synchronized Object promote(Object event) {
    if(!"direct-entry".equals(mode)||expires==0||SystemClock.elapsedRealtime()>expires||!enabled())return event;
    try{
      if(!"Nlp.Request".equals(call(event,"getFullName")))return event;
      String id=(String)call(event,"getId");
      if(promoted){expires=0;log("closedOnNextRequest");return event;}
      String query=(String)call(call(event,"getPayload"),"getQuery");
      if(id==null||id.isEmpty()||query==null||!query.contains(targetLabel)){expires=0;log("targetQueryMismatch");return event;}
      if(prepareLaunch&&!query.matches("(?s).*(?:打开|启动|切换到)\\s*"+java.util.regex.Pattern.quote(targetLabel)+".*")){expires=0;log("nativeLaunchRequiresExplicitOpen");return event;}
      Object payload=XposedHelpers.newInstance(XposedHelpers.findClass("com.xiaomi.ai.api.Application$ResumeUIAgent",loader));
      call(payload,"setRelevantDialogId",id);call(payload,"setRelevantSkillName",SKILL);
      if("target".equals(appHint))call(payload,"setAppName",targetLabel);
      else if("foreground".equals(appHint)){
        Object contexts=call(event,"getContexts");String pkg="";
        if(contexts instanceof List)for(Object c:(List<?>)contexts){
          if(c!=null&&"Application.AppDetailV1".equals(call(c,"getFullName"))){
            Object optional=call(call(c,"getPayload"),"getForegroundApp");
            if(Boolean.TRUE.equals(call(optional,"isPresent")))pkg=String.valueOf(call(optional,"get"));
          }
        }
        if(!pkg.isEmpty()){
          android.content.pm.ApplicationInfo info=app.getPackageManager().getApplicationInfo(pkg,0);
          call(payload,"setAppName",String.valueOf(info.loadLabel(app.getPackageManager())));
        }
      }
      if(decoupling!=null)call(payload,"setIsDecouplingVersion",decoupling);
      call(payload,"setRelevantQuery",query);call(payload,"setIsFirstFlow",true);
      Object replacement=api("buildEvent",payload,call(event,"getContexts"),id);
      Object state=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.e",loader),"a");
      call(state,"setCurrentRid",id);
      active=id;promoted=true;promotedAt=SystemClock.elapsedRealtime();
      log("replacedOriginalNlpWithFirstFlowSameId");
      log("appHint_"+appHint);
      return replacement;
    }catch(Throwable e){Log.e("XiaoAiAllowList","{\"experiment\":\"direct-entry\",\"event\":\"entryPromotionError\",\"type\":\""+e.getClass().getSimpleName()+"\"}");return event;}
  }
  private static String optionalString(Object value){
    if(value instanceof String)return (String)value;
    try{return Boolean.TRUE.equals(call(value,"isPresent"))?String.valueOf(call(value,"get")):"";}catch(Throwable ignored){return "";}
  }
  private static Object nativeLaunchInstruction(String dialog,String transaction){
    Intent launch=app.getPackageManager().getLaunchIntentForPackage(targetPackage);
    if(launch==null||launch.getComponent()==null||!targetPackage.equals(launch.getComponent().getPackageName()))throw new IllegalStateException("Invalid launch component");
    Object androidIntent=XposedHelpers.newInstance(XposedHelpers.findClass("com.xiaomi.ai.api.Template$AndroidIntent",loader));
    call(androidIntent,"setPkgName",targetPackage);call(androidIntent,"setType","activity");call(androidIntent,"setUri",launch.toUri(Intent.URI_INTENT_SCHEME));
    Object launcher=XposedHelpers.newInstance(XposedHelpers.findClass("com.xiaomi.ai.api.Launcher$LaunchApp",loader));
    call(launcher,"setIdentifier",targetPackage);call(launcher,"setName",targetLabel);call(launcher,"setIntent",androidIntent);
    Object launchType=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.xiaomi.ai.api.Launcher$LaunchTargetType",loader),"ANDROID_INTENT");call(launcher,"setType",launchType);
    Object instruction=api("buildInstruction",launcher);call(instruction,"setDialogId",dialog);
    if(!transaction.isEmpty())call(call(instruction,"getHeader"),"setTransactionId",transaction);
    return instruction;
  }
  /** Tests only serialization and the original native data parser; never dispatches an operation. */
  private static boolean validateNativeLaunchTemplate(){
    try{
      String dialog=String.valueOf(api("randomRequestId",false));
      Object instruction=api("readInstruction",api("toJsonString",nativeLaunchInstruction(dialog,"")));
      Object model=XposedHelpers.callStaticMethod(XposedHelpers.findClass("cc0.j0",loader),"parseLaunchAppData",instruction);
      Object intentModel=call(model,"getIntentModel");
      boolean valid=targetPackage.equals(call(model,"getIdentifier"))&&dialog.equals(call(model,"getDialogId"))&&"ANDROID_INTENT".equals(call(model,"getType"))&&intentModel!=null&&targetPackage.equals(call(intentModel,"getPkgName"))&&"activity".equalsIgnoreCase(String.valueOf(call(intentModel,"getType")));
      log(valid?"nativeLaunchTemplateSelfCheckPassed":"nativeLaunchTemplateSelfCheckFailed");return valid;
    }catch(Throwable e){Log.e("XiaoAiAllowList","{\"experiment\":\"direct-entry\",\"event\":\"nativeLaunchTemplateSelfCheckError\",\"type\":\""+e.getClass().getSimpleName()+"\"}");return false;}
  }
  /** Adds only the user's explicitly requested app launch, through the native operation queue. */
  private static synchronized Object prepareInitialPush(Object instruction){
    if(!prepareLaunch||launchInserted||!promoted||active.isEmpty()||!enabled()||expires==0||SystemClock.elapsedRealtime()>expires||SystemClock.elapsedRealtime()-promotedAt>15000L)return instruction;
    try{
      if(!"General.Push".equals(call(instruction,"getFullName")))return instruction;
      Object payload=call(instruction,"getPayload");
      Object parsed=api("readInstructions",call(payload,"getInstructions"));
      if(!(parsed instanceof List))return instruction;
      List<?> children=(List<?>)parsed;int initIndex=-1;Object init=null;boolean hasLaunch=false;boolean bound=active.equals(optionalString(call(instruction,"getDialogId")))||active.equals(optionalString(call(payload,"getDialogId")));
      for(int i=0;i<children.size();i++){
        Object child=children.get(i);String name=String.valueOf(call(child,"getFullName"));
        if("Launcher.LaunchApp".equals(name))hasLaunch=true;
        if("Application.UIAgentPhase".equals(name)&&"INIT".equals(String.valueOf(call(call(child,"getPayload"),"getUiAgentPhaseStatus")))){initIndex=i;init=child;}
      }
      if(init==null)return instruction;
      Object initPayload=call(init,"getPayload");
      String childDialog=optionalString(call(init,"getDialogId")),phaseDialog=optionalString(call(initPayload,"getDialogId"));
      bound=bound||active.equals(childDialog)||active.equals(phaseDialog);
      boolean appMatch=targetLabel.equals(optionalString(call(initPayload,"getAppName")));
      if(!bound&&!appMatch){log("initialPushBindingRejected");return instruction;}
      if(hasLaunch){launchInserted=true;log("nativeLaunchAlreadyPresent");return instruction;}
      String dialog=!childDialog.isEmpty()?childDialog:!phaseDialog.isEmpty()?phaseDialog:active;
      String transaction=optionalString(call(call(init,"getHeader"),"getTransactionId"));
      Object launchInstruction=nativeLaunchInstruction(dialog,transaction);
      List<Object> patched=new ArrayList<Object>(children);patched.add(initIndex+1,launchInstruction);
      Object replacement=api("readInstruction",api("toJsonString",instruction));
      call(call(replacement,"getPayload"),"setInstructions",api("writeInstructions",patched));
      launchInserted=true;log(bound?"insertedNativeLaunchStrongBinding":"insertedNativeLaunchAppNameBinding");
      return replacement;
    }catch(Throwable e){Log.e("XiaoAiAllowList","{\"experiment\":\"direct-entry\",\"event\":\"nativeLaunchPreparationError\",\"type\":\""+e.getClass().getSimpleName()+"\"}");return instruction;}
  }
  private static synchronized void patch(Object event) {
    if(!"skill-page".equals(mode))return;
    if(expires==0||SystemClock.elapsedRealtime()>expires)return;
    if(!enabled()){expires=0;active="";log("disabled");return;}
    try{
      String name=(String)call(event,"getFullName");
      String start="voice".equals(input)?"SpeechRecognizer.Recognize":"Nlp.Request";
      if(!start.equals(name)&&!"General.ContextUpdate".equals(name))return;
      String id=(String)call(event,"getId");
      if(id==null||id.isEmpty())return;
      if(start.equals(name)){
        if(active.isEmpty())active=id;
        else if(!active.equals(id)){expires=0;active="";log("closedOnNextRequest");return;}
      }
      if(!active.equals(id))return;
      Object old=call(event,"getContexts");
      if(!(old instanceof List))return;
      List<Object> contexts=new ArrayList<Object>(); boolean dialog=false,state=false;
      for(Object c:(List<?>)old){
        String n=c==null?"":String.valueOf(call(c,"getFullName"));
        if("Dialog.DialogState".equals(n)){contexts.add(patched(c,n));dialog=true;}
        else if("Application.State".equals(n)){contexts.add(patched(c,n));state=true;}
        else contexts.add(c);
      }
      if(!dialog)contexts.add(patched(newContext("com.xiaomi.ai.api.Dialog$DialogState"),"Dialog.DialogState"));
      if(!state)contexts.add(patched(newContext("com.xiaomi.ai.api.Application$State"),"Application.State"));
      call(event,"setContext",contexts);
      log(start.equals(name)?"patchedOriginalRequest":"patchedAssociatedContextUpdate");
    }catch(Throwable e){Log.e("XiaoAiAllowList","{\"experiment\":\"skill-page\",\"event\":\"contextPatchError\",\"type\":\""+e.getClass().getSimpleName()+"\"}");}
  }
  @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp){
    if(!"com.miui.voiceassist".equals(lp.packageName)||!lp.packageName.equals(lp.processName))return;
    XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){
      @Override protected void afterHookedMethod(MethodHookParam p){
        if(installed)return;
        try{
          app=(Context)p.args[0];
          if(app.getPackageManager().getPackageInfo(lp.packageName,0).getLongVersionCode()!=507013034L){log("contextExperimentVersionMismatch");return;}
          loader=lp.classLoader;
          XposedHelpers.findAndHookMethod("ul.j",loader,"postEvent",XposedHelpers.findClass("com.xiaomi.ai.api.common.Event",loader),new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){
              if("direct-entry".equals(mode))p.args[0]=promote(p.args[0]);else patch(p.args[0]);
            }
          });
          XposedBridge.hookAllMethods(XposedHelpers.findClass("xl.f",loader),"interceptInstruction",new XC_MethodHook(){
            @Override protected void beforeHookedMethod(MethodHookParam p){if(p.args.length>0)p.args[0]=prepareInitialPush(p.args[0]);}
          });
          app.registerReceiver(new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){if(i!=null&&ACTION.equals(i.getAction()))arm(i);}},new IntentFilter(ACTION),"android.permission.DUMP",null,Context.RECEIVER_EXPORTED);
          installed=true;log("loadedDisarmed");
        }catch(Throwable e){Log.e("XiaoAiAllowList","{\"experiment\":\"skill-page\",\"event\":\"contextExperimentInitError\",\"type\":\""+e.getClass().getSimpleName()+"\"}");}
      }
    });
  }
}
