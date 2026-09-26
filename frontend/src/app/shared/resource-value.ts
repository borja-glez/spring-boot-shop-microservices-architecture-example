import { Resource } from '@angular/core';

/**
 * The value of a resource, or `undefined` while it has none. Since Angular 22, `value()` throws
 * when the resource failed, which would break the template that is trying to show the error.
 */
export function valueOf<T>(resource: Resource<T>): T | undefined {
  return resource.hasValue() ? resource.value() : undefined;
}
