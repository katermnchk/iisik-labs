package by.bsuir.travel.booking.model;

import java.io.Serializable;
import java.time.*;
import java.util.*;

public class IdempotencyKeysEntityKey implements Serializable {
  public IdempotencyKeysEntityKey() {}

  public String scope;
  public String key;

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof IdempotencyKeysEntityKey x)) return false;
    return Objects.equals(scope, x.scope) && Objects.equals(key, x.key);
  }

  public int hashCode() {
    return Objects.hash(scope, key);
  }
}
