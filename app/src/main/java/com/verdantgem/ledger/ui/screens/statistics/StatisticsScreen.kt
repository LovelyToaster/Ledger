package com.verdantgem.ledger.ui.screens.statistics

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    val categories by viewModel.categoryDistribution.collectAsState()

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

            Spacer(modifier = Modifier.height(12.dp))

            if (data.isNotEmpty() && data.any { it > 0f }) {
                SimpleBarChart(data, labels)
            } else {
                SimpleBarChart(data.ifEmpty { listOf(0f) }, labels.ifEmpty { listOf("") })
            }

            Spacer(modifier = Modifier.height(d.spacingLg))

            Text(
                text = if (isCategoryIncome) "分类收入占比" else "分类支出占比",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(d.spacingMd))

            if (categories.isNotEmpty() && categories.any { it.value > 0f }) {
                CategoryPieChart(categories)
            } else {
                CategoryPieChart(emptyMap())
            }

            Spacer(modifier = Modifier.height(d.spacingXl))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPieChart(categories: Map<String, Float>) {
    val d = MaterialTheme.dimens
    val total = categories.values.sum()
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.error,
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val pieSize = (maxWidth * 0.5f).coerceAtMost(200.dp)
        val emptyColor = MaterialTheme.colorScheme.surfaceVariant

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Canvas(modifier = Modifier.size(pieSize)) {
                if (total > 0f) {
                    var startAngle = -90f
                    categories.values.forEachIndexed { index, value ->
                        val sweepAngle = (value / total) * 360f
                        drawArc(
                            color = colors[index % colors.size],
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true
                        )
                        startAngle += sweepAngle
                    }
                } else {
                    drawArc(
                        color = emptyColor,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(d.spacingXl))

            FlowRow(
                modifier = Modifier.padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(d.spacingSm)
            ) {
                categories.entries.take(6).forEachIndexed { index, entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = d.spacingSm)
                    ) {
                        Box(modifier = Modifier.size(10.dp).background(colors[index % colors.size], CircleShape))
                        Spacer(modifier = Modifier.width(d.spacingSm))
                        Text(
                            text = if (total > 0f) "${entry.key} ${String.format("%.1f", (entry.value / total) * 100)}%" else entry.key,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleBarChart(data: List<Float>, labels: List<String>) {
    val d = MaterialTheme.dimens
    val maxData = data.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val animateProgress = remember { Animatable(0f) }

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

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .padding(vertical = d.spacingLg)
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
    }
}
