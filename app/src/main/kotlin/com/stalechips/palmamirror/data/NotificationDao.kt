package com.stalechips.palmamirror.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for [NotificationEntity] persistence.
 * Provides CRUD operations and reactive queries via Flow.
 */
@Dao
interface NotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(entity: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY receivedAt DESC")
    fun getAll(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE uid = :uid")
    fun getByUid(uid: Int): NotificationEntity?

    @Query("DELETE FROM notifications WHERE uid = :uid")
    fun deleteByUid(uid: Int)

    @Query("DELETE FROM notifications")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun getUnreadCount(): Int

    @Query("UPDATE notifications SET isRead = 1 WHERE uid = :uid")
    fun markRead(uid: Int)

    @Query("UPDATE notifications SET isActioned = 1 WHERE uid = :uid")
    fun markActioned(uid: Int)

    @Query("SELECT * FROM notifications ORDER BY receivedAt DESC")
    fun getAllFlow(): Flow<List<NotificationEntity>>

    @Query("DELETE FROM notifications WHERE receivedAt < :timestamp")
    fun pruneOlderThan(timestamp: Long)
}
