const express = require('express');
const router = express.Router();
const db = require('../../db');

router.get('/', async (req, res) => {
  try {
    const { rows } = await db.query(`
      SELECT id, name, operator_id
      FROM employees

      ORDER BY name
    `);

    res.json(rows);
  } catch (err) {
    console.error("EmployeesApp error", err);
    res.status(500).json({ error: "Eroare EmployeesApp" });
  }
});

module.exports = router;
