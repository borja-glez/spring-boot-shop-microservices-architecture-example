import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { FilterLabPage } from './filter-lab-page';

describe('FilterLabPage', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FilterLabPage],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
  });

  async function render(): Promise<HTMLElement> {
    const fixture = TestBed.createComponent(FilterLabPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows the operator of the first condition as selected', async () => {
    const element = await render();

    const operator = element.querySelector<HTMLSelectElement>('select[aria-label="Operator"]');
    expect(operator?.value).toBe('contains');
  });

  it('starts with a valid example query', async () => {
    const element = await render();

    expect(element.querySelector('.builder-error')).toBeNull();
    expect(element.querySelector<HTMLTextAreaElement>('#lab-query')?.value).toContain(
      'filter=name%3Acontains%3Aaceite',
    );
  });

  it('does not report builder errors while a hand-written query is in use', async () => {
    const fixture = TestBed.createComponent(FilterLabPage);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const value = element.querySelector<HTMLInputElement>('input[aria-label="Value"]')!;
    value.value = '';
    value.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(element.querySelector('.builder-error')).not.toBeNull();

    const textarea = element.querySelector<HTMLTextAreaElement>('#lab-query')!;
    textarea.value = 'filter=status:EQ:ACTIVE';
    textarea.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    expect(element.querySelector('.builder-error')).toBeNull();
  });
});
