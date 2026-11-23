package com.nanobuo.myfifty

import com.nanobuo.myfifty.data.SupabaseConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigTest {
    @Test fun normalizesUrls() {
        val n = SupabaseConfig::normalizeUrl
        assertEquals("https://abc.supabase.co", n("https://abc.supabase.co"))
        assertEquals("https://abc.supabase.co", n(" abc.supabase.co/ "))
        assertEquals("https://abc.supabase.co", n("https://abc.supabase.co/rest/v1/"))
        assertEquals("https://abc.supabase.co", n("\"https://abc.supabase.co/auth/v1\""))
        assertEquals("http://10.0.2.2:54321", n("http://10.0.2.2:54321/"))
        assertEquals("", n("  "))
    }
}
