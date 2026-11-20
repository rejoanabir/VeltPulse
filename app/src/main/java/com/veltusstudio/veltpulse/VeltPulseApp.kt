package com.veltusstudio.veltpulse

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.veltusstudio.veltpulse.data.remote.YouTubeAccountManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class VeltPulseApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient(container.okHttpClient)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}

class AppContainer(context: Context) {
    val youTubeAccountManager by lazy { YouTubeAccountManager(context) }

    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val original = chain.request()
                val host = original.url.host.lowercase()
                val isGoogleVideo = host == "googlevideo.com" || host.endsWith(".googlevideo.com")
                val isPlayerEndpoint = original.url.encodedPath.contains("/player")
                val isMusicApi = host == "music.youtube.com" && !isPlayerEndpoint

                if (isGoogleVideo || isPlayerEndpoint) {
                    // Googlevideo media servers and InnerTube /player endpoints reject requests
                    // or return LOGIN_REQUIRED if user session cookies are attached.
                    // Explicitly strip any Authorization and Cookie headers.
                    val cleanRequest = original.newBuilder()
                        .removeHeader("Authorization")
                        .removeHeader("Cookie")
                        .build()
                    chain.proceed(cleanRequest)
                } else if (isMusicApi) {
                    val builder = original.newBuilder()
                    val authHeaders = youTubeAccountManager.getAuthHeaders()
                    authHeaders.forEach { (k, v) ->
                        if (original.header(k) == null) {
                            builder.header(k, v)
                        }
                    }
                    chain.proceed(builder.build())
                } else {
                    chain.proceed(original)
                }
            }
            .build()
    }
}
