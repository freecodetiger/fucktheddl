package com.zpc.fucktheddl.commitments.room

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(
    tableName = "schedules",
    indices = [Index(value = ["ownerUserId", "startAt"])],
)
data class ScheduleEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val title: String,
    val startAt: String,
    val endAt: String,
    val timezone: String,
    val location: String,
    val notes: String,
    val tags: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "todos",
    indices = [Index(value = ["ownerUserId", "due"])],
)
data class TodoEntity(
    @PrimaryKey val id: String,
    val ownerUserId: String,
    val title: String,
    val due: String,
    val timezone: String,
    val priority: String,
    val notes: String,
    val tags: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

@Dao
interface CommitmentDao {
    @Query("SELECT * FROM schedules WHERE ownerUserId = :ownerUserId AND status = 'confirmed' ORDER BY startAt")
    fun listSchedules(ownerUserId: String): List<ScheduleEntity>

    @Query("SELECT * FROM todos WHERE ownerUserId = :ownerUserId AND status IN ('active', 'done') ORDER BY due, title")
    fun listTodos(ownerUserId: String): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSchedule(entity: ScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertTodo(entity: TodoEntity)

    @Query("SELECT * FROM todos WHERE ownerUserId = :ownerUserId AND id = :id")
    fun getTodo(ownerUserId: String, id: String): TodoEntity?

    @Query("UPDATE schedules SET status = 'cancelled', updatedAt = :updatedAt WHERE ownerUserId = :ownerUserId AND id = :id")
    fun cancelSchedule(ownerUserId: String, id: String, updatedAt: String): Int

    @Query("UPDATE todos SET status = 'cancelled', updatedAt = :updatedAt WHERE ownerUserId = :ownerUserId AND id = :id")
    fun cancelTodo(ownerUserId: String, id: String, updatedAt: String): Int
}

@Database(
    entities = [ScheduleEntity::class, TodoEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class CommitmentDatabase : RoomDatabase() {
    abstract fun commitmentDao(): CommitmentDao
}
