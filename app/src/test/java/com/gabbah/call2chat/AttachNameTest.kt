package com.gabbah.call2chat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AttachNameTest {
    @Test fun keepsOriginalNameAndMigratesOldFiles() {
        assertEquals("Catalogo_2026.pdf", Attachments.safeName("Catalogo/2026", "pdf"))
        assertEquals("Guida.pdf", Attachments.safeName("Guida.pdf", "pdf"))
        val app = ApplicationProvider.getApplicationContext<Application>()
        val dir = File(app.filesDir, "attach").apply { mkdirs() }
        File(dir, "pdf_1795862142569.pdf").writeText("y")
        app.getSharedPreferences("call2chat", 0).edit().putString("attachments",
            """[{"k":"pdf","f":"pdf_1795862142569.pdf","n":"Guida-sui-contratti-2025.pdf"}]""").apply()
        val item = Attachments.list(app).single()
        assertEquals("pdf_1795862142569/Guida-sui-contratti-2025.pdf", item.file)
        assertTrue(Attachments.file(app, item).exists())
        assertTrue(Attachments.uris(app).single().toString().endsWith("/Guida-sui-contratti-2025.pdf"))
    }
}
