import { Condition, FilterQuery, SortOrder } from '../../core/filters/filter-model';

export type SortChoice = 'newest' | 'price-asc' | 'price-desc' | 'name';

/** What the shopper has chosen on the catalog page. */
export interface CatalogSearch {
  text: string;
  category: string | null;
  sellers: string[];
  tags: string[];
  minPrice: number | null;
  maxPrice: number | null;
  sort: SortChoice;
  page: number;
  size: number;
}

export const EMPTY_SEARCH: CatalogSearch = {
  text: '',
  category: null,
  sellers: [],
  tags: [],
  minPrice: null,
  maxPrice: null,
  sort: 'newest',
  page: 0,
  size: 24,
};

export const SORT_LABELS: Record<SortChoice, string> = {
  newest: 'Newest',
  'price-asc': 'Price: low to high',
  'price-desc': 'Price: high to low',
  name: 'Name',
};

const SORTS: Record<SortChoice, SortOrder> = {
  newest: { field: 'publishedAt', direction: 'desc' },
  'price-asc': { field: 'price.amount', direction: 'asc' },
  'price-desc': { field: 'price.amount', direction: 'desc' },
  name: { field: 'name', direction: 'asc' },
};

/** Translates the shopper's choices into API filters. */
export function toFilterQuery(search: CatalogSearch): FilterQuery {
  const all: Condition[] = [];
  const anyOf: Condition[][] = [];

  // ';' separates alternatives in orFilter and cannot be escaped, so free text drops it.
  const text = search.text.replaceAll(';', ' ').trim();
  if (text) {
    anyOf.push([
      { field: 'name', operator: 'contains', value: text },
      { field: 'description', operator: 'contains', value: text },
    ]);
  }
  if (search.category) {
    all.push({ field: 'categories.slug', operator: 'eq', value: search.category });
  }
  if (search.sellers.length) {
    all.push({ field: 'seller.id', operator: 'in', value: [...search.sellers] });
  }
  if (search.tags.length) {
    all.push({ field: 'tags', operator: 'in', value: [...search.tags] });
  }
  if (search.minPrice !== null) {
    all.push({ field: 'price.amount', operator: 'gte', value: String(search.minPrice) });
  }
  if (search.maxPrice !== null) {
    all.push({ field: 'price.amount', operator: 'lte', value: String(search.maxPrice) });
  }
  return { all, anyOf, sort: [SORTS[search.sort]], page: search.page, size: search.size };
}
