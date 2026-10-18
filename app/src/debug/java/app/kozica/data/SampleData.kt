package app.kozica.data

/** Debug-only seeding seam, empty for now — see docs/phase-0.md § 2. */
object SampleData {
    suspend fun seed(repository: ItemRepository) = wipe(repository)

    suspend fun wipe(repository: ItemRepository) {
        for (item in repository.all()) repository.delete(item)
    }
}
