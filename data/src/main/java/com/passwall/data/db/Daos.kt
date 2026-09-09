package com.passwall.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NodeDao {
    @Query("SELECT * FROM nodes ORDER BY id ASC")
    fun observeAll(): Flow<List<NodeEntity>>

    @Query("SELECT * FROM nodes ORDER BY id ASC")
    suspend fun getAll(): List<NodeEntity>

    @Query("SELECT * FROM nodes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): NodeEntity?

    @Insert
    suspend fun insert(entity: NodeEntity): Long

    @Insert
    suspend fun insertAll(entities: List<NodeEntity>): List<Long>

    @Update
    suspend fun update(entity: NodeEntity)

    @Query("DELETE FROM nodes WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM nodes WHERE subscriptionId = :subscriptionId")
    suspend fun deleteBySubscription(subscriptionId: Long)

    @Query("SELECT COUNT(*) FROM nodes")
    suspend fun count(): Int

    @Query("UPDATE nodes SET latencyMs = :latencyMs WHERE id = :id")
    suspend fun updateLatency(id: Long, latencyMs: Long?)
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY id ASC")
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions ORDER BY id ASC")
    suspend fun getAll(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SubscriptionEntity?

    @Insert
    suspend fun insert(entity: SubscriptionEntity): Long

    @Update
    suspend fun update(entity: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    fun observe(): Flow<SettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE id = 1 LIMIT 1")
    suspend fun get(): SettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SettingsEntity)
}
