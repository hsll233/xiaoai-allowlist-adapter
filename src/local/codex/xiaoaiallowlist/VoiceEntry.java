package local.codex.xiaoaiallowlist;

import android.app.Application;
import android.content.*;
import android.os.*;
import android.util.Log;
import org.json.*;
import java.util.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Official ASR followed by one native NLP handoff for ordinary, single-turn main voice. */
public final class VoiceEntry implements IXposedHookLoadPackage {
  private static Context app; private static ClassLoader loader; private static boolean installed;
  private static Pending pending;
  private static Handler main;
  private static final class Pending {
    final String id; final boolean wantsTts;final FollowupSession followup;final long created=SystemClock.elapsedRealtime(); boolean forwarded;
    Pending(String id,boolean wantsTts,FollowupSession followup){this.id=id;this.wantsTts=wantsTts;this.followup=followup;}
  }
  private static Object call(Object o,String m,Object...a){return NativeLaunchPlan.call(o,m,a);}
  private static Object api(String m,Object...a){return NativeLaunchPlan.api(loader,m,a);}
  private static String string(Object o){return NativeLaunchPlan.string(o);}
  private static void log(String event){Log.i("XiaoAiAllowList","{\"voiceEntry\":true,\"event\":\""+event+"\"}");}
  private static void error(String event,Throwable e){Throwable root=e;for(int i=0;i<5&&root.getCause()!=null;i++)root=root.getCause();Log.e("XiaoAiAllowList","{\"voiceEntry\":true,\"event\":\""+event+"\",\"type\":\""+root.getClass().getSimpleName()+"\"}");}
  private static boolean enabled(){try{Bundle b=app.getContentResolver().call(SettingsProvider.URI,"get",null,null);return b!=null&&b.getBoolean("enabled",false)&&b.getBoolean("automaticVoiceEntry",false);}catch(Throwable e){return false;}}
  static boolean entryEnabled(){return enabled();}
  private static boolean continuous(){return Boolean.TRUE.equals(XposedHelpers.callStaticMethod(XposedHelpers.findClass("i10.g",loader),"isInContinuousDialog"));}
  private static boolean disabled(Object event,String flag){
    Object list=call(event,"getContexts");
    if(list instanceof List)for(Object context:(List<?>)list){
      if(context==null||!"Execution.RequestControl".equals(call(context,"getFullName")))continue;
      Object flags=call(call(context,"getPayload"),"getDisabled");
      if(flags instanceof List)for(Object f:(List<?>)flags)if(flag.equals(String.valueOf(f)))return true;
    }
    return false;
  }
  private static boolean activeTask(){
    Object manager=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.j",loader),"a");
    int priority=((Number)call(call(manager,"getCurrentState"),"getPriority")).intValue();return priority>=0&&priority<5;
  }
  private static Object asrOnly(Object event) throws Exception {
    JSONObject json=new JSONObject(String.valueOf(api("toJsonString",event)));
    String key=json.has("context")?"context":"contexts"; JSONArray contexts=json.optJSONArray(key);
    if(contexts==null){contexts=new JSONArray();json.put(key,contexts);}
    boolean found=false;
    for(int i=0;i<contexts.length();i++){
      JSONObject c=contexts.optJSONObject(i),h=c==null?null:c.optJSONObject("header");
      if(h==null||!"Execution".equals(h.optString("namespace"))||!"RequestControl".equals(h.optString("name")))continue;
      found=true;JSONObject payload=c.optJSONObject("payload");if(payload==null){payload=new JSONObject();c.put("payload",payload);}
      JSONArray disabled=payload.optJSONArray("disabled");if(disabled==null){disabled=new JSONArray();payload.put("disabled",disabled);}
      for(String flag:new String[]{"NLP","LLM_TTS_AUDIO"}){boolean has=false;for(int j=0;j<disabled.length();j++)has|=flag.equals(disabled.optString(j));if(!has)disabled.put(flag);}
    }
    if(!found){Object c=XposedHelpers.callStaticMethod(XposedHelpers.findClass("f10.b0",loader),"buildDisableTtsAndNlpContext");contexts.put(new JSONObject(String.valueOf(api("toJsonString",c))));}
    JSONObject payload=json.optJSONObject("payload");
    if(payload!=null){JSONArray deletes=payload.optJSONArray("delete_items");if(deletes!=null){JSONArray keep=new JSONArray();for(int i=0;i<deletes.length();i++){JSONObject d=deletes.optJSONObject(i);if(d==null||!"Execution".equals(d.optString("namespace"))||!"RequestControl".equals(d.optString("name")))keep.put(deletes.get(i));}payload.put("delete_items",keep);}}
    Object copy=api("readEvent",json.toString());
    if(copy==null)throw new IllegalStateException("Native event clone failed");
    return copy;
  }
  private static void selfCheck() throws Exception {
    Object payload=XposedHelpers.newInstance(XposedHelpers.findClass("com.xiaomi.ai.api.SpeechRecognizer$Recognize",loader));
    List<Object> contexts=new ArrayList<Object>();
    Object controlPayload=XposedHelpers.newInstance(XposedHelpers.findClass("com.xiaomi.ai.api.Execution$RequestControl",loader));
    call(controlPayload,"setDisabled",new ArrayList<Object>());
    contexts.add(api("buildContext",controlPayload));
    Object original=api("buildEvent",payload,contexts,"codex-voice-readonly-selfcheck");
    String before=String.valueOf(api("toJsonString",original));Object copy=asrOnly(original);
    if(copy==null||!before.equals(String.valueOf(api("toJsonString",original)))||!string(call(original,"getId")).equals(string(call(copy,"getId"))))throw new IllegalStateException("Copy or ID changed");
    Object control=((List<?>)call(copy,"getContexts")).get(0);List<?> flags=(List<?>)call(call(control,"getPayload"),"getDisabled");
    boolean nlp=false,tts=false;for(Object flag:flags){nlp|="NLP".equals(String.valueOf(flag));tts|="LLM_TTS_AUDIO".equals(String.valueOf(flag));}
    if(!nlp||!tts)throw new IllegalStateException("Request control flags missing");
    log("readOnlyVoiceContextSelfCheckPassed");
  }
  private static synchronized Object beforeEvent(Object engine,Object event){
    try{
      if(!enabled()||!"main".equals(call(engine,"getEnginePrefix")))return event;
      String name=String.valueOf(call(event,"getFullName")),id=string(call(event,"getId"));long now=SystemClock.elapsedRealtime();
      if("SpeechRecognizer.Recognize".equals(name)){
        pending=null;
        if(id.isEmpty()||disabled(event,"NLP")){log("preservedExistingAsrOnlyRequest");return event;}
        if(continuous()){log("skippedContinuousDialog");return event;}
        FollowupSession followup=null;
        if(activeTask()){
          // Voice wakeup can enqueue ASR before the takeover UI sets its flag.
          // Keep the live task snapshot; decide from the final recognized text.
          followup=FollowupSession.capture(loader,false);
          if(followup==null){log("preservedActiveTaskVoice");return event;}
        }
        Object scenario=call(call(event,"getPayload"),"getScenario");
        if(Boolean.TRUE.equals(call(scenario,"isPresent"))){log("skippedSpecialScenario");return event;}
        Object asr=call(call(event,"getPayload"),"getAsr");
        if(Boolean.TRUE.equals(call(asr,"isPresent"))&&Boolean.TRUE.equals(call(call(call(asr,"get"),"getScenario"),"isPresent"))){log("skippedSpecialAsrScenario");return event;}
        boolean wantsTts=!disabled(event,"LLM_TTS_AUDIO");
        Object patched=asrOnly(event);pending=new Pending(id,wantsTts,followup);log(followup==null?"capturedAutomaticVoiceAsrOnly":"capturedTakeoverAdviceForOriginalTask");return patched;
      }
      Pending p=pending;
      if(p!=null&&!p.forwarded&&now-p.created<60000L&&p.id.equals(id)&&"General.ContextUpdate".equals(name)){Object patched=asrOnly(event);log("keptAssociatedContextAsrOnly");return patched;}
    }catch(Throwable e){error("voiceEventError",e);}
    return event;
  }
  private static synchronized void afterFinal(Object processor,Object instruction){
    try{
      final Pending p=pending;
      if(p==null||p.forwarded||SystemClock.elapsedRealtime()-p.created>60000L||!enabled())return;
      if(!p.id.equals(string(call(instruction,"getDialogId"))))return;
      Object payload=call(instruction,"getPayload");if(!Boolean.TRUE.equals(call(payload,"isFinal")))return;
      Object engine=XposedHelpers.getObjectField(processor,"b");if(!"main".equals(call(engine,"getEnginePrefix")))return;
      List<?> results=(List<?>)call(payload,"getResults");if(results==null||results.isEmpty())return;
      final String query=string(call(results.get(0),"getText"));if(query.trim().isEmpty()){log("emptyFinalNotForwarded");return;}
      p.forwarded=true;log(p.followup==null?"receivedFinalQueuedNativeNlp":"receivedFinalQueuedNativeFollowup");
      main.post(new Runnable(){@Override public void run(){
        try{
          if(!enabled()||pending!=p){log("cancelledBeforeHandoff");return;}
          if(p.followup!=null&&IntentPolicy.adviceAction(query)&&AutoEntry.sameAdviceApp(query,p.followup.appName)){
            log(p.followup.restart(loader,query)?"changedGoalRestartQueued":"cancelledStaleFollowup");return;
          }
          Object nlp=XposedHelpers.newInstance(XposedHelpers.findClass("t20.c",loader));call(nlp,"setTextToProcess",query);
          Object tts=XposedHelpers.newInstance(XposedHelpers.findClass("t20.f",loader));
          Object request=XposedHelpers.newInstance(XposedHelpers.findClass("y00.x0$d",loader));
          call(request,"setEventLevel",1);call(request,"needNlp");call(request,"setNlpRequest",nlp);
          if(p.wantsTts){call(request,"needTts");call(request,"setTtsRequest",tts);}
          Object helper=XposedHelpers.callStaticMethod(XposedHelpers.findClass("y00.h",loader),"getInstance");
          log(Boolean.TRUE.equals(call(helper,"sendEvent",request))?"nativeNlpHandoffAccepted":"nativeNlpHandoffRejected");
        }catch(Throwable e){error("voiceHandoffError",e);}
      }});
    }catch(Throwable e){error("voiceFinalError",e);}
  }
  @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp){
    if(!"com.miui.voiceassist".equals(lp.packageName)||!lp.packageName.equals(lp.processName))return;
    XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){
      install((Context)p.args[0],lp);
    }});
  }
  static synchronized void install(Context context,XC_LoadPackage.LoadPackageParam lp){
      if(installed)return;
      try{
        app=context;loader=lp.classLoader;main=new Handler(Looper.getMainLooper());
        if(!HostCompatibility.allow(app,loader))return;
        selfCheck();
        FollowupSession.verifyBindings(loader);log("followupNativeBindingsVerified");
        XposedBridge.hookAllMethods(XposedHelpers.findClass("yy.i",loader),"onClientStop",new XC_MethodHook(){
          @Override protected void beforeHookedMethod(MethodHookParam p){FollowupSession.stopped();}
        });
        Class<?> event=XposedHelpers.findClass("com.xiaomi.ai.api.common.Event",loader);
        XposedHelpers.findAndHookMethod("y00.r0",loader,"C0",event,new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam q){q.args[0]=beforeEvent(q.thisObject,q.args[0]);}});
        XposedHelpers.findAndHookMethod("z10.a",loader,"c",XposedHelpers.findClass("com.xiaomi.ai.api.common.Instruction",loader),new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam q){if(!q.hasThrowable())afterFinal(q.thisObject,q.args[0]);}});
        // Shell-only, explicit text-advice diagnostic. Uses the same native submit path as voice.
        ReceiverCompat.diagnosticReceiver(app,new BroadcastReceiver(){@Override public void onReceive(Context c,Intent intent){
          try{
            if(!enabled()||!"pause-and-submit".equals(intent.getStringExtra("command")))return;
            final String query=intent.getStringExtra("query");if(query==null||query.trim().isEmpty()||query.length()>2048)return;
            final FollowupSession session=FollowupSession.capture(loader,false);if(session==null){log("testAdviceNoLiveTask");return;}
            Object manager=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.i",loader),"a");
            call(manager,"onClientPause","action_pause_takeover_wakeup");log("testAdvicePauseRequested");
            main.postDelayed(new Runnable(){@Override public void run(){try{
              if(!enabled()||FollowupSession.priority(loader)!=2){log("testAdviceNotPaused");return;}
              log(session.restart(loader,query)?"testAdviceRestartQueued":"testAdviceStale");
            }catch(Throwable e){error("testAdviceSubmitError",e);}}},1500L);
          }catch(Throwable e){error("testAdviceError",e);}
        }},new IntentFilter("local.codex.xiaoaiallowlist.TEST_ADVICE"));
        installed=true;log("loadedAutomaticSingleTurnVoiceEntry");HostCompatibility.component(app,"voice",true);
      }catch(Throwable e){HostCompatibility.component(app,"voice",false);error("voiceEntryInitError",e);}
  }
}

