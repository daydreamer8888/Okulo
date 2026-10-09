package com.example.okulo.photo

import android.net.Uri
import com.example.okulo.composition.AnalysisMode
import com.example.okulo.composition.CropAspect
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
    fun reshapedFreeCropsRestoreTheLatestRecommendationAndItsSearchRatio() = PhotoTestHarness().use { fixture ->
        fixture.select()
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy }
        val first = fixture.model.state.value.result
        fixture.model.updateCrop(CropBox(0.1f, 0.2f, 0.9f, 0.6f))
        fixture.model.restoreRecommendation()
        assertEquals(first, fixture.model.state.value.result)
        assertEquals(first?.crop, fixture.model.state.value.displayedCrop)
        assertNull(fixture.model.state.value.freeRatio)
        fixture.model.updateCrop(CropBox(0.1f, 0.2f, 0.9f, 0.6f))
        fixture.analyzer.analyze = { _, _ -> recommendation().copy(crop = CropBox(0.1f, 0.2f, 0.9f, 0.6f)) }
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy }
        val latest = fixture.model.state.value.result
        fixture.model.updateCrop(CropBox(0.2f, 0.2f, 0.6f, 0.6f))
        fixture.model.restoreRecommendation()
        assertEquals(latest, fixture.model.state.value.result)
        assertEquals(latest?.crop, fixture.model.state.value.displayedCrop)
        assertEquals(2f, checkNotNull(fixture.model.state.value.freeRatio), 1e-5f)
        assertNull(fixture.model.state.value.manualCrop)
    }

    @Test
    fun rotationAndMirroringKeepTheSelectedRegionAndInvalidateOldScores() = PhotoTestHarness().use { fixture ->
        fixture.select()
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        fixture.model.updateCrop(CropBox(0.1f, 0.2f, 0.6f, 0.8f))
        fixture.model.transformPhoto(PhotoOperation.RotateClockwise)
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals(3, fixture.model.state.value.photo?.width)
        assertEquals(4, fixture.model.state.value.photo?.height)
        val rotated = checkNotNull(fixture.model.state.value.displayedCrop)
        assertEquals(0.2f, rotated.left, 1e-6f)
        assertEquals(0.1f, rotated.top, 1e-6f)
        assertEquals(0.8f, rotated.right, 1e-6f)
        assertEquals(0.6f, rotated.bottom, 1e-6f)
        assertNull(fixture.model.state.value.result)
        assertNull(fixture.model.state.value.displayedScore)
        fixture.model.transformPhoto(PhotoOperation.FlipHorizontal)
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        fixture.model.transformPhoto(PhotoOperation.FlipHorizontal)
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        val restored = checkNotNull(fixture.model.state.value.displayedCrop)
        assertEquals(rotated.left, restored.left, 1e-6f)
        assertEquals(rotated.right, restored.right, 1e-6f)
        fixture.select(Uri.parse("content://photos/two"))
        assertEquals(PhotoTransform(), fixture.model.state.value.transform)
    }

    @Test
    fun chosenAspectSurvivesAnalysisModeAndRestoration() = PhotoTestHarness().use { fixture ->
        fixture.select()
        val wide = CropBox(0f, 0.125f, 1f, 0.875f)
        fixture.analyzer.analyze = { _, _ -> recommendation().copy(crop = wide) }
        fixture.model.setAspect(CropAspect.Wide)
        fixture.model.setMode(AnalysisMode.Standard)
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals(CropAspect.Wide, fixture.model.state.value.aspect)
        assertEquals(4f / 3f, checkNotNull(fixture.analyzer.requestedRatios.single()), 1e-6f)
        assertEquals(wide, fixture.model.state.value.displayedCrop)
        fixture.model.updateCrop(CropBox(0.1f, 0.2f, 0.9f, 0.8f))
        fixture.model.restoreRecommendation()
        assertEquals(CropAspect.Wide, fixture.model.state.value.aspect)
        assertEquals(wide, fixture.model.state.value.displayedCrop)
        fixture.model.updateCrop(CropBox(0.1f, 0.2f, 0.9f, 0.8f))
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertNull(fixture.model.state.value.manualCrop)
        assertEquals(wide, fixture.model.state.value.displayedCrop)
    }

    @Test
    fun freeSearchUsesManyAspectsUntilTheUserChangesTheShape() = PhotoTestHarness().use { fixture ->
        fixture.select()
        fixture.model.setAspect(CropAspect.Free)
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        val moved = CropBox(0.15f, 0.1f, 0.95f, 0.9f)
        fixture.model.updateCrop(moved)
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals(listOf(null, null), fixture.analyzer.requestedRatios)
        val resized = CropBox(0.1f, 0.2f, 0.9f, 0.6f)
        fixture.model.updateCrop(resized)
        fixture.model.evaluateCrop()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals(resized, fixture.model.state.value.displayedCrop)
        assertEquals(2f, fixture.model.state.value.originalScore)
        assertEquals(4f, fixture.model.state.value.displayedScore)
        assertEquals(2, fixture.analyzer.analysisCalls.get())
        assertEquals(recommendation(), fixture.model.state.value.result)
        fixture.analyzer.analyze = { _, _ -> recommendation().copy(crop = CropBox(0f, 0.2f, 1f, 0.7f)) }
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals(2f, checkNotNull(fixture.analyzer.requestedRatios.last()), 1e-6f)
        fixture.model.setAspect(CropAspect.Free)
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertNull(fixture.analyzer.requestedRatios.last())
    }

    @Test
    fun rotatingAFreeCropCarriesItsChosenAspectIntoTheNextMode() = PhotoTestHarness().use { fixture ->
        fixture.select()
        fixture.model.setAspect(CropAspect.Free)
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        fixture.model.updateCrop(CropBox(0.1f, 0.2f, 0.9f, 0.6f))
        fixture.model.transformPhoto(PhotoOperation.RotateClockwise)
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        fixture.model.transformPhoto(PhotoOperation.FlipHorizontal)
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        fixture.model.setMode(AnalysisMode.Standard)
        fixture.analyzer.analyze = { _, _ -> recommendation().copy(crop = CropBox(0.1f, 0.1f, 0.5f, 0.9f)) }
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals(CropAspect.Free, fixture.model.state.value.aspect)
        assertEquals(0.5f, checkNotNull(fixture.analyzer.requestedRatios.last()), 1e-6f)
        val crop = checkNotNull(fixture.model.state.value.displayedCrop)
        assertEquals(0.5f, crop.width / crop.height, 1e-6f)
    }

    @Test
    fun switchingAspectRejectsAnInFlightRecommendation() = PhotoTestHarness().use { fixture ->
        fixture.select()
        val old = fixture.gate()
        val fresh = fixture.gate()
        val wide = CropBox(0f, 0.125f, 1f, 0.875f)
        var calls = 0
        fixture.analyzer.analyze = { _, _ ->
            if (calls++ == 0) {
                old.block()
                recommendation()
            } else {
                fresh.block()
                recommendation().copy(crop = wide)
            }
        }
        fixture.model.analyze()
        old.awaitEntry()
        fixture.model.setAspect(CropAspect.Wide)
        assertFalse(fixture.analyzer.isCurrent())
        fixture.model.analyze()
        old.release()
        fresh.awaitEntry()
        fixture.await { fixture.model.state.value.status == "正在分析照片…" }
        assertNull(fixture.model.state.value.result)
        assertTrue(fixture.model.state.value.busy)
        fresh.release()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals(wide, fixture.model.state.value.displayedCrop)
        assertEquals(4f / 3f, checkNotNull(fixture.analyzer.requestedRatios.last()), 1e-6f)
    }

    @Test
    fun importedPhotosStartWithUnrestrictedFreeAnalysis() = PhotoTestHarness().use { fixture ->
        fixture.select()
        assertEquals(CropAspect.Free, fixture.model.state.value.aspect)
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertNull(fixture.analyzer.requestedRatios.single())
        assertEquals(CropAspect.Free, CropAspect.entries.first())
        assertEquals(CropAspect.Original, CropAspect.entries[1])
    }

    @Test
    fun newPhotosUseFastAnalysisUnlessTheUserChoosesStandard() = PhotoTestHarness().use { fixture ->
        assertEquals(AnalysisMode.Fast, fixture.model.state.value.mode)
        fixture.select()
        assertEquals(AnalysisMode.Fast, fixture.model.state.value.mode)
        fixture.model.setMode(AnalysisMode.Standard)
        fixture.model.selectPhoto(Uri.parse("content://photos/two"))
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals(AnalysisMode.Standard, fixture.model.state.value.mode)
    }

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
        fixture.await { !model.state.value.busy && !model.state.value.scoring }
        assertEquals(recommendation(), model.state.value.result)
        val crop = CropBox(0.2f, 0.2f, 0.6f, 0.6f)
        model.updateCrop(crop)
        assertNull(model.state.value.displayedScore)
        model.evaluateCrop()
        fixture.await { !model.state.value.busy && !model.state.value.scoring }
        assertEquals(crop, model.state.value.displayedCrop)
        assertEquals(4f, model.state.value.displayedScore)
        model.analyze()
        fixture.await { !model.state.value.busy && !model.state.value.scoring }
        assertEquals(recommendation().crop, model.state.value.displayedCrop)
        assertEquals("分析完成", model.state.value.status)
        model.restoreRecommendation()
        assertEquals(recommendation().crop, model.state.value.displayedCrop)
        assertEquals(3f, model.state.value.displayedScore)
        assertNull(model.state.value.manualMillis)
    }

    @Test
    fun switchingModeDuringInferenceRejectsOldScores() = PhotoTestHarness().use { fixture ->
        fixture.model.setMode(AnalysisMode.Standard)
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
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals(AnalysisMode.Fast, fixture.model.state.value.mode)
        assertEquals(recommendation(), fixture.model.state.value.result)
        assertEquals(2, fixture.analyzer.analysisCalls.get())
    }

    @Test
    fun replacingPhotoRejectsAnOldFailureAndClearsCrop() = PhotoTestHarness().use { fixture ->
        fixture.select()
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
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
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertNull(fixture.model.state.value.error)
        assertNull(fixture.model.state.value.result)
        assertNull(fixture.model.state.value.manualCrop)
        assertEquals("照片已就绪", fixture.model.state.value.status)
    }

    @Test
    fun restoringRecommendationRejectsAnInFlightManualScore() = PhotoTestHarness().use { fixture ->
        fixture.select()
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
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
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
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
        fixture.model.setMode(AnalysisMode.Standard)
        gate.release()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals(2, reads)
        assertEquals(AnalysisMode.Standard, fixture.model.state.value.mode)
        assertSame(fixture.bitmap, fixture.model.state.value.photo)
        fixture.model.setMode(AnalysisMode.Standard)
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
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        fixture.analyzer.analyze = { _, _ -> throw IOException("model missing") }
        fixture.model.analyze()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals("无法准备本地模型，请检查存储空间后重试", fixture.model.state.value.error)
        fixture.analyzer.analyze = { _, _ -> recommendation() }
        fixture.model.retry()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        fixture.model.updateCrop(CropBox(0.2f, 0.2f, 0.6f, 0.6f))
        fixture.analyzer.evaluate = { throw IllegalStateException("invalid score") }
        fixture.model.evaluateCrop()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
        assertEquals("invalid score", fixture.model.state.value.error)
        fixture.analyzer.evaluate = { crop -> recommendation().copy(crop = crop, cropScore = 4f) }
        fixture.model.retry()
        fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
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
            fixture.await { !fixture.model.state.value.busy && !fixture.model.state.value.scoring }
            assertTrue(checkNotNull(fixture.model.state.value.error).contains("分析"))
            assertEquals("可以重试或选择其他照片", fixture.model.state.value.status)
        }
    }
}
