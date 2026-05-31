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
}

