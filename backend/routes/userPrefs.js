// backend/routes/userPrefs.js — preferințe per utilizator/agent (MariaDB)
const express = require('express');
const router = express.Router();
const db = require('../db');
const { requireAuth } = require('../middleware/auth');

// toate rutele de aici cer autentificare
router.use(requireAuth);

/* =========================================================
 *  ROUTE ORDER
 *  /api/user/route-order (GET / PUT)
 * =======================================================*/

/* GET /api/user/route-order
   -> [{ route_id, position_idx }, ...] */
router.get('/route-order', async (req, res) => {
  const userId = Number(req.user?.id);
  if (!userId) {
    return res.status(401).json({ error: 'unauthorized' });
  }

  try {
    const { rows } = await db.query(
      'SELECT route_id, position_idx FROM user_route_order WHERE user_id = ? ORDER BY position_idx ASC',
      [userId]
    );
    return res.json(rows);
  } catch (err) {
    console.error('GET /api/user/route-order error:', err);
    return res.status(500).json({ error: 'internal error' });
  }
});

/* PUT /api/user/route-order
   body: { order: [{ route_id, position_idx }, ...] } */
router.put('/route-order', async (req, res) => {
  const userId = Number(req.user?.id);
  if (!userId) {
    return res.status(401).json({ error: 'unauthorized' });
  }

  const order = Array.isArray(req.body?.order) ? req.body.order : [];
  const conn = await db.getConnection();

  try {
    await conn.beginTransaction();
    await conn.execute('DELETE FROM user_route_order WHERE user_id = ?', [userId]);

    for (const item of order) {
      const rId = Number(item.route_id);
      const pos = Number(item.position_idx);
      if (!rId || !pos) continue;
      await conn.execute(
        'INSERT INTO user_route_order (user_id, route_id, position_idx) VALUES (?, ?, ?)',
        [userId, rId, pos]
      );
    }

    await conn.commit();
    conn.release();
    return res.sendStatus(204);
  } catch (err) {
    await conn.rollback();
    conn.release();
    console.error('PUT /api/user/route-order error:', err);
    return res.status(500).json({ error: 'internal error' });
  }
});

/* =========================================================
 *  USER PREFERENCES (user_preferences.prefs_json)
 *  /api/user/preferences (GET / PUT)
 * =======================================================*/

/* GET /api/user/preferences
   -> { ...json... }  (sau {} dacă nu există) */
router.get('/preferences', async (req, res) => {
  const userId = Number(req.user?.id);
  if (!userId) {
    return res.status(401).json({ error: 'unauthorized' });
  }

  try {
    // pentru MariaDB: db.query() întoarce { rows }
    const { rows } = await db.query(
      'SELECT prefs_json FROM user_preferences WHERE user_id = ? LIMIT 1',
      [userId]
    );

    let data = rows && rows[0] ? rows[0].prefs_json : null;

    // Poate fi fie string (TEXT), fie obiect (JSON) – le acoperim pe ambele
    if (!data) {
      return res.json({});
    }

    if (typeof data === 'string') {
      try {
        data = JSON.parse(data);
      } catch (e) {
        console.error('Nu pot parsa prefs_json din DB pentru user', userId, e);
        data = {};
      }
    }

    if (!data || typeof data !== 'object') {
      data = {};
    }

    return res.json(data);
  } catch (err) {
    console.error('GET /api/user/preferences error:', err);
    return res.status(500).json({ error: 'internal error' });
  }
});

/* PUT /api/user/preferences
   body: { ... orice … }  => stocat ca JSON complet */
router.put('/preferences', async (req, res) => {
  const userId = Number(req.user?.id);
  if (!userId) {
    console.warn('PUT /api/user/preferences fără user autenticat, req.user=', req.user);
    return res.status(401).json({ error: 'unauthorized' });
  }

  // log pentru debug
  console.log('PUT /api/user/preferences userId=', userId, 'raw body=', req.body);

  let json;

  // Dacă body-ul vine deja ca obiect (caz normal cu express.json())
  if (req.body && typeof req.body === 'object') {
    json = req.body;
  } else if (typeof req.body === 'string') {
    // fallback: dacă ai cumva body ca string brut
    try {
      json = JSON.parse(req.body);
    } catch (e) {
      console.error('Body JSON invalid la /api/user/preferences pentru user', userId, e);
      return res.status(400).json({ error: 'invalid JSON body' });
    }
  } else {
    json = {};
  }

  try {
    const serialized = JSON.stringify(json);

    await db.query(
      `INSERT INTO user_preferences (user_id, prefs_json)
       VALUES (?, ?)
       ON DUPLICATE KEY UPDATE prefs_json = VALUES(prefs_json)`,
      [userId, serialized]
    );

    console.log('✅ PUT /api/user/preferences OK pentru user', userId);
    return res.sendStatus(204);
  } catch (err) {
    console.error('❌ PUT /api/user/preferences DB error pentru user', userId, err);
    return res.status(500).json({ error: 'db error' });
  }
});

/* =========================================================
 *  SEATMAP PREFS (user_seatmap_prefs)
 *  /api/user/seatmap (GET / POST)
 *  (momentan frontendul tău folosește în continuare user_preferences,
 *   dar lăsăm și varianta pe tabel separat pentru viitor)
 * =======================================================*/

// helper generic pt user id (dacă pe viitor vei folosi alt mecanism)
function getCurrentUserId(req) {
  if (req.user && req.user.id) return req.user.id;
  if (req.body && req.body.user_id) return req.body.user_id;
  return null;
}

// GET /api/user/seatmap → citește preferințele de seatmap din tabel separat
router.get('/seatmap', async (req, res) => {
  try {
    const userId = getCurrentUserId(req);
    if (!userId) {
      return res.status(401).json({ error: 'User neautentificat' });
    }

    const { rows } = await db.query(
      `SELECT
         narrow_text_size,
         narrow_text_color,
         wide_text_size,
         wide_text_color,
         wide_seat_width,
         wide_seat_height
       FROM user_seatmap_prefs
       WHERE user_id = ?`,
      [userId]
    );

    if (!rows || rows.length === 0) {
      // default-uri
      return res.json({
        narrow_text_size: 11,
        narrow_text_color: '#ffffff',
        wide_text_size: 11,
        wide_text_color: '#ffffff',
        wide_seat_width: 260,
        wide_seat_height: 150,
      });
    }

    return res.json(rows[0]);
  } catch (err) {
    console.error('GET /api/user/seatmap error', err);
    return res.status(500).json({ error: 'Eroare server la citirea preferințelor seatmap' });
  }
});

// POST /api/user/seatmap → salvează / upsertează preferințele în user_seatmap_prefs
router.post('/seatmap', async (req, res) => {
  try {
    const userId = getCurrentUserId(req);
    if (!userId) {
      return res.status(401).json({ error: 'User neautentificat' });
    }

    const {
      narrow_text_size,
      narrow_text_color,
      wide_text_size,
      wide_text_color,
      wide_seat_width,
      wide_seat_height,
    } = req.body || {};

    await db.query(
      `INSERT INTO user_seatmap_prefs (
         user_id,
         narrow_text_size,
         narrow_text_color,
         wide_text_size,
         wide_text_color,
         wide_seat_width,
         wide_seat_height,
         updated_at
       )
       VALUES (?,?,?,?,?,?,?, NOW())
       ON DUPLICATE KEY UPDATE
         narrow_text_size = VALUES(narrow_text_size),
         narrow_text_color = VALUES(narrow_text_color),
         wide_text_size = VALUES(wide_text_size),
         wide_text_color = VALUES(wide_text_color),
         wide_seat_width = VALUES(wide_seat_width),
         wide_seat_height = VALUES(wide_seat_height),
         updated_at = NOW()`,
      [
        userId,
        narrow_text_size,
        narrow_text_color,
        wide_text_size,
        wide_text_color,
        wide_seat_width,
        wide_seat_height,
      ]
    );

    return res.json({ success: true });
  } catch (err) {
    console.error('POST /api/user/seatmap error', err);
    return res.status(500).json({ error: 'Eroare server la salvarea preferințelor seatmap' });
  }
});

module.exports = router;
