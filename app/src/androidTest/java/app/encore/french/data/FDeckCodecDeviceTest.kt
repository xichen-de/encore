package app.encore.french.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** Runs on Android so the platform JSON implementation is exercised. */
class FDeckCodecDeviceTest {
    @Test fun parsesUtf8PhrasesAccentsAndOptionalGender() {
        val deck = FDeckCodec.parse("""{"format":"fdeck","version":1,"name":"Assimil","cards":[
          {"front":"à côté de","back":"next to / beside"},
          {"front":"l’église où se dépêcher","back":"the church where one hurries","gender":"f"}]}
        """.trimIndent())
        assertEquals(2, deck.cards.size)
        assertNull(deck.cards.first().gender)
        assertEquals("f", deck.cards.last().gender)
    }

    @Test fun rejectsMalformedSchemaAndFutureVersions() {
        assertThrows(DeckParseException.Invalid::class.java) { FDeckCodec.parse("not json") }
        assertThrows(DeckParseException.Invalid::class.java) {
            FDeckCodec.parse("""{"format":"fdeck","version":1,"name":"x","cards":[{"front":"où"}]}""")
        }
        assertThrows(DeckParseException.UnsupportedVersion::class.java) {
            FDeckCodec.parse("""{"format":"fdeck","version":2,"name":"x","cards":[]}""")
        }
    }

    @Test fun rejectsInvalidGender() {
        assertThrows(DeckParseException.Invalid::class.java) {
            FDeckCodec.parse("""{"format":"fdeck","version":1,"name":"x","cards":[{"front":"église","back":"church","gender":"x"}]}""")
        }
    }
}
