package com.leo.wardrobe.export

import com.leo.wardrobe.data.mock.MockWardrobeData
import org.junit.Assert.*
import org.junit.Test

class PersonReferenceTest {
    @Test fun `missing and historical mannequin references are omitted`() {
        assertNull(usablePersonReference(null))
        assertNull(usablePersonReference(" "))
        assertNull(usablePersonReference("person-ref.png"))
        assertNull(usablePersonReference("mock/person-ref.png"))
        assertEquals("uploaded-person.webp", usablePersonReference("uploaded-person.webp"))
    }

    @Test fun `old demo json clears mannequin but preserves real references`() {
        val data = MockWardrobeData.fromJson("""{"persons":[{"id":"a","name":"A","emoji":"","refImageFile":"person-ref.png","createdAt":0},{"id":"b","name":"B","emoji":"","refImageFile":"real.webp","createdAt":0}]}""")
        assertNull(data.persons[0].refImageFile)
        assertEquals("real.webp", data.persons[1].refImageFile)
        assertTrue(MockWardrobeData.create().persons.all { it.refImageFile == null })
    }
}
