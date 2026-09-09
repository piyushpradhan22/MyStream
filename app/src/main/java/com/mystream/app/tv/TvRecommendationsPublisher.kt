package com.mystream.app.tv

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.tvprovider.media.tv.PreviewChannel
import androidx.tvprovider.media.tv.PreviewChannelHelper
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import com.mystream.app.MainActivity
import com.mystream.app.data.model.StremioMetaPreview

/**
 * Publishes trending titles to the Android TV home screen "Watch Next"/channel row via the
 * standard on-device TvProvider content API. This is purely local (launcher-driven) — separate
 * from, and unrelated to, Google's server-side "where to watch" search/Assistant data.
 */
object TvRecommendationsPublisher {

    private const val TAG = "TvRecommendations"
    private const val CHANNEL_INTERNAL_ID = "mystream_trending"

    fun publish(context: Context, movies: List<StremioMetaPreview>) {
        if (movies.isEmpty()) return
        try {
            val helper = PreviewChannelHelper(context)
            val channelId = findOrCreateChannel(context, helper)
            if (channelId <= 0L) return
            android.util.Log.d(TAG, "Using channel id=$channelId (total channels=${helper.allChannels.size})")

            var inserted = 0
            movies.take(15).forEachIndexed { index, movie ->
                val posterUri = movie.poster?.let { Uri.parse(it) } ?: return@forEachIndexed
                val contentIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("mystream://detail/${movie.type}/${movie.id}")
                ).setPackage(context.packageName)

                val program = PreviewProgram.Builder()
                    .setChannelId(channelId)
                    .setType(TvContractCompat.PreviewPrograms.TYPE_MOVIE)
                    .setTitle(movie.name)
                    .setDescription(movie.description ?: "")
                    .setPosterArtUri(posterUri)
                    .setIntent(contentIntent)
                    .setInternalProviderId("${CHANNEL_INTERNAL_ID}_${movie.id}")
                    .setWeight(movies.size - index)
                    .build()

                val resultUri = context.contentResolver.insert(
                    TvContractCompat.PreviewPrograms.CONTENT_URI,
                    program.toContentValues()
                )
                if (resultUri != null) inserted++
            }
            android.util.Log.d(TAG, "Inserted $inserted/${movies.take(15).size} preview programs")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to publish TV recommendations", e)
        }
    }

    /** Reuses the channel published on a previous launch instead of creating duplicates. */
    private fun findOrCreateChannel(context: Context, helper: PreviewChannelHelper): Long {
        helper.allChannels.forEach { existing ->
            if (existing.internalProviderId == CHANNEL_INTERNAL_ID) return existing.id
        }

        val appLaunchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent(context, MainActivity::class.java)

        val logo = androidx.core.content.ContextCompat.getDrawable(context, com.mystream.app.R.mipmap.ic_launcher)
            ?.let { drawable ->
                val bitmap = android.graphics.Bitmap.createBitmap(
                    drawable.intrinsicWidth.coerceAtLeast(1),
                    drawable.intrinsicHeight.coerceAtLeast(1),
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }

        val channel = PreviewChannel.Builder()
            .setDisplayName("MyStream")
            .setDescription("Trending on MyStream")
            .setAppLinkIntent(appLaunchIntent)
            .setInternalProviderId(CHANNEL_INTERNAL_ID)
            .apply { logo?.let { setLogo(it) } }
            .build()

        return helper.publishDefaultChannel(channel)
    }
}
