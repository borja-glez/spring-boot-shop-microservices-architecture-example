import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { CatalogApi } from '../../core/api/catalog-api';
import { toProblem } from '../../core/api/problem';
import { toCartProduct } from '../../core/cart/cart-store';
import { AddToCart } from '../../shared/add-to-cart';
import { PriceTag } from '../../shared/price-tag';
import { ProblemAlert } from '../../shared/problem-alert';
import { valueOf } from '../../shared/resource-value';

/** Product page, reached from the grid or directly by its slug. */
@Component({
  selector: 'app-product-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PriceTag, ProblemAlert, DatePipe, AddToCart],
  template: `
    <a routerLink="/">Back to the shop</a>
    @if (problem(); as problem) {
      <app-problem-alert [problem]="problem" />
    }
    @if (detail(); as p) {
      <article class="panel">
        <div class="head">
          <h1>{{ p.name }}</h1>
          <app-price-tag [amount]="p.price" [currency]="p.currency" size="large" />
        </div>
        @if (p.status === 'DISCONTINUED') {
          <p class="discontinued">This product is no longer sold.</p>
        } @else {
          @if (cartProduct(); as cp) {
            <app-add-to-cart [product]="cp" />
          }
        }
        <p class="description">{{ p.description }}</p>
        <dl>
          <dt>Seller</dt>
          <dd>{{ p.seller.displayName }}, {{ p.seller.city }}</dd>
          <dt>Categories</dt>
          <dd>
            @for (c of p.categories; track c.slug; let last = $last) {
              {{ c.name }}{{ last ? '' : ', ' }}
            }
          </dd>
          <dt>SKU</dt>
          <dd>
            <code>{{ p.sku }}</code>
          </dd>
          @if (p.publishedAt) {
            <dt>On sale since</dt>
            <dd>{{ p.publishedAt | date: 'longDate' }}</dd>
          }
        </dl>
      </article>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
      max-width: 760px;
    }
    article {
      padding: 24px;
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    .head {
      display: flex;
      flex-wrap: wrap;
      justify-content: space-between;
      align-items: start;
      gap: 16px;
    }
    app-add-to-cart {
      align-self: start;
    }
    .description {
      margin: 0;
      max-width: 65ch;
    }
    .discontinued {
      margin: 0;
      color: var(--tomato);
      font-weight: 600;
    }
    dl {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 6px 16px;
      margin: 0;
    }
    dt {
      color: var(--ink-soft);
    }
    dd {
      margin: 0;
    }
  `,
})
export class ProductPage {
  private readonly api = inject(CatalogApi);

  /** Bound from the route parameter. */
  readonly slug = input.required<string>();

  protected readonly product = rxResource({
    params: () => this.slug(),
    stream: ({ params }) => this.api.product(params),
  });

  protected readonly detail = computed(() => valueOf(this.product));

  protected readonly cartProduct = computed(() => {
    const product = this.detail();
    return product ? toCartProduct(product) : null;
  });

  protected readonly problem = computed(() => {
    const error = this.product.error();
    return error ? toProblem(error) : null;
  });
}
