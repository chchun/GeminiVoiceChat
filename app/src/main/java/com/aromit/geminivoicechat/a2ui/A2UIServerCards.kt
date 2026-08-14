package com.aromit.geminivoicechat.a2ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 서버 커스텀 카탈로그 카드 렌더러 (spec 001 D6).
 * 각 카드는 dataModel 의 summary 객체(Map) 하나를 바인딩한다 —
 * 데이터 형태의 진실원천은 saferyn-langgraph `app/utils/a2ui.py`.
 */
@Composable
fun ServerCardNode(component: A2UIComponent.ServerCard, surface: A2UISurface, modifier: Modifier = Modifier) {
    val data = getAtPath(surface.dataModel, component.valuePath) as? Map<*, *> ?: emptyMap<String, Any?>()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (component.kind) {
                "CompletionGaugeCard" -> CompletionGaugeCard(data, surface.primaryColor)
                "ActvScoreSummaryCard" -> ActvScoreSummaryCard(data, surface.primaryColor)
                "WeeklyScheduleCard" -> WeeklyScheduleCard(data, surface.primaryColor)
                "PendingApprovalListCard" -> PendingApprovalListCard(data, surface.primaryColor)
                "OpertPlanStatusCard" -> OpertPlanStatusCard(data, surface.primaryColor)
                "OpertStopStatusCard" -> OpertStopStatusCard(data, surface.primaryColor)
                "AccidentListCard" -> AccidentListCard(data, surface.primaryColor)
                else -> UnsupportedCard(component.kind)
            }
        }
    }
}

// ── 공통 헬퍼 ─────────────────────────────────────────────────

private fun Map<*, *>.str(key: String): String = this[key]?.toString() ?: ""
private fun Map<*, *>.num(key: String): Int = (this[key] as? Number)?.toInt() ?: 0
private fun Map<*, *>.bool(key: String): Boolean = this[key] as? Boolean ?: false
private fun Map<*, *>.maps(key: String): List<Map<*, *>> =
    (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()

private fun toneColor(tone: String): Color = when (tone) {
    "bad" -> Color(0xFFEF4444)
    "caution" -> Color(0xFFF59E0B)
    "good" -> Color(0xFF22C55E)
    else -> Color(0xFF9CA3AF)
}

@Composable
private fun CardTitle(title: String, accent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun StatChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun UnsupportedCard(kind: String) {
    Text(
        "지원하지 않는 컴포넌트입니다: $kind",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// ── 1. CompletionGaugeCard — 이행률/조치율 게이지 ─────────────

@Composable
private fun CompletionGaugeCard(data: Map<*, *>, accent: Color) {
    CardTitle(data.str("title"), accent)
    val percent = data.num("percent")
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$percent", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = accent)
        Text("%", style = MaterialTheme.typography.titleMedium, color = accent, modifier = Modifier.padding(bottom = 6.dp))
    }
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
        progress = { percent.coerceIn(0, 100) / 100f },
        modifier = Modifier.fillMaxWidth().height(8.dp),
        color = accent,
        trackColor = accent.copy(alpha = 0.15f),
    )
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        StatChip("완료", "${data.num("completed")}", toneColor("good"))
        StatChip("진행 중", "${data.num("inProgress")}", toneColor("caution"))
        StatChip("전체", "${data.num("total")}", MaterialTheme.colorScheme.onSurface)
    }
}

// ── 2. ActvScoreSummaryCard — 종합 안전활동 점수 ──────────────

@Composable
private fun ActvScoreSummaryCard(data: Map<*, *>, accent: Color) {
    CardTitle(data.str("title"), accent)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(data.str("siteName"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${data.num("score")}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = accent)
                Text(
                    "/ ${data.num("maxScore")}점",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
                )
            }
            Text(data.str("previousLabel"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val gradeTone = toneColor(data.str("gradeTone"))
        Box(
            modifier = Modifier.background(gradeTone.copy(alpha = 0.12f), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(data.str("gradeName"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = gradeTone)
        }
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "전체 평균 ${data.num("averageScore")}점 (평균 대비 ${data.str("averageDeltaText")})",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        for (gc in data.maps("gradeCounts")) {
            StatChip(gc.str("label"), "${gc.num("count")}", toneColor(gc.str("tone")))
        }
    }
}

// ── 3. WeeklyScheduleCard — 주간 일정 ─────────────────────────

@Composable
private fun WeeklyScheduleCard(data: Map<*, *>, accent: Color) {
    CardTitle(data.str("title"), accent)
    Text(data.str("monthLabel"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(8.dp))
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        for (day in data.maps("days")) {
            val isToday = day.bool("isToday")
            val dayColor = when {
                day.bool("isSunday") -> toneColor("bad")
                day.bool("isSaturday") -> Color(0xFF3B82F6)
                else -> MaterialTheme.colorScheme.onSurface
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(64.dp)
                    .background(
                        if (isToday) accent.copy(alpha = 0.08f) else Color.Transparent,
                        RoundedCornerShape(8.dp),
                    )
                    .padding(vertical = 6.dp, horizontal = 2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(if (isToday) accent else Color.Transparent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        day.str("day"),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) Color.White else dayColor,
                    )
                }
                Spacer(Modifier.height(4.dp))
                for (item in day.maps("items").take(3)) {
                    val chipColor = A2UIEnvelopeColor.parse(item.str("color"))
                    Text(
                        item.str("name"),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(vertical = 1.dp)
                            .fillMaxWidth()
                            .background(chipColor.copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
                val overflowCount = day.maps("items").size - 3
                if (overflowCount > 0) {
                    Text("+$overflowCount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ── 4. PendingApprovalListCard — 결재 대기 문서 ───────────────

@Composable
private fun PendingApprovalListCard(data: Map<*, *>, accent: Color) {
    CardTitle(data.str("title"), accent)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${data.num("total")}건", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = accent)
        Spacer(Modifier.weight(1f))
        Text("${data.str("updatedAt")} 기준", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (data.str("description").isNotEmpty()) {
        Spacer(Modifier.height(4.dp))
        Text(data.str("description"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.height(8.dp))
    for ((index, doc) in data.maps("documents").withIndex()) {
        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        val roleTone = if (doc.str("roleTone") == "confirm") toneColor("caution") else accent
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(doc.str("documentType"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "${doc.str("siteName")} · ${doc.str("drafterName")}(${doc.str("drafterRole")}) · ${doc.str("draftedAt")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier.background(roleTone.copy(alpha = 0.12f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(doc.str("standbyRoleName"), style = MaterialTheme.typography.labelSmall, color = roleTone, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── 5. OpertPlanStatusCard — 작업계획서 처리 상태 ─────────────

@Composable
private fun OpertPlanStatusCard(data: Map<*, *>, accent: Color) {
    CardTitle(data.str("title"), accent)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        StatChip("대기", "${data.num("waitCount")}", MaterialTheme.colorScheme.onSurface)
        StatChip("작성 중", "${data.num("writingCount")}", toneColor("caution"))
        StatChip("진행 중", "${data.num("progressCount")}", Color(0xFF3B82F6))
        StatChip("결재 중", "${data.num("approvalCount")}", accent)
        StatChip("완료", "${data.num("completedCount")}", toneColor("good"))
    }
    val formats = data.maps("formats")
    if (formats.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        for (fmt in formats) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(fmt.str("label"), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text("${fmt.num("count")}건", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── 7. AccidentListCard — 재해(사고) 발생 내역 ────────────────

@Composable
private fun AccidentListCard(data: Map<*, *>, accent: Color) {
    CardTitle(data.str("title"), accent)
    if (data.str("subtitle").isNotEmpty()) {
        Text(data.str("subtitle"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
    }
    val items = data.maps("items")
    for ((index, item) in items.withIndex()) {
        if (index > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .background(accent.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(item.str("type"), style = MaterialTheme.typography.labelSmall, color = accent, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                val itemTitle = item.str("title").takeIf { it.isNotEmpty() && it != "-" }
                if (itemTitle != null) {
                    Text(itemTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${item.str("siteName")} · ${item.str("date").take(16)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val content = item.str("content")
                if (content.isNotEmpty()) {
                    Text(content, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    val hidden = data.num("total") - data.num("shown")
    if (hidden > 0) {
        Spacer(Modifier.height(8.dp))
        Text("외 ${hidden}건", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── 6. OpertStopStatusCard — 작업중지 요청 처리 현황 ──────────

@Composable
private fun OpertStopStatusCard(data: Map<*, *>, accent: Color) {
    CardTitle(data.str("title"), accent)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("${data.num("total")}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = accent)
        Text("건", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp, start = 2.dp))
    }
    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        StatChip("대기", "${data.num("waiting")}", toneColor("caution"))
        StatChip("결재 중", "${data.num("approval")}", Color(0xFF3B82F6))
        StatChip("예약", "${data.num("reserved")}", MaterialTheme.colorScheme.onSurface)
    }
}

/** "#RRGGBB" 문자열 파서 — 일정 아이템 색 등 서버가 내려주는 hex 색상용. */
private object A2UIEnvelopeColor {
    fun parse(hex: String): Color {
        val clean = hex.trimStart('#')
        val argb = when (clean.length) {
            6 -> ("FF$clean").toLong(16)
            8 -> clean.toLong(16)
            else -> 0xFFE5E7EBL
        }
        return Color(argb.toInt())
    }
}
