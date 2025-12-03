// routes/mobile/TicketsApp.js
// Endpoint batch pentru bilete emise de șofer (Android).
// Scop:
//  - Pentru fiecare bilet primit din aplicația de șofer, creăm:
//      * o rezervare în `reservations` (ca să se vadă în diagrame / rapoarte)
//      * un rând în `reservation_pricing` (dacă avem preț / listă de preț)
//      * un rând în `reservation_discounts` (dacă avem discount)
//      * un rând în `payments` cu status='paid' (încasarea efectivă)
//
// Important:
//  - Nu modifică rezervările existente (cele făcute de agenți / online)
//  - Lucrează DOAR cu useri autentificați cu rol `driver`.

const express = require('express');
const router = express.Router();

const db = require('../../db');
const { requireAuth, requireRole } = require('../../middleware/auth');

/**
 * Copiat/adaptat din routes/reservations.js
 * Verifică dacă un loc este liber pe segmentul [boardStationId -> exitStationId]
 * pentru trip-ul dat.
 */
async function validateSegmentAvailability({
  tripId,
  seatId,
  boardStationId,
  exitStationId,
}) {
  if (!tripId || !seatId || !boardStationId || !exitStationId) {
    return { ok: false, error: 'Date segment incomplete' };
  }

  const stationRes = await db.query(
    `SELECT station_id, sequence
       FROM trip_stations
      WHERE trip_id = ?
        AND station_id IN (?, ?)`,
    [tripId, boardStationId, exitStationId]
  );

  if (stationRes.rowCount < 2) {
    return { ok: false, error: 'Stațiile nu aparțin cursei selectate' };
  }

  const seqMap = new Map(
    stationRes.rows.map((row) => [Number(row.station_id), Number(row.sequence)])
  );
  const boardSeq = seqMap.get(Number(boardStationId));
  const exitSeq = seqMap.get(Number(exitStationId));

  if (boardSeq === undefined || exitSeq === undefined) {
    return { ok: false, error: 'Stațiile nu aparțin cursei selectate' };
  }

  if (boardSeq >= exitSeq) {
    return { ok: false, error: 'Segment invalid' };
  }

  let overlapSql = `
    SELECT r.id, b.sequence AS board_seq, e.sequence AS exit_seq
      FROM reservations r
      JOIN trip_stations b
        ON b.trip_id = r.trip_id AND b.station_id = r.board_station_id
      JOIN trip_stations e
        ON e.trip_id = r.trip_id AND e.station_id = r.exit_station_id
     WHERE r.trip_id = ?
       AND r.seat_id = ?
       AND r.status = 'active'
  `;
  const params = [tripId, seatId];

  const existing = await db.query(overlapSql, params);
  const conflict = existing.rows.find((row) => {
    const existingBoard = Number(row.board_seq);
    const existingExit = Number(row.exit_seq);
    return !(existingExit <= boardSeq || existingBoard >= exitSeq);
  });

  if (conflict) {
    return { ok: false, error: 'Loc ocupat pe segment' };
  }

  return { ok: true, boardSeq, exitSeq };
}

/**
 * Normalizează un număr (sau string numeric) la int sau null.
 */
function toIntOrNull(value) {
  if (value === undefined || value === null || value === '') return null;
  const n = Number(value);
  return Number.isInteger(n) && n > 0 ? n : null;
}

/**
 * Normalizează un număr cu zecimale (preț) la float sau null.
 */
function toNumberOrNull(value) {
  if (value === undefined || value === null || value === '') return null;
  const n = Number(value);
  return Number.isFinite(n) ? n : null;
}

/**
 * POST /api/mobile/tickets/batch
 *
 * Body:
 * {
 *   "tickets": [
 *     {
 *       "local_id": 123,              // id-ul local din SQLite (pentru mapare înapoi)
 *       "trip_id": 1,
 *       "trip_vehicle_id": 10,        // (nu îl folosim direct aici)
 *       "from_station_id": 4,
 *       "to_station_id": 9,
 *       "seat_id": 12,                // poate fi null pentru curse scurte / bilete fără loc
 *
 *       "price_list_id": 3,           // opțional – dacă lipsește, sărim peste reservation_pricing
 *       "pricing_category_id": 1,     // opțional – dacă lipsește, punem 1 („Normal”)
 *       "discount_type_id": 2,        // opțional – dacă există, facem reservation_discounts
 *
 *       "base_price": 100.0,          // preț listă (înainte de reduceri)
 *       "final_price": 80.0,          // preț după reduceri – ce s-a încasat efectiv
 *       "currency": "RON",
 *       "payment_method": "cash",     // "cash" / "card"
 *
 *       "created_at": "2025-11-30 10:15:00"  // opțional – pentru payments.timestamp
 *     }
 *   ]
 * }
 *
 * Răspuns:
 * {
 *   "ok": true,
 *   "results": [
 *     {
 *       "local_id": 123,
 *       "ok": true,
 *       "reservation_id": 555,
 *       "payment_id": 777,
 *       "error": null
 *     },
 *     ...
 *   ]
 * }
 */
router.post(
  '/batch',
  requireAuth,
  requireRole('driver'),
  async (req, res) => {
    const { tickets } = req.body || {};

    if (!Array.isArray(tickets) || tickets.length === 0) {
      return res.status(400).json({
        ok: false,
        error: 'Lipsesc biletele în payload (tickets[]).',
      });
    }

    const currentUserId = Number(req.user?.id) || null;
    const results = [];

    for (const t of tickets) {
      const localId = t.local_id ?? null;

      try {
        const tripId = toIntOrNull(t.trip_id);
        const seatId = toIntOrNull(t.seat_id);

        const boardStationId = toIntOrNull(
          t.from_station_id ?? t.board_station_id
        );
        const exitStationId = toIntOrNull(
          t.to_station_id ?? t.exit_station_id
        );

        const priceListId = toIntOrNull(t.price_list_id);
        const pricingCategoryId = toIntOrNull(t.pricing_category_id);
        const discountTypeId = toIntOrNull(t.discount_type_id);

        const basePrice = toNumberOrNull(t.base_price);
        const finalPriceRaw = toNumberOrNull(t.final_price);
        const currency = t.currency || 'RON';
        const paymentMethod = (t.payment_method || 'cash').toLowerCase();
        const createdAt = t.created_at || null;

        if (!tripId) {
          results.push({
            local_id: localId,
            ok: false,
            reservation_id: null,
            payment_id: null,
            error: 'trip_id lipsă sau invalid',
          });
          continue;
        }

        // Pentru biletele cu loc, avem nevoie și de segment complet.
        if (seatId && (!boardStationId || !exitStationId)) {
          results.push({
            local_id: localId,
            ok: false,
            reservation_id: null,
            payment_id: null,
            error: 'Pentru biletele cu loc este necesar segmentul (from/to station).',
          });
          continue;
        }

        // 1️⃣ dacă avem loc → verificăm suprapunerea pe segment
        if (seatId && boardStationId && exitStationId) {
          const seg = await validateSegmentAvailability({
            tripId,
            seatId,
            boardStationId,
            exitStationId,
          });

          if (!seg.ok) {
            results.push({
              local_id: localId,
              ok: false,
              reservation_id: null,
              payment_id: null,
              error: seg.error || 'Loc ocupat pe segment',
            });
            continue;
          }
        }

        // 2️⃣ inserăm rezervarea
        //    - pentru bilete fără nume/telefon: person_id = NULL
        const insertRes = await db.query(
          `
          INSERT INTO reservations
            (trip_id, seat_id, person_id, board_station_id, exit_station_id, observations, status, created_by)
          VALUES (?, ?, ?, ?, ?, ?, 'active', ?)
          `,
          [
            tripId,
            seatId || null,
            null, // person_id – pentru început nu trimitem nume/telefon din aplicația de șofer
            boardStationId || 0,
            exitStationId || 0,
            null,
            currentUserId,
          ]
        );

        const reservationId = insertRes.insertId;

        // 3️⃣ pricing – dacă avem finalPrice și priceListId, salvăm în reservation_pricing
        let netPrice = finalPriceRaw;
        if (netPrice == null && basePrice != null) {
          netPrice = basePrice;
        }

        if (netPrice != null && priceListId) {
          const bookingChannel = 'driver';
          const employeeIdForPricing = currentUserId;

          await db.query(
            `
            INSERT INTO reservation_pricing
              (reservation_id, price_value, price_list_id, pricing_category_id, booking_channel, employee_id)
            VALUES (?, ?, ?, ?, ?, ?)
            `,
            [
              reservationId,
              netPrice,
              priceListId,
              pricingCategoryId || 1, // fallback: categoria „Normal” (id=1) dacă nu primim nimic
              bookingChannel,
              employeeIdForPricing || 12,
            ]
          );

          // 4️⃣ discount – dacă avem discountTypeId și basePrice > netPrice, salvăm diferența
          if (discountTypeId && basePrice != null && basePrice > netPrice) {
            const discountAmount = +(basePrice - netPrice).toFixed(2);
            const snapshotPercent =
              basePrice > 0 ? +((discountAmount / basePrice) * 100).toFixed(2) : 0;

            await db.query(
              `
              INSERT INTO reservation_discounts
                (reservation_id, discount_type_id, promo_code_id, discount_amount, discount_snapshot)
              VALUES (?, ?, NULL, ?, ?)
              `,
              [reservationId, discountTypeId, discountAmount, snapshotPercent]
            );
          }
        }

        // 5️⃣ payment – orice bilet de șofer este CU încasare
        let paymentId = null;
        if (netPrice != null) {
          const payRes = await db.query(
            `
            INSERT INTO payments
              (reservation_id, amount, status, payment_method, transaction_id, timestamp, collected_by)
            VALUES (?, ?, 'paid', ?, NULL, ?, ?)
            `,
            [
              reservationId,
              netPrice,
              paymentMethod,
              createdAt || new Date(),
              currentUserId,
            ]
          );
          paymentId = payRes.insertId;
        }

        results.push({
          local_id: localId,
          ok: true,
          reservation_id: reservationId,
          payment_id: paymentId,
          error: null,
        });
      } catch (err) {
        console.error('[TicketsApp] eroare la procesarea biletului local_id=', localId, err);
        results.push({
          local_id: localId,
          ok: false,
          reservation_id: null,
          payment_id: null,
          error: err.message || 'Eroare internă la salvarea biletului.',
        });
      }
    }

    return res.json({ ok: true, results });
  }
);

module.exports = router;
