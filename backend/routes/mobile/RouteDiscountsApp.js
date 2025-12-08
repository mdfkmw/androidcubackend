// routes/mobile/RouteDiscountsApp.js
// Expune route_schedule_discounts pentru aplicația de șofer.

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
        rsd.visible_driver
      FROM route_schedule_discounts rsd
      ORDER BY rsd.route_schedule_id, rsd.discount_type_id
    `);

    const normalized = rows.map((row) => ({
      discount_type_id: Number(row.discount_type_id),
      route_schedule_id: Number(row.route_schedule_id),
      visible_driver: Number(row.visible_driver) === 1,
    }));

    res.json(normalized);
  } catch (err) {
    console.error('[GET /api/mobile/route_discounts] error', err);
    res.status(500).json({ error: 'Eroare la încărcarea reducerilor pe programări.' });
  }
});

module.exports = router;
