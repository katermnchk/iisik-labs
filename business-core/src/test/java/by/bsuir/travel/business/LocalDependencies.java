package by.bsuir.travel.business;

import by.bsuir.travel.contracts.Model.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Local in-process replacements for future remote clients. No sockets or shared database. */
public final class LocalDependencies {
  private LocalDependencies() {}

  public static TourBusiness tours(List<Tour> tours, List<Direction> directions, Clock clock) {
    return new TourBusiness(
        new MemoryStore<>(new CopyOnWriteArrayList<>(tours), Tour::id), () -> directions, clock);
  }
}
