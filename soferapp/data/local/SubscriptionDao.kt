package ro.priscom.sofer.ui.data.local

import androidx.room.*

@Dao
interface SubscriptionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sub: SubscriptionEntity)

    @Query("SELECT COUNT(*) FROM subscriptions_local")
    suspend fun countAll(): Int
}
