/**
 * @ghatana/state — State Machine
 *
 * Minimal type-safe finite state machine implementation.
 */

import type {
  StateMachine,
  StateMachineContext,
  Transition,
} from "./types";

// ---------------------------------------------------------------------------
// createStateMachine
// ---------------------------------------------------------------------------

/**
 * Creates an immutable finite state machine.
 *
 * @param initial     - The starting state.
 * @param transitions - Allowed transitions between states.
 * @param context     - Optional context object passed to guards and effects.
 *
 * @example
 * ```ts
 * type TrafficState = 'red' | 'yellow' | 'green';
 * type TrafficEvent = 'go' | 'slow' | 'stop';
 *
 * const machine = createStateMachine<TrafficState, TrafficEvent>('red', [
 *   { from: 'red',    to: 'green',  on: 'go'   },
 *   { from: 'green',  to: 'yellow', on: 'slow' },
 *   { from: 'yellow', to: 'red',    on: 'stop' },
 * ]);
 *
 * const next = machine.transition('go'); // state → 'green'
 * ```
 */
export function createStateMachine<
  TState extends string,
  TEvent extends string,
>(
  initial: TState,
  transitions: ReadonlyArray<Transition<TState, TEvent>>,
  context: StateMachineContext = {}
): StateMachine<TState, TEvent> {
  return new StateMachineImpl(initial, transitions, context);
}

// ---------------------------------------------------------------------------
// Implementation
// ---------------------------------------------------------------------------

class StateMachineImpl<TState extends string, TEvent extends string>
  implements StateMachine<TState, TEvent>
{
  constructor(
    public readonly currentState: TState,
    private readonly transitions: ReadonlyArray<Transition<TState, TEvent>>,
    private readonly ctx: StateMachineContext
  ) {}

  canTransition(event: TEvent): boolean {
    const t = this.findTransition(event);
    if (!t) return false;
    if (t.guard && !t.guard(this.ctx)) return false;
    return true;
  }

  transition(event: TEvent): StateMachine<TState, TEvent> {
    const t = this.findTransition(event);

    if (!t) {
      throw new Error(
        `StateMachine: no transition from '${this.currentState}' on event '${event}'`
      );
    }

    if (t.guard && !t.guard(this.ctx)) {
      throw new Error(
        `StateMachine: guard rejected transition from '${this.currentState}' on event '${event}'`
      );
    }

    t.effect?.(this.ctx);

    return new StateMachineImpl(t.to, this.transitions, this.ctx);
  }

  validEvents(): TEvent[] {
    return this.transitions
      .filter(
        (t) => t.from === this.currentState && (!t.guard || t.guard(this.ctx))
      )
      .map((t) => t.on);
  }

  private findTransition(
    event: TEvent
  ): Transition<TState, TEvent> | undefined {
    return this.transitions.find(
      (t) => t.from === this.currentState && t.on === event
    );
  }
}
