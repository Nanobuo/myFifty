package com.nanobuo.myfifty

import com.nanobuo.myfifty.data.UpdateChecker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test fun versionOrdering() {
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0.0"))
        assertTrue(UpdateChecker.isNewer("1.10.0", "1.9.9"))
        assertTrue(UpdateChecker.isNewer("v1.0.0", "1.0.0-dev.12"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "1.0.0-dev.12-debug"))
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("1.0.0", "1.1.0-dev.3"))
        assertEquals(0, UpdateChecker.compare("1.0", "1.0.0"))
    }

    @Test fun parsesGithubRelease() {
        val json = """
            {"tag_name":"v1.2.0","body":"Novità","draft":false,"prerelease":false,
             "html_url":"https://github.com/o/r/releases/tag/v1.2.0",
             "assets":[{"name":"myFifty-debug.apk","browser_download_url":"https://x/debug.apk","size":1},
                       {"name":"myFifty-1.2.0.apk","browser_download_url":"https://x/app.apk","size":123}]}
        """.trimIndent()
        val r = UpdateChecker.parse(json)!!
        assertEquals("1.2.0", r.version)
        assertEquals("https://x/app.apk", r.apkUrl)
        assertEquals(123L, r.apkSize)
        assertNull(UpdateChecker.parse(json.replace("\"prerelease\":false", "\"prerelease\":true")))
    }
}
