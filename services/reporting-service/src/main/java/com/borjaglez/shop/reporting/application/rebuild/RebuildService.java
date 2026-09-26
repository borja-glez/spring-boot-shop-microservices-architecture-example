package com.borjaglez.shop.reporting.application.rebuild;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.shop.eskit.ProcessedMessageRepository;
import com.borjaglez.shop.reporting.domain.ReportLineRepository;
import com.borjaglez.shop.reporting.domain.ReportOrderRepository;
import com.borjaglez.shop.support.error.ConflictException;

/**
 * Throws the projections away and builds them again from the first event in Kafka.
 *
 * <p>Consumption is paused first, so no event is applied to half-cleared tables; the processed
 * message markers go with the tables, or the replayed events would be skipped as duplicates. Events
 * published meanwhile wait in Kafka and are read after the replay. Only one rebuild runs at a time.
 *
 * <p>A rebuild can only read what Kafka still keeps: the brokers of the example keep events forever
 * ({@code KAFKA_LOG_RETENTION_MS=-1}); with a finite retention, older orders would silently drop
 * out of the reports.
 */
@Component
public class RebuildService {

  private static final Logger log = LoggerFactory.getLogger(RebuildService.class);

  /** When the last rebuild started and finished; {@code finishedAt} is null while it runs. */
  public record RebuildStatus(
      boolean running, OffsetDateTime startedAt, OffsetDateTime finishedAt, String error) {}

  private final EventReplay replay;
  private final ReportOrderRepository orders;
  private final ReportLineRepository lines;
  private final ProcessedMessageRepository processed;
  private final TransactionTemplate transactions;
  private final Clock clock;
  private final AtomicBoolean running = new AtomicBoolean();
  private final AtomicReference<RebuildStatus> status =
      new AtomicReference<>(new RebuildStatus(false, null, null, null));

  public RebuildService(
      EventReplay replay,
      ReportOrderRepository orders,
      ReportLineRepository lines,
      ProcessedMessageRepository processed,
      TransactionTemplate transactions,
      Clock clock) {
    this.replay = replay;
    this.orders = orders;
    this.lines = lines;
    this.processed = processed;
    this.transactions = transactions;
    this.clock = clock;
  }

  public RebuildStatus status() {
    return status.get();
  }

  /** Rebuilds the projections; fails with 409 if a rebuild is already running. */
  public RebuildStatus rebuild() {
    if (!running.compareAndSet(false, true)) {
      throw new ConflictException("rebuild-in-progress", "A rebuild is already running");
    }
    OffsetDateTime started = OffsetDateTime.now(clock);
    status.set(new RebuildStatus(true, started, null, null));
    try {
      replay.pause();
      try {
        // Rewind first: if it fails, the tables are untouched and consumption goes on where it
        // was. If clearing fails after it, the markers are still there and the replayed events
        // are skipped as duplicates, so nothing is counted twice either.
        replay.rewind();
        transactions.executeWithoutResult(
            tx -> {
              lines.deleteAllInBatch();
              orders.deleteAllInBatch();
              // This database only holds reporting's markers.
              processed.deleteAllInBatch();
            });
      } finally {
        replay.resume();
      }
      RebuildStatus done = new RebuildStatus(false, started, OffsetDateTime.now(clock), null);
      status.set(done);
      return done;
    } catch (RuntimeException e) {
      log.error("Reporting rebuild failed", e);
      status.set(new RebuildStatus(false, started, OffsetDateTime.now(clock), e.getMessage()));
      throw e;
    } finally {
      running.set(false);
    }
  }
}
