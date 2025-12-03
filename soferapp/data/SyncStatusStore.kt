package ro.priscom.sofer.ui.data

import java.time.LocalDateTime

/**
 * Păstrează în memorie informațiile despre ultima sincronizare pentru a le afișa
 * la revenirea pe ecranul de sincronizare.
 */
object SyncStatusStore {
    var lastResultMessage: String? = null
        private set

    var lastSyncTimestamp: LocalDateTime? = null
        private set

    fun update(message: String, timestamp: LocalDateTime = LocalDateTime.now()) {
        lastResultMessage = message
        lastSyncTimestamp = timestamp
    }
}
