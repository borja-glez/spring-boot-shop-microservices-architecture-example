import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Observable, catchError, forkJoin, map, of } from 'rxjs';

import {
  CHAOS_SERVICES,
  ChaosApi,
  ChaosFault,
  ChaosService,
  FAULT_GUIDE,
} from '../../core/api/chaos-api';
import { ApiProblem, toProblem } from '../../core/api/problem';
import { ProblemAlert } from '../../shared/problem-alert';
import { valueOf } from '../../shared/resource-value';

const SERVICE_NAMES: Record<ChaosService, string> = {
  payments: 'Payments',
  inventory: 'Inventory',
  orders: 'Orders',
  catalog: 'Catalog',
};

/** What a service answered: its faults, `null` when chaos is off there, or why it failed. */
interface ServiceFaults {
  service: ChaosService;
  faults: ChaosFault[] | null;
  problem: ApiProblem | null;
}

/**
 * Injects the failures the platform is designed to absorb, so the saga timeline, the reports and
 * the notices show how it recovers. The endpoints answer only when the services start with
 * `SHOP_CHAOS_ENABLED=true`; each service is shown on its own, so one that is down (a likely thing
 * during a chaos demo) does not hide the others.
 */
@Component({
  selector: 'app-chaos-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ProblemAlert],
  template: `
    <h1>Chaos</h1>
    <p class="intro">
      Trigger on purpose the failures the platform is designed to absorb and watch it recover: place
      an order with a fault switched on and follow its saga in “My orders”, the reports and the
      notifications. Everything starts off, and faults can only be switched on when the services
      start with <code>SHOP_CHAOS_ENABLED=true</code>.
    </p>
    <div class="actions">
      <button type="button" class="button secondary" (click)="calmDown()" [disabled]="busy()">
        Turn everything off
      </button>
      <button type="button" class="button secondary" (click)="reload()">Refresh</button>
    </div>

    @if (problem(); as problem) {
      <app-problem-alert [problem]="problem" />
    }

    @for (entry of services(); track entry.service) {
      <section class="panel service" [attr.aria-labelledby]="'chaos-' + entry.service">
        <h2 [id]="'chaos-' + entry.service">{{ name(entry.service) }}</h2>
        @if (entry.problem; as problem) {
          <app-problem-alert [problem]="problem" />
        } @else if (entry.faults === null) {
          <p class="off">Chaos is disabled in this service.</p>
        } @else {
          <ul>
            @for (fault of entry.faults; track fault.key) {
              <li [class.active]="fault.value > 0">
                <div class="fault">
                  @if (fault.kind === 'TOGGLE') {
                    <label>
                      <input
                        type="checkbox"
                        [checked]="fault.value > 0"
                        [disabled]="busy()"
                        (change)="set(entry.service, fault, $any($event.target).checked ? 1 : 0)"
                      />
                      {{ label(fault) }}
                    </label>
                  } @else {
                    <form
                      (submit)="
                        $event.preventDefault(); setDelay(entry.service, fault, delay.value)
                      "
                    >
                      <label [for]="'delay-' + fault.key">{{ label(fault) }}</label>
                      <input
                        #delay
                        class="field"
                        type="number"
                        min="0"
                        max="60000"
                        step="500"
                        [id]="'delay-' + fault.key"
                        [value]="fault.value"
                      />
                      <span>ms</span>
                      <button type="submit" class="button secondary" [disabled]="busy()">
                        Apply
                      </button>
                    </form>
                  }
                  <code>{{ fault.key }}</code>
                </div>
                <p class="effect">{{ effect(fault) }}</p>
              </li>
            }
          </ul>
        }
      </section>
    }
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }
    h1,
    .intro {
      margin: 0;
    }
    .intro {
      max-width: 70ch;
      color: var(--ink-soft);
    }
    .actions {
      display: flex;
      gap: 8px;
    }
    .service {
      padding: 16px;
    }
    h2 {
      margin: 0 0 8px;
      font-size: var(--step-1);
    }
    .off {
      margin: 0;
      color: var(--ink-soft);
    }
    ul {
      list-style: none;
      margin: 0;
      padding: 0;
      display: flex;
      flex-direction: column;
    }
    li {
      padding: 10px 0;
      border-top: 1px solid var(--line);
    }
    li.active label {
      color: var(--tomato);
      font-weight: 700;
    }
    .fault {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 16px;
      align-items: center;
      justify-content: space-between;
    }
    .fault form {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      align-items: center;
    }
    .fault input.field {
      width: 7em;
    }
    .fault code {
      color: var(--ink-soft);
      font-size: var(--step--1);
    }
    .effect {
      margin: 4px 0 0;
      max-width: 80ch;
      font-size: var(--step--1);
      color: var(--ink-soft);
    }
  `,
})
export class ChaosPage {
  private readonly api = inject(ChaosApi);

  protected readonly busy = signal(false);
  private readonly refresh = signal(0);
  private readonly actionProblem = signal<ApiProblem | null>(null);

  private readonly all = rxResource({
    params: () => this.refresh(),
    stream: () =>
      forkJoin(
        CHAOS_SERVICES.map((service) =>
          this.api.faults(service).pipe(
            map((faults): ServiceFaults => ({ service, faults, problem: null })),
            catchError((error: unknown) =>
              of<ServiceFaults>({ service, faults: null, problem: toProblem(error) }),
            ),
          ),
        ),
      ),
  });

  protected readonly services = computed<ServiceFaults[]>(() => valueOf(this.all) ?? []);

  protected readonly problem = computed(() => this.actionProblem());

  protected name(service: ChaosService): string {
    return SERVICE_NAMES[service];
  }

  protected label(fault: ChaosFault): string {
    return FAULT_GUIDE[fault.key]?.label ?? fault.key;
  }

  protected effect(fault: ChaosFault): string {
    return FAULT_GUIDE[fault.key]?.effect ?? fault.description;
  }

  protected reload(): void {
    this.actionProblem.set(null);
    this.refreshFaults();
  }

  private refreshFaults(): void {
    this.refresh.update((n) => n + 1);
  }

  protected setDelay(service: ChaosService, fault: ChaosFault, raw: string): void {
    const value = Number(raw);
    if (raw.trim() === '' || !Number.isInteger(value) || value < 0 || value > 60000) {
      this.actionProblem.set({
        status: 400,
        code: 'invalid-delay',
        title: 'Invalid delay',
        detail: 'Enter a whole number of milliseconds between 0 and 60000.',
      });
      return;
    }
    this.set(service, fault, value);
  }

  protected set(service: ChaosService, fault: ChaosFault, value: number): void {
    this.run(this.api.set(service, fault.key, value));
  }

  /** Every service, whether or not its faults could be read: it may be back by now. */
  protected calmDown(): void {
    this.run(forkJoin(CHAOS_SERVICES.map((service) => this.api.reset(service))));
  }

  private run(action: Observable<unknown>): void {
    this.busy.set(true);
    this.actionProblem.set(null);
    action.subscribe({
      error: (error: unknown) => {
        this.busy.set(false);
        this.actionProblem.set(toProblem(error));
        this.refreshFaults();
      },
      complete: () => {
        this.busy.set(false);
        this.refreshFaults();
      },
    });
  }
}
