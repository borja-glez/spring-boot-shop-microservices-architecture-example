import { describe, expect, it } from 'vitest';

import { EVENT_TYPES, HistoryEntry } from '../../core/api/order-models';
import { toTimeline } from './history-timeline';

const placed: HistoryEntry = {
  version: 1,
  eventType: EVENT_TYPES.orderPlaced,
  occurredAt: '2026-09-25T10:00:00Z',
  publishedAt: '2026-09-25T10:00:01Z',
  event: { orderId: 'o-1', lines: [{ sku: 'CAF-001' }, { sku: 'ACE-001' }] },
  stateAfter: { status: 'PLACED', total: 19.5, currency: 'EUR', lineCount: 2 },
};

const cancelled: HistoryEntry = {
  version: 2,
  eventType: EVENT_TYPES.orderCancelled,
  occurredAt: '2026-09-25T10:05:00Z',
  publishedAt: null,
  event: { orderId: 'o-1', reason: ' My mistake ', cancelledBy: 'cliente-lucia' },
  stateAfter: { status: 'CANCELLED', total: 19.5, currency: 'EUR', lineCount: 2 },
};

describe('toTimeline', () => {
  it('describes each event in order, oldest first', () => {
    const steps = toTimeline([cancelled, placed]);

    expect(steps.map((s) => s.version)).toEqual([1, 2]);
    expect(steps.map((s) => s.title)).toEqual(['Order placed', 'Order cancelled']);
    expect(steps[0].detail).toMatch(/^2 products for €19\.50$/);
    expect(steps[1].detail).toBe('By cliente-lucia: “My mistake”');
  });

  it('marks what changed and what is still in the outbox', () => {
    const steps = toTimeline([placed, cancelled]);

    expect(steps[0].changed).toEqual(['status', 'total', 'currency', 'lineCount']);
    expect(steps[1].changed).toEqual(['status']);
    expect(steps.map((s) => s.pending)).toEqual([false, true]);
  });

  it('copes with a cancellation without reason and unknown events', () => {
    const steps = toTimeline([
      placed,
      { ...cancelled, event: { cancelledBy: 'cliente-lucia' } },
      { ...cancelled, version: 3, eventType: 'shop.orders.2.event.order.order-shipped' },
    ]);

    expect(steps[1].detail).toBe('By cliente-lucia, no reason given');
    expect(steps[2].title).toBe('order-shipped');
    expect(steps[2].detail).toBe('');
  });
});
