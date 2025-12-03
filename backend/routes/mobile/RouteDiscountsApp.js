// routes/mobile/RouteDiscountsApp.js
// Expune route_schedule_discounts pentru aplicația de șofer.
// Aceste date se sincronizează în SQLite (tabel local route_discounts).

const express = require('express');
const router = express.Router();
const db = require('../../db');
const { requireAuth } = require('../../middleware/auth');

// GET /api/mobile/route_discounts
// Returnează TOATE rândurile din route_schedule_discounts.
// Filtrarea pe cursă + vizibil_driver se face în aplicația de șofer.
router.get('/route_discounts', requireAuth, async (req, res) => {
  try {
    const { rows } = await db.query(`
      SELECT
        rsd.discount_type_id,
        rsd.route_schedule_id,
        rsd.visible_agents,
        rsd.visible_online,
        rsd.visible_driver,
        rs.route_id,
        TIME_FORMAT(rs.departure, '%H:%i') AS departure,
        rs.direction
      FROM route_schedule_discounts rsd
      JOIN route_schedules rs
        ON rs.id = rsd.route_schedule_id
      ORDER BY rs.route_id, rs.route_id, rsd.discount_type_id
    `);

    const normalized = rows.map((row) => ({
      discount_type_id: row.discount_type_id,
      route_schedule_id: row.route_schedule_id,
      visible_agents: !!row.visible_agents,
      visible_online: !!row.visible_online,
      visible_driver: !!row.visible_driver,
      route_id: row.route_id,
      departure: row.departure, // HH:mm
      direction: row.direction // 'tur' / 'retur'
    }));

    res.json(normalized);
  } catch (err) {
    console.error('[GET /api/mobile/route_discounts] error', err);
    res.status(500).json({ error: 'Eroare la încărcarea reducerilor pe programări.' });
  }
});

module.exports = router;
