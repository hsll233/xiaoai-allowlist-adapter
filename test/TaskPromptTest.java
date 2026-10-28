package local.codex.xiaoaiallowlist;
public final class TaskPromptTest {
  public static void main(String[] args){
    String goal="游戏里帮我签到", result=TaskPrompt.forGoal(goal);
    if(!result.startsWith(goal)||!result.contains("签到成功")||!result.contains("不要声称已完成"))throw new AssertionError("Goal and success criteria");
    if(!result.equals(TaskPrompt.forGoal(result)))throw new AssertionError("Repeated guidance");
    if(!"进入好友页面".equals(TaskPrompt.forGoal("进入好友页面")))throw new AssertionError("Unrelated goal");
    if(!"游戏里怎么签到".equals(TaskPrompt.forGoal("游戏里怎么签到")))throw new AssertionError("Question");
    if(!"介绍签到功能".equals(TaskPrompt.forGoal("介绍签到功能")))throw new AssertionError("Explanation");
    if(TaskPrompt.forGoal(null)!=null)throw new AssertionError("Null");
    StringBuilder longGoal=new StringBuilder("签到");while(longGoal.length()<2048)longGoal.append('字');
    if(!longGoal.toString().equals(TaskPrompt.forGoal(longGoal.toString())))throw new AssertionError("Length bound");
    if(!result.contains("当前游戏内活动")||!result.contains("不要自行切换"))throw new AssertionError("In-game goal scope");
    for(String q:new String[]{"打开B站搜索签到教程","查看签到记录","查找活动签到入口"})if(!q.equals(TaskPrompt.forGoal(q)))throw new AssertionError("Information query: "+q);
    if(TaskPrompt.forGoal("打开小米社区帮我签到").contains("不要自行切换"))throw new AssertionError("Do not force game scope onto community");
    System.out.println("12 task guidance checks passed");
  }
}
