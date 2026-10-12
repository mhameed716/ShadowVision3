package ai.shadowvision.cleanup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun PhotoEditorApp(viewModel: PhotoEditorViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let(viewModel::loadImage) }
    )

    PhotoEditorScreen(
        state = state,
        onPickImage = {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onProcess = viewModel::process,
        onSave = viewModel::saveResult,
        onEditAgain = viewModel::editSelectionAgain
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PhotoEditorScreen(
    state: EditorUiState,
    onPickImage: () -> Unit,
    onProcess: (List<BrushStroke>, RedactionMode) -> Unit,
    onSave: () -> Unit,
    onEditAgain: () -> Unit
) {
    var strokes by remember(state.source) { mutableStateOf<List<BrushStroke>>(emptyList()) }
    var selectedMode by remember { mutableStateOf(RedactionMode.PIXELATE) }
    var brushSize by remember { mutableFloatStateOf(30f) }

    Scaffold(
        containerColor = Color(0xFF0B1220),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ShadowVision", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            "محرّر خصوصية يعمل دون اتصال",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0B1220),
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 18.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "أخفِ التفاصيل، واحتفظ بصورتك.",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        "حدّد منطقة ثم طبّق الموزاييك أو التمويه أو التعتيم.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { PrivacyCard() }

            item {
                val sourceImage = state.source
                if (sourceImage == null) {
                    EmptyPhotoCard(onPickImage = onPickImage, isBusy = state.isBusy)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    if (state.result == null) "ارسم فوق المنطقة المراد إخفاؤها"
                                    else "معاينة النتيجة",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    if (state.result == null) "اسحب بإصبعك على الصورة لتحديدها"
                                    else "الصورة الأصلية محفوظة دون تعديل",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = onPickImage, enabled = !state.isBusy) {
                                Text("تغيير الصورة")
                            }
                        }

                        EditorCanvas(
                            source = sourceImage,
                            preview = state.result,
                            strokes = strokes,
                            brushSizeDp = brushSize,
                            editable = state.result == null && !state.isBusy,
                            onStrokeCommitted = { stroke -> strokes = strokes + stroke },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp)
                        )

                        if (state.result == null) {
                            SelectionControls(
                                strokes = strokes,
                                brushSize = brushSize,
                                selectedMode = selectedMode,
                                enabled = !state.isBusy,
                                onBrushSizeChange = { brushSize = it },
                                onModeChange = { selectedMode = it },
                                onUndo = { if (strokes.isNotEmpty()) strokes = strokes.dropLast(1) },
                                onClear = { strokes = emptyList() }
                            )
                            Button(
                                onClick = { onProcess(strokes, selectedMode) },
                                enabled = !state.isBusy && strokes.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(vertical = 15.dp)
                            ) {
                                if (state.isBusy) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Text(if (state.isBusy) "جارٍ تطبيق التأثير…" else "تطبيق الإخفاء")
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onEditAgain,
                                    enabled = !state.isBusy,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(vertical = 14.dp)
                                ) {
                                    Text("تعديل التحديد")
                                }
                                Button(
                                    onClick = onSave,
                                    enabled = !state.isBusy,
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(vertical = 14.dp)
                                ) {
                                    Text(if (state.isBusy) "جارٍ الحفظ…" else "حفظ الصورة")
                                }
                            }
                        }
                    }
                }
            }

            state.statusMessage?.let { message ->
                item {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            item {
                Text(
                    "تُعالَج الصورة على هذا الجهاز. لا تُرفع الصور إلى خادم.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF152235))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("●", color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("خصوصية أولاً", fontWeight = FontWeight.SemiBold)
                Text(
                    "التأثير يحجب التفاصيل فقط؛ لا يعيد بناء ما وراء التحديد ولا ينشئ محتوى جديداً. " +
                        "التطبيق لا يحتاج إلى اتصال بالإنترنت.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyPhotoCard(onPickImage: () -> Unit, isBusy: Boolean) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131D2D))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("＋", fontSize = 36.sp, color = MaterialTheme.colorScheme.primary)
            Text(
                "ابدأ بصورة من جهازك",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "اختر صورة، وارسم فوق التفاصيل التي تريد حجبها.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onPickImage,
                enabled = !isBusy,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("اختيار صورة")
            }
        }
    }
}

@Composable
private fun SelectionControls(
    strokes: List<BrushStroke>,
    brushSize: Float,
    selectedMode: RedactionMode,
    enabled: Boolean,
    onBrushSizeChange: (Float) -> Unit,
    onModeChange: (RedactionMode) -> Unit,
    onUndo: () -> Unit,
    onClear: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("حجم الفرشاة", style = MaterialTheme.typography.titleSmall)
            Text("${brushSize.toInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = brushSize,
            onValueChange = onBrushSizeChange,
            valueRange = 12f..56f,
            enabled = enabled
        )

        Text("نوع الإخفاء", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RedactionMode.values().forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { onModeChange(mode) },
                    enabled = enabled,
                    label = { Text(mode.title) }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                if (strokes.isEmpty()) "لا يوجد تحديد بعد" else "عدد المسارات: ${strokes.size}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row {
                TextButton(onClick = onUndo, enabled = enabled && strokes.isNotEmpty()) {
                    Text("تراجع")
                }
                TextButton(onClick = onClear, enabled = enabled && strokes.isNotEmpty()) {
                    Text("مسح")
                }
            }
        }
    }
}
