package app.bosk.service.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.bosk.MainActivity
import app.bosk.core.review.ReviewRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

// Brand palette (fixed — widgets don't follow the in-app theme object).
private val WidgetBg = ColorProvider(Color(0xFF0D0B0B))
private val WidgetAccent = ColorProvider(Color(0xFFC94040))
private val WidgetText = ColorProvider(Color(0xFFEDE7E4))
private val WidgetDim = ColorProvider(Color(0xFF8A817D))

/**
 * The Bosk home-screen widget: today's due-card count at a glance, one tap into the app.
 * Data comes straight from the repositories via a Hilt entry point (no service round-trip);
 * the launcher refreshes it on its ~30-min cycle (see bosk_widget_info.xml).
 */
class BoskWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun reviewRepository(): ReviewRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val due = runCatching {
            EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)
                .reviewRepository()
                .observeDueCount()
                .first()
        }.getOrDefault(0)

        provideContent {
            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(WidgetBg)
                    .cornerRadius(18.dp)
                    .padding(14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(
                        "Bosk",
                        style = TextStyle(color = WidgetDim, fontSize = 12.sp),
                    )
                }
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    if (due == 0) "All caught up" else "$due card${if (due == 1) "" else "s"} due",
                    style = TextStyle(
                        color = if (due == 0) WidgetText else WidgetAccent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                )
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    if (due == 0) "Tap to open your vault" else "Tap to practise",
                    style = TextStyle(color = WidgetDim, fontSize = 12.sp),
                )
            }
        }
    }
}

/** Manifest-registered receiver that hosts [BoskWidget]. */
class BoskWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BoskWidget()
}
