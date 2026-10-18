/*
 * Velune - by Nikhil
 * Nikhil
 * Licensed Under GPL-3.0
 */

package com.nikhil.yt.campaign

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * The campaign delivery curve — the shape a campaign's streams are expected
 * to arrive in over its lifetime.
 *
 * This mirrors `public.seed_daily_stream_plan()` in the database, which is
 * the one place the estimate is defined. The card animates along this curve
 * so the figure it shows climbs the way the campaign is actually expected
 * to deliver: fast while the promotion ramps, peaking around
 * [peakPosition], then easing toward [tailFraction] of the peak by the end.
 * A fixed tween would climb at a constant rate and describe nothing.
 *
 * Values come from `simulation_engine_config`, which is publicly readable,
 * so the client never hardcodes them. [DEFAULT] exists only so a card can
 * render before the config arrives; it is the config's own seeded values.
 */
data class CampaignDeliveryCurve(
    val peakPosition: Double,
    val tailFraction: Double,
    val wobbleDepth: Double,
    val wobbleFrequency: Double,
    val planDailyMin: Long,
    val totalFillRatio: Double,
) {
    companion object {
        val DEFAULT = CampaignDeliveryCurve(
            peakPosition = 0.35,
            tailFraction = 0.15,
            wobbleDepth = 0.30,
            wobbleFrequency = 0.7,
            planDailyMin = 1L,
            totalFillRatio = 1.0,
        )
    }
}

/**
 * The per-day delivery plan for a campaign, in the same units and order as
 * the server's own plan.
 *
 * Kept as a whole array rather than evaluated day-by-day on demand because
 * each day's share is normalized against the sum of every day's share —
 * `shape / shape_sum` in the SQL. A day cannot be known without knowing
 * all of them.
 */
fun campaignDailyPlan(
    targetViews: Long,
    durationDays: Int,
    curve: CampaignDeliveryCurve = CampaignDeliveryCurve.DEFAULT,
): LongArray {
    val duration = max(1, durationDays)
    val target = max(0L, targetViews)

    // Shape, before normalization. `u` is progress through the campaign on
    // both arms of the ramp. The SQL comments record why: testing
    // `dayIndex <= duration * peak` while dividing by `dayIndex + 1` ran the
    // rising arm past 1.0 before switching arms, which jumped the curve and
    // put the peak 50% above its intended height. One progress variable for
    // both arms keeps it monotone.
    val shapes = DoubleArray(duration)
    for (dayIndex in 0 until duration) {
        val u = (dayIndex + 1).toDouble() / duration
        val ramp = if (u <= curve.peakPosition) {
            u / max(0.0001, curve.peakPosition)
        } else {
            1.0 - ((u - curve.peakPosition) / max(0.0001, 1.0 - curve.peakPosition)) *
                (1.0 - curve.tailFraction)
        }
        val wobble = 1.0 - curve.wobbleDepth +
            2.0 * curve.wobbleDepth *
            (0.5 + 0.5 * sin((dayIndex + 1).toDouble() * curve.wobbleFrequency))
        shapes[dayIndex] = max(0.01, ramp * wobble)
    }

    val shapeSum = shapes.sum()
    if (shapeSum <= 0.0) return LongArray(duration) { curve.planDailyMin }

    return LongArray(duration) { dayIndex ->
        val share = target * curve.totalFillRatio * (shapes[dayIndex] / shapeSum)
        max(curve.planDailyMin, share.roundToLong())
    }
}

/**
 * The campaign fields the estimate is computed from — the same fields the
 * server's `seed_daily_stream_plan()` reads off `track_campaigns`.
 *
 * @param targetViews the target the campaign was sold.
 * @param durationDays how long it runs. Zero or null means the server's own
 *   fallback applies (30 days), so [resolvedDurationDays] is what callers
 *   should use.
 * @param startIso ISO-8601 timestamp the run starts from.
 */
data class CampaignEstimateInputs(
    val targetViews: Long,
    val durationDays: Int,
    val startIso: String,
) {
    /**
     * The duration the plan is actually built over. The server falls back
     * to 30 when `estimated_duration_days` is null or zero; matching that
     * here means the client's curve and the server's plan cover the same
     * span, so the card cannot show a campaign finishing before the
     * database does.
     */
    val resolvedDurationDays: Int
        get() = if (durationDays <= 0) 30 else durationDays
}

/**
 * Milliseconds since epoch for an ISO-8601 timestamp, or `null` if it does
 * not parse.
 *
 * The API returns fractional seconds and a `Z`, which the plain
 * `SimpleDateFormat` patterns in this app do not accept, so the fraction is
 * trimmed first. Returning null rather than a default matters: a campaign
 * with an unreadable start date has no position on its curve, and showing
 * one from a guessed epoch would invent progress that has not happened.
 */
fun parseIsoMillis(iso: String): Long? {
    if (iso.isBlank()) return null
    val normalized = iso
        .substringBefore('+')
        .removeSuffix("Z")
        .substringBefore('.')
    return try {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        format.parse(normalized)?.time
    } catch (e: Exception) {
        null
    }
}

/**
 * How many streams the campaign is expected to have gathered [elapsedDays]
 * into its run.
 *
 * Fractional days matter: a campaign is on screen between whole days, and
 * without the fraction the figure would sit still and then jump once a day
 * rather than climb. The fraction is applied to the current day's own
 * allowance, so the climb is fastest on the days the curve says are the
 * campaign's busiest.
 *
 * Past the end of the plan the total is simply the whole plan, so a
 * finished campaign holds at its final figure instead of climbing forever.
 */
fun campaignProjectedStreams(
    plan: LongArray,
    elapsedDays: Double,
): Long {
    if (plan.isEmpty()) return 0L
    val clamped = max(0.0, elapsedDays)
    val wholeDays = floor(clamped).toInt()
    if (wholeDays >= plan.size) return plan.sum()

    var total = 0L
    for (dayIndex in 0 until wholeDays) total += plan[dayIndex]

    val fraction = clamped - wholeDays
    total += (plan[wholeDays] * fraction).roundToLong()
    return total
}
