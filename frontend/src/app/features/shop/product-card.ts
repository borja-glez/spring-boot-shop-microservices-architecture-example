import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ProductCard as Product } from '../../core/api/models';
import { toCartProduct } from '../../core/cart/cart-store';
import { AddToCart } from '../../shared/add-to-cart';
import { PriceTag } from '../../shared/price-tag';

/** A product on its stall: name, who sells it, and the price tag. */
@Component({
  selector: 'app-product-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, PriceTag, AddToCart],
  template: `
    <article>
      <a class="name" [routerLink]="['/products', product().slug]">{{ product().name }}</a>
      <p class="seller">{{ product().seller.displayName }} · {{ product().seller.city }}</p>
      @if (product().tags.length) {
        <ul class="tags" aria-label="Tags">
          @for (tag of product().tags; track tag) {
            <li>{{ tag }}</li>
          }
        </ul>
      }
      <app-price-tag [amount]="product().price" [currency]="product().currency" />
      <app-add-to-cart [product]="cartProduct()" />
    </article>
  `,
  styles: `
    article {
      display: grid;
      grid-template-rows: auto auto 1fr auto auto;
      gap: 6px;
      height: 100%;
      padding: 16px;
      background: var(--surface);
      border: 1px solid var(--line);
      border-radius: var(--radius-m);
    }
    .name {
      font-family: var(--font-display);
      font-weight: 600;
      font-size: var(--step-1);
      line-height: 1.15;
      color: var(--ink);
      text-decoration: none;
    }
    .name:hover {
      color: var(--cobalt);
      text-decoration: underline;
    }
    .seller {
      margin: 0;
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
    .tags {
      display: flex;
      flex-wrap: wrap;
      gap: 4px;
      list-style: none;
      margin: 0;
      padding: 0;
      align-content: start;
    }
    .tags li {
      font-size: 0.75rem;
      padding: 1px 8px;
      border-radius: 999px;
      background: var(--leaf-wash);
      color: var(--leaf);
    }
    app-price-tag {
      justify-self: start;
      margin-top: 6px;
    }
  `,
})
export class ProductCard {
  readonly product = input.required<Product>();

  protected readonly cartProduct = computed(() => toCartProduct(this.product()));
}
