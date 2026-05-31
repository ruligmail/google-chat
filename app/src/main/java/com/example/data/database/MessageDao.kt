package com.example.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM conversation_messages ORDER BY timestamp ASC")
    fun getAllMessages(): Flow<List<ConversationMessage>>

    @Query("SELECT * FROM conversation_messages WHERE chatRoomId = :roomId ORDER BY timestamp ASC")
    fun getMessagesForRoom(roomId: String): Flow<List<ConversationMessage>>

    @Query("SELECT * FROM conversation_messages WHERE chatRoomId IS NULL OR chatRoomId = 'default' ORDER BY timestamp ASC")
    fun getStandardMessages(): Flow<List<ConversationMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ConversationMessage): Long

    @Query("DELETE FROM conversation_messages")
    suspend fun clearHistory()

    @Query("DELETE FROM conversation_messages WHERE id = :id")
    suspend fun deleteMessageById(id: Int)

    // Chat room specific methods
    @Query("SELECT * FROM chat_rooms ORDER BY timestamp DESC")
    fun getAllChatRooms(): Flow<List<ChatRoom>>

    @Query("SELECT * FROM chat_rooms")
    suspend fun getAllChatRoomsList(): List<ChatRoom>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatRoom(room: ChatRoom)

    @Query("UPDATE chat_rooms SET participantsString = :parts WHERE id = :id")
    suspend fun updateChatRoomParticipants(id: String, parts: String)

    @Query("DELETE FROM chat_rooms WHERE id = :id")
    suspend fun deleteChatRoom(id: String)

    @Query("DELETE FROM conversation_messages WHERE chatRoomId = :roomId")
    suspend fun clearMessagesForRoom(roomId: String)
}
