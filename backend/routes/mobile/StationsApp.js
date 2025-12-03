const express = require('express');
const router = express.Router();
const db = require('../../db');

router.get('/', async (req, res) => {
  try {
        const { rows } = await db.query(`
      SELECT
        id,
        name,
        latitude,
        longitude
      FROM stations
      ORDER BY name
    `);


    res.json(rows);
  } catch (err) {
    console.error("StationsApp error", err);
    res.status(500).json({ error: "Eroare StationsApp" });
  }
});

module.exports = router;
