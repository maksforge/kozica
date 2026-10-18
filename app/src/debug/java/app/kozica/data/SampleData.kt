package app.kozica.data

/**
 * Rows to develop and screenshot against. Debug-only.
 *
 * **Deterministic on purpose** — the same titles in the same order every time. A seeder that
 * randomises produces a different screenshot on every run, which makes an image diff useless and
 * turns a screenshot suite from a check into decoration.
 */
object SampleData {
    // Seeds nothing until a phase has data of its own; the seam stays so the capture scripts and
    // SeedReceiver keep working unchanged (docs/phase-0.md § 2).
    suspend fun seed(repository: ItemRepository) = wipe(repository)

    suspend fun wipe(repository: ItemRepository) {
        for (item in repository.all()) repository.delete(item)
    }
}
