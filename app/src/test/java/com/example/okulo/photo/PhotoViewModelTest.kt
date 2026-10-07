package com.example.okulo.photo

import android.net.Uri
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CropBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PhotoViewModelTest {
    @Test
    fun importRecommendationAndManualScoreRemainConsistent() = PhotoTestHarness().use { fixture ->
        val model = fixture.model
        model.analyze()
        model.updateCrop(CropBox.FullFrame)
        model.evaluateCrop()
        assertEquals(0, fixture.analyzer.analysisCalls.get())
        fixture.select()
        assertSame(fixture.bitmap, model.state.value.photo)
        model.analyze()
        fixture.await { !model.state.value.busy }
        assertEquals(recommendation(), model.state.value.result)
        val crop = CropBox(0.2f, 0.2f, 0.6f, 0.6f)
        model.updateCrop(crop)
        assertNull(model.state.value.displayedScore)
        model.evaluateCrop()
        fixture.await { !model.state.value.busy }
        assertEquals(crop, model.state.value.displayedCrop)
        assertEquals(4f, model.state.value.displayedScore)
        model.analyze()
        fixture.await { !model.state.value.busy }
        assertEquals(crop, model.state.value.displayedCrop)
        assertEquals("有新推荐", model.state.value.status)
        model.restoreRecommendation()
        assertEquals(recommendation().crop, model.state.value.displayedCrop)
        assertEquals(3f, model.state.value.displayedScore)
        assertNull(model.state.value.manualMillis)
    }

    @Test
    fun switchingModeDuringInferenceRejectsOldScores() = PhotoTestHarness().use { fixture ->
        fixture.select()
        val old = fixture.gate()
        val fresh = fixture.gate()
        fixture.analyzer.analyze = { _, mode ->
            if (mode == AnalysisMode.Standard) old.block() else fresh.block()
            recommendation()
        }
        fixture.model.analyze()
        old.awaitEntry()
        fixture.model.setMode(AnalysisMode.Fast)
        assertFalse("Previous mode must be cancelled before another analysis starts", fixture.analyzer.isCurrent())
        old.release()
        fixture.model.analyze()
        fresh.awaitEntry()
        fixture.await { fixture.model.state.value.status == "正在分析照片…" }
        assertNull(fixture.model.state.value.result)
        assertTrue(fixture.model.state.value.busy)
        fresh.release()
        fixture.await { !fixture.model.state.value.busy }
        assertEquals(AnalysisMode.Fast, fixture.model.state.value.mode)
        assertEquals(recommendation(), fixture.model.state.value.result)
        assertEquals(2, fixture.analyzer.analysisCalls.get())
    }

    @Test
    fun replacingPhotoRejectsAnOldFailureAndClearsCrop() = PhotoTestHarness().use { fixture ->
        fixture.select()
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy }
        fixture.model.updateCrop(CropBox(0.2f, 0.2f, 0.6f, 0.6f))
        val gate = fixture.gate()
        fixture.analyzer.evaluate = {
            gate.block()
            throw IOException("old photo failed")
        }
        fixture.model.evaluateCrop()
        gate.awaitEntry()
        fixture.model.selectPhoto(Uri.parse("content://photos/two"))
        gate.release()
        fixture.await { !fixture.model.state.value.busy }
        assertNull(fixture.model.state.value.error)
        assertNull(fixture.model.state.value.result)
        assertNull(fixture.model.state.value.manualCrop)
        assertEquals("照片已就绪", fixture.model.state.value.status)
    }

    @Test
    fun restoringRecommendationRejectsAnInFlightManualScore() = PhotoTestHarness().use { fixture ->
        fixture.select()
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy }
        val gate = fixture.gate()
        fixture.analyzer.evaluate = { crop ->
            gate.block()
            recommendation().copy(crop = crop, cropScore = 99f)
        }
        fixture.model.updateCrop(CropBox(0.2f, 0.2f, 0.6f, 0.6f))
        fixture.model.evaluateCrop()
        gate.awaitEntry()
        fixture.model.restoreRecommendation()
        assertFalse("Restoring must cancel the pending manual score", fixture.analyzer.isCurrent())
        gate.release()
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy }
        assertNull(fixture.model.state.value.manualScore)
        assertEquals(3f, fixture.model.state.value.displayedScore)
        assertEquals(recommendation().crop, fixture.model.state.value.displayedCrop)
    }

    @Test
    fun cancelInvalidatesRunningAnalysisWithoutStartingAnotherRequest() = PhotoTestHarness().use { fixture ->
        fixture.select()
        val gate = fixture.gate()
        fixture.analyzer.analyze = { _, _ ->
            gate.block()
            recommendation()
        }
        fixture.model.analyze()
        gate.awaitEntry()
        assertTrue(fixture.analyzer.isCurrent())
        fixture.model.cancel()
        assertFalse(fixture.analyzer.isCurrent())
        assertFalse(fixture.model.state.value.busy)
        assertEquals("已取消分析", fixture.model.state.value.status)
        assertNull(fixture.model.state.value.result)
    }

    @Test
    fun modeChangeRestartsAnInFlightImport() = PhotoTestHarness().use { fixture ->
        val gate = fixture.gate()
        var reads = 0
        fixture.reader = {
            reads++
            if (reads == 1) gate.block()
            fixture.bitmap
        }
        fixture.model.selectPhoto(Uri.parse("content://photos/one"))
        gate.awaitEntry()
        fixture.model.setMode(AnalysisMode.Fast)
        gate.release()
        fixture.await { !fixture.model.state.value.busy }
        assertEquals(2, reads)
        assertEquals(AnalysisMode.Fast, fixture.model.state.value.mode)
        assertSame(fixture.bitmap, fixture.model.state.value.photo)
        fixture.model.setMode(AnalysisMode.Fast)
        assertSame(fixture.bitmap, fixture.model.state.value.photo)
    }

    @Test
    fun retryImportAndManualScoringChooseTheFailedOperation() = PhotoTestHarness().use { fixture ->
        fixture.model.retry()
        fixture.reader = { throw IOException("read denied") }
        fixture.select()
        assertEquals("照片读取失败，请重新选择", fixture.model.state.value.error)
        fixture.reader = { fixture.bitmap }
        fixture.model.retry()
        fixture.await { !fixture.model.state.value.busy }
        fixture.analyzer.analyze = { _, _ -> throw IOException("model missing") }
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy }
        assertEquals("无法准备本地模型，请检查存储空间后重试", fixture.model.state.value.error)
        fixture.analyzer.analyze = { _, _ -> recommendation() }
        fixture.model.retry()
        fixture.await { !fixture.model.state.value.busy }
        fixture.model.updateCrop(CropBox(0.2f, 0.2f, 0.6f, 0.6f))
        fixture.analyzer.evaluate = { throw IllegalStateException("invalid score") }
        fixture.model.evaluateCrop()
        fixture.await { !fixture.model.state.value.busy }
        assertEquals("invalid score", fixture.model.state.value.error)
        fixture.analyzer.evaluate = { crop -> recommendation().copy(crop = crop, cropScore = 4f) }
        fixture.model.retry()
        fixture.await { !fixture.model.state.value.busy }
        assertEquals(2, fixture.analyzer.analysisCalls.get())
        assertEquals(2, fixture.analyzer.evaluationCalls.get())
        assertEquals(4f, fixture.model.state.value.manualScore)
    }

    @Test
    fun unexpectedFailuresHaveARecoverableMessage() = PhotoTestHarness().use { fixture ->
        fixture.select()
        for (failure in listOf(IllegalStateException(), IllegalArgumentException("invalid input"))) {
            fixture.analyzer.analyze = { _, _ -> throw failure }
            fixture.model.analyze()
            fixture.await { !fixture.model.state.value.busy }
            assertTrue(checkNotNull(fixture.model.state.value.error).contains("分析"))
            assertEquals("可以重试或选择其他照片", fixture.model.state.value.status)
        }
    }
}
