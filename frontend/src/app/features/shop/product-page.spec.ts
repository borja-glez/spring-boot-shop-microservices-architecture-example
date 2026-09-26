import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';

import { ProductPage } from './product-page';

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
});
