import { describe, expect, it } from 'vitest';

import { EMPTY_SEARCH, toFilterQuery } from './catalog-search';

describe('toFilterQuery', () => {
  it('asks for everything when nothing is selected', () => {
    expect(toFilterQuery(EMPTY_SEARCH)).toEqual({
      all: [],
      anyOf: [],
      sort: [{ field: 'publishedAt', direction: 'desc' }],
      page: 0,
      size: 24,
    });
  });

  it('searches the text in the name or the description', () => {
    const query = toFilterQuery({ ...EMPTY_SEARCH, text: '  café  ' });

    expect(query.anyOf).toEqual([
      [
        { field: 'name', operator: 'contains', value: 'café' },
        { field: 'description', operator: 'contains', value: 'café' },
      ],
    ]);
  });

  it('drops characters the filter syntax reserves from free text', () => {
    const query = toFilterQuery({ ...EMPTY_SEARCH, text: 'vino;tinto' });

    expect(query.anyOf[0][0].value).toBe('vino tinto');
  });

  it('combines category, sellers, tags and price bounds', () => {
    const query = toFilterQuery({
      ...EMPTY_SEARCH,
      category: 'vinos',
      sellers: ['seller-elena', 'seller-fermin'],
      tags: ['denominacion-de-origen'],
      minPrice: 10,
      maxPrice: 15,
    });

    expect(query.all).toEqual([
      { field: 'categories.slug', operator: 'eq', value: 'vinos' },
      { field: 'seller.id', operator: 'in', value: ['seller-elena', 'seller-fermin'] },
      { field: 'tags', operator: 'in', value: ['denominacion-de-origen'] },
      { field: 'price.amount', operator: 'gte', value: '10' },
      { field: 'price.amount', operator: 'lte', value: '15' },
    ]);
  });

  it('maps the sort choice to the API sort', () => {
    expect(toFilterQuery({ ...EMPTY_SEARCH, sort: 'price-asc' }).sort).toEqual([
      { field: 'price.amount', direction: 'asc' },
    ]);
    expect(toFilterQuery({ ...EMPTY_SEARCH, sort: 'name' }).sort).toEqual([
      { field: 'name', direction: 'asc' },
    ]);
  });
});
