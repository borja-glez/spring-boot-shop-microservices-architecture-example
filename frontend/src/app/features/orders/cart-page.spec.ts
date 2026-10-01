import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { CartQuote } from '../../core/api/order-models';
import { CartStore } from '../../core/cart/cart-store';
import { CartPage } from './cart-page';

const TEA = {
  productId: 'p-tea',
  slug: 'te-verde',
  sku: 'CAF-005',
  name: 'Té verde',
  price: 20,
  currency: 'EUR',
};
const HONEY = { ...TEA, productId: 'p-honey', slug: 'miel', sku: 'DES-001', name: 'Miel' };

async function render(quote: CartQuote): Promise<HTMLElement> {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
  const cart = TestBed.inject(CartStore);
  cart.add(TEA, 2);
  cart.add(HONEY, 5);
  const fixture = TestBed.createComponent(CartPage);
  TestBed.tick();

  const request = TestBed.inject(HttpTestingController).expectOne('/api/orders/quote');
  expect(request.request.body).toEqual({
    items: [
      { productId: 'p-tea', quantity: 2 },
      { productId: 'p-honey', quantity: 5 },
    ],
  });
  request.flush(quote);
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('CartPage', () => {
  beforeEach(() => localStorage.clear());
  afterEach(() => localStorage.clear());

  it('warns about price changes and missing stock before the order is placed', async () => {
    const page = await render({
      lines: [
        {
          productId: 'p-tea',
          sku: 'CAF-005',
          name: 'Té verde',
          quantity: 2,
          unitPrice: 21.5,
          subtotal: 43,
          available: 10,
          problem: null,
        },
        {
          productId: 'p-honey',
          sku: 'DES-001',
          name: 'Miel',
          quantity: 5,
          unitPrice: 20,
          subtotal: 100,
          available: 3,
          problem: 'NOT_ENOUGH_STOCK',
        },
      ],
      total: 143,
      currency: 'EUR',
      stockChecked: true,
      orderable: false,
    });

    const text = page.textContent ?? '';
    expect(text).toContain('Price is now €21.50');
    expect(text).toContain('Only 3 available');
    expect(text).toContain('Total now');
    const place = [...page.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Place order'),
    );
    expect(place?.disabled).toBe(true);
  });

  it('lets the order go ahead when the inventory did not answer', async () => {
    const page = await render({
      lines: [
        {
          productId: 'p-tea',
          sku: 'CAF-005',
          name: 'Té verde',
          quantity: 2,
          unitPrice: 20,
          subtotal: 40,
          available: null,
          problem: null,
        },
        {
          productId: 'p-honey',
          sku: 'DES-001',
          name: 'Miel',
          quantity: 5,
          unitPrice: 20,
          subtotal: 100,
          available: null,
          problem: null,
        },
      ],
      total: 140,
      currency: 'EUR',
      stockChecked: false,
      orderable: true,
    });

    expect(page.textContent).toContain('the stock was not checked');
    const place = [...page.querySelectorAll('button')].find((b) =>
      b.textContent?.includes('Place order'),
    );
    expect(place?.disabled).toBe(false);
  });
});
