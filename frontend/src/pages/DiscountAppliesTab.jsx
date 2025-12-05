import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';

export default function DiscountAppliesTab({ activeDiscountId: controlledDiscountId = null, onActiveChange, onClose }) {
  const [discounts, setDiscounts] = useState([]);
  const [schedules, setSchedules] = useState([]);
  const [internalActiveDiscountId, setInternalActiveDiscountId] = useState(null);
  const [discountAssignments, setDiscountAssignments] = useState(new Map());
  const [loadingDiscount, setLoadingDiscount] = useState(false);
  const [savingDiscount, setSavingDiscount] = useState(false);
  const [routeFilter, setRouteFilter] = useState('');

  const setActiveDiscountId = onActiveChange ?? setInternalActiveDiscountId;
  const activeDiscountId = controlledDiscountId ?? internalActiveDiscountId;

  // sorting state
  const [sortConfig, setSortConfig] = useState({ key: 'route_name', direction: 'asc' });

  useEffect(() => {
    axios.get('/api/discount-types').then((r) => setDiscounts(r.data));
    axios.get('/api/discount-types/schedules/all').then(r => setSchedules(r.data));
  }, []);

  useEffect(() => {
    if (controlledDiscountId != null) {
      setInternalActiveDiscountId(controlledDiscountId);
    }
  }, [controlledDiscountId]);

  useEffect(() => {
    if (discounts.length === 0) return;
    setActiveDiscountId((prev) => {
      if (prev != null) return prev;
      const first = discounts[0]?.id;
      return typeof first === 'number' ? first : null;
    });
  }, [discounts]);

  useEffect(() => {
    if (!activeDiscountId) return;

    const current = discountAssignments.get(activeDiscountId);
    if (current?.loaded) return;

    let cancelled = false;
    setLoadingDiscount(true);
    axios
      .get(`/api/discount-types/${activeDiscountId}/schedules`)
      .then((r) => {
        if (cancelled) return;
        const raw = Array.isArray(r.data) ? r.data : [];
        const agents = new Set();
        const online = new Set();
        const driver = new Set();
        raw.forEach((item) => {
          if (item == null) return;
          if (typeof item === 'number') {
            const id = Number(item);
            if (Number.isFinite(id)) {
              agents.add(id);
            }
            return;
          }
          const schedId = Number(item.route_schedule_id ?? item.id ?? item);
          if (!Number.isFinite(schedId)) return;
          if (item.visible_agents ?? item.agents ?? item.apply ?? false) {
            agents.add(schedId);
          }
          if (item.visible_online ?? item.online ?? false) {
            online.add(schedId);
          }
          if (item.visible_driver ?? item.driver ?? false) {
            driver.add(schedId);
          }
        });

        setDiscountAssignments((prev) => {
          const next = new Map(prev);
          next.set(activeDiscountId, {
            agents,
            online,
            driver,
            loaded: true,
            dirty: false,
          });
          return next;
        });
      })
      .catch(() => {
        if (cancelled) return;
        setDiscountAssignments((prev) => {
          const next = new Map(prev);
          next.set(activeDiscountId, {
            agents: new Set(),
            online: new Set(),
            driver: new Set(),
            loaded: true,
            dirty: false,
          });
          return next;
        });
      })
      .finally(() => {
        if (!cancelled) {
          setLoadingDiscount(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [activeDiscountId, discountAssignments]);

  const activeEntry = activeDiscountId ? discountAssignments.get(activeDiscountId) : null;
  const activeAgents = activeEntry?.agents ?? new Set();
  const activeOnline = activeEntry?.online ?? new Set();
  const activeDriver = activeEntry?.driver ?? new Set();
  const isDirty = !!activeEntry?.dirty;

  function toggleAgents(id) {
    if (!activeDiscountId) return;
    setDiscountAssignments((prev) => {
      const next = new Map(prev);
      const previous = next.get(activeDiscountId) || {
        agents: new Set(),
        online: new Set(),
        driver: new Set(),
        loaded: true,
        dirty: false,
      };
      const agents = new Set(previous.agents);
      agents.has(id) ? agents.delete(id) : agents.add(id);
      next.set(activeDiscountId, {
        ...previous,
        agents,
        dirty: true,
        loaded: true,
      });
      return next;
    });
  }

  function toggleOnline(id) {
    if (!activeDiscountId) return;
    setDiscountAssignments((prev) => {
      const next = new Map(prev);
      const previous = next.get(activeDiscountId) || {
        agents: new Set(),
        online: new Set(),
        driver: new Set(),
        loaded: true,
        dirty: false,
      };
      const online = new Set(previous.online);
      online.has(id) ? online.delete(id) : online.add(id);
      next.set(activeDiscountId, {
        ...previous,
        online,
        dirty: true,
        loaded: true,
      });
      return next;
    });
  }

  function toggleDriver(id) {
    if (!activeDiscountId) return;
    setDiscountAssignments((prev) => {
      const next = new Map(prev);
      const previous = next.get(activeDiscountId) || {
        agents: new Set(),
        online: new Set(),
        driver: new Set(),
        loaded: true,
        dirty: false,
      };
      const driver = new Set(previous.driver);
      driver.has(id) ? driver.delete(id) : driver.add(id);
      next.set(activeDiscountId, {
        ...previous,
        driver,
        dirty: true,
        loaded: true,
      });
      return next;
    });
  }

  async function save() {
    if (!activeDiscountId) return;
    const entry = discountAssignments.get(activeDiscountId) || {
      agents: new Set(),
      online: new Set(),
      driver: new Set(),
    };

    const union = new Set([...(entry.agents ?? []), ...(entry.online ?? []), ...(entry.driver ?? [])]);
    const payload = Array.from(union).map((id) => ({
      scheduleId: id,
      visibleAgents: entry.agents?.has(id) ?? false,
      visibleOnline: entry.online?.has(id) ?? false,
      visibleDriver: entry.driver?.has(id) ?? false,
    }));

    setSavingDiscount(true);
    try {
      await axios.put(`/api/discount-types/${activeDiscountId}/schedules`, { schedules: payload });
      setDiscountAssignments((prev) => {
        const next = new Map(prev);
        const current = next.get(activeDiscountId);
        if (current) {
          next.set(activeDiscountId, {
            ...current,
            dirty: false,
          });
        }
        return next;
      });
      alert('Salvat!');
    } catch (err) {
      console.error('DiscountAppliesTab: nu pot salva reducerile', err);
      alert('Eroare la salvare');
    } finally {
      setSavingDiscount(false);
    }
  }

  // sort handler
  const requestSort = key => {
    let direction = 'asc';
    if (sortConfig.key === key && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key, direction });
  };

  const sortedSchedules = useMemo(() => {
    const sortable = [...schedules];
    sortable.sort((a, b) => {
      let aVal = a[sortConfig.key];
      let bVal = b[sortConfig.key];
      if (typeof aVal === 'string') aVal = aVal.toLowerCase();
      if (typeof bVal === 'string') bVal = bVal.toLowerCase();
      if (aVal < bVal) return sortConfig.direction === 'asc' ? -1 : 1;
      if (aVal > bVal) return sortConfig.direction === 'asc' ? 1 : -1;
      return 0;
    });
    return sortable;
  }, [schedules, sortConfig]);

  const availableRoutes = useMemo(() => {
    const map = new Map();
    schedules.forEach(s => {
      if (!map.has(s.route_id)) {
        map.set(s.route_id, s.route_name);
      }
    });
    return Array.from(map.entries()).map(([id, name]) => ({ id, name }));
  }, [schedules]);

  const filteredSchedules = useMemo(() => {
    if (!routeFilter) return sortedSchedules;
    return sortedSchedules.filter(s => String(s.route_id) === String(routeFilter));
  }, [sortedSchedules, routeFilter]);

  const activeDiscount = discounts.find((d) => d.id === activeDiscountId) || null;

  return (
    <div className="space-y-10">
      <h2 className="text-lg font-semibold mb-4">Se aplică la</h2>

      <section className="mb-10">
        <h3 className="text-base font-semibold mb-2">Reducerile</h3>
        <div className="flex flex-col gap-3 mb-4">
          <div className="flex flex-wrap gap-2">
            {discounts.map((d) => {
              const entry = discountAssignments.get(d.id);
              const dirty = entry?.dirty;
              const isActive = activeDiscountId === d.id;
              return (
                <button
                  key={d.id}
                  type="button"
                  onClick={() => setActiveDiscountId(d.id)}
                  className={[
                    'px-2 py-1 text-sm rounded border transition-colors',
                    isActive
                      ? 'bg-blue-600 border-blue-600 text-white'
                      : 'bg-gray-100 border-gray-300 text-gray-800 hover:bg-gray-200',
                  ].join(' ')}
                >
                  {d.label}
                  {dirty ? <span className="ml-2 text-[10px] font-semibold uppercase">modificat</span> : null}
                </button>
              );
            })}
          </div>
          {discounts.length > 0 && (
            <div className="text-xs text-gray-600">
              {activeDiscount
                ? `Configurezi: ${activeDiscount.label}`
                : 'Selectează o reducere pentru a configura programările disponibile.'}
            </div>
          )}
        </div>

        <div className="flex items-center gap-3 mb-3 flex-wrap">
          <label className="text-sm font-medium">Filtrează traseul:</label>
          <select
            className="p-2 text-sm border rounded"
            value={routeFilter}
            onChange={e => setRouteFilter(e.target.value)}
          >
            <option value="">Toate traseele</option>
            {availableRoutes.map(route => (
              <option key={route.id} value={route.id}>{route.name}</option>
            ))}
          </select>
          {onClose && (
            <button
              type="button"
              className="ml-auto px-3 py-1 text-xs rounded bg-gray-100 hover:bg-gray-200 border"
              onClick={onClose}
            >
              Închide
            </button>
          )}
        </div>

        <div className="overflow-x-auto">
          <table className="w-auto text-sm table-auto border-collapse">
            <thead>
              <tr>
                <th
                  onClick={() => requestSort('route_name')}
                  className="p-1 border text-left cursor-pointer select-none bg-gray-200"
                >
                  Traseu {sortConfig.key === 'route_name' ? (sortConfig.direction === 'asc' ? '▲' : '▼') : ''}
                </th>
                <th
                  onClick={() => requestSort('departure')}
                  className="p-1 border text-left cursor-pointer select-none bg-gray-200"
                >
                  Ora {sortConfig.key === 'departure' ? (sortConfig.direction === 'asc' ? '▲' : '▼') : ''}
                </th>
                <th
                  onClick={() => requestSort('direction')}
                  className="p-1 border text-left cursor-pointer select-none bg-gray-200"
                >
                  Direcție {sortConfig.key === 'direction' ? (sortConfig.direction === 'asc' ? '▲' : '▼') : ''}
                </th>
                <th className="p-1 border text-left bg-gray-200">Agenți</th>
                <th className="p-1 border text-left bg-gray-200">Online</th>
                <th className="p-1 border text-left bg-gray-200">Șofer</th>
              </tr>
            </thead>
            <tbody>
              {filteredSchedules.map((s, idx) => (
                <tr
                  key={s.id}
                  className={idx % 2 === 0 ? 'bg-white' : 'bg-gray-50'}
                >
                  <td className="p-1 border">{s.route_name}</td>
                  <td className="p-1 border">{s.departure}</td>
                  <td className="p-1 border">{s.direction}</td>
                  <td className="p-1 border text-center">
                    <input
                      type="checkbox"
                      checked={activeAgents.has(s.id)}
                      onChange={() => toggleAgents(s.id)}
                      disabled={!activeDiscountId}
                    />
                  </td>
                  <td className="p-1 border text-center">
                    <input
                      type="checkbox"
                      checked={activeOnline.has(s.id)}
                      onChange={() => toggleOnline(s.id)}
                      disabled={!activeDiscountId}
                    />
                  </td>
                  <td className="p-1 border text-center">
                    <input
                      type="checkbox"
                      checked={activeDriver.has(s.id)}
                      onChange={() => toggleDriver(s.id)}
                      disabled={!activeDiscountId}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="text-left mt-4">
          <button
            className="px-3 py-1 text-sm bg-green-600 text-white rounded disabled:opacity-60"
            disabled={!activeDiscountId || savingDiscount || loadingDiscount || !isDirty}
            onClick={save}
          >
            {savingDiscount ? 'Se salvează…' : 'Salvează'}
          </button>
          {loadingDiscount && (
            <span className="ml-3 text-xs text-gray-500">Se încarcă reducerile selectate…</span>
          )}
        </div>
      </section>

    </div>
  );
}
