package app.bosk.core.ai

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Client-side requests-per-minute cap (Gemini free tier is 15 RPM). A sliding 60s window:
 * if the window is full, callers suspend until the oldest request ages out — so we queue
 * rather than fire-and-fail. Coroutine-friendly (no thread blocking).
 */
class RpmRateLimiter(private val maxPerMinute: Int = 15) {
    private val windowMs = 60_000L
    private val timestamps = ArrayDeque<Long>()
    private val mutex = Mutex()

    suspend fun acquire(now: () -> Long = System::currentTimeMillis) {
        while (true) {
            val waitMs = mutex.withLock {
                val t = now()
                while (timestamps.isNotEmpty() && t - timestamps.first() >= windowMs) {
                    timestamps.removeFirst()
                }
                if (timestamps.size < maxPerMinute) {
                    timestamps.addLast(t)
                    0L
                } else {
                    windowMs - (t - timestamps.first()) + 1
                }
            }
            if (waitMs <= 0L) return
            delay(waitMs)
        }
    }
}
