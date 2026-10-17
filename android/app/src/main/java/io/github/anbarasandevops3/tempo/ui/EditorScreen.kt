package io.github.anbarasandevops3.tempo.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.anbarasandevops3.tempo.model.CueSettings
import io.github.anbarasandevops3.tempo.model.Repeat
import io.github.anbarasandevops3.tempo.model.Step
import io.github.anbarasandevops3.tempo.model.StepKind
import io.github.anbarasandevops3.tempo.model.Workout
import io.github.anbarasandevops3.tempo.model.WorkoutNode
import io.github.anbarasandevops3.tempo.model.append
import io.github.anbarasandevops3.tempo.model.cloneWithNewIds
import io.github.anbarasandevops3.tempo.model.compile
import io.github.anbarasandevops3.tempo.model.formatClock
import io.github.anbarasandevops3.tempo.model.insertAfter
import io.github.anbarasandevops3.tempo.model.move
import io.github.anbarasandevops3.tempo.model.newId
import io.github.anbarasandevops3.tempo.model.parseDuration
import io.github.anbarasandevops3.tempo.model.remove
import io.github.anbarasandevops3.tempo.model.update

private class NodeOps(val setNodes: ((List<WorkoutNode>) -> List<WorkoutNode>) -> Unit) {
    fun update(id: String, fn: (WorkoutNode) -> WorkoutNode) = setNodes { it.update(id, fn) }
    fun remove(id: String) = setNodes { it.remove(id) }
    fun move(id: String, dir: Int) = setNodes { it.move(id, dir) }
    fun duplicate(node: WorkoutNode) = setNodes { it.insertAfter(node.id, node.cloneWithNewIds()) }
    fun addStep(parent: String?) = setNodes { it.append(parent, Step(newId(), StepKind.RUN, 60)) }
    fun addRepeat(parent: String?) = setNodes {
        it.append(parent, Repeat(newId(), 4, listOf(Step(newId(), StepKind.WORK, 30), Step(newId(), StepKind.REST, 30))))
    }
}

@Composable
fun EditorScreen(initial: Workout, isNew: Boolean, onSave: (Workout) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    var w by remember { mutableStateOf(initial) }
    var confirmDelete by remember { mutableStateOf(false) }
    val ops = NodeOps { fn -> w = w.copy(nodes = fn(w.nodes)) }
    fun setCues(fn: (CueSettings) -> CueSettings) { w = w.copy(cues = fn(w.cues)) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancel", color = Palette.text) }
            Text(formatClock(compile(w).total), color = Palette.muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Button(onClick = { onSave(w.copy(name = w.name.trim().ifEmpty { "Workout" }, updatedAt = System.currentTimeMillis())) }) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            BasicTextField(
                value = w.name, onValueChange = { w = w.copy(name = it) },
                textStyle = TextStyle(color = Palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold),
                cursorBrush = SolidColor(Palette.accent), singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            )
            NodeList(w.nodes, null, ops)

            SectionTitle("Cues")
            Column(Modifier.fillMaxWidth().background(Palette.card, RoundedCornerShape(14.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                ChoiceRow("Get ready", listOf(0, 5, 10, 15, 30), w.cues.getReady, { if (it == 0) "Off" else "${it}s" }) { v -> setCues { it.copy(getReady = v) } }
                ChoiceRow("Countdown beeps", listOf(0, 3, 5), w.cues.countdownBeeps, { if (it == 0) "Off" else "Last $it s" }) { v -> setCues { it.copy(countdownBeeps = v) } }
                ToggleRow("Say “Round 2 of 8”", w.cues.announceRounds) { v -> setCues { it.copy(announceRounds = v) } }
                ToggleRow("Say “Halfway” (steps ≥ 1 min)", w.cues.halfway) { v -> setCues { it.copy(halfway = v) } }
                ToggleRow("Say “Ten seconds” (steps ≥ 30 s)", w.cues.tenSeconds) { v -> setCues { it.copy(tenSeconds = v) } }
                ToggleRow("Say step names (“Burpees”)", w.cues.speakNames) { v -> setCues { it.copy(speakNames = v) } }
                ToggleRow("Vibrate", w.cues.vibrate) { v -> setCues { it.copy(vibrate = v) } }
            }

            if (!isNew) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    border = BorderStroke(1.dp, Palette.line)) { Text("Delete workout", color = Palette.danger) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${w.name}”?") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Palette.danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NodeList(nodes: List<WorkoutNode>, parent: String?, ops: NodeOps) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (n in nodes) {
            when (n) {
                is Step -> StepRow(n, ops)
                is Repeat -> Column(
                    Modifier.fillMaxWidth().border(1.dp, Palette.line, RoundedCornerShape(14.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Repeat", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Stepper("${n.times}×", { ops.update(n.id) { (it as Repeat).copy(times = (it.times - 1).coerceAtLeast(1)) } }) {
                            ops.update(n.id) { (it as Repeat).copy(times = (it.times + 1).coerceAtMost(99)) }
                        }
                        Spacer(Modifier.weight(1f))
                        NodeMenu(n, ops)
                    }
                    NodeList(n.children, n.id, ops)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                        ops.update(n.id) { (it as Repeat).copy(skipLastRest = !it.skipLastRest) }
                    }) {
                        Checkbox(checked = n.skipLastRest, onCheckedChange = { v -> ops.update(n.id) { (it as Repeat).copy(skipLastRest = v) } })
                        Text("Skip the last rest on the final round", color = Palette.muted, fontSize = 14.sp)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { ops.addStep(parent) }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Palette.line)) {
                Text("+ Step", color = Palette.muted)
            }
            OutlinedButton(onClick = { ops.addRepeat(parent) }, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, Palette.line)) {
                Text("+ Repeat", color = Palette.muted)
            }
        }
    }
}

@Composable
private fun StepRow(step: Step, ops: NodeOps) {
    val color = kindColor(step.kind)
    Row(Modifier.fillMaxWidth().background(Palette.card, RoundedCornerShape(14.dp))) {
        Box(Modifier.width(6.dp).height(96.dp).background(color, RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)))
        Column(Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KindPicker(step.kind) { k -> ops.update(step.id) { (it as Step).copy(kind = k) } }
                DurationField(step.seconds) { s -> ops.update(step.id) { (it as Step).copy(seconds = s) } }
                Spacer(Modifier.weight(1f))
                NodeMenu(step, ops)
            }
            BasicTextField(
                value = step.label ?: "",
                onValueChange = { v -> ops.update(step.id) { (it as Step).copy(label = v) } },
                textStyle = TextStyle(color = Palette.text, fontSize = 14.sp),
                cursorBrush = SolidColor(Palette.accent), singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                decorationBox = { inner ->
                    if (step.label.isNullOrEmpty()) Text("Name (optional, e.g. Burpees)", color = Palette.muted, fontSize = 14.sp)
                    inner()
                },
            )
        }
    }
}

@Composable
private fun KindPicker(kind: StepKind, onPick: (StepKind) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Text(kind.label + " ▾", color = kindColor(kind), fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
            modifier = Modifier.clickable { open = true }.padding(vertical = 8.dp).width(110.dp))
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            StepKind.entries.forEach { k ->
                DropdownMenuItem(text = { Text(k.label, color = kindColor(k)) }, onClick = { open = false; onPick(k) })
            }
        }
    }
}

@Composable
private fun DurationField(seconds: Int, onChange: (Int) -> Unit) {
    var text by remember(seconds) { mutableStateOf(formatClock(seconds)) }
    val stepBy = if (seconds >= 120) 30 else 5
    fun commit() {
        val s = parseDuration(text)
        if (s != null && s > 0) onChange(s.coerceAtMost(6 * 3600)) else text = formatClock(seconds)
    }
    Stepper(
        center = {
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = TextStyle(color = Palette.text, fontSize = 17.sp, textAlign = TextAlign.Center),
                cursorBrush = SolidColor(Palette.accent), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(60.dp).onFocusChanged { if (!it.isFocused) commit() },
            )
        },
        onMinus = { onChange((seconds - stepBy).coerceAtLeast(5)) },
        onPlus = { onChange(seconds + stepBy) },
    )
}

@Composable
private fun Stepper(value: String, onMinus: () -> Unit, onPlus: () -> Unit) =
    Stepper({ Text(value, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp), textAlign = TextAlign.Center) }, onMinus, onPlus)

@Composable
private fun Stepper(center: @Composable () -> Unit, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.background(Palette.bg, RoundedCornerShape(10.dp)), verticalAlignment = Alignment.CenterVertically) {
        SmallIcon("−", "Less", onMinus)
        center()
        SmallIcon("+", "More", onPlus)
    }
}

@Composable
private fun SmallIcon(symbol: String, label: String, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clickable(onClickLabel = label, onClick = onClick), contentAlignment = Alignment.Center) {
        Text(symbol, color = Palette.muted, fontSize = 20.sp)
    }
}

@Composable
private fun NodeMenu(node: WorkoutNode, ops: NodeOps) {
    var open by remember { mutableStateOf(false) }
    Box {
        SmallIcon("⋮", "Step options") { open = true }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Move up") }, onClick = { open = false; ops.move(node.id, -1) })
            DropdownMenuItem(text = { Text("Move down") }, onClick = { open = false; ops.move(node.id, 1) })
            DropdownMenuItem(text = { Text("Duplicate") }, onClick = { open = false; ops.duplicate(node) })
            DropdownMenuItem(text = { Text("Remove", color = Palette.danger) }, onClick = { open = false; ops.remove(node.id) })
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text.uppercase(), color = Palette.muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 8.dp))
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, value: T, format: (T) -> String, onPick: (T) -> Unit) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { o -> FilterChip(selected = o == value, onClick = { onPick(o) }, label = { Text(format(o)) }) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().height(52.dp).clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}
