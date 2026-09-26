import {
  EVENT_TYPES,
  HistoryEntry,
  OrderState,
  REJECTION_LABELS,
} from '../../core/api/order-models';

/** A step of the order's life as the timeline shows it. */
export interface TimelineStep {
  version: number;
  title: string;
  /** What the event says, in words. */
  detail: string;
  occurredAt: string;
  /** Still in the outbox: the relay has not handed it to Kafka yet. */
  pending: boolean;
  eventType: string;
  state: OrderState;
  /** Fields whose value changed compared with the previous step. */
  changed: (keyof OrderState)[];
  event: Record<string, unknown>;
}

const STATE_FIELDS: (keyof OrderState)[] = ['status', 'total', 'currency', 'lineCount'];

/** Turns the event stream into timeline steps, oldest first. */
export function toTimeline(history: HistoryEntry[]): TimelineStep[] {
  const ordered = [...history].sort((a, b) => a.version - b.version);
  return ordered.map((entry, index) => {
    const previous = index > 0 ? ordered[index - 1].stateAfter : null;
    return {
      version: entry.version,
      title: titleOf(entry.eventType),
      detail: detailOf(entry),
      occurredAt: entry.occurredAt,
      pending: entry.publishedAt === null,
      eventType: entry.eventType,
      state: entry.stateAfter,
      changed: previous
        ? STATE_FIELDS.filter((field) => previous[field] !== entry.stateAfter[field])
        : [...STATE_FIELDS],
      event: entry.event,
    };
  });
}

function titleOf(eventType: string): string {
  switch (eventType) {
    case EVENT_TYPES.orderPlaced:
      return 'Order placed';
    case EVENT_TYPES.orderConfirmed:
      return 'Order confirmed';
    case EVENT_TYPES.orderRejected:
      return 'Order rejected';
    case EVENT_TYPES.orderCancelled:
      return 'Order cancelled';
    default:
      // A newer service version may add events this page does not know yet.
      return eventType.split('.').pop() ?? eventType;
  }
}

function detailOf(entry: HistoryEntry): string {
  const event = entry.event;
  switch (entry.eventType) {
    case EVENT_TYPES.orderPlaced: {
      const lines = Array.isArray(event['lines']) ? event['lines'].length : 0;
      return `${lines} ${lines === 1 ? 'product' : 'products'} for ${formatMoney(
        entry.stateAfter.total,
        entry.stateAfter.currency,
      )}`;
    }
    case EVENT_TYPES.orderConfirmed:
      return 'Stock reserved and payment authorized';
    case EVENT_TYPES.orderRejected: {
      const reason = typeof event['reason'] === 'string' ? event['reason'] : '';
      return REJECTION_LABELS[reason] ?? reason;
    }
    case EVENT_TYPES.orderCancelled: {
      const reason = typeof event['reason'] === 'string' ? event['reason'].trim() : '';
      const by = typeof event['cancelledBy'] === 'string' ? event['cancelledBy'] : 'someone';
      return reason ? `By ${by}: “${reason}”` : `By ${by}, no reason given`;
    }
    default:
      return '';
  }
}

function formatMoney(amount: number, currency: string): string {
  return new Intl.NumberFormat('en', { style: 'currency', currency }).format(amount);
}
