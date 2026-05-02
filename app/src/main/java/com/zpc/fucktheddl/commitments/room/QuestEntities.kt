package com.zpc.fucktheddl.commitments.room

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(
    tableName = "quest_books",
    indices = [Index(value = ["kind", "createdAt"])],
)
data class QuestBookEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val description: String,
    val location: String,
    val targetDate: String,
    val done: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(
    tableName = "quest_nodes",
    indices = [
        Index(value = ["bookId", "parentId", "sortOrder"]),
        Index(value = ["parentId"]),
    ],
)
data class QuestNodeEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val parentId: String?,
    val title: String,
    val description: String,
    val location: String,
    val targetDate: String,
    val done: Boolean,
    val expanded: Boolean,
    val sortOrder: Int,
    val createdAt: String,
    val updatedAt: String,
)

@Dao
interface QuestDao {
    @Query("SELECT * FROM quest_books WHERE kind = :kind ORDER BY createdAt DESC")
    fun listBooks(kind: String): List<QuestBookEntity>

    @Query("SELECT * FROM quest_books WHERE id = :id")
    fun getBook(id: String): QuestBookEntity?

    @Query("SELECT * FROM quest_nodes WHERE id = :id")
    fun getNode(id: String): QuestNodeEntity?

    @Query("SELECT * FROM quest_nodes WHERE bookId = :bookId ORDER BY sortOrder, createdAt")
    fun listNodes(bookId: String): List<QuestNodeEntity>

    @Query("SELECT * FROM quest_nodes WHERE parentId = :parentId ORDER BY sortOrder, createdAt")
    fun listChildren(parentId: String): List<QuestNodeEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM quest_nodes WHERE bookId = :bookId AND parentId IS :parentId")
    fun nextSortOrder(bookId: String, parentId: String?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertBook(entity: QuestBookEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertNode(entity: QuestNodeEntity)

    @Query("DELETE FROM quest_nodes WHERE id = :id")
    fun deleteNode(id: String): Int

    @Query("DELETE FROM quest_nodes WHERE bookId = :bookId")
    fun deleteNodesForBook(bookId: String): Int

    @Query("DELETE FROM quest_books WHERE id = :id")
    fun deleteBook(id: String): Int
}
