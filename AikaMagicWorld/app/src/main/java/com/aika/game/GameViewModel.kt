package com.aika.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { MENU, LEVELS, GAME }

/** Holds UI state, drives the turn loop (walking animation) and saves progress to Room. */
class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()

    var screen by mutableStateOf(Screen.MENU)
    var progress by mutableStateOf(Progress())
    var state by mutableStateOf<GameState?>(null)
    var dialog by mutableStateOf<List<Line>>(emptyList())
    var dialogIdx by mutableIntStateOf(0)
    var paused by mutableStateOf(false)
    var armed by mutableStateOf(false)      // teleport armed
    var rot by mutableIntStateOf(0)         // camera rotation, 0..3 (x 90 degrees)
    var banner by mutableStateOf("")

    private var afterDialog: (() -> Unit)? = null
    private var walkJob: Job? = null

    init { viewModelScope.launch { progress = dao.get() ?: Progress() } }

    fun hasSpell(s: Spell) = (progress.spellMask and (1 shl s.ordinal)) != 0

    fun startLevel(idx: Int) {
        walkJob?.cancel()
        val lv = Levels.all[idx]
        state = Engine.create(lv)
        armed = false; paused = false; rot = 0; banner = ""
        screen = Screen.GAME
        showDialog(lv.intro) { }
    }

    fun restart() {
        walkJob?.cancel()
        val lv = state?.level ?: return
        state = Engine.create(lv)
        armed = false; banner = ""
    }

    private fun showDialog(lines: List<Line>, then: () -> Unit) {
        dialog = lines; dialogIdx = 0; afterDialog = then
    }

    fun nextDialog() {
        if (dialogIdx + 1 < dialog.size) dialogIdx++
        else {
            dialog = emptyList()
            val a = afterDialog
            afterDialog = null
            a?.invoke()
        }
    }

    fun rotate(d: Int) { rot = (rot + d + 4) % 4 }

    fun onTap(p: Pos) {
        val s = state ?: return
        if (paused || dialog.isNotEmpty() || s.status != Status.PLAYING) return
        if (p.x < 0 || p.y < 0 || p.x >= s.level.w || p.y >= s.level.h) return
        val c = s.level.rows[p.y][p.x]
        if (c == 'm' || c == 'n') { apply(Engine.toggleMirror(s, p)); return }
        if (armed) {
            armed = false
            if (Engine.canTeleport(s, p)) apply(Engine.teleport(s, p))
            else banner = "Телепорт: выбери свободную клетку ровно через одну"
            return
        }
        walkJob?.cancel()
        val path = Engine.path(s, p)
        walkJob = viewModelScope.launch {
            for (n in path) {
                val cur = state ?: break
                if (cur.status != Status.PLAYING || paused) break
                apply(Engine.move(cur, n))
                delay(170)
            }
        }
    }

    fun cast(sp: Spell) {
        val s = state ?: return
        if (paused || dialog.isNotEmpty() || s.status != Status.PLAYING) return
        if (!hasSpell(sp)) { banner = "Заклинание ещё закрыто"; return }
        if (s.mana < sp.cost) { banner = "Не хватает маны"; return }
        when (sp) {
            Spell.TELEPORT -> { armed = !armed; banner = if (armed) "Коснись клетки через одну" else "" }
            Spell.FREEZE -> apply(Engine.freeze(s))
            Spell.INVIS -> apply(Engine.invis(s))
        }
    }

    private fun apply(n: GameState) {
        val old = state
        state = n
        banner = n.msg
        if (old != null) {
            when {
                n.hearts < old.hearts -> Sfx.play(3)
                n.monsters.size < old.monsters.size -> Sfx.play(2)
                n.mana > old.mana || n.mana < old.mana - 1 -> Sfx.play(1)
                n.aika != old.aika -> Sfx.play(0)
            }
        }
        if (n.status == Status.WON) onWon(n)
        if (n.status == Status.LOST || n.status == Status.LOOP) Sfx.play(5)
    }

    private fun onWon(n: GameState) {
        Sfx.play(4)
        walkJob?.cancel()
        val lv = n.level
        var mask = progress.spellMask
        lv.unlocks?.let { mask = mask or (1 shl it.ordinal) }
        val np = progress.copy(levelsDone = maxOf(progress.levelsDone, lv.id),
            spellMask = mask, crystals = progress.crystals + n.crystals)
        progress = np
        viewModelScope.launch { dao.save(np) }
        showDialog(lv.outro) {
            val next = lv.id // index of next level
            if (next < Levels.all.size) startLevel(next) else screen = Screen.MENU
        }
    }

    fun togglePause() { paused = !paused }
    fun toMenu() { walkJob?.cancel(); paused = false; dialog = emptyList(); screen = Screen.MENU }
}
