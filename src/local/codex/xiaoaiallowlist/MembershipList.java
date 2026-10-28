package local.codex.xiaoaiallowlist;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** An SDK-compatible snapshot; only additional contains decisions change, not the server's stored list. */
public final class MembershipList extends CopyOnWriteArrayList<String> {
  private final Predicate<String> eligible;
  private final Consumer<String> expanded;
  public MembershipList(Collection<String> original, Predicate<String> eligible, Consumer<String> expanded) {
    super(original); this.eligible = eligible; this.expanded = expanded;
  }
  @Override public boolean contains(Object value) {
    if (super.contains(value)) return true;
    if (!(value instanceof String) || !eligible.test((String)value)) return false;
    expanded.accept((String)value); return true;
  }
}
