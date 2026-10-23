import type { components } from './generated/catalog-api.v1';

/** Shapes of the catalog API, taken from its contract (`catalog-api.v1.yaml`). */
type CatalogSchemas = components['schemas'];

export type ProductStatus = CatalogSchemas['ProductStatus'];
export type StockStatus = CatalogSchemas['StockStatus'];
export type SellerSummary = CatalogSchemas['SellerSummary'];
export type CategoryRef = CatalogSchemas['CategoryRef'];
export type ProductCard = CatalogSchemas['ProductCard'];
export type ProductDetail = CatalogSchemas['ProductDetail'];
export type ProductPage = CatalogSchemas['ProductPage'];
/** Stock asked to the inventory over RabbitMQ; `ProductDetail.availability` is `null` once the product is no longer sold. */
export type Availability = NonNullable<CatalogSchemas['Availability']>;
export type FacetValue = CatalogSchemas['FacetValue'];
export type CatalogFacets = CatalogSchemas['CatalogFacets'];
export type CategoryView = CatalogSchemas['Category'];

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** A page without a total: the server skips the count and only says whether more rows follow. */
export interface SliceResponse<T> {
  content: T[];
  page: number;
  size: number;
  hasNext: boolean;
}

/** Fields the public search accepts (the whitelist declared by the catalog controller). */
export const FILTERABLE_FIELDS = [
  'name',
  'description',
  'sku',
  'price.amount',
  'status',
  'categories.slug',
  'tags',
  'seller.id',
  'seller.city',
  'publishedAt',
] as const;

export const SORTABLE_FIELDS = ['name', 'sku', 'price.amount', 'publishedAt'] as const;
