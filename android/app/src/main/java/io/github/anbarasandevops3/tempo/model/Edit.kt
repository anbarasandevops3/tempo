package io.github.anbarasandevops3.tempo.model

// Mirrors src/model/edit.ts: immutable edits on the workout tree, addressed by node id.

fun List<WorkoutNode>.update(id: String, fn: (WorkoutNode) -> WorkoutNode): List<WorkoutNode> = map {
    when {
        it.id == id -> fn(it)
        it is Repeat -> it.copy(children = it.children.update(id, fn))
        else -> it
    }
}

fun List<WorkoutNode>.remove(id: String): List<WorkoutNode> =
    filter { it.id != id }.map { if (it is Repeat) it.copy(children = it.children.remove(id)) else it }

fun List<WorkoutNode>.move(id: String, dir: Int): List<WorkoutNode> {
    val i = indexOfFirst { it.id == id }
    if (i >= 0) {
        val j = i + dir
        if (j !in indices) return this
        return toMutableList().also { val tmp = it[i]; it[i] = it[j]; it[j] = tmp }
    }
    return map { if (it is Repeat) it.copy(children = it.children.move(id, dir)) else it }
}

/** Append to the repeat with id [parentId], or to the top level when it is null. */
fun List<WorkoutNode>.append(parentId: String?, node: WorkoutNode): List<WorkoutNode> =
    if (parentId == null) this + node
    else update(parentId) { if (it is Repeat) it.copy(children = it.children + node) else it }

fun List<WorkoutNode>.insertAfter(id: String, node: WorkoutNode): List<WorkoutNode> {
    val i = indexOfFirst { it.id == id }
    if (i >= 0) return toMutableList().also { it.add(i + 1, node) }
    return map { if (it is Repeat) it.copy(children = it.children.insertAfter(id, node)) else it }
}

/** Deep copy with fresh ids. */
fun WorkoutNode.cloneWithNewIds(): WorkoutNode = when (this) {
    is Step -> copy(id = newId())
    is Repeat -> copy(id = newId(), children = children.map { it.cloneWithNewIds() })
}

/** "1:30" / "90" / "1m30" -> seconds, or null if unparseable. */
fun parseDuration(text: String): Int? {
    val t = text.trim()
    Regex("""^(\d+):(\d{1,2})$""").find(t)?.let { return it.groupValues[1].toInt() * 60 + it.groupValues[2].toInt() }
    Regex("""^(\d+)\s*m(?:in)?\s*(\d+)?\s*s?$""", RegexOption.IGNORE_CASE).find(t)?.let {
        return it.groupValues[1].toInt() * 60 + (it.groupValues[2].toIntOrNull() ?: 0)
    }
    Regex("""^(\d+)\s*s?$""", RegexOption.IGNORE_CASE).find(t)?.let { return it.groupValues[1].toInt() }
    return null
}
