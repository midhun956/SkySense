package com.skysense.app.domain

import com.skysense.app.data.model.*
import java.text.SimpleDateFormat
import java.util.*

data class RecordedEpoch(
    val timestampMs: Long,
    val clock: RawClockData,
    val measurements: List<RawMeasurementData>,
    val snapshot: GnssSnapshot
)

/**
 * Buffers live GNSS streams and formats exports in CSV, JSON, NMEA, GPX, and RINEX 3.04.
 */
class GnssDataRecorder {

    val epochs = mutableListOf<RecordedEpoch>()
    val nmeaLines = mutableListOf<NmeaMessageData>()
    val navMessages = mutableListOf<NavMessageData>()
    var startTimeMs: Long = 0L
        private set
    var hardwareReport: HardwareCapabilitiesReport = HardwareCapabilitiesReport()

    fun reset(capabilities: HardwareCapabilitiesReport) {
        epochs.clear()
        nmeaLines.clear()
        navMessages.clear()
        startTimeMs = System.currentTimeMillis()
        hardwareReport = capabilities
    }

    fun totalMeasurementCount(): Int = epochs.sumOf { it.measurements.size }

    fun estimatedSizeKb(): Int {
        val measBytes = totalMeasurementCount() * 140
        val nmeaBytes = nmeaLines.sumOf { it.sentence.length + 2 }
        val navBytes = navMessages.sumOf { it.dataHex.length + 40 }
        return ((measBytes + nmeaBytes + navBytes) / 1024).coerceAtLeast(1)
    }

    fun exportCsv(): String {
        val sb = StringBuilder()
        sb.appendLine("# SkySense Raw GNSS Export | Model: ${hardwareReport.hardwareModel} | Year: ${hardwareReport.hardwareYear}")
        sb.appendLine("timestamp_ms,lat,lon,alt_wgs84,h_acc_m,time_nanos,full_bias_nanos,bias_nanos,svid,constellation,band,cn0_dbhz,pr_rate_mps,adr_m,adr_state,state")
        for (ep in epochs) {
            for (m in ep.measurements) {
                sb.appendLine(
                    "${ep.timestampMs},${ep.snapshot.latitude},${ep.snapshot.longitude},${ep.snapshot.altitude}," +
                        "${ep.snapshot.horizontalAccuracy},${ep.clock.timeNanos},${ep.clock.fullBiasNanos},${ep.clock.biasNanos}," +
                        "${m.svid},${m.constellation.shortName},${m.frequencyBandLabel},${m.cn0DbHz}," +
                        "${m.pseudorangeRateMetersPerSecond},${m.accumulatedDeltaRangeMeters},${m.accumulatedDeltaRangeState},${m.state}"
                )
            }
        }
        return sb.toString()
    }

    fun exportNmea(): String = nmeaLines.joinToString("\n") { it.sentence }

    fun exportGpx(): String {
        val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<gpx version="1.1" creator="SkySense v2.0.0" xmlns="http://www.topografix.com/GPX/1/1">""")
        sb.appendLine("  <trk><name>SkySense GNSS Track</name><trkseg>")
        epochs.map { it.snapshot }.filter { it.hasValidFix }.distinctBy { it.timestamp }.forEach { s ->
            sb.appendLine("""    <trkpt lat="${s.latitude}" lon="${s.longitude}"><ele>${s.altitude}</ele><time>${isoFmt.format(Date(s.timestamp))}</time></trkpt>""")
        }
        sb.appendLine("  </trkseg></trk></gpx>")
        return sb.toString()
    }

    fun exportJson(): String {
        val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
        val payload = mapOf(
            "metadata" to hardwareReport,
            "startTimeMs" to startTimeMs,
            "epochCount" to epochs.size,
            "epochs" to epochs,
            "nmeaCount" to nmeaLines.size,
            "navMessageCount" to navMessages.size
        )
        return gson.toJson(payload)
    }

    fun exportRinex304(): String {
        val sb = StringBuilder()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        if (epochs.isNotEmpty()) cal.timeInMillis = epochs.first().timestampMs

        sb.appendLine("     3.04           OBSERVATION DATA    M (MIXED)           RINEX VERSION / TYPE")
        sb.appendLine("%-20s%-20s%-20sPGM / RUN BY / DATE".format(Locale.US, "SkySense 2.0", "Android GNSS", SimpleDateFormat("yyyyMMdd HHmmss 'UTC'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())))
        sb.appendLine("%-60sMARKER NAME".format(Locale.US, "SKYSENSE"))
        sb.appendLine("%-20s%-40sREC # / TYPE / VERS".format(Locale.US, "0001", hardwareReport.hardwareModel.take(40)))
        sb.appendLine("G    4 C1C D1C S1C L1C                                      SYS / # / OBS TYPES")
        sb.appendLine("E    4 C1C D1C S1C L1C                                      SYS / # / OBS TYPES")
        sb.appendLine("R    4 C1C D1C S1C L1C                                      SYS / # / OBS TYPES")
        sb.appendLine("C    4 C2I D2I S2I L2I                                      SYS / # / OBS TYPES")
        sb.appendLine("                                                            END OF HEADER")

        for (ep in epochs) {
            val c = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = ep.timestampMs }
            val sec = c.get(Calendar.SECOND) + c.get(Calendar.MILLISECOND) / 1000.0
            val validMeas = ep.measurements.mapNotNull { m ->
                val pr = GnssMath.computePseudorangeMeters(m, ep.clock) ?: return@mapNotNull null
                val sys = when (m.constellation) {
                    ConstellationType.GPS -> "G"
                    ConstellationType.GLONASS -> "R"
                    ConstellationType.GALILEO -> "E"
                    ConstellationType.BEIDOU -> "C"
                    ConstellationType.QZSS -> "J"
                    ConstellationType.IRNSS -> "I"
                    else -> return@mapNotNull null
                }
                Triple(sys, m, pr)
            }
            if (validMeas.isEmpty()) continue
            sb.appendLine(
                "> %04d %02d %02d %02d %02d %10.7f  0 %2d".format(
                    Locale.US,
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
                    c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), sec, validMeas.size
                )
            )
            for ((sys, m, pr) in validMeas) {
                val doppler = GnssMath.computeDopplerShiftHz(m.pseudorangeRateMetersPerSecond, m.carrierFrequencyHz) ?: 0.0
                val wl = if (m.carrierFrequencyHz > 0f) GnssMath.SPEED_OF_LIGHT_MPS / m.carrierFrequencyHz else 0.19029367
                val cycles = m.accumulatedDeltaRangeMeters / wl
                sb.appendLine(
                    "%s%02d%14.3f  %14.3f  %14.3f  %14.3f".format(
                        Locale.US, sys, m.svid.coerceIn(1, 99), pr, doppler, m.cn0DbHz, cycles
                    )
                )
            }
        }
        return sb.toString()
    }
}
