package com.nanobuo.myfifty.data

import com.nanobuo.myfifty.domain.Expense
import com.nanobuo.myfifty.domain.ExpenseWrite
import com.nanobuo.myfifty.domain.Household
import com.nanobuo.myfifty.domain.Member
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
    prettyPrint = false
}

@Serializable
private data class MemberRow(
    @SerialName("user_id") val userId: String,
    val role: String = "member",
    val profiles: ProfileRow? = null,
)

@Serializable
private data class ProfileRow(val id: String? = null, @SerialName("display_name") val displayName: String = "")

/** Accesso a Supabase: autenticazione, case, membri, movimenti e realtime. */
class Repository(val config: SupabaseConfig) {

    val client: SupabaseClient = createSupabaseClient(config.url, config.anonKey) {
        defaultSerializer = KotlinXSerializer(AppJson)
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    val currentUserId: String? get() = client.auth.currentUserOrNull()?.id
    val currentEmail: String? get() = client.auth.currentUserOrNull()?.email

    // ---------------- Auth ----------------

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) { this.email = email.trim(); this.password = password }
    }

    /** @return true se l'utente è già loggato (conferma email disattivata). */
    suspend fun signUp(name: String, email: String, password: String): Boolean {
        client.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject { put("display_name", name.trim()) }
        }
        return client.auth.currentSessionOrNull() != null
    }

    suspend fun resetPassword(email: String) = client.auth.resetPasswordForEmail(email.trim())

    suspend fun signOut() = client.auth.signOut()

    suspend fun myDisplayName(): String {
        val id = currentUserId ?: return ""
        return client.from("profiles").select { filter { eq("id", id) } }
            .decodeSingleOrNull<ProfileRow>()?.displayName.orEmpty()
    }

    suspend fun updateDisplayName(name: String) {
        val id = currentUserId ?: return
        client.from("profiles").update({ set("display_name", name.trim()) }) { filter { eq("id", id) } }
    }

    // ---------------- Case ----------------

    suspend fun households(): List<Household> =
        client.from("households").select { order("created_at", Order.ASCENDING) }.decodeList()

    suspend fun createHousehold(name: String): Household =
        client.postgrest.rpc("create_household", buildJsonObject { put("p_name", name.trim()) }).decodeAs()

    suspend fun joinHousehold(code: String): Household =
        client.postgrest.rpc("join_household", buildJsonObject { put("p_code", code.trim().uppercase()) }).decodeAs()

    suspend fun regenerateInvite(householdId: String): String =
        client.postgrest.rpc("regenerate_invite_code", buildJsonObject { put("p_household", householdId) }).decodeAs()

    suspend fun leaveHousehold(householdId: String) {
        client.postgrest.rpc("leave_household", buildJsonObject { put("p_household", householdId) })
    }

    suspend fun renameHousehold(householdId: String, name: String) {
        client.from("households").update({ set("name", name.trim()) }) { filter { eq("id", householdId) } }
    }

    suspend fun members(householdId: String): List<Member> =
        client.from("household_members")
            .select(Columns.raw("user_id, role, profiles(id, display_name)")) {
                filter { eq("household_id", householdId) }
            }
            .decodeList<MemberRow>()
            .map { Member(it.userId, it.profiles?.displayName?.ifBlank { null } ?: "Membro", it.role) }
            .sortedBy { it.name.lowercase() }

    // ---------------- Movimenti ----------------

    suspend fun expenses(householdId: String): List<Expense> {
        val page = 1000L
        val out = mutableListOf<Expense>()
        var from = 0L
        while (true) {
            val chunk = client.from("expenses").select {
                filter { eq("household_id", householdId) }
                order("expense_date", Order.DESCENDING)
                order("created_at", Order.DESCENDING)
                range(from, from + page - 1)
            }.decodeList<Expense>()
            out += chunk
            if (chunk.size < page) break
            from += page
        }
        return out
    }

    suspend fun insertExpense(e: ExpenseWrite) {
        client.from("expenses").insert(e.copy(id = e.id ?: UUID.randomUUID().toString()))
    }

    suspend fun updateExpense(e: ExpenseWrite) {
        val id = requireNotNull(e.id)
        client.from("expenses").update(e) { filter { eq("id", id) } }
    }

    suspend fun upsertExpenses(list: List<ExpenseWrite>) {
        list.chunked(500).forEach { client.from("expenses").upsert(it) }
    }

    suspend fun deleteExpense(id: String) {
        client.from("expenses").delete { filter { eq("id", id) } }
    }

    /**
     * Emette un evento ogni volta che un altro membro (o questo dispositivo)
     * modifica movimenti o membri della casa.
     */
    fun changes(householdId: String): Flow<Unit> = callbackFlow {
        val channel = client.channel("household-$householdId-${UUID.randomUUID()}")
        val expenses = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "expenses"
            filter("household_id", FilterOperator.EQ, householdId)
        }
        // Gli eventi DELETE non sono filtrabili lato server: ascoltiamo tutto e ricarichiamo.
        val deletes = channel.postgresChangeFlow<PostgresAction.Delete>(schema = "public") {
            table = "expenses"
        }
        val members = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "household_members"
            filter("household_id", FilterOperator.EQ, householdId)
        }
        val job = launch { merge(expenses, deletes, members).collect { trySend(Unit) } }
        runCatching { channel.subscribe() }
        awaitClose {
            job.cancel()
            CoroutineScope(Dispatchers.IO + NonCancellable).launch {
                runCatching { client.realtime.removeChannel(channel) }
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        runCatching { client.realtime.removeAllChannels() }
        runCatching { client.close() }
    }
}
