import React, { useEffect, useMemo, useState } from 'react';
import axios from 'axios';
import DiscountAppliesTab from './DiscountAppliesTab';

const defaultDiscount = {
  code: '',
  label: '',
  value_off: '',
  type: 'percent',
  description_required: false,
  description_label: '',
  date_limited: false,
  valid_from: '',
  valid_to: '',
};

const AdminDiscountType = () => {
  const [discounts, setDiscounts] = useState([]);
  const [newDiscount, setNewDiscount] = useState({ ...defaultDiscount });
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingDiscount, setEditingDiscount] = useState(null);
  const [showApplyModal, setShowApplyModal] = useState(false);
  const [applyDiscountId, setApplyDiscountId] = useState(null);

  const [pricingCategories, setPricingCategories] = useState([]);
  const [categorySchedules, setCategorySchedules] = useState([]);
  const [selectedCategoryId, setSelectedCategoryId] = useState('');
  const [categoryChecked, setCategoryChecked] = useState(new Set());
  const [categorySortConfig, setCategorySortConfig] = useState({ key: 'route_name', direction: 'asc' });
  const [categoryRouteFilter, setCategoryRouteFilter] = useState('');

  // sorting state
  const [sortConfig, setSortConfig] = useState({ key: 'id', direction: 'asc' });

  const fetchDiscounts = async () => {
    const res = await axios.get('/api/discount-types');
    setDiscounts(res.data);
  };

  useEffect(() => {
    fetchDiscounts();
  }, []);

  useEffect(() => {
    axios
      .get('/api/pricing-categories')
      .then((r) => setPricingCategories(Array.isArray(r.data) ? r.data : []))
      .catch(() => setPricingCategories([]));

    axios
      .get('/api/discount-types/schedules/all')
      .then((r) => setCategorySchedules(Array.isArray(r.data) ? r.data : []))
      .catch(() => setCategorySchedules([]));
  }, []);

  useEffect(() => {
    if (!selectedCategoryId) {
      setCategoryChecked(new Set());
      return;
    }
    axios
      .get(`/api/pricing-categories/${selectedCategoryId}/schedules`)
      .then((r) => setCategoryChecked(new Set(r.data)))
      .catch(() => setCategoryChecked(new Set()));
  }, [selectedCategoryId]);

  const handleSave = async () => {
    if (!newDiscount.code || !newDiscount.label || !newDiscount.value_off) return;
    if (newDiscount.description_required && !newDiscount.description_label.trim()) {
      alert('Te rugăm să completezi textul pentru descriere.');
      return;
    }
    if (newDiscount.date_limited && (!newDiscount.valid_from || !newDiscount.valid_to)) {
      alert('Completează atât data de început cât și cea de sfârșit pentru perioada de valabilitate.');
      return;
    }
    if (editingDiscount) {
      await axios.put(
        `/api/discount-types/${editingDiscount.id}`,
        newDiscount
      );
    } else {
      await axios.post('/api/discount-types', newDiscount);
    }
    setShowAddModal(false);
    setEditingDiscount(null);
    setNewDiscount({ ...defaultDiscount });
    fetchDiscounts();
  };

  const handleEdit = (discount) => {
    setEditingDiscount(discount);
    setNewDiscount({
      code: discount.code,
      label: discount.label,
      value_off: discount.value_off,
      type: discount.type,
      description_required: Boolean(discount.description_required),
      description_label: discount.description_label || '',
      date_limited: Boolean(discount.date_limited),
      valid_from: discount.valid_from ? String(discount.valid_from).slice(0, 10) : '',
      valid_to: discount.valid_to ? String(discount.valid_to).slice(0, 10) : '',
    });
    setShowAddModal(true);
  };

  const handleDelete = async (id) => {
    if (!window.confirm('Sigur dorești să ștergi această reducere?')) return;
    try {
      await axios.delete(`/api/discount-types/${id}`);
      setDiscounts(discounts.filter((d) => d.id !== id));
    } catch (error) {
      const msg = error.response?.data?.message || 'Eroare la ștergere';
      console.error('Eroare la ștergere:', error);
      alert(msg);
    }
  };

  // sort handler
  const requestSort = (key) => {
    let direction = 'asc';
    if (sortConfig.key === key && sortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setSortConfig({ key, direction });
  };

  const sortedDiscounts = useMemo(() => {
    const sortable = [...discounts];
    sortable.sort((a, b) => {
      let aVal = a[sortConfig.key];
      let bVal = b[sortConfig.key];
      // for value_off: compare as number
      if (sortConfig.key === 'value_off') {
        aVal = parseFloat(aVal);
        bVal = parseFloat(bVal);
      } else {
        if (typeof aVal === 'string') aVal = aVal.toLowerCase();
        if (typeof bVal === 'string') bVal = bVal.toLowerCase();
      }
      if (aVal < bVal) return sortConfig.direction === 'asc' ? -1 : 1;
      if (aVal > bVal) return sortConfig.direction === 'asc' ? 1 : -1;
      return 0;
    });
    return sortable;
  }, [discounts, sortConfig]);

  const requestCategorySort = (key) => {
    let direction = 'asc';
    if (categorySortConfig.key === key && categorySortConfig.direction === 'asc') {
      direction = 'desc';
    }
    setCategorySortConfig({ key, direction });
  };

  const sortedCategorySchedules = useMemo(() => {
    const sortable = [...categorySchedules];
    sortable.sort((a, b) => {
      let aVal = a[categorySortConfig.key];
      let bVal = b[categorySortConfig.key];
      if (typeof aVal === 'string') aVal = aVal.toLowerCase();
      if (typeof bVal === 'string') bVal = bVal.toLowerCase();
      if (aVal < bVal) return categorySortConfig.direction === 'asc' ? -1 : 1;
      if (aVal > bVal) return categorySortConfig.direction === 'asc' ? 1 : -1;
      return 0;
    });
    return sortable;
  }, [categorySchedules, categorySortConfig]);

  const availableCategoryRoutes = useMemo(() => {
    const map = new Map();
    categorySchedules.forEach((s) => {
      if (!map.has(s.route_id)) {
        map.set(s.route_id, s.route_name);
      }
    });
    return Array.from(map.entries()).map(([id, name]) => ({ id, name }));
  }, [categorySchedules]);

  const filteredCategorySchedules = useMemo(() => {
    if (!categoryRouteFilter) return sortedCategorySchedules;
    return sortedCategorySchedules.filter((s) => String(s.route_id) === String(categoryRouteFilter));
  }, [sortedCategorySchedules, categoryRouteFilter]);

  const toggleCategorySchedule = (id) => {
    setCategoryChecked((prev) => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const savePricingCategories = () => {
    if (!selectedCategoryId) return;
    axios
      .put(`/api/pricing-categories/${selectedCategoryId}/schedules`, {
        scheduleIds: Array.from(categoryChecked),
      })
      .then(() => alert('Salvat!'))
      .catch(() => alert('Eroare la salvare'));
  };

  return (
    <div className="space-y-10">
      <div className="overflow-x-auto">
      <h2 className="text-lg font-semibold mb-4">Tipuri de reduceri</h2>
      <button
        className="mb-4 px-3 py-1 text-sm bg-blue-600 text-white rounded"
        onClick={() => {
          setEditingDiscount(null);
          setNewDiscount({ ...defaultDiscount });
          setShowAddModal(true);
        }}
      >
        + Adaugă
      </button>

      <table className="w-auto text-sm table-auto border-collapse">
        <thead>
          <tr>
            <th
              onClick={() => requestSort('code')}
              className="p-1 border text-left cursor-pointer select-none bg-gray-200"
            >
              Cod {sortConfig.key === 'code' ? (sortConfig.direction === 'asc' ? '▲' : '▼') : ''}
            </th>
            <th
              onClick={() => requestSort('label')}
              className="p-1 border text-left cursor-pointer select-none bg-gray-200"
            >
              Etichetă {sortConfig.key === 'label' ? (sortConfig.direction === 'asc' ? '▲' : '▼') : ''}
            </th>
            <th
              onClick={() => requestSort('value_off')}
              className="p-1 border text-left cursor-pointer select-none bg-gray-200"
            >
              Reducere {sortConfig.key === 'value_off' ? (sortConfig.direction === 'asc' ? '▲' : '▼') : ''}
            </th>
            <th className="p-1 border text-left bg-gray-200">Acțiuni</th>
          </tr>
        </thead>
        <tbody>
          {sortedDiscounts.map((d, idx) => (
            <tr key={d.id} className={idx % 2 === 0 ? 'bg-white' : 'bg-gray-50'}>
              <td className="p-1 border">{d.code}</td>
              <td className="p-1 border">{d.label}</td>
              <td className="p-1 border">
                {`${parseFloat(d.value_off) % 1 === 0 ? parseInt(d.value_off) : parseFloat(d.value_off)}${d.type === 'percent' ? '%' : ''}`}
              </td>
              <td className="p-1 border space-x-2">
                <button
                  className="px-2 py-1 text-xs bg-indigo-600 text-white rounded"
                  onClick={() => {
                    setApplyDiscountId(d.id);
                    setShowApplyModal(true);
                  }}
                >
                  Se aplică la
                </button>
                <button
                  className="px-2 py-1 text-xs bg-blue-500 text-white rounded"
                  onClick={() => handleEdit(d)}
                >
                  Editează
                </button>
                <button
                  className="px-2 py-1 text-xs bg-red-500 text-white rounded"
                  onClick={() => handleDelete(d.id)}
                >
                  Șterge
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {/* Modal */}
      {showAddModal && (
        <div className="fixed inset-0 bg-black bg-opacity-30 flex items-center justify-center z-50">
          <div className="bg-white p-6 rounded shadow-lg w-80">
            <h3 className="mb-4 text-lg font-semibold">
              {editingDiscount ? 'Editează Reducere' : 'Adaugă Reducere'}
            </h3>
            <input
              type="text"
              placeholder="Cod"
              className="w-full mb-2 px-2 py-1 border rounded text-sm"
              value={newDiscount.code}
              onChange={(e) => setNewDiscount({ ...newDiscount, code: e.target.value })}
            />
            <input
              type="text"
              placeholder="Etichetă"
              className="w-full mb-2 px-2 py-1 border rounded text-sm"
              value={newDiscount.label}
              onChange={(e) => setNewDiscount({ ...newDiscount, label: e.target.value })}
            />
            <input
              type="number"
              placeholder="Reducere"
              className="w-full mb-2 px-2 py-1 border rounded text-sm"
              value={newDiscount.value_off}
              onChange={(e) => setNewDiscount({ ...newDiscount, value_off: e.target.value })}
            />
            <div className="mb-2 text-sm">
              <label className="mr-4">
                <input
                  type="radio"
                  value="percent"
                  checked={newDiscount.type === 'percent'}
                  onChange={() => setNewDiscount({ ...newDiscount, type: 'percent' })}
                  className="mr-1"
                />
                Procent
              </label>
              <label>
                <input
                  type="radio"
                  value="fixed"
                  checked={newDiscount.type === 'fixed'}
                  onChange={() => setNewDiscount({ ...newDiscount, type: 'fixed' })}
                  className="mr-1"
                />
                Valoare fixă
              </label>
            </div>
            <div className="mb-3 text-sm border rounded p-2 space-y-2">
              <label className="flex items-center gap-2 text-sm font-medium">
                <input
                  type="checkbox"
                  checked={newDiscount.description_required}
                  onChange={(e) => setNewDiscount({ ...newDiscount, description_required: e.target.checked })}
                />
                Descriere obligatorie (completată de șofer la îmbarcare)
              </label>
              {newDiscount.description_required && (
                <input
                  type="text"
                  placeholder="Text implicit pentru descriere"
                  className="w-full px-2 py-1 border rounded text-sm"
                  value={newDiscount.description_label}
                  onChange={(e) => setNewDiscount({ ...newDiscount, description_label: e.target.value })}
                />
              )}
            </div>
            <div className="mb-3 text-sm border rounded p-2 space-y-2">
              <label className="flex items-center gap-2 text-sm font-medium">
                <input
                  type="checkbox"
                  checked={newDiscount.date_limited}
                  onChange={(e) => setNewDiscount({ ...newDiscount, date_limited: e.target.checked })}
                />
                Valabil doar într-o perioadă
              </label>
              {newDiscount.date_limited && (
                <div className="grid grid-cols-2 gap-2">
                  <div>
                    <div className="text-xs text-gray-600 mb-1">De la</div>
                    <input
                      type="date"
                      className="w-full px-2 py-1 border rounded text-sm"
                      value={newDiscount.valid_from}
                      onChange={(e) => setNewDiscount({ ...newDiscount, valid_from: e.target.value })}
                    />
                  </div>
                  <div>
                    <div className="text-xs text-gray-600 mb-1">Până la</div>
                    <input
                      type="date"
                      className="w-full px-2 py-1 border rounded text-sm"
                      value={newDiscount.valid_to}
                      onChange={(e) => setNewDiscount({ ...newDiscount, valid_to: e.target.value })}
                    />
                  </div>
                </div>
              )}
            </div>
            <div className="flex justify-end gap-2">
              <button
                className="px-3 py-1 bg-gray-300 rounded text-sm"
                onClick={() => {
                  setShowAddModal(false);
                  setEditingDiscount(null);
                  setNewDiscount({ ...defaultDiscount });
                }}
              >
                Anulează
              </button>
              <button
                className="px-3 py-1 bg-green-600 text-white rounded text-sm"
                onClick={handleSave}
              >
                Salvează
              </button>
            </div>
          </div>
        </div>
      )}
      </div>

      <section className="space-y-4">
        <h3 className="text-lg font-semibold">Categorii de preț afișate agenților</h3>

        <div className="flex items-center gap-3 flex-wrap">
          <label className="text-sm font-medium">Selectează categorie:</label>
          <select
            className="p-2 text-sm border rounded"
            value={selectedCategoryId}
            onChange={(e) => setSelectedCategoryId(e.target.value)}
          >
            <option value="">Alege categorie…</option>
            {pricingCategories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>

          <label className="text-sm font-medium">Filtrează traseul:</label>
          <select
            className="p-2 text-sm border rounded"
            value={categoryRouteFilter}
            onChange={(e) => setCategoryRouteFilter(e.target.value)}
          >
            <option value="">Toate traseele</option>
            {availableCategoryRoutes.map((route) => (
              <option key={route.id} value={route.id}>
                {route.name}
              </option>
            ))}
          </select>
        </div>

        <div className="overflow-x-auto">
          <table className="w-auto text-sm table-auto border-collapse">
            <thead>
              <tr>
                <th
                  onClick={() => requestCategorySort('route_name')}
                  className="p-1 border text-left cursor-pointer select-none bg-gray-200"
                >
                  Traseu {categorySortConfig.key === 'route_name' ? (categorySortConfig.direction === 'asc' ? '▲' : '▼') : ''}
                </th>
                <th
                  onClick={() => requestCategorySort('departure')}
                  className="p-1 border text-left cursor-pointer select-none bg-gray-200"
                >
                  Ora {categorySortConfig.key === 'departure' ? (categorySortConfig.direction === 'asc' ? '▲' : '▼') : ''}
                </th>
                <th
                  onClick={() => requestCategorySort('direction')}
                  className="p-1 border text-left cursor-pointer select-none bg-gray-200"
                >
                  Direcție {categorySortConfig.key === 'direction' ? (categorySortConfig.direction === 'asc' ? '▲' : '▼') : ''}
                </th>
                <th className="p-1 border text-left bg-gray-200">Disponibil</th>
              </tr>
            </thead>
            <tbody>
              {filteredCategorySchedules.map((s, idx) => (
                <tr key={s.id} className={idx % 2 === 0 ? 'bg-white' : 'bg-gray-50'}>
                  <td className="p-1 border">{s.route_name}</td>
                  <td className="p-1 border">{s.departure}</td>
                  <td className="p-1 border">{s.direction}</td>
                  <td className="p-1 border text-center">
                    <input
                      type="checkbox"
                      checked={categoryChecked.has(s.id)}
                      onChange={() => toggleCategorySchedule(s.id)}
                      disabled={!selectedCategoryId}
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="text-left mt-2">
          <button
            className="px-3 py-1 text-sm bg-green-600 text-white rounded"
            disabled={!selectedCategoryId}
            onClick={savePricingCategories}
          >
            Salvează
          </button>
        </div>
      </section>

      {showApplyModal && (
        <div className="fixed inset-0 bg-black bg-opacity-40 flex items-center justify-center z-40 p-4">
          <div className="bg-white rounded shadow-lg w-full max-w-5xl max-h-[90vh] overflow-hidden flex flex-col">
            <div className="flex items-center justify-between px-4 py-3 border-b">
              <div>
                <div className="text-base font-semibold">Configurează unde se aplică reducerea</div>
                {applyDiscountId && (
                  <div className="text-xs text-gray-600">Discount selectat ID #{applyDiscountId}</div>
                )}
              </div>
              <button
                className="px-3 py-1 text-sm rounded bg-gray-200 hover:bg-gray-300"
                onClick={() => setShowApplyModal(false)}
              >
                Închide
              </button>
            </div>
            <div className="overflow-y-auto p-4 flex-1">
              <DiscountAppliesTab
                activeDiscountId={applyDiscountId}
                onActiveChange={setApplyDiscountId}
                onClose={() => setShowApplyModal(false)}
              />
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminDiscountType;
