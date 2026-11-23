package com.nanobuo.myfifty.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nanobuo.myfifty.data.Repository
import com.nanobuo.myfifty.data.SettingsStore
import com.nanobuo.myfifty.data.SupabaseConfig
import com.nanobuo.myfifty.data.ThemeMode
import com.nanobuo.myfifty.domain.Expense
import com.nanobuo.myfifty.domain.ExpenseKind
import com.nanobuo.myfifty.domain.ExpenseWrite
import com.nanobuo.myfifty.domain.Household
import com.nanobuo.myfifty.domain.Ledger
import com.nanobuo.myfifty.domain.Member
import com.nanobuo.myfifty.domain.Recurring
import com.nanobuo.myfifty.io.ImportExport
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

enum class Gate { LOADING, NEEDS_CONFIG, SIGNED_OUT, NO_HOUSEHOLD, READY }

data class HouseholdData(
    val household: Household,
    val members: List<Member> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val loaded: Boolean = false,
) {
    val memberById: Map<String, Member> by lazy { members.associateBy { it.id } }
    fun nameOf(id: String): String = memberById[id]?.name ?: "Ex membro"
    val balances: Map<String, Long> by lazy { Ledger.balances(expenses, members.map { it.id }) }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val settings = SettingsStore(app)

    val theme: StateFlow<ThemeMode> = settings.theme.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)

    private val configLoaded = MutableStateFlow(false)
    private val _repo = MutableStateFlow<Repository?>(null)
    val repo: StateFlow<Repository?> = _repo.asStateFlow()

    private val session: StateFlow<SessionStatus?> = _repo
        .flatMapLatest { it?.sessionStatus ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _households = MutableStateFlow<List<Household>?>(null)
    val households: StateFlow<List<Household>?> = _households.asStateFlow()

    private val _data = MutableStateFlow<HouseholdData?>(null)
    val data: StateFlow<HouseholdData?> = _data.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    /** Errore di inizializzazione del client (configurazione di build errata). */
    val initError: StateFlow<String?> = _initError.asStateFlow()

    private val _loadError = MutableStateFlow<String?>(null)
    val loadError: StateFlow<String?> = _loadError.asStateFlow()

    fun retryLoad() = viewModelScope.launch { _loadError.value = null; loadHouseholds() }

    private val _myName = MutableStateFlow("")
    val myName: StateFlow<String> = _myName.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var previewUserId: String? = null
    val myId: String? get() = _repo.value?.currentUserId ?: previewUserId

    /** Solo per test e screenshot: mostra dati finti senza Supabase. */
    @androidx.annotation.VisibleForTesting
    fun showPreview(d: HouseholdData, userId: String) {
        previewUserId = userId
        _data.value = d
        _households.value = listOf(d.household)
        _myName.value = d.nameOf(userId)
    }
    val myEmail: String? get() = _repo.value?.currentEmail

    val gate: StateFlow<Gate> = combine(configLoaded, _repo, session, _households) { loaded, repo, s, hh ->
        when {
            !loaded -> Gate.LOADING
            repo == null -> Gate.NEEDS_CONFIG
            s == null || s is SessionStatus.Initializing -> Gate.LOADING
            s !is SessionStatus.Authenticated -> Gate.SIGNED_OUT
            hh == null -> Gate.LOADING
            hh.isEmpty() -> Gate.NO_HOUSEHOLD
            else -> Gate.READY
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Gate.LOADING)

    private var realtimeJob: Job? = null

    init {
        viewModelScope.launch {
            val cfg = SupabaseConfig.fromBuild()
            if (cfg == null) {
                _initError.value = "Questa versione è stata compilata senza i dati del server Supabase."
            } else {
                runCatching { Repository(cfg) }
                    .onSuccess { _repo.value = it }
                    .onFailure { _initError.value = "Impossibile collegarsi a ${cfg.url}: ${it.message ?: it}" }
            }
            configLoaded.value = true
        }
        viewModelScope.launch {
            session.map { it is SessionStatus.Authenticated }.distinctUntilChanged().collect { authed ->
                if (authed) {
                    loadHouseholds()
                    _myName.value = runCatching { _repo.value?.myDisplayName() }.getOrNull().orEmpty()
                } else {
                    _households.value = null
                    _data.value = null
                }
            }
        }
        viewModelScope.launch {
            combine(_households, settings.householdId) { hh, sel ->
                hh?.let { list -> list.firstOrNull { it.id == sel } ?: list.firstOrNull() }
            }.distinctUntilChanged { a, b -> a?.id == b?.id }.collect { h -> openHousehold(h) }
        }
    }

    private fun openHousehold(h: Household?) {
        realtimeJob?.cancel()
        if (previewUserId != null) return
        if (h == null) { _data.value = null; return }
        _data.value = HouseholdData(h)
        val repo = _repo.value ?: return
        realtimeJob = viewModelScope.launch {
            launch { refresh(showSpinner = false) }
            repo.changes(h.id).debounce(400).collect { refresh(showSpinner = false) }
        }
    }

    // ------------------------------------------------------------ loading

    private suspend fun loadHouseholds() {
        val repo = _repo.value ?: return
        runCatching { repo.households() }
            .onSuccess { _households.value = it; _loadError.value = null }
            .onFailure {
                if (it is kotlinx.coroutines.CancellationException) throw it
                if (_households.value == null) _loadError.value = friendly(it) else report(it)
            }
    }

    fun refresh() = viewModelScope.launch { refresh(showSpinner = true) }

    private suspend fun refresh(showSpinner: Boolean) {
        val repo = _repo.value ?: return
        val current = _data.value ?: return
        if (showSpinner) _refreshing.value = true
        try {
            val id = current.household.id
            val hh = repo.households()
            _households.value = hh
            val h = hh.firstOrNull { it.id == id } ?: return
            val members = repo.members(id)
            val expenses = repo.expenses(id)
            if (_data.value?.household?.id == id) {
                _data.value = HouseholdData(h, members, expenses, loaded = true)
            }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            report(t)
        } finally {
            _refreshing.value = false
        }
    }

    // ------------------------------------------------------------ helpers

    private fun report(t: Throwable) { _messages.trySend(friendly(t)) }

    fun toast(msg: String) { _messages.trySend(msg) }

    private fun launchBusy(block: suspend (Repository) -> Unit): Job = viewModelScope.launch {
        val repo = _repo.value ?: return@launch
        _busy.value = true
        try { block(repo) } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            report(t)
        } finally { _busy.value = false }
    }

    // ------------------------------------------------------------ config / tema

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { settings.setTheme(mode) }


    // ------------------------------------------------------------ auth

    fun signIn(email: String, password: String) = launchBusy { it.signIn(email, password) }

    fun signUp(name: String, email: String, password: String) = launchBusy {
        val loggedIn = it.signUp(name, email, password)
        if (!loggedIn) _messages.send("Registrazione completata: conferma l'indirizzo dal link che ti abbiamo inviato via email, poi accedi.")
    }

    fun resetPassword(email: String) = launchBusy {
        it.resetPassword(email)
        _messages.send("Se l'indirizzo è registrato riceverai un'email per reimpostare la password.")
    }

    fun signOut() = launchBusy {
        it.signOut()
        settings.setHouseholdId(null)
    }

    fun updateName(name: String) = launchBusy {
        it.updateDisplayName(name)
        _myName.value = name.trim()
        refresh(false)
    }

    // ------------------------------------------------------------ case

    fun createHousehold(name: String) = launchBusy {
        val h = it.createHousehold(name)
        settings.setHouseholdId(h.id)
        loadHouseholds()
    }

    fun joinHousehold(code: String) = launchBusy {
        val h = it.joinHousehold(code)
        settings.setHouseholdId(h.id)
        loadHouseholds()
        _messages.send("Sei entrato in \"${h.name}\"")
    }

    fun selectHousehold(id: String) = viewModelScope.launch { settings.setHouseholdId(id) }

    fun renameHousehold(name: String) = launchBusy {
        val h = _data.value?.household ?: return@launchBusy
        it.renameHousehold(h.id, name)
        refresh(false)
    }

    fun regenerateInvite() = launchBusy {
        val h = _data.value?.household ?: return@launchBusy
        it.regenerateInvite(h.id)
        refresh(false)
        _messages.send("Nuovo codice invito generato")
    }

    fun leaveHousehold() = launchBusy {
        val h = _data.value?.household ?: return@launchBusy
        it.leaveHousehold(h.id)
        settings.setHouseholdId(null)
        loadHouseholds()
    }

    // ------------------------------------------------------------ movimenti

    fun saveExpense(e: ExpenseWrite, onDone: () -> Unit) = launchBusy {
        if (e.id == null) it.insertExpense(e) else it.updateExpense(e)
        refresh(false)
        onDone()
    }

    fun deleteExpense(id: String, onDone: () -> Unit) = launchBusy {
        it.deleteExpense(id)
        refresh(false)
        onDone()
    }

    fun pendingRecurring(month: YearMonth): List<Expense> =
        _data.value?.let { Recurring.pending(it.expenses, month) }.orEmpty()

    fun addRecurring(month: YearMonth) = launchBusy {
        val list = pendingRecurring(month).map { e -> Recurring.copyInto(e, month) }
        if (list.isEmpty()) return@launchBusy
        it.upsertExpenses(list)
        refresh(false)
        _messages.send("Aggiunte ${list.size} spese ricorrenti")
    }

    fun recordTransfer(t: Ledger.Transfer) = launchBusy {
        val h = _data.value?.household ?: return@launchBusy
        it.insertExpense(
            ExpenseWrite(
                id = UUID.randomUUID().toString(), householdId = h.id, kind = ExpenseKind.SETTLEMENT.key,
                description = "Saldo", category = "other", amountCents = t.amountCents,
                date = LocalDate.now().toString(), paidBy = t.from, splitAmong = listOf(t.to),
                recurring = false, notes = "",
            )
        )
        refresh(false)
        _messages.send("Saldo registrato")
    }

    // ------------------------------------------------------------ import / export

    fun exportJson(): String? = _data.value?.let { ImportExport.exportJson(it.household, it.members, it.expenses) }

    fun exportCsv(): String? = _data.value?.let { ImportExport.exportCsv(it.members, it.expenses) }

    fun prepareImport(text: String): Result<ImportExport.ImportResult> = runCatching {
        val d = requireNotNull(_data.value) { "Nessuna casa selezionata" }
        ImportExport.parse(text, d.household, d.members, d.expenses)
    }

    fun applyImport(result: ImportExport.ImportResult) = launchBusy {
        it.upsertExpenses(result.toWrite)
        refresh(false)
        _messages.send("Import completato: ${result.newCount} nuovi, ${result.updatedCount} aggiornati")
    }

    suspend fun awaitReady() { gate.first { it != Gate.LOADING } }

    companion object {
        fun friendly(t: Throwable): String {
            val m = (t.message ?: t.toString())
            return when {
                m.contains("Invalid login credentials", true) -> "Email o password non corretti"
                m.contains("Email not confirmed", true) -> "Devi prima confermare l'email (controlla la posta)"
                m.contains("User already registered", true) -> "Esiste già un account con questa email"
                m.contains("Password should be", true) -> "La password deve avere almeno 6 caratteri"
                m.contains("codice invito non valido", true) -> "Codice invito non valido"
                m.contains("Unable to resolve host", true) || m.contains("UnknownHost", true) ||
                    m.contains("timeout", true) || m.contains("Failed to connect", true) ->
                    "Connessione assente o server non raggiungibile"
                m.contains("JWT", true) -> "Sessione scaduta, accedi di nuovo"
                m.contains("relation", true) && m.contains("does not exist", true) ||
                    m.contains("Could not find the function", true) || m.contains("schema cache", true) ->
                    "Il database non è configurato: esegui supabase/schema.sql nel progetto Supabase"
                else -> m.lineSequence().firstOrNull()?.take(200) ?: "Errore sconosciuto"
            }
        }
    }
}
