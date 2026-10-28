package local.codex.xiaoaiallowlist;
import java.util.*;
public final class IntentPolicyTest {
  static int checks;
  static IntentPolicy.Target community=new IntentPolicy.Target("community","小米社区");
  static IntentPolicy.Target discord=new IntentPolicy.Target("discord","Discord","迪斯科德");
  static IntentPolicy.Target youtube=new IntentPolicy.Target("youtube","YouTube","油管");
  static IntentPolicy.Target music=new IntentPolicy.Target("music","YouTube Music","油管音乐");
  static IntentPolicy.Target bili=new IntentPolicy.Target("bili","哔哩哔哩","B站");
  static List<IntentPolicy.Target> apps=Arrays.asList(community,discord,youtube,music,bili);
  static void expect(String q,IntentPolicy.Target fg,IntentPolicy.Target wanted){
    IntentPolicy.Target got=IntentPolicy.select(q,apps,fg);
    if(got!=wanted)throw new AssertionError(q+" expected="+(wanted==null?"none":wanted.packageName)+" got="+(got==null?"none":got.packageName));checks++;
  }
  public static void main(String[] args){
    expect("打开小米社区，进入我的页面",youtube,community);
    expect("打开小米社区帮我签到",youtube,community);
    expect("打开小米社区搜索小米15Pro打开第一篇帖子并向下浏览",youtube,community);
    expect("打开YouTube再打开YouTube搜索页",community,youtube);
    expect("打开 Discord，进入好友页面",community,discord);
    expect("打开 YouTube，帮我搜索猫咪视频",community,youtube);
    expect("打开YouTube",community,youtube);
    expect("请帮我打开YouTube Music",community,music);
    expect("在当前应用下搜索人生",youtube,youtube);
    expect("点击当前应用底部的我的",community,community);
    expect("在当前应用搜索Discord教程",youtube,youtube);
    expect("在当前应用搜索YouTube Music",community,community);
    expect("进入好友页面",discord,discord);
    expect("帮我签到",community,community);
    expect("游戏里帮我签到",community,community);
    expect("在游戏里面帮我签到",community,community);
    expect("帮我在游戏中签到",community,community);
    expect("当前游戏帮我签到",community,community);
    expect("这个游戏里签到",community,community);
    expect("游戏里帮我签到",null,null);
    expect("游戏里怎么签到",community,null);
    expect("搜索游戏里的签到攻略",community,null);
    expect("在Discord里搜索猫咪",community,discord);
    expect("打开YouTube搜索哔哩哔哩教程",community,youtube);
    expect("打开油管音乐搜索猫咪",community,music);
    expect("如何在Discord搜索消息",community,null);
    expect("YouTube是什么",community,null);
    expect("介绍一下小米社区",community,null);
    expect("搜索Discord教程",youtube,null);
    expect("帮我搜索猫咪视频",youtube,null);
    expect("算了帮我搜索Discord教程",community,null);
    expect("改为搜索YouTube教程",community,null);
    expect("那就打开Discord进入好友页面",community,discord);
    expect("北京天气怎么样",community,null);
    expect("设置明天的闹钟",community,null);
    expect("打开WiFi",community,null);
    expect("打开YouTube再打开Discord",community,null);
    expect("在当前应用搜索猫咪",null,null);
    expect("Discord为什么打不开",community,null);
    List<IntentPolicy.Target> ambiguous=Arrays.asList(new IntentPolicy.Target("one","测试应用"),new IntentPolicy.Target("two","测试应用"));
    if(IntentPolicy.select("打开测试应用",ambiguous,null)!=null)throw new AssertionError("Ambiguous installed labels");checks++;
    IntentPolicy.Target noIcon=new IntentPolicy.Target("hidden.package","无图标应用",false,"hidden.package");
    if(IntentPolicy.select("在当前应用进入我的页面",Arrays.asList(noIcon),noIcon)!=noIcon)throw new AssertionError("Foreground no-icon app");checks++;
    if(IntentPolicy.select("打开无图标应用",Arrays.asList(noIcon),null)!=null)throw new AssertionError("Cannot invent a launch entry");checks++;
    if(IntentPolicy.select("在无图标应用进入我的页面",Arrays.asList(noIcon),noIcon)!=noIcon)throw new AssertionError("Named no-icon foreground app");checks++;
    IntentPolicy.Target different=new IntentPolicy.Target("org.example.different","另一个应用",true,"Different App","org.example.different");
    List<IntentPolicy.Target> expanded=Arrays.asList(different,community,discord);
    if(IntentPolicy.select("打开 Different App，进入我的页面",expanded,community)!=different)throw new AssertionError("Localized application name");checks++;
    if(IntentPolicy.select("打开org.example.different，进入我的页面",expanded,community)!=different)throw new AssertionError("Installed package name");checks++;
    if(IntentPolicy.select("打开不存在的应用",expanded,null)!=null)throw new AssertionError("Unknown app");checks++;
    IntentPolicy.Target settings=new IntentPolicy.Target("com.android.settings","设置",true,"Settings");
    if(IntentPolicy.select("设置明天的闹钟",Arrays.asList(settings),community)!=null)throw new AssertionError("Native alarm command mistaken for Settings app");checks++;
    if(IntentPolicy.select("打开设置，进入显示设置",Arrays.asList(settings),community)!=settings)throw new AssertionError("Explicit Settings application");checks++;
    if(IntentPolicy.select("在设置里查看显示设置",Arrays.asList(settings),community)!=settings)throw new AssertionError("Settings application reference");checks++;
    if(!IntentPolicy.adviceAction("算了帮我搜索原神"))throw new AssertionError("Action advice");checks++;
    if(!IntentPolicy.adviceAction("不要打开帖子了，改为进入我的页面"))throw new AssertionError("Changed target");checks++;
    for(String q:Arrays.asList("停止点击","帮我取消操作","不要再点击了","一加一等于几")){
      if(IntentPolicy.adviceAction(q))throw new AssertionError("Do not restart for stop or question: "+q);checks++;
    }
    System.out.println(checks+" request target and advice checks passed");
  }
}
