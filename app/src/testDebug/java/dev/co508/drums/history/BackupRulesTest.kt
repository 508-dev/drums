package dev.co508.drums.history

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.co508.drums.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BackupRulesTest {
    @Test fun backupEnabledAndBothRuleFormatsIncludeOnlyDurableUserData() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertTrue(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP != 0)
        for ((resource, copies) in listOf(R.xml.backup_rules to 1, R.xml.data_extraction_rules to 2)) {
            val entries = mutableListOf<Pair<String?, String?>>()
            context.resources.getXml(resource).use { parser ->
                while (parser.next() != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType != XmlPullParser.START_TAG) continue
                    if (parser.name ==
                        "include"
                    ) {
                        entries +=
                            parser.getAttributeValue(null, "domain") to parser.getAttributeValue(null, "path")
                    }
                    if (parser.name ==
                        "cloud-backup"
                    ) {
                        assertEquals("true", parser.getAttributeValue(null, "disableIfNoEncryptionCapabilities"))
                    }
                }
            }
            assertEquals(copies * 2, entries.size)
            assertEquals(copies, entries.count { it == "database" to "." })
            assertEquals(copies, entries.count { it == "file" to "datastore/" })
        }
    }
}
