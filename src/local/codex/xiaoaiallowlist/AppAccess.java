package local.codex.xiaoaiallowlist;

import android.content.*;
import android.content.pm.*;
import java.util.*;

/** Installed package eligibility and verified public launch entry; no app-specific allow table. */
final class AppAccess {
  static Set<String> enabledPackages(Context context){
    Set<String> result=new HashSet<String>();
    for(ApplicationInfo info:context.getPackageManager().getInstalledApplications(0))if(info!=null&&info.enabled)result.add(info.packageName);
    return result;
  }
  static Intent launch(Context context,String pkg){
    PackageManager pm=context.getPackageManager();
    try{
      if(!pm.getApplicationInfo(pkg,0).enabled)return null;
      Intent intent=pm.getLaunchIntentForPackage(pkg);
      if(intent==null)intent=pm.getLeanbackLaunchIntentForPackage(pkg);
      if(intent==null||intent.getComponent()==null||!pkg.equals(intent.getComponent().getPackageName()))return null;
      ActivityInfo activity=pm.getActivityInfo(intent.getComponent(),0);
      return activity.enabled&&activity.applicationInfo.enabled&&activity.exported?intent:null;
    }catch(PackageManager.NameNotFoundException error){return null;}
  }
  private AppAccess(){}
}
