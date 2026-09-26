/**
 * Filters in the syntax of the specification-repository HTTP module:
 * `filter=field:op:value` (all must match) and `orFilter=a:op:v;b:op:v` (one group of alternatives).
 */
export const OPERATORS = [
  'eq',
  'neq',
  'contains',
  'notcontains',
  'startswith',
  'endswith',
  'gt',
  'gte',
  'lt',
  'lte',
  'between',
  'in',
  'notin',
  'isnull',
  'isnotnull',
  'isempty',
  'isnotempty',
] as const;

export type Operator = (typeof OPERATORS)[number];

/** Operators whose value is a list joined with `|`. */
export const LIST_OPERATORS: readonly Operator[] = ['between', 'in', 'notin'];

/** Operators that take no value. */
export const VALUELESS_OPERATORS: readonly Operator[] = [
  'isnull',
  'isnotnull',
  'isempty',
  'isnotempty',
];

export interface Condition {
  field: string;
  operator: Operator;
  value?: string | string[];
}

export interface SortOrder {
  field: string;
  direction: 'asc' | 'desc';
}

export interface FilterQuery {
  /** Every condition must match. */
  all: Condition[];
  /** Each group must have at least one matching condition. */
  anyOf: Condition[][];
  sort?: SortOrder[];
  page?: number;
  size?: number;
}

export const EMPTY_QUERY: FilterQuery = { all: [], anyOf: [] };
