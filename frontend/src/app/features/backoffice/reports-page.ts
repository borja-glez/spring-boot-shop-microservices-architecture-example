import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';

import { REJECTION_LABELS, STATUS_LABELS } from '../../core/api/order-models';
import { ApiProblem, toProblem } from '../../core/api/problem';
import { RebuildStatus, ReportingApi, dayRange, lineDayRange } from '../../core/api/reporting-api';
import { toQueryParams } from '../../core/filters/filter-serializer';
import { ProblemAlert } from '../../shared/problem-alert';
import { valueOf } from '../../shared/resource-value';

/** Width of a bar, as a percentage of the largest value in its list. */
export function barWidth(value: number, max: number): number {
  return max > 0 ? Math.max(2, Math.round((value / max) * 100)) : 0;
}

/**
 * Backoffice reports from reporting-service, which keeps its own copy of the order events and
 * answers with grouped queries (GROUP BY, HAVING, COUNT DISTINCT) of specification-repository.
 */
@Component({
  selector: 'app-reports-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, DatePipe, ProblemAlert],
  template: `
    <h1>Reports</h1>
    <form class="panel filters" (submit)="$event.preventDefault()">
      <label>
        From
        <input
          class="field"
          type="date"
          [value]="from()"
          (change)="from.set($any($event.target).value)"
        />
      </label>
      <label>
        To
        <input
          class="field"
          type="date"
          [value]="to()"
          (change)="to.set($any($event.target).value)"
        />
      </label>
      <label>
        Minimum units per product
        <input
          class="field"
          type="number"
          min="1"
          [value]="minUnits()"
          (change)="minUnits.set(+$any($event.target).value || 1)"
        />
      </label>
      <button type="button" class="button secondary" [disabled]="rebuilding()" (click)="rebuild()">
        {{ rebuilding() ? 'Rebuilding…' : 'Rebuild from Kafka' }}
      </button>
      <p class="request">
        <code>GET /api/reporting/…?{{ readableQuery() }}</code>
      </p>
      @if (lastRebuild(); as r) {
        <p class="rebuild">
          Last rebuild: {{ r.finishedAt | date: 'medium' }}. The reports fill up again as the events
          are replayed.
        </p>
      }
    </form>

    @if (problem(); as problem) {
      <app-problem-alert [problem]="problem" />
    }

    @if (data(); as d) {
      <section class="cards" aria-label="Orders by status">
        @for (s of d.summary; track s.status) {
          <div class="panel card">
            <span class="value">{{ s.orders }}</span>
            <span class="label">{{ statusLabels[s.status] }}</span>
          </div>
        }
      </section>

      <div class="grid">
        <section class="panel report">
          <h2>Confirmed sales by day</h2>
          <table>
            <thead>
              <tr>
                <th scope="col">Day</th>
                <th scope="col" class="num">Orders</th>
                <th scope="col" class="num">Customers</th>
                <th scope="col">Revenue</th>
              </tr>
            </thead>
            <tbody>
              @for (day of d.sales; track day.day + day.currency) {
                <tr>
                  <td>{{ day.day | date: 'mediumDate' }}</td>
                  <td class="num">{{ day.orders }}</td>
                  <td class="num">{{ day.customers }}</td>
                  <td>
                    <span class="bar" [style.width.%]="width(day.revenue, maxRevenue())"></span>
                    {{ day.revenue | currency: day.currency : 'symbol' : '1.2-2' }}
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="4" class="empty">No sales in this period.</td>
                </tr>
              }
            </tbody>
          </table>
        </section>

        <section class="panel report">
          <h2>Best-selling products</h2>
          <table>
            <thead>
              <tr>
                <th scope="col">Product</th>
                <th scope="col" class="num">Units</th>
                <th scope="col" class="num">Orders</th>
                <th scope="col" class="num">Revenue</th>
              </tr>
            </thead>
            <tbody>
              @for (p of d.products; track p.sku + p.currency) {
                <tr>
                  <td>
                    {{ p.name }} <code>{{ p.sku }}</code>
                    <span class="bar" [style.width.%]="width(p.units, maxUnits())"></span>
                  </td>
                  <td class="num">{{ p.units }}</td>
                  <td class="num">{{ p.orders }}</td>
                  <td class="num">
                    {{ p.revenue | currency: p.currency : 'symbol' : '1.2-2' }}
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td colspan="4" class="empty">No product reaches the minimum.</td>
                </tr>
              }
            </tbody>
          </table>
        </section>

        <section class="panel report">
          <h2>Rejection reasons</h2>
          <table>
            <tbody>
              @for (r of d.rejections; track r.reason) {
                <tr>
                  <td>{{ rejectionLabels[r.reason] ?? r.reason }}</td>
                  <td class="num">{{ r.orders }}</td>
                </tr>
              } @empty {
                <tr>
                  <td class="empty">No rejected orders.</td>
                </tr>
              }
            </tbody>
          </table>
        </section>

        <section class="panel report">
          <h2>Customers</h2>
          <table>
            <tbody>
              @for (c of d.customers; track c.customerId + c.currency) {
                <tr>
                  <td>{{ c.customerId }}</td>
                  <td class="num">{{ c.orders }} {{ c.orders === 1 ? 'order' : 'orders' }}</td>
                  <td class="num">
                    {{ c.spent | currency: c.currency : 'symbol' : '1.2-2' }}
                  </td>
                </tr>
              } @empty {
                <tr>
                  <td class="empty">No confirmed purchases.</td>
                </tr>
              }
            </tbody>
          </table>
        </section>
      </div>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    h1,
    h2 {
      margin: 0;
    }
    h2 {
      font-size: var(--step-1);
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
    .request,
    .rebuild {
      flex-basis: 100%;
      margin: 0;
      font-size: var(--step--1);
      overflow-wrap: anywhere;
    }
    .rebuild {
      color: var(--leaf);
    }
    .cards {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
      gap: 12px;
    }
    .card {
      padding: 16px;
      display: flex;
      flex-direction: column;
    }
    .value {
      font-family: var(--font-display);
      font-size: var(--step-3);
      font-weight: 700;
    }
    .label {
      color: var(--ink-soft);
    }
    .grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr));
      gap: 16px;
      align-items: start;
    }
    .report {
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 8px;
      overflow-x: auto;
    }
    table {
      width: 100%;
      border-collapse: collapse;
      font-size: var(--step--1);
    }
    th,
    td {
      padding: 6px 8px;
      border-bottom: 1px solid var(--line);
      text-align: left;
      vertical-align: top;
    }
    td code {
      color: var(--ink-soft);
    }
    .num {
      text-align: right;
      font-variant-numeric: tabular-nums;
    }
    .bar {
      display: block;
      height: 6px;
      margin: 2px 0;
      border-radius: 3px;
      background: var(--cobalt);
    }
    .empty {
      color: var(--ink-soft);
    }
  `,
})
export class ReportsPage {
  private readonly api = inject(ReportingApi);

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly rejectionLabels = REJECTION_LABELS;
  protected readonly from = signal('');
  protected readonly to = signal('');
  protected readonly minUnits = signal(1);
  protected readonly rebuilding = signal(false);
  protected readonly lastRebuild = signal<RebuildStatus | null>(null);
  private readonly rebuildProblem = signal<ApiProblem | null>(null);
  private readonly refresh = signal(0);

  protected readonly readableQuery = computed(() =>
    decodeURIComponent(toQueryParams(dayRange(this.from(), this.to()))),
  );

  protected readonly reports = rxResource({
    params: () => ({
      from: this.from(),
      to: this.to(),
      minUnits: this.minUnits(),
      refresh: this.refresh(),
    }),
    stream: ({ params }) => {
      const orders = dayRange(params.from, params.to);
      return forkJoin({
        summary: this.api.summary(orders),
        sales: this.api.salesByDay(orders),
        products: this.api.topProducts(lineDayRange(params.from, params.to), params.minUnits),
        rejections: this.api.rejections(orders),
        customers: this.api.customers(orders),
      });
    },
  });
  protected readonly data = computed(() => valueOf(this.reports));

  protected readonly maxRevenue = computed(() =>
    Math.max(0, ...(this.data()?.sales.map((s) => s.revenue) ?? [])),
  );
  protected readonly maxUnits = computed(() =>
    Math.max(0, ...(this.data()?.products.map((p) => p.units) ?? [])),
  );

  protected readonly problem = computed(() => {
    const error = this.reports.error();
    return this.rebuildProblem() ?? (error ? toProblem(error) : null);
  });

  protected width(value: number, max: number): number {
    return barWidth(value, max);
  }

  protected rebuild(): void {
    this.rebuilding.set(true);
    this.rebuildProblem.set(null);
    this.api.rebuild().subscribe({
      next: (status) => {
        this.rebuilding.set(false);
        this.lastRebuild.set(status);
        // The replay takes a moment; ask again a little later.
        setTimeout(() => this.refresh.update((n) => n + 1), 2000);
      },
      error: (error: unknown) => {
        this.rebuilding.set(false);
        this.rebuildProblem.set(toProblem(error));
      },
    });
  }
}
