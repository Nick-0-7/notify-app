package com.notify.app.core

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.okhttp.OkHttp

object SupabaseClientProvider {
    // Live Supabase Project Configuration
    var supabaseUrl: String = "https://gurgykcakzimfmeainqc.supabase.co"
    var supabaseAnonKey: String = "sb_publishable_GO6rsvBOH_PCQsuhpPB5kw_KA3w9zhl"

    val isConfigured: Boolean
        get() = supabaseUrl.contains(".supabase.co") && !supabaseUrl.contains("your-project-ref") && supabaseAnonKey.length > 20

    private var clientInstance: SupabaseClient? = null

    val client: SupabaseClient
        get() {
            if (clientInstance == null) {
                clientInstance = createSupabaseClient(
                    supabaseUrl = supabaseUrl,
                    supabaseKey = supabaseAnonKey
                ) {
                    httpEngine = OkHttp.create()
                    install(Auth) {
                        sessionManager = try {
                            io.github.jan.supabase.auth.SettingsSessionManager()
                        } catch (_: Throwable) {
                            io.github.jan.supabase.auth.MemorySessionManager()
                        }
                        codeVerifierCache = try {
                            io.github.jan.supabase.auth.SettingsCodeVerifierCache()
                        } catch (_: Throwable) {
                            io.github.jan.supabase.auth.MemoryCodeVerifierCache()
                        }
                    }
                    install(Postgrest)
                    install(Storage)
                    install(Realtime)
                }
            }
            return clientInstance!!
        }

    val auth get() = client.auth
    val postgrest get() = client.postgrest
    val storage get() = client.storage
    val realtime get() = client.realtime

    fun reconfigure(url: String, anonKey: String) {
        supabaseUrl = url.trim()
        supabaseAnonKey = anonKey.trim()
        clientInstance = null
    }
}
