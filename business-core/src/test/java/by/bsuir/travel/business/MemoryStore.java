package by.bsuir.travel.business;

import by.bsuir.travel.contracts.DomainException;
import java.util.*;
import java.util.function.*;

public final class MemoryStore<T> implements Store<T> {
  private static final Object COMMAND_LOCK = new Object();
  private final List<T> data;
  private final Function<T, String> id;
  private final Map<String, List<String>> receipts = new HashMap<>();

  public MemoryStore(List<T> data, Function<T, String> id) {
    this.data = data;
    this.id = id;
  }

  public synchronized List<T> all() {
    return List.copyOf(data);
  }

  public synchronized void save(T value) {
    for (int n = 0; n < data.size(); n++) {
      if (id.apply(data.get(n)).equalsIgnoreCase(id.apply(value))) {
        data.set(n, value);
        return;
      }
    }
    data.add(value);
  }

  public synchronized String receipt(String key, String fingerprint) {
    var v = receipts.get(key);
    if (v == null) return null;
    if (!v.get(0).equals(fingerprint))
      throw new DomainException("CONFLICT", "Idempotency key reused with different input");
    return v.get(1);
  }

  public synchronized void receipt(String k, String f, String result) {
    receipts.put(k, List.of(f, result));
  }

  public <R> R atomic(Supplier<R> action) {
    synchronized (COMMAND_LOCK) {
      return action.get();
    }
  }
}
