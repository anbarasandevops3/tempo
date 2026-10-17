package io.github.anbarasandevops3.tempo.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.anbarasandevops3.tempo.model.CueSettings
import io.github.anbarasandevops3.tempo.model.HistoryStats
import io.github.anbarasandevops3.tempo.model.TEMPLATES
import io.github.anbarasandevops3.tempo.model.Workout
import io.github.anbarasandevops3.tempo.model.compile
import io.github.anbarasandevops3.tempo.model.formatClock
import io.github.anbarasandevops3.tempo.model.newId
import io.github.anbarasandevops3.tempo.store.BackupException
import io.github.anbarasandevops3.tempo.store.SavedSession
import io.github.anbarasandevops3.tempo.store.Store
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStart: (Workout) -> Unit,
    onEdit: (Workout) -> Unit,
    onCreate: (Workout) -> Unit,
    onResume: (SavedSession) -> Unit,
    onHistory: () -> Unit,
) {
    val workouts by Store.workouts.collectAsState()
    val history by Store.history.collectAsState()
    var session by remember { mutableStateOf(Store.loadSession()) }
    var picking by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val stats = HistoryStats(history)
    val today = LocalDate.now()
    val context = LocalContext.current

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            context.contentResolver.openOutputStream(uri)!!.use { it.write(Store.exportBackup().toByteArray()) }
        }.onSuccess { Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Couldn't save the backup", Toast.LENGTH_LONG).show() }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val msg = try {
                val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
                val (w, h) = Store.importBackup(text)
                "Imported: $w workouts, $h history entries"
            } catch (e: BackupException) {
                e.message ?: "Import failed"
            } catch (e: Exception) {
                "Couldn't read that file"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Text("Tempo", fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onHistory, border = BorderStroke(1.dp, Palette.line)) { Text("History", color = Palette.text) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { picking = true }) { Text("+ New", fontWeight = FontWeight.SemiBold) }
                Column {
                    TextButton(onClick = { menu = true }) { Text("⋮", fontSize = 22.sp, color = Palette.text) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Export backup") }, onClick = {
                            menu = false; exporter.launch("tempo-backup-$today.json")
                        })
                        DropdownMenuItem(text = { Text("Import backup") }, onClick = {
                            menu = false; importer.launch(arrayOf("application/json", "text/plain", "*/*"))
                        })
                    }
                }
            }
        }

        val s = session
        val resumable = s?.let { ss -> workouts.firstOrNull { it.id == ss.workoutId } }
        if (s != null && resumable != null) item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Palette.card),
                border = BorderStroke(1.dp, Palette.accent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Unfinished: ${resumable.name}", fontWeight = FontWeight.SemiBold)
                    Text("Stopped at ${formatClock(s.position)}", color = Palette.muted, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            s.entryId?.let { id -> Store.finishStopped(id, Store.history.value.firstOrNull { it.id == id }?.doneSec ?: 0) }
                            Store.saveSession(null)
                            session = null
                        }) { Text("Discard", color = Palette.text) }
                        Button(onClick = { onResume(s) }) { Text("Resume") }
                    }
                }
            }
        }

        if (workouts.isEmpty()) item {
            Text("No workouts yet. Tap “+ New”.", color = Palette.muted, modifier = Modifier.fillMaxWidth().padding(24.dp))
        }

        items(workouts, key = { it.id }) { w ->
            val tl = compile(w)
            val count = tl.segments.count { !it.isReady }
            val last = stats.lastDone(w.id)
            Card(
                colors = CardDefaults.cardColors(containerColor = Palette.card),
                border = BorderStroke(1.dp, Palette.line),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { onStart(w) }.padding(16.dp)) {
                        Text(w.name, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                        Text("${formatClock(tl.total)} · $count ${if (count == 1) "interval" else "intervals"}", color = Palette.muted, fontSize = 14.sp)
                        if (last != null) Text("Last done ${stats.relativeDay(last, today)}", color = Palette.muted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onEdit(w) }) { Text("Edit", color = Palette.text) }
                }
            }
        }
    }

    if (picking) {
        ModalBottomSheet(onDismissRequest = { picking = false }, containerColor = Palette.bg) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("New workout", fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                item {
                    PickCard("Blank", "Build it step by step") {
                        picking = false
                        onCreate(Workout(newId(), "My workout", emptyList(), CueSettings()))
                    }
                }
                items(TEMPLATES) { t -> PickCard(t.name, t.blurb) { picking = false; onCreate(t.make()) } }
                item {
                    OutlinedButton(onClick = { picking = false }, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Palette.text)) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun PickCard(title: String, blurb: String, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Palette.card),
        border = BorderStroke(1.dp, Palette.line),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(blurb, color = Palette.muted, fontSize = 14.sp)
        }
    }
}
