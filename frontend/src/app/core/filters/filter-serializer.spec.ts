import { describe, expect, it } from 'vitest';

import { FilterError, toQueryParams } from './filter-serializer';
import { FilterQuery } from './filter-model';

function parse(query: FilterQuery): URLSearchParams {
  return new URLSearchParams(toQueryParams(query));
}

describe('toQueryParams', () => {
  it('writes each required condition as a filter parameter', () => {
    const params = parse({
      all: [
        { field: 'name', operator: 'contains', value: 'café' },
        { field: 'status', operator: 'eq', value: 'ACTIVE' },
      ],
      anyOf: [],
    });

    expect(params.getAll('filter')).toEqual(['name:contains:café', 'status:eq:ACTIVE']);
  });

  it('joins range and list values with a pipe', () => {
    const params = parse({
      all: [
        { field: 'price.amount', operator: 'between', value: ['5', '20'] },
        { field: 'tags', operator: 'in', value: ['ecologico', 'artesania'] },
      ],
      anyOf: [],
    });

    expect(params.getAll('filter')).toEqual([
      'price.amount:between:5|20',
      'tags:in:ecologico|artesania',
    ]);
  });

  it('writes each alternative group as one orFilter parameter', () => {
    const params = parse({
      all: [],
      anyOf: [
        [
          { field: 'categories.slug', operator: 'eq', value: 'vinos' },
          { field: 'seller.id', operator: 'eq', value: 'seller-diego' },
        ],
      ],
    });

    expect(params.getAll('orFilter')).toEqual([
      'categories.slug:eq:vinos;seller.id:eq:seller-diego',
    ]);
  });

  it('omits the value of operators that take none', () => {
    const params = parse({ all: [{ field: 'publishedAt', operator: 'isnotnull' }], anyOf: [] });

    expect(params.getAll('filter')).toEqual(['publishedAt:isnotnull']);
  });

  it('keeps colons inside values', () => {
    const params = parse({
      all: [{ field: 'name', operator: 'contains', value: 'Crème brûlée: kit' }],
      anyOf: [],
    });

    expect(params.getAll('filter')).toEqual(['name:contains:Crème brûlée: kit']);
  });

  it('adds sort, page and size', () => {
    const params = parse({
      all: [],
      anyOf: [],
      sort: [{ field: 'price.amount', direction: 'desc' }],
      page: 2,
      size: 24,
    });

    expect(params.getAll('sort')).toEqual(['price.amount,desc']);
    expect(params.get('page')).toBe('2');
    expect(params.get('size')).toBe('24');
  });

  it('skips empty groups', () => {
    expect(toQueryParams({ all: [], anyOf: [[]] })).toBe('');
  });

  it('rejects a pipe inside a list value because the API cannot escape it', () => {
    expect(() =>
      toQueryParams({
        all: [{ field: 'tags', operator: 'in', value: ['a|b', 'c'] }],
        anyOf: [],
      }),
    ).toThrow(FilterError);
  });

  it('rejects a semicolon inside an alternative because it separates conditions', () => {
    expect(() =>
      toQueryParams({
        all: [],
        anyOf: [[{ field: 'name', operator: 'contains', value: 'a;b' }]],
      }),
    ).toThrow(FilterError);
  });

  it('rejects empty values because the API cannot filter by an empty string', () => {
    expect(() =>
      toQueryParams({ all: [{ field: 'name', operator: 'eq', value: '' }], anyOf: [] }),
    ).toThrow(/needs a value/);
  });

  it('rejects ranges that do not have exactly two ends', () => {
    expect(() =>
      toQueryParams({
        all: [{ field: 'price.amount', operator: 'between', value: ['5'] }],
        anyOf: [],
      }),
    ).toThrow(/two values/);
  });
});
