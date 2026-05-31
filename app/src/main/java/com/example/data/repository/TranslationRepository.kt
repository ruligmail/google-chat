package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import com.example.data.api.TranslationResult
import com.example.data.database.ConversationMessage
import com.example.data.database.GlossaryDao
import com.example.data.database.GlossaryTerm
import com.example.data.database.MessageDao
import com.squareup.moshi.JsonAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TranslationRepository(
    private val messageDao: MessageDao,
    private val glossaryDao: GlossaryDao
) {
    val allMessages: Flow<List<ConversationMessage>> = messageDao.getAllMessages()
    val allGlossaryTerms: Flow<List<GlossaryTerm>> = glossaryDao.getAllTerms()
    val allChatRooms: Flow<List<com.example.data.database.ChatRoom>> = messageDao.getAllChatRooms()

    fun getMessagesForRoom(roomId: String): Flow<List<ConversationMessage>> = messageDao.getMessagesForRoom(roomId)
    fun getStandardMessages(): Flow<List<ConversationMessage>> = messageDao.getStandardMessages()

    suspend fun getAllChatRoomsList(): List<com.example.data.database.ChatRoom> = messageDao.getAllChatRoomsList()
    suspend fun insertChatRoom(room: com.example.data.database.ChatRoom) = messageDao.insertChatRoom(room)
    suspend fun updateChatRoomParticipants(id: String, parts: String) = messageDao.updateChatRoomParticipants(id, parts)
    suspend fun deleteChatRoom(id: String) = messageDao.deleteChatRoom(id)
    suspend fun clearMessagesForRoom(roomId: String) = messageDao.clearMessagesForRoom(roomId)

    suspend fun saveMessage(message: ConversationMessage): Long = withContext(Dispatchers.IO) {
        messageDao.insertMessage(message)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        messageDao.clearHistory()
    }

    suspend fun deleteMessage(id: Int) = withContext(Dispatchers.IO) {
        messageDao.deleteMessageById(id)
    }

    suspend fun addGlossaryTerm(term: GlossaryTerm): Long = withContext(Dispatchers.IO) {
        glossaryDao.insertTerm(term)
    }

    suspend fun deleteGlossaryTerm(id: Int) = withContext(Dispatchers.IO) {
        glossaryDao.deleteTermById(id)
    }

    /**
     * Translates a sentence using Gemini with retrieval augmented definitions from the glossary database.
     */
    suspend fun translateWithRag(
        text: String,
        sender: String,
        sourceLang: String,
        targetLang: String
    ): TranslationResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Missing API Key. Please configure your GEMINI_API_KEY in the AI Studio Secrets panel.")
        }

        // --- Retrieval Step (RAG) ---
        // Fetch saved vocabulary terms matching current language settings
        val systemGlossaryText = StringBuilder()
        try {
            val terms = glossaryDao.getTermsByLanguagePair(sourceLang, targetLang)
            val matchedTerms = terms.filter { term ->
                text.contains(term.sourcePhrase, ignoreCase = true)
            }

            if (matchedTerms.isNotEmpty()) {
                systemGlossaryText.append("RAG CONTEXT (User-defined glossary references to prioritize):\n")
                matchedTerms.forEach { term ->
                    systemGlossaryText.append("- User wants translation of \"${term.sourcePhrase}\" to prioritize \"${term.translatedPhrase}\" (Explanation/Note: ${term.notes})\n")
                }
            }
        } catch (e: Exception) {
            Log.e("TranslationRepository", "Error retrieving glossary matching terms", e)
        }

        // Prepare the prompt
        val contextInstruction = if (systemGlossaryText.isNotEmpty()) {
            "CRITICAL: Prioritize the following custom glossary translations:\n$systemGlossaryText\n"
        } else {
            ""
        }

        val prompt = """
            Please translate the following text from $sourceLang to $targetLang.
            Text to translate: "$text"
            
            $contextInstruction
            Please output exactly 3 contextual alternatives (e.g. Formal, Casual/Colloquial, and Idiomatic).
            For each alternative, specify a concise explanation of when to use it in the target language's culture.
        """.trimIndent()

        val systemInstructionText = """
            You are a bilingual translation and cultural expert engine. 
            Your role is to translate conversation text and provide context-specific alternatives.
            You MUST return a JSON object that adheres exactly to this JSON schema:
            {
               "translation": "The direct, natural translation of the full text",
               "alternatives": [
                  "Formal/Polite alternative text",
                  "Colloquial/Casual alternative text",
                  "Idiomatic/Creative alternative text"
               ],
               "explanations": [
                  "Use for formal or official settings",
                  "Use for talking with close friends",
                  "An idiomatic or native expression of equivalent meaning"
               ]
            }
            Do not include any markdown format blocks or introductory notes. Return ONLY the raw JSON string.
        """.trimIndent()

        Log.d("TranslationRepository", "Prompts: \nSystem: $systemInstructionText \nUser: $prompt")

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.2f,
                responseMimeType = "application/json"
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText)))
        )

        try {
            val response = RetrofitClient.service.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )

            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("Empty response received from Gemini engine")

            Log.d("TranslationRepository", "Raw Response: $rawJson")

            // Parse response JSON
            val adapter: JsonAdapter<TranslationResult> = RetrofitClient.moshiInstance.adapter(TranslationResult::class.java)
            val result = adapter.fromJson(rawJson)

            result ?: throw Exception("Failed to deserialize translation JSON: $rawJson")
        } catch (e: Exception) {
            Log.e("TranslationRepository", "API execution failed: ", e)
            throw e
        }
    }

    /**
     * Translates a sentence for a group chat with multiple active target languages.
     * Integrates user glossary retrieval-augmented generation (RAG) based on local matches.
     */
    suspend fun translateGroupChatWithRag(
        text: String,
        senderName: String,
        sourceLang: String,
        targetLangs: List<String>
    ): com.example.data.api.GroupTranslationResult = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            throw Exception("Missing API Key. Please configure your GEMINI_API_KEY in the AI Studio Secrets panel.")
        }

        val systemGlossaryText = StringBuilder()
        try {
            val terms = glossaryDao.getAllTermsList()
            // Retrieve matched glossary terms where source phrase is contained in incoming text
            val matchedTerms = terms.filter { term ->
                term.sourceLanguage == sourceLang && text.contains(term.sourcePhrase, ignoreCase = true)
            }

            if (matchedTerms.isNotEmpty()) {
                systemGlossaryText.append("RAG CONTEXT (User-defined terminology definitions to prioritize):\n")
                matchedTerms.forEach { term ->
                    systemGlossaryText.append("- Translate \"${term.sourcePhrase}\" prioritizing the specific translation \"${term.translatedPhrase}\" (Note: ${term.notes}) if target language is \"${term.targetLanguage}\"\n")
                }
            }
        } catch (e: Exception) {
            Log.e("TranslationRepository", "Error retrieving matching group glossary references", e)
        }

        val contextInstruction = if (systemGlossaryText.isNotEmpty()) {
            "CRITICAL: Adhere to the following custom dictionary translations:\n$systemGlossaryText\n"
        } else {
            ""
        }

        val targetLanguagesList = targetLangs.ifEmpty { listOf("Spanish") }

        val prompt = """
            Please translate this conversation text from the sender:
            Sender: "$senderName"
            Original Text: "$text"
            Source Language: $sourceLang
            Target Languages: ${targetLanguagesList.joinToString(", ")}

            $contextInstruction
            Please return translations for ALL target languages. In addition, suggest exactly 3 contextual alternative phrases/sentences or vocabulary words that would be natural suggestions or polite replies in the context of this conversation.
        """.trimIndent()

        val systemInstructionText = """
            You are a sophisticated multi-way group chat translation and conversational suggestions engine.
            Your role is to translate messages for a multi-lingual group and output exactly 3 smart contextual replies or vocab words.
            You MUST return a JSON object that adheres exactly to this JSON schema:
            {
               "translations": {
                  ${targetLanguagesList.joinToString(",\n") { "\"$it\": \"translated text here\"" }}
               },
               "alternatives": [
                  "1st contextual alternative, key phrase, or suggested response in one of the active languages",
                  "2nd contextual alternative, key phrase, or suggested response",
                  "3rd contextual alternative, key phrase, or suggested response"
               ],
               "explanations": [
                  "Explanation/Situation when to use alternative 1",
                  "Explanation/Situation when to use alternative 2",
                  "Explanation/Situation when to use alternative 3"
               ]
            }
            Do not write any notes, markdown code fences, or explanations outside the JSON block. Return ONLY raw JSON.
        """.trimIndent()

        Log.d("TranslationRepository", "Group translation instructions: \n$systemInstructionText \nPrompt: $prompt")

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = prompt)))
            ),
            generationConfig = GenerationConfig(
                temperature = 0.3f,
                responseMimeType = "application/json"
            ),
            systemInstruction = Content(parts = listOf(Part(text = systemInstructionText)))
        )

        try {
            val response = RetrofitClient.service.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )

            val rawJson = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: throw Exception("No response received from the translation engine")

            Log.d("TranslationRepository", "Group Response: $rawJson")

            val adapter: JsonAdapter<com.example.data.api.GroupTranslationResult> = 
                RetrofitClient.moshiInstance.adapter(com.example.data.api.GroupTranslationResult::class.java)
            
            val result = adapter.fromJson(rawJson)
            result ?: throw Exception("Failed to deserialize group translation JSON: $rawJson")
        } catch (e: Exception) {
            Log.e("TranslationRepository", "Group translation API execution failed: ", e)
            throw e
        }
    }
}
