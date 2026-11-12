package com.example.jarvis

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ChatStore(context: Context) : SQLiteOpenHelper(context, "jarvis_memory.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE messages (id INTEGER PRIMARY KEY AUTOINCREMENT, speaker TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun add(speaker: String, body: String) {
        writableDatabase.execSQL(
            "INSERT INTO messages(speaker, body, created_at) VALUES(?, ?, ?)",
            arrayOf(speaker, body, System.currentTimeMillis())
        )
    }
    fun readAll(): String {
        val result = StringBuilder()
        readableDatabase.rawQuery("SELECT speaker, body FROM messages ORDER BY id ASC LIMIT 80", null).use { c ->
            while (c.moveToNext()) result.append(c.getString(0)).append("\n").append(c.getString(1)).append("\n\n")
        }
        return result.toString()
    }
}
