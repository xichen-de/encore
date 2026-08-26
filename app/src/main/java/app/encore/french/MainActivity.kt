package app.encore.french

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import app.encore.french.ui.EncoreApp
import app.encore.french.ui.EncoreTheme

class MainActivity : ComponentActivity() {
    private var incomingIntent: ((Intent) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val vm: MainViewModel = viewModel()
            incomingIntent = { handleIntent(it, vm) }
            EncoreTheme { EncoreApp(vm, initialIntent = if (savedInstanceState == null) intent else null) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntent?.invoke(intent)
    }

    private fun handleIntent(intent: Intent, vm: MainViewModel) {
        if (intent.action == Intent.ACTION_VIEW) intent.data?.let(vm::readDeck)
    }
}
