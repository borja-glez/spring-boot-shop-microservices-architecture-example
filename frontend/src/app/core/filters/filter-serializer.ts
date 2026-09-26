import { Condition, FilterQuery, LIST_OPERATORS, VALUELESS_OPERATORS } from './filter-model';

/** A filter the API cannot represent. The message is shown to the user as is. */
export class FilterError extends Error {
  override readonly name = 'FilterError';
}

/**
 * Serializes a filter query into a URL query string (without the leading `?`).
 *
 * The API has no escaping: `|` separates list values and `;` separates alternatives, so values
 * containing them are rejected here instead of being silently split by the server.
 */
export function toQueryParams(query: FilterQuery): string {
  const params = new URLSearchParams();
  for (const condition of query.all) {
    params.append('filter', encodeCondition(condition, false));
  }
  for (const group of query.anyOf) {
    if (group.length > 0) {
      params.append('orFilter', group.map((c) => encodeCondition(c, true)).join(';'));
    }
  }
  for (const order of query.sort ?? []) {
    params.append('sort', `${order.field},${order.direction}`);
  }
  if (query.page !== undefined) {
    params.set('page', String(query.page));
  }
  if (query.size !== undefined) {
    params.set('size', String(query.size));
  }
  return params.toString();
}

function encodeCondition(condition: Condition, alternative: boolean): string {
  const { field, operator } = condition;
  if (VALUELESS_OPERATORS.includes(operator)) {
    return `${field}:${operator}`;
  }
  const values = Array.isArray(condition.value) ? condition.value : [condition.value ?? ''];
  if (LIST_OPERATORS.includes(operator)) {
    if (operator === 'between' && values.length !== 2) {
      throw new FilterError(`The range on “${field}” needs two values: from and to.`);
    }
    for (const value of values) {
      checkValue(field, value, alternative);
      if (value.includes('|')) {
        throw new FilterError(`The list values of “${field}” cannot contain “|”.`);
      }
    }
    return `${field}:${operator}:${values.join('|')}`;
  }
  const value = values[0];
  checkValue(field, value, alternative);
  return `${field}:${operator}:${value}`;
}

function checkValue(field: string, value: string, alternative: boolean): void {
  if (value.length === 0) {
    throw new FilterError(`The filter on “${field}” needs a value.`);
  }
  if (alternative && value.includes(';')) {
    throw new FilterError(`Values in an “any of” group cannot contain “;” (${field}).`);
  }
}
