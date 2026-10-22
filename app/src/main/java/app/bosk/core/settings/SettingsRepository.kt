package app.bosk.core.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** How a surfaced item is delivered during an ambient session. */
enum class DeliveryMode { TTS, OVERLAY, NOTIFICATION }

/** User-tunable ambient/voice settings (the Settings UI in CP5/CP6 edits these). */
data class AmbientPrefs(
    val enabled: Boolean = false,
    val intervalMinutes: Int = 60,        // free tier min 60, paid min 15 (gated in CP6)
    val quietStartHour: Int = 22,         // 22:00
    val quietEndHour: Int = 7,            // 07:00
    val delivery: DeliveryMode = DeliveryMode.NOTIFICATION,
    val ttsSpeed: Float = 1.0f,           // 0.75x–2.0x
    val voiceId: String? = null,
) {
    /** True if [now] (hour-of-day) falls inside the quiet window (handles wrap past midnight). */
    fun isQuietAt(hour: Int): Boolean =
        if (quietStartHour <= quietEndHour) hour in quietStartHour until quietEndHour
        else hour >= quietStartHour || hour < quietEndHour

    fun isQuietNow(): Boolean = isQuietAt(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
}

interface SettingsRepository {
    val prefs: Flow<AmbientPrefs>
    suspend fun update(transform: (AmbientPrefs) -> AmbientPrefs)

    val onboardingComplete: Flow<Boolean>
    suspend fun setOnboardingComplete(complete: Boolean)

    /** Accessibility: note body/title font size in sp (default 16). */
    val noteFontSp: Flow<Float>
    suspend fun setNoteFontSp(sp: Float)

    /** Graph: min zoom scale at which node name labels appear ("name render distance"). */
    val graphLabelMinScale: Flow<Float>
    suspend fun setGraphLabelMinScale(scale: Float)

    /** Graph: label text size multiplier (labels also scale with zoom; default 1.0). */
    val graphLabelSizeMult: Flow<Float>
    suspend fun setGraphLabelSizeMult(mult: Float)

    /** Graph: opening settle motion, 0 = open frozen … 1 = full reheat (default 0.35). */
    val graphSettleTemp: Flow<Float>
    suspend fun setGraphSettleTemp(temp: Float)

    /**
     * Graph: tag→colour groups for the ACTIVE preset (list order = priority, top wins). Reads/writes
     * route through whichever of the [GRAPH_GROUP_PRESET_COUNT] presets is currently selected.
     */
    val graphGroups: Flow<List<GraphGroup>>
    suspend fun setGraphGroups(groups: List<GraphGroup>)

    /** All saved colour-group presets (always [GRAPH_GROUP_PRESET_COUNT] of them). */
    val graphGroupPresets: Flow<List<List<GraphGroup>>>
    /** Index (0-based) of the active preset; default 0 (the first). */
    val graphActivePreset: Flow<Int>
    suspend fun setActivePreset(index: Int)

    /** Graph Overview synopsis arrangement: tiled by nodes, radial ring, side list, or bottom grid. */
    val graphOverviewLayout: Flow<OverviewLayout>
    suspend fun setGraphOverviewLayout(layout: OverviewLayout)

    /** Graph focus on open: a tag/group to focus ("" = everything). */
    val graphFocusTag: Flow<String>
    suspend fun setGraphFocusTag(tag: String)

    /** Whether the user has made the first-open focus choice (controls the chooser overlay). */
    val graphFocusChosen: Flow<Boolean>
    suspend fun setGraphFocusChosen(chosen: Boolean)

    /** Graph: on open, zoom in on & select the most-recently-focused note's node (default true). */
    val graphOpenZoomRecent: Flow<Boolean>
    suspend fun setGraphOpenZoomRecent(on: Boolean)

    /** The editable "command note": the slash-command reference that drives the palette + /help. */
    val commandNote: Flow<String>
    suspend fun setCommandNote(text: String)

    /** The customisable AI Partner persona, merged into every AI call's system instruction. */
    val persona: Flow<app.bosk.core.ai.Persona>
    suspend fun savePersona(persona: app.bosk.core.ai.Persona)

    /** The Compiler queue, encoded "noteId:withSummary;…" — so the staging area survives restarts. */
    val compilerQueue: Flow<String>
    suspend fun setCompilerQueue(encoded: String)

    /** Per-day review counts (epochDay → count, last ~120 days) — powers streak + activity chart. */
    val reviewLog: Flow<Map<Long, Int>>
    /** Increment the review counter for [epochDay] (called once per rated card). */
    suspend fun bumpReviewCount(epochDay: Long)

    /** The saved Daily Roadmap (JSON of [app.bosk.core.ai.DailyRoadmap]); "" if none. */
    val dailyRoadmapJson: Flow<String>
    /** The day the saved roadmap was generated (`LocalDate.toEpochDay()`); 0 if none. */
    val dailyRoadmapEpochDay: Flow<Long>
    /** Persist today's generated roadmap so Home shows it without regenerating. */
    suspend fun saveDailyRoadmap(json: String, epochDay: Long)
}

/** A graph "group": notes whose tags match [tag] render their node in [colorArgb]. */
data class GraphGroup(val tag: String, val colorArgb: Int)

/** How Overview-mode synopsis boxes are arranged on the graph screen. */
enum class OverviewLayout { TILED, RADIAL, LIST, GRID }

/** Number of saveable colour-group presets. */
const val GRAPH_GROUP_PRESET_COUNT = 5

/**
 * Default "name render distance": node names only draw once the graph is zoomed in past this
 * scale. Deliberately high so the default view is clean shapes, not a wall of text — zoom in
 * to read names (tunable in graph settings).
 */
const val DEFAULT_LABEL_MIN_SCALE = 1.2f

private val Context.settingsDataStore by preferencesDataStore(name = "bosk_settings")

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SettingsRepository {

    private object Keys {
        val ENABLED = booleanPreferencesKey("ambient_enabled")
        val INTERVAL = intPreferencesKey("ambient_interval_min")
        val QUIET_START = intPreferencesKey("quiet_start_hour")
        val QUIET_END = intPreferencesKey("quiet_end_hour")
        val DELIVERY = stringPreferencesKey("delivery_mode")
        val SPEED = floatPreferencesKey("tts_speed")
        val VOICE = stringPreferencesKey("voice_id")
        val ONBOARDED = booleanPreferencesKey("onboarding_complete")
        val NOTE_FONT_SP = floatPreferencesKey("note_font_sp")
        val GRAPH_LABEL_SCALE = floatPreferencesKey("graph_label_min_scale")
        val GRAPH_LABEL_MULT = floatPreferencesKey("graph_label_size_mult")
        val GRAPH_SETTLE_TEMP = floatPreferencesKey("graph_settle_temp")
        val GRAPH_GROUPS = stringPreferencesKey("graph_groups") // legacy (pre-presets) — migrated to preset 0
        val GRAPH_GROUP_PRESETS = stringPreferencesKey("graph_group_presets")
        val GRAPH_ACTIVE_PRESET = intPreferencesKey("graph_active_preset")
        val GRAPH_OVERVIEW_LIST = booleanPreferencesKey("graph_overview_list") // legacy — migrated into LAYOUT
        val GRAPH_OVERVIEW_LAYOUT = stringPreferencesKey("graph_overview_layout")
        val GRAPH_FOCUS_TAG = stringPreferencesKey("graph_focus_tag")
        val GRAPH_FOCUS_CHOSEN = booleanPreferencesKey("graph_focus_chosen")
        val GRAPH_OPEN_ZOOM_RECENT = booleanPreferencesKey("graph_open_zoom_recent")
        val DAILY_ROADMAP_JSON = stringPreferencesKey("daily_roadmap_json")
        val DAILY_ROADMAP_EPOCH_DAY = longPreferencesKey("daily_roadmap_epoch_day")
        val COMMAND_NOTE = stringPreferencesKey("command_note")
        val PERSONA_JSON = stringPreferencesKey("persona_json")
        val COMPILER_QUEUE = stringPreferencesKey("compiler_queue")
        val REVIEW_LOG = stringPreferencesKey("review_log")
    }

    override val compilerQueue: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.COMPILER_QUEUE] ?: "" }

    override suspend fun setCompilerQueue(encoded: String) {
        context.settingsDataStore.edit { it[Keys.COMPILER_QUEUE] = encoded }
    }

    override val reviewLog: Flow<Map<Long, Int>> =
        context.settingsDataStore.data.map { decodeReviewLog(it[Keys.REVIEW_LOG] ?: "") }

    override suspend fun bumpReviewCount(epochDay: Long) {
        context.settingsDataStore.edit { p ->
            val log = decodeReviewLog(p[Keys.REVIEW_LOG] ?: "").toMutableMap()
            log[epochDay] = (log[epochDay] ?: 0) + 1
            // Keep it bounded: only the most recent ~120 days.
            val pruned = log.entries.sortedByDescending { it.key }.take(120)
            p[Keys.REVIEW_LOG] = pruned.joinToString(";") { "${it.key}:${it.value}" }
        }
    }

    private fun decodeReviewLog(encoded: String): Map<Long, Int> =
        encoded.split(";").mapNotNull { part ->
            val bits = part.split(":")
            val day = bits.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
            val count = bits.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            day to count
        }.toMap()

    override val persona: Flow<app.bosk.core.ai.Persona> =
        context.settingsDataStore.data.map { app.bosk.core.ai.Persona.decode(it[Keys.PERSONA_JSON]) }

    override suspend fun savePersona(persona: app.bosk.core.ai.Persona) {
        context.settingsDataStore.edit { it[Keys.PERSONA_JSON] = app.bosk.core.ai.Persona.encode(persona) }
    }

    override val commandNote: Flow<String> =
        context.settingsDataStore.data.map {
            it[Keys.COMMAND_NOTE]?.takeIf { s -> s.isNotBlank() }
                ?: app.bosk.core.commands.CommandNote.DEFAULT
        }

    override suspend fun setCommandNote(text: String) {
        context.settingsDataStore.edit { it[Keys.COMMAND_NOTE] = text }
    }

    override val onboardingComplete: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.ONBOARDED] ?: false }

    override suspend fun setOnboardingComplete(complete: Boolean) {
        context.settingsDataStore.edit { it[Keys.ONBOARDED] = complete }
    }

    override val noteFontSp: Flow<Float> =
        context.settingsDataStore.data.map { (it[Keys.NOTE_FONT_SP] ?: 16f).coerceIn(10f, 32f) }

    override suspend fun setNoteFontSp(sp: Float) {
        context.settingsDataStore.edit { it[Keys.NOTE_FONT_SP] = sp.coerceIn(10f, 32f) }
    }

    override val graphLabelMinScale: Flow<Float> =
        context.settingsDataStore.data.map { (it[Keys.GRAPH_LABEL_SCALE] ?: DEFAULT_LABEL_MIN_SCALE).coerceIn(0.2f, 3f) }

    override suspend fun setGraphLabelMinScale(scale: Float) {
        context.settingsDataStore.edit { it[Keys.GRAPH_LABEL_SCALE] = scale.coerceIn(0.2f, 3f) }
    }

    override val graphLabelSizeMult: Flow<Float> =
        context.settingsDataStore.data.map { (it[Keys.GRAPH_LABEL_MULT] ?: 1f).coerceIn(0.6f, 1.6f) }

    override suspend fun setGraphLabelSizeMult(mult: Float) {
        context.settingsDataStore.edit { it[Keys.GRAPH_LABEL_MULT] = mult.coerceIn(0.6f, 1.6f) }
    }

    override val graphSettleTemp: Flow<Float> =
        context.settingsDataStore.data.map { (it[Keys.GRAPH_SETTLE_TEMP] ?: 0.35f).coerceIn(0f, 1f) }

    override suspend fun setGraphSettleTemp(temp: Float) {
        context.settingsDataStore.edit { it[Keys.GRAPH_SETTLE_TEMP] = temp.coerceIn(0f, 1f) }
    }

    override val graphGroups: Flow<List<GraphGroup>> =
        context.settingsDataStore.data.map { p ->
            val presets = decodePresets(p[Keys.GRAPH_GROUP_PRESETS], p[Keys.GRAPH_GROUPS])
            presets[(p[Keys.GRAPH_ACTIVE_PRESET] ?: 0).coerceIn(0, GRAPH_GROUP_PRESET_COUNT - 1)]
        }

    override suspend fun setGraphGroups(groups: List<GraphGroup>) {
        context.settingsDataStore.edit { p ->
            val presets = decodePresets(p[Keys.GRAPH_GROUP_PRESETS], p[Keys.GRAPH_GROUPS]).toMutableList()
            val active = (p[Keys.GRAPH_ACTIVE_PRESET] ?: 0).coerceIn(0, GRAPH_GROUP_PRESET_COUNT - 1)
            presets[active] = groups
            p[Keys.GRAPH_GROUP_PRESETS] = encodePresets(presets)
        }
    }

    override val graphGroupPresets: Flow<List<List<GraphGroup>>> =
        context.settingsDataStore.data.map { decodePresets(it[Keys.GRAPH_GROUP_PRESETS], it[Keys.GRAPH_GROUPS]) }

    override val graphActivePreset: Flow<Int> =
        context.settingsDataStore.data.map { (it[Keys.GRAPH_ACTIVE_PRESET] ?: 0).coerceIn(0, GRAPH_GROUP_PRESET_COUNT - 1) }

    override suspend fun setActivePreset(index: Int) {
        context.settingsDataStore.edit { it[Keys.GRAPH_ACTIVE_PRESET] = index.coerceIn(0, GRAPH_GROUP_PRESET_COUNT - 1) }
    }

    override val graphOverviewLayout: Flow<OverviewLayout> =
        context.settingsDataStore.data.map { p ->
            p[Keys.GRAPH_OVERVIEW_LAYOUT]?.let { s -> runCatching { OverviewLayout.valueOf(s) }.getOrNull() }
                // Fall back to the legacy boolean so an existing "List" choice survives the upgrade.
                ?: if (p[Keys.GRAPH_OVERVIEW_LIST] == true) OverviewLayout.LIST else OverviewLayout.TILED
        }

    override suspend fun setGraphOverviewLayout(layout: OverviewLayout) {
        context.settingsDataStore.edit { it[Keys.GRAPH_OVERVIEW_LAYOUT] = layout.name }
    }

    override val graphFocusTag: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.GRAPH_FOCUS_TAG] ?: "" }

    override suspend fun setGraphFocusTag(tag: String) {
        context.settingsDataStore.edit { it[Keys.GRAPH_FOCUS_TAG] = tag }
    }

    override val graphFocusChosen: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.GRAPH_FOCUS_CHOSEN] ?: false }

    override suspend fun setGraphFocusChosen(chosen: Boolean) {
        context.settingsDataStore.edit { it[Keys.GRAPH_FOCUS_CHOSEN] = chosen }
    }

    override val graphOpenZoomRecent: Flow<Boolean> =
        context.settingsDataStore.data.map { it[Keys.GRAPH_OPEN_ZOOM_RECENT] ?: true }

    override suspend fun setGraphOpenZoomRecent(on: Boolean) {
        context.settingsDataStore.edit { it[Keys.GRAPH_OPEN_ZOOM_RECENT] = on }
    }

    override val dailyRoadmapJson: Flow<String> =
        context.settingsDataStore.data.map { it[Keys.DAILY_ROADMAP_JSON] ?: "" }

    override val dailyRoadmapEpochDay: Flow<Long> =
        context.settingsDataStore.data.map { it[Keys.DAILY_ROADMAP_EPOCH_DAY] ?: 0L }

    override suspend fun saveDailyRoadmap(json: String, epochDay: Long) {
        context.settingsDataStore.edit {
            it[Keys.DAILY_ROADMAP_JSON] = json
            it[Keys.DAILY_ROADMAP_EPOCH_DAY] = epochDay
        }
    }

    override val prefs: Flow<AmbientPrefs> = context.settingsDataStore.data.map { p ->
        val default = AmbientPrefs()
        AmbientPrefs(
            enabled = p[Keys.ENABLED] ?: default.enabled,
            intervalMinutes = p[Keys.INTERVAL] ?: default.intervalMinutes,
            quietStartHour = p[Keys.QUIET_START] ?: default.quietStartHour,
            quietEndHour = p[Keys.QUIET_END] ?: default.quietEndHour,
            delivery = p[Keys.DELIVERY]?.let { runCatching { DeliveryMode.valueOf(it) }.getOrNull() }
                ?: default.delivery,
            ttsSpeed = p[Keys.SPEED] ?: default.ttsSpeed,
            voiceId = p[Keys.VOICE] ?: default.voiceId,
        )
    }

    override suspend fun update(transform: (AmbientPrefs) -> AmbientPrefs) {
        context.settingsDataStore.edit { p ->
            // Read current, apply transform, write back.
            val current = AmbientPrefs(
                enabled = p[Keys.ENABLED] ?: AmbientPrefs().enabled,
                intervalMinutes = p[Keys.INTERVAL] ?: AmbientPrefs().intervalMinutes,
                quietStartHour = p[Keys.QUIET_START] ?: AmbientPrefs().quietStartHour,
                quietEndHour = p[Keys.QUIET_END] ?: AmbientPrefs().quietEndHour,
                delivery = p[Keys.DELIVERY]?.let { runCatching { DeliveryMode.valueOf(it) }.getOrNull() }
                    ?: AmbientPrefs().delivery,
                ttsSpeed = p[Keys.SPEED] ?: AmbientPrefs().ttsSpeed,
                voiceId = p[Keys.VOICE] ?: AmbientPrefs().voiceId,
            )
            val next = transform(current)
            p[Keys.ENABLED] = next.enabled
            p[Keys.INTERVAL] = next.intervalMinutes
            p[Keys.QUIET_START] = next.quietStartHour
            p[Keys.QUIET_END] = next.quietEndHour
            p[Keys.DELIVERY] = next.delivery.name
            p[Keys.SPEED] = next.ttsSpeed
            next.voiceId?.let { p[Keys.VOICE] = it }
        }
    }
}

// Groups serialise as "tag|argb" entries joined by ';' (tags can't contain | or ;).
private fun encodeGroups(groups: List<GraphGroup>): String =
    groups.joinToString(";") { "${it.tag.replace("|", "").replace(";", "")}|${it.colorArgb}" }

private fun decodeGroups(raw: String?): List<GraphGroup> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(";").mapNotNull { entry ->
        val parts = entry.split("|")
        if (parts.size != 2) return@mapNotNull null
        val tag = parts[0].trim()
        val argb = parts[1].toIntOrNull() ?: return@mapNotNull null
        if (tag.isEmpty()) null else GraphGroup(tag, argb)
    }
}

// Presets serialise as GRAPH_GROUP_PRESET_COUNT group-lists, one per line (tags can't contain '\n').
private fun encodePresets(presets: List<List<GraphGroup>>): String =
    (0 until GRAPH_GROUP_PRESET_COUNT).joinToString("\n") { encodeGroups(presets.getOrElse(it) { emptyList() }) }

private fun decodePresets(raw: String?, legacy: String?): List<List<GraphGroup>> {
    if (raw.isNullOrEmpty()) {
        // First run after the presets upgrade: migrate the old single group list into preset 0.
        val migrated = decodeGroups(legacy)
        return List(GRAPH_GROUP_PRESET_COUNT) { if (it == 0) migrated else emptyList() }
    }
    val parts = raw.split("\n")
    return List(GRAPH_GROUP_PRESET_COUNT) { decodeGroups(parts.getOrNull(it)) }
}
