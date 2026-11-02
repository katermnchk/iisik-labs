package by.bsuir.travel.analytics.model;

import java.io.Serializable;
import java.time.*;
import java.util.*;

public class DirectionPopularityDailyEntityKey implements Serializable {
  public DirectionPopularityDailyEntityKey() {}

  public LocalDate day;
  public UUID directionId;

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof DirectionPopularityDailyEntityKey x)) return false;
    return Objects.equals(day, x.day) && Objects.equals(directionId, x.directionId);
  }

  public int hashCode() {
    return Objects.hash(day, directionId);
  }
}
