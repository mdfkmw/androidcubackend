// routes/mobile/DiscountTypesApp.js
// Expune discount_types pentru aplicația de șofer (Android).

const express = require('express');
const router = express.Router();
const db = require('../../db');
const { requireAuth } = require('../../middleware/auth');

// GET /api/mobile/discount_types
// Returnează toate tipurile de reducere definite în discount_types.
// Filtrarea pe rută/cursă o facem separat, pe baza route_schedule_discounts.
router.get('/discount_types', requireAuth, async (req, res) => {
  try {
    const { rows } = await db.query(`
      SELECT
        id,
        code,
        label,
        value_off,
        type,
        description_required,
        description_label,
        date_limited,
        valid_from,
        valid_to
      FROM discount_types
      ORDER BY id
    `);

    // MariaDB trimite 0/1 numeric – convertim în boolean pentru GSON
    const normalized = rows.map((row) => ({
      id: row.id,
      code: row.code,
      label: row.label,
      value_off: Number(row.value_off),
      type: row.type, // 'percent' sau 'fixed'
      description_required: !!row.description_required,
      description_label: row.description_label,
      date_limited: !!row.date_limited,
      valid_from: row.valid_from ? row.valid_from.toISOString().slice(0, 10) : null,
      valid_to: row.valid_to ? row.valid_to.toISOString().slice(0, 10) : null
    }));

    res.json(normalized);
  } catch (err) {
    console.error('[GET /api/mobile/discount_types] error', err);
    res.status(500).json({ error: 'Eroare la încărcarea tipurilor de reducere.' });
  }
});

module.exports = router;
