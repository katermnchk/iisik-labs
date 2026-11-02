package by.bsuir.travel.contracts;

import by.bsuir.travel.contracts.Model.*;
import java.time.OffsetDateTime;
import java.util.*;

public final class ReadSupport {
  private ReadSupport() {}

  public static void id(String id) {
    if (id == null
        || !id.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw new DomainException("BAD_REQUEST", "Invalid UUID");
  }

  /** UUID identity is independent of hexadecimal letter case. */
  public static boolean sameId(String left, String right) {
    return left != null && right != null && left.equalsIgnoreCase(right);
  }

  public static <T> Page<T> page(List<T> list, PageInput p) {
    return page(list, p, null);
  }

  public static <T> Page<T> page(List<T> list, PageInput p, OffsetDateTime asOf) {
    long offset = (long) p.page() * p.size();
    int from = (int) Math.min(offset, list.size());
    int to = Math.min(from + p.size(), list.size());
    return new Page<>(
        List.copyOf(list.subList(from, to)),
        list.size(),
        new PageInfo(p.page(), p.size(), to < list.size()),
        asOf);
  }

  public static <T> T found(Optional<T> value) {
    return value.orElseThrow(() -> new DomainException("NOT_FOUND", "Object not found"));
  }

  public static void period(AnalyticsFilter f) {
    if (f == null || f.from() == null || f.to() == null || !f.from().isBefore(f.to()))
      throw new DomainException("BAD_REQUEST", "Expected from < to");
    if (!f.from()
            .withOffsetSameInstant(java.time.ZoneOffset.UTC)
            .toLocalTime()
            .equals(java.time.LocalTime.MIDNIGHT)
        || !f.to()
            .withOffsetSameInstant(java.time.ZoneOffset.UTC)
            .toLocalTime()
            .equals(java.time.LocalTime.MIDNIGHT))
      throw new DomainException("BAD_REQUEST", "Analytics period must use complete UTC days");
  }
}
