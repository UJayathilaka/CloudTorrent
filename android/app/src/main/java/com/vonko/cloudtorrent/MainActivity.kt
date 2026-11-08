package com.vonko.cloudtorrent

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vonko.cloudtorrent.data.AddTorrentRequest
import com.vonko.cloudtorrent.data.CloudTorrentApi
import com.vonko.cloudtorrent.data.CloudTorrentApiFactory
import com.vonko.cloudtorrent.data.TorrentDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

private val Bg = Color(0xFF0B1020)
private val Panel = Color(0xFF151D31)
private val Accent = Color(0xFF7895FF)
private val Muted = Color(0xFF9AA7C2)
private enum class Tab { HOME, DOWNLOADS, DRIVE, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CloudTorrentApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudTorrentApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cloudtorrent_settings", Context.MODE_PRIVATE) }
    var server by remember { mutableStateOf(prefs.getString("server_url", "") ?: "") }
    var token by remember { mutableStateOf(prefs.getString("api_token", "") ?: "") }
    var serverInput by remember { mutableStateOf(server) }
    var tokenInput by remember { mutableStateOf(token) }
    val api = remember(server, token) {
        if (server.isBlank() || token.isBlank()) null
        else runCatching { CloudTorrentApiFactory.create(server, token) }.getOrNull()
    }
    var tab by remember { mutableStateOf(Tab.HOME) }
    var showAdd by remember { mutableStateOf(false) }
    var magnet by remember { mutableStateOf("") }
    var torrents by remember { mutableStateOf<List<TorrentDto>>(emptyList()) }
    var connected by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var busyHash by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(api, tab) {
        if (api == null) {
            connected = false
            torrents = emptyList()
        } else {
            do {
                try {
                    torrents = api.getTorrents()
                    connected = true
                    if (error) message = ""
                    error = false
                } catch (e: Exception) {
                    connected = false
                    message = e.message?.substringBefore("\n") ?: "Could not connect to server."
                    error = true
                }
                if (tab == Tab.DOWNLOADS) delay(5000) else break
            } while (true)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Panel, onBackground = Color.White, onSurface = Color.White)) {
        Scaffold(
            containerColor = Bg,
            topBar = {
                TopAppBar(
                    title = { Column {
                        Text("CLOUDTORRENT", fontSize = 11.sp, color = Muted, fontWeight = FontWeight.Bold)
                        Text("Your cloud library", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    } },
                    actions = {
                        Surface(color = if (connected) Color(0xFF173A32) else Color(0xFF382B25), shape = RoundedCornerShape(50)) {
                            Text(if (connected) "● ONLINE" else "● OFFLINE",
                                color = if (connected) Color(0xFF66D6A5) else Color(0xFFFFC46B),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Panel) {
                    NavigationBarItem(tab == Tab.HOME, { tab = Tab.HOME }, { Icon(Icons.Default.Cloud, null) }, label = { Text("Home") })
                    NavigationBarItem(tab == Tab.DOWNLOADS, { tab = Tab.DOWNLOADS }, { Icon(Icons.Default.Download, null) }, label = { Text("Downloads") })
                    NavigationBarItem(tab == Tab.DRIVE, { tab = Tab.DRIVE }, { Icon(Icons.Default.Folder, null) }, label = { Text("Drive") })
                    NavigationBarItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS; serverInput = server; tokenInput = token }, { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
                }
            }
        ) { padding ->
            when (tab) {
                Tab.HOME -> HomeScreen(Modifier.padding(padding), connected, torrents, { showAdd = true }, { tab = Tab.DOWNLOADS })
                Tab.DOWNLOADS -> DownloadsScreen(
                    Modifier.padding(padding), torrents, busyHash, { showAdd = true },
                    { hash, command ->
                        if (api == null) {
                            message = "Set your HTTPS server URL and API token in Settings."
                            error = true
                        } else scope.launch {
                            busyHash = hash
                            try {
                                command(api, hash)
                                torrents = api.getTorrents()
                                message = "Torrent updated."
                                error = false
                            } catch (e: Exception) {
                                message = e.message ?: "Request failed."
                                error = true
                            } finally { busyHash = null }
                        }
                    }
                )
                Tab.DRIVE -> SimpleScreen(Modifier.padding(padding), "Google Drive",
                    "Drive OAuth, automatic uploads and the cloud library are the next development stage.")
                Tab.SETTINGS -> SettingsScreen(
                    Modifier.padding(padding), serverInput, { serverInput = it }, tokenInput, { tokenInput = it },
                    message, error, {
                        val value = serverInput.trim().trimEnd('/')
                        if (!value.startsWith("https://", true)) {
                            message = "Enter an HTTPS server URL."
                            error = true
                        } else if (tokenInput.isBlank()) {
                            message = "Enter the API token."
                            error = true
                        } else {
                            prefs.edit().putString("server_url", value).putString("api_token", tokenInput.trim()).apply()
                            server = value
                            token = tokenInput.trim()
                            message = "Settings saved. Connecting..."
                            error = false
                        }
                    }
                )
            }
        }

        if (showAdd) {
            AlertDialog(
                onDismissRequest = { if (!busy) showAdd = false },
                containerColor = Panel,
                title = { Text("Add torrent") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("The magnet link is sent to your cloud server. Your phone does not download the torrent data.", color = Muted, fontSize = 13.sp)
                        OutlinedTextField(value = magnet, onValueChange = { magnet = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("magnet:?xt=urn:btih:...") }, minLines = 3)
                        if (api == null) Text("Configure the HTTPS server and API token in Settings first.", color = Color(0xFFFFC46B), fontSize = 12.sp)
                    }
                },
                confirmButton = {
                    TextButton(enabled = !busy, onClick = {
                        if (api == null) {
                            message = "Configure the server in Settings first."
                            error = true
                            showAdd = false
                        } else if (!magnet.trim().startsWith("magnet:?xt=", true)) {
                            message = "Enter a valid BitTorrent magnet link."
                            error = true
                        } else scope.launch {
                            busy = true
                            try {
                                api.addTorrent(AddTorrentRequest(magnet.trim()))
                                magnet = ""
                                showAdd = false
                                tab = Tab.DOWNLOADS
                                torrents = api.getTorrents()
                                message = "Magnet link sent to server."
                                error = false
                            } catch (e: Exception) {
                                message = e.message ?: "Could not add torrent."
                                error = true
                            } finally { busy = false }
                        }
                    }) { Text(if (busy) "Sending..." else "Add to cloud", color = Accent) }
                },
                dismissButton = { TextButton(enabled = !busy, onClick = { showAdd = false; magnet = "" }) { Text("Cancel", color = Muted) } }
            )
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, connected: Boolean, torrents: List<TorrentDto>, onAdd: () -> Unit, onDownloads: () -> Unit) {
    val active = torrents.count { it.progress < 1.0 && !it.state.startsWith("paused") }
    val completed = torrents.count { it.progress >= 1.0 }
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("CLOUD DOWNLOADS", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Your downloads,\nwithout filling your phone.", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                Text("Send a magnet link to your own server. The server handles the torrent transfer.", color = Muted, fontSize = 13.sp)
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("＋  Add magnet link") }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Active", active.toString(), Modifier.weight(1f))
            StatCard("Completed", completed.toString(), Modifier.weight(1f))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent downloads", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onDownloads) { Text("View all") }
        }
        if (torrents.isEmpty()) EmptyState("No downloads yet",
            if (connected) "Add a magnet link to start your first cloud download." else "Connect your backend in Settings to see live downloads.")
        else torrents.take(3).forEach { TorrentSummary(it) }
    }
}

@Composable
private fun DownloadsScreen(
    modifier: Modifier, torrents: List<TorrentDto>, busyHash: String?, onAdd: () -> Unit,
    onAction: (String, suspend (CloudTorrentApi, String) -> Unit) -> Unit
) {
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Downloads", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onAdd) { Text("＋ Add") }
        }
        Spacer(Modifier.height(12.dp))
        if (torrents.isEmpty()) EmptyState("No downloads yet", "Add a magnet link to see its live status here.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(torrents, key = { it.hash }) { torrent ->
                TorrentCard(torrent, busyHash == torrent.hash,
                    { onAction(torrent.hash) { api, hash -> api.pauseTorrent(hash) } },
                    { onAction(torrent.hash) { api, hash -> api.resumeTorrent(hash) } },
                    { onAction(torrent.hash) { api, hash -> api.deleteTorrent(hash, false) } })
            }
        }
    }
}

@Composable
private fun TorrentSummary(torrent: TorrentDto) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(torrent.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
            LinearProgressIndicator(progress = { torrent.progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text(torrent.progressPercent() + " • " + torrent.state, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TorrentCard(torrent: TorrentDto, busy: Boolean, onPause: () -> Unit, onResume: () -> Unit, onDelete: () -> Unit) {
    val paused = torrent.state.startsWith("paused")
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(torrent.name, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 2)
            LinearProgressIndicator(progress = { torrent.progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(torrent.progressPercent() + " • " + torrent.state, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(formatBytes(torrent.size), color = Muted, fontSize = 12.sp)
            }
            Text("↓ " + formatBytes(torrent.dlspeed) + "/s     ↑ " + formatBytes(torrent.upspeed) + "/s", color = Accent, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = if (paused) onResume else onPause, enabled = !busy && torrent.progress < 1.0, modifier = Modifier.weight(1f)) {
                    Text(if (paused) "Resume" else "Pause")
                }
                OutlinedButton(onClick = onDelete, enabled = !busy, modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8A8A))) { Text("Remove") }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    modifier: Modifier, server: String, onServer: (String) -> Unit, token: String, onToken: (String) -> Unit,
    message: String, error: Boolean, onSave: () -> Unit
) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Settings", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text("Backend connection", color = Color.White, fontWeight = FontWeight.SemiBold)
        Text("Enter the public HTTPS address of your CloudTorrent server and its API token.", color = Muted, fontSize = 13.sp)
        OutlinedTextField(server, onServer, modifier = Modifier.fillMaxWidth(), label = { Text("Server URL") },
            placeholder = { Text("https://cloud.example.com") }, singleLine = true)
        OutlinedTextField(token, onToken, modifier = Modifier.fillMaxWidth(), label = { Text("API token") },
            placeholder = { Text("Paste your server token") }, singleLine = true)
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save connection") }
        if (message.isNotBlank()) Text(message, color = if (error) Color(0xFFFF8A8A) else Color(0xFF66D6A5), fontSize = 12.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Security", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("HTTPS only. The API token is stored in the app's private preferences.", color = Muted, fontSize = 12.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Panel)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Google Drive", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("OAuth and automatic upload will be implemented next.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SimpleScreen(modifier: Modifier, title: String, description: String) {
    Column(modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        EmptyState("Coming next", description)
    }
}

@Composable
private fun StatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, color = Muted, fontSize = 12.sp)
            Text(value, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Folder, null, tint = Muted, modifier = Modifier.size(38.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(description, color = Muted, fontSize = 12.sp)
        }
    }
}

private fun TorrentDto.progressPercent() = ((progress * 100).toInt().coerceIn(0, 100)).toString() + "%"
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) { value /= 1024; index++ }
    return String.format(Locale.US, "%.1f %s", value, units[index])
}
