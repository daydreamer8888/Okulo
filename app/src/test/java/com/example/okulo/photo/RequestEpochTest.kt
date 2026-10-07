package com.example.okulo.photo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestEpochTest {
    @Test
    fun switchingPhotoModeOrCancellingRejectsAlreadyRunningResults() {
        val requests = RequestEpoch()
        val firstPhoto = requests.next()
        val secondPhoto = requests.next()
        val fastMode = requests.next()
        assertFalse(requests.isCurrent(firstPhoto))
        assertFalse(requests.isCurrent(secondPhoto))
        assertTrue(requests.isCurrent(fastMode))
        requests.next()
        assertFalse(requests.isCurrent(fastMode))
    }
}
