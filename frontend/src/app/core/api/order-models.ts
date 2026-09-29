/** Shapes returned by the orders API (see orders-service `OrderViews`). */

export type OrderStatus = 'PLACED' | 'CONFIRMED' | 'REJECTED' | 'CANCELLED';

export interface OrderSummary {
  orderId: string;
  status: OrderStatus;
  total: number;
  currency: string;
  lineCount: number;
  placedAt: string;
  updatedAt: string;
}

export interface OrderLineView {
  productId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface OrderDetail {
  orderId: string;
  status: OrderStatus;
  total: number;
  currency: string;
  cancelReason: string | null;
  paymentId: string | null;
  rejectionReason: string | null;
  rejectionDetail: string | null;
  placedAt: string;
  updatedAt: string;
  lines: OrderLineView[];
}

export type CheckoutStepName =
  | 'RESERVE_STOCK'
  | 'AUTHORIZE_PAYMENT'
  | 'CONFIRM_ORDER'
  | 'REFUND_PAYMENT'
  | 'RELEASE_STOCK'
  | 'REJECT_ORDER'
  | 'DONE';

export type StepOutcome = 'SUCCEEDED' | 'DECLINED' | 'RETRYING' | 'GAVE_UP';

/** The checkout saga of an order (see orders-service `CheckoutView`). */
export interface CheckoutView {
  orderId: string;
  state: 'RUNNING' | 'COMPLETED' | 'STUCK';
  mode: 'CHECKOUT' | 'REJECTING' | 'CANCELLING';
  step: CheckoutStepName;
  attempts: number;
  nextAttemptAt: string | null;
  lastError: string | null;
  rejectionReason: string | null;
  rejectionDetail: string | null;
  steps: { step: CheckoutStepName; outcome: StepOutcome; detail: string | null; at: string }[];
}

export interface OrderState {
  status: OrderStatus;
  total: number;
  currency: string;
  lineCount: number;
}

/** One event of an order's stream, with the state the order had right after it. */
export interface HistoryEntry {
  version: number;
  eventType: string;
  occurredAt: string;
  publishedAt: string | null;
  event: Record<string, unknown>;
  stateAfter: OrderState;
}

/** A row of the event store. */
export interface StoredEventView {
  globalPosition: number;
  eventId: string;
  streamType: string;
  streamId: string;
  version: number | null;
  eventType: string;
  occurredAt: string;
  publishedAt: string | null;
  publishAttempts: number;
  lastError: string | null;
  payload: Record<string, unknown>;
  metadata: Record<string, string>;
}

export interface OrderItem {
  productId: string;
  quantity: number;
}

/** Why a cart line cannot be ordered as it is. */
export type QuoteProblem = 'NOT_FOR_SALE' | 'NOT_ENOUGH_STOCK';

/** A cart line as it would be ordered now (see orders-service `OrderViews.QuoteLine`). */
export interface QuoteLine {
  productId: string;
  sku: string | null;
  name: string | null;
  quantity: number;
  /** `null` when the product is not for sale. */
  unitPrice: number | null;
  subtotal: number | null;
  /** Free units; `null` when unknown. */
  available: number | null;
  problem: QuoteProblem | null;
}

/**
 * The cart priced with the orders service's copy of the catalog (Kafka) and checked against the
 * inventory's stock (RabbitMQ).
 */
export interface CartQuote {
  lines: QuoteLine[];
  total: number;
  currency: string | null;
  /** Whether the inventory answered; when it did not, only prices were checked. */
  stockChecked: boolean;
  orderable: boolean;
}

/** A notice the notifications service sent about an order. */
export interface OrderNotice {
  kind: 'ORDER_CONFIRMED' | 'ORDER_REJECTED' | 'ORDER_CANCELLED' | 'PAYMENT_REFUNDED';
  title: string;
  body: string;
  sentAt: string;
  read: boolean;
}

/** The notices of an order, asked to the notifications service over RabbitMQ. */
export interface OrderNotices {
  /** Whether the notifications service answered. */
  available: boolean;
  notices: OrderNotice[];
}

/** Wire names of the events the shop stores (`@CqrsMessage` of the contracts). */
export const EVENT_TYPES = {
  orderPlaced: 'shop.orders.1.event.order.order-placed',
  orderConfirmed: 'shop.orders.1.event.order.order-confirmed',
  orderRejected: 'shop.orders.1.event.order.order-rejected',
  orderCancelled: 'shop.orders.1.event.order.order-cancelled',
  productPublished: 'shop.catalog.1.event.product.product-published',
  productPriceChanged: 'shop.catalog.1.event.product.product-price-changed',
  productDiscontinued: 'shop.catalog.1.event.product.product-discontinued',
} as const;

export const STATUS_LABELS: Record<OrderStatus, string> = {
  PLACED: 'In checkout',
  CONFIRMED: 'Confirmed',
  REJECTED: 'Rejected',
  CANCELLED: 'Cancelled',
};

/** Why a checkout failed, in words (codes from the saga and the payment service). */
export const REJECTION_LABELS: Record<string, string> = {
  'out-of-stock': 'Not enough stock left',
  'card-limit-exceeded': 'The amount exceeds the card limit',
  'inventory-unavailable': 'Inventory did not respond in time',
  'payment-unavailable': 'The payment service did not respond in time',
  voided: 'The payment was voided before authorization',
};
