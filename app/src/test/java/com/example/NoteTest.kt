package com.example

import com.example.data.Note
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class NoteTest {

    @Test
    fun `default note has empty fields and a unique UUID`() {
        val a = Note(title = "", body = "")
        val b = Note(title = "", body = "")
        assertEquals("", a.title)
        assertEquals("", a.body)
        assertEquals("", a.tags)
        assertFalse(a.isPinned)
        assertFalse(a.cloudSynced)
        // Each default note gets its own UUID so cloud sync never collides.
        assertNotEquals(a.noteUuid, b.noteUuid)
    }

    @Test
    fun `checklist JSON round-trip exposes unchecked items`() {
        val checklist = JSONArray().apply {
            put(org.json.JSONObject().put("text", "Buy milk").put("checked", false))
            put(org.json.JSONObject().put("text", "Ship PR").put("checked", true))
            put(org.json.JSONObject().put("text", "Write README").put("checked", false))
        }
        val note = Note(title = "Plan", body = "", checklistJson = checklist.toString())

        val parsed = JSONArray(note.checklistJson!!)
        val openItems = (0 until parsed.length())
            .map { parsed.getJSONObject(it) }
            .filter { !it.optBoolean("checked") }
            .map { it.optString("text") }

        assertEquals(listOf("Buy milk", "Write README"), openItems)
    }

    @Test
    fun `pin toggle flips via copy without mutating the original`() {
        val note = Note(title = "t", body = "b", isPinned = false)
        val pinned = note.copy(isPinned = true)
        assertFalse(note.isPinned)
        assertTrue(pinned.isPinned)
        assertEquals(note.noteUuid, pinned.noteUuid)
    }
}
