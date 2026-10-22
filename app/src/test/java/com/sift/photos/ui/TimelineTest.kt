package com.sift.photos.ui

import com.sift.photos.data.DaySection
import com.sift.photos.data.PhotoArt
import com.sift.photos.data.PhotoItem
import com.sift.photos.data.PhotoRef
import com.sift.photos.ui.screens.monthGroups
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class TimelineTest {

    private fun day(y: Int, m: Int, d: Int, key: String) = DaySection(
        "$y-$m-$d", "", listOf(PhotoItem(key, PhotoRef.Art(PhotoArt.Sky), Calendar.getInstance().apply { set(y, m - 1, d, 12, 0) }.timeInMillis, key)),
    )

    @Test
    fun monthsAreUniqueAndNewestFirstEvenWhenSectionsArriveOutOfOrder() {
        // A photo without DATE_TAKEN falls back to DATE_ADDED and can sort after older years.
        val sections = listOf(day(2026, 10, 19, "a"), day(2025, 5, 1, "b"), day(2026, 10, 2, "c"), day(2026, 3, 7, "d"))
        val months = monthGroups(sections)
        assertEquals(listOf(2026 to 9, 2026 to 2, 2025 to 4), months.map { it.year to it.month })
        assertEquals(months.size, months.map { "${it.year}-${it.month}" }.toSet().size)
        assertEquals(2, months.first().items.size)
    }
}
