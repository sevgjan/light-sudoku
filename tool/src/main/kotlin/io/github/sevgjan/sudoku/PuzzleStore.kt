package io.github.sevgjan.sudoku

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class PuzzleStore(private val dataStore: DataStore<Preferences>) {
    suspend fun loadGame(day: Long): Game? = dataStore.data.first()[GAME]
        ?.let { runCatching { Json.decodeFromString(Game.serializer(), it) }.getOrNull() }
        ?.takeIf { it.day == day }

    suspend fun saveGame(game: Game) {
        dataStore.edit { it[GAME] = Json.encodeToString(Game.serializer(), game) }
    }

    /** The replay or practice round in progress, kept apart so the daily result is never overwritten. */
    suspend fun loadExtra(day: Long): Game? = dataStore.data.first()[EXTRA]
        ?.let { runCatching { Json.decodeFromString(Game.serializer(), it) }.getOrNull() }
        ?.takeIf { it.day == day }

    suspend fun saveExtra(game: Game) {
        dataStore.edit { it[EXTRA] = Json.encodeToString(Game.serializer(), game) }
    }

    suspend fun clearExtra() {
        dataStore.edit { it.remove(EXTRA) }
    }

    suspend fun save(game: Game) = if (game.mode == Mode.DAILY) saveGame(game) else saveExtra(game)

    suspend fun loadStats(): Stats = dataStore.data.first()[STATS]
        ?.let { runCatching { Json.decodeFromString(Stats.serializer(), it) }.getOrNull() } ?: Stats()

    suspend fun saveStats(stats: Stats) {
        dataStore.edit { it[STATS] = Json.encodeToString(Stats.serializer(), stats) }
    }

    private companion object {
        val GAME = stringPreferencesKey("game")
        val STATS = stringPreferencesKey("stats")
        val EXTRA = stringPreferencesKey("extra")
    }
}
