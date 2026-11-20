package com.veltusstudio.veltpulse.data.generator

import com.veltusstudio.veltpulse.data.model.Track
import com.veltusstudio.veltpulse.data.remote.LastFmApi
import com.veltusstudio.veltpulse.data.remote.LastFmTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class LastWaveMode(
    val title: String,
    val subtitle: String,
    val iconName: String
) {
    TOP_TRACKS("Top Tracks", "Your most-played tracks of all time", "trending"),
    RECENT_TRACKS("Recent Tracks", "What you've been listening to lately", "history"),
    SIMILAR_TRACKS("Similar Tracks", "Tracks similar to one you love", "track_similarity"),
    SIMILAR_ARTISTS("Similar Artists", "Discover artists like your favourites", "artist_similarity"),
    BY_TAG("By Tag / Genre", "Browse by genre — rock, lo-fi, jazz, indie", "tag"),
    MY_MIX("My Mix", "Smart blend of top, recent & similar", "mix"),
    RECOMMENDATIONS("My Recommendations", "Fresh tracks picked just for you", "recommendations"),
    MY_LIBRARY("My Library", "Re-discover the sounds of your past", "library")
}

data class GeneratedPlaylist(
    val title: String,
    val subtitle: String,
    val mode: LastWaveMode,
    val tracks: List<Track>,
    val timestamp: Long = System.currentTimeMillis()
)

class LastWaveEngine(private val lfmApi: LastFmApi) {

    private val adjectives = listOf(
        "Crimson","Violet","Amber","Emerald","Sapphire","Scarlet","Ivory","Obsidian",
        "Cobalt","Copper","Golden","Silver","Jade","Ruby","Indigo","Pearl",
        "Electric","Radiant","Frozen","Stormy","Misty","Blazing","Frosted","Luminous",
        "Fading","Glowing","Shimmering","Windswept","Sunlit","Moonlit","Hazy","Rainswept",
        "Silent","Restless","Wild","Faded","Quiet","Wistful","Feral","Serene",
        "Fractured","Wandering","Distant","Hollow","Tender","Bold","Gentle","Dreamy",
        "Midnight","Lunar","Solar","Arctic","Celestial","Nocturnal","Endless","Ancient",
        "Eternal","Timeless","Drifting","Hidden","Velvet","Neon","Glass","Forgotten"
    )

    private val nouns = listOf(
        "Horizon","Orbit","Nebula","Eclipse","Comet","Nova","Zenith","Meridian",
        "Solstice","Constellation","Cosmos","Galaxy","Halo","Aurora","Starlight","Satellite",
        "Tides","Storm","Tempest","Cascade","Glacier","Canyon","Meadow","Grove",
        "Summit","Valley","Wildfire","Monsoon","Frost","Ember","Bloom","Thicket",
        "Harbor","Lagoon","Prairie","Oasis","Phoenix","Oracle","Siren","Valkyrie",
        "Elysium","Odyssey","Atlas","Titan","Muse","Chimera","Echo","Echoes",
        "Mirage","Dreams","Reverie","Pulse","Drift","Wanderlust","Shadows","Sparks",
        "Voyage","Whisper","Serenade","Solitude","Skyline","Twilight","Daybreak",
        "Sanctuary","Labyrinth","Wilderness"
    )

    fun generateUniqueName(existingTitles: Set<String> = emptySet()): String {
        for (attempt in 0 until 50) {
            val r = Math.random()
            val candidate = when {
                r < 0.12 -> nouns.random()
                r < 0.90 -> "${adjectives.random()} ${nouns.random()}"
                else -> "${adjectives.random()} ${adjectives.random()} ${nouns.random()}"
            }
            if (candidate.lowercase() !in existingTitles.map { it.lowercase() }) {
                return candidate
            }
        }
        return "${adjectives.random()} ${nouns.random()}"
    }

    suspend fun generatePlaylist(
        mode: LastWaveMode,
        limit: Int = 25,
        period: String = "overall",
        seedTrack: String = "",
        seedArtist: String = "",
        tag: String = "",
        existingTitles: Set<String> = emptySet()
    ): GeneratedPlaylist = withContext(Dispatchers.IO) {
        val tracks: List<LastFmTrack> = when (mode) {
            LastWaveMode.TOP_TRACKS -> {
                lfmApi.getTopTracks(period = period, limit = limit * 2).shuffled().take(limit)
            }
            LastWaveMode.RECENT_TRACKS -> {
                lfmApi.getRecentTracks(limit = limit * 2).filterNot { it.isNowPlaying }.distinctBy { "${it.name}|${it.artist}".lowercase() }.shuffled().take(limit)
            }
            LastWaveMode.SIMILAR_TRACKS -> {
                val t = seedTrack.ifBlank { "Radiohead" }
                val a = seedArtist.ifBlank { "Creep" }
                lfmApi.getSimilarTracks(track = t, artist = a, limit = limit)
            }
            LastWaveMode.SIMILAR_ARTISTS -> {
                val a = seedArtist.ifBlank { "Daft Punk" }
                val similar = lfmApi.getSimilarArtists(a, limit = 6)
                val collected = mutableListOf<LastFmTrack>()
                similar.forEach { simArtist ->
                    val artTracks = lfmApi.getArtistTopTracks(simArtist, limit = 5)
                    collected.addAll(artTracks)
                }
                collected.shuffled().distinctBy { "${it.name}|${it.artist}".lowercase() }.take(limit)
            }
            LastWaveMode.BY_TAG -> {
                val genreTag = tag.ifBlank { "Indie" }
                lfmApi.getTagTopTracks(tag = genreTag, limit = limit * 2).shuffled().take(limit)
            }
            LastWaveMode.MY_MIX -> {
                val top = lfmApi.getTopTracks(period = "1month", limit = 15).shuffled()
                val recent = lfmApi.getRecentTracks(limit = 15).filterNot { it.isNowPlaying }.shuffled()
                val combined = (top + recent).distinctBy { "${it.name}|${it.artist}".lowercase() }
                val similarSeed = combined.firstOrNull()
                val similar = if (similarSeed != null) {
                    lfmApi.getSimilarTracks(similarSeed.name, similarSeed.artist, limit = 10)
                } else emptyList()
                (combined + similar).shuffled().distinctBy { "${it.name}|${it.artist}".lowercase() }.take(limit)
            }
            LastWaveMode.RECOMMENDATIONS -> {
                val topArtists = lfmApi.getTopArtists(period = "1month", limit = 8)
                val candidateTracks = mutableListOf<LastFmTrack>()
                topArtists.take(4).forEach { (art, _) ->
                    val simArtists = lfmApi.getSimilarArtists(art, limit = 4)
                    simArtists.forEach { sa ->
                        candidateTracks.addAll(lfmApi.getArtistTopTracks(sa, limit = 4))
                    }
                }
                candidateTracks.shuffled().distinctBy { "${it.name}|${it.artist}".lowercase() }.take(limit)
            }
            LastWaveMode.MY_LIBRARY -> {
                lfmApi.getTopTracks(period = "overall", limit = 100).shuffled().take(limit)
            }
        }

        val converted = tracks.map { it.toTrack() }
        val title = generateUniqueName(existingTitles)
        val subtitle = when (mode) {
            LastWaveMode.TOP_TRACKS -> "Top Tracks · ${period.replaceFirstChar { it.uppercase() }} · ${converted.size} songs"
            LastWaveMode.RECENT_TRACKS -> "Recent History · ${converted.size} songs"
            LastWaveMode.SIMILAR_TRACKS -> "Similar to $seedTrack · ${converted.size} songs"
            LastWaveMode.SIMILAR_ARTISTS -> "Inspired by $seedArtist · ${converted.size} songs"
            LastWaveMode.BY_TAG -> "Genre · ${tag.ifBlank { "Tag" }} · ${converted.size} songs"
            LastWaveMode.MY_MIX -> "Personal Mix · Blend · ${converted.size} songs"
            LastWaveMode.RECOMMENDATIONS -> "Recommendations · Fresh Discovery · ${converted.size} songs"
            LastWaveMode.MY_LIBRARY -> "Deep Cuts · From Your Past · ${converted.size} songs"
        }

        GeneratedPlaylist(
            title = title,
            subtitle = subtitle,
            mode = mode,
            tracks = converted
        )
    }
}
