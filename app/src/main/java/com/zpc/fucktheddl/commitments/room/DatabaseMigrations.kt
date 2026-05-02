package com.zpc.fucktheddl.commitments.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quest_books (
                id TEXT NOT NULL PRIMARY KEY,
                kind TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                location TEXT NOT NULL,
                targetDate TEXT NOT NULL,
                done INTEGER NOT NULL,
                createdAt TEXT NOT NULL,
                updatedAt TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quest_books_kind_createdAt ON quest_books(kind, createdAt)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS quest_nodes (
                id TEXT NOT NULL PRIMARY KEY,
                bookId TEXT NOT NULL,
                parentId TEXT,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                location TEXT NOT NULL,
                targetDate TEXT NOT NULL,
                done INTEGER NOT NULL,
                expanded INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                createdAt TEXT NOT NULL,
                updatedAt TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quest_nodes_bookId_parentId_sortOrder ON quest_nodes(bookId, parentId, sortOrder)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_quest_nodes_parentId ON quest_nodes(parentId)")
    }
}
