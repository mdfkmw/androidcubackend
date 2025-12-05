import { useCallback, useEffect, useMemo, useState } from 'react';
import dayjs from 'dayjs';

function formatDate(value, fmt) {
  if (!value) return '—';
  const parsed = dayjs(value);
  if (!parsed.isValid()) return '—';
  return parsed.format(fmt);
}

export default function AdminCallLog() {
  const [entries, setEntries] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [searchTerm, setSearchTerm] = useState('');
  const [limit, setLimit] = useState(() => {
    const stored = Number(localStorage.getItem('adminCallLogLimit'));
    return Number.isFinite(stored) && stored > 0 ? stored : 100;
  });
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);

  const loadData = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const response = await fetch(`/api/incoming-calls/log?limit=${limit}`, { credentials: 'include' });
      if (!response.ok) {
        throw new Error('Nu am putut încărca call log-ul');
      }
      const payload = await response.json();
      setEntries(Array.isArray(payload?.entries) ? payload.entries : []);
      setLastUpdatedAt(new Date().toISOString());
    } catch (err) {
      console.error('[AdminCallLog] loadData failed', err);
      setError('Nu am putut încărca lista de apeluri. Încearcă din nou.');
      setEntries([]);
    } finally {
      setLoading(false);
    }
  }, [limit]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  useEffect(() => {
    localStorage.setItem('adminCallLogLimit', String(limit));
  }, [limit]);

  const emptyState = !loading && !entries.length && !error;

  const rows = useMemo(() => {
    const query = searchTerm.trim().toLowerCase();

    const filteredEntries = !query
      ? entries
      : entries.filter((entry) => {
          const phoneValue = String(entry.phone || entry.digits || '').replace(/\D/g, '');
          const nameValue = String(entry.caller_name || '').toLowerCase();
          const normalizedQueryDigits = query.replace(/\D/g, '');
          const hasDigitQuery = normalizedQueryDigits.length > 0;

          const phoneMatches = hasDigitQuery
            ? phoneValue.includes(normalizedQueryDigits)
            : phoneValue.includes(query);
          const nameMatches = nameValue.includes(query);

          return phoneMatches || nameMatches;
        });

    return filteredEntries.map((entry) => ({
      id: entry.id,
      date: formatDate(entry.received_at, 'DD.MM.YYYY'),
      time: formatDate(entry.received_at, 'HH:mm:ss'),
      phone: entry.phone || entry.digits || '—',
      name: entry.caller_name || 'Nume neasociat',
    }));
  }, [entries, searchTerm]);

  return (
    <div className="p-6 space-y-6">
      <div className="flex flex-wrap items-center gap-4 border-b border-gray-200 pb-3">
        <div className="flex items-center gap-3">
          <div className="text-3xl leading-none">📞</div>
          <div>
            <h1 className="text-2xl font-semibold text-gray-900">Call Log administrare</h1>
            <p className="text-sm text-gray-600">Vezi ultimele apeluri primite și statusul lor (fără durata apelului).</p>
          </div>
        </div>
        <div className="ml-auto flex flex-wrap gap-3 items-center text-sm">
          <div className="relative">
            <input
              type="text"
              value={searchTerm}
              onChange={(event) => setSearchTerm(event.target.value)}
              placeholder="Caută după nume sau telefon"
              className="w-64 rounded border border-gray-300 px-3 py-1.5 pr-9 text-sm shadow-sm focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-200"
            />
            <span className="pointer-events-none absolute inset-y-0 right-2 flex items-center text-gray-400">🔍</span>
          </div>
          <label className="text-gray-700 flex items-center gap-2">
            Afișează
            <select
              className="border rounded px-2 py-1 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
              value={limit}
              onChange={(event) => setLimit(Number(event.target.value) || 50)}
            >
              {[25, 50, 100, 200, 300, 500].map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
            apeluri
          </label>
          <button
            type="button"
            onClick={loadData}
            className="px-3 py-1.5 text-sm font-medium bg-blue-600 text-white rounded hover:bg-blue-700 shadow-sm"
          >
            Reîncarcă
          </button>
        </div>
      </div>

      {error && (
        <div className="border border-red-200 bg-red-50 text-red-700 px-4 py-2 rounded">
          {error}
        </div>
      )}

      {lastUpdatedAt && (
        <p className="text-xs text-gray-500">
          Ultima actualizare: {formatDate(lastUpdatedAt, 'DD.MM.YYYY HH:mm:ss')}
        </p>
      )}

      {loading && (
        <div className="text-gray-600">Se încarcă lista de apeluri...</div>
      )}

      {emptyState && (
        <div className="border border-dashed rounded p-6 text-center text-gray-500">
          Nu există apeluri în istoricul recent.
        </div>
      )}

      {!loading && !!rows.length && (
        <div className="overflow-x-auto">
          <table className="min-w-full text-sm border border-gray-200 rounded-md overflow-hidden shadow-sm">
            <thead className="bg-gray-100 text-left text-gray-700">
              <tr>
                <th className="px-3 py-2 border-b border-gray-200">Data</th>
                <th className="px-3 py-2 border-b border-gray-200">Ora (cu secunde)</th>
                <th className="px-3 py-2 border-b border-gray-200">Telefon</th>
                <th className="px-3 py-2 border-b border-gray-200">Nume asociat</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="odd:bg-white even:bg-gray-50">
                  <td className="px-3 py-1.5 align-top whitespace-nowrap text-gray-900">{row.date}</td>
                  <td className="px-3 py-1.5 align-top whitespace-nowrap font-mono text-gray-900">{row.time}</td>
                  <td className="px-3 py-1.5 align-top font-mono text-base text-gray-900">{row.phone}</td>
                  <td className="px-3 py-1.5 align-top text-gray-700">{row.name}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
