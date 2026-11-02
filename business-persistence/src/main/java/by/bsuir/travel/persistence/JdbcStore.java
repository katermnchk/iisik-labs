package by.bsuir.travel.persistence;

import by.bsuir.travel.business.Store;
import by.bsuir.travel.contracts.DomainException;
import java.util.*;
import java.util.function.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

public final class JdbcStore<T> implements Store<T> {
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final Supplier<List<T>> read;
  private final BiConsumer<T, Change> write;

  public JdbcStore(
      JdbcTemplate jdbc, TransactionTemplate tx, Supplier<List<T>> read, Consumer<T> write) {
    this(jdbc, tx, read, (value, change) -> write.accept(value));
  }

  public JdbcStore(
      JdbcTemplate jdbc,
      TransactionTemplate tx,
      Supplier<List<T>> read,
      BiConsumer<T, Change> write) {
    this.jdbc = jdbc;
    this.tx = tx;
    this.read = read;
    this.write = write;
  }

  public void save(T value, String reason, String actorId) {
    write.accept(value, new Change(reason, actorId));
    event(value);
  }

  public List<T> all() {
    return read.get();
  }

  public void save(T value) {
    write.accept(value, null);
    event(value);
  }

  private static final com.fasterxml.jackson.databind.ObjectMapper EVENTS =
      new com.fasterxml.jackson.databind.ObjectMapper()
          .findAndRegisterModules()
          .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  private void event(T value) {
    if (value instanceof by.bsuir.travel.contracts.Model.Booking b)
      EventJournal.append(jdbc, EVENTS, "BookingStatusChanged", "booking.events.v1", b.id(), b);
    else if (value instanceof by.bsuir.travel.contracts.Model.Tour t)
      EventJournal.append(jdbc, EVENTS, "TourChanged", "tour.events.v1", t.id(), t);
    else if (value instanceof by.bsuir.travel.contracts.Model.User u)
      EventJournal.append(jdbc, EVENTS, "UserUpdated", "user.events.v1", u.id(), u);
  }

  public String receipt(String key, String fingerprint) {
    var rows =
        jdbc.queryForList(
            "select fingerprint,result_id from command_receipts where command_key=?", key);
    if (rows.isEmpty()) return null;
    var row = rows.get(0);
    if (!fingerprint.equals(row.get("fingerprint")))
      throw new DomainException("CONFLICT", "Idempotency key reused with different input");
    return row.get("result_id").toString();
  }

  public void receipt(String key, String f, String id) {
    jdbc.update(
        "insert into command_receipts(command_key,fingerprint,result_id) values (?,?,?::uuid)",
        key,
        f,
        id);
  }

  public <R> R atomic(Supplier<R> action) {
    return tx.execute(
        status -> {
          jdbc.execute("select pg_advisory_xact_lock(417204)");
          return action.get();
        });
  }
}
