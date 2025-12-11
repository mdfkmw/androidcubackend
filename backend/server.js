require('dotenv').config();
// === JWT secret obligatoriu (ca în producție)
if (!process.env.JWT_SECRET) {
  console.error('FATAL: Lipseste JWT_SECRET in .env');
  process.exit(1);
}

const { validateMailerConfig } = require('./utils/mailer');

const mailerStatus = validateMailerConfig();
if (!mailerStatus.ok) {
  console.warn(
    `[public-auth] Config SMTP incompletă. Lipsesc: ${mailerStatus.missing.join(', ')}. Emailurile de bun venit nu vor fi trimise.`,
  );
} else if (!mailerStatus.wantsAuth) {
  console.warn('[public-auth] SMTP configurat fără autentificare. Asigură-te că serverul acceptă conexiuni fără login.');
}


// Importă frameworkul Express – esențial pentru crearea aplicației backend
const express = require('express');

// Importă modulul CORS – permite accesul din altă origine (frontendul tău React)
const cors = require('cors');

const cookieParser = require('cookie-parser');

// Creează instanța aplicației Express
const app = express();
// ✅ HEARTBEAT global seats (fallback dacă serverul pornește și nimeni nu a cerut seats)
global.__lastSeatActivityAt = Date.now();


// Conectează la baza de date – fișierul db.js conține configurarea MariaDB (mysql2/promise)
const pool = require('./db');

// Auth/RBAC middleware helpers
const { verifyAccessToken, requireAuth, requireRole } = require('./middleware/auth');
const { attachPublicUser } = require('./middleware/publicAuth');

// Încarcă fișierele pentru rutele individuale
const routesApi = require('./routes/routes');
const seatsRoutes = require('./routes/seats');
const reservationsRoutes = require('./routes/reservations');
const publicSiteRoutes = require('./routes/publicSite');
const publicAuthRoutes = require('./routes/publicAuth');
const tripRoutes = require('./routes/trips');
const tripVehiclesRoutes = require('./routes/tripVehicles');
const peopleRouter = require('./routes/people');
const employeesRouter = require('./routes/employees');
const operatorsRouter = require('./routes/operators');
const tripAssignmentsRouter = require('./routes/tripAssignments');
const routeTimeDiscountsRouter = require('./routes/routeTimeDiscounts');
const discountTypesRouter = require('./routes/discountTypes');
const priceListsRouter = require('./routes/priceLists');
const reportsRouter = require('./routes/reports');
const agenciesRouter = require('./routes/agencies');
const routeScheduleSettingsRouter = require('./routes/routeScheduleSettings');
const stationsRouter = require('./routes/stations');
const routeStationsRouter = require('./routes/routeStations'); // 🔄 Master data pentru app ȘOFER (route_stations)
const cashRouter = require('./routes/cash');
const fiscalSettingsRouter = require('./routes/fiscalSettings');
const onlineSettingsRouter = require('./routes/onlineSettings');
const phonesRoutes = require('./routes/phones');
const travelerDefaultsRouter = require('./routes/travelerDefaults');
const promoCodesRoutes = require('./routes/promoCodes');
const authRoutes = require('./routes/auth');
const invitationsRoutes = require('./routes/invitations');
const userPrefs = require('./routes/userPrefs');
const intentsRoutes = require('./routes/intents');
const chatRoutes = require('./routes/chat');
const incomingCallsRouter = require('./routes/incomingCalls');
const mobileRoutes = require('./routes/mobile');

// === SERVEȘTE FRONTEND-UL (Vite build) DIN EXPRESS ===
const path = require('path');


// ✅ Activează CORS pentru a permite comunicarea între frontend (localhost:5173) și backend (localhost:5000)
const ALLOWED_ORIGINS = [
  'http://localhost:5173',
  'http://127.0.0.1:5173',
  'http://localhost:3000',
  'http://127.0.0.1:3000',
  'http://diagrama.pris-com.ro',
  'http://www.diagrama.pris-com.ro',
  'https://diagrama.pris-com.ro',
  'https://www.diagrama.pris-com.ro',
  'https://pris-com.ro',
  'https://www.pris-com.ro',
  'http://pris-com.ro',
  'http://www.pris-com.ro',
];

const LAN_REGEXES = [
  /^https?:\/\/192\.168\.\d{1,3}\.\d{1,3}(?::\d+)?$/,
  /^https?:\/\/10\.\d{1,3}\.\d{1,3}\.\d{1,3}(?::\d+)?$/,
  /^https?:\/\/172\.(1[6-9]|2\d|3[0-1])\.\d{1,3}\.\d{1,3}(?::\d+)?$/,
];

function isAllowedOrigin(origin) {
  if (ALLOWED_ORIGINS.includes(origin)) return true;
  return LAN_REGEXES.some((regex) => regex.test(origin));
}
app.set('trust proxy', 1); // necesar pt cookie secure când e în spatele webserverului
app.use(cors({
  credentials: true,
  origin(origin, cb) {
    // permite și requests fără Origin (ex: curl, healthchecks)
    if (!origin) return cb(null, true);
    if (isAllowedOrigin(origin)) return cb(null, true);
    return cb(new Error('CORS not allowed'), false);
  },
  allowedHeaders: ['Content-Type', 'Authorization', 'Idempotency-Key'],
  methods: ['GET', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'],
}));

// ✅ Middleware Express pentru a interpreta automat datele JSON din body-ul requestului
app.use(express.json({ limit: '10mb' }));
app.use(express.urlencoded({ extended: true, limit: '10mb' }));

app.use(cookieParser());
// Atașează user-ul în req.user dacă există access token valid în cookie
app.use(verifyAccessToken);
// Atașează utilizatorul public (site) în req.publicUser dacă există cookie dedicat
app.use(attachPublicUser);

// 🔎 LOG GLOBAL: vezi orice request intră în backend
//app.use((req, res, next) => {
// console.log(`[REQ] ${req.method} ${req.originalUrl} q=`, req.query || {});
//  next();
//});



// ✅ Înregistrează rutele definite în fișierele externe

// ——— /api/auth/me răspunde mereu 200 (chiar dacă nu ești logat)
app.get('/api/auth/me', (req, res) => {
  res.status(200).json({ user: req.user || null });
});

app.use('/api/auth', authRoutes);
app.use('/api/public/auth', publicAuthRoutes);
app.use('/api/invitations', invitationsRoutes);
app.use('/api/seats', seatsRoutes);
app.use('/api', require('./routes/agentJobs'));


// ======================================================
// AGENT LOCAL – polling endpoint (acceptă GET și POST)
// ======================================================
async function agentPollHandler(req, res) {
  try {
    const now = Date.now();

    const lastSeat = global.__lastSeatActivityAt || 0;
    const secondsSinceSeat = Math.floor((now - lastSeat) / 1000);
    const seatActive = secondsSinceSeat <= 5;

    // ✅ interval recomandat pentru agent
    let recommendedPollIntervalMs = 5000; // default mai rar
    if (seatActive) {
      recommendedPollIntervalMs = 2000;
    }

    // 🔎 Căutăm un job "queued" în agent_jobs
    let job = null;

    const { rows } = await pool.query(
      `SELECT id, job_type, payload
         FROM agent_jobs
        WHERE status = 'queued'
        ORDER BY id ASC
        LIMIT 1`
    );

    if (rows && rows.length) {
      const row = rows[0];

      let parsedPayload = null;
      try {
        parsedPayload = row.payload ? JSON.parse(row.payload) : null;
      } catch (e) {
        console.error('[agent/poll] Nu pot parsa payload JSON pentru job', row.id, e);
      }

      // 🔒 Marcăm jobul ca "in_progress" și creștem attempt_count
      await pool.query(
        `UPDATE agent_jobs
            SET status = 'in_progress',
                attempt_count = attempt_count + 1,
                last_attempt_at = NOW()
          WHERE id = ? AND status = 'queued'`,
        [row.id]
      );

      job = {
        id: row.id,
        job_type: row.job_type,
        payload: parsedPayload,
      };
    }

    return res.json({
      ok: true,
      job,
      system: {
        seat_activity: {
          last_seen_seconds: secondsSinceSeat,
          active: seatActive,
        },
        recommended_poll_interval_ms: recommendedPollIntervalMs,
      },
    });
  } catch (err) {
    console.error('[agent/poll] Eroare:', err);
    return res.status(500).json({ error: 'Eroare internă agent poll' });
  }
}

app.get('/api/agent/poll', agentPollHandler);
app.post('/api/agent/poll', agentPollHandler);


app.get('/api/agent/poll', agentPollHandler);
app.post('/api/agent/poll', agentPollHandler);



app.use('/api/reservations', reservationsRoutes);
app.use('/api/intents', intentsRoutes);
app.use('/api/routes', routesApi);
app.use('/api/route_stations', routeStationsRouter); // ✅ folosit de aplicația de ȘOFER pentru sincronizare offline
app.use('/api/vehicles', require('./routes/vehicles'));
//app.use('/api/trips/:tripId/vehicles', tripVehiclesRoutes);
app.use('/api/trips', tripVehiclesRoutes);
app.use('/api/trips', require('./routes/trips'));



// 🔹 Endpointuri dedicate aplicației de șofer (Android)
app.use('/api/mobile', mobileRoutes);




app.use('/api/public', publicSiteRoutes);
app.use('/api/incoming-calls', incomingCallsRouter);
// ✅ Blacklist: montăm la /api (rutele interne sunt /blacklist, /blacklist/check etc.)
//    RBAC este definit per-metodă în routes/blacklist.js
app.use('/api', require('./routes/blacklist'));
app.use('/api/people', peopleRouter);
app.use('/api/employees', employeesRouter);
app.use('/api/operators', operatorsRouter);
app.use('/api/trip_assignments', tripAssignmentsRouter);
app.use('/api/routes_order', require('./routes/routesOrder'));
// ✅ Route-time-discounts: montat la /api ca să meargă /api/routes/:id/discounts?time=...
app.use('/api', routeTimeDiscountsRouter);
app.use('/api/discount-types', discountTypesRouter);
app.use('/api', routeScheduleSettingsRouter);
// ✅ Price-lists: montat la /api (ex: /api/pricing-categories). RBAC fin îl facem în router.
app.use('/api', priceListsRouter)
app.use('/api/reports', reportsRouter);
app.use('/api/agencies', agenciesRouter);
app.use('/api/stations', stationsRouter);
app.use('/api/cash', cashRouter);
app.use('/api/fiscal-settings', fiscalSettingsRouter);
app.use('/api/online-settings', onlineSettingsRouter);

app.use('/api/phones', phonesRoutes);
app.use('/api/traveler-defaults', requireAuth, travelerDefaultsRouter);
app.use('/api/promo-codes', promoCodesRoutes);
// Log global (router separat) – doar montare aici, logica este în routes/audit.js
app.use('/api', require('./routes/audit'));
app.use('/api/user', userPrefs);
app.use('/api/chat', chatRoutes);
app.use('/uploads', express.static(path.resolve(__dirname, 'uploads')));




// 1) folderul cu build-ul Vite (ajustează calea dacă ai altă structură)
const distPath = path.resolve(__dirname, './frontend');

// 2) fișiere statice (JS/CSS din /dist/assets)
app.use(express.static(distPath));

// 3) CATCH-ALL pt. SPA: orice rută care NU e /api → index.html
app.get(/^(?!\/api).*/, (req, res) => {
  res.sendFile(path.join(distPath, 'index.html'));
});

// 🔚 404 logger pentru orice rută negăsită (DOAR după SPA)
app.use((req, res) => {
  console.log(`[404] ${req.method} ${req.originalUrl}`);
  res.status(404).json({ error: 'Not found' });
});

// ✅ Pornește serverul pe portul 5000
const PORT = process.env.PORT || 5000;
app.listen(PORT, '0.0.0.0', () => {
  console.log(`✅ Server backend ascultă pe portul ${PORT}`);
});
