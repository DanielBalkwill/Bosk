package com.generic.mnemos.ui.screens.graph

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.sqrt

/**
 * A tiny force-directed layout that gives the graph Obsidian's living, "elastic blob" feel:
 * nodes repel each other, edges pull like springs, and a gentle centre force keeps it on-screen.
 * Runs on the frame clock and writes positions into a snapshot map so only the Canvas redraws.
 *
 * **Annealing:** motion is scaled by a "temperature" that cools toward zero over a couple of
 * seconds, so the graph settles and goes still instead of wobbling forever. Interacting with it
 * ([ForceLayout.reheat]) — or the node set changing — warms it back up. Once cooled and at rest
 * the simulation freezes (no maths, no writes, no redraws) until reheated.
 *
 * **Performance:** all per-step working memory (position/force/velocity arrays, the id→index
 * map, compiled edge indices) is allocated once per simulation run, not per frame. Velocities
 * live in plain arrays — they're internal to the maths, so they never pay snapshot-state
 * bookkeeping. Above [GRID_THRESHOLD] nodes, pairwise repulsion switches from exact O(n²) to a
 * uniform spatial grid with a cutoff radius, so "Show all" stays interactive on large vaults.
 */
class ForceLayout internal constructor(
    val positions: SnapshotStateMap<Long, Offset>,
    private val temp: FloatArray,
    private val frozen: BooleanArray,
) {
    /** Warm the simulation back up (call on drag so motion resumes briefly). */
    fun reheat() { temp[0] = 1f; frozen[0] = false }

    /** Directly move a node (a user drag) and let the simulation react around it. */
    fun move(id: Long, delta: Offset) {
        positions[id] = (positions[id] ?: Offset.Zero) + delta
        reheat()
    }
}

@Composable
fun rememberForceLayout(
    nodeIds: List<Long>,
    edges: List<Pair<Long, Long>>,
    running: Boolean = true,
    /** If non-null, ONLY these nodes are simulated (move); all others stay frozen in place.
     *  Used for Obsidian-style local graphs: only a selected node's neighbourhood reorients. */
    activeIds: Set<Long>? = null,
    /** When true, the layout starts already settled (frozen) on seeding — used when positions were
     *  restored from a previous session, so reopening the graph shows almost no movement. */
    settleOnSeed: Boolean = false,
    /** Opening temperature for a settleOnSeed layout: 0 = open fully frozen, up to 1 = a full
     *  reheat. The default gives a brief, gentle "coming to rest" animation (~1s). */
    settleInTemp: Float = SETTLE_IN_TEMP,
    /** Nodes that should push their neighbours away harder — used in Overview mode so a node's
     *  surroundings spread out, leaving clean room for its summary box. */
    boostedIds: Set<Long> = emptySet(),
    seed: (Long) -> Offset,
): ForceLayout {
    val pos = remember { mutableStateMapOf<Long, Offset>() }
    val temp = remember { floatArrayOf(1f) }
    val frozen = remember { booleanArrayOf(false) }
    val layout = remember { ForceLayout(pos, temp, frozen) }

    // Seed new nodes, drop departed ones, whenever the node set changes. Either warm up (fresh
    // graph → animate into shape) or start at rest (restored positions → minimal movement).
    LaunchedEffect(nodeIds) {
        val incoming = nodeIds.filter { it !in pos }
        if (incoming.isNotEmpty()) {
            // Place each NEW node at the centroid of its already-placed neighbours, so a reload
            // "drops it in" right next to its connections instead of flying in from a far spiral
            // point or the origin. Existing nodes are never repositioned (high effective weight),
            // so reloading adds the new nodes cleanly without re-geometrising the whole layout.
            val nbrs = HashMap<Long, MutableList<Long>>()
            edges.fastForEach { (a, b) ->
                nbrs.getOrPut(a) { mutableListOf() }.add(b)
                nbrs.getOrPut(b) { mutableListOf() }.add(a)
            }
            incoming.forEachIndexed { i, id ->
                val placed = nbrs[id]?.mapNotNull { pos[it] }.orEmpty()
                pos[id] = if (placed.isNotEmpty()) {
                    val cx = placed.sumOf { it.x.toDouble() } / placed.size
                    val cy = placed.sumOf { it.y.toDouble() } / placed.size
                    // Tiny deterministic spread so several siblings added at once don't fully overlap.
                    Offset(cx.toFloat() + (i % 7 - 3) * 6f, cy.toFloat() + (i / 7 % 7 - 3) * 6f)
                } else {
                    seed(id)
                }
            }
        }
        val keep = nodeIds.toHashSet()
        (pos.keys.toList()).forEach { if (it !in keep) pos.remove(it) }
        if (settleOnSeed) {
            // Freeze FIRST so the frame-clock simulation below can't race the pre-settle.
            temp[0] = 0f
            frozen[0] = true
            // A frozen start is only correct when the seeded geometry IS a settled layout. On a
            // first-ever open (or a layout saved before it ever settled) it isn't — the graph then
            // sat in raw seed geometry until the first touch snapped everything into place. So when
            // most of the graph was (re)seeded this pass, advance the simulation to equilibrium OFF
            // the frame clock, on a plain map copy (no snapshot bookkeeping per step), and publish
            // the result once. From an already-settled layout the first probe step is at rest and
            // this exits immediately, so restored (and hand-arranged) layouts are left untouched.
            if (incoming.size * 2 >= nodeIds.size && nodeIds.isNotEmpty()) {
                withContext(Dispatchers.Default) {
                    val work = HashMap<Long, Offset>(pos)
                    val sim = SimState(nodeIds, edges, emptySet())
                    var steps = 0
                    while (steps++ < PRESETTLE_MAX_STEPS) {
                        // Stop a touch short of full rest — the last visible bit of settling is
                        // left to the live sim below, so the graph opens gently in motion.
                        if (sim.step(work, applyCenter = true) < REST_EPS * 4) break
                    }
                    pos.putAll(work)
                }
            }
            if (incoming.isNotEmpty() && settleInTemp > 0.01f) {
                // Open ALIVE, not frozen: a short low-temperature settle (~1s) from the cached
                // layout, so the graph arrives exactly where it was and visibly comes to rest —
                // instead of sitting rigid until the first touch. Node-set changes that only
                // REMOVE nodes (e.g. search narrowing) stay frozen; nothing new was placed.
                // settleInTemp ≤ 0 (user setting) keeps the old fully-frozen open.
                temp[0] = settleInTemp.coerceAtMost(1f)
                frozen[0] = false
            }
        } else layout.reheat()
    }

    LaunchedEffect(nodeIds, edges, running, activeIds, boostedIds) {
        if (!running || nodeIds.isEmpty()) return@LaunchedEffect
        // The simulated set: everything, or just the active subset (intersected with present nodes).
        val simIds = if (activeIds == null) nodeIds else nodeIds.filter { it in activeIds }
        if (simIds.isEmpty()) return@LaunchedEffect
        // Centre pull only when simulating the WHOLE graph — for a local subset it would suck the
        // selected cluster into the middle (the user wants it to stay where it was clicked/dragged).
        val applyCenter = activeIds == null
        // All working buffers for this run, allocated once (not per frame).
        val sim = SimState(simIds, edges, boostedIds)
        // Throttle the simulation to ~33 fps. Mobile screens run the frame clock at 60–120 Hz, but
        // the layout doesn't need that — stepping every frame just burns battery/CPU. We still wake
        // each frame (cheap) but only run the pairwise maths when enough time has passed.
        var lastStep = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                // Once cooled and at rest, skip the maths entirely until something reheats us.
                if (frozen[0]) return@withFrameNanos
                if (now - lastStep < STEP_INTERVAL_NS) return@withFrameNanos
                lastStep = now
                val moved = sim.step(pos, applyCenter)
                // Cool down fast; multiplicative decay clamped to a hard zero so it truly settles.
                temp[0] = (temp[0] * COOLING).let { if (it < TEMP_FLOOR) 0f else it }
                if (temp[0] == 0f && moved < REST_EPS) frozen[0] = true
            }
        }
    }
    return layout
}

private const val REPULSION = 11000.0
private const val SPRING_REST = 95.0
private const val SPRING_K = 0.025
private const val CENTER_K = 0.013
private const val DAMPING = 0.82
private const val DT = 0.6
private const val MAX_SPEED = 22.0
private const val BOOST = 3.5         // extra repulsion multiplier for Overview-pinned nodes
private const val COOLING = 0.92f      // per-frame temperature decay (settles ~1s after a touch)
private const val TEMP_FLOOR = 0.04f   // below this, snap temperature to 0 (fully still)
private const val REST_EPS = 0.25f     // max per-frame movement considered "at rest" (freeze sooner)
private const val MIN_SPEED = 0.05     // zero out tiny velocities — kills micro-jitter feedback loops
private const val STEP_INTERVAL_NS = 30_000_000L // ~33 fps physics tick (buffered; saves CPU on mobile)
// Cap on the off-frame settling run a fresh seed gets before it is frozen (see settleOnSeed).
private const val PRESETTLE_MAX_STEPS = 600
// Opening temperature for a settleOnSeed layout: enough for a brief, gentle "coming to rest"
// animation (~1s of cooling), far below a full reheat (1.0) so nothing jumps.
private const val SETTLE_IN_TEMP = 0.35f
// Above this many simulated nodes, repulsion uses a spatial grid + cutoff instead of all pairs.
private const val GRID_THRESHOLD = 72
// Repulsion cutoff (sim units). At this range the force is ~REPULSION/CUTOFF² ≈ 0.08 — negligible.
private const val CUTOFF = 380.0

/**
 * One simulation run's working state: index maps, compiled edges, and reusable force/velocity
 * buffers. Only the nodes in [ids] move (the "active" set); edges to nodes outside it still tug
 * the active endpoint toward the frozen one, so a local graph stays tethered to its (greyed,
 * stationary) surroundings.
 */
private class SimState(
    simIds: List<Long>,
    edges: List<Pair<Long, Long>>,
    boostedIds: Set<Long>,
) {
    val n = simIds.size
    val ids = LongArray(n) { simIds[it] }
    val boosted = BooleanArray(n) { simIds[it] in boostedIds }
    val anyBoost = boostedIds.isNotEmpty()
    val px = DoubleArray(n)
    val py = DoubleArray(n)
    val vx = DoubleArray(n)
    val vy = DoubleArray(n)
    val fx = DoubleArray(n)
    val fy = DoubleArray(n)

    // Edges compiled to sim indices (-1 = endpoint outside the active set → read its fixed
    // position from the map each step). Edges touching no active node are dropped entirely.
    val eIa: IntArray
    val eIb: IntArray
    val eAId: LongArray
    val eBId: LongArray

    // Spatial-grid scratch (only used when n > GRID_THRESHOLD): cell key → node indices.
    val grid = HashMap<Long, MutableList<Int>>()

    init {
        val index = HashMap<Long, Int>(n * 2)
        for (i in 0 until n) index[ids[i]] = i
        val ia = ArrayList<Int>(edges.size)
        val ib = ArrayList<Int>(edges.size)
        val aid = ArrayList<Long>(edges.size)
        val bid = ArrayList<Long>(edges.size)
        edges.fastForEach { (a, b) ->
            val i = index[a] ?: -1
            val j = index[b] ?: -1
            if (i >= 0 || j >= 0) { ia.add(i); ib.add(j); aid.add(a); bid.add(b) }
        }
        eIa = ia.toIntArray(); eIb = ib.toIntArray()
        eAId = aid.toLongArray(); eBId = bid.toLongArray()
    }

    /** Repulsion between active nodes [i] and [j] at squared distance [d2in] along (dx0, dy0). */
    private fun repel(i: Int, j: Int, dx0: Double, dy0: Double, d2in: Double) {
        var dx = dx0; var dy = dy0; var d2 = d2in
        // Coincident nodes: nudge apart DETERMINISTICALLY (a fixed offset by index) rather than
        // with random noise, which otherwise feeds back into endless tiny jitter.
        if (d2 < 0.01) { dx = 1.0 + (i - j) * 0.001; dy = 1.0 - (i - j) * 0.001; d2 = dx * dx + dy * dy }
        val dist = sqrt(d2)
        // Boosted nodes (Overview pins) repel harder so their surroundings clear out for boxes.
        val boost = if (anyBoost && (boosted[i] || boosted[j])) BOOST else 1.0
        val f = REPULSION * boost / d2
        val ux = dx / dist; val uy = dy / dist
        fx[i] += ux * f; fy[i] += uy * f
        fx[j] -= ux * f; fy[j] -= uy * f
    }

    /**
     * Advances the simulation one frame, writing new positions into [pos]. Returns the largest
     * single-node displacement this step (to detect rest).
     */
    fun step(pos: MutableMap<Long, Offset>, applyCenter: Boolean): Float {
        for (i in 0 until n) {
            val p = pos[ids[i]] ?: Offset.Zero
            px[i] = p.x.toDouble(); py[i] = p.y.toDouble()
            fx[i] = 0.0; fy[i] = 0.0
            if (applyCenter) {
                fx[i] -= px[i] * CENTER_K
                fy[i] -= py[i] * CENTER_K
            }
        }

        // Pairwise repulsion — exact for small sets, spatial grid + cutoff for large ones.
        if (n <= GRID_THRESHOLD) {
            for (i in 0 until n) {
                for (j in i + 1 until n) {
                    val dx = px[i] - px[j]
                    val dy = py[i] - py[j]
                    repel(i, j, dx, dy, dx * dx + dy * dy)
                }
            }
        } else {
            // Bucket nodes into CUTOFF-sized cells; only pairs in the same or adjacent cells can
            // be within range, so each node checks a 3×3 block instead of every other node.
            grid.values.forEach { it.clear() }
            for (i in 0 until n) {
                grid.getOrPut(cellKey(cellOf(px[i]), cellOf(py[i]))) { ArrayList() }.add(i)
            }
            val cutoff2 = CUTOFF * CUTOFF
            for (i in 0 until n) {
                val cx = cellOf(px[i]); val cy = cellOf(py[i])
                for (gx in cx - 1..cx + 1) {
                    for (gy in cy - 1..cy + 1) {
                        val bucket = grid[cellKey(gx, gy)] ?: continue
                        for (k in bucket.indices) {
                            val j = bucket[k]
                            if (j <= i) continue // each pair once
                            val dx = px[i] - px[j]
                            val dy = py[i] - py[j]
                            val d2 = dx * dx + dy * dy
                            if (d2 <= cutoff2) repel(i, j, dx, dy, d2)
                        }
                    }
                }
            }
        }

        // Spring attraction along edges. An endpoint outside the active set contributes its (fixed)
        // position as a pull target but receives no force itself.
        for (e in eIa.indices) {
            val ia = eIa[e]; val ib = eIb[e]
            val ax: Double; val ay: Double
            if (ia >= 0) { ax = px[ia]; ay = py[ia] } else {
                val p = pos[eAId[e]] ?: continue
                ax = p.x.toDouble(); ay = p.y.toDouble()
            }
            val bx: Double; val by: Double
            if (ib >= 0) { bx = px[ib]; by = py[ib] } else {
                val p = pos[eBId[e]] ?: continue
                bx = p.x.toDouble(); by = p.y.toDouble()
            }
            val dx = bx - ax
            val dy = by - ay
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(0.01)
            val f = (dist - SPRING_REST) * SPRING_K
            val ux = dx / dist; val uy = dy / dist
            if (ia >= 0) { fx[ia] += ux * f; fy[ia] += uy * f }
            if (ib >= 0) { fx[ib] -= ux * f; fy[ib] -= uy * f }
        }

        var maxDisp = 0f
        for (i in 0 until n) {
            var nvx = (vx[i] + fx[i] * DT) * DAMPING
            var nvy = (vy[i] + fy[i] * DT) * DAMPING
            val speed = sqrt(nvx * nvx + nvy * nvy)
            if (speed > MAX_SPEED) { nvx = nvx / speed * MAX_SPEED; nvy = nvy / speed * MAX_SPEED }
            // Snap negligible velocities to zero so the layout actually comes to rest instead of
            // drifting forever in tiny self-perpetuating increments (a real source of overhead).
            if (speed < MIN_SPEED) { nvx = 0.0; nvy = 0.0 }
            vx[i] = nvx; vy[i] = nvy
            val ndx = nvx * DT; val ndy = nvy * DT
            if (ndx == 0.0 && ndy == 0.0) continue // at rest → skip the snapshot write entirely
            pos[ids[i]] = Offset((px[i] + ndx).toFloat(), (py[i] + ndy).toFloat())
            val disp = sqrt(ndx * ndx + ndy * ndy).toFloat()
            if (disp > maxDisp) maxDisp = disp
        }
        return maxDisp
    }
}

private fun cellOf(v: Double): Int = Math.floorDiv(v.toInt(), CUTOFF.toInt())
private fun cellKey(gx: Int, gy: Int): Long = (gx.toLong() shl 32) xor (gy.toLong() and 0xFFFFFFFFL)
