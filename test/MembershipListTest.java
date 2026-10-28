package local.codex.xiaoaiallowlist;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MembershipListTest {
  private static int checks;
  private static void check(boolean result, String label) { checks++; if (!result) throw new AssertionError(label); }
  public static void main(String[] args) {
    CopyOnWriteArrayList<String> server = new CopyOnWriteArrayList<String>(Arrays.asList("original.app"));
    Set<String> launcher = new HashSet<String>(Arrays.asList("new.app", "intercepted.app"));
    Set<String> intercepted = new HashSet<String>(Arrays.asList("intercepted.app"));
    Set<String> observed = new HashSet<String>();
    CopyOnWriteArrayList<String> sdkView = new MembershipList(server,
        pkg -> launcher.contains(pkg) && !intercepted.contains(pkg), observed::add);
    check(sdkView.contains("new.app"), "previously unlisted launcher gains SDK membership");
    check(sdkView.contains("original.app"), "server's original allowance survives eligibility filter");
    check(!sdkView.contains("intercepted.app"), "explicit intercepted package cannot gain allowance");
    check(!sdkView.contains("permission.controller"), "non-launcher system prompt is not broadened");
    check(!sdkView.contains(null) && !sdkView.contains(Integer.valueOf(2)), "non-package inputs remain SDK-compatible");
    check(!server.contains("new.app") && server.size() == 1, "server backing collection never rewritten");
    check(sdkView.size() == 1 && sdkView.get(0).equals("original.app"), "iteration remains the original snapshot");
    check(observed.equals(new HashSet<String>(Arrays.asList("new.app"))), "only added membership emits a diagnostic");
    server.add("later.app");
    check(!sdkView.contains("later.app"), "one window callback has a stable original snapshot");
    CopyOnWriteArrayList<String> nextView = new MembershipList(server, pkg -> false, pkg -> { throw new AssertionError("disabled gate expanded"); });
    check(nextView.contains("later.app") && !nextView.contains("new.app"), "fresh callback sees server updates while disabled gate preserves original decisions");
    System.out.println("PASS " + checks + " membership integration checks");
  }
}
