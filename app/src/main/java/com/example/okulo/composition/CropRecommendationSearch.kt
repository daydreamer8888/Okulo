package com.example.okulo.composition

/** Owns candidate coverage, refinement and selection independently of image/model inference. */
internal fun searchCrops(
    width: Int,
    height: Int,
    search: CropSearch,
    score: (List<CropBox>) -> FloatArray,
    onRefinement: () -> Unit
): CropSearchResult {
    val ratio = search.normalizedRatio
    var candidates = ratio?.let { cropCandidates(it, search.minimumArea) }
        ?: freeCropCandidates(width, height, search.minimumArea)
    val coarseScores = score(candidates)
    val refinement = if (ratio == null) {
        refineFreeCrops(width, height, candidates, coarseScores, search.minimumArea)
    } else {
        emptyList()
    }
    val scores = if (refinement.isEmpty()) {
        coarseScores
    } else {
        onRefinement()
        val fineScores = score(refinement)
        candidates = candidates + refinement
        coarseScores + fineScores
    }
    // Fixed-aspect searches use the source only as a score baseline.
    val firstEligible = if (ratio == null) 0 else 1
    val best = (firstEligible until scores.size).maxBy { scores[it] }
    return CropSearchResult(candidates[best], scores[0], scores[best], candidates.size - 1)
}
