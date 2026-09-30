package com.leo.lottery

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leo.lottery.core.DemoDrawRepository
import com.leo.lottery.core.DrawResult
import com.leo.lottery.core.Game
import com.leo.lottery.core.Generator
import com.leo.lottery.core.IssueCalendar
import com.leo.lottery.core.Ticket
import com.leo.lottery.core.comboCount
import com.leo.lottery.data.ImageSeed
import com.leo.lottery.data.TicketStore
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LotteryViewModel(app: Application) : AndroidViewModel(app) {

    enum class Tab { GENERATE, DRAW, TICKETS }

    /** 选号页当前生成（票草稿）：stale 时结果区降透明但保留。 */
    data class Generated(
        val ticket: Ticket,
        val zone1: List<Int>,
        val zone2: List<Int>,
        val animKey: Long,
        val saved: Boolean,
        val stale: Boolean,
    )

    data class GenerateUi(
        val game: Game = Game.SSQ,
        val combo: Boolean = false,
        val comboZone1: Int = Game.SSQ.comboMinZone1,
        val seed: ImageSeed.SeedImage? = null,
        val seedBusy: Boolean = false,
        val take: Int = 1,
        val generated: Generated? = null,
    ) {
        val zone1Size: Int get() = if (combo) comboZone1 else game.baseZone1
        val zone2Size: Int get() = game.baseZone2
        val canGenerate: Boolean get() = seed != null && !seedBusy
        val comboNote: Int get() = game.comboCount(zone1Size, zone2Size).toInt()
    }

    data class DrawUi(
        val game: Game = Game.SSQ,
        val issue: String = "",
        val issueList: List<String> = emptyList(),
        val replaying: Boolean = false,
        val revealed: Set<String> = emptySet(),
    ) {
        val revealedKey: String get() = "$game|$issue"
    }

    data class UiState(
        val tab: Tab = Tab.GENERATE,
        val generate: GenerateUi = GenerateUi(),
        val draw: DrawUi = DrawUi(),
        val tickets: List<Ticket> = emptyList(),
        val detail: Ticket? = null,
    )

    sealed interface Event {
        data class Message(val text: String) : Event
        data class Deleted(val ticket: Ticket) : Event
    }

    private val repo = DemoDrawRepository()
    private val store = TicketStore(File(app.filesDir, "tickets.json"))
    private val today: LocalDate get() = LocalDate.now()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _events = Channel<Event>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var animSeq = 0L

    init {
        val ssqIssues = issueListUpToLatest(Game.SSQ)
        _state.value = _state.value.copy(
            tickets = store.load(),
            draw = DrawUi(
                game = Game.SSQ,
                issue = ssqIssues.lastOrNull() ?: IssueCalendar.latestIssue(Game.SSQ, today),
                issueList = ssqIssues,
            ),
        )
    }

    private fun issueListUpToLatest(game: Game): List<String> {
        val latest = IssueCalendar.latestIssue(game, today)
        val year = latest.take(4).toInt()
        return (1..latest.drop(4).toInt()).map { "%d%03d".format(year, it) }
    }

    // ---------- 导航 ----------

    fun selectTab(tab: Tab) {
        _state.value = _state.value.copy(tab = tab)
    }

    fun openDetail(ticket: Ticket) {
        _state.value = _state.value.copy(detail = ticket)
    }

    fun closeDetail() {
        _state.value = _state.value.copy(detail = null)
    }

    fun back(): Boolean {
        val s = _state.value
        return when {
            s.detail != null -> { closeDetail(); true }
            s.tab != Tab.GENERATE -> { selectTab(Tab.GENERATE); true }
            else -> false
        }
    }

    // ---------- 选号 ----------

    fun setGame(game: Game) {
        updateGenerate { g ->
            if (g.game == game) g else {
                g.copy(
                    game = game,
                    comboZone1 = game.comboMinZone1,
                    generated = g.generated?.copy(stale = true),
                )
            }
        }
    }

    fun setCombo(combo: Boolean) {
        updateGenerate { g ->
            if (g.combo == combo) g else g.copy(
                combo = combo,
                comboZone1 = g.game.comboMinZone1,
                generated = g.generated?.copy(stale = true),
            )
        }
    }

    fun setComboZone1(n: Int) {
        updateGenerate { g ->
            val clamped = n.coerceIn(g.game.comboMinZone1, g.game.comboMaxZone1)
            if (g.comboZone1 == clamped) g else g.copy(
                comboZone1 = clamped,
                generated = g.generated?.copy(stale = true),
            )
        }
    }

    fun pickSeed(uri: Uri) {
        updateGenerate { it.copy(seedBusy = true) }
        viewModelScope.launch {
            val seed = withContext(Dispatchers.Default) {
                ImageSeed.decode(getApplication(), uri)
            }
            if (seed == null) {
                updateGenerate { it.copy(seedBusy = false) }
                _events.send(Event.Message("这张图读不出来，换一张试试"))
            } else {
                updateGenerate {
                    it.copy(seed = seed, seedBusy = false, take = 1, generated = null)
                }
            }
        }
    }

    fun generate() = generateInternal(advanceTake = false)

    fun regenerateBatch() = generateInternal(advanceTake = true)

    private fun generateInternal(advanceTake: Boolean) {
        updateGenerate { g ->
            val seed = g.seed ?: return@updateGenerate g
            if (!g.canGenerate) return@updateGenerate g
            val take = if (advanceTake) g.take + 1 else g.take
            val numbers = Generator.generate(seed.fingerprint, g.game, g.zone1Size, g.zone2Size, take)
            animSeq++
            val draft = Ticket(
                id = UUID.randomUUID().toString(),
                game = g.game,
                createdAt = System.currentTimeMillis(),
                seed = seed.fingerprint,
                take = take,
                zone1 = numbers.zone1,
                zone2 = numbers.zone2,
                targetIssue = IssueCalendar.latestIssue(g.game, today),
            )
            g.copy(
                take = take,
                generated = Generated(
                    ticket = draft,
                    zone1 = numbers.zone1,
                    zone2 = numbers.zone2,
                    animKey = animSeq,
                    saved = false,
                    stale = false,
                ),
            )
        }
    }

    /** 存票（按 id 去重）；返回票供导出使用。 */
    fun saveCurrent(): Ticket? {
        val s = _state.value.generate
        val gen = s.generated ?: return null
        if (gen.stale) return null
        if (!gen.saved) {
            val tickets = _state.value.tickets + gen.ticket
            store.save(tickets)
            _state.value = _state.value.copy(
                tickets = tickets,
                generate = s.copy(generated = gen.copy(saved = true)),
            )
            viewModelScope.launch { _events.send(Event.Message("已存入票夹")) }
        }
        return gen.ticket
    }

    fun deleteTicket(ticket: Ticket) {
        val tickets = _state.value.tickets.filterNot { it.id == ticket.id }
        store.save(tickets)
        _state.value = _state.value.copy(
            tickets = tickets,
            detail = if (_state.value.detail?.id == ticket.id) null else _state.value.detail,
        )
        viewModelScope.launch { _events.send(Event.Deleted(ticket)) }
    }

    fun undoDelete(ticket: Ticket) {
        if (_state.value.tickets.any { it.id == ticket.id }) return
        val tickets = _state.value.tickets + ticket
        store.save(tickets)
        _state.value = _state.value.copy(tickets = tickets)
        viewModelScope.launch { _events.send(Event.Message("已恢复")) }
    }

    // ---------- 开奖 ----------

    fun setDrawGame(game: Game) {
        val issues = issueListUpToLatest(game)
        _state.value = _state.value.copy(
            draw = _state.value.draw.copy(
                game = game,
                issueList = issues,
                issue = issues.lastOrNull() ?: IssueCalendar.latestIssue(game, today),
            ),
        )
    }

    fun stepIssue(delta: Int) {
        val d = _state.value.draw
        val idx = d.issueList.indexOf(d.issue) + delta
        if (idx in d.issueList.indices) {
            _state.value = _state.value.copy(draw = d.copy(issue = d.issueList[idx]))
        }
    }

    fun resultOf(game: Game, issue: String): DrawResult? = repo.result(game, issue, today)

    fun startReplay() {
        _state.value = _state.value.copy(draw = _state.value.draw.copy(replaying = true))
    }

    fun finishReplay() {
        val d = _state.value.draw
        _state.value = _state.value.copy(
            draw = d.copy(replaying = false, revealed = d.revealed + d.revealedKey),
        )
    }

    fun ticketsFor(game: Game, issue: String): List<Ticket> =
        _state.value.tickets.filter { it.game == game && it.targetIssue == issue }

    fun goToDraw(ticket: Ticket) {
        val issues = issueListUpToLatest(ticket.game)
        val issue = ticket.targetIssue
        val current = if (issue in issues) issue else (issues.lastOrNull() ?: issue)
        _state.value = _state.value.copy(
            tab = Tab.DRAW,
            detail = null,
            draw = _state.value.draw.copy(game = ticket.game, issueList = issues, issue = current),
        )
    }

    private fun updateGenerate(block: (GenerateUi) -> GenerateUi) {
        _state.value = _state.value.copy(generate = block(_state.value.generate))
    }
}
