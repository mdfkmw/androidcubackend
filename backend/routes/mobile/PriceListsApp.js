const express = require('express');
const router = express.Router();
const db = require('../../db');

router.get('/', async (req, res) => {
  try {
    const { rows } = await db.query(`
SELECT id, route_id, category_id, effective_from
FROM price_lists
WHERE category_id = 1      -- doar categoria NORMAL
ORDER BY id

    `);

    res.json(rows);
  } catch (err) {
    console.error("PriceListsApp error", err);
    res.status(500).json({ error: "Eroare PriceListsApp" });
  }
});

module.exports = router;
