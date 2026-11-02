package by.bsuir.travel.analytics.model;

import java.io.Serializable;
import java.time.*;
import java.util.*;

public class ProcessedEventsEntityKey implements Serializable {
  public ProcessedEventsEntityKey() {}

  public String consumerName;
  public UUID eventId;

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ProcessedEventsEntityKey x)) return false;
    return Objects.equals(consumerName, x.consumerName) && Objects.equals(eventId, x.eventId);
  }

  public int hashCode() {
    return Objects.hash(consumerName, eventId);
  }
}
