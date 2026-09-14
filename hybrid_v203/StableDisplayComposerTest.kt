package com.nameemrooz.journal.writing

import org.junit.Assert.assertEquals
import org.junit.Test

class StableDisplayComposerTest {
    @Test fun punctuatedStablePrefixIsPreservedWhilePartialTextExtends() {
        assertEquals(
            "سلام، دنیا. امروز خوب بود",
            StableDisplayComposer.compose(
                stableRaw = "سلام دنیا",
                stableDisplay = "سلام، دنیا.",
                currentRaw = "سلام دنیا امروز خوب بود",
            )
        )
    }
}
