import { HttpErrorResponse } from '@angular/common/http';

/** An RFC 9457 problem as rendered by the shop services. */
export interface ApiProblem {
  status: number;
  code: string;
  title: string;
  detail: string;
  correlationId?: string;
  errors?: { field: string; message: string }[];
}

export function toProblem(error: unknown): ApiProblem {
  if (!(error instanceof HttpErrorResponse)) {
    return {
      status: 0,
      code: 'client-error',
      title: 'Browser error',
      detail: error instanceof Error ? error.message : String(error),
    };
  }
  if (error.status === 0) {
    return {
      status: 0,
      code: 'network-error',
      title: 'Cannot reach the API',
      detail: 'Could not contact the gateway. Check that the services are running.',
    };
  }
  const body = typeof error.error === 'object' && error.error !== null ? error.error : {};
  return {
    status: error.status,
    code: body.code ?? `http-${error.status}`,
    title: body.title ?? error.statusText,
    detail: body.detail ?? error.message,
    correlationId: body.correlationId ?? error.headers?.get('X-Correlation-Id') ?? undefined,
    errors: body.errors,
  };
}
