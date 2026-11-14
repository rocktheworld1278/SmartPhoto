package com.smartphoto

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat

class MainActivity : ComponentActivity() {
    private lateinit var store: ArchiveStore
    private var refresh by mutableIntStateOf(0)
    private var status by mutableStateOf("Your photos stay on this device.")
    private val importPicker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) importUris(uris) }
    private val restorePicker = registerForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri -> if (uri != null) restoreTo(uri) }

    private var pendingRestore: ArchiveInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ArchiveStore(this)
        setContent {
            MaterialTheme {
                val archives = remember(refresh) { store.list() }
                val originalTotal = archives.sumOf { it.originalBytes }
                val archiveTotal = archives.sumOf { it.archiveBytes }
                Scaffold(
                    topBar = { TopAppBar(title = { Text("SmartPhoto") }) },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            importPicker.launch(arrayOf("image/*"))
                        }) { Icon(Icons.Default.Add, contentDescription = "Import photos") }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Private, offline photo archive", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Text("${archives.size} archived photos")
                                Text("Originals: ${formatBytes(originalTotal)}")
                                Text("Stored: ${formatBytes(archiveTotal)}")
                                Text("Saved: ${formatBytes((originalTotal - archiveTotal).coerceAtLeast(0))}")
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Archived photos", style = MaterialTheme.typography.titleMedium)
                        }
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(archives, key = { it.id }) { item ->
                                Card(Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(item.originalName, style = MaterialTheme.typography.titleSmall)
                                        Text("${formatBytes(item.originalBytes)} original · ${formatBytes(item.archiveBytes)} stored",
                                            style = MaterialTheme.typography.bodySmall)
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            TextButton(onClick = {
                                                pendingRestore = item
                                                restorePicker.launch(item.originalName)
                                            }) {
                                                Icon(Icons.Default.Restore, contentDescription = null)
                                                Spacer(Modifier.width(4.dp)); Text("Restore")
                                            }
                                            TextButton(onClick = {
                                                store.delete(item); refresh++; status = "Archive deleted."
                                            }) { Text("Delete archive") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun importUris(uris: List<Uri>) {
        Thread {
            var imported = 0
            var failed = 0
            uris.forEach { uri ->
                try {
                    val name = queryDisplayName(uri) ?: "photo-${System.currentTimeMillis()}"
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    store.import(uri, name)
                    imported++
                } catch (_: Throwable) { failed++ }
            }
            runOnUiThread {
                refresh++
                status = "Imported $imported file(s). ${if (failed > 0) "$failed failed." else "All passed integrity checks."}"
            }
        }.start()
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun restoreTo(uri: Uri) {
        val item = pendingRestore ?: return
        Thread {
            try {
                contentResolver.openOutputStream(uri)?.use { store.restore(item, it) }
                    ?: error("Cannot create output file")
                runOnUiThread { status = "Restored ${item.originalName}; SHA-256 verified." }
            } catch (t: Throwable) {
                runOnUiThread { status = "Restore failed: ${t.message}" }
            } finally { pendingRestore = null }
        }.start()
    }

    private fun formatBytes(value: Long): String {
        if (value < 1024) return "$value B"
        val units = listOf("KB", "MB", "GB", "TB")
        var v = value.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return "${DecimalFormat("0.##").format(v)} ${units[i]}"
    }
}
