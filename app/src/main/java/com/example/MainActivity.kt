package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.database.AppDatabase
import com.example.data.repository.TranslationRepository
import com.example.ui.screens.TranslationScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TranslationViewModel
import com.example.ui.viewmodel.TranslationViewModelFactory

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: TranslationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Edge-to-edge support configuration
        enableEdgeToEdge()

        // Room database and Repository setup
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = TranslationRepository(
            messageDao = database.messageDao(),
            glossaryDao = database.glossaryDao()
        )

        // Instantiate ViewModel with Custom Factory
        val factory = TranslationViewModelFactory(application, repository)
        viewModel = ViewModelProvider(this, factory)[TranslationViewModel::class.java]

        setContent {
            MyApplicationTheme {
                TranslationScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@androidx.compose.runtime.Composable
fun Greeting(name: String, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    androidx.compose.material3.Text(text = "Hello $name!", modifier = modifier)
}

