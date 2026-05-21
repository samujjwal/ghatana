/**
 * Audit Page for PHR healthcare system.
 *
 * Displays immutable access history for patient data, including
 * access events, consent grants, and consent revocations.
 *
 * @doc.type page
 * @doc.purpose Audit page for immutable access history
 * @doc.layer frontend
 */
import React, { useState, useEffect } from 'react';

interface AuditEvent {
  id: string;
  tenantId: string;
  eventType: string;
  principal: string;
  resourceType: string;
  resourceId: string | null;
  timestamp: string;
  success: boolean;
  details: Record<string, string>;
}

export function AuditPage(): React.ReactElement {
  const [auditEvents, setAuditEvents] = useState<AuditEvent[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<'all' | 'access' | 'consent'>('all');

  useEffect(() => {
    // Simulate loading audit events
    const mockEvents: AuditEvent[] = [
      {
        id: '1',
        tenantId: 'tenant-1',
        eventType: 'PATIENT_READ',
        principal: 'provider-1',
        resourceType: 'Patient',
        resourceId: 'patient-1',
        timestamp: new Date(Date.now() - 3600000).toISOString(),
        success: true,
        details: { classification: 'C3' },
      },
      {
        id: '2',
        tenantId: 'tenant-1',
        eventType: 'CONSENT_GRANTED',
        principal: 'patient-1',
        resourceType: 'Consent',
        resourceId: 'consent-1',
        timestamp: new Date(Date.now() - 7200000).toISOString(),
        success: true,
        details: { grantee: 'provider-1', action: 'PATIENT_READ' },
      },
    ];
    
    setTimeout(() => {
      setAuditEvents(mockEvents);
      setLoading(false);
    }, 500);
  }, []);

  const filteredEvents = auditEvents.filter((event) => {
    if (filter === 'all') return true;
    if (filter === 'access') return event.eventType.includes('READ') || event.eventType.includes('WRITE');
    if (filter === 'consent') return event.eventType.includes('CONSENT');
    return true;
  });

  return (
    <section className="max-w-6xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold mb-6">Audit Trail</h1>
      
      <div className="mb-6 flex gap-4">
        <button
          onClick={() => setFilter('all')}
          className={`px-4 py-2 rounded ${filter === 'all' ? 'bg-blue-500 text-white' : 'bg-gray-200'}`}
        >
          All Events
        </button>
        <button
          onClick={() => setFilter('access')}
          className={`px-4 py-2 rounded ${filter === 'access' ? 'bg-blue-500 text-white' : 'bg-gray-200'}`}
        >
          Access Events
        </button>
        <button
          onClick={() => setFilter('consent')}
          className={`px-4 py-2 rounded ${filter === 'consent' ? 'bg-blue-500 text-white' : 'bg-gray-200'}`}
        >
          Consent Events
        </button>
      </div>

      {loading ? (
        <div className="text-center py-8">Loading audit events...</div>
      ) : (
        <div className="bg-white shadow rounded-lg overflow-hidden">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Timestamp
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Event Type
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Principal
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Resource
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Status
                </th>
                <th className="px-6 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wider">
                  Details
                </th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {filteredEvents.map((event) => (
                <tr key={event.id}>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {new Date(event.timestamp).toLocaleString()}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {event.eventType}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {event.principal}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-900">
                    {event.resourceType}
                    {event.resourceId && ` (${event.resourceId})`}
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm">
                    <span
                      className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                        event.success
                          ? 'bg-green-100 text-green-800'
                          : 'bg-red-100 text-red-800'
                      }`}
                    >
                      {event.success ? 'Success' : 'Failed'}
                    </span>
                  </td>
                  <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    {Object.entries(event.details).map(([key, value]) => (
                      <div key={key}>
                        {key}: {value}
                      </div>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          
          {filteredEvents.length === 0 && (
            <div className="text-center py-8 text-gray-500">
              No audit events found
            </div>
          )}
        </div>
      )}
    </section>
  );
}
