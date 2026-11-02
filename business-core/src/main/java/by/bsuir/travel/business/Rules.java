package by.bsuir.travel.business;

import by.bsuir.travel.contracts.*;
import by.bsuir.travel.contracts.Model.*;
import java.util.*;

public final class Rules {
  private Rules() {}

  public static void require(boolean ok, String code, String message) {
    if (!ok) throw new DomainException(code, message);
  }

  public static void role(Principal p, RoleCode... roles) {
    require(
        p != null && p.status() == UserStatus.ACTIVE && Arrays.asList(roles).contains(p.role()),
        "FORBIDDEN",
        "Operation is not permitted for this role");
  }

  public static String id(String id) {
    ReadSupport.id(id);
    return UUID.fromString(id).toString();
  }

  public static void text(String v, int max) {
    require(
        v != null && !v.isBlank() && v.length() <= max, "BAD_REQUEST", "Text is blank or too long");
  }

  public static void version(long expected, long actual) {
    require(expected == actual, "CONFLICT", "Version has changed; reload object");
  }

  public static void own(Booking b, Principal p) {
    require(
        p.role() != RoleCode.CLIENT || ReadSupport.sameId(b.clientId(), p.clientId()),
        "FORBIDDEN",
        "Booking belongs to another client");
  }

  public static String key(Principal p, String operation, String key) {
    text(key, 100);
    return p.userId() + ":" + operation + ":" + key;
  }
}
