package com.aika.game

import kotlin.math.abs
import kotlin.math.min

/** Pure game rules: every function takes a state and returns a new state. */
object Engine {
    const val MAX_MANA = 10

    /** Straight/diagonal list of cells from a to b inclusive (used for patrol routes). */
    fun line(a: Pos, b: Pos): List<Pos> {
        val out = mutableListOf(a)
        var x = a.x
        var y = a.y
        while (x != b.x || y != b.y) {
            x += Integer.signum(b.x - x)
            y += Integer.signum(b.y - y)
            out.add(Pos(x, y))
        }
        return out
    }

    fun create(level: LevelDef): GameState {
        var start = Pos(1, 1)
        val mirrors = mutableMapOf<Pos, Boolean>()
        for (y in level.rows.indices) for (x in level.rows[y].indices) {
            val c = level.rows[y][x]
            if (c == 'S') start = Pos(x, y)
            if (c == 'm') mirrors[Pos(x, y)] = true
            if (c == 'n') mirrors[Pos(x, y)] = false
        }
        var nextId = 0
        val monsters = level.monsters.map { d ->
            Monster(nextId++, d.type, d.route[0], d.route, 0, true, d.hp, d.hp, 0)
        }
        val (beam, hit) = beam(level, mirrors)
        return GameState(
            level, start, Dir.DOWN, 3, MAX_MANA, 0, monsters, emptySet(), emptySet(),
            mirrors, beam, hit, level.paintCount == 0, 0, Status.PLAYING, 0, "", emptyList()
        )
    }

    /** Traces the magic beam from 'L' eastwards, reflecting on mirrors. Returns cells and whether target was hit. */
    fun beam(level: LevelDef, mirrors: Map<Pos, Boolean>): Pair<List<Pos>, Boolean> {
        var sx = -1
        var sy = -1
        for (y in level.rows.indices) for (x in level.rows[y].indices) if (level.rows[y][x] == 'L') { sx = x; sy = y }
        val cells = mutableListOf<Pos>()
        if (sx < 0) return cells to false
        var x = sx
        var y = sy
        var dx = 1
        var dy = 0
        for (i in 0 until 80) {
            x += dx
            y += dy
            if (x < 0 || y < 0 || x >= level.w || y >= level.h) return cells to false
            val c = level.rows[y][x]
            val p = Pos(x, y)
            if (c == '#' || c == 'D' || c == '~' || c == 'L') return cells to false
            if (c == 'T') { cells.add(p); return cells to true }
            cells.add(p)
            if (c == 'm' || c == 'n') {
                val slash = mirrors[p] ?: (c == 'm')
                if (slash) { val t = dx; dx = -dy; dy = -t } else { val t = dx; dx = dy; dy = t }
            }
        }
        return cells to false
    }

    fun toggleMirror(s: GameState, p: Pos): GameState {
        val cur = s.mirrors[p] ?: return s
        val mirrors = s.mirrors + (p to !cur)
        val (beam, hit) = beam(s.level, mirrors)
        return s.copy(mirrors = mirrors, beam = beam, doorOpen = hit || s.doorOpen,
            msg = if (hit) "Луч попал в цель! Дверь открыта" else "",
            fx = s.fx + Fx(p, 1, System.currentTimeMillis()))
    }

    fun walkable(s: GameState, p: Pos): Boolean {
        val l = s.level
        if (p.x < 0 || p.y < 0 || p.x >= l.w || p.y >= l.h) return false
        return when (l.rows[p.y][p.x]) {
            '#', '~', 'm', 'n', 'L', 'T' -> false
            'D' -> s.doorOpen
            'B' -> s.bridgeOpen
            else -> true
        }
    }

    /** BFS path (excluding the start cell). Empty if unreachable. */
    fun path(s: GameState, to: Pos): List<Pos> {
        if (!walkable(s, to) || to == s.aika) return emptyList()
        val prev = HashMap<Pos, Pos>()
        val q = ArrayDeque<Pos>()
        q.add(s.aika)
        prev[s.aika] = s.aika
        while (q.isNotEmpty()) {
            val c = q.removeFirst()
            if (c == to) break
            for (d in Dir.values()) {
                val n = Pos(c.x + d.dx, c.y + d.dy)
                if (n !in prev && walkable(s, n)) { prev[n] = c; q.add(n) }
            }
        }
        if (to !in prev) return emptyList()
        val out = mutableListOf<Pos>()
        var c = to
        while (c != s.aika) { out.add(c); c = prev[c]!! }
        out.reverse()
        return out
    }

    private fun faceOf(a: Pos, b: Pos): Dir = when {
        abs(b.x - a.x) >= abs(b.y - a.y) -> if (b.x >= a.x) Dir.RIGHT else Dir.LEFT
        else -> if (b.y >= a.y) Dir.DOWN else Dir.UP
    }

    private fun nextIdx(m: Monster): Pair<Int, Boolean> {
        if (m.route.size < 2) return m.idx to m.fwd
        var f = m.fwd
        var i = m.idx + if (f) 1 else -1
        if (i >= m.route.size) { f = false; i = m.route.size - 2 }
        if (i < 0) { f = true; i = 1 }
        return i to f
    }

    /** One game turn: Aika moves (or attacks), then monsters move. */
    fun move(s: GameState, to: Pos): GameState {
        if (s.status != Status.PLAYING) return s
        val now = System.currentTimeMillis()
        val level = s.level
        var aika = s.aika
        var hearts = s.hearts
        var monsters = s.monsters
        var msg = ""
        var fx = s.fx.filter { now - it.born < 800 }
        val facing = faceOf(s.aika, to)

        val target = monsters.firstOrNull { it.pos == to }
        if (target != null) {
            // Frozen targets take big damage, sneak attacks (invisible) medium, plain hits small
            val dmg = if (target.frozen > 0) 4 else if (s.invis > 0) 3 else 2
            val hp = target.hp - dmg
            fx = fx + Fx(to, 2, now)
            if (hp <= 0) {
                monsters = monsters.filter { it.id != target.id }
                aika = to
            } else {
                monsters = monsters.map { if (it.id == target.id) it.copy(hp = hp) else it }
                if (target.frozen == 0 && s.invis == 0) { hearts -= 1; fx = fx + Fx(aika, 3, now) }
            }
        } else {
            aika = to
        }

        var mana = s.mana
        var crystals = s.crystals
        var collected = s.collected
        var painted = s.painted
        val tile = level.rows[aika.y][aika.x]
        if (tile == 'c' && aika !in collected) {
            collected = collected + aika
            mana = min(MAX_MANA, mana + 2)
            crystals += 1
            fx = fx + Fx(aika, 1, now)
            msg = "Кристалл! +2 маны"
        }
        if (tile == 'p' && aika !in painted) {
            painted = painted + aika
            fx = fx + Fx(aika, 1, now)
            msg = "Клетка раскрашена (${painted.size}/${level.paintCount})"
        }
        val bridgeOpen = painted.size >= level.paintCount
        if (bridgeOpen && !s.bridgeOpen && level.paintCount > 0) msg = "Мост проявился!"

        // Monsters phase
        monsters = monsters.map { m ->
            if (m.frozen > 0) m.copy(frozen = m.frozen - 1)
            else { val (i, f) = nextIdx(m); m.copy(pos = m.route[i], idx = i, fwd = f) }
        }
        if (s.invis == 0 && monsters.any { it.pos == aika }) { hearts -= 1; fx = fx + Fx(aika, 3, now) }

        val moves = s.moves + 1
        if (moves % 2 == 0) mana = min(MAX_MANA, mana + 1)

        val bossAlive = monsters.any { it.type == MType.BOSS }
        val onPortal = tile == 'P'
        if (onPortal && level.bossGoal && bossAlive) msg = "Портал закрыт: победи скелета-мага!"
        val status = when {
            hearts <= 0 -> Status.LOST
            onPortal && !(level.bossGoal && bossAlive) -> Status.WON
            level.moveLimit > 0 && moves >= level.moveLimit -> Status.LOOP
            else -> Status.PLAYING
        }
        return s.copy(aika = aika, facing = facing, hearts = maxOf(0, hearts), mana = mana, moves = moves,
            monsters = monsters, painted = painted, collected = collected, bridgeOpen = bridgeOpen,
            invis = maxOf(0, s.invis - 1), status = status, crystals = crystals, msg = msg, fx = fx)
    }

    /** Teleport: jump exactly two cells in a straight line over anything. */
    fun canTeleport(s: GameState, to: Pos): Boolean {
        val dx = abs(to.x - s.aika.x)
        val dy = abs(to.y - s.aika.y)
        return ((dx == 2 && dy == 0) || (dx == 0 && dy == 2)) && walkable(s, to) && s.mana >= Spell.TELEPORT.cost
    }

    fun teleport(s: GameState, to: Pos): GameState =
        move(s.copy(mana = s.mana - Spell.TELEPORT.cost,
            fx = s.fx + Fx(s.aika, 1, System.currentTimeMillis())), to)

    /** Freeze nearest monster within 3 cells for 1 turn (free action). */
    fun freeze(s: GameState): GameState {
        val near = s.monsters.filter { abs(it.pos.x - s.aika.x) + abs(it.pos.y - s.aika.y) <= 3 }
            .minByOrNull { abs(it.pos.x - s.aika.x) + abs(it.pos.y - s.aika.y) }
            ?: return s.copy(msg = "Рядом нет монстров")
        return s.copy(mana = s.mana - Spell.FREEZE.cost,
            monsters = s.monsters.map { if (it.id == near.id) it.copy(frozen = 1) else it },
            fx = s.fx + Fx(near.pos, 1, System.currentTimeMillis()), msg = "Монстр заморожен!")
    }

    /** Invisibility for the next turn (free action). */
    fun invis(s: GameState): GameState =
        s.copy(mana = s.mana - Spell.INVIS.cost, invis = 1, msg = "Айка невидима",
            fx = s.fx + Fx(s.aika, 1, System.currentTimeMillis()))
}
