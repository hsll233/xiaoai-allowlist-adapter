package local.codex.xiaoaiallowlist;

/** Clarifies success criteria; never supplies coordinates or a fabricated result. */
public final class TaskPrompt {
  private static final String MARKER="【签到任务验收要求】";
  private static final String HINT="\n"+MARKER+"先确认当前应用中对应的签到入口。页面正在加载时先等待并重新观察。不要把浏览页面、领取邮件或其他无关奖励当作签到完成。只有看到签到成功、已签到、对应活动签到奖励已领取等明确完成状态，才报告完成。连续点击同一位置没有变化时先重新观察页面和入口，不要照搬上次点击。如果找不到入口、条件不满足或实际需要另一应用，请明确说明卡住的步骤和原因，不要声称已完成。";
  private static final String GAME="本次目标限定当前游戏内活动的签到或对应签到奖励，不是社区每日签到；不要自行切换到社区、网页或其他应用。";
  public static String forGoal(String query){
    if(query==null||!IntentPolicy.action(query)||query.contains(MARKER)||(!query.contains("签到")&&!query.contains("打卡")))return query;
    if(query.matches("(?s).*(?:搜索|查找|查看|学习).*"))return query;
    String hint=HINT+(IntentPolicy.gameCurrent(query)?GAME:"");
    return query.length()+hint.length()<=2048?query+hint:query;
  }
}
