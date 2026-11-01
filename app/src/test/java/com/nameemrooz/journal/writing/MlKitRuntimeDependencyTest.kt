package com.nameemrooz.journal.writing

import org.junit.Assert.assertNotNull
import org.junit.Test

class MlKitRuntimeDependencyTest {
    @Test fun google_client_runtime_dependencies_remain_linkable() {
        // ML Kit common's transport constructor references this class while a
        // client is created, before an unsupported-device status can be returned.
        assertNotNull(Class.forName("com.google.android.datatransport.cct.CCTDestination"))
    }
}
