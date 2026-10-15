package com.aika.game

/** Facing direction of Aika (logical, not camera-relative). */
enum class Dir(val dx: Int, val dy: Int) { UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0) }

enum class MType { SLIME, BAT, SKELETON, BOSS }

enum class Spell(val title: String, val icon: String, val cost: Int) {
    TELEPORT("Телепорт", "✨", 2),
    FREEZE("Заморозка", "❄", 2),
    INVIS("Невидим.", "👻", 3)
}

enum class Status { PLAYING, WON, LOST, LOOP }

data class Pos(val x: Int, val y: Int)

/** One line of story dialog: speaker name, portrait emoji, text. */
data class Line(val who: String, val icon: String, val text: String)

data class MonsterDef(val type: MType, val route: List<Pos>, val hp: Int = 1)

/**
 * Tile legend (rows are strings):
 * '.' grass  '#' stone  '~' chasm/water  'S' start  'P' portal  'c' crystal
 * 'p' paint tile (painting all opens bridges 'B')  'B' bridge (closed until painted)
 * 'm' mirror '/'  'n' mirror '\'  'L' beam source (shoots east)  'T' beam target  'D' door (opens when target hit)
 */
data class LevelDef(
    val id: Int,
    val name: String,
    val rows: List<String>,
    val monsters: List<MonsterDef>,
    val moveLimit: Int,            // 0 = unlimited; otherwise "time loop" limit
    val intro: List<Line>,
    val outro: List<Line>,
    val unlocks: Spell?,
    val bossGoal: Boolean = false  // portal opens only after the boss dies
) {
    val w get() = rows[0].length
    val h get() = rows.size
    val paintCount get() = rows.sumOf { r -> r.count { it == 'p' } }
}

data class Monster(
    val id: Int, val type: MType, val pos: Pos, val route: List<Pos>,
    val idx: Int, val fwd: Boolean, val hp: Int, val maxHp: Int, val frozen: Int
)

/** Short-lived visual effect: kind 1 = sparkle, 2 = hit, 3 = hurt. */
data class Fx(val pos: Pos, val kind: Int, val born: Long)

data class GameState(
    val level: LevelDef,
    val aika: Pos,
    val facing: Dir,
    val hearts: Int,
    val mana: Int,
    val moves: Int,
    val monsters: List<Monster>,
    val painted: Set<Pos>,
    val collected: Set<Pos>,
    val mirrors: Map<Pos, Boolean>,   // true = '/', false = '\'
    val beam: List<Pos>,
    val doorOpen: Boolean,
    val bridgeOpen: Boolean,
    val invis: Int,
    val status: Status,
    val crystals: Int,
    val msg: String,
    val fx: List<Fx>
)
