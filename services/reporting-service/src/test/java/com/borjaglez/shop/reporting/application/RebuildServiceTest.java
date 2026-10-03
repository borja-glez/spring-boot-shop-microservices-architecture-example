package com.borjaglez.shop.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.cqrs.jdbc.JdbcIdempotencyStore;
import com.borjaglez.shop.reporting.application.rebuild.EventReplay;
import com.borjaglez.shop.reporting.application.rebuild.RebuildService;
import com.borjaglez.shop.reporting.domain.ReportLineRepository;
import com.borjaglez.shop.reporting.domain.ReportOrderRepository;

class RebuildServiceTest {

  private final EventReplay replay = mock(EventReplay.class);
  private final ReportOrderRepository orders = mock(ReportOrderRepository.class);
  private final ReportLineRepository lines = mock(ReportLineRepository.class);
  private final JdbcIdempotencyStore processed = mock(JdbcIdempotencyStore.class);
  private final Instant now = Instant.parse("2026-10-03T10:00:00Z");

  /** Runs the callback directly: the order of the calls is what matters here. */
  private final TransactionTemplate transactions =
      new TransactionTemplate() {
        @Override
        public <T> T execute(
            org.springframework.transaction.support.TransactionCallback<T> action) {
          return action.doInTransaction(new SimpleTransactionStatus());
        }
      };

  private final RebuildService rebuilds =
      new RebuildService(
          replay, orders, lines, processed, transactions, Clock.fixed(now, ZoneOffset.UTC));

  @Test
  void itRewindsBeforeClearingAndAlwaysResumes() {
    rebuilds.rebuild();

    InOrder order = inOrder(replay, lines, orders, processed);
    order.verify(replay).pause();
    order.verify(replay).rewind();
    order.verify(lines).deleteAllInBatch();
    order.verify(orders).deleteAllInBatch();
    order.verify(processed).deleteProcessedBefore(now);
    order.verify(replay).resume();
    assertThat(rebuilds.status().error()).isNull();
  }

  @Test
  void aFailedRewindLeavesTheReportsAsTheyWere() {
    doThrow(new IllegalStateException("group still has members")).when(replay).rewind();

    assertThatThrownBy(rebuilds::rebuild).hasMessageContaining("members");

    verify(orders, never()).deleteAllInBatch();
    verify(processed, never()).deleteProcessedBefore(any());
    verify(replay).resume();
    assertThat(rebuilds.status().running()).isFalse();
    assertThat(rebuilds.status().error()).contains("members");
  }
}
