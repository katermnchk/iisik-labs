package by.bsuir.travel.business;

import java.util.*;
import java.util.function.Supplier;

public interface Store<T> {
  List<T> all();

  void save(T value);

  record Change(String reason, String actorId) {}

  default void save(T value, String reason, String actorId) {
    save(value);
  }

  String receipt(String key, String fingerprint);

  void receipt(String key, String fingerprint, String resultId);

  <R> R atomic(Supplier<R> action);
}
