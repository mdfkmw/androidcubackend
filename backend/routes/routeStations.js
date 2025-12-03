// backend/routes/routeStations.js
// ✅ Endpoint special pentru aplicația de ȘOFER (Android).
// Scop: să expună TOATE înregistrările din tabela `route_stations`
// într-un singur răspuns, pentru sincronizare/offline în aplicația de șofer.
// Avantaj: în loc de zeci de request-uri /api/routes/:id/stations,
// facem UN SINGUR request /api/route_stations și populăm Room mult mai rapid.

const express = require('express');
const router  = express.Router();
const db      = require('../db');
const { requireAuth, requireRole } = require('../middleware/auth');

// ✅ Protejăm ruta: doar utilizatori autentificați cu rol valid
//   - admin, operator_admin, agent, driver (aplicația de șofer)
router.use(requireAuth, requireRole('admin', 'operator_admin', 'agent', 'driver'));

// ✅ GET /api/route_stations
// Returnează toate legăturile rută–stație (route_stations)
// ordonate pe route_id + sequence.
router.get('/', async (req, res) => {
  try {
    const { rows } = await db.query(
      `
      SELECT
        rs.id,
        rs.route_id,
        rs.station_id,
        rs.sequence                   AS order_index,
        rs.geofence_type,
        rs.geofence_radius_m          AS geofence_radius,
        ST_AsText(rs.geofence_polygon) AS geofence_polygon
      FROM route_stations rs
      ORDER BY rs.route_id, rs.sequence
      `
    );

    // În Android vei mapa:
    //  - id            -> RouteStationEntity.id
    //  - route_id      -> RouteStationEntity.routeId
    //  - station_id    -> RouteStationEntity.stationId
    //  - order_index   -> RouteStationEntity.orderIndex
    //  - geofence_type -> RouteStationEntity.geofenceType
    //  - geofence_radius -> RouteStationEntity.geofenceRadius
    //  - geofence_polygon (WKT) -> RouteStationEntity.geofencePolygon (string)
    res.json(rows);
  } catch (err) {
    console.error('GET /api/route_stations error:', err);
    res.status(500).json({ error: 'Eroare la citirea route_stations' });
  }
});

module.exports = router;
