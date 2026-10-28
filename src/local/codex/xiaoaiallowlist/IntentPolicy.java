package local.codex.xiaoaiallowlist;

import java.util.*;
import java.util.regex.*;

/** Resolves the user's stated application; planning and screen actions remain with XiaoAi. */
public final class IntentPolicy {
  public static final class Target {
    public final String packageName,label;
    public final boolean launchable;
    public final List<String> names;
    private final List<Pattern> patterns=new ArrayList<Pattern>();
    public Target(String pkg,String label,String... aliases){
      this(pkg,label,true,aliases);
    }
    public Target(String pkg,String label,boolean launchable,String... aliases){
      if(pkg==null||pkg.isEmpty()||label==null||label.trim().isEmpty())throw new IllegalArgumentException();
      packageName=pkg;this.label=label;this.launchable=launchable;names=new ArrayList<String>();names.add(label);
      if(aliases!=null)Collections.addAll(names,aliases);
      Set<String> unique=new LinkedHashSet<String>();for(String alias:names){String n=compact(alias);if(!n.isEmpty())unique.add(n);}
      for(String n:unique){String pattern=Pattern.quote(n);if(n.matches("[a-z0-9]+"))pattern="(?<![a-z0-9])"+pattern+"(?![a-z0-9])";patterns.add(Pattern.compile(pattern));}
    }
  }
  private static String compact(String q){return q==null?"":q.toLowerCase(Locale.ROOT).replaceAll("[\\s，。,.!?！？、]","");}
  private static String unprefixed(String q){return q.replaceFirst("^(?:(?:请|麻烦|小爱同学|小爱|帮我|替我|给我|算了|还是|那就|现在|改为|改成))*","");}
  public static boolean action(String query){
    String q=unprefixed(compact(query));
    if(q.isEmpty()||q.matches("^(?:请问|我想知道|告诉我|介绍|解释|如何|怎么|为什么|什么是).*$"))return false;
    if(!q.contains("搜索")&&!q.contains("查找")&&(q.contains("是什么")||q.contains("为什么")||q.contains("怎么")||q.contains("如何")||q.contains("好用吗")))return false;
    for(String verb:new String[]{"打开","启动","进入","点击","切换","搜索","查找","查看","滚动","下滑","上滑","返回","签到","打卡","领取","收藏","关注","点赞","刷新","播放","暂停","发送","创建","选择","扫码","扫描","添加","删除","设置","购买","下单"})if(q.contains(verb))return true;
    return q.endsWith("我的")||q.endsWith("好友");
  }
  public static boolean current(String query){
    String q=compact(query);
    return q.contains("当前应用")||q.contains("这个应用")||q.contains("当前页面")||q.contains("这个页面")||q.contains("在这里")
      ||gameCurrent(query);
  }
  public static boolean gameCurrent(String query){return unprefixed(compact(query)).matches("^(?:在)?(?:当前游戏|这个游戏|游戏里面|游戏里|游戏中).*$");}
  public static boolean adviceAction(String query){
    String q=compact(query);
    if(q.matches("(?s).*(?:停止|结束任务|取消任务|取消操作|别点了|不要再点|不要点击).*"))return false;
    return action(query);
  }
  private static final class Match {
    final Target target; final int start,end;
    Match(Target t,int s,int e){target=t;start=s;end=e;}
  }
  public static Target select(String query,List<Target> catalog,Target foreground){
    if(!action(query))return null;
    if(current(query))return foreground;
    String q=compact(query),bare=unprefixed(q);
    if(bare.startsWith("搜索")||bare.startsWith("查找"))return null;
    List<Match> matches=new ArrayList<Match>();
    if(catalog!=null)for(Target t:catalog)for(Pattern pattern:t.patterns){
      if(!t.launchable&&(foreground==null||!t.packageName.equals(foreground.packageName)))continue;
      Matcher m=pattern.matcher(q);
      while(m.find()){
        // An action word at the start is not an app reference: 设置明天的闹钟.
        String value=m.group(),tail=q.substring(m.end());
        if(m.start()==0&&Arrays.asList("设置","搜索","返回","播放","暂停","添加","删除","选择").contains(value)&&!tail.startsWith("里")&&!tail.startsWith("中")&&!tail.startsWith("应用"))continue;
        matches.add(new Match(t,m.start(),m.end()));
      }
    }
    List<Match> longest=new ArrayList<Match>();
    for(Match m:matches){boolean nested=false;for(Match other:matches)if(other.start<=m.start&&other.end>=m.end&&other.end-other.start>m.end-m.start){nested=true;break;}if(!nested)longest.add(m);}
    int opening=q.indexOf("打开"); if(opening<0)opening=q.indexOf("启动");
    if(opening>=0){
      Target opened=null;
      for(Match m:longest)if(m.start==opening+2){if(opened!=null&&!opened.packageName.equals(m.target.packageName))return null;opened=m.target;}
      if(opened!=null){
        Matcher later=Pattern.compile("打开|启动").matcher(q);later.region(opening+2,q.length());
        while(later.find())for(Match m:longest)if(m.start==later.end()&&!opened.packageName.equals(m.target.packageName))return null;
        return opened;
      }
    }
    Target named=null;
    for(Match m:longest){if(named!=null&&!named.packageName.equals(m.target.packageName))return null;named=m.target;}
    if(named!=null)return named;
    if(bare.matches("(?:(?:进入|切换到|查看|点击|返回|去|到)(?:我的(?:页面|主页|界面)?|个人(?:主页|中心)|好友(?:页面|列表|界面)?|首页|消息(?:页面|界面)?|设置(?:页面|界面)?)|签到|打卡|向下滚动|向上滚动|下滑|上滑)(?:吧|呀|啊)?"))return foreground;
    return null;
  }
  private IntentPolicy(){}
}
