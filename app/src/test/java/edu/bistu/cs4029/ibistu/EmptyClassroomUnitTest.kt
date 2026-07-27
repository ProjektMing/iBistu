package edu.bistu.cs4029.ibistu

import edu.bistu.cs4029.ibistu.schedule.CampusCodes
import edu.bistu.cs4029.ibistu.schedule.EmptyClassroom
import edu.bistu.cs4029.ibistu.schedule.EmptyClassroomQuery
import edu.bistu.cs4029.ibistu.schedule.sectionDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 空教室数据模型的 JVM 单元测试（无需 Android 设备）。
 */
class EmptyClassroomUnitTest {

    // ── EmptyClassroom ────────────────────────────────────────

    @Test
    fun emptyClassroom_allFields() {
        val room = EmptyClassroom(
            name = "WLA-106",
            buildingCode = "501",
            buildingName = "文理楼A座",
            campusCode = "10",
            campusName = "沙河校区",
            floor = 1,
            classSeats = 40,
            examSeats = 20,
            typeCode = "02",
            typeName = "多媒体",
            roomCode = "050101",
            id = "abc-123",
            note = "智慧教室",
            allowSchedule = true,
            allowBorrow = false,
            allowExam = true
        )

        assertEquals("WLA-106", room.name)
        assertEquals(1, room.floor)
        assertEquals(40, room.classSeats)
        assertEquals(20, room.examSeats)
        assertEquals("智慧教室", room.note)
        assertTrue(room.allowSchedule)
        assertFalse(room.allowBorrow)
        assertTrue(room.allowExam)
    }

    @Test
    fun emptyClassroom_emptyNote() {
        val room = EmptyClassroom(
            name = "XXB-301",
            buildingCode = "503",
            buildingName = "信息楼B座",
            campusCode = "10",
            campusName = "沙河校区",
            floor = 3,
            classSeats = 60,
            examSeats = 45,
            typeCode = "01",
            typeName = "普通",
            roomCode = "050301",
            id = "def-456",
            note = "",
            allowSchedule = true,
            allowBorrow = true,
            allowExam = false
        )

        assertEquals("", room.note)
        assertFalse(room.allowExam)
    }

    // ── EmptyClassroomQuery ───────────────────────────────────

    @Test
    fun emptyClassroomQuery_defaultMinSeats() {
        val query = EmptyClassroomQuery(
            campusCode = "10",
            campusName = "沙河校区",
            startDate = "2026-07-06",
            endDate = "2026-07-06",
            startSection = 3,
            endSection = 3
        )
        assertEquals("10", query.campusCode)
        assertEquals(3, query.startSection)
        assertEquals(3, query.endSection)
        assertNull(query.buildingCode)
        assertNull(query.roomName)
    }

    @Test
    fun emptyClassroomQuery_withOptionalFields() {
        val query = EmptyClassroomQuery(
            campusCode = "20",
            campusName = "小营校区",
            startDate = "2026-07-06",
            endDate = "2026-07-20",
            startSection = 1,
            endSection = 12,
            buildingCode = "501",
            roomName = "WLA"
        )
        assertEquals("20", query.campusCode)
        assertEquals("小营校区", query.campusName)
        assertEquals("2026-07-06", query.startDate)
        assertEquals("2026-07-20", query.endDate)
        assertEquals(1, query.startSection)
        assertEquals(12, query.endSection)
        assertEquals("501", query.buildingCode)
        assertEquals("WLA", query.roomName)
    }

    // ── CampusCodes ───────────────────────────────────────────

    @Test
    fun campusCodes_knownCampuses() {
        assertEquals("10", CampusCodes.codeOf("沙河校区"))
        assertEquals("20", CampusCodes.codeOf("小营校区"))
    }

    @Test
    fun campusCodes_unknownCampus() {
        assertNull(CampusCodes.codeOf("未知校区"))
        assertNull(CampusCodes.codeOf(""))
    }

    @Test
    fun campusCodes_nameOf() {
        assertEquals("沙河校区", CampusCodes.nameOf("10"))
        assertEquals("小营校区", CampusCodes.nameOf("20"))
        assertNull(CampusCodes.nameOf("99"))
    }

    // ── sectionDisplayName ────────────────────────────────────

    @Test
    fun sectionDisplayName_values() {
        assertEquals("第1节", sectionDisplayName(1))
        assertEquals("第8节", sectionDisplayName(8))
        assertEquals("第12节", sectionDisplayName(12))
    }
}
