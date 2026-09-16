package jp.hisiragi.worklauncher.domain

/** What a model can take as input, which decides how voice input is handled. */
enum class ModelModality {
    /** Text only; speech has to be transcribed before it reaches the model. */
    TEXT,

    /** Accepts audio directly, so a recording can be passed through untouched. */
    AUDIO,
}

/**
 * A model the app can fetch for the user. These are the LiteRT builds published
 * by litert-community, which is the format MediaPipe's runtime loads.
 */
data class CatalogModel(
    val id: String,
    val displayName: String,
    val repo: String,
    val file: String,
    val sizeBytes: Long,
    val modality: ModelModality = ModelModality.TEXT,
    val note: String = "",
) {
    /** Hugging Face serves LFS files from the resolve endpoint. */
    val downloadUrl: String get() = "https://huggingface.co/$repo/resolve/main/$file"

    /** Name the file is saved under locally. */
    val fileName: String get() = file.substringAfterLast('/')

    val sizeGb: Double get() = sizeBytes / 1_000_000_000.0
}

object LlmModelCatalog {

    /** Smallest and fastest; what a first-time user gets unless they pick another. */
    val DEFAULT_ID = "deepseek-r1-distill-qwen-1.5b"

    val models: List<CatalogModel> = listOf(
        CatalogModel(
            id = DEFAULT_ID,
            displayName = "DeepSeek-R1-Distill-Qwen 1.5B",
            repo = "litert-community/DeepSeek-R1-Distill-Qwen-1.5B",
            file = "DeepSeek-R1-Distill-Qwen-1.5B_multi-prefill-seq_q8_ekv4096.task",
            sizeBytes = 1_834_078_546L,
        ),
        CatalogModel(
            id = "gemma-4-e2b",
            displayName = "Gemma 4 E2B",
            repo = "litert-community/gemma-4-E2B-it-litert-lm",
            file = "gemma-4-E2B-it.litertlm",
            sizeBytes = 2_588_147_712L,
            modality = ModelModality.AUDIO,
        ),
        CatalogModel(
            id = "qwen3-4b",
            displayName = "Qwen3 4B",
            repo = "litert-community/Qwen3-4B",
            file = "qwen3_4b_mixed_int4.litertlm",
            sizeBytes = 2_659_057_664L,
        ),
        CatalogModel(
            id = "qwen3-8b",
            displayName = "Qwen3 8B",
            repo = "litert-community/Qwen3-8B",
            file = "qwen3_8b_mixed_int4.litertlm",
            sizeBytes = 4_887_412_736L,
        ),
        CatalogModel(
            id = "qwen3-14b",
            displayName = "Qwen3 14B",
            repo = "litert-community/Qwen3-14B",
            file = "qwen3_14b_mixed_int4.litertlm",
            sizeBytes = 8_655_863_808L,
        ),
    )

    fun byId(id: String): CatalogModel? = models.firstOrNull { it.id == id }

    /** Matches a stored path back to its catalog entry, if it came from here. */
    fun byFileName(fileName: String): CatalogModel? =
        models.firstOrNull { it.fileName == fileName }
}

/** Progress of a model download, surfaced to the settings screen. */
sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Running(
        val modelId: String,
        val bytesDownloaded: Long,
        val totalBytes: Long,
    ) : ModelDownloadState {
        val fraction: Float
            get() = if (totalBytes > 0) {
                (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
            } else {
                0f
            }
    }

    data class Failed(val modelId: String, val reason: String) : ModelDownloadState
}
