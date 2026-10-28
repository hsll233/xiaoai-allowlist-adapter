package local.codex.xiaoaiallowlist;

import android.content.Context;
import android.content.Intent;
import de.robv.android.xposed.XposedHelpers;

/** Builds native operation data; never directly starts an Activity or dispatches a task. */
final class NativeLaunchPlan {
  static Object call(Object o,String method,Object...args){return XposedHelpers.callMethod(o,method,args);}
  static Object api(ClassLoader loader,String method,Object...args){return XposedHelpers.callStaticMethod(XposedHelpers.findClass("com.xiaomi.ai.api.common.APIUtils",loader),method,args);}
  static String string(Object value){
    if(value instanceof String)return (String)value;
    try{return Boolean.TRUE.equals(call(value,"isPresent"))?String.valueOf(call(value,"get")):"";}catch(Throwable ignored){return "";}
  }
  static Object buildValidated(Context app,ClassLoader loader,IntentPolicy.Target target,String dialog){
    Intent intent=AppAccess.launch(app,target.packageName);
    if(intent==null||intent.getComponent()==null||!target.packageName.equals(intent.getComponent().getPackageName()))throw new IllegalStateException("No validated Launcher component");
    Object androidIntent=XposedHelpers.newInstance(XposedHelpers.findClass("com.xiaomi.ai.api.Template$AndroidIntent",loader));
    call(androidIntent,"setPkgName",target.packageName);call(androidIntent,"setType","activity");call(androidIntent,"setUri",intent.toUri(Intent.URI_INTENT_SCHEME));
    Object payload=XposedHelpers.newInstance(XposedHelpers.findClass("com.xiaomi.ai.api.Launcher$LaunchApp",loader));
    call(payload,"setIdentifier",target.packageName);call(payload,"setName",target.label);call(payload,"setIntent",androidIntent);
    call(payload,"setType",XposedHelpers.getStaticObjectField(XposedHelpers.findClass("com.xiaomi.ai.api.Launcher$LaunchTargetType",loader),"ANDROID_INTENT"));
    Object instruction=api(loader,"buildInstruction",payload);call(instruction,"setDialogId",dialog);
    instruction=api(loader,"readInstruction",api(loader,"toJsonString",instruction));
    Object model=XposedHelpers.callStaticMethod(XposedHelpers.findClass("cc0.j0",loader),"parseLaunchAppData",instruction);
    Object nativeIntent=call(model,"getIntentModel");
    if(nativeIntent==null||!target.packageName.equals(call(model,"getIdentifier"))||!dialog.equals(call(model,"getDialogId"))||!"ANDROID_INTENT".equals(call(model,"getType"))||!target.packageName.equals(call(nativeIntent,"getPkgName"))||!"activity".equalsIgnoreCase(String.valueOf(call(nativeIntent,"getType"))))throw new IllegalStateException("Native Launcher parser rejected template");
    return instruction;
  }
  static Object forDialog(ClassLoader loader,Object template,String dialog,String transaction){
    Object instruction=api(loader,"readInstruction",api(loader,"toJsonString",template));
    call(instruction,"setDialogId",dialog);
    if(!transaction.isEmpty())call(call(instruction,"getHeader"),"setTransactionId",transaction);
    return instruction;
  }
  private NativeLaunchPlan(){}
}
