import { CurrencyPipe, DatePipe, JsonPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { of } from 'rxjs';

import { BackofficeApi, PAYMENT_STATUS_LABELS, PaymentStatus } from '../../core/api/backoffice-api';
import { REJECTION_LABELS } from '../../core/api/order-models';
import { toProblem } from '../../core/api/problem';
import { Condition, FilterQuery } from '../../core/filters/filter-model';
import { toQueryParams } from '../../core/filters/filter-serializer';
import { ProblemAlert } from '../../shared/problem-alert';
import { valueOf } from '../../shared/resource-value';

const PAGE_SIZE = 20;

/** Payments of every order, with the event stream of the one selected. */
@Component({
  selector: 'app-payments-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, JsonPipe, ProblemAlert],
  template: `
    <h1>Payments</h1>
    <p class="intro">
      Every order has a stream of payment events. The demo card declines any amount over €300.
    </p>
    <form class="panel filters" (submit)="$event.preventDefault()">
      <label>
        Status
        <select class="field" (change)="status.set($any($event.target).value); page.set(0)">
          <option value="">All</option>
          @for (s of statuses; track s) {
            <option [value]="s">{{ statusLabels[s] }}</option>
          }
        </select>
      </label>
      <label>
        Customer
        <input
          class="field"
          [value]="customer()"
          (change)="customer.set($any($event.target).value.trim()); page.set(0)"
        />
      </label>
      <p class="request">
        <code>GET /api/payments?{{ readableQuery() }}</code>
      </p>
    </form>

    @if (problem(); as problem) {
      <app-problem-alert [problem]="problem" />
    }

    <div class="columns">
      @if (paymentsPage(); as page) {
        <div class="panel table">
          <table>
            <thead>
              <tr>
                <th scope="col">Order</th>
                <th scope="col">Customer</th>
                <th scope="col" class="num">Amount</th>
                <th scope="col">Status</th>
                <th scope="col">Updated</th>
              </tr>
            </thead>
            <tbody>
              @for (p of page.content; track p.orderId) {
                <tr [class.selected]="selected() === p.orderId">
                  <td>
                    <button type="button" class="link" (click)="selected.set(p.orderId)">
                      {{ p.orderId.slice(0, 8) }}
                    </button>
                  </td>
                  <td>{{ p.customerId ?? '—' }}</td>
                  <td class="num">
                    {{ p.currency ? (p.amount | currency: p.currency : 'symbol' : '1.2-2') : '—' }}
                  </td>
                  <td>
                    <span class="status" [class]="p.status.toLowerCase()">{{
                      statusLabels[p.status]
                    }}</span>
                    @if (p.reason) {
                      <small>{{ reasonLabels[p.reason] ?? p.reason }}</small>
                    }
                  </td>
                  <td>{{ p.updatedAt | date: 'short' }}</td>
                </tr>
              }
            </tbody>
          </table>
          @if (page.totalPages > 1) {
            <nav class="pages" aria-label="Pages">
              <button
                type="button"
                class="button secondary"
                [disabled]="page.page === 0"
                (click)="this.page.set(page.page - 1)"
              >
                Previous
              </button>
              <span>Page {{ page.page + 1 }} of {{ page.totalPages }}</span>
              <button
                type="button"
                class="button secondary"
                [disabled]="page.page + 1 >= page.totalPages"
                (click)="this.page.set(page.page + 1)"
              >
                Next
              </button>
            </nav>
          }
        </div>
      }
      @if (selected()) {
        <section class="panel history" aria-label="Payment events">
          <h2>Payment events</h2>
          <ol>
            @for (e of historyEvents(); track e.version) {
              <li>
                <strong>v{{ e.version }} · {{ e.eventType.split('.').pop() }}</strong>
                <time>{{ e.occurredAt | date: 'mediumTime' }}</time>
                <pre>{{ e.event | json }}</pre>
              </li>
            }
          </ol>
        </section>
      }
    </div>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    h1,
    h2,
    .intro {
      margin: 0;
    }
    .intro {
      max-width: 70ch;
      color: var(--ink-soft);
    }
    .filters {
      display: flex;
      flex-wrap: wrap;
      gap: 12px 24px;
      padding: 16px;
      align-items: end;
    }
    .filters label {
      display: flex;
      flex-direction: column;
      gap: 4px;
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
    .request {
      flex-basis: 100%;
      margin: 0;
      font-size: var(--step--1);
      overflow-wrap: anywhere;
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
    .table {
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: var(--step--1);
    }
    th,
    td {
      padding: 8px;
      border-bottom: 1px solid var(--line);
      text-align: left;
      vertical-align: top;
    }
    tr.selected {
      background: var(--cobalt-wash);
    }
    .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .status {
      font-weight: 600;
    }
    .status.authorized {
      color: var(--leaf);
    }
    .status.declined {
      color: var(--tomato);
    }
    .status.refunded {
      color: var(--amber);
    }
    td small {
      display: block;
      color: var(--ink-soft);
    }
    .link {
      border: 0;
      background: none;
      padding: 0;
      color: var(--cobalt);
      font: inherit;
      font-family: var(--font-code);
      cursor: pointer;
      text-decoration: underline;
    }
    .history {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .history ol {
      margin: 0;
      padding-left: 20px;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .history time {
      margin-left: 8px;
      color: var(--ink-soft);
      font-size: var(--step--1);
    }
    pre {
      max-height: 200px;
      overflow: auto;
      font-size: 0.75rem;
    }
    .pages {
      display: flex;
      gap: 12px;
      align-items: center;
      padding: 8px;
    }
  `,
})
export class PaymentsPage {
  private readonly api = inject(BackofficeApi);

  protected readonly statuses = Object.keys(PAYMENT_STATUS_LABELS) as PaymentStatus[];
  protected readonly statusLabels = PAYMENT_STATUS_LABELS;
  protected readonly reasonLabels = REJECTION_LABELS;
  protected readonly status = signal<PaymentStatus | ''>('');
  protected readonly customer = signal('');
  protected readonly page = signal(0);
  protected readonly selected = signal<string | null>(null);

  protected readonly query = computed<FilterQuery>(() => {
    const all: Condition[] = [];
    if (this.status()) {
      all.push({ field: 'status', operator: 'eq', value: this.status() });
    }
    if (this.customer()) {
      all.push({ field: 'customerId', operator: 'eq', value: this.customer() });
    }
    return {
      all,
      anyOf: [],
      sort: [{ field: 'updatedAt', direction: 'desc' }],
      page: this.page(),
      size: PAGE_SIZE,
    };
  });
  protected readonly readableQuery = computed(() =>
    decodeURIComponent(toQueryParams(this.query())),
  );

  protected readonly payments = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.api.payments(params),
  });
  protected readonly paymentsPage = computed(() => valueOf(this.payments));

  protected readonly history = rxResource({
    params: () => this.selected(),
    stream: ({ params }) => (params ? this.api.paymentHistory(params) : of([])),
  });
  protected readonly historyEvents = computed(() => valueOf(this.history) ?? []);

  protected readonly problem = computed(() => {
    const error = this.payments.error() ?? this.history.error();
    return error ? toProblem(error) : null;
  });
}
