import { DatePipe } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  linkedSignal,
  signal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { OrderStatus, STATUS_LABELS } from '../../core/api/order-models';
import { OrdersApi } from '../../core/api/orders-api';
import { toProblem } from '../../core/api/problem';
import { FilterQuery } from '../../core/filters/filter-model';
import { toQueryParams } from '../../core/filters/filter-serializer';
import { UserStore } from '../../core/user/user-store';
import { PriceTag } from '../../shared/price-tag';
import { ProblemAlert } from '../../shared/problem-alert';
import { valueOf } from '../../shared/resource-value';

type SortChoice = 'recientes' | 'importe';

const PAGE_SIZE = 10;

/** The current user's orders, read from the orders read model with HTTP filters. */
@Component({
  selector: 'app-orders-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, PriceTag, ProblemAlert],
  template: `
    <h1>My orders</h1>
    <form class="panel filters" (submit)="$event.preventDefault()">
      <label>
        Status
        <select class="field" (change)="status.set($any($event.target).value); page.set(0)">
          <option value="">All</option>
          @for (s of statuses; track s) {
            <option [value]="s" [selected]="status() === s">{{ statusLabels[s] }}</option>
          }
        </select>
      </label>
      <label>
        Containing product (SKU)
        <input
          class="field"
          placeholder="CAF-001"
          [value]="sku()"
          (change)="sku.set($any($event.target).value.trim()); page.set(0)"
        />
      </label>
      <label>
        Sort
        <select class="field" (change)="sort.set($any($event.target).value)">
          <option value="recientes">Newest first</option>
          <option value="importe">Highest total</option>
        </select>
      </label>
      <p class="request">
        <code>GET /api/orders?{{ readableQuery() }}</code>
      </p>
    </form>

    @if (problem(); as problem) {
      <app-problem-alert [problem]="problem" />
    }

    @if (ordersPage(); as page) {
      @if (page.content.length === 0) {
        <p class="panel empty">
          {{ users.current().name }} has no orders matching these filters.
          <a routerLink="/">Go to the shop</a>
        </p>
      } @else {
        <ul class="list">
          @for (order of page.content; track order.orderId) {
            <li class="panel">
              <a [routerLink]="['/orders', order.orderId]">
                Order of {{ order.placedAt | date: 'medium' }}
              </a>
              <span
                class="status"
                [class.cancelled]="order.status === 'CANCELLED' || order.status === 'REJECTED'"
                [class.progress]="order.status === 'PLACED'"
              >
                {{ statusLabels[order.status] }}
              </span>
              <span class="lines"
                >{{ order.lineCount }} {{ order.lineCount === 1 ? 'product' : 'products' }}</span
              >
              <app-price-tag [amount]="order.total" [currency]="order.currency" />
            </li>
          }
        </ul>
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
      }
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
      max-width: 900px;
    }
    h1 {
      margin: 0;
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
    .empty {
      padding: 24px;
      margin: 0;
    }
    .list {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
      gap: 8px;
    }
    .list li {
      display: grid;
      grid-template-columns: 1fr auto auto auto;
      align-items: center;
      gap: 16px;
      padding: 12px 16px;
    }
    .status {
      padding: 2px 10px;
      border-radius: 999px;
      background: var(--leaf-wash);
      color: var(--leaf);
      font-size: var(--step--1);
      font-weight: 600;
    }
    .status.cancelled {
      background: var(--tomato-wash);
      color: var(--tomato);
    }
    .status.progress {
      background: var(--cobalt-wash);
      color: var(--cobalt);
    }
    .lines {
      color: var(--ink-soft);
      font-size: var(--step--1);
    }
    .pages {
      display: flex;
      gap: 12px;
      align-items: center;
    }
    @media (max-width: 600px) {
      .list li {
        grid-template-columns: 1fr auto;
      }
    }
  `,
})
export class OrdersPage {
  private readonly api = inject(OrdersApi);
  protected readonly users = inject(UserStore);

  protected readonly statuses = Object.keys(STATUS_LABELS) as OrderStatus[];
  protected readonly statusLabels = STATUS_LABELS;
  protected readonly status = signal<OrderStatus | ''>('');
  protected readonly sku = signal('');
  protected readonly sort = signal<SortChoice>('recientes');
  /** Back to the first page whenever the user changes: another user has other orders. */
  protected readonly page = linkedSignal({
    source: () => this.users.current().id,
    computation: () => 0,
  });

  protected readonly query = computed<FilterQuery>(() => ({
    all: [
      ...(this.status()
        ? [{ field: 'status', operator: 'eq' as const, value: this.status() }]
        : []),
      ...(this.sku() ? [{ field: 'lines.sku', operator: 'eq' as const, value: this.sku() }] : []),
    ],
    anyOf: [],
    sort: [
      this.sort() === 'importe'
        ? { field: 'total', direction: 'desc' }
        : { field: 'placedAt', direction: 'desc' },
    ],
    page: this.page(),
    size: PAGE_SIZE,
  }));
  protected readonly readableQuery = computed(() =>
    decodeURIComponent(toQueryParams(this.query())),
  );

  protected readonly orders = rxResource({
    // The user is part of the params so switching user reloads the list.
    params: () => ({ query: this.query(), user: this.users.current().id }),
    stream: ({ params }) => this.api.myOrders(params.query),
  });

  protected readonly ordersPage = computed(() => valueOf(this.orders));

  protected readonly problem = computed(() => {
    const error = this.orders.error();
    return error ? toProblem(error) : null;
  });
}
