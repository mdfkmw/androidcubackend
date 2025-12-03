// routes/mobile/TripVehiclesApp.js
// Endpointuri dedicate aplicației de șofer pentru asocierea vehiculului la un trip,
// DOAR pentru cursele scurte (fără rezervări).

const express = require('express');
const router = express.Router();

const db = require('../../db');
const { requireAuth, requireRole } = require('../../middleware/auth');

// Copiem helperul parseBooleanFlag din routes/trips.js
function parseBooleanFlag(value) {
  if (value === true || value === false) return value;
  if (value === 1 || value === '1') return true;
  if (value === 0 || value === '0') return false;
  if (typeof value === 'string') {
    const normalized = value.trim().toLowerCase();
    if (['true', 'yes', 'da', 'on'].includes(normalized)) return true;
    if (['false', 'no', 'nu', 'off'].includes(normalized)) return false;
  }
  return false;
}

/**
 * POST /api/mobile/trips/:trip_id/driver-vehicle
 *
 * Scop:
 *  - folosit DOAR de aplicația de șofer (rol: driver)
 *  - DOAR pentru cursele scurte (fără rezervări):
 *      visible_for_drivers = 1
 *      visible_in_reservations = 0
 *      visible_online = 0
 *  - atașează un vehicul la trip, fără să modifice vehiculele deja existente
 *    (nu umblăm la is_primary, nu ștergem nimic).
 *
 * Body JSON:
 *  {
 *    "vehicle_id": <number>
 *  }
 */
router.post(
  '/:trip_id/driver-vehicle',
  requireAuth,
  requireRole('driver'),
  async (req, res) => {
    const tripId = Number(req.params.trip_id);
    if (!Number.isInteger(tripId) || tripId <= 0) {
      return res.status(400).json({ error: 'trip_id invalid' });
    }

    const vehicleId = req.body?.vehicle_id ? Number(req.body.vehicle_id) : null;
    if (!Number.isInteger(vehicleId) || vehicleId <= 0) {
      return res.status(400).json({ error: 'vehicle_id invalid' });
    }

    try {
      // 1️⃣ Verificăm că trip-ul există și că este cursă scurtă (fără rezervări)
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
        return res.status(404).json({ error: 'Cursa (trip) nu există.' });
      }

      const tripInfo = tripRows[0];
      const visibleInReservations = parseBooleanFlag(tripInfo.visible_in_reservations);
      const visibleOnline        = parseBooleanFlag(tripInfo.visible_online);
      const visibleForDrivers    = parseBooleanFlag(tripInfo.visible_for_drivers);

      // Acceptăm endpointul doar pentru:
      //  - visible_for_drivers = 1
      //  - fără rezervări: visible_in_reservations = 0, visible_online = 0
      if (!visibleForDrivers || visibleInReservations || visibleOnline) {
        return res.status(400).json({
          error:
            'Vehiculul poate fi atașat din aplicația de șofer doar pentru curse scurte (fără rezervări).'
        });
      }

      // 2️⃣ Verificăm că vehiculul există
      const { rows: vehRows } = await db.query(
        'SELECT id FROM vehicles WHERE id = ? LIMIT 1',
        [vehicleId]
      );
      if (!vehRows.length) {
        return res.status(400).json({ error: 'Vehicul inexistent.' });
      }

      // 3️⃣ Verificăm dacă există deja combinația (trip_id, vehicle_id)
      const { rows: existingRows } = await db.query(
        'SELECT id, is_primary FROM trip_vehicles WHERE trip_id = ? AND vehicle_id = ? LIMIT 1',
        [tripId, vehicleId]
      );

      if (existingRows.length) {
        // Există deja acest vehicul pe trip → nu mai inserăm din nou
        return res.json({
          success: true,
          trip_id: tripId,
          trip_vehicle_id: existingRows[0].id,
          vehicle_id: vehicleId,
          is_primary: !!existingRows[0].is_primary,
          created: false
        });
      }

      // 4️⃣ Inserăm vehiculul pentru acest trip.
      // Nu schimbăm is_primary pentru alte vehicule – nu atingem logica de dubluri a frontendului.
      const insRes = await db.query(
        `INSERT INTO trip_vehicles (trip_id, vehicle_id, is_primary)
         VALUES (?, ?, 0)`,
        [tripId, vehicleId]
      );

      return res.json({
        success: true,
        trip_id: tripId,
        trip_vehicle_id: insRes.insertId || null,
        vehicle_id: vehicleId,
        is_primary: false,
        created: true
      });
    } catch (err) {
      console.error('[POST /api/mobile/trips/:trip_id/driver-vehicle] error:', err);
      return res.status(500).json({ error: 'Eroare internă la atașarea vehiculului.' });
    }
  }
);

module.exports = router;
