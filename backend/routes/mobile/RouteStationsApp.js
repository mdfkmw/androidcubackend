const express = require('express');
const router = express.Router();
const db = require('../../db');

// Stațiile unei rute pentru aplicația de șofer
// fără requireAuth, fără cookie
router.get('/', async (req, res) => {
  try {
    const { route_id, direction } = req.query;

    const params = [];
    let where = '';

    if (route_id) {
      where = 'WHERE rs.route_id = ?';
      params.push(route_id);
    }

    const { rows } = await db.query(
      `
      SELECT
        rs.id,
        rs.route_id,
        rs.station_id,
        rs.sequence             AS order_index,
        rs.geofence_type,
        rs.geofence_radius_m    AS geofence_radius,
        ST_AsGeoJSON(rs.geofence_polygon) AS geofence_geojson,
        s.name                  AS station_name
      FROM route_stations rs
      JOIN stations s ON s.id = rs.station_id
      ${where}
      ORDER BY rs.route_id, rs.sequence
    `,
      params
    );

    // Transformă geometria în format simplu [[lat, lng], ...] ca să poată fi stocată în Room
    const sanitized = rows.map((row) => {
      let geofence_polygon = null;

      if (row.geofence_geojson) {
        try {
          const geojson = JSON.parse(row.geofence_geojson);
          if (
            geojson.type === 'Polygon' &&
            Array.isArray(geojson.coordinates) &&
            Array.isArray(geojson.coordinates[0])
          ) {
            geofence_polygon = geojson.coordinates[0].map((pair) => {
              const [lng, lat] = pair;
              return [lat, lng];
            });
          }
        } catch (err) {
          console.warn('Nu pot parse geofence_polygon pentru route_station', row.id, err);
        }
      }

      return {
        ...row,
        geofence_polygon,
      };
    });

    // Dacă direcția este RETUR, inversăm ordinea pe fiecare rută
    // direct pe server, ca să scutim telefonul de procesare suplimentară.
    if (direction && String(direction).toLowerCase() === 'retur') {
      const grouped = new Map();

      sanitized.forEach((row) => {
        const list = grouped.get(row.route_id) || [];
        list.push(row);
        grouped.set(row.route_id, list);
      });

      const reversed = [];
      grouped.forEach((list) => {
        reversed.push(...list.reverse());
      });

      return res.json(reversed);
    }

    res.json(sanitized);
  } catch (err) {
    console.error('RouteStationsApp ERROR', err);
    res.status(500).json({ error: 'Eroare RouteStationsApp' });
  }
});

module.exports = router;
