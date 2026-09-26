/**
 * Ready-made requests against the catalog search that walk through the HTTP filter syntax of
 * specification-repository: text search, ranges, lists, collections, alternatives, sorting and
 * the RFC 9457 problems returned for invalid filters.
 */
export interface LabPreset {
  id: string;
  title: string;
  expected: string;
  query: string;
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** Midnight (UTC) of the day `days` days ago, in the ISO-8601 form an OffsetDateTime accepts. */
function daysAgo(days: number): string {
  return `${new Date(Date.now() - days * DAY_MS).toISOString().slice(0, 10)}T00:00:00Z`;
}

export const LAB_PRESETS: readonly LabPreset[] = [
  {
    id: 'text-search',
    title: 'Case- and accent-insensitive text',
    expected:
      '“contains” searches inside the name. The catalog ignores case and accents, so “cafe” finds “Café de Colombia”, “CAFÉ MOLIDO” and “Cafetera italiana”.',
    query: 'filter=name:contains:cafe',
  },
  {
    id: 'price-range',
    title: 'Price range',
    expected:
      '“between” takes two values separated by “|” (encoded as %7C in the URL) and includes both ends: products from €5 to €20, cheapest first.',
    query: 'filter=price.amount:between:5%7C20&sort=price.amount,asc',
  },
  {
    id: 'category-list',
    title: 'Several categories',
    expected:
      '“in” accepts a list of values separated by “|”. The “categories.slug” field walks the relation to the categories: wines and preserves show up.',
    query: 'filter=categories.slug:in:vinos%7Cconservas',
  },
  {
    id: 'tag',
    title: 'Tag in a collection',
    expected:
      'Tags are a collection of simple values (@ElementCollection). “tags:eq:ecologico” returns the products that have that tag among theirs.',
    query: 'filter=tags:eq:ecologico',
  },
  {
    id: 'combined',
    title: 'Several conditions at once',
    expected:
      'Every “filter” parameter is combined with AND: organic products that also cost less than €10.',
    query: 'filter=tags:eq:ecologico&filter=price.amount:lt:10',
  },
  {
    id: 'or-group',
    title: 'Alternatives with orFilter',
    expected:
      '“orFilter” groups conditions separated by “;” and any one of them is enough: cheeses, or products tagged “navidad”.',
    query: 'orFilter=name:contains:queso;tags:eq:navidad',
  },
  {
    id: 'sort',
    title: 'Sort by price',
    expected:
      '“sort=field,direction” sorts the result. Only fields declared as sortable are accepted: here, wines from most to least expensive.',
    query: 'filter=categories.slug:eq:vinos&sort=price.amount,desc',
  },
  {
    id: 'nested-field',
    title: 'Field of an association',
    expected:
      '“seller.city” navigates from the product to its seller. “eq” is an exact match: the city is written as is, accent included.',
    query: 'filter=seller.city:eq:Jaén',
  },
  {
    id: 'published-since',
    title: 'Published in the last 30 days',
    expected:
      '“gte” on a date: the value uses ISO-8601 with a time zone, because “publishedAt” is an OffsetDateTime. Sorted newest first.',
    query: `filter=publishedAt:gte:${daysAgo(30)}&sort=publishedAt,desc`,
  },
  {
    id: 'not-null',
    title: 'Field with a value',
    expected:
      '“isnotnull” and “isnull” take no value. Every product on sale has a publication date, so the whole catalog shows up here.',
    query: 'filter=publishedAt:isnotnull',
  },
  {
    id: 'empty-collection',
    title: 'No tags',
    expected:
      '“isempty” checks that a collection has no elements (its opposite is “isnotempty”): products without any tag.',
    query: 'filter=tags:isempty',
  },
  {
    id: 'field-not-allowed',
    title: 'Field not allowed',
    expected:
      'The seller’s email is not in the endpoint’s list of filterable fields. The response is a 400 with an RFC 9457 problem of type “invalid-filter”.',
    query: 'filter=seller.email:startswith:ana',
  },
  {
    id: 'sort-not-allowed',
    title: 'Sort not allowed',
    expected:
      'Sorting is also restricted to a list of fields: sorting by the seller’s email returns 400 “invalid-filter”.',
    query: 'sort=seller.email,asc',
  },
  {
    id: 'invalid-value',
    title: 'Badly formatted value',
    expected:
      'The price is numeric and “abc” cannot be converted. The response is a 400 “invalid-filter” that explains which value failed and which type was expected.',
    query: 'filter=price.amount:gte:abc',
  },
  {
    id: 'unknown-operator',
    title: 'Unknown operator',
    expected:
      '“like” is not an operator of the syntax (the equivalent is “contains”). The response is a 400 “invalid-filter” that names the operator.',
    query: 'filter=name:like:cafe',
  },
];
