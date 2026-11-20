package com.veltusstudio.veltpulse.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.veltusstudio.veltpulse.core.theme.VeltColors
import com.veltusstudio.veltpulse.data.model.Track
import com.veltusstudio.veltpulse.data.remote.LastFmApi
import com.veltusstudio.veltpulse.ui.components.AnimatedEqualizerIndicator
import com.veltusstudio.veltpulse.ui.components.VeltPulseLogo

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userName: String,
    tracks: List<Track>,
    isLoading: Boolean,
    currentTrackId: String?,
    lfmApi: LastFmApi,
    onOpenSearch: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenGenres: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onOpenLikedSongs: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val featuredTrack = remember(tracks) { tracks.firstOrNull() }
    val albumsForYou = remember(tracks) { if (tracks.size > 1) tracks.take(6) else tracks }
    val quickPicks = remember(tracks) { if (tracks.size > 6) tracks.drop(6) else tracks }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VeltColors.DeepBg)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 170.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // --- 1. TOP HEADER: [VeltPulse Logo + Title] | [Profile Avatar] (Consistent with other screens) ---
            item(key = "home_top_bar") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Logo + Brand (Matching typography of Your Library / Search)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        VeltPulseLogo(size = 32.dp)
                        Text(
                            text = "VeltPulse",
                            style = MaterialTheme.typography.headlineLarge,
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Profile Avatar (Notification bell removed per request)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.verticalGradient(
                                    listOf(Color(0xFF3B1D6E), Color(0xFF1E0E38))
                                )
                            )
                            .border(1.5.dp, VeltColors.NeonViolet.copy(alpha = 0.65f), CircleShape)
                            .clickable(onClick = onOpenProfile),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = "Profile",
                            tint = VeltColors.SilverSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // --- 2. HERO CARD: Swipeable Horizontal Pager (Mockup 16475.png) ---
            item(key = "hero_infinite_banner") {
                val pagerState = androidx.compose.foundation.pager.rememberPagerState { 3 }
                val bannerCards = listOf(
                    Triple(
                        "Infinite Music.",
                        "Endless Vibes.",
                        "Discover. Play. Feel."
                    ),
                    Triple(
                        "Daily Velocity.",
                        "Curated For You.",
                        "Fresh soundscapes refreshed every dawn."
                    ),
                    Triple(
                        "Pure Sound.",
                        "Hi-Res Audio DSP.",
                        "Bit-perfect transients & studio acoustics."
                    )
                )
                val bannerGradients = listOf(
                    listOf(Color(0xFF2E1752), Color(0xFF160B2B), Color(0xFF0C0617)),
                    listOf(Color(0xFF3B154D), Color(0xFF200D2D), Color(0xFF0F0618)),
                    listOf(Color(0xFF152642), Color(0xFF0D1728), Color(0xFF070B14))
                )

                androidx.compose.foundation.pager.HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth()
                ) { page ->
                    val (title1, title2, tagline) = bannerCards[page]
                    val gradient = bannerGradients[page]
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(brush = Brush.verticalGradient(gradient))
                            .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.35f), RoundedCornerShape(28.dp))
                            .clickable {
                                featuredTrack?.let { onTrackClick(it) }
                            }
                    ) {
                        // Ambient subtle glow (no random track artwork)
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .align(Alignment.TopEnd)
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            VeltColors.NeonViolet.copy(alpha = 0.22f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )

                        // Content overlay
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    title1,
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 28.sp
                                )
                                Text(
                                    title2,
                                    color = VeltColors.NeonViolet,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 28.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    tagline,
                                    color = VeltColors.SilverSecondary.copy(alpha = 0.85f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // Bottom Actions: [▶ Play Now] pill button + [...] button + dots indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // "▶ Play Now" pill button
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    listOf(Color(0xFF9333EA), Color(0xFF7C3AED))
                                                )
                                            )
                                            .clickable {
                                                featuredTrack?.let { onTrackClick(it) }
                                            }
                                            .padding(horizontal = 22.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = "Play",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (page == 0) "Play Now" else if (page == 1) "Listen Now" else "Tune In",
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // "..." secondary button
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.12f))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                                            .clickable { onOpenSearch() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Rounded.MoreHoriz,
                                            contentDescription = "More",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Interactive Animated Carousel dots
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(3) { dotIdx ->
                                        val isSel = pagerState.currentPage == dotIdx
                                        Box(
                                            modifier = Modifier
                                                .size(width = if (isSel) 18.dp else 6.dp, height = 6.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(if (isSel) VeltColors.NeonViolet else Color.White.copy(alpha = 0.35f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 3. QUICK ACCESS SECTION: Liked Songs | Liked on YT | Discover (Mockup 16475.png) ---
            item(key = "quick_access_section") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Quick access",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .clickable { onOpenLikedSongs() }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "See all",
                                color = VeltColors.LavenderMuted,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = VeltColors.LavenderMuted,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 1: Liked Songs (Deep Purple glow)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.92f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0xFF38154D), Color(0xFF1E0E2E))
                                    )
                                )
                                .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                                .clickable(onClick = onOpenLikedSongs)
                                .padding(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    Icons.Rounded.Favorite,
                                    contentDescription = "Liked Songs",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Liked Songs", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Your collection", color = VeltColors.LavenderMuted, fontSize = 10.sp)
                                    }
                                    // Mini equalizer bars indicator
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.Bottom
                                    ) {
                                        Box(modifier = Modifier.size(width = 3.dp, height = 8.dp).background(VeltColors.NeonViolet, RoundedCornerShape(1.dp)))
                                        Box(modifier = Modifier.size(width = 3.dp, height = 14.dp).background(VeltColors.MagentaGlow, RoundedCornerShape(1.dp)))
                                        Box(modifier = Modifier.size(width = 3.dp, height = 10.dp).background(VeltColors.NeonViolet, RoundedCornerShape(1.dp)))
                                    }
                                }
                            }
                        }

                        // Card 2: Liked on YT (Crimson/Burgundy glow)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.92f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0xFF401828), Color(0xFF220E18))
                                    )
                                )
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                                .clickable { }
                                .padding(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    Icons.Rounded.ThumbUp,
                                    contentDescription = "Liked on YT",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Liked on YT", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Your collection", color = VeltColors.LavenderMuted, fontSize = 10.sp)
                                    }
                                    // YT red badge icon
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFFF0000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(10.dp))
                                    }
                                }
                            }
                        }

                        // Card 3: Discover (Emerald/Teal glow)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.92f)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    brush = Brush.verticalGradient(
                                        listOf(Color(0xFF143235), Color(0xFF0A1B1D))
                                    )
                                )
                                .border(1.dp, Color(0xFF10B981).copy(alpha = 0.25f), RoundedCornerShape(22.dp))
                                .clickable(onClick = onOpenGenres)
                                .padding(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    Icons.Rounded.Explore,
                                    contentDescription = "Discover",
                                    tint = Color.White,
                                    modifier = Modifier.size(26.dp)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Discover", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("Made for you", color = VeltColors.LavenderMuted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- 4. "MY GENRES" SECTION (Mockup 16475.png) ---
            item(key = "my_genres_section") {
                Column {
                    Text(
                        "My genres",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Tap a vibe to start instant radio",
                        color = VeltColors.SilverMuted,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val genres = remember {
                        listOf(
                            Triple("Pop", Color(0xFF4C1D95), Color(0xFF2E1065)),
                            Triple("Hip-Hop", Color(0xFF3B1D54), Color(0xFF1E0A30)),
                            Triple("Lo-fi & Chill", Color(0xFF1E293B), Color(0xFF0F172A)),
                            Triple("Indie", Color(0xFF381530), Color(0xFF1F091B)),
                            Triple("Electronic", Color(0xFF143538), Color(0xFF0A1C1E)),
                            Triple("Rock", Color(0xFF2E2018), Color(0xFF19110B)),
                            Triple("R&B / Soul", Color(0xFF38182E), Color(0xFF1E0B18)),
                            Triple("Acoustic", Color(0xFF242838), Color(0xFF121422))
                        )
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(genres) { (gName, c1, c2) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(
                                        brush = Brush.verticalGradient(listOf(c1, c2))
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                                    .clickable { onOpenGenres() }
                                    .padding(horizontal = 18.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = gName,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        item {
                            // Circular chevron button to view all
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(VeltColors.SurfaceElevated)
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                                    .clickable { onOpenGenres() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "All Genres",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // --- 5. "DISCOVER NEW MUSIC" PROMO BANNER (Mockup 16475.png) ---
            item(key = "discover_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF2D164E),
                                    Color(0xFF1D0D36),
                                    Color(0xFF130924)
                                )
                            )
                        )
                        .border(1.dp, VeltColors.NeonViolet.copy(alpha = 0.40f), RoundedCornerShape(22.dp))
                        .clickable { onOpenSearch() }
                        .padding(horizontal = 18.dp, vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4C1D95)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.GraphicEq,
                                    contentDescription = null,
                                    tint = VeltColors.NeonViolet,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Discover New Music",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Handpicked tracks matched to your taste",
                                    color = VeltColors.LavenderMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // --- 6. "ALBUMS FOR YOU" CAROUSEL ---
            if (albumsForYou.isNotEmpty()) {
                item(key = "albums_for_you_section") {
                    Column {
                        Text(
                            "Albums for you",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Handpicked album gems from your taste",
                            color = VeltColors.SilverMuted,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(albumsForYou, key = { idx, t -> "alb_${t.id}_$idx" }) { _, track ->
                                Column(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clickable { onTrackClick(track) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(140.dp)
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(VeltColors.SurfaceElevated)
                                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
                                    ) {
                                        if (track.artworkUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = track.artworkUrl,
                                                contentDescription = track.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        track.title,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        track.artist,
                                        color = VeltColors.SilverMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // --- 7. QUICK PICKS FEED ---
            item(key = "quick_picks_header") {
                Text(
                    "Made for you",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isLoading) {
                item(key = "loading_spinner") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = VeltColors.NeonViolet)
                    }
                }
            } else {
                itemsIndexed(quickPicks, key = { idx, track -> "qp_${track.id}_$idx" }) { _, track ->
                    val isActive = track.id == currentTrackId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (isActive) Color(0xFF2E1752) else Color(0xFF130E22)
                            )
                            .border(
                                1.dp,
                                if (isActive) VeltColors.NeonViolet.copy(alpha = 0.50f) else Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(18.dp)
                            )
                            .clickable { onTrackClick(track) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(VeltColors.SurfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            if (track.artworkUrl.isNotBlank()) {
                                AsyncImage(
                                    model = track.artworkUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.matchParentSize()
                                )
                            } else {
                                Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = VeltColors.SilverSecondary)
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                color = if (isActive) VeltColors.NeonViolet else Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                track.artist,
                                color = VeltColors.SilverMuted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (isActive) {
                            AnimatedEqualizerIndicator()
                        } else {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = VeltColors.SilverMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
