# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-11-20 - Initial Release

### Added
- **Modern Fluid UI & Glassmorphism**: Complete Jetpack Compose interface with dark theme, dynamic album cover breathing animation, animated equalizer waves, expanding bottom navigation bar, and spring transitions.
- **Multi-Tier Stream Resolution Engine**: 4-tier fallback cascade in `MusicSearchApi` and `InnerTubeApi` with `VISIONOS` client emulation, dynamic visitor ID extraction, and multi-client fallback (`iOS`, `WEB_REMIX`).
- **Visitor ID Caching**: 12-hour in-memory cache for YouTube visitor data tokens scraped dynamically from `sw.js_data` and YouTube homepage.
- **Chunked Range Downloader**: 1MB HTTP range streaming downloader in `OfflineCacheManager` that circumvents YouTube's server-side audio playback bandwidth throttling.
- **Loudness Normalization**: ITU-R BS.1770 perceptual loudness normalization in `PlayerController` targeting -14 LUFS using track metadata extracted from InnerTube audio configurations.
- **YouTube Account Sync**: `YouTubeAccountManager` supporting authenticated sessions, automatic `SAPISIDHASH` signature generation, profile metadata retrieval, and remote liked playlist browsing.
- **10-Band DSP Equalizer**: Integrated Android `DynamicsProcessing` audio effects with 10 customizable frequency bands (31Hz - 16kHz) and preconfigured acoustic presets (`Flat`, `Bass Boost`, `Vocal Clarity`, `V-Shape`, `Harman IEM`).
- **Synchronized Karaoke & Word Lyrics**: Full support for synchronized and word-level LRC parsing from LrcLib with real-time UI highlighting and CJK Romanization (Japanese Hepburn, Mandarin Pinyin, Korean Revised Romanization).
- **Native Last.fm Scrobbler**: Background scrobbler supporting both Real-time Now Playing notifications and 50%/4-minute threshold scrobble submission with offline caching.
- **SponsorBlock Integration**: Automatic segment skipping for non-music segments, sponsor messages, intros, and outros using community-sourced timestamps.
- **Local Device Audio Scanner**: Comprehensive device audio scanner supporting MediaStore indexing.
- **Continuous Radio Queue**: Auto-generating infinite radio queues when playback reaches the end of current playlist.

### Security
- **Path Traversal Shield**: Strict canonical path verification (`isPathSafe`) for all offline cache audio and lyric write operations, blocking directory traversal attacks (`../`).
- **Credential Isolation**: Enforced `Context.MODE_PRIVATE` storage for YouTube session cookies and Last.fm credentials.
- **Cookie Purge on Logout**: Complete WebKit `CookieManager.removeAllCookies()` execution when signing out to ensure complete session termination.

[1.0.0]: https://github.com/rejoanabir/VeltPulse/releases/tag/v1.0.0
