package com.example.data.local

import android.content.Context
import com.example.data.model.UserAccountSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.util.UUID

data class GhostAccount(val id: String, val session: UserAccountSession)

/** Local slot IDs are independent of Telegram user IDs and phone numbers. */
class GhostAccounts private constructor(context: Context) {
    private val context = context.applicationContext
    private val prefs = this.context.getSharedPreferences("ghostgram_accounts", Context.MODE_PRIVATE)
    private val _activeId = MutableStateFlow(prefs.getString("active_id", DEFAULT_ACCOUNT) ?: DEFAULT_ACCOUNT)
    val activeId = _activeId.asStateFlow()
    private val _accounts = MutableStateFlow(readAccounts())
    val accounts = _accounts.asStateFlow()

    private fun ids(): List<String> {
        val array = try { JSONArray(prefs.getString("ids", "[\"default\"]")) } catch (_: Exception) { JSONArray("[\"default\"]") }
        return (0 until array.length()).map { array.getString(it) }.filter(::isValidId).distinct()
            .ifEmpty { listOf(DEFAULT_ACCOUNT) }
    }

    private fun readAccounts(): List<GhostAccount> = ids().map { id ->
        val accountPrefs = context.getSharedPreferences(preferencesName(id), Context.MODE_PRIVATE)
        GhostAccount(id, UserAccountSession(
            isLoggedIn = accountPrefs.getBoolean("user_is_logged_in", false),
            phoneNumber = accountPrefs.getString("user_phone", "") ?: "",
            userName = accountPrefs.getString("user_name", "") ?: "",
            userHandle = accountPrefs.getString("user_handle", "") ?: "",
            userId = accountPrefs.getLong("user_id", 0),
            profilePicPath = accountPrefs.getString("user_profile_pic", null)
        ))
    }

    @Synchronized fun refresh() { _accounts.value = readAccounts() }

    @Synchronized fun addAccount(): String {
        // Reuse an unfinished sign-in instead of accumulating abandoned sessions.
        val pending = readAccounts().firstOrNull { !it.session.isLoggedIn && it.session.userId == 0L }
        val id = pending?.id ?: UUID.randomUUID().toString()
        if (id !in ids()) prefs.edit().putString("ids", JSONArray(ids() + id).toString()).commit()
        refresh()
        switchTo(id)
        return id
    }

    @Synchronized fun switchTo(id: String) {
        require(id in ids()) { "Unknown account" }
        prefs.edit().putString("active_id", id).commit()
        _activeId.value = id
    }

    companion object {
        const val DEFAULT_ACCOUNT = "default"
        @Volatile private var instance: GhostAccounts? = null
        fun get(context: Context): GhostAccounts = synchronized(this) {
            val application = context.applicationContext
            instance?.takeIf { it.context === application }
                ?: GhostAccounts(application).also { instance = it }
        }
        fun isValidId(id: String): Boolean = id == DEFAULT_ACCOUNT ||
            id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"))
        fun preferencesName(id: String): String {
            require(isValidId(id))
            return if (id == DEFAULT_ACCOUNT) "ghostgram_prefs" else "ghostgram_prefs_$id"
        }
        fun databaseName(id: String): String {
            require(isValidId(id))
            return if (id == DEFAULT_ACCOUNT) "ghostgram_database" else "ghostgram_database_$id"
        }
        fun tdlibDirectory(id: String): String {
            require(isValidId(id))
            return if (id == DEFAULT_ACCOUNT) "tdlib" else "tdlib_accounts/$id"
        }
    }
}
