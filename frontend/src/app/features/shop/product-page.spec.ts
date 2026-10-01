import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { Availability, ProductDetail } from '../../core/api/models';
import { ProductPage } from './product-page';

function detail(availability: Availability | null): ProductDetail {
  return {
    id: 'p-1',
    slug: 'te-verde',
    sku: 'CAF-005',
    name: 'Té verde matcha',
    description: 'Molido en piedra.',
    price: 21.5,
    currency: 'EUR',
    status: 'ACTIVE',
    seller: { id: 'seller-ana', displayName: 'Tostadores Ana', city: 'Bilbao' },
    categories: [],
    tags: [],
    publishedAt: null,
    updatedAt: '2026-09-29T10:00:00Z',
    version: 1,
    availability,
  };
}

async function render(product: ProductDetail): Promise<HTMLElement> {
  TestBed.configureTestingModule({
    providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
  });
  const fixture = TestBed.createComponent(ProductPage);
  fixture.componentRef.setInput('slug', product.slug);
  TestBed.tick();
  TestBed.inject(HttpTestingController)
    .expectOne(`/api/catalog/products/${product.slug}`)
    .flush(product);
  await fixture.whenStable();
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('ProductPage', () => {
  it('explains why a product cannot be shown', async () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    const fixture = TestBed.createComponent(ProductPage);
    fixture.componentRef.setInput('slug', 'no-existe');
    TestBed.tick();

    TestBed.inject(HttpTestingController).expectOne('/api/catalog/products/no-existe').flush(
      {
        status: 404,
        title: 'Not Found',
        detail: 'No product is published as no-existe',
        code: 'product-not-found',
        correlationId: 'c-1',
      },
      { status: 404, statusText: '' },
    );
    await fixture.whenStable();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('No product is published as no-existe');
    expect(text).toContain('product-not-found');
  });

  it('shows the stock the inventory reported', async () => {
    const page = await render(detail({ status: 'LOW_STOCK', units: 3 }));

    expect(page.textContent).toContain('Only 3 left');
    expect(page.textContent).toContain('Add to cart');
  });

  it('cannot add a product that is out of stock', async () => {
    const page = await render(detail({ status: 'OUT_OF_STOCK', units: 0 }));

    expect(page.textContent).toContain('Out of stock');
    expect(page.textContent).not.toContain('Add to cart');
  });

  it('still sells the product when the stock is unknown', async () => {
    const page = await render(detail({ status: 'UNKNOWN', units: null }));

    expect(page.textContent).toContain('Stock unknown right now');
    expect(page.textContent).toContain('Add to cart');
  });
});
