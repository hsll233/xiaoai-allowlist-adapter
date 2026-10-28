package local.codex.xiaoaiallowlist;

import de.robv.android.xposed.XposedHelpers;
import java.util.*;

/** A snapshot of a live official task. Never creates a new first-flow task. */
final class FollowupSession {
  private static final java.util.concurrent.atomic.AtomicLong stopEpoch=new java.util.concurrent.atomic.AtomicLong();
  static void stopped(){stopEpoch.incrementAndGet();}
  final String dialog,skill,appName;final List<?> contextInfo;
  private FollowupSession(String dialog,String skill,String appName,List<?> contextInfo){this.dialog=dialog;this.skill=skill;this.appName=appName;this.contextInfo=contextInfo;}
  private static Object call(Object o,String m,Object...a){return NativeLaunchPlan.call(o,m,a);}
  private static String string(Object o){return NativeLaunchPlan.string(o);}
  private static Object data(ClassLoader l){return XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.e",l),"a");}
  static int priority(ClassLoader l){Object manager=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.j",l),"a");return ((Number)call(call(manager,"getCurrentState"),"getPriority")).intValue();}
  static FollowupSession capture(ClassLoader l,boolean requireTakeover){
    int priority=priority(l);if(priority<0||priority>=5)return null;
    Object controller=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.xiaomi.voiceassist.baselibrary.uiagent.b",l),"a");
    if(requireTakeover&&!Boolean.TRUE.equals(call(controller,"is_uiagent_takeover_state$uiagent_release")))return null;
    Object data=data(l);String dialog=string(call(data,"getRelevantDialogId")),skill=string(call(data,"getCurrentAgentSkillName")),app=string(call(data,"getAppName"));
    if(dialog.isEmpty()||skill.isEmpty()||app.isEmpty())return null;
    Object infos=call(data,"getCurrentContextInfo");
    return new FollowupSession(dialog,skill,app,infos instanceof List?new ArrayList<Object>((List<?>)infos):null);
  }
  boolean current(ClassLoader l){int p=priority(l);return p>=0&&p<5&&dialog.equals(string(call(data(l),"getRelevantDialogId")));}
  boolean submit(ClassLoader l,String query){
    if(query==null||query.trim().isEmpty()||query.length()>2048||!current(l))return false;
    Object manager=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.i",l),"a");
    call(manager,"reportResumeUIAgent",dialog,skill,false,appName,query,contextInfo);
    return true; // Native method was invoked; actual network acceptance is observed separately.
  }
  boolean submitNlp(ClassLoader l,String query){
    if(query==null||query.trim().isEmpty()||query.length()>2048||!current(l))return false;
    Object controller=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.xiaomi.voiceassist.baselibrary.uiagent.b",l),"a");
    call(controller,"set_uiagent_takeover_state$uiagent_release",true);
    Object nlp=XposedHelpers.newInstance(XposedHelpers.findClass("t20.c",l));call(nlp,"setTextToProcess",query);
    Object request=XposedHelpers.newInstance(XposedHelpers.findClass("y00.x0$d",l));
    call(request,"setEventLevel",1);call(request,"needNlp");call(request,"setNlpRequest",nlp);
    Object helper=XposedHelpers.callStaticMethod(XposedHelpers.findClass("y00.h",l),"getInstance");
    return Boolean.TRUE.equals(call(helper,"sendEvent",request));
  }
  boolean restart(final ClassLoader l,final String query){
    if(query==null||query.trim().isEmpty()||query.length()>2048||!IntentPolicy.adviceAction(query)||!current(l))return false;
    final Object manager=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("yy.i",l),"a");
    call(manager,"onClientStop","action_stop_change_goal","",false,false);
    if(priority(l)!=5)return false;
    final long generation=stopEpoch.get();
    final String stoppedRid=string(call(data(l),"getCurrentRid"));
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable(){public void run(){try{
      if(!VoiceEntry.entryEnabled()||generation!=stopEpoch.get()||priority(l)!=5||!stoppedRid.equals(string(call(data(l),"getCurrentRid")))){
        android.util.Log.i("XiaoAiAllowList","{\"event\":\"cancelledStaleAdviceRestart\"}");return;
      }
      Object controller=XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.xiaomi.voiceassist.baselibrary.uiagent.b",l),"a");
      call(controller,"set_uiagent_takeover_state$uiagent_release",false);
      String newDialog=string(NativeLaunchPlan.api(l,"randomRequestId",false));
      call(manager,"reportResumeUIAgent",newDialog,skill,true,appName,TaskPrompt.forGoal(query),contextInfo);
      android.util.Log.i("XiaoAiAllowList","{\"event\":\"submittedChangedGoalAsNewOfficialTask\"}");
    }catch(Throwable e){android.util.Log.e("XiaoAiAllowList","{\"event\":\"adviceRestartError\",\"type\":\""+e.getClass().getSimpleName()+"\"}");}}},700L);
    return true;
  }
  static void verifyBindings(ClassLoader l){
    Class<?> data=XposedHelpers.findClass("yy.e",l);
    for(String method:new String[]{"getRelevantDialogId","getCurrentAgentSkillName","getAppName","getCurrentContextInfo"})XposedHelpers.findMethodExact(data,method);
    XposedHelpers.findMethodExact(XposedHelpers.findClass("com.xiaomi.voiceassist.baselibrary.uiagent.b",l),"is_uiagent_takeover_state$uiagent_release");
    XposedHelpers.findMethodExact(XposedHelpers.findClass("yy.i",l),"reportResumeUIAgent",String.class,String.class,boolean.class,String.class,String.class,List.class);
  }
}
