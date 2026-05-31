package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.TranslationRepository
import com.example.ui.viewmodel.TranslationViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Dual Conversation Translator", appName)
  }

  @Test
  fun `verify database and viewmodel initialization`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val application = context as Application
    
    val database = AppDatabase.getDatabase(context)
    assertNotNull("AppDatabase should be initialized", database)
    
    val repository = TranslationRepository(
      messageDao = database.messageDao(),
      glossaryDao = database.glossaryDao()
    )
    assertNotNull("TranslationRepository should be initialized", repository)
    
    val viewModel = TranslationViewModel(application, repository)
    assertNotNull("TranslationViewModel should be initialized", viewModel)
  }

  @Test
  fun `verify main activity launches successfully`() {
    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    val activity = controller.get()
    assertNotNull("MainActivity should launch without crashing", activity)
  }

  @Test
  fun `verify invite multiple emails to group chat splits properly`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val application = context as Application
    val database = AppDatabase.getDatabase(context)
    val dao = database.messageDao()

    // Explicitly insert a test room first to avoid relying on asynchronous init timing
    val testRoom = com.example.data.database.ChatRoom(
      id = "test_space",
      name = "Test Space",
      participantsString = "Alice:en"
    )
    dao.insertChatRoom(testRoom)

    val repository = TranslationRepository(
      messageDao = dao,
      glossaryDao = database.glossaryDao()
    )
    val viewModel = TranslationViewModel(application, repository)
    viewModel.selectActiveGroup("test_space")
    val english = viewModel.availableLanguages.first()

    // Invite multiple emails at once
    viewModel.addGroupMember("a@gmail.com, b@gmail.com", english)

    // Wait a brief moment for coroutines inside viewModel to execute the DB insertion
    var activeRoom = dao.getAllChatRoomsList().firstOrNull { it.id == "test_space" }
    var attempts = 0
    while (activeRoom != null && !activeRoom.participantsString.contains("a@gmail.com") && attempts < 15) {
      kotlinx.coroutines.delay(100)
      try {
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
      } catch (e: Exception) {
        // ignore looper exceptions in simple unit test runs
      }
      activeRoom = dao.getAllChatRoomsList().firstOrNull { it.id == "test_space" }
      attempts++
    }

    assertNotNull("Active chat room exists", activeRoom)
    val participants = activeRoom!!.participantsString
    org.junit.Assert.assertTrue("Should contain a@gmail.com with correct language suffix", participants.contains("a@gmail.com:en"))
    org.junit.Assert.assertTrue("Should contain b@gmail.com with correct language suffix", participants.contains("b@gmail.com:en"))
  }

  @Test
  fun `verify Google Sign In updates viewmodel state and preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val application = context as Application
    val database = AppDatabase.getDatabase(context)
    val dao = database.messageDao()
    val repository = TranslationRepository(
      messageDao = dao,
      glossaryDao = database.glossaryDao()
    )
    val viewModel = TranslationViewModel(application, repository)
    
    // Initial state should be logged out (null)
    org.junit.Assert.assertNull("Should start as logged out", viewModel.currentUser.value)
    
    // Perform Google Sign-In login action
    viewModel.signInWithGoogle("testuser@gmail.com", "Test User")
    
    // Assert State updates
    val loggedInUser = viewModel.currentUser.value
    assertNotNull("User should be logged in", loggedInUser)
    assertEquals("testuser@gmail.com", loggedInUser!!.email)
    assertEquals("Test User", loggedInUser.displayName)
    
    // Perform sign out
    viewModel.signOut()
    org.junit.Assert.assertNull("Should sign out cleanly", viewModel.currentUser.value)
  }
}

