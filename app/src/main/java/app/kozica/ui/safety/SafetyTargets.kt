package app.kozica.ui.safety

/**
 * Where each of the Safety screen's rows goes, per country (`docs/phase-3.md` § 2, checked against
 * each body's own site on 2026-10-22).
 *
 * **Code, not strings**: a translator must never be able to change a number or a URL, and
 * `SafetyTest` holds the numbers to the phase file's table. Names no Android type, so that test runs
 * on the JVM.
 */
internal data class RescueNumber(
    /** As the service publishes it, spaces included, because that is how the hint shows it. */
    val shown: String,
    val fromForeignPhone: Boolean = false,
) {
    val dialled: String get() = shown.filterNot(Char::isWhitespace)
}

internal data class RescueApp(
    val name: String,
    val packageId: String,
)

/** A page that opens in the browser; [issuer] is the body that publishes it, never translated. */
internal data class Page(
    val issuer: String,
    val url: String,
) {
    val host: String get() = url.substringAfter("://").substringBefore('/').removePrefix("www.")
}

internal val EMERGENCY = RescueNumber("112")

/**
 * Kotlin note: an enum whose entries carry data is a fixed, ordered table — the order here is the
 * order on the screen, and nothing picks an entry by position (ADR-0008's amendment of 2026-10-22).
 */
internal enum class Country(
    val rescue: List<RescueNumber>,
    val app: RescueApp,
    val avalanche: List<Page>,
    val weather: Page,
) {
    Poland(
        rescue = listOf(RescueNumber("985"), RescueNumber("+48 601 100 300", fromForeignPhone = true)),
        app = RescueApp("Ratunek", "com.pagasolutions.emergencycall_pl"),
        avalanche = listOf(Page("TOPR (Tatry)", "https://lawiny.topr.pl"), Page("GOPR", "https://lawiny.gopr.pl")),
        weather = Page("IMGW", "https://gory.imgw.pl"),
    ),
    Slovakia(
        rescue = listOf(RescueNumber("18 300")),
        app = RescueApp("Horská záchranná služba", "hzs.sk.hzs"),
        avalanche = listOf(Page("HZS", "https://www.laviny.sk")),
        weather = Page("SHMÚ (Tatry)", "https://www.shmu.sk/sk/?page=107"),
    ),
    Czechia(
        rescue = listOf(RescueNumber("+420 1210")),
        app = RescueApp("Záchranka", "com.medicalit.zachranka"),
        avalanche =
            listOf(
                Page(
                    "Horská služba (Karkonosze)",
                    "https://www.horskasluzba.cz/cz/pocasi-na-horach/lavinova-predpoved/krkonose",
                ),
            ),
        weather = Page("ČHMÚ", "https://www.chmi.cz/predpoved-pocasi/lyzarska-strediska-a-hory/"),
    ),
}
