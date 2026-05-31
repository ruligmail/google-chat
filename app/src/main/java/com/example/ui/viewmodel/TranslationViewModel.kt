package com.example.ui.viewmodel

import android.app.Application
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.database.ConversationMessage
import com.example.data.database.GlossaryTerm
import com.example.data.repository.TranslationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class TranslationViewModel(
    application: Application,
    private val repository: TranslationRepository
) : AndroidViewModel(application), TextToSpeech.OnInitListener {

    // Google User Model for dynamic sign in representation
    data class GoogleUser(
        val email: String,
        val displayName: String,
        val photoUrl: String? = null
    )

    private val sharedPrefs = application.getSharedPreferences("duotrans_prefs", android.content.Context.MODE_PRIVATE)

    private val _currentUser = MutableStateFlow<GoogleUser?>(null)
    val currentUser = _currentUser.asStateFlow()

    fun signInWithGoogle(email: String, displayName: String) {
        viewModelScope.launch {
            sharedPrefs.edit().apply {
                putString("user_email", email)
                putString("user_name", displayName)
                apply()
            }
            _currentUser.value = GoogleUser(email, displayName)
            
            // Also automatically modify the default/active room to map Alice:en to user:en to make chat transition personalized!
            try {
                val rooms = repository.getAllChatRoomsList()
                for (room in rooms) {
                    if (room.participantsString.contains("Alice:en")) {
                        val updated = room.participantsString.replace("Alice:en", "$displayName:en")
                        repository.updateChatRoomParticipants(room.id, updated)
                    }
                }
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Auto-updating participant name failed", e)
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            sharedPrefs.edit().apply {
                remove("user_email")
                remove("user_name")
                apply()
            }
            _currentUser.value = null
        }
    }

    // Language list definition
    val availableLanguages = listOf(
        LanguageItem("English", "en", Locale.US),
        LanguageItem("Spanish", "es", Locale.forLanguageTag("es-ES")),
        LanguageItem("French", "fr", Locale.FRANCE),
        LanguageItem("German", "de", Locale.GERMANY),
        LanguageItem("Mandarin", "zh", Locale.SIMPLIFIED_CHINESE),
        LanguageItem("Japanese", "ja", Locale.JAPAN),
        LanguageItem("Korean", "ko", Locale.KOREA),
        LanguageItem("Indonesian", "id", Locale.forLanguageTag("id-ID")),
        LanguageItem("Italian", "it", Locale.ITALY),
        LanguageItem("Portuguese", "pt", Locale.forLanguageTag("pt-PT"))
    )

    // State representation
    val messages: StateFlow<List<ConversationMessage>> = repository.allMessages
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val glossaryTerms: StateFlow<List<GlossaryTerm>> = repository.allGlossaryTerms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _languageA = MutableStateFlow(availableLanguages[0]) // English
    val languageA = _languageA.asStateFlow()

    private val _languageB = MutableStateFlow(availableLanguages[1]) // Spanish
    val languageB = _languageB.asStateFlow()

    private val _isTranslatingA = MutableStateFlow(false)
    val isTranslatingA = _isTranslatingA.asStateFlow()

    private val _isTranslatingB = MutableStateFlow(false)
    val isTranslatingB = _isTranslatingB.asStateFlow()

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState = _errorState.asStateFlow()

    private val _lastResult = MutableStateFlow<com.example.data.api.TranslationResult?>(null)
    val lastResult = _lastResult.asStateFlow()

    private val _glossarySearchQuery = MutableStateFlow("")
    val glossarySearchQuery = _glossarySearchQuery.asStateFlow()

    // Screen Flip / Rotation toggle for Face-to-Face Split Layout
    private val _isSplitFaceToFaceMode = MutableStateFlow(true)
    val isSplitFaceToFaceMode = _isSplitFaceToFaceMode.asStateFlow()

    // Support for Group Chat Mode (with multiple participants speaking various languages)
    val chatRooms: StateFlow<List<com.example.data.database.ChatRoom>> = repository.allChatRooms
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _activeGroupId = MutableStateFlow<String?>(null)
    val activeGroupId = _activeGroupId.asStateFlow()

    data class ChatMember(
        val id: String,
        val nickname: String,
        val language: LanguageItem
    )

    private val _isGroupMode = MutableStateFlow(false)
    val isGroupMode = _isGroupMode.asStateFlow()

    private val _isTranslatingGroup = MutableStateFlow(false)
    val isTranslatingGroup = _isTranslatingGroup.asStateFlow()

    val groupMembers: StateFlow<List<ChatMember>> = combine(_activeGroupId, chatRooms) { activeId, rooms ->
        val activeRoom = rooms.firstOrNull { it.id == activeId }
        if (activeRoom != null) {
            activeRoom.participantsString.split(",").mapNotNull { part ->
                if (part.isBlank()) return@mapNotNull null
                val spl = part.split(":")
                val nick = spl.getOrNull(0) ?: return@mapNotNull null
                val code = spl.getOrNull(1) ?: "en"
                val lang = availableLanguages.firstOrNull { it.code == code } ?: availableLanguages[0]
                ChatMember(id = nick, nickname = nick, language = lang)
            }
        } else {
            emptyList()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter glossary terms based on query
    val filteredGlossary: StateFlow<List<GlossaryTerm>> = combine(
        glossaryTerms,
        _glossarySearchQuery
    ) { terms, query ->
        if (query.isBlank()) {
            terms
        } else {
            terms.filter {
                it.sourcePhrase.contains(query, ignoreCase = true) ||
                it.translatedPhrase.contains(query, ignoreCase = true) ||
                it.notes.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // TextToSpeech Initialization
    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false

    init {
        // Load custom user session on start
        val savedEmail = sharedPrefs.getString("user_email", null)
        val savedName = sharedPrefs.getString("user_name", null)
        if (savedEmail != null && savedName != null) {
            _currentUser.value = GoogleUser(savedEmail, savedName)
        }

        try {
            tts = TextToSpeech(application, this)
        } catch (e: Exception) {
            Log.e("TranslationViewModel", "Failed to initialize TextToSpeech", e)
        }
        
        // Load chat rooms, if none exist, pre-populate default space and direct message
        viewModelScope.launch {
            val existing = repository.getAllChatRoomsList()
            if (existing.isEmpty()) {
                val defaultRoom = com.example.data.database.ChatRoom(
                    id = "space_general",
                    name = "Global Marketing Space",
                    participantsString = "Alice:en,Carlos:es,Yuki:ja",
                    spaceType = "SPACE"
                )
                val defaultDm = com.example.data.database.ChatRoom(
                    id = "dm_carlos",
                    name = "Carlos (Direct Message)",
                    participantsString = "Alice:en,Carlos:es",
                    spaceType = "DIRECT_MESSAGE"
                )
                repository.insertChatRoom(defaultRoom)
                repository.insertChatRoom(defaultDm)
                _activeGroupId.value = defaultRoom.id
            } else {
                _activeGroupId.value = existing.first().id
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true
        } else {
            Log.e("TranslationViewModel", "Failed to initialize Text to Speech engine")
        }
    }

    fun setLanguageA(lang: LanguageItem) {
        _languageA.value = lang
    }

    fun setLanguageB(lang: LanguageItem) {
        _languageB.value = lang
    }

    fun swapLanguages() {
        val temp = _languageA.value
        _languageA.value = _languageB.value
        _languageB.value = temp
    }

    fun toggleSplitMode() {
        _isSplitFaceToFaceMode.value = !_isSplitFaceToFaceMode.value
    }

    fun setGlossarySearchQuery(query: String) {
        _glossarySearchQuery.value = query
    }

    fun clearError() {
        _errorState.value = null
    }

    // Translate sentence typed by side A (English to Spanish, e.g.)
    fun translateAtoB(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isTranslatingA.value = true
            _errorState.value = null
            try {
                val source = _languageA.value.name
                val target = _languageB.value.name
                
                val result = repository.translateWithRag(
                    text = text,
                    sender = "A",
                    sourceLang = source,
                    targetLang = target
                )

                _lastResult.value = result

                // Store in database
                val msg = ConversationMessage(
                    sender = "A",
                    originalText = text,
                    translatedText = result.translation,
                    sourceLang = source,
                    targetLang = target,
                    suggestions = result.alternatives.zip(result.explanations ?: emptyList()) { alt, exp ->
                        "$alt|$exp"
                    }.joinToString("\n")
                )
                repository.saveMessage(msg)
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Translation error Side A", e)
                _errorState.value = e.message ?: "An unknown API error occurred"
            } finally {
                _isTranslatingA.value = false
            }
        }
    }

    // Translate sentence typed by side B (Spanish to English, e.g.)
    fun translateBtoA(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _isTranslatingB.value = true
            _errorState.value = null
            try {
                val source = _languageB.value.name
                val target = _languageA.value.name

                val result = repository.translateWithRag(
                    text = text,
                    sender = "B",
                    sourceLang = source,
                    targetLang = target
                )

                _lastResult.value = result

                // Store in database
                val msg = ConversationMessage(
                    sender = "B",
                    originalText = text,
                    translatedText = result.translation,
                    sourceLang = source,
                    targetLang = target,
                    suggestions = result.alternatives.zip(result.explanations ?: emptyList()) { alt, exp ->
                        "$alt|$exp"
                    }.joinToString("\n")
                )
                repository.saveMessage(msg)
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Translation error Side B", e)
                _errorState.value = e.message ?: "An unknown API error occurred"
            } finally {
                _isTranslatingB.value = false
            }
        }
    }

    // Playback translation speech using TTS
    fun speakTranslation(text: String, langName: String) {
        if (!isTtsInitialized) return
        val languageItem = availableLanguages.firstOrNull { it.name.equals(langName, ignoreCase = true) }
        val locale = languageItem?.locale ?: Locale.US

        try {
            tts?.apply {
                language = locale
                speak(text, TextToSpeech.QUEUE_FLUSH, null, "TranslationSpeechID")
            }
        } catch (e: Exception) {
            Log.e("TranslationViewModel", "TTS playback failed", e)
        }
    }

    // Save term into glossary (RAG database)
    fun addGlossaryTerm(sourcePhrase: String, translatedPhrase: String, notes: String, isAtoB: Boolean) {
        if (sourcePhrase.isBlank() || translatedPhrase.isBlank()) return
        viewModelScope.launch {
            val sourceLang = if (isAtoB) _languageA.value.name else _languageB.value.name
            val targetLang = if (isAtoB) _languageB.value.name else _languageA.value.name

            val term = GlossaryTerm(
                sourcePhrase = sourcePhrase.trim(),
                translatedPhrase = translatedPhrase.trim(),
                notes = notes.trim(),
                sourceLanguage = sourceLang,
                targetLanguage = targetLang
            )
            repository.addGlossaryTerm(term)
        }
    }

    // Delete a glossary term
    fun deleteGlossaryTerm(id: Int) {
        viewModelScope.launch {
            repository.deleteGlossaryTerm(id)
        }
    }

    // Delete a message
    fun deleteMessage(id: Int) {
        viewModelScope.launch {
            repository.deleteMessage(id)
        }
    }

    // Clear whole translation history
    fun clearConversation() {
        viewModelScope.launch {
            repository.clearHistory()
            _lastResult.value = null
        }
    }

    fun toggleGroupMode() {
        _isGroupMode.value = !_isGroupMode.value
    }

    fun selectActiveGroup(roomId: String) {
        _activeGroupId.value = roomId
    }

    fun createChatRoom(name: String, initialMembers: List<ChatMember>, spaceType: String = "SPACE") {
        viewModelScope.launch {
            val serializedParts = initialMembers.joinToString(",") { "${it.nickname}:${it.language.code}" }
            val newRoom = com.example.data.database.ChatRoom(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                participantsString = serializedParts,
                spaceType = spaceType
            )
            repository.insertChatRoom(newRoom)
            _activeGroupId.value = newRoom.id
        }
    }

    fun addGroupMember(nickname: String, lang: LanguageItem) {
        val activeId = _activeGroupId.value ?: return
        viewModelScope.launch {
            val rooms = repository.getAllChatRoomsList()
            val room = rooms.firstOrNull { it.id == activeId } ?: return@launch
            val currentParts = room.participantsString
            
            // Split by comma in case multiple nicknames/emails are entered at once
            val nicknamesList = nickname.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (nicknamesList.isEmpty()) return@launch
            
            val addedSegment = nicknamesList.joinToString(",") { "$it:${lang.code}" }
            val updatedParts = if (currentParts.isBlank()) {
                addedSegment
            } else {
                "${currentParts},$addedSegment"
            }
            repository.updateChatRoomParticipants(activeId, updatedParts)
        }
    }

    fun removeGroupMember(id: String) {
        val activeId = _activeGroupId.value ?: return
        viewModelScope.launch {
            val rooms = repository.getAllChatRoomsList()
            val room = rooms.firstOrNull { it.id == activeId } ?: return@launch
            val updatedParts = room.participantsString.split(",")
                .filter { 
                    val spl = it.split(":")
                    val nick = spl.getOrNull(0) ?: ""
                    nick != id && it != id
                }
                .joinToString(",")
            repository.updateChatRoomParticipants(activeId, updatedParts)
        }
    }

    fun deleteActiveChatRoom() {
        val activeId = _activeGroupId.value ?: return
        viewModelScope.launch {
            repository.deleteChatRoom(activeId)
            repository.clearMessagesForRoom(activeId)
            val rooms = repository.getAllChatRoomsList()
            _activeGroupId.value = rooms.firstOrNull { it.id != activeId }?.id
        }
    }

    fun translateGroupMessage(senderId: String, text: String) {
        if (text.isBlank()) return
        val activeId = _activeGroupId.value ?: return
        viewModelScope.launch {
            _isTranslatingGroup.value = true
            _errorState.value = null
            try {
                val members = groupMembers.value
                val sender = members.firstOrNull { it.id == senderId || it.nickname == senderId }
                    ?: throw Exception("Sender participant not found in active room")

                // Determine target languages of all other members
                val targetLangs = members
                    .filter { it.nickname != sender.nickname }
                    .map { it.language.name }
                    .distinct()

                if (targetLangs.isEmpty()) {
                    throw Exception("Group chat needs at least one other participant to translate into.")
                }

                val result = repository.translateGroupChatWithRag(
                    text = text,
                    senderName = sender.nickname,
                    sourceLang = sender.language.name,
                    targetLangs = targetLangs
                )

                // Format translation outputs beautifully
                val formattedTranslations = result.translations.entries.joinToString("\n") { (lang, translatedText) ->
                    "$lang ➔ $translatedText"
                }

                // Construct a message with sender name prefixed with Group| for identification
                val msg = ConversationMessage(
                    sender = "Group|${sender.nickname}|${sender.language.code}",
                    originalText = text,
                    translatedText = formattedTranslations,
                    sourceLang = sender.language.name,
                    targetLang = targetLangs.joinToString(", "),
                    suggestions = result.alternatives.zip(result.explanations ?: emptyList()) { alt, exp ->
                        "$alt|$exp"
                    }.joinToString("\n"),
                    chatRoomId = activeId
                )
                repository.saveMessage(msg)
            } catch (e: Exception) {
                Log.e("TranslationViewModel", "Group translation failed", e)
                _errorState.value = e.message ?: "An unknown group translation error occurred"
            } finally {
                _isTranslatingGroup.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            tts?.apply {
                stop()
                shutdown()
            }
        } catch (e: Exception) {
            Log.e("TranslationViewModel", "TTS shutdown failed", e)
        }
    }
}

data class LanguageItem(
    val name: String,
    val code: String,
    val locale: Locale
)

class TranslationViewModelFactory(
    private val application: Application,
    private val repository: TranslationRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TranslationViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TranslationViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
