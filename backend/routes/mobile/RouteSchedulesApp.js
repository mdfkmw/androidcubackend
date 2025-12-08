const express = require('express');
const router = express.Router();
const db = require('../../db');

/**
 * Endpoint simplu pentru aplicația de șofer:
 * returnează lista de route_schedules (programările unei rute),
 * cât mai aproape de structura din DB.
 *
 * Optional: poți filtra după operator_id, dacă vrei mai târziu.
 */
router.get('/', async (req, res) => {
  try {
    const { operator_id } = req.query;

    const params = [];
    let where = '';

    if (operator_id) {
      where = 'WHERE operator_id = ?';
      params.push(operator_id);
    }

    const sql = `
      SELECT
        id,
        route_id,
        operator_id,
        TIME_FORMAT(departure, '%H:%i') AS departure,
        direction
      FROM route_schedules
      ${where}
      ORDER BY route_id, departure
    `;

    const { rows } = await db.query(sql, params);

    res.json(rows);
  } catch (err) {
    console.error("RouteSchedulesApp error", err);
    res.status(500).json({ error: "Eroare RouteSchedulesApp" });
  }
});

module.exports = router;
