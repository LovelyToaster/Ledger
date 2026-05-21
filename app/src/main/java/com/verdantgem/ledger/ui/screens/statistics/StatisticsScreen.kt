package com.verdantgem.ledger.ui.screens.statistics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import androidx.compose.ui.graphics.toArgb
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.verdantgem.ledger.ui.theme.dimens
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val d = MaterialTheme.dimens
    val mode by viewModel.mode.collectAsState()
    val data by viewModel.chartData.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val isCategoryIncome by viewModel.isCategoryIncome.collectAsState()
    val labels by viewModel.chartLabels.collectAsState()
    val ranking by viewModel.activeRanking.collectAsState()
    val showDetail by viewModel.showDetail.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                viewModel.resetToDefault()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("收支统计", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets.statusBars
            )
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TabRow(
                selectedTabIndex = mode.ordinal,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                StatsMode.entries.forEach { m ->
                    Tab(
                        selected = mode == m,
                        onClick = { viewModel.setMode(m) },
                        text = {
                            Text(
                                when (m) {
                                    StatsMode.RECENT -> "日常"
                                    StatsMode.MONTH -> "月"
                                    StatsMode.YEAR -> "年"
                                }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(72.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (!isCategoryIncome) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { if (isCategoryIncome) viewModel.toggleCategoryType() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("支出", fontSize = 13.sp, color = if (!isCategoryIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(
                    modifier = Modifier
                        .width(72.dp).fillMaxHeight()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isCategoryIncome) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { if (!isCategoryIncome) viewModel.toggleCategoryType() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("收入", fontSize = 13.sp, color = if (isCategoryIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (mode != StatsMode.RECENT) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.adjustDate(-1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一个")
                        }
                        Text(
                            text = if (mode == StatsMode.MONTH) {
                                SimpleDateFormat("yyyy年MM月", Locale.getDefault()).format(selectedDate.time)
                            } else {
                                SimpleDateFormat("yyyy年", Locale.getDefault()).format(selectedDate.time)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.adjustDate(1) }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一个")
                        }
                    }
                }
            }

@Composable
fun CompactToggle(isIncome: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(2.dp)
                .defaultMinSize(minWidth = 48.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(if (!isIncome) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { if (isIncome) onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Text("支出", fontSize = 11.sp, color = if (!isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(
            modifier = Modifier
                .padding(2.dp)
                .defaultMinSize(minWidth = 48.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isIncome) MaterialTheme.colorScheme.primary else Color.Transparent)
                .clickable { if (!isIncome) onToggle() },
            contentAlignment = Alignment.Center
        ) {
            Text("收入", fontSize = 11.sp, color = if (isIncome) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CategoryRankingList(ranking: List<CategoryRank>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("类别", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
            Text("金额", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline)
        }

        ranking.forEach { item ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = String.format("%.2f%%", item.percentage * 100),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = String.format("%.2f", item.amount),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { item.percentage },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

            Spacer(modifier = Modifier.height(12.dp))

            val chartDataNonNull = if (data.isNotEmpty() && data.any { it > 0f }) data else data.ifEmpty { listOf(0f) }
            val chartLabelsNonNull = if (data.isNotEmpty() && data.any { it > 0f }) labels else labels.ifEmpty { listOf("") }
            SimpleBarChart(
                data = chartDataNonNull,
                labels = chartLabelsNonNull,
                isIncome = isCategoryIncome,
                mode = mode
            )

            Spacer(modifier = Modifier.height(d.spacingLg))



            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isCategoryIncome) "收入明细" else "支出明细",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = { viewModel.toggleDetail() },
                        shape = RoundedCornerShape(14.dp),
                        color = if (showDetail) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.height(28.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "详细",
                                fontSize = 11.sp,
                                color = if (showDetail) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (showDetail) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                    CompactToggle(isIncome = isCategoryIncome, onToggle = { viewModel.toggleCategoryType() })
                }
            }
            Spacer(modifier = Modifier.height(d.spacingMd))

            CategoryRankingList(
                ranking = ranking,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(d.spacingXl))
        }
    }
}

@Composable
fun SimpleBarChart(data: List<Float>, labels: List<String>, isIncome: Boolean, mode: StatsMode = StatsMode.RECENT) {
    val d = MaterialTheme.dimens
    val maxData = data.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val animateProgress = remember { Animatable(0f) }
    var selectedBarIndex by remember { mutableStateOf(-1) }

    LaunchedEffect(data) {
        animateProgress.snapTo(0f)
        animateProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val textColor = MaterialTheme.colorScheme.outline.toArgb()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val chartHeight = (maxWidth * 0.7f).coerceIn(200.dp, 400.dp)

        val yAxisWidthDp = (maxWidth * 0.08f).coerceIn(32.dp, 48.dp)
        val chartLeftDp = 8.dp + yAxisWidthDp
        val chartWidthDp = maxWidth - chartLeftDp - 16.dp
        val barCount = data.size
        val barWidthDp = if (barCount > 0) (chartWidthDp / barCount) * 0.6f else 0.dp
        val spaceDp = if (barCount > 0) (chartWidthDp - (barWidthDp * barCount)) / (barCount + 1) else 0.dp
        val localMaxWidth = maxWidth
        val barCenterXDps = remember(data, chartLeftDp, spaceDp, barWidthDp) {
            data.indices.map { i -> chartLeftDp + spaceDp + (barWidthDp + spaceDp) * i + barWidthDp / 2 }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(chartHeight)
                    .padding(vertical = d.spacingLg)
                    .pointerInput(data) {
                        detectTapGestures { offset ->
                            val yAxisWidth = (size.width * 0.08f).coerceIn(32.dp.toPx(), 48.dp.toPx())
                            val lMargin = 8.dp.toPx() + yAxisWidth
                            val rMargin = 16.dp.toPx()
                            val chartLeft = lMargin
                            val chartWidth = size.width - lMargin - rMargin
                            val barCount = data.size
                            var hit = false
                            if (barCount > 0 && offset.x >= chartLeft && offset.x <= chartLeft + chartWidth) {
                                val barWidth = (chartWidth / barCount) * 0.6f
                                val space = (chartWidth - (barWidth * barCount)) / (barCount + 1)
                                for (i in 0 until barCount) {
                                    val xCenter = chartLeft + space + i * (barWidth + space) + barWidth / 2
                                    if (kotlin.math.abs(offset.x - xCenter) <= barWidth / 2) {
                                        selectedBarIndex = if (selectedBarIndex == i) -1 else i
                                        hit = true
                                        break
                                    }
                                }
                            }
                            if (!hit) selectedBarIndex = -1
                        }
                    }
            ) {
                val yAxisWidth = (size.width * 0.08f).coerceIn(32.dp.toPx(), 48.dp.toPx())
                val lMargin = 8.dp.toPx() + yAxisWidth
                val rMargin = 16.dp.toPx()
                val chartLeft = lMargin
                val chartWidth = size.width - lMargin - rMargin
                val xAxisHeight = 30.dp.toPx()
                val chartHeight = size.height - xAxisHeight
                val axisFontSize = (8f * (size.width / 360f)).coerceIn(7f, 12f).sp.toPx()

                val paint = Paint().apply {
                    color = textColor
                    textSize = axisFontSize
                    textAlign = Paint.Align.RIGHT
                }

                val steps = 4
                for (i in 0..steps) {
                    val value = (maxData / steps) * i
                    val y = chartHeight - (i.toFloat() / steps) * chartHeight
                    drawContext.canvas.nativeCanvas.drawText(
                        String.format("%.0f", value),
                        chartLeft - 4.dp.toPx(),
                        y + 4.dp.toPx(),
                        paint
                    )
                    drawLine(
                        color = outlineColor,
                        start = Offset(chartLeft, y),
                        end = Offset(chartLeft + chartWidth, y),
                        strokeWidth = 0.5.dp.toPx()
                    )
                }

                val barCount = data.size
                val barWidth = (chartWidth / barCount) * 0.6f
                val space = (chartWidth - (barWidth * barCount)) / (barCount + 1)

                data.forEachIndexed { index, value ->
                    val barHeight = (value / maxData) * chartHeight * animateProgress.value
                    val xCenter = chartLeft + space + index * (barWidth + space) + barWidth / 2
                    val x = xCenter - barWidth / 2
                    val y = chartHeight - barHeight

                    drawRoundRect(
                        color = primaryColor,
                        topLeft = Offset(x, y),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(4.dp.toPx())
                    )

                    val label = labels.getOrNull(index) ?: ""
                    if (label.isNotEmpty()) {
                        val labelPaint = Paint().apply {
                            color = textColor
                            textSize = axisFontSize
                            textAlign = Paint.Align.CENTER
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            xCenter,
                            size.height - 4.dp.toPx(),
                            labelPaint
                        )
                    }
                }
            }

            if (selectedBarIndex in data.indices) {
                val value = data[selectedBarIndex]
                if (value > 0f) {
                    val rawLabel = labels.getOrElse(selectedBarIndex) { "" }
                    val dateLabel = if (mode == StatsMode.MONTH) "${selectedBarIndex + 1}日" else rawLabel
                    val xDp = barCenterXDps.getOrElse(selectedBarIndex) { chartWidthDp / 2 }
                    val density = LocalDensity.current
                    val bodyFontPx = with(density) { MaterialTheme.typography.bodyMedium.fontSize.toPx() }
                    val measurePaint = remember { Paint() }.apply { textSize = bodyFontPx }
                    val amountStr = "${if (isIncome) "收入" else "支出"} ${String.format("%.2f", value)}"
                    val labelStr = dateLabel
                    val textWidthPx = maxOf(measurePaint.measureText(amountStr), measurePaint.measureText(labelStr))
                    val tooltipWidthDp = with(density) { (textWidthPx + 32.dp.toPx()).toDp() }
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(
                                x = (xDp - tooltipWidthDp / 2).coerceIn(0.dp, localMaxWidth - tooltipWidthDp)
                            )
                            .padding(top = 4.dp),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 4.dp,
                        color = MaterialTheme.colorScheme.inverseSurface,
                        tonalElevation = 2.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = dateLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${if (isIncome) "收入" else "支出"} ${String.format("%.2f", value)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.inverseOnSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
