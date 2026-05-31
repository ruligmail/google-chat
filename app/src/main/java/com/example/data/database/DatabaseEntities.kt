package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "conversation_messages")
data class ConversationMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sender: String, // "A" or "B"
    val originalText: String,
    val translatedText: String,
    val sourceLang: String,
    val targetLang: String,
    val timestamp: Long = System.currentTimeMillis(),
    val suggestions: String = "", // Newline separated alternative translations
    val chatRoomId: String? = null // Null or "default" means standard split/face-to-face chat, others are group chat IDs!
) : Serializable

@Entity(tableName = "chat_rooms")
data class ChatRoom(
    @PrimaryKey val id: String, // UUID
    val name: String,
    val participantsString: String, // Serialized comma-separated list of Nickname:LangCode. E.g. "Alice:en,Carlos:es"
    val spaceType: String = "SPACE", // "SPACE" or "DIRECT_MESSAGE"
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "glossary_terms")
data class GlossaryTerm(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val sourcePhrase: String,
    val translatedPhrase: String,
    val notes: String = "",
    val sourceLanguage: String = "English",
    val targetLanguage: String = "Spanish",
    val timestamp: Long = System.currentTimeMillis()
) : Serializable
