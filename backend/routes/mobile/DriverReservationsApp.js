// routes/mobile/DriverReservationsApp.js
const express = require('express');
const router = express.Router();

// ATENȚIE la cale: folosește ACEEAȘI importare de db ca în TicketsApp.js
const db = require('../../db');

// Pentru autentificare – ca în driverApp.js / TicketsApp.js
const { requireAuth } = require('../../middleware/auth');

const { randomUUID } = require('crypto');




// helper simplu: scrie în audit_logs, la fel ca în reservations.js
async function logEvent(reservationId, action, actorId, details = null) {
  try {
    const correlation_id = details?.correlation_id || randomUUID();
    const channel = details?.channel || 'agent'; // șoferul e tratat ca 'agent'
    const amount = details?.amount || null;
    const payment_method = details?.method || null;
    const transaction_id = details?.transaction_id || null;
    const related_id = details?.from_reservation_id || details?.related_reservation_id || null;
    const note = details?.note || null;

    const serialize = (payload) => {
      if (!payload) return null;
      if (typeof payload === 'string') return payload;
      try {
        return JSON.stringify(payload);
      } catch {
        return null;
      }
    };

    const before_json = serialize(details?.before);
    const after_json = serialize(details?.after);

    let entity = 'reservation';
    let finalAction = action && action.startsWith('reservation.') ? action : `reservation.${action}`;

    // pentru plăți (deocamdată nu-l folosim aici)
    if (action === 'pay' || action === 'payment.capture') {
      entity = 'payment';
      finalAction = 'payment.capture';
    }

    await db.query(
      `
      INSERT INTO audit_logs
        (created_at, actor_id, entity, entity_id, action, related_entity, related_id,
         correlation_id, channel, amount, payment_method, transaction_id, note, before_json, after_json)
      VALUES (NOW(), ?, ?, ?, ?, 'reservation', ?, ?, ?, ?, ?, ?, ?, ?, ?)
      `,
      [
        actorId || null,
        entity,
        reservationId || null,
        finalAction,
        related_id || null,
        correlation_id,
        channel,
        amount,
        payment_method,
        transaction_id,
        note,
        before_json,
        after_json,
      ]
    );
  } catch (e) {
    console.warn('[audit] failed', e.message);
  }
}


// Lista rezervărilor pentru un anumit trip, pentru aplicația de șofer
// GET /api/mobile/trips/:tripId/reservations
router.get('/trips/:tripId/reservations', requireAuth, async (req, res) => {
  const tripId = parseInt(req.params.tripId, 10);

  if (!Number.isInteger(tripId)) {
    return res.status(400).json({ ok: false, error: 'tripId invalid' });
  }

  try {
    const sql = `
      SELECT
        r.id,
        r.trip_id,
        r.seat_id,
        r.person_id,
        p.name AS person_name,
        p.phone AS person_phone,
        r.status,
        r.board_station_id,
        r.exit_station_id,
        r.boarded,
        r.boarded_at,
        r.reservation_time,
        r.created_by AS agent_id,
        e.name AS agent_name,

        -- preț de bază (din reservation_pricing)
        -- preț NET (final) salvat în reservation_pricing
        rp.net_price AS final_price,

        -- suma reducerilor pentru rezervare (din reservation_discounts)
        disc.discount_amount,

        -- preț de bază (reconstruit: net + reduceri)
        CASE
          WHEN rp.net_price IS NULL THEN NULL
          ELSE rp.net_price + COALESCE(disc.discount_amount, 0)
        END AS base_price,

        -- cât s-a plătit efectiv (SUM(payments.amount, status='paid'))
        pay.paid_amount,

        -- cât mai este de plătit
        CASE
          WHEN rp.net_price IS NULL THEN NULL
          ELSE GREATEST(
            rp.net_price - COALESCE(pay.paid_amount, 0),
            0
          )
        END AS due_amount,

        -- câmp numeric intern, îl convertim la boolean în JS
        CASE
          WHEN rp.net_price IS NULL THEN 0
          WHEN rp.net_price <= COALESCE(pay.paid_amount, 0)
            THEN 1
          ELSE 0
        END AS is_paid_raw,
        -- eticheta reducerii + cod promo, dacă există
        disc.discount_label,
        disc.promo_code,

        -- dacă există un rând în no_shows pentru această rezervare
        ns.has_no_show
      FROM reservations r
      LEFT JOIN people p
        ON p.id = r.person_id
      LEFT JOIN employees e
        ON e.id = r.created_by

      -- prețul NET (final) din reservation_pricing
      LEFT JOIN (
        SELECT
          reservation_id,
          MAX(price_value) AS net_price
        FROM reservation_pricing
        GROUP BY reservation_id
      ) rp
        ON rp.reservation_id = r.id

      -- reduceri + discount label + promo code
      LEFT JOIN (
        SELECT
          rd.reservation_id,
          SUM(rd.discount_amount) AS discount_amount,
          MAX(dt.label) AS discount_label,
          MAX(pc.code) AS promo_code
        FROM reservation_discounts rd
        LEFT JOIN discount_types dt
          ON dt.id = rd.discount_type_id
        LEFT JOIN promo_codes pc
          ON pc.id = rd.promo_code_id
        GROUP BY rd.reservation_id
      ) disc
        ON disc.reservation_id = r.id

      -- plăți efective
      LEFT JOIN (
        SELECT
          reservation_id,
          SUM(CASE WHEN status = 'paid' THEN amount ELSE 0 END) AS paid_amount
        FROM payments
        GROUP BY reservation_id
      ) pay
        ON pay.reservation_id = r.id

      -- no-shows (log simplu pe rezervare)
      LEFT JOIN (
        SELECT DISTINCT
          reservation_id,
          1 AS has_no_show
        FROM no_shows
      ) ns
        ON ns.reservation_id = r.id

      WHERE r.trip_id = ?
        AND r.status IN ('active', 'cancelled')
      ORDER BY r.seat_id, r.id
    `;

    const { rows } = await db.query(sql, [tripId]);

    // Normalizăm câmpurile pentru aplicația mobilă (GSON)
    const reservations = rows.map((row) => {
      // convertim numeric -> boolean pentru GSON
      const isPaid = !!row.is_paid_raw;
      const hasNoShow = !!row.has_no_show;

      // status: păstrăm 'cancelled', dar suprascriem în 'no_show' dacă există în no_shows
      const status =
        row.status === 'cancelled'
          ? 'cancelled'
          : hasNoShow
            ? 'no_show'
            : row.status;

      // scoatem câmpurile interne
      const {
        is_paid_raw,
        has_no_show,
        ...rest
      } = row;

      return {
        ...rest,
        status,
        is_paid: isPaid,
      };
    });

    return res.json({
      ok: true,
      trip_id: tripId,
      reservations,
    });
  } catch (err) {
    console.error('[mobile] get trip reservations error', err);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});


// -----------------------------------------------------------------------------------
// POST /api/mobile/reservations/:id/update-pricing
// Schimbă prețul + reducerea + (opțional) destinația unei rezervări.
//  - aplicația de șofer trimite base_price și final_price (calcul local, offline).
//  - backend-ul:
//      * actualizează reservation_pricing.price_value = base_price
//      * șterge reducerile vechi și inserează reducerea nouă (dacă e cazul)
//      * (opțional) actualizează exit_station_id
//      * calculează cât e plătit și cât mai e de plătit
//      * scrie în audit_logs before/after
// -----------------------------------------------------------------------------------
router.post('/reservations/:id/update-pricing', requireAuth, async (req, res) => {
  const reservationId = parseInt(req.params.id, 10);
  if (!Number.isInteger(reservationId)) {
    return res.status(400).json({ ok: false, error: 'invalid_reservation_id' });
  }

  const actorId = Number(req.user?.id) || null;

  // body așteptat de la Android:
  // {
  //   base_price: 100.0,
  //   final_price: 80.0,
  //   discount_type_id: 2 | null,
  //   exit_station_id: 123 | null
  // }
  const {
    base_price,
    final_price,
    discount_type_id,
    exit_station_id,
  } = req.body || {};

  const basePrice = Number(base_price);
  const finalPrice = Number(final_price);

  if (!Number.isFinite(basePrice) || !Number.isFinite(finalPrice) || basePrice < 0 || finalPrice < 0) {
    return res.status(400).json({ ok: false, error: 'invalid_price_values' });
  }

  const discountTypeId =
    discount_type_id === null || discount_type_id === undefined
      ? null
      : Number(discount_type_id);

  if (discountTypeId !== null && !Number.isInteger(discountTypeId)) {
    return res.status(400).json({ ok: false, error: 'invalid_discount_type_id' });
  }

  const newExitStationId =
    exit_station_id === null || exit_station_id === undefined
      ? null
      : Number(exit_station_id);

  try {
    // 1️⃣ Luăm din DB situația curentă (pentru validare + before_json)
    const { rows } = await db.query(
      `
      SELECT
        r.id,
        r.status,
        r.boarded,
        r.board_station_id,
        r.exit_station_id AS cur_exit_station_id,
        rp.price_value    AS cur_price_value,
        rp.price_list_id,
        rp.pricing_category_id,
        COALESCE(disc.discount_amount, 0) AS cur_discount_amount
      FROM reservations r
      LEFT JOIN reservation_pricing rp
        ON rp.reservation_id = r.id
      LEFT JOIN (
        SELECT reservation_id, SUM(discount_amount) AS discount_amount
        FROM reservation_discounts
        GROUP BY reservation_id
      ) disc
        ON disc.reservation_id = r.id
      WHERE r.id = ?
      LIMIT 1
      `,
      [reservationId]
    );

    const current = rows && rows[0];

    if (!current) {
      return res.status(404).json({ ok: false, error: 'reservation_not_found' });
    }

    if (current.boarded) {
      return res.status(400).json({ ok: false, error: 'reservation_already_boarded' });
    }

    if (current.status === 'cancelled') {
      return res.status(400).json({ ok: false, error: 'reservation_cancelled' });
    }

    if (!current.price_list_id) {
      // fără info de pricing nu știm ce să actualizăm
      return res.status(400).json({ ok: false, error: 'missing_pricing_row' });
    }

    // discount = baza - final (nu mergem sub 0)
    const discountAmountRaw = basePrice - finalPrice;
    const discountAmount =
      discountAmountRaw > 0 ? Number(discountAmountRaw.toFixed(2)) : 0;

    const snapshotPercent =
      basePrice > 0 && discountAmount > 0
        ? Number(((discountAmount / basePrice) * 100).toFixed(2))
        : 0;

    // 2️⃣ (opțional) actualizăm destinația
    let effectiveExitStationId = current.cur_exit_station_id;
    if (newExitStationId && newExitStationId !== current.cur_exit_station_id) {
      const updExit = await db.query(
        `
        UPDATE reservations
        SET exit_station_id = ?, version = version + 1
        WHERE id = ?
        `,
        [newExitStationId, reservationId]
      );
      if (updExit.rowCount === 0) {
        return res.status(404).json({ ok: false, error: 'reservation_not_found' });
      }
      effectiveExitStationId = newExitStationId;
    }

    // 3️⃣ ștergem reducerile vechi
    await db.query(
      `DELETE FROM reservation_discounts WHERE reservation_id = ?`,
      [reservationId]
    );

    // 4️⃣ adăugăm reducerea nouă (dacă există și are sens)
    if (discountTypeId !== null && discountAmount > 0) {
      await db.query(
        `
        INSERT INTO reservation_discounts
          (reservation_id, discount_type_id, discount_amount, discount_snapshot)
        VALUES (?, ?, ?, ?)
        `,
        [reservationId, discountTypeId, discountAmount, snapshotPercent]
      );
    }

    // 5️⃣ actualizăm prețul de bază în reservation_pricing
    await db.query(
      `
      UPDATE reservation_pricing
      SET price_value = ?, updated_at = NOW()
      WHERE reservation_id = ?
      `,
      [basePrice, reservationId]
    );

    // 6️⃣ cât e deja plătit?
    const payRes = await db.query(
      `
      SELECT
        COALESCE(SUM(CASE WHEN status = 'paid' THEN amount ELSE 0 END), 0) AS paid_amount
      FROM payments
      WHERE reservation_id = ?
      `,
      [reservationId]
    );
    const paidAmount = Number(payRes.rows?.[0]?.paid_amount || 0);
    const dueAmount = Math.max(0, Number(finalPrice.toFixed(2)) - paidAmount);

    // 7️⃣ audit: before/after în audit_logs
    const beforeSnapshot = {
      exit_station_id: current.cur_exit_station_id,
      base_price: current.cur_price_value != null ? Number(current.cur_price_value) : null,
      discount_amount: Number(current.cur_discount_amount || 0),
      paid_amount: paidAmount, // îl includem ca referință
    };

    const afterSnapshot = {
      exit_station_id: effectiveExitStationId,
      base_price: basePrice,
      discount_amount: discountAmount,
      final_price: Number(finalPrice.toFixed(2)),
      paid_amount: paidAmount,
      due_amount: dueAmount,
      discount_type_id: discountTypeId,
    };

    await logEvent(reservationId, 'update', actorId, {
      before: beforeSnapshot,
      after: afterSnapshot,
      note: 'update pricing from driver_app',
      channel: 'agent',
    });

    // 8️⃣ răspuns pentru aplicația de șofer
    return res.json({
      ok: true,
      reservation_id: reservationId,
      base_price: basePrice,
      final_price: Number(finalPrice.toFixed(2)),
      discount_amount: discountAmount,
      paid_amount: paidAmount,
      due_amount: dueAmount,
      exit_station_id: effectiveExitStationId,
    });
  } catch (err) {
    console.error('[mobile] error updating reservation pricing', err);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});




// -----------------------------------------------------------------------------------
// POST /api/mobile/reservations/:id/board
// Marchează rezervarea ca îmbarcată (boarded = 1, boarded_at = NOW())
// Este permis doar dacă există cel puțin o plată cu status='paid' pentru rezervare
// (UI deja verifică că rezervarea este complet achitată).
// -----------------------------------------------------------------------------------
router.post('/reservations/:id/board', requireAuth, async (req, res) => {
  const reservationId = parseInt(req.params.id, 10);

  if (!Number.isInteger(reservationId)) {
    return res.status(400).json({ ok: false, error: 'invalid_reservation_id' });
  }

  try {
    // 1️⃣ Verificăm dacă există o plată "paid" pentru rezervare
    const paySql = `
      SELECT COUNT(*) AS cnt
      FROM payments
      WHERE reservation_id = ?
        AND status = 'paid'
    `;
    const payRes = await db.query(paySql, [reservationId]);
    const hasPaid = payRes.rows[0]?.cnt > 0;

    if (!hasPaid) {
      return res.status(400).json({
        ok: false,
        error: 'reservation_not_paid'
      });
    }

    // 2️⃣ Marcăm rezervarea ca îmbarcată
    const updateSql = `
      UPDATE reservations
      SET boarded = 1,
          boarded_at = NOW()
      WHERE id = ?
    `;
    const upd = await db.query(updateSql, [reservationId]);

    if (upd.rowCount === 0) {
      return res.status(404).json({ ok: false, error: 'reservation_not_found' });
    }

    // Opțional: returnăm doar OK; aplicația va face refresh la rezervări
    return res.json({ ok: true });

  } catch (err) {
    console.error('[mobile] error marking boarded', err);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});


// -----------------------------------------------------------------------------------
// POST /api/mobile/reservations/:id/noshow
// Marchează rezervarea ca NO-SHOW (log în tabela no_shows).
// -----------------------------------------------------------------------------------
router.post('/reservations/:id/noshow', requireAuth, async (req, res) => {
  const reservationId = parseInt(req.params.id, 10);

  if (!Number.isInteger(reservationId)) {
    return res.status(400).json({ ok: false, error: 'invalid_reservation_id' });
  }

  try {
    // 1️⃣ Verificăm dacă rezervarea există
    const resSql = `
      SELECT
        id,
        person_id,
        trip_id,
        seat_id,
        board_station_id,
        exit_station_id
      FROM reservations
      WHERE id = ?
      LIMIT 1
    `;
    const { rows } = await db.query(resSql, [reservationId]);
    const reservation = rows && rows[0];

    if (!reservation) {
      return res.status(404).json({ ok: false, error: 'reservation_not_found' });
    }

    // 2️⃣ Verificăm dacă există deja un no_show pentru această rezervare (idempotent)
    const checkSql = `
      SELECT id
      FROM no_shows
      WHERE reservation_id = ?
      LIMIT 1
    `;
    const existing = await db.query(checkSql, [reservationId]);
    const already = existing.rows && existing.rows[0];

    if (!already) {
      // 3️⃣ Inserăm în no_shows
      const insertSql = `
        INSERT INTO no_shows (
          person_id,
          trip_id,
          seat_id,
          reservation_id,
          board_station_id,
          exit_station_id,
          added_by_employee_id
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
      `;

      const employeeId = req.user?.id || null;

      await db.query(insertSql, [
        reservation.person_id,
        reservation.trip_id,
        reservation.seat_id,
        reservation.id,
        reservation.board_station_id,
        reservation.exit_station_id,
        employeeId,
      ]);
    }

    // 4️⃣ Răspuns OK – UI va reciti rezervările / va face update local
    return res.json({ ok: true });

  } catch (err) {
    console.error('[mobile] error marking no_show', err);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});

// -----------------------------------------------------------------------------------
// POST /api/mobile/reservations/:id/cancel
// Anulează rezervarea (status = 'cancelled') pentru aplicația de șofer.
// - NU șterge rândul din reservations
// - NU atinge no_shows (dacă a fost NO-SHOW, rămâne în istoric)
// - NU permite anulare dacă rezervarea este deja îmbarcată (boarded = 1)
// -----------------------------------------------------------------------------------
router.post('/reservations/:id/cancel', requireAuth, async (req, res) => {
  const reservationId = parseInt(req.params.id, 10);

  if (!Number.isInteger(reservationId)) {
    return res.status(400).json({ ok: false, error: 'invalid_reservation_id' });
  }

  try {
    // 1️⃣ Luăm status-ul actual și boarded din DB
    const checkSql = `
      SELECT status, boarded
      FROM reservations
      WHERE id = ?
      LIMIT 1
    `;
    const checkRes = await db.query(checkSql, [reservationId]);
    const reservation = checkRes.rows && checkRes.rows[0];

    if (!reservation) {
      return res.status(404).json({ ok: false, error: 'reservation_not_found' });
    }

    // Dacă e deja îmbarcată, nu permitem anulare
    if (reservation.boarded) {
      return res.status(400).json({
        ok: false,
        error: 'reservation_already_boarded',
      });
    }

    // Dacă e deja anulată, răspundem OK (idempotent)
    if (reservation.status === 'cancelled') {
      return res.json({ ok: true });
    }

    // 2️⃣ Marcăm status = 'cancelled'
    const updateSql = `
      UPDATE reservations
      SET status = 'cancelled',
          version = version + 1
      WHERE id = ?
    `;
    const upd = await db.query(updateSql, [reservationId]);

    if (upd.rowCount === 0) {
      return res.status(404).json({ ok: false, error: 'reservation_not_found' });
    }

    // 3️⃣ Salvăm și în reservation_events pentru istoric
    const actorId = req.user?.id || null;
    const details = JSON.stringify({
      source: 'driver_app',
      reason: 'cancel_from_driver',
    });

    const eventSql = `
      INSERT INTO reservation_events (reservation_id, action, actor_id, details)
      VALUES (?, 'cancel', ?, ?)
    `;
    await db.query(eventSql, [reservationId, actorId, details]);

    return res.json({ ok: true });
  } catch (err) {
    console.error('[mobile] error cancelling reservation', err);
    return res.status(500).json({ ok: false, error: 'internal_error' });
  }
});




module.exports = router;
