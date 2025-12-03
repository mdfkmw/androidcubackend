const express = require('express');
const router = express.Router();
const db = require('../../db');

router.get('/', async (req, res) => {
  try {
    const { rows } = await db.query(`
      SELECT id, name, plate_number, operator_id
      FROM vehicles
      
      ORDER BY name
    `);

    res.json(rows);
  } catch (err) {
    console.error("VehiclesApp error", err);
    res.status(500).json({ error: "Eroare VehiclesApp" });
  }
});

module.exports = router;
