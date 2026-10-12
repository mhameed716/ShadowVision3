package ai.shadowvision.cleanup

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_IMAGE_EDGE = 2048

data class EditorUiState(
    val source: Bitmap? = null,
    val result: Bitmap? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null
)

class PhotoEditorViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState = _uiState.asStateFlow()

    fun loadImage(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, statusMessage = null)
            try {
                val bitmap = withContext(Dispatchers.IO) { decodeImage(uri) }
                _uiState.value = EditorUiState(
                    source = bitmap,
                    statusMessage = "تم فتح الصورة محلياً. حدّد مساحة لإخفائها."
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    statusMessage = "تعذّر فتح الصورة. اختر ملف صورة آخر."
                )
            }
        }
    }

    fun process(strokes: List<BrushStroke>, mode: RedactionMode) {
        val source = _uiState.value.source ?: return
        if (strokes.isEmpty()) {
            _uiState.value = _uiState.value.copy(statusMessage = "حدّد مساحة بالفرشاة أولاً.")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, statusMessage = null)
            try {
                val redacted = withContext(Dispatchers.Default) {
                    val mask = MaskRasterizer.createMask(source.width, source.height, strokes)
                    try {
                        BitmapRedactor.apply(source, mask, mode)
                    } finally {
                        mask.recycle()
                    }
                }
                _uiState.value = _uiState.value.copy(
                    result = redacted,
                    isBusy = false,
                    statusMessage = "تم تطبيق تأثير الإخفاء على الجهاز."
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    statusMessage = "تعذّرت معالجة الصورة. جرّب تحديد مساحة أصغر."
                )
            }
        }
    }

    fun editSelectionAgain() {
        _uiState.value = _uiState.value.copy(
            result = null,
            statusMessage = "عدّل التحديد ثم طبّق التأثير من جديد."
        )
    }

    fun saveResult() {
        val bitmap = _uiState.value.result ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, statusMessage = null)
            try {
                withContext(Dispatchers.IO) { saveToGallery(bitmap) }
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    statusMessage = "تم حفظ الصورة في مجلد الصور على جهازك."
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBusy = false,
                    statusMessage = "تعذّر حفظ الصورة. تحقّق من مساحة التخزين المتاحة."
                )
            }
        }
    }

    private fun decodeImage(uri: Uri): Bitmap {
        val resolver = getApplication<Application>().contentResolver
        val source = ImageDecoder.createSource(resolver, uri)
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            decoder.setTargetConfig(Bitmap.Config.ARGB_8888)

            val size = info.size
            val longestEdge = maxOf(size.width, size.height)
            if (longestEdge > MAX_IMAGE_EDGE) {
                val scale = MAX_IMAGE_EDGE.toFloat() / longestEdge
                decoder.setTargetSize(
                    (size.width * scale).toInt().coerceAtLeast(1),
                    (size.height * scale).toInt().coerceAtLeast(1)
                )
            }
        }
    }

    private fun saveToGallery(bitmap: Bitmap) {
        val resolver = getApplication<Application>().contentResolver
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "ShadowVision_$timestamp.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/ShadowVision"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Cannot create gallery item")
        try {
            val written = resolver.openOutputStream(uri, "w")?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 94, output)
            } ?: false
            check(written) { "Cannot write image" }

            val published = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, published, null, null)
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            throw exception
        }
    }
}
