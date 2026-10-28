package local.codex.xiaoaiallowlist;
import android.content.*;
import android.os.Build;

final class ReceiverCompat {
  static void privateReceiver(Context c,BroadcastReceiver r,IntentFilter f){
    if(Build.VERSION.SDK_INT>=33)c.registerReceiver(r,f,Context.RECEIVER_NOT_EXPORTED);else c.registerReceiver(r,f);
  }
  static void diagnosticReceiver(Context c,BroadcastReceiver r,IntentFilter f){
    if(Build.VERSION.SDK_INT>=33)c.registerReceiver(r,f,"android.permission.DUMP",null,Context.RECEIVER_EXPORTED);
    else c.registerReceiver(r,f,"android.permission.DUMP",null);
  }
}
