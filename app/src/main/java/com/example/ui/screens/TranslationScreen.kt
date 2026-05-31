package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ConversationMessage
import com.example.data.database.GlossaryTerm
import com.example.ui.viewmodel.LanguageItem
import com.example.ui.viewmodel.TranslationViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranslationScreen(
    viewModel: TranslationViewModel,
    modifier: Modifier = Modifier
) {
    val chatRooms by viewModel.chatRooms.collectAsState()
    val activeGroupId by viewModel.activeGroupId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val groupMembers by viewModel.groupMembers.collectAsState()
    val isTranslatingGroup by viewModel.isTranslatingGroup.collectAsState()
    val errorState by viewModel.errorState.collectAsState()
    val glossaryTerms by viewModel.glossaryTerms.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()

    if (currentUser == null) {
        GmailLoginScreen(
            viewModel = viewModel,
            modifier = modifier
        )
        return
    }

    var showGlossarySheet by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var showCreateRoomDialog by remember { mutableStateOf(false) }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Filter messages for active space/chat
    val activeMessages = remember(messages, activeGroupId) {
        messages.filter { it.chatRoomId == activeGroupId }
    }

    val activeRoom = remember(chatRooms, activeGroupId) {
        chatRooms.firstOrNull { it.id == activeGroupId }
    }

    // Modal Drawer content
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(320.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Workspace Drawer Header
                        Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "DuoTrans Hub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Secure Workspace Chat",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Start/Create Chat Action Styled as Google Chat FAB
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            showCreateRoomDialog = true
                            scope.launch { drawerState.close() }
                        },
                        icon = { Icon(Icons.Default.Add, contentDescription = null) },
                        text = { Text("New Conversation", fontWeight = FontWeight.Bold) },
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("new_workspace_chat_fab")
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Scrollable Drawer Menu Sections
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // 1. Named Collaboration Spaces
                    item {
                        Text(
                            text = "SPACES",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    val spaceRooms = chatRooms.filter { it.spaceType == "SPACE" }
                    if (spaceRooms.isEmpty()) {
                        item {
                            Text(
                                text = "No spaces joined yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        items(spaceRooms) { room ->
                            val isSelected = room.id == activeGroupId
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Tag,
                                        contentDescription = null
                                    )
                                },
                                label = {
                                    Text(
                                        text = room.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectActiveGroup(room.id)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .testTag("drawer_space_${room.id}"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }

                    // 2. Direct Messages
                    item {
                        Text(
                            text = "DIRECT MESSAGES",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                        )
                    }

                    val dmRooms = chatRooms.filter { it.spaceType == "DIRECT_MESSAGE" }
                    if (dmRooms.isEmpty()) {
                        item {
                            Text(
                                text = "No direct messages yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        items(dmRooms) { room ->
                            val isSelected = room.id == activeGroupId
                            NavigationDrawerItem(
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubble,
                                        contentDescription = null
                                    )
                                },
                                label = {
                                    Text(
                                        text = room.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectActiveGroup(room.id)
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .testTag("drawer_dm_${room.id}"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
                } // Close Column(modifier = Modifier.weight(1f))

                // Bottom Profile / Sign Out Card (Google account integration details)
                currentUser?.let { user ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = user.displayName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontSize = 16.sp
                            )
                        }

                        // Username & Email details
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = user.email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Sign out icon button
                        IconButton(
                            onClick = {
                                viewModel.signOut()
                            },
                            modifier = Modifier.testTag("google_sign_out_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Logout,
                                contentDescription = "Sign Out",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                } // Close Column(modifier = Modifier.fillMaxHeight())
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = activeRoom?.name ?: "No Conversation",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            activeRoom?.let { room ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val typeLabel = if (room.spaceType == "DIRECT_MESSAGE") "1:1 DM" else "Space [Group]"
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(typeLabel, fontSize = 10.sp) },
                                        modifier = Modifier.height(20.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                                        )
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("drawer_menu_button")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Open Drawer")
                        }
                    },
                    actions = {
                        // Action menu item: Add participants to active space/DM
                        IconButton(
                            onClick = { showAddMemberDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("add_member_top_button"),
                            enabled = activeRoom != null
                        ) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Participant")
                        }

                        IconButton(
                            onClick = { showInfoDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("info_button")
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "RAG translation details")
                        }

                        IconButton(
                            onClick = { showGlossarySheet = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("notebook_button")
                        ) {
                            BadgedBox(
                                badge = {
                                    if (glossaryTerms.isNotEmpty()) {
                                        Badge { Text(glossaryTerms.size.toString()) }
                                    }
                                }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = "Open Custom Glossary")
                            }
                        }

                        IconButton(
                            onClick = { showHistoryDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("history_button")
                        ) {
                            Icon(Icons.Default.History, contentDescription = "View conversation logs")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp)
                    )
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Error notification overlay banner
                errorState?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .align(Alignment.TopCenter)
                            .testTag("error_banner")
                            .zIndex(10f),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Default.Error,
                                    contentDescription = "Error notification",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss error")
                            }
                        }
                    }
                }

                // Render main workspace Space Chat panel
                if (activeRoom != null) {
                    SpaceChatPanel(
                        messages = activeMessages,
                        members = groupMembers,
                        isTranslating = isTranslatingGroup,
                        onTranslate = { sender, text ->
                            viewModel.translateGroupMessage(sender, text)
                        },
                        activeRoom = activeRoom,
                        viewModel = viewModel
                    )
                } else {
                    // Empty configuration
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No active conversation selected",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Open the left menu drawer and select (or create) a space room/direct message conversation to begin translating.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { scope.launch { drawerState.open() } }
                        ) {
                            Text("Open Menu Drawer")
                        }
                    }
                }
            }
        }

        // Glossary bottom sheet
        if (showGlossarySheet) {
            ModalBottomSheet(
                onDismissRequest = { showGlossarySheet = false },
                sheetState = bottomSheetState,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                GlossaryNotebookContent(
                    viewModel = viewModel,
                    activeLangA = viewModel.languageA.value.name,
                    activeLangB = viewModel.languageB.value.name,
                    onClose = { showGlossarySheet = false }
                )
            }
        }

        // History logs dialog
        if (showHistoryDialog) {
            HistoryLogsDialog(
                messages = messages,
                onDismiss = { showHistoryDialog = false },
                onClearAll = { viewModel.clearConversation() },
                onDelete = { viewModel.deleteMessage(it) }
            )
        }

        // RAG Information Details Dialog
        if (showInfoDialog) {
            RagInstructionDialog(
                onDismiss = { showInfoDialog = false }
            )
        }

        // Add Member/Membership to space dialog
        if (showAddMemberDialog && activeRoom != null) {
            AddGroupMemberDialog(
                availableLanguages = viewModel.availableLanguages,
                onDismiss = { showAddMemberDialog = false },
                onAdd = { nickname, lang ->
                    viewModel.addGroupMember(nickname, lang)
                }
            )
        }

        // Create ChatRoom Workspace Dialog
        if (showCreateRoomDialog) {
            CreateChatRoomDialog(
                availableLanguages = viewModel.availableLanguages,
                onDismiss = { showCreateRoomDialog = false },
                onCreate = { roomName, membersList, spaceTypeStr ->
                    viewModel.createChatRoom(roomName, membersList, spaceTypeStr)
                }
            )
        }
    }
}

/**
 * Visual space chat panel replacing obsolete split layout
 */
@Composable
fun SpaceChatPanel(
    messages: List<ConversationMessage>,
    members: List<TranslationViewModel.ChatMember>,
    isTranslating: Boolean,
    onTranslate: (String, String) -> Unit,
    activeRoom: com.example.data.database.ChatRoom,
    viewModel: TranslationViewModel
) {
    var activeSenderId by remember { mutableStateOf("") }
    LaunchedEffect(members) {
        if (activeSenderId.isBlank() || !members.any { it.id == activeSenderId }) {
            activeSenderId = members.firstOrNull()?.id ?: ""
        }
    }

    var textInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Members header info ribbon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Group,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )

            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(members) { member ->
                    var showMemberOptions by remember { mutableStateOf(false) }

                    AssistChip(
                        onClick = { showMemberOptions = true },
                        label = {
                            Text("${member.nickname} (${member.language.code.uppercase()})")
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (member.id == activeSenderId) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                    )

                    if (showMemberOptions) {
                        DropdownMenu(
                            expanded = showMemberOptions,
                            onDismissRequest = { showMemberOptions = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Speak as ${member.nickname}") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                                onClick = {
                                    activeSenderId = member.id
                                    showMemberOptions = false
                                }
                            )

                            if (members.size > 2) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Remove ${member.nickname}", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        viewModel.removeGroupMember(member.id)
                                        showMemberOptions = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Quick Delete Space Room button
            IconButton(
                onClick = { showDeleteConfirmDialog = true },
                modifier = Modifier.size(36.dp).testTag("delete_room_button")
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = "Delete Active Room", tint = MaterialTheme.colorScheme.error)
            }
        }

        // Live Message Feed Timeline List
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            if (messages.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.MarkChatRead,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This channel is quiet...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Choose your native speaker, type a sentence below, and Gemini will perform automated RAG translations into other members' languages!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(messages) { msg ->
                        WorkspaceMessageBubble(
                            message = msg,
                            onSpeak = { text, lang ->
                                viewModel.speakTranslation(text, lang)
                            }
                        )
                    }
                }
            }

            if (isTranslating) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                )
            }
        }

        // Smart reply contextual alternatives chips
        val lastMsg = messages.lastOrNull()
        if (lastMsg != null && lastMsg.suggestions.isNotBlank()) {
            val suggestionsList = lastMsg.suggestions.split("\n")
                .map { it.split("|").firstOrNull() ?: "" }
                .filter { it.isNotBlank() }

            if (suggestionsList.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "Smart responses & phrasing alternatives:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(suggestionsList) { suggestion ->
                            SuggestionChip(
                                onClick = { textInput = suggestion },
                                label = { Text(suggestion, fontSize = 11.sp) },
                                modifier = Modifier.testTag("suggestion_chip_$suggestion")
                            )
                        }
                    }
                }
            }
        }

        // Message typing bottom bar with interactive drafting info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.5.dp)
            ),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                // Sender indicator
                val currentSender = members.firstOrNull { it.id == activeSenderId }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        text = "Drafting as ${currentSender?.nickname ?: "Anon"} (${currentSender?.language?.name ?: "English"})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = { Text("Write translated message here...") },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("group_chat_input"),
                        maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent
                        )
                    )

                    FloatingActionButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                onTranslate(activeSenderId, textInput)
                                textInput = ""
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("group_send_button"),
                        shape = CircleShape,
                        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send original messsage", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    // Terminate Space Confirmation Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete This Conversation?") },
            text = { Text("This will permanently delete information about \"${activeRoom.name}\" and clear all translated logs from this device. Doing this will rebuild the local roster.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteActiveChatRoom()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete Room")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Beautiful workspace-inspired messaging bubble representing spaces/messages structure
 */
@Composable
fun WorkspaceMessageBubble(
    message: ConversationMessage,
    onSpeak: (String, String) -> Unit
) {
    // Process sender name
    val rawSender = message.sender
    val isGroupFormatted = rawSender.startsWith("Group|")
    val senderName = if (isGroupFormatted) {
        rawSender.split("|").getOrNull(1) ?: "Guest"
    } else {
        if (rawSender == "A") "Person A" else if (rawSender == "B") "Person B" else rawSender
    }

    val senderLangCode = if (isGroupFormatted) {
        rawSender.split("|").getOrNull(2)?.uppercase() ?: "EN"
    } else {
        if (rawSender == "A") "EN" else "ES"
    }

    // Color code participant avatars to distinguish them elegantly
    val avatarBackground = remember(senderName) {
        val colors = listOf(
            Color(0xFF0F9D58), // Green
            Color(0xFF4285F4), // Blue
            Color(0xFFEA4335), // Red
            Color(0xFFF4B400), // Yellow
            Color(0xFF673AB7), // Deep Purple
            Color(0xFF1E88E5)  // Material Blue
        )
        val index = (senderName.hashCode() % colors.size + colors.size) % colors.size
        colors[index]
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("message_bubble_${message.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            // Header: Avatar, Name, Language Code and Timestamp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(avatarBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = senderName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = senderName,
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                            Text(senderLangCode, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Text(
                        text = "Original speaking tongue: ${message.sourceLang}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }

                // Play message audio original
                IconButton(
                    onClick = { onSpeak(message.originalText, message.sourceLang) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Play original text aloud", modifier = Modifier.size(16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Message Original Content
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = "Original Sentence:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message.originalText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Translated Results Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Translated (Gemini AI Multi-way):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = message.translatedText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                // If group text contains mapping, speak the text corresponding to first mapping
                                val textToSpeak = if (message.translatedText.contains("➔")) {
                                    val lines = message.translatedText.split("\n")
                                    val line = lines.firstOrNull { it.contains("➔") } ?: ""
                                    line.split("➔").getOrNull(1)?.trim() ?: message.translatedText
                                } else {
                                    message.translatedText
                                }
                                val speakLang = if (message.targetLang.contains(",")) {
                                    message.targetLang.split(",").firstOrNull()?.trim() ?: "Spanish"
                                } else {
                                    message.targetLang
                                }
                                onSpeak(textToSpeak, speakLang)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Play translated audio aloud",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            // Display explanations / cultural nuances if present
            if (message.suggestions.isNotBlank()) {
                val sugPairs = message.suggestions.split("\n")
                    .map { it.split("|") }
                    .filter { it.size >= 2 }

                if (sugPairs.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.secondary)
                                Text(
                                    text = "Cultural Phrasing Options (RAG):",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            sugPairs.forEach { item ->
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "Alternative: \"${item[0]}\"",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Nuance: ${item[1]}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Glossary bottom sheet content representing RAG custom terminology settings
 */
@Composable
fun GlossaryNotebookContent(
    viewModel: TranslationViewModel,
    activeLangA: String,
    activeLangB: String,
    onClose: () -> Unit
) {
    val searchVal by viewModel.glossarySearchQuery.collectAsState()
    val terms by viewModel.filteredGlossary.collectAsState()

    var sourceTerm by remember { mutableStateOf("") }
    var translatedTerm by remember { mutableStateOf("") }
    var termNotes by remember { mutableStateOf("") }
    var customIsAtoB by remember { mutableStateOf(true) }

    var isAddingNew by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 600.dp)
            .padding(16.dp)
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Custom Glossary Terminology",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "RAG Dictionary words prioritized in Gemini translation contexts",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { isAddingNew = !isAddingNew },
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                Icon(
                    imageVector = if (isAddingNew) Icons.Default.Close else Icons.Default.Add,
                    contentDescription = "New word toggle"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Create new term drawer form
        AnimatedVisibility(
            visible = isAddingNew,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Add Custom Glossary Term",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    // Direction label selection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (customIsAtoB) "Direction: $activeLangA ➔ $activeLangB" else "Direction: $activeLangB ➔ $activeLangA",
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = { customIsAtoB = !customIsAtoB }) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Swap")
                        }
                    }

                    OutlinedTextField(
                        value = sourceTerm,
                        onValueChange = { sourceTerm = it },
                        label = { Text("Source Vocabulary Word/Phrase") },
                        placeholder = { Text("e.g. break a leg") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("glossary_source_input")
                    )

                    OutlinedTextField(
                        value = translatedTerm,
                        onValueChange = { translatedTerm = it },
                        label = { Text("Preferred Translation Output") },
                        placeholder = { Text("e.g. ¡mucha mierda!") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("glossary_target_input")
                    )

                    OutlinedTextField(
                        value = termNotes,
                        onValueChange = { termNotes = it },
                        label = { Text("Usage Explanation Nuance Notes (Optional)") },
                        placeholder = { Text("e.g. Idiom for wishing actors good luck.") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (sourceTerm.isNotBlank() && translatedTerm.isNotBlank()) {
                                viewModel.addGlossaryTerm(sourceTerm, translatedTerm, termNotes, customIsAtoB)
                                sourceTerm = ""
                                translatedTerm = ""
                                termNotes = ""
                                isAddingNew = false
                            }
                        },
                        enabled = sourceTerm.isNotBlank() && translatedTerm.isNotBlank(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("glossary_save_button")
                    ) {
                        Text("Add Custom Word to Glossary")
                    }
                }
            }
        }

        // Search Bar for Glossary
        OutlinedTextField(
            value = searchVal,
            onValueChange = { viewModel.setGlossarySearchQuery(it) },
            placeholder = { Text("Search glossary terminology...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Glossary list
        if (terms.isEmpty()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))
                Text("No terms matched. Add custom ones to train Gemini context translations!", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(terms) { term ->
                    GlossaryTermCard(
                        term = term,
                        onDelete = { viewModel.deleteGlossaryTerm(term.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun GlossaryTermCard(
    term: GlossaryTerm,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = term.sourcePhrase,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 15.sp
                    )
                    Text("➔", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                    Text(
                        text = term.translatedPhrase,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 15.sp
                    )
                }
                Text(
                    text = "Pair: ${term.sourceLanguage} to ${term.targetLanguage}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                if (term.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Notes: ${term.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.minimumInteractiveComponentSize().testTag("delete_glossary_term_${term.id}")
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete term entries", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

/**
 * Dialog to add participants dynamically to a Chat room
 */
@Composable
fun AddGroupMemberDialog(
    availableLanguages: List<LanguageItem>,
    onDismiss: () -> Unit,
    onAdd: (String, LanguageItem) -> Unit
) {
    var nickname by remember { mutableStateOf("") }
    var selectedLang by remember { mutableStateOf(availableLanguages.firstOrNull() ?: availableLanguages[0]) }
    var expandedMenu by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Participant", fontWeight = FontWeight.ExtraBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text("Nickname(s) or Email(s)") },
                    placeholder = { Text("e.g. a@gmail.com, b@gmail.com") },
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_member_nickname_input")
                )

                Column {
                    Text("Native Speaking Tongue", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        OutlinedButton(
                            onClick = { expandedMenu = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${selectedLang.name} (${selectedLang.code.uppercase()})")
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = expandedMenu,
                            onDismissRequest = { expandedMenu = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            availableLanguages.forEach { lang ->
                                DropdownMenuItem(
                                    text = { Text("${lang.name} (${lang.code.uppercase()})") },
                                    onClick = {
                                        selectedLang = lang
                                        expandedMenu = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nickname.isNotBlank()) {
                        onAdd(nickname.trim(), selectedLang)
                        onDismiss()
                    }
                },
                enabled = nickname.isNotBlank()
            ) {
                Text("Add Member")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Unified dialog to create Workspace Conversations of type SPACE or DIRECT_MESSAGE
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateChatRoomDialog(
    availableLanguages: List<LanguageItem>,
    onDismiss: () -> Unit,
    onCreate: (String, List<TranslationViewModel.ChatMember>, String) -> Unit
) {
    var roomName by remember { mutableStateOf("") }
    var isDirectMessage by remember { mutableStateOf(false) }
    var draftNickname by remember { mutableStateOf("") }
    var draftSelectedLang by remember { mutableStateOf(availableLanguages.firstOrNull() ?: availableLanguages[0]) }
    var draftLangExpanded by remember { mutableStateOf(false) }

    val draftedMembers = remember { mutableStateListOf<TranslationViewModel.ChatMember>() }

    // Seed self participant immediately as English speaker for demo/practical interaction
    LaunchedEffect(Unit) {
        draftedMembers.add(
            TranslationViewModel.ChatMember(
                id = "Alice",
                nickname = "Alice",
                language = availableLanguages.firstOrNull { it.code == "en" } ?: availableLanguages[0]
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Conversation", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Workspace Type Toggles
                Column {
                    Text(
                        text = "Conversation Type",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !isDirectMessage,
                            onClick = { isDirectMessage = false },
                            label = { Text("Named Space (Group Chat)") },
                            modifier = Modifier.weight(1.0f)
                        )
                        FilterChip(
                            selected = isDirectMessage,
                            onClick = { isDirectMessage = true },
                            label = { Text("Direct Message (1:1)") },
                            modifier = Modifier.weight(1.0f)
                        )
                    }
                }

                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    label = { Text(if (!isDirectMessage) "Space Room Name" else "Conversation Partner Name") },
                    placeholder = { Text(if (!isDirectMessage) "e.g. Global Tech Engineers" else "e.g. Carlos") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_chat_name_input")
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                Text(
                    text = "Configured Members & Speakers:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                if (draftedMembers.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(draftedMembers) { member ->
                            InputChip(
                                selected = true,
                                onClick = {},
                                label = { Text("${member.nickname} (${member.language.code.uppercase()})") },
                                trailingIcon = {
                                    if (draftedMembers.size > 1) {
                                        IconButton(
                                            onClick = { draftedMembers.remove(member) },
                                            modifier = Modifier.size(16.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove member draft",
                                                modifier = Modifier.size(12.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Participant draft compiler drawer for custom members addition
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = draftNickname,
                                onValueChange = { draftNickname = it },
                                label = { Text("Add Name(s) / Email(s)") },
                                placeholder = { Text("e.g. a@gmail.com, b@gmail.com") },
                                maxLines = 3,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("new_chat_member_nickname")
                            )

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { draftLangExpanded = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "${draftSelectedLang.name} (${draftSelectedLang.code.uppercase()})",
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                DropdownMenu(
                                    expanded = draftLangExpanded,
                                    onDismissRequest = { draftLangExpanded = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    availableLanguages.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text("${lang.name} (${lang.code.uppercase()})") },
                                            onClick = {
                                                draftSelectedLang = lang
                                                draftLangExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = {
                                if (draftNickname.isNotBlank()) {
                                    val parts = draftNickname.split(",").map { it.trim() }.filter { it.isNotBlank() }
                                    for (trimmedNick in parts) {
                                        if (!draftedMembers.any { it.id == trimmedNick }) {
                                            draftedMembers.add(
                                                TranslationViewModel.ChatMember(
                                                    id = trimmedNick,
                                                    nickname = trimmedNick,
                                                    language = draftSelectedLang
                                                )
                                            )
                                        }
                                    }
                                    draftNickname = ""
                                }
                            },
                            enabled = draftNickname.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_drafted_member_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Confirm Member")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (roomName.isNotBlank() && draftedMembers.isNotEmpty()) {
                        onCreate(
                            roomName.trim(),
                            draftedMembers.toList(),
                            if (isDirectMessage) "DIRECT_MESSAGE" else "SPACE"
                        )
                        onDismiss()
                    }
                },
                enabled = roomName.isNotBlank() && draftedMembers.isNotEmpty()
            ) {
                Text("Create Conversation")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Modern dialog showcasing all previous messages logs in details
 */
@Composable
fun HistoryLogsDialog(
    messages: List<ConversationMessage>,
    onDismiss: () -> Unit,
    onClearAll: () -> Unit,
    onDelete: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Device Conversation History", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "A full historical log of all translations made in local sessions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No saved translations yet.", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages) { msg ->
                            val rawSender = msg.sender
                            val displaySender = if (rawSender.startsWith("Group|")) {
                                rawSender.split("|").getOrNull(1) ?: "Guest"
                            } else {
                                if (rawSender == "A") "A" else "B"
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sender $displaySender [${msg.sourceLang} ➔ ${msg.targetLang}]:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(text = "Original: \"${msg.originalText}\"", style = MaterialTheme.typography.bodySmall)
                                        Text(text = "Translated: \"${msg.translatedText}\"", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    }
                                    IconButton(onClick = { onDelete(msg.id) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete entry", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        },
        dismissButton = {
            if (messages.isNotEmpty()) {
                TextButton(
                    onClick = {
                        onClearAll()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear All")
                }
            }
        }
    )
}

/**
 * Information guidelines detailing dynamic RAG prompts engineering
 */
@Composable
fun RagInstructionDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.TipsAndUpdates, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Text("Gemini RAG System Details", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "DuoTrans utilizes Retrieval-Augmented Generation (RAG) to solve translation inaccuracy:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "1. Local Knowledge Base: Customized terms added to your RAG Dictionary are queried in real time.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "2. Prompt Injection: Matches are formatted as a localized system prompt instruction context block, which dynamically guides Gemini.",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    text = "3. Smart Culture Nuance: The model returns 3 alternative cultural tones with advice on appropriate contexts.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}
