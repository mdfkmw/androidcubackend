// backend/routes/agentJobs.js
const express = require('express');
const db = require('../db');
const router = express.Router();

// helper pentru a extrage rows din diverse forme (mysql2 / mysql / pg wrapper)
function extractRows(result) {
  if (!result) return [];
  if (Array.isArray(result)) {
    if (Array.isArray(result[0])) return result[0];
    return result;
  }
  if (result.rows) return result.rows;
  return [];
}

// POST /api/agent/jobs/:id/report
router.post('/agent/jobs/:id/report', async (req, res) => {
  try {
    const jobId = Number(req.params.id);
    if (!jobId) {
      return res.status(400).json({ error: 'jobId invalid' });
    }

    const {
      success = false,
      pos_ok = false,
      fiscal_ok = false,
      error_message = null,
      result = null,
    } = req.body || {};

    // 1) luăm jobul
    const jobRes = await db.query(
      `SELECT id, reservation_id, payment_id, job_type, status
         FROM agent_jobs
        WHERE id = ?
        LIMIT 1`,
      [jobId]
    );
    const jobRows = extractRows(jobRes);
    const job = jobRows[0];

    if (!job) {
      return res.status(404).json({ error: 'Job inexistent' });
    }

    // 2) actualizăm agent_jobs
    const newJobStatus = success ? 'done' : 'error';

    await db.query(
      `UPDATE agent_jobs
          SET status = ?,
              result = ?,
              error_message = ?
        WHERE id = ?`,
      [
        newJobStatus,
        result ? JSON.stringify(result) : null,
        error_message || null,
        jobId,
      ]
    );

    // 3) dacă jobul este legat de un payment, actualizăm și payments
    if (job.payment_id) {
      let paymentStatus = null;
      let receiptStatus = 'none';

      if (success) {
        if (fiscal_ok && (pos_ok || job.job_type === 'cash_receipt_only')) {
          // totul OK: bani + bon
          paymentStatus = 'paid';
          receiptStatus = 'ok';
        } else if (pos_ok && !fiscal_ok && job.job_type !== 'cash_receipt_only') {
          // POS OK, dar bonul NU – risc dublă încasare
          paymentStatus = 'pos_ok_waiting_receipt';
          receiptStatus = 'error_needs_retry';
        } else {
          // succes=false logic sau combinație ciudată
          paymentStatus = 'failed';
          receiptStatus = 'none';
        }
      } else {
        // success = false (agent / device a raportat eșec)
        if (pos_ok && !fiscal_ok && job.job_type !== 'cash_receipt_only') {
          // card luat, bon lipsă
          paymentStatus = 'pos_ok_waiting_receipt';
          receiptStatus = 'error_needs_retry';
        } else {
          paymentStatus = 'failed';
          receiptStatus = 'none';
        }
      }

      if (paymentStatus) {
        await db.query(
          `UPDATE payments
              SET status = ?,
                  receipt_status = ?
            WHERE id = ?`,
          [paymentStatus, receiptStatus, job.payment_id]
        );
      }
    }

    return res.json({ ok: true });
  } catch (err) {
    console.error('[POST /api/agent/jobs/:id/report] eroare:', err);
    return res.status(500).json({ error: 'Eroare la procesarea raportului de job' });
  }
});

module.exports = router;
