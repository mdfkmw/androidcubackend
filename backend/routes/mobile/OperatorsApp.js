const express = require('express');
const router = express.Router();
const db = require('../../db');

// Lista operatorilor pentru aplicația de șofer (fără autentificare)
router.get('/', async (req, res) => {
  try {
    const { rows } = await db.query(`
      SELECT id, name
      FROM operators
      ORDER BY name
    `);

    res.json(rows);

  } catch (err) {
    console.error("OperatorsApp error", err);
    res.status(500).json({ error: "Eroare OperatorsApp" });
  }
});

module.exports = router;
