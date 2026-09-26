package com.borjaglez.shop.orders.application.checkout;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Hands the due checkout sagas out to the runners, one runner per saga, so several instances of the
 * service can run checkouts side by side.
 *
 * <p>A claimed saga is leased: its next attempt moves to the end of the lease, and no other runner
 * picks it up before then. The step it runs sets the real next attempt. If the instance dies
 * mid-step, the lease runs out and another instance takes the saga over. The lease only avoids
 * duplicated work: a step run twice is still safe, because the saga checks its version before
 * recording a result and every remote command is idempotent.
 */
public interface DueCheckouts {

  /**
   * Claims up to {@code max} sagas that are running or stuck and due at {@code now}, oldest first,
   * and leases them until {@code leasedUntil}.
   *
   * @return the order ids of the claimed sagas
   */
  List<UUID> claim(int max, OffsetDateTime now, OffsetDateTime leasedUntil);
}
