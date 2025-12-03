const express = require('express');
const router = express.Router();
const db = require('../../db');

router.get('/', async (req, res) => {
  try {
    const { rows } = await db.query(`
      SELECT id, price_list_id, from_station_id, to_station_id, price, currency
      FROM price_list_items
      ORDER BY id
    `);

    res.json(rows);
  } catch (err) {
    console.error("PriceListItemsApp error", err);
    res.status(500).json({ error: "Eroare PriceListItemsApp" });
  }
});

module.exports = router;
