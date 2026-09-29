import { DatePipe, JsonPipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { OrderStatus, REJECTION_LABELS, STATUS_LABELS } from '../../core/api/order-models';
import { OrdersApi } from '../../core/api/orders-api';
import { ApiProblem, toProblem } from '../../core/api/problem';
import { PriceTag } from '../../shared/price-tag';
import { ProblemAlert } from '../../shared/problem-alert';
import { toCheckoutSummary } from './checkout-steps';
import { toTimeline } from './history-timeline';
import { valueOf } from '../../shared/resource-value';

/**
 * One order: its current state from the read model, its checkout saga step by step, and its history
 * from the event store with the state after each event. They are shown side by side on purpose:
 * the saga and the history are up to date the moment something happens, the read model a moment
 * later.
 */
@Component({
  selector: 'app-order-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, JsonPipe, PriceTag, ProblemAlert],
  template: `
    <a routerLink="/orders">Back to my orders</a>
    @if (problem(); as problem) {
      <app-problem-alert [problem]="problem" />
    }

    <div class="columns">
      <section class="panel detail" aria-labelledby="detail-title">
        <h1 id="detail-title">Order</h1>
        @if (order.isLoading() && !detail()) {
          <p class="waiting" role="status">Waiting for the read model to receive the order…</p>
        }
        @if (detail(); as o) {
          <p class="meta">
            <span
              class="status"
              [class.cancelled]="o.status === 'CANCELLED' || o.status === 'REJECTED'"
              [class.progress]="o.status === 'PLACED'"
              >{{ statusLabels[o.status] }}</span
            >
            placed on {{ o.placedAt | date: 'medium' }}
          </p>
          @if (o.cancelReason) {
            <p class="reason">Reason: “{{ o.cancelReason }}”</p>
          }
          @if (o.rejectionReason) {
            <p class="reason">
              {{ rejectionLabels[o.rejectionReason] ?? o.rejectionReason }}
              @if (o.rejectionDetail) {
                <small>({{ o.rejectionDetail }})</small>
              }
            </p>
          }
          <table>
            <tbody>
              @for (line of o.lines; track line.productId) {
                <tr>
                  <td>
                    {{ line.name }} <code>{{ line.sku }}</code>
                  </td>
                  <td class="num">{{ line.quantity }} × {{ money(line.unitPrice, o.currency) }}</td>
                  <td class="num">{{ money(line.subtotal, o.currency) }}</td>
                </tr>
              }
            </tbody>
          </table>
          <p class="total">
            Total <app-price-tag [amount]="o.total" [currency]="o.currency" size="large" />
          </p>
          <section class="checkout" aria-labelledby="checkout-title">
            <h2 id="checkout-title">Checkout</h2>
            @if (checkoutSummary(); as c) {
              <p class="headline" [class]="'tone-' + c.tone" role="status">{{ c.headline }}</p>
              @if (c.reason) {
                <p class="why">{{ c.reason }}</p>
              }
              <ol class="steps">
                @for (line of c.lines; track $index) {
                  <li
                    [class]="'outcome-' + line.outcome.toLowerCase()"
                    [class.compensation]="line.compensation"
                  >
                    <strong>{{ line.title }}</strong>
                    <span class="outcome">{{ line.outcomeLabel }}</span>
                    <time [attr.datetime]="line.at">{{ line.at | date: 'mediumTime' }}</time>
                    @if (line.detail) {
                      <small>{{ line.detail }}</small>
                    }
                  </li>
                }
              </ol>
            } @else if (!checkoutMissing()) {
              <p class="waiting" role="status">Loading checkout…</p>
            } @else {
              <p class="waiting">This order has no checkout.</p>
            }
          </section>
          <section class="notices" aria-labelledby="notices-title">
            <div class="history-head">
              <h2 id="notices-title">Notices sent</h2>
              <button type="button" class="button secondary" (click)="notices.reload()">
                Refresh
              </button>
            </div>
            <p class="why">
              Asked to the notifications service (Spring Boot 3, Jackson 2) over RabbitMQ.
            </p>
            @if (noticesView(); as n) {
              @if (!n.available) {
                <p class="waiting" role="status">
                  The notifications service did not answer in time.
                </p>
              } @else if (n.notices.length === 0) {
                <p class="waiting">No notice yet.</p>
              } @else {
                <ul class="notice-list">
                  @for (notice of n.notices; track notice.sentAt + notice.kind) {
                    <li>
                      <strong>{{ notice.title }}</strong>
                      <time [attr.datetime]="notice.sentAt">{{
                        notice.sentAt | date: 'mediumTime'
                      }}</time>
                      <span>{{ notice.body }}</span>
                    </li>
                  }
                </ul>
              }
            } @else if (notices.isLoading()) {
              <p class="waiting" role="status">Loading notices…</p>
            }
          </section>
          @if (o.status === 'PLACED') {
            <p class="waiting">You can cancel once checkout finishes.</p>
          }
          @if (o.status === 'CONFIRMED' && checkoutSummary()?.settled) {
            <form class="cancel" (submit)="$event.preventDefault(); cancel()">
              <label for="reason">Cancellation reason (optional)</label>
              <input
                id="reason"
                class="field"
                maxlength="200"
                [value]="reason()"
                (input)="reason.set($any($event.target).value)"
              />
              <button type="submit" class="button secondary" [disabled]="cancelling()">
                {{ cancelling() ? 'Cancelling…' : 'Cancel order' }}
              </button>
            </form>
          }
        }
      </section>

      <section class="panel history" aria-labelledby="history-title">
        <div class="history-head">
          <h2 id="history-title">History in the event store</h2>
          <button type="button" class="button secondary" (click)="history.reload()">Refresh</button>
        </div>
        <ol class="timeline">
          @for (step of timeline(); track step.version) {
            <li>
              <p class="step-head">
                <span class="version">v{{ step.version }}</span>
                <strong>{{ step.title }}</strong>
                <time [attr.datetime]="step.occurredAt">{{
                  step.occurredAt | date: 'mediumTime'
                }}</time>
              </p>
              @if (step.detail) {
                <p>{{ step.detail }}</p>
              }
              <p class="state">
                State after:
                <span [class.changed]="step.changed.includes('status')">{{
                  statusLabels[step.state.status]
                }}</span>
                ·
                <span [class.changed]="step.changed.includes('total')">{{
                  money(step.state.total, step.state.currency)
                }}</span>
              </p>
              <p class="outbox" [class.pending]="step.pending">
                {{ step.pending ? 'In the outbox, waiting for Kafka' : 'Published to Kafka' }}
              </p>
              <details>
                <summary>
                  <code>{{ step.eventType }}</code>
                </summary>
                <pre>{{ step.event | json }}</pre>
              </details>
            </li>
          }
        </ol>
      </section>
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .columns {
      display: grid;
      grid-template-columns: minmax(0, 3fr) minmax(0, 2fr);
      gap: 16px;
      align-items: start;
    }
    @media (max-width: 860px) {
      .columns {
        grid-template-columns: 1fr;
      }
    }
    section {
      padding: 20px;
      display: flex;
      flex-direction: column;
      gap: 12px;
    }
    h1,
    h2 {
      margin: 0;
    }
    p {
      margin: 0;
    }
    .meta {
      display: flex;
      align-items: center;
      gap: 8px;
      color: var(--ink-soft);
    }
    .status {
      padding: 2px 10px;
      border-radius: 999px;
      background: var(--leaf-wash);
      color: var(--leaf);
      font-weight: 600;
    }
    .status.cancelled {
      background: var(--tomato-wash);
      color: var(--tomato);
    }
    .waiting {
      color: var(--ink-soft);
    }
    .status.progress {
      background: var(--cobalt-wash);
      color: var(--cobalt);
    }
    .reason small {
      color: var(--ink-soft);
    }
    .checkout {
      display: flex;
      flex-direction: column;
      gap: 8px;
      border-top: 1px solid var(--line);
      padding: 12px 0 0;
    }
    .checkout h2,
    .notices h2 {
      font-size: var(--step-1);
    }
    .notices {
      display: flex;
      flex-direction: column;
      gap: 8px;
      border-top: 1px solid var(--line);
      padding: 12px 0 0;
    }
    .notice-list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: var(--step--1);
    }
    .notice-list li {
      display: grid;
      grid-template-columns: 1fr auto;
      gap: 2px 12px;
      padding: 6px 10px;
      border-left: 3px solid var(--cobalt);
      background: var(--tile);
    }
    .notice-list span {
      grid-column: 1 / -1;
      color: var(--ink-soft);
    }
    .headline {
      font-weight: 600;
    }
    .tone-progress {
      color: var(--cobalt);
    }
    .tone-success {
      color: var(--leaf);
    }
    .tone-failure {
      color: var(--tomato);
    }
    .tone-alert {
      color: var(--amber);
    }
    .why {
      color: var(--ink-soft);
    }
    .steps {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: var(--step--1);
    }
    .steps li {
      display: grid;
      grid-template-columns: 1fr auto auto;
      gap: 2px 12px;
      padding: 6px 10px;
      border-left: 3px solid var(--leaf);
      background: var(--tile);
    }
    .steps li.outcome-declined,
    .steps li.outcome-gave_up {
      border-left-color: var(--tomato);
    }
    .steps li.outcome-retrying {
      border-left-color: var(--amber);
    }
    .steps li.compensation strong::before {
      content: '↩ ';
    }
    .steps small {
      grid-column: 1 / -1;
      color: var(--ink-soft);
      overflow-wrap: anywhere;
    }
    table {
      width: 100%;
      border-collapse: collapse;
    }
    td {
      padding: 6px 4px;
      border-bottom: 1px solid var(--line);
    }
    td code {
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
    .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
      white-space: nowrap;
    }
    .total {
      display: flex;
      justify-content: end;
      align-items: center;
      gap: 12px;
    }
    .cancel {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;
      align-items: end;
      border-top: 1px solid var(--line);
      padding-top: 12px;
    }
    .cancel label {
      flex-basis: 100%;
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
    .cancel input {
      flex: 1;
      min-width: 12em;
    }
    .history-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 8px;
    }
    .timeline {
      list-style: none;
      margin: 0;
      padding: 0 0 0 16px;
      border-left: 2px solid var(--line);
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .timeline li {
      position: relative;
      display: flex;
      flex-direction: column;
      gap: 4px;
    }
    .timeline li::before {
      content: '';
      position: absolute;
      left: -23px;
      top: 6px;
      width: 10px;
      height: 10px;
      border-radius: 50%;
      background: var(--cobalt);
      border: 2px solid var(--surface);
    }
    .step-head {
      display: flex;
      gap: 8px;
      align-items: baseline;
    }
    .version {
      font-family: var(--font-code);
      font-size: var(--step--1);
      color: var(--cobalt);
    }
    time {
      margin-left: auto;
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
    .state {
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
    .changed {
      color: var(--ink);
      font-weight: 600;
    }
    .outbox {
      font-size: var(--step--1);
      color: var(--leaf);
    }
    .outbox.pending {
      color: var(--amber);
    }
    details pre {
      max-height: 240px;
      overflow: auto;
      font-size: 0.8rem;
    }
  `,
})
export class OrderPage {
  private readonly api = inject(OrdersApi);

  /** Bound from the route parameter. */
  readonly id = input.required<string>();
  /** Set by the checkout: the order was just placed and may not be in the read model yet. */
  readonly placed = input<string>();

  /** A new order may not be in the read model yet; its detail is requested until it shows. */
  private readonly expected = linkedSignal<OrderStatus | 'exists' | undefined>(() =>
    this.placed() ? 'exists' : undefined,
  );

  /** Bumped to watch the saga again, after a cancellation. */
  private readonly checkoutRound = signal(0);

  /** The checkout saga, polled while it runs. */
  protected readonly checkout = rxResource({
    params: () => ({ id: this.id(), round: this.checkoutRound() }),
    stream: ({ params }) => this.api.watchCheckout(params.id),
  });

  protected readonly checkoutSummary = computed(() => {
    const checkout = valueOf(this.checkout);
    return checkout ? toCheckoutSummary(checkout) : null;
  });

  /** Orders placed before the saga existed have none. */
  protected readonly checkoutMissing = computed(() => {
    const error = this.checkout.error();
    return !!error && toProblem(error).status === 404;
  });

  /** The status the read model reaches once the saga settles; the detail waits for it. */
  private readonly settledStatus = computed<OrderStatus | undefined>(() => {
    const checkout = valueOf(this.checkout);
    if (!checkout || checkout.state !== 'COMPLETED') {
      return undefined;
    }
    switch (checkout.mode) {
      case 'CHECKOUT':
        return 'CONFIRMED';
      case 'REJECTING':
        return 'REJECTED';
      case 'CANCELLING':
        return 'CANCELLED';
    }
  });

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly rejectionLabels = REJECTION_LABELS;
  protected readonly reason = signal('');
  protected readonly cancelling = signal(false);
  private readonly commandProblem = signal<ApiProblem | null>(null);

  protected readonly order = rxResource({
    params: () => ({ id: this.id(), expected: this.settledStatus() ?? this.expected() }),
    stream: ({ params }) => this.api.order(params.id, params.expected),
  });

  protected readonly detail = computed(() => valueOf(this.order));

  /**
   * Asked again whenever the read model changes: by then Kafka has delivered the event, so the
   * history can show it as published instead of pending.
   */
  protected readonly history = rxResource({
    params: () => ({ id: this.id(), seen: valueOf(this.order)?.updatedAt }),
    stream: ({ params }) => this.api.history(params.id),
  });

  protected readonly timeline = computed(() => toTimeline(valueOf(this.history) ?? []));

  /**
   * Asked once the order is in the read model, and again whenever it changes: the notifications
   * service turns the same Kafka events into notices.
   */
  protected readonly notices = rxResource({
    params: () => {
      const order = valueOf(this.order);
      return order ? { id: this.id(), seen: order.updatedAt } : undefined;
    },
    stream: ({ params }) => this.api.notices(params.id),
  });

  protected readonly noticesView = computed(() => valueOf(this.notices));

  protected readonly problem = computed(() => {
    const checkoutError = this.checkoutMissing() ? undefined : this.checkout.error();
    const error = this.order.error() ?? this.history.error() ?? checkoutError;
    return this.commandProblem() ?? (error ? toProblem(error) : null);
  });

  protected cancel(): void {
    this.cancelling.set(true);
    this.commandProblem.set(null);
    this.api.cancel(this.id(), this.reason()).subscribe({
      next: () => {
        this.cancelling.set(false);
        // The saga now refunds and releases; watch it until it settles.
        this.checkoutRound.update((round) => round + 1);
      },
      error: (error: unknown) => {
        this.cancelling.set(false);
        this.commandProblem.set(toProblem(error));
      },
    });
  }

  protected money(amount: number, currency: string): string {
    return new Intl.NumberFormat('en', { style: 'currency', currency }).format(amount);
  }
}
