import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';

import { BackofficeApi, StockView } from '../../core/api/backoffice-api';
import { ApiProblem, toProblem } from '../../core/api/problem';
import { Condition, FilterQuery } from '../../core/filters/filter-model';
import { toQueryParams } from '../../core/filters/filter-serializer';
import { ProblemAlert } from '../../shared/problem-alert';
import { valueOf } from '../../shared/resource-value';

const PAGE_SIZE = 20;

/**
 * Warehouse stock. Setting a product's stock low is the easiest way to watch the checkout saga
 * reject an order for lack of stock.
 */
@Component({
  selector: 'app-stock-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, ProblemAlert],
  template: `
    <h1>Stock</h1>
    <p class="intro">
      Inventory starts with 25 units of every published product. A checkout reservation sets units
      aside until the order is confirmed or rolled back.
    </p>
    <form class="panel filters" (submit)="$event.preventDefault()">
      <label>
        Name or SKU
        <input
          class="field"
          [value]="text()"
          (change)="text.set($any($event.target).value.trim()); page.set(0)"
        />
      </label>
      <label class="check">
        <input
          type="checkbox"
          [checked]="lowOnly()"
          (change)="lowOnly.set($any($event.target).checked); page.set(0)"
        />
        Only fewer than 5 units
      </label>
      <p class="request">
        <code>GET /api/inventory/stock?{{ readableQuery() }}</code>
      </p>
    </form>

    @if (problem(); as problem) {
      <app-problem-alert [problem]="problem" />
    }

    @if (stockPage(); as page) {
      <div class="panel table">
        <table>
          <thead>
            <tr>
              <th scope="col">Product</th>
              <th scope="col" class="num">On hand</th>
              <th scope="col" class="num">Reserved</th>
              <th scope="col" class="num">Available</th>
              <th scope="col">Stock count</th>
              <th scope="col">Updated</th>
            </tr>
          </thead>
          <tbody>
            @for (item of page.content; track item.productId) {
              <tr [class.low]="item.available < 5">
                <td>
                  {{ item.name }}
                  <code>{{ item.sku }}</code>
                </td>
                <td class="num">{{ item.onHand }}</td>
                <td class="num">{{ item.reserved }}</td>
                <td class="num">{{ item.available }}</td>
                <td>
                  <form
                    class="adjust"
                    (submit)="$event.preventDefault(); adjust(item, count.value)"
                  >
                    <label class="visually-hidden" [for]="'count-' + item.productId"
                      >Counted units of {{ item.name }}</label
                    >
                    <input
                      #count
                      class="field"
                      type="number"
                      min="0"
                      required
                      [id]="'count-' + item.productId"
                      [value]="item.onHand"
                    />
                    <button
                      type="submit"
                      class="button secondary"
                      [disabled]="saving() === item.productId"
                    >
                      Save
                    </button>
                  </form>
                </td>
                <td>{{ item.updatedAt | date: 'short' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
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
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    h1,
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
    .filters label.check {
      flex-direction: row;
      align-items: center;
    }
    .request {
      flex-basis: 100%;
      margin: 0;
      font-size: var(--step--1);
      overflow-wrap: anywhere;
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
      vertical-align: middle;
    }
    td code {
      display: block;
      color: var(--ink-soft);
    }
    .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    tr.low td.num:nth-child(4) {
      color: var(--tomato);
      font-weight: 700;
    }
    .adjust {
      display: flex;
      gap: 6px;
    }
    .adjust input {
      width: 6em;
    }
    .pages {
      display: flex;
      gap: 12px;
      align-items: center;
    }
  `,
})
export class StockPage {
  private readonly api = inject(BackofficeApi);

  protected readonly text = signal('');
  protected readonly lowOnly = signal(false);
  protected readonly page = signal(0);
  protected readonly saving = signal<string | null>(null);
  private readonly adjustProblem = signal<ApiProblem | null>(null);
  private readonly refresh = signal(0);

  protected readonly query = computed<FilterQuery>(() => {
    const all: Condition[] = [];
    if (this.lowOnly()) {
      all.push({ field: 'onHand', operator: 'lt', value: '5' });
    }
    const text = this.text();
    return {
      all,
      anyOf: text
        ? [
            [
              { field: 'name', operator: 'contains', value: text },
              { field: 'sku', operator: 'contains', value: text },
            ],
          ]
        : [],
      sort: [{ field: 'sku', direction: 'asc' }],
      page: this.page(),
      size: PAGE_SIZE,
    };
  });
  protected readonly readableQuery = computed(() =>
    decodeURIComponent(toQueryParams(this.query())),
  );

  protected readonly stock = rxResource({
    params: () => ({ query: this.query(), refresh: this.refresh() }),
    stream: ({ params }) => this.api.stock(params.query),
  });
  protected readonly stockPage = computed(() => valueOf(this.stock));

  protected readonly problem = computed(() => {
    const error = this.stock.error();
    return this.adjustProblem() ?? (error ? toProblem(error) : null);
  });

  protected adjust(item: StockView, value: string): void {
    const onHand = Number(value);
    if (value.trim() === '' || !Number.isInteger(onHand) || onHand < 0) {
      this.adjustProblem.set({
        status: 400,
        code: 'invalid-stock',
        title: 'Invalid stock count',
        detail: `Enter the counted units of ${item.sku}: a whole number from 0.`,
      });
      return;
    }
    this.saving.set(item.productId);
    this.adjustProblem.set(null);
    this.api.adjustStock(item.productId, onHand).subscribe({
      next: () => {
        this.saving.set(null);
        this.refresh.update((n) => n + 1);
      },
      error: (error: unknown) => {
        this.saving.set(null);
        this.adjustProblem.set(toProblem(error));
      },
    });
  }
}
