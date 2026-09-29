/** Shapes returned by the catalog API (see catalog-service `ProductViews`). */

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'DISCONTINUED';

export interface SellerSummary {
  id: string;
  displayName: string;
  city: string;
}

export interface ProductCard {
  id: string;
  slug: string;
  sku: string;
  name: string;
  price: number;
  currency: string;
  status: ProductStatus;
  seller: SellerSummary;
  categories: string[];
  tags: string[];
  publishedAt: string | null;
}

export interface CategoryRef {
  slug: string;
  name: string;
}

export interface ProductDetail extends Omit<ProductCard, 'categories'> {
  description: string;
  categories: CategoryRef[];
  updatedAt: string;
  version: number;
  /** Stock asked to the inventory over RabbitMQ; `null` once the product is no longer sold. */
  availability: Availability | null;
}

export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'UNKNOWN';

export interface Availability {
  status: StockStatus;
  /** Free units; `null` when the inventory did not answer. */
  units: number | null;
}

export interface FacetValue {
  value: string;
  label: string;
  count: number;
}

export interface CatalogFacets {
  categories: FacetValue[];
  sellers: FacetValue[];
  tags: FacetValue[];
  price: { min: number | null; max: number | null };
}

export interface CategoryView {
  slug: string;
  name: string;
  activeProducts: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
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
