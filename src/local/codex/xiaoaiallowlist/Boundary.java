package local.codex.xiaoaiallowlist;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.os.SystemClock;
import java.util.*;
import org.json.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Passive protocol-boundary observer. No business arguments or results are changed. */
public final class Boundary implements IXposedHookLoadPackage {
  private static final String TAG="XiaoAiListBoundary";
  private static final String[] PACKAGES={"com.discord","com.xiaomi.vipaccount","com.bilibili.app.in","tv.danmaku.bili","com.miui.voiceassist","com.xiaomi.aicr","com.google.android.youtube","com.google.android.apps.youtube.music"};
  private static boolean installed;
  private static int nextNumber;
  private static ClassLoader loader;
  private static final ThreadLocal<Object> rawChannel=new ThreadLocal<Object>();
  private static Session last;
  private static final Map<String,Session> sessions=new LinkedHashMap<String,Session>();
  private static final Map<String,String> labels=new LinkedHashMap<String,String>();
  private static final class Session {
    int number; long started=SystemClock.elapsedRealtime(); boolean selected,decided;
    final List<String> pending=new ArrayList<String>();
    Session(){number=++nextNumber;}
  }
  private static Object call(Object o,String method){return o==null?null:XposedHelpers.callMethod(o,method);}
  private static Object safe(Object o,String method){try{return optional(call(o,method));}catch(Throwable e){return null;}}
  private static Object optional(Object o){
    if(o==null||o instanceof String||o instanceof Number||o instanceof Boolean||o instanceof List||o instanceof Enum)return o;
    try{return Boolean.TRUE.equals(call(o,"isPresent"))?call(o,"get"):null;}catch(Throwable e){return o;}
  }
  private static String id(Object o,String method){Object v=safe(o,method);return v instanceof String?(String)v:"";}
  private static JSONObject obj(Object... pairs){JSONObject o=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)o.put(String.valueOf(pairs[i]),pairs[i+1]);}catch(Exception e){}return o;}
  private static void log(JSONObject o){Log.i(TAG,o.toString());}
  private static String protocol(Object o){String s=String.valueOf(safe(o,"getFullName"));return s.matches("[A-Za-z][A-Za-z0-9_.]{1,100}")?s:"unknown";}
  private static String pkg(Object v){for(String p:PACKAGES)if(p.equals(v))return p;return "other";}
  private static Object scalar(Object v){v=optional(v);if(v instanceof Number||v instanceof Boolean)return v;if(v instanceof Enum)return ((Enum<?>)v).name();return v==null?"absent":"present";}
  private static synchronized String label(Object v){
    if(!(v instanceof String)||((String)v).isEmpty())return "absent";
    String s=(String)v;String old=labels.get(s);if(old!=null)return old;
    if(labels.size()>64)return "present";
    String name="S"+(labels.size()+1);labels.put(s,name);return name;
  }
  private static synchronized Session request(String key){
    Session s=sessions.get(key);if(s==null){s=new Session();if(!key.isEmpty())sessions.put(key,s);last=s;}
    while(sessions.size()>32)sessions.remove(sessions.keySet().iterator().next());return s;
  }
  private static synchronized Session associate(String key,String secondary,boolean create){
    Session s=sessions.get(key);if(s==null&&!secondary.isEmpty())s=sessions.get(secondary);
    if(s==null&&create)return request(key);
    if(s==null&&last!=null&&SystemClock.elapsedRealtime()-last.started<180000)s=last;
    return s;
  }
  private static synchronized void emit(Session s,JSONObject data){
    if(s==null||SystemClock.elapsedRealtime()-s.started>180000)return;
    try{data.put("request","R"+s.number);data.put("elapsedMs",SystemClock.elapsedRealtime()-s.started);}catch(Exception ignored){}
    if(s.selected)log(data);else if(!s.decided&&s.pending.size()<180)s.pending.add(data.toString());
  }
  private static synchronized void choose(Session s,String query){
    if(s==null||s.decided)return;s.decided=true;String q=query.toLowerCase(Locale.ROOT);
    boolean community=q.contains("小米社区")||q.contains("xiaomi community");
    boolean discord=q.contains("discord")||q.contains("迪斯科德");
    boolean bili=q.contains("bilibili")||q.contains("哔哩哔哩")||q.contains("b站");
    s.selected=true;
    if(s.selected){log(obj("event","selected","request","R"+s.number,"community",community,"discord",discord,"bilibili",bili,
      "hasOpenAction",q.contains("打开"),"hasEnterAction",q.contains("进入"),"hasCheckinAction",q.contains("签到")||q.contains("打卡"),"hasMineFragment",q.contains("我的")||q.contains("个人"),"hasProfilePage",q.contains("我的页面")||q.contains("个人主页")||q.contains("个人中心"),"hasPopularPage",q.contains("热门"),"hasFriendsPage",q.contains("好友")));for(String line:s.pending)Log.i(TAG,line);}
    s.pending.clear();
  }
  private static Object field(JSONObject o,String key){Object v=o==null?null:o.opt(key);return v==null||v==JSONObject.NULL?"absent":v instanceof Boolean||v instanceof Number?v:"present";}
  private static JSONObject members(JSONArray a,boolean items){
    JSONObject result=new JSONObject();try{for(String target:PACKAGES){boolean found=false;
      if(a!=null)for(int i=0;i<a.length();i++){Object x=a.opt(i);String p=items&&x instanceof JSONObject?((JSONObject)x).optString("pkg_name",""):String.valueOf(x);if(target.equals(p))found=true;}
      result.put(target,a==null?"absent":found);
    }}catch(Exception ignored){}return result;
  }
  private static JSONObject context(JSONObject c){
    JSONObject h=c.optJSONObject("header"),p=c.optJSONObject("payload");
    if(h==null||p==null)return obj("context","invalid");
    String name=h.optString("namespace")+"."+h.optString("name");if(!name.matches("[A-Za-z][A-Za-z0-9_.]{1,100}"))name="unknown";JSONObject r=obj("context",name);
    try{
      if("Application.State".equals(name)){
        for(String k:new String[]{"is_uiagent_state","is_uiagent_takeover_state","is_cross_device_uiagent_state"})r.put(k,field(p,k));
        JSONObject app=p.optJSONObject("app_state");
        for(String k:new String[]{"support_smart_app","new_aireco_portal","is_icon_hidden","interaction_mode","screen_permission_state"})r.put(k,field(app,k));
        r.put("support_screen_permission_safety_privacy",field(p.optJSONObject("privacy_setting"),"support_screen_permission_safety_privacy"));
        r.put("skill",label(p.opt("skill_id")));
      }else if("Application.AppDetailV1".equals(name)){
        r.put("available",members(p.optJSONArray("available_apps"),true));r.put("forbidden",members(p.optJSONArray("forbidden_apps"),false));
        Object fg=p.opt("foreground_app");if(fg instanceof JSONObject)fg=((JSONObject)fg).optString("pkg_name");r.put("foreground",pkg(fg));
        JSONArray delta=p.optJSONArray("delta_apps");r.put("deltaCount",delta==null?"absent":delta.length());
      }else if("BuiltinSkills.PreferredSkills".equals(name)){
        JSONArray hints=p.optJSONArray("name");r.put("nameCount",hints==null?0:hints.length());boolean official=false;
        if(hints!=null)for(int i=0;i<hints.length();i++)if("llmControlCopilotSkill".equals(hints.optString(i)))official=true;
        r.put("observedUiAgentSkillHint",official);
      }else if("Application.AvailableApps".equals(name))r.put("available",members(p.optJSONArray("apps"),true));
      else if("Agent.ActionState".equals(name)){
        for(String k:new String[]{"support_ddf","ddf_ids","supported_ddf_ids","support_ddf_ids"})if(p.has(k)){JSONArray a=p.optJSONArray(k);r.put(k+"Count",a==null?"present":a.length());if(a!=null)r.put("ddfSetFingerprint",Integer.toHexString(a.toString().hashCode()));}
        r.put("payloadFieldCount",p.length());
      }else if("General.RequestState".equals(name)){
        r.put("origin",label(p.opt("origin")));r.put("launchSource",label(p.opt("launch_source")));r.put("is_init_wakeup",field(p,"is_init_wakeup"));
        r.put("capabilities_version",field(p.optJSONObject("client_info"),"capabilities_version"));
      }else if("Dialog.DialogState".equals(name)){
        for(String k:new String[]{"continuous_dialog","user_in_skill_page","is_new_ui_request","deepseek_switch","deep_research_switch","local_search_switch","scene"})r.put(k,field(p,k));
        r.put("userSkill",label(p.opt("user_in_skill_id")));
        r.put("officialUiAgentSkillPage","llmControlCopilotSkill".equals(p.optString("user_in_skill_id")));
      }else if("Execution.RequestControl".equals(name)){
        JSONArray disabled=p.optJSONArray("disabled");boolean nlp=false,tts=false;
        if(disabled!=null)for(int i=0;i<disabled.length();i++){nlp|="NLP".equals(disabled.optString(i));tts|="LLM_TTS_AUDIO".equals(disabled.optString(i));}
        r.put("nlpDisabled",nlp);r.put("ttsDisabled",tts);
      }else if("General.RenewSession".equals(name))r.put("payloadFieldCount",p.length());
    }catch(Exception ignored){}return r;
  }
  private static void outgoing(Object wrapper,String stage,Object result){
    if(wrapper==null||!"com.xiaomi.ai.core.d".equals(wrapper.getClass().getName()))return;
    try{
      Object original=call(wrapper,"getOriginal");if(!(original instanceof String))return;
      JSONObject event=new JSONObject((String)original),h=event.optJSONObject("header"),p=event.optJSONObject("payload");
      if(h==null)return;String name=h.optString("namespace")+"."+h.optString("name");
      boolean resume="Application.ResumeUIAgent".equals(name);
      boolean start="SpeechRecognizer.Recognize".equals(name)||"Nlp.Request".equals(name)||resume;
      if(!start&&!"General.ContextUpdate".equals(name)&&!"General.UpdateGlobalContexts".equals(name)&&!"General.EngineCreated".equals(name)&&!"General.GeneralInfoUploadFinished".equals(name)&&!"Application.PauseUIAgent".equals(name)&&!"Application.CancelUIAgent".equals(name)&&!"Application.ResumeUIAgent".equals(name)&&!"Application.UploadResourceResult".equals(name)&&!name.startsWith("Application.CheckApps"))return;
      String key=h.optString("id","");Session s=associate(key,"",start);
      if("Nlp.Request".equals(name)&&p!=null)choose(s,p.optString("query",""));
      if(resume&&p!=null)choose(s,p.optString("relevant_query",""));
      JSONObject data=obj("stage",stage,"event",name,"result",scalar(result));
      if("Application.UploadResourceResult".equals(name)&&p!=null){String code=p.optString("code","absent");data.put("uploadCode",code.matches("[A-Z0-9_]{1,80}")?code:"present");data.put("checkAccessCode",field(p,"check_access_result_code"));data.put("screenCaptureCode",field(p,"screen_capture_result_code"));data.put("descriptionPresent",!p.optString("desc","").isEmpty());}
      if(resume&&p!=null){data.put("isFirstFlow",field(p,"is_first_flow"));data.put("appNamePresent",p.has("app_name"));data.put("decoupling",field(p,"is_decoupling_version"));data.put("officialSkillKnown","llmControlCopilotSkill".equals(p.optString("relevant_skill_name")));data.put("targetYouTube","YouTube".equalsIgnoreCase(p.optString("app_name")));data.put("targetCommunity","小米社区".equals(p.optString("app_name")));data.put("targetDiscord","Discord".equals(p.optString("app_name")));}
      if("General.EngineCreated".equals(name))data.put("resourceIdPresent",!h.optString("resource_id","").isEmpty());
      JSONArray contexts=event.optJSONArray("context");if(contexts==null)contexts=event.optJSONArray("contexts");JSONArray names=new JSONArray();List<JSONObject> summaries=new ArrayList<JSONObject>();
      if(contexts!=null)for(int i=0;i<contexts.length();i++){JSONObject c=contexts.optJSONObject(i);if(c!=null){JSONObject summary=context(c);String n=summary.optString("context");names.put(n);if(summary.length()>1)summaries.add(summary);}}
      data.put("contextNames",names);
      if("General.ContextUpdate".equals(name)&&p!=null){data.put("incremental",field(p,"enable_incremental_update"));JSONArray deletes=p.optJSONArray("delete_items"),list=new JSONArray();
        if(deletes!=null)for(int i=0;i<deletes.length();i++){JSONObject d=deletes.optJSONObject(i);if(d!=null){String v=d.optString("namespace")+"."+d.optString("name");if(v.matches("[A-Za-z][A-Za-z0-9_.]{1,100}"))list.put(v);}}
        data.put("deleteContexts",list);
      }
      emit(s,data);
      for(JSONObject summary:summaries){summary.put("stage",stage);summary.put("event",name);emit(s,summary);}
    }catch(Throwable e){log(obj("error","outgoing","stage",stage,"type",e.getClass().getSimpleName()));}
  }
  private static JSONObject detail(Object ins){
    String name=protocol(ins);Object p=safe(ins,"getPayload");JSONObject r=obj("instruction",name);
    try{
      Object header=safe(ins,"getHeader");r.put("offline",scalar(safe(header,"getIsOffline")));
      if("Application.UIAgentPhase".equals(name)){
        r.put("phase",scalar(safe(p,"getUiAgentPhaseStatus")));r.put("subtype",scalar(safe(p,"getUiAgentSubType")));r.put("mcp",scalar(safe(p,"isUiAgentMcp")));r.put("skill",label(safe(p,"getAgentSkillName")));
        Object list=safe(p,"getWhiteAppList");r.put("whiteAppListPresent",list instanceof List);
        if(list instanceof List){r.put("whiteAppListCount",((List<?>)list).size());JSONArray entries=new JSONArray();for(Object entry:(List<?>)list)entries.put(entry);r.put("whiteAppMembership",members(entries,false));}
        r.put("appName",label(safe(p,"getAppName")));r.put("screenshotPackage",pkg(safe(p,"getScreenshotAppPackage")));
        Object phaseMessage=safe(p,"getPhaseMsg");
        if(phaseMessage instanceof String){String text=(String)phaseMessage;r.put("phaseMessage",text.substring(0,Math.min(text.length(),300)));}
      }else if("Application.UIAgent".equals(name)){
        r.put("actionType",scalar(safe(p,"getUiAgentType")));
        Object position=safe(p,"getStartPos");r.put("startX",safe(position,"getX"));r.put("startY",safe(position,"getY"));
      }
      else if("Application.UploadResource".equals(name)){
        r.put("directedArea",scalar(safe(p,"getDirectedArea")));r.put("appPackage",pkg(safe(p,"getAppPackageName")));
      }else if("General.SwitchEngine".equals(name))r.put("engine",scalar(safe(p,"getEngineType")));
      else if("General.GeneralInfoUpload".equals(name)){
        r.put("resourceIdPresent",!id(p,"getResourceId").isEmpty());r.put("resourceTokenPresent",!id(p,"getResourceToken").isEmpty());r.put("uploadType",scalar(safe(p,"getUploadType")));
      }
      else if("General.Push".equals(name))r.put("needLocalExecution",scalar(safe(p,"isNeedLocalExecution")));
      else if("Application.Operate".equals(name))r.put("operation",scalar(safe(p,"getOperation")));
      else if("SpeechSynthesizer.Speak".equals(name)){
        Object value=safe(p,"getText");String reply=value instanceof String?(String)value:"";
        r.put("textPresent",!reply.isEmpty());
        r.put("unsupportedReply",reply.contains("不支持")||reply.contains("暂时无法")||reply.contains("还不能"));
        r.put("installReply",reply.contains("下载")||reply.contains("安装"));
      }
      else if("Application.CheckApps".equals(name)){
        r.put("requireReport",scalar(safe(p,"isRequireReport")));Object list=safe(p,"getApps");JSONArray a=new JSONArray();
        if(list instanceof List)for(Object item:(List<?>)list){String packageName=pkg(safe(item,"getPkgName"));JSONObject x=obj("package",packageName);if(!"other".equals(packageName)){x.put("minVersion",scalar(safe(item,"getMinVersion")));x.put("launched",scalar(safe(item,"isLaunched")));}a.put(x);}r.put("apps",a);
      }
    }catch(Exception ignored){}return r;
  }
  private static void incoming(Object ins,String stage,Object result){
    if(ins==null)return;try{
      String name=protocol(ins),key=id(ins,"getDialogId");Object header=safe(ins,"getHeader"),payload=safe(ins,"getPayload");String tx=id(header,"getTransactionId");
      String secondary=tx;if("General.Push".equals(name)&&secondary.isEmpty())secondary=id(payload,"getDialogId");
      Session s=associate(key,secondary,false);if(s==null)return;
      if("SpeechRecognizer.RecognizeResult".equals(name)&&Boolean.TRUE.equals(safe(payload,"isFinal"))){
        Object list=safe(payload,"getResults");if(list instanceof List)for(Object item:(List<?>)list){Object q=safe(item,"getText");if(q instanceof String&&!((String)q).isEmpty()){choose(s,(String)q);break;}}
      }
      JSONObject data=detail(ins);data.put("stage",stage);data.put("result",scalar(result));
      if("raw".equals(stage)&&rawChannel.get()!=null){data.put("channel",rawChannel.get().getClass().getName());data.put("channelType",scalar(safe(rawChannel.get(),"getType")));}
      data.put("association",sessions.get(key)==s?"requestId":sessions.get(secondary)==s?"relatedId":"recentRequest");emit(s,data);
      if("General.Push".equals(name)){
        Object nodes=safe(payload,"getInstructions");Object parsed=XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.xiaomi.ai.api.common.APIUtils",loader),"readInstructions",nodes);
        if(parsed instanceof List)for(Object child:(List<?>)parsed){JSONObject nested=detail(child);nested.put("stage",stage);nested.put("nested",true);if("raw".equals(stage)&&rawChannel.get()!=null){nested.put("channel",rawChannel.get().getClass().getName());nested.put("channelType",scalar(safe(rawChannel.get(),"getType")));}emit(s,nested);}
      }
    }catch(Throwable e){log(obj("error","incoming","stage",stage,"type",e.getClass().getSimpleName()));}
  }
  private static void hook(String cls,String method,XC_MethodHook callback){try{int count=XposedBridge.hookAllMethods(XposedHelpers.findClass(cls,loader),method,callback).size();log(obj("hook",cls+"."+method,"count",count));}catch(Throwable e){log(obj("hookError",cls+"."+method,"type",e.getClass().getSimpleName()));}}
  private static void wrapperHook(final String cls,final String stage){hook(cls,"postEvent",new XC_MethodHook(){
    @Override protected void beforeHookedMethod(MethodHookParam p){if(p.args.length>0)outgoing(p.args[0],stage+".before",null);}
    @Override protected void afterHookedMethod(MethodHookParam p){if(p.args.length>0)outgoing(p.args[0],stage+".after",p.hasThrowable()?"threw":p.getResult());}
  });}
  private static void install(){
    hook("yy.i","onClientStop",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){
      String reason=p.args.length>0?String.valueOf(p.args[0]):"";
      emit(last,obj("event","nativeStopRequested","reason",reason.matches("[A-Za-z0-9_]{1,100}")?reason:"other"));
    }});
    hook("yy.i","onClientPause",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){
      String reason=p.args.length>0?String.valueOf(p.args[0]):"";
      emit(last,obj("event","nativePauseRequested","reason",reason.matches("[A-Za-z0-9_]{1,100}")?reason:"other"));
    }});
    wrapperHook("ul.j","sdk");wrapperHook("com.xiaomi.ai.core.XMDChannel","wire.XMD");wrapperHook("com.xiaomi.ai.core.h","wire.WS");
    hook("ul.f","onInstruction",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){if(p.args.length>1){rawChannel.set(p.args[0]);try{incoming(safe(p.args[1],"getInstruction"),"raw",null);}finally{rawChannel.remove();}}}});
    hook("xl.f","interceptInstruction",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){incoming(p.args[0],"local.input",null);}});
    hook("xl.f","i",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){incoming(p.args[0],"local.output",null);}});
    hook("ul.o","a",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){if(p.args.length>0&&p.args[0]!=null&&"com.xiaomi.ai.core.e".equals(p.args[0].getClass().getName()))incoming(safe(p.args[0],"getInstruction"),"dispatch",null);}});
    hook("ul.k","a",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){if(p.args.length==2&&p.args[1] instanceof Boolean)incoming(p.args[0],"sdk.filter",p.hasThrowable()?"threw":p.getResult());}});
    hook("yy.i","checkAndReport",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){incoming(p.args[0],"business",null);}
      @Override protected void afterHookedMethod(MethodHookParam p){incoming(p.args[0],"business.gate",p.hasThrowable()?"threw":p.getResult());}});
    hook("jb0.e1","checkApps",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){incoming(safe(p.thisObject,"getInstruction"),"checkApps.result",p.hasThrowable()?"threw":p.getResult());}});
    hook("jb0.c3","Z",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){
      Object ins=safe(p.thisObject,"getInstruction");Session s=associate(id(ins,"getDialogId"),"",false);
      Object result=p.getResult();emit(s,obj("event","checkUIAgent.stopStrategy","passed",!p.hasThrowable()&&(result==null||"".equals(result)),"threw",p.hasThrowable()));
    }});
    hook("com.xiaomi.voiceassist.baselibrary.uiagent.b","isSupportUIAgent",new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){
      emit(last,obj("event","uiAgent.deviceSupport","result",p.hasThrowable()?"threw":scalar(p.getResult())));
    }});
    hook("f10.m1","q",new XC_MethodHook(){@Override protected void beforeHookedMethod(MethodHookParam p){Session s=last;if(s==null)return;JSONArray names=new JSONArray();if(p.args[0] instanceof List)for(Object c:(List<?>)p.args[0])names.put(protocol(c));emit(s,obj("stage","context.update.input","contexts",names,"queryFrom",scalar(p.args[2]),"fromAsync",scalar(p.args[3])));}});
  }
  @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp){
    if(!"com.miui.voiceassist".equals(lp.packageName))return;
    XposedHelpers.findAndHookMethod(Application.class,"attach",Context.class,new XC_MethodHook(){@Override protected void afterHookedMethod(MethodHookParam p){
      if(installed)return;try{Context c=(Context)p.args[0];if(!HostCompatibility.allow(c,lp.classLoader)){log(obj("event","compatibilityHooksSkipped"));return;}installed=true;loader=lp.classLoader;log(obj("loaded",true,"module","allowlist-0.20","process",lp.processName));install();}catch(Throwable e){log(obj("initError",e.getClass().getSimpleName()));}
    }});
  }
}


















