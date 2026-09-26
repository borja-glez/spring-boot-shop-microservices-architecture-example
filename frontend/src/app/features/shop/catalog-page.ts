import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';

import { CatalogApi } from '../../core/api/catalog-api';
import { FacetValue } from '../../core/api/models';
import { toProblem } from '../../core/api/problem';
import { toQueryParams } from '../../core/filters/filter-serializer';
import { ProblemAlert } from '../../shared/problem-alert';
import {
  CatalogSearch,
  EMPTY_SEARCH,
  SORT_LABELS,
  SortChoice,
  toFilterQuery,
} from './catalog-search';
import { FacetGroup } from './facet-group';
import { ProductCard } from './product-card';
import { valueOf } from '../../shared/resource-value';

/** The shop: free-text search, refinements with live counts, and the product grid. */
@Component({
  selector: 'app-catalog-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FacetGroup, ProductCard, ProblemAlert],
  templateUrl: './catalog-page.html',
  styleUrl: './catalog-page.scss',
})
export class CatalogPage {
  private readonly api = inject(CatalogApi);

  protected readonly search = signal<CatalogSearch>(EMPTY_SEARCH);
  protected readonly draftText = signal('');
  protected readonly sortLabels = SORT_LABELS;
  protected readonly sortChoices = Object.keys(SORT_LABELS) as SortChoice[];

  protected readonly query = computed(() => toFilterQuery(this.search()));
  protected readonly queryString = computed(() => toQueryParams(this.query()));
  /** The same request, decoded so people can read it. */
  protected readonly readableQuery = computed(() =>
    decodeURIComponent(this.queryString().replaceAll('+', ' ')),
  );

  protected readonly results = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.api.search(params),
  });

  protected readonly facets = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.api.facets(params),
  });

  protected readonly categories = rxResource({ stream: () => this.api.categories() });

  protected readonly resultsPage = computed(() => valueOf(this.results));
  protected readonly facetValues = computed(() => valueOf(this.facets));

  /**
   * Categories with the counts of the current search. With no filters, the full category list;
   * once the shopper refines, the facet counts, so every number matches what a click would show.
   */
  protected readonly categoryFacet = computed<FacetValue[]>(() => {
    const facets = this.facetValues()?.categories;
    if (facets && this.hasFilters()) {
      return [...facets].sort((a, b) => a.label.localeCompare(b.label, 'es'));
    }
    return (valueOf(this.categories) ?? [])
      .filter((c) => c.activeProducts > 0)
      .map((c) => ({ value: c.slug, label: c.name, count: c.activeProducts }));
  });

  protected readonly problem = computed(() => {
    const error = this.results.error() ?? this.facets.error();
    return error ? toProblem(error) : null;
  });

  protected readonly hasFilters = computed(() => {
    const s = this.search();
    return !!(
      s.text ||
      s.category ||
      s.sellers.length ||
      s.tags.length ||
      s.minPrice !== null ||
      s.maxPrice !== null
    );
  });

  protected submitText(event: Event): void {
    event.preventDefault();
    this.update({ text: this.draftText() });
  }

  protected toggleCategory(slug: string): void {
    this.update({ category: this.search().category === slug ? null : slug });
  }

  protected toggleSeller(id: string): void {
    this.update({ sellers: toggle(this.search().sellers, id) });
  }

  protected toggleTag(tag: string): void {
    this.update({ tags: toggle(this.search().tags, tag) });
  }

  protected setPrice(bound: 'minPrice' | 'maxPrice', raw: string): void {
    const value = raw === '' ? null : Number(raw);
    this.update({ [bound]: value !== null && Number.isFinite(value) && value >= 0 ? value : null });
  }

  protected setSort(sort: string): void {
    this.update({ sort: sort as SortChoice });
  }

  protected goToPage(page: number): void {
    this.search.update((s) => ({ ...s, page }));
  }

  protected clear(): void {
    this.draftText.set('');
    this.search.set(EMPTY_SEARCH);
  }

  /** Any change of criteria starts again from the first page. */
  private update(change: Partial<CatalogSearch>): void {
    this.search.update((s) => ({ ...s, ...change, page: 0 }));
  }
}

function toggle(values: string[], value: string): string[] {
  return values.includes(value) ? values.filter((v) => v !== value) : [...values, value];
}
