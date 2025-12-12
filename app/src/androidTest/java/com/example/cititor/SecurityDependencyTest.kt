package com.example.cititor

import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * This is not a real test. It's a diagnostic tool.
 * Its only purpose is to see if the test compiler can find the MasterKey class.
 * If this class fails to compile, the problem is a fundamental dependency issue.
 */
@RunWith(AndroidJUnit4::class)
class SecurityDependencyTest {

    @Test
    fun masterKeyClass_shouldBeAvailable() {
        // We don't need to do anything with the key.
        // We just need to prove that the compiler can find the MasterKey class.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        // The test will pass if this line is reached without a compilation error.
        assert(true)
    }
}