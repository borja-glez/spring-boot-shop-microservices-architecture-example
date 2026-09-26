package com.borjaglez.shop.reporting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import com.borjaglez.shop.eskit.ProcessedMessageRepository;
import com.borjaglez.shop.reporting.application.rebuild.EventReplay;
import com.borjaglez.shop.reporting.application.rebuild.RebuildService;
import com.borjaglez.shop.reporting.domain.ReportLineRepository;
import com.borjaglez.shop.reporting.domain.ReportOrderRepository;

class RebuildServiceTest {

  private final EventReplay replay = mock(EventReplay.class);
  private final ReportOrderRepository orders = mock(ReportOrderRepository.class);
  private final ReportLineRepository lines = mock(ReportLineRepository.class);
  private final ProcessedMessageRepository processed = mock(ProcessedMessageRepository.class);

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
      new RebuildService(replay, orders, lines, processed, transactions, Clock.systemUTC());

  @Test
  void itRewindsBeforeClearingAndAlwaysResumes() {
    rebuilds.rebuild();

    InOrder order = inOrder(replay, lines, orders, processed);
    order.verify(replay).pause();
    order.verify(replay).rewind();
    order.verify(lines).deleteAllInBatch();
    order.verify(orders).deleteAllInBatch();
    order.verify(processed).deleteAllInBatch();
    order.verify(replay).resume();
    assertThat(rebuilds.status().error()).isNull();
  }

  @Test
  void aFailedRewindLeavesTheReportsAsTheyWere() {
    doThrow(new IllegalStateException("group still has members")).when(replay).rewind();

    assertThatThrownBy(rebuilds::rebuild).hasMessageContaining("members");

    verify(orders, never()).deleteAllInBatch();
    verify(processed, never()).deleteAllInBatch();
    verify(replay).resume();
    assertThat(rebuilds.status().running()).isFalse();
    assertThat(rebuilds.status().error()).contains("members");
  }
}
