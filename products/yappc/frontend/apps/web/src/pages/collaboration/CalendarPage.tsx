import React, { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';

// ============================================================================
// Types
// ============================================================================

type EventType = 'meeting' | 'deadline' | 'release' | 'review';

interface CalendarEvent {
  id: string;
  title: string;
  date: string;
  time?: string;
  type: EventType;
  description?: string;
}

interface CalendarResponse {
  events: CalendarEvent[];
}

// ============================================================================
// Constants
// ============================================================================

const EVENT_COLORS: Record<EventType, string> = {
  meeting: 'bg-blue-400',
  deadline: 'bg-red-400',
  release: 'bg-green-400',
  review: 'bg-purple-400',
};

const EVENT_LABELS: Record<EventType, string> = {
  meeting: 'Meeting',
  deadline: 'Deadline',
  release: 'Release',
  review: 'Review',
};

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
];

// ============================================================================
// Helpers
// ============================================================================

function getDaysInMonth(year: number, month: number): number {
  return new Date(year, month + 1, 0).getDate();
}

function getFirstDayOfMonth(year: number, month: number): number {
  return new Date(year, month, 1).getDay();
}

function toDateKey(year: number, month: number, day: number): string {
  return `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

// ============================================================================
// API
// ============================================================================

async function fetchCalendarEvents(): Promise<CalendarResponse> {
  const res = await fetch('/api/calendar/events', {
    headers: { Authorization: `Bearer ${localStorage.getItem('auth_token') ?? ''}` },
  });
  if (!res.ok) throw new Error('Failed to load calendar events');
  return res.json();
}

// ============================================================================
// Component
// ============================================================================

/**
 * CalendarPage — Monthly calendar grid with event indicators.
 *
 * @doc.type component
 * @doc.purpose Calendar management interface with monthly grid and upcoming events
 * @doc.layer product
 */
const CalendarPage: React.FC = () => {
  const today = useMemo(() => new Date(), []);
  const [currentMonth, setCurrentMonth] = useState(today.getMonth());
  const [currentYear, setCurrentYear] = useState(today.getFullYear());

  const { data, isLoading, error } = useQuery<CalendarResponse>({
    queryKey: ['calendar-events'],
    queryFn: fetchCalendarEvents,
  });

  const events = data?.events ?? [];

  const eventsByDate = useMemo(() => {
    const map = new Map<string, CalendarEvent[]>();
    for (const event of events) {
      const key = event.date.slice(0, 10);
      const list = map.get(key) ?? [];
      list.push(event);
      map.set(key, list);
    }
    return map;
  }, [events]);

  const daysInMonth = getDaysInMonth(currentYear, currentMonth);
  const firstDay = getFirstDayOfMonth(currentYear, currentMonth);

  const calendarCells = useMemo(() => {
    const cells: Array<{ day: number; isCurrentMonth: boolean; dateKey: string }> = [];

    const prevMonth = currentMonth === 0 ? 11 : currentMonth - 1;
    const prevYear = currentMonth === 0 ? currentYear - 1 : currentYear;
    const prevDays = getDaysInMonth(prevYear, prevMonth);
    for (let i = firstDay - 1; i >= 0; i--) {
      const d = prevDays - i;
      cells.push({ day: d, isCurrentMonth: false, dateKey: toDateKey(prevYear, prevMonth, d) });
    }

    for (let d = 1; d <= daysInMonth; d++) {
      cells.push({ day: d, isCurrentMonth: true, dateKey: toDateKey(currentYear, currentMonth, d) });
    }

    const nextMonth = currentMonth === 11 ? 0 : currentMonth + 1;
    const nextYear = currentMonth === 11 ? currentYear + 1 : currentYear;
    const remaining = 42 - cells.length;
    for (let d = 1; d <= remaining; d++) {
      cells.push({ day: d, isCurrentMonth: false, dateKey: toDateKey(nextYear, nextMonth, d) });
    }

    return cells;
  }, [currentYear, currentMonth, daysInMonth, firstDay]);

  const upcomingEvents = useMemo(() => {
    const todayStr = toDateKey(today.getFullYear(), today.getMonth(), today.getDate());
    return events
      .filter((e) => e.date.slice(0, 10) >= todayStr)
      .sort((a, b) => a.date.localeCompare(b.date))
      .slice(0, 8);
  }, [events, today]);

  const todayKey = toDateKey(today.getFullYear(), today.getMonth(), today.getDate());

  const goToPrev = () => {
    if (currentMonth === 0) { setCurrentMonth(11); setCurrentYear((y) => y - 1); }
    else setCurrentMonth((m) => m - 1);
  };

  const goToNext = () => {
    if (currentMonth === 11) { setCurrentMonth(0); setCurrentYear((y) => y + 1); }
    else setCurrentMonth((m) => m + 1);
  };

  return (
    <div className="mx-auto max-w-6xl px-6 py-8 space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-zinc-100">Calendar</h1>
        <p className="mt-1 text-sm text-zinc-400">Team events, deadlines, and milestones</p>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-12">
          <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-500" />
        </div>
      ) : error ? (
        <div className="rounded-lg border border-red-800 bg-red-900/20 p-4">
          <p className="text-sm text-red-400">Failed to load calendar. Please try again later.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Calendar Grid */}
          <div className="lg:col-span-2 bg-zinc-900 border border-zinc-800 rounded-lg p-5">
            {/* Month Navigation */}
            <div className="flex items-center justify-between mb-4">
              <button onClick={goToPrev} className="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition-colors">
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path d="M15 19l-7-7 7-7" /></svg>
              </button>
              <h2 className="text-lg font-semibold text-zinc-100">
                {MONTHS[currentMonth]} {currentYear}
              </h2>
              <button onClick={goToNext} className="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-zinc-800 transition-colors">
                <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}><path d="M9 5l7 7-7 7" /></svg>
              </button>
            </div>

            {/* Day Headers */}
            <div className="grid grid-cols-7 mb-1">
              {DAYS.map((d) => (
                <div key={d} className="py-2 text-center text-xs font-medium text-zinc-500 uppercase">
                  {d}
                </div>
              ))}
            </div>

            {/* Days Grid */}
            <div className="grid grid-cols-7">
              {calendarCells.map((cell, idx) => {
                const dayEvents = eventsByDate.get(cell.dateKey) ?? [];
                const isToday = cell.dateKey === todayKey && cell.isCurrentMonth;
                return (
                  <div
                    key={idx}
                    className={`relative min-h-[72px] border border-zinc-800/50 p-1.5 ${
                      cell.isCurrentMonth ? '' : 'opacity-30'
                    } ${isToday ? 'bg-zinc-800/60' : 'hover:bg-zinc-800/30'} transition-colors`}
                  >
                    <span
                      className={`text-xs ${
                        isToday
                          ? 'inline-flex h-6 w-6 items-center justify-center rounded-full bg-blue-600 text-white font-bold'
                          : 'text-zinc-400'
                      }`}
                    >
                      {cell.day}
                    </span>
                    <div className="mt-0.5 space-y-0.5">
                      {dayEvents.slice(0, 3).map((ev) => (
                        <div key={ev.id} className="flex items-center gap-1">
                          <span className={`inline-block h-1.5 w-1.5 rounded-full ${EVENT_COLORS[ev.type]}`} />
                          <span className="text-[10px] text-zinc-400 truncate">{ev.title}</span>
                        </div>
                      ))}
                      {dayEvents.length > 3 && (
                        <span className="text-[10px] text-zinc-500">+{dayEvents.length - 3} more</span>
                      )}
                    </div>
                  </div>
                );
              })}
            </div>

            {/* Legend */}
            <div className="mt-4 flex flex-wrap gap-4">
              {(Object.keys(EVENT_COLORS) as EventType[]).map((type) => (
                <div key={type} className="flex items-center gap-1.5">
                  <span className={`inline-block h-2.5 w-2.5 rounded-full ${EVENT_COLORS[type]}`} />
                  <span className="text-xs text-zinc-400">{EVENT_LABELS[type]}</span>
                </div>
              ))}
            </div>
          </div>

          {/* Upcoming Events Sidebar */}
          <div className="bg-zinc-900 border border-zinc-800 rounded-lg p-5">
            <h3 className="text-sm font-semibold text-zinc-100 uppercase tracking-wider mb-4">
              Upcoming Events
            </h3>
            {upcomingEvents.length === 0 ? (
              <p className="text-sm text-zinc-500">No upcoming events.</p>
            ) : (
              <div className="space-y-3">
                {upcomingEvents.map((ev) => (
                  <div
                    key={ev.id}
                    className="flex gap-3 rounded-lg p-2.5 hover:bg-zinc-800/50 transition-colors"
                  >
                    <div className={`mt-0.5 h-2.5 w-2.5 shrink-0 rounded-full ${EVENT_COLORS[ev.type]}`} />
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-zinc-200 truncate">{ev.title}</p>
                      <p className="text-xs text-zinc-500">
                        {new Date(ev.date).toLocaleDateString('en-US', {
                          weekday: 'short',
                          month: 'short',
                          day: 'numeric',
                        })}
                        {ev.time ? ` at ${ev.time}` : ''}
                      </p>
                      {ev.description && (
                        <p className="mt-0.5 text-xs text-zinc-500 truncate">{ev.description}</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

export default CalendarPage;
