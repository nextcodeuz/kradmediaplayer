package space.manus.kradusmedia.t004358040099515

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Test
import space.manus.kradusmedia.t004358040099515.music.parseFoundJson

/**
 * Covers the hand parser for native scan output [{"p":path,"s":size},...].
 * It must agree with Gson on valid input and return [] (never throw) on junk.
 */
class FoundJsonParseTest {

    @Test
    fun parsesSingleEntry() {
        assertEquals(
            listOf("/Music/a b.mp3"),
            parseFoundJson("""[{"p":"/Music/a b.mp3","s":12345}]""")
        )
    }

    @Test
    fun parsesEmptyArray() {
        assertEquals(emptyList<String>(), parseFoundJson("[]"))
        assertEquals(emptyList<String>(), parseFoundJson("  [ \n\t]  "))
    }

    @Test
    fun handlesEscapesAndUnicode() {
        // quote, backslash, newline, é (U+00E9), emoji surrogate pair.
        val json = """[{"p":"/M\"u\\s\ni\u00e9🎵.mp3","s":7}]"""
        assertEquals(listOf("/M\"u\\s\nié🎵.mp3"), parseFoundJson(json))
    }

    @Test
    fun toleratesKeyOrderAndExtraKeys() {
        assertEquals(
            listOf("/x.mp3"),
            parseFoundJson("""[{"s":9,"p":"/x.mp3","zzz":true}]""")
        )
    }

    @Test
    fun rejectsMalformedWithoutThrowing() {
        listOf(
            "",
            "   ",
            "[",
            "[{",
            """[{"p":"/a.mp3"}""",
            """[{"p":"/a.mp3","s":}]""",
            """[{"p":"/a.mp3","s":1},]""",
            """[{"p":"/a.mp3","s":1}]trailing""",
            """[{"p":"/a.mp3","s":1}, {"p":"/b.mp3","s":2}""",
            """[{"p":"/a\q.mp3","s":1}]""",
            """[{"p":"/a.mp3","s":1""",
            "[{\"p\":\"/a" + 1.toChar() + ".mp3\",\"s\":1}]", // raw control char
            "[null]",
            "[\"/a.mp3\"]"
        ).forEach { bad ->
            assertEquals("input: $bad", emptyList<String>(), parseFoundJson(bad))
        }
    }

    @Test
    fun matchesGsonOnBulkOutput() {
        val rnd = java.util.Random(42)
        val sb = StringBuilder("[")
        val want = ArrayList<String>()
        repeat(5000) { i ->
            if (i > 0) sb.append(',')
            val tail = if (i % 7 == 0) "qo‘shiq _mix-$i.mp3" else "track-$i.mp3"
            val path = "/Music/Sub $i/$tail"
            want.add(path)
            sb.append("{\"p\":")
            sb.append(Gson().toJson(path))
            sb.append(",\"s\":")
            sb.append(rnd.nextInt(20_000_000))
            sb.append('}')
        }
        sb.append(']')
        val json = sb.toString()
        // Reference: Gson tree.
        val type = object : TypeToken<List<Map<String, Any?>>>() {}.type
        val ref: List<Map<String, Any?>> = Gson().fromJson(json, type)
        assertEquals(ref.map { it["p"] }, parseFoundJson(json))
    }
}
