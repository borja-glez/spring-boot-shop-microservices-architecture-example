import { JsonPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { CatalogApi, RawResponse } from '../../core/api/catalog-api';
import { FILTERABLE_FIELDS, SORTABLE_FIELDS } from '../../core/api/models';
import {
  Condition,
  LIST_OPERATORS,
  OPERATORS,
  Operator,
  VALUELESS_OPERATORS,
} from '../../core/filters/filter-model';
import { FilterError, toQueryParams } from '../../core/filters/filter-serializer';
import { LAB_PRESETS, LabPreset } from './lab-presets';

interface LabRow {
  id: number;
  field: string;
  operator: Operator;
  value: string;
}

let nextId = 1;

function newRow(): LabRow {
  return { id: nextId++, field: 'name', operator: 'contains', value: '' };
}

/**
 * Builds requests against the catalog search, shows the exact query string and the raw answer,
 * errors included. Useful to learn the filter syntax and how the API reports invalid filters.
 */
@Component({
  selector: 'app-filter-lab-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [JsonPipe],
  templateUrl: './filter-lab-page.html',
  styleUrl: './filter-lab-page.scss',
})
export class FilterLabPage {
  private readonly api = inject(CatalogApi);

  protected readonly fields = FILTERABLE_FIELDS;
  protected readonly sortableFields = SORTABLE_FIELDS;
  protected readonly operators = OPERATORS;
  protected readonly presets = LAB_PRESETS;

  /** Starts with a working example so the first request already returns products. */
  protected readonly required = signal<LabRow[]>([{ ...newRow(), value: 'aceite' }]);
  protected readonly alternatives = signal<LabRow[]>([]);
  protected readonly sortField = signal('');
  protected readonly sortDirection = signal<'asc' | 'desc'>('asc');
  protected readonly size = signal(5);

  /** Set when the user edits the query string by hand or loads a preset. */
  protected readonly manualQuery = signal<string | null>(null);

  protected readonly generated = computed<{ query: string; error: string | null }>(() => {
    try {
      const query = toQueryParams({
        all: this.required().map(toCondition),
        anyOf: [this.alternatives().map(toCondition)],
        sort: this.sortField()
          ? [{ field: this.sortField(), direction: this.sortDirection() }]
          : [],
        size: this.size(),
      });
      return { query, error: null };
    } catch (e) {
      if (e instanceof FilterError) {
        return { query: '', error: e.message };
      }
      throw e;
    }
  });

  protected readonly effectiveQuery = computed(() => this.manualQuery() ?? this.generated().query);

  protected readonly response = signal<RawResponse | null>(null);
  protected readonly sending = signal(false);
  protected readonly activePreset = signal<LabPreset | null>(null);

  protected readonly statusClass = computed(() => {
    const status = this.response()?.status ?? 0;
    if (status >= 500 || status === 0) return 'server';
    if (status >= 400) return 'client';
    return 'ok';
  });

  protected needsValue(operator: Operator): boolean {
    return !VALUELESS_OPERATORS.includes(operator);
  }

  protected isList(operator: Operator): boolean {
    return LIST_OPERATORS.includes(operator);
  }

  protected addRow(list: 'required' | 'alternatives'): void {
    this.rowsOf(list).update((rows) => [...rows, newRow()]);
    this.manualQuery.set(null);
  }

  protected removeRow(list: 'required' | 'alternatives', id: number): void {
    this.rowsOf(list).update((rows) => rows.filter((r) => r.id !== id));
    this.manualQuery.set(null);
  }

  protected editRow(
    list: 'required' | 'alternatives',
    id: number,
    change: Partial<Omit<LabRow, 'id'>>,
  ): void {
    this.rowsOf(list).update((rows) => rows.map((r) => (r.id === id ? { ...r, ...change } : r)));
    this.manualQuery.set(null);
    this.activePreset.set(null);
  }

  protected editQuery(query: string): void {
    this.manualQuery.set(query);
    this.activePreset.set(null);
  }

  protected resetQuery(): void {
    this.manualQuery.set(null);
    this.activePreset.set(null);
  }

  protected loadPreset(preset: LabPreset): void {
    this.manualQuery.set(preset.query);
    this.activePreset.set(preset);
    this.send();
  }

  protected send(): void {
    this.sending.set(true);
    this.api.raw('/api/catalog/products', this.effectiveQuery()).subscribe((response) => {
      this.response.set(response);
      this.sending.set(false);
    });
  }

  private rowsOf(list: 'required' | 'alternatives') {
    return list === 'required' ? this.required : this.alternatives;
  }
}

function toCondition(row: LabRow): Condition {
  if (VALUELESS_OPERATORS.includes(row.operator)) {
    return { field: row.field, operator: row.operator };
  }
  if (LIST_OPERATORS.includes(row.operator)) {
    return {
      field: row.field,
      operator: row.operator,
      value: row.value.split(',').map((v) => v.trim()),
    };
  }
  return { field: row.field, operator: row.operator, value: row.value };
}
