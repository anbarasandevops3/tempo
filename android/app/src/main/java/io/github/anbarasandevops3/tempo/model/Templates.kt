package io.github.anbarasandevops3.tempo.model

// Mirrors src/model/templates.ts.

private fun step(kind: StepKind, seconds: Int, label: String? = null) = Step(newId(), kind, seconds, label)
private fun repeat(times: Int, children: List<WorkoutNode>, skipLastRest: Boolean = false) =
    Repeat(newId(), times, children, skipLastRest)
private fun workout(name: String, nodes: List<WorkoutNode>) = Workout(newId(), name, nodes)

data class Template(val name: String, val blurb: String, val make: () -> Workout)

val TEMPLATES = listOf(
    Template("Run / walk intervals", "5 min warm-up, 6 × (1 min run, 1:30 walk), 5 min cool-down") {
        workout("Run / walk intervals", listOf(
            step(StepKind.WARMUP, 300),
            repeat(6, listOf(step(StepKind.RUN, 60), step(StepKind.WALK, 90))),
            step(StepKind.COOLDOWN, 300),
        ))
    },
    Template("Tabata", "8 × (20 s work, 10 s rest)") {
        workout("Tabata", listOf(repeat(8, listOf(step(StepKind.WORK, 20), step(StepKind.REST, 10)), true)))
    },
    Template("Dynamic warm-up", "6 moves, 40 s each, 5 min") {
        workout("Dynamic warm-up", listOf(
            step(StepKind.JOG, 60, "Easy jog"),
            step(StepKind.WORK, 40, "Leg swings"),
            step(StepKind.WORK, 40, "Walking lunges"),
            step(StepKind.WORK, 40, "High knees"),
            step(StepKind.WORK, 40, "Butt kicks"),
            step(StepKind.STRETCH, 40, "Hip openers"),
            step(StepKind.SPRINT, 40, "Strides"),
        ))
    },
    Template("Sprint intervals", "10 min jog, 8 × (30 s sprint, 90 s recover), 10 min cool-down") {
        workout("Sprint intervals", listOf(
            step(StepKind.JOG, 600, "Warm-up jog"),
            repeat(8, listOf(step(StepKind.SPRINT, 30), step(StepKind.RECOVER, 90)), true),
            step(StepKind.COOLDOWN, 600),
        ))
    },
    Template("HIIT 30/30", "10 × (30 s work, 30 s rest)") {
        workout("HIIT 30/30", listOf(repeat(10, listOf(step(StepKind.WORK, 30), step(StepKind.REST, 30)), true)))
    },
    Template("HIIT circuit", "3 rounds × 4 moves (40 s on, 20 s off), 1 min between rounds") {
        workout("HIIT circuit", listOf(
            repeat(3, listOf(
                repeat(4, listOf(step(StepKind.WORK, 40), step(StepKind.REST, 20)), true),
                step(StepKind.RECOVER, 60),
            ), true),
        ))
    },
)
