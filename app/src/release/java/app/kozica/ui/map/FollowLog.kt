package app.kozica.ui.map

import app.kozica.data.hikes.Pace
import app.kozica.data.routes.FollowFix

/** The release half of phase 6b's following log: nothing. */
@Suppress("UNUSED_PARAMETER")
fun logFollow(
    fix: FollowFix,
    metres: Double,
    reach: Double,
    following: FollowingUiState?,
    note: FollowingNote?,
    pace: Pace?,
) = Unit
