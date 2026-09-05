package com.nova.assistantlite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var commandText by mutableStateOf("")
    private var statusText by mutableStateOf("Ready — speak naturally.")
    private var history by mutableStateOf(listOf<String>())

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()?.let {
                        commandText = it
                        executeCommand()
                    }
            } else statusText = "Voice recognition was cancelled."
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
            else statusText = "Microphone permission is required."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NovaScreen(
                    command = commandText,
                    status = statusText,
                    history = history,
                    onCommandChange = { commandText = it },
                    onExecute = { executeCommand() },
                    onSpeak = { requestVoice() },
                    onHistoryClick = { commandText = it; executeCommand() }
                )
            }
        }
    }

    private fun executeCommand() {
        val command = commandText.trim()
        if (command.isBlank()) {
            statusText = "Type or say something first."
            return
        }
        try {
            statusText = CommandProcessor.execute(this, command)
            history = (listOf(command) + history.filterNot { it == command }).take(10)
        } catch (e: Exception) {
            statusText = "Unable to perform that action."
        }
    }

    private fun requestVoice() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
            startListening()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startListening() {
        speechLauncher.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "What would you like me to do?")
        })
    }
}

@Composable
fun NovaScreen(
    command: String,
    status: String,
    history: List<String>,
    onCommandChange: (String) -> Unit,
    onExecute: () -> Unit,
    onSpeak: () -> Unit,
    onHistoryClick: (String) -> Unit
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("NOVA ASSISTANT V2", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Smart Command Edition", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(28.dp))

            OutlinedTextField(
                value = command,
                onValueChange = onCommandChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("Speak naturally or type a command") },
                placeholder = { Text("Example: I want to watch relaxing worship music") }
            )

            Spacer(Modifier.height(14.dp))

            Button(onClick = onSpeak, modifier = Modifier.fillMaxWidth()) {
                Text("🎤 SPEAK COMMAND")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onExecute, modifier = Modifier.fillMaxWidth()) {
                Text("▶ EXECUTE")
            }

            Spacer(Modifier.height(18.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(status, modifier = Modifier.padding(16.dp))
            }

            if (history.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Recent Commands", modifier = Modifier.fillMaxWidth(), fontWeight = FontWeight.Bold)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(history) { item ->
                        TextButton(onClick = { onHistoryClick(item) }, modifier = Modifier.fillMaxWidth()) {
                            Text(item)
                        }
                    }
                }
            } else Spacer(Modifier.weight(1f))

            Text(
                "Try: “Take me to Facebook” • “Find funny cat videos” • “Search Android tutorials”",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
