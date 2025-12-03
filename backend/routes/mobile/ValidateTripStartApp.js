// routes/mobile/ValidateTripStartApp.js
// Endpoint dedicat aplicației de șofer pentru verificarea începerii cursei

const express = require('express');
const router = express.Router();
const db = require('../../db');
const { requireAuth, requireRole } = require('../../middleware/auth');

// funcție preluată din trips.js
function parseBooleanFlag(value) {
  if (value === true || value === false) return value;
  if (value === 1 || value === '1') return true;
  if (value === 0 || value === '0') return false;
  if (typeof value === 'string') {
    const n = value.trim().toLowerCase();
    if (['true','yes','da','on'].includes(n)) return true;
    if (['false','no','nu','off'].includes(n)) return false;
  }
  return false;
}

// POST /api/mobile/validate-trip-start
router.post(
  '/validate-trip-start',
  requireAuth,
  requireRole('driver'),
  async (req, res) => {
    const body = req.body || {};
    const routeId = Number(body.route_id);
    const tripId  = body.trip_id != null ? Number(body.trip_id) : null;
    const vehicleId = Number(body.vehicle_id);

    if (!Number.isInteger(routeId) || routeId <= 0) {
      return res.status(400).json({
        ok: false,
        critical: false,
        error: 'route_id invalid'
      });
    }
    if (!Number.isInteger(vehicleId) || vehicleId <= 0) {
      return res.status(400).json({
        ok: false,
        critical: false,
        error: 'vehicle_id invalid'
      });
    }

    // dacă nu avem tripId → nu blocăm șoferul
    if (!tripId || !Number.isInteger(tripId) || tripId <= 0) {
      return res.json({
        ok: true,
        critical: false,
        error: null
      });
    }

    try {
      // luăm ruta + setările ei
      const { rows: tripRows } = await db.query(
        `SELECT
           t.id AS trip_id,
           r.visible_in_reservations,
           r.visible_online,
           r.visible_for_drivers
         FROM trips t
         JOIN route_schedules rs ON rs.id = t.route_schedule_id
         JOIN routes r          ON r.id  = rs.route_id
         WHERE t.id = ?
         LIMIT 1`,
        [tripId]
      );

      if (!tripRows.length) {
        return res.json({
          ok: false,
          critical: false,
          error: 'Cursa nu există.'
        });
      }

      const r = tripRows[0];
      const visibleInReservations = parseBooleanFlag(r.visible_in_reservations);
      const visibleOnline        = parseBooleanFlag(r.visible_online);
      const visibleForDrivers    = parseBooleanFlag(r.visible_for_drivers);

      const hasReservations = visibleInReservations || visibleOnline;

      // curse scurte → șoferul poate porni oricând
      if (!hasReservations && visibleForDrivers) {
        return res.json({
          ok: true,
          critical: false,
          error: null
        });
      }

      // curse cu rezervări → verificăm mașina principală
      const { rows: tv } = await db.query(
        `SELECT vehicle_id
           FROM trip_vehicles
          WHERE trip_id = ?
            AND is_primary = 1
          LIMIT 1`,
        [tripId]
      );

      if (!tv.length) {
        return res.json({
          ok: false,
          critical: true,
          error: 'Pentru această cursă există rezervări, dar nu este setată nicio mașină.'
        });
      }

      const primaryVehicleId = tv[0].vehicle_id;

      if (primaryVehicleId !== vehicleId) {
        return res.json({
          ok: false,
          critical: false,
          error: 'Mașina aleasă nu este asociată acestei curse cu rezervări.'
        });
      }

      return res.json({
        ok: true,
        critical: false,
        error: null
      });

    } catch (err) {
      console.error('[validate-trip-start] ERROR:', err);
      return res.status(500).json({
        ok: false,
        critical: false,
        error: 'Eroare internă.'
      });
    }
  }
);

module.exports = router;
