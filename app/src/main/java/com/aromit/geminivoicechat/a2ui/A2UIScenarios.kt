package com.aromit.geminivoicechat.a2ui

import androidx.compose.ui.graphics.Color

object A2UIScenarios {

    data class Scenario(
        val keys: List<String>,
        val replyMd: String,
        val surface: () -> A2UISurface,
    )

    val ALL: List<Scenario> = listOf(
        Scenario(
            keys = listOf("인시던트", "장애 보고", "incident", "보고서 폼", "인시던트 생성"),
            replyMd = "장애 내용을 바로 접수할 수 있도록 **인시던트 보고 폼**을 생성했어요. 아래에서 작성 후 제출하면 인시던트가 등록됩니다.",
            surface = ::incidentSurface,
        ),
        Scenario(
            keys = listOf("배포", "deploy", "롤아웃", "카나리", "canary", "배포 승인"),
            replyMd = "프로덕션 **카나리 배포 승인 패널**을 준비했어요. 헬스체크를 확인하고 트래픽 비중을 정한 뒤 배포를 시작하세요.",
            surface = ::deploySurface,
        ),
        Scenario(
            keys = listOf("상태 카드", "서비스 상태", "status card", "상태 대시보드", "헬스 카드"),
            replyMd = "`payment-service` 의 **실시간 상태 카드**입니다. 기간을 바꾸거나 새로고침을 누르면 에이전트가 지표를 다시 조회해요.",
            surface = ::statusSurface,
        ),
        Scenario(
            keys = listOf("위험성평가", "위험 평가", "리스크 평가", "안전 점검", "작업 위험"),
            replyMd = "작업 공종과 환경 정보를 입력하면 AI가 **위험성평가 항목**을 자동으로 생성합니다.",
            surface = ::riskSurface,
        ),
    )

    fun match(text: String): Scenario? {
        val lower = text.lowercase()
        return ALL.firstOrNull { s -> s.keys.any { lower.contains(it.lowercase()) } }
    }

    // ─────────────────────────────────────────────────────────
    // 1. Incident Report Form
    // ─────────────────────────────────────────────────────────
    fun incidentSurface(): A2UISurface = A2UISurface(
        surfaceId = "incident_form_${System.currentTimeMillis()}",
        primaryColor = Color(0xFFE5484D),
        agentDisplayName = "Incident Bot",
        components = mapOf(
            "root" to A2UIComponent.Card("root", "form_col"),
            "form_col" to A2UIComponent.Column("form_col",
                children = listOf("hdr", "sev_label", "sev_pick", "svc_field", "sum_field", "impact_check", "submit_btn")),
            "hdr" to A2UIComponent.Row("hdr", children = listOf("hdr_ico", "hdr_txt"), align = "center"),
            "hdr_ico" to A2UIComponent.IconComp("hdr_ico", "alert", 20),
            "hdr_txt" to A2UIComponent.TextComp("hdr_txt", A2UIValue.Static("인시던트 보고"), "h2"),
            "sev_label" to A2UIComponent.TextComp("sev_label", A2UIValue.Static("심각도"), "caption"),
            "sev_pick" to A2UIComponent.ChoicePickerComp("sev_pick",
                options = listOf(
                    A2UIOption("P1 · 치명적", "P1"),
                    A2UIOption("P2 · 높음", "P2"),
                    A2UIOption("P3 · 보통", "P3"),
                ),
                valuePath = "/incident/severity"),
            "svc_field" to A2UIComponent.TextFieldComp("svc_field",
                label = "영향 서비스", valuePath = "/incident/service",
                placeholder = "예: order-service"),
            "sum_field" to A2UIComponent.TextFieldComp("sum_field",
                label = "증상 요약", valuePath = "/incident/summary",
                variant = "longText", placeholder = "무슨 일이 발생했나요?",
                checks = listOf(A2UICheck(A2UICondition.Required("/incident/summary"), "증상 요약은 필수입니다."))),
            "impact_check" to A2UIComponent.CheckBoxComp("impact_check",
                label = "고객 영향이 발생했습니다", valuePath = "/incident/customerImpact"),
            "submit_btn" to A2UIComponent.ButtonComp("submit_btn",
                label = A2UIValue.Static("인시던트 생성"), variant = "primary",
                action = A2UIAction(
                    eventName = "create_incident",
                    contextRefs = listOf(
                        A2UIContextRef("service", path = "/incident/service"),
                        A2UIContextRef("severity", path = "/incident/severity"),
                        A2UIContextRef("summary", path = "/incident/summary"),
                        A2UIContextRef("customerImpact", path = "/incident/customerImpact"),
                    ),
                ),
                checks = listOf(A2UICheck(A2UICondition.Required("/incident/summary"), "증상 요약을 입력해야 생성할 수 있어요."))),
        ),
        dataModel = mutableMapOf(
            "incident" to mutableMapOf(
                "severity" to "P2",
                "service" to "order-service",
                "summary" to "",
                "customerImpact" to false,
            )
        ),
    )

    // ─────────────────────────────────────────────────────────
    // 2. Deploy Approval Panel
    // ─────────────────────────────────────────────────────────
    fun deploySurface(): A2UISurface = A2UISurface(
        surfaceId = "deploy_panel_${System.currentTimeMillis()}",
        primaryColor = Color(0xFF1F8A5B),
        agentDisplayName = "Deploy Bot",
        components = mapOf(
            "root" to A2UIComponent.Card("root", "col"),
            "col" to A2UIComponent.Column("col",
                children = listOf("title", "sub", "canary_slider", "canary_txt", "div", "health_check", "btn_row")),
            "title" to A2UIComponent.TextComp("title", A2UIValue.Static("카나리 배포 승인"), "h2"),
            "sub" to A2UIComponent.TextComp("sub", A2UIValue.Static("payment-service · v2.4.1 → production"), "caption"),
            "canary_slider" to A2UIComponent.SliderComp("canary_slider",
                label = "카나리 트래픽 비중", valuePath = "/deploy/canary",
                min = 0f, max = 100f, step = 5f),
            "canary_txt" to A2UIComponent.TextComp("canary_txt",
                A2UIValue.FormatStr("신버전으로 **\${/deploy/canary}%** 의 트래픽을 전환합니다."), "body"),
            "div" to A2UIComponent.Divider("div"),
            "health_check" to A2UIComponent.CheckBoxComp("health_check",
                label = "스테이징 헬스체크 통과를 확인했습니다", valuePath = "/deploy/healthOk"),
            "btn_row" to A2UIComponent.Row("btn_row",
                children = listOf("cancel_btn", "deploy_btn"), justify = "spaceBetween"),
            "cancel_btn" to A2UIComponent.ButtonComp("cancel_btn",
                label = A2UIValue.Static("취소"), variant = "borderless",
                action = A2UIAction("cancel_deploy")),
            "deploy_btn" to A2UIComponent.ButtonComp("deploy_btn",
                label = A2UIValue.Static("배포 시작"), variant = "primary",
                action = A2UIAction(
                    eventName = "start_deploy",
                    contextRefs = listOf(
                        A2UIContextRef("canary", path = "/deploy/canary"),
                        A2UIContextRef("healthOk", path = "/deploy/healthOk"),
                    ),
                    extraLiterals = mapOf("service" to "payment-service", "version" to "v2.4.1"),
                ),
                checks = listOf(A2UICheck(
                    A2UICondition.And(listOf(
                        A2UICondition.PathRef("/deploy/healthOk"),
                        A2UICondition.Numeric("/deploy/canary", min = 1.0),
                    )),
                    "헬스체크를 확인하고 카나리 비중을 1% 이상으로 설정하세요.",
                ))),
        ),
        dataModel = mutableMapOf(
            "deploy" to mutableMapOf("canary" to 10, "healthOk" to false)
        ),
    )

    // ─────────────────────────────────────────────────────────
    // 3. Service Status Card
    // ─────────────────────────────────────────────────────────
    fun statusSurface(): A2UISurface = A2UISurface(
        surfaceId = "status_card_${System.currentTimeMillis()}",
        primaryColor = Color(0xFF2A6FDB),
        agentDisplayName = "Observability Bot",
        components = mapOf(
            "root" to A2UIComponent.Card("root", "col"),
            "col" to A2UIComponent.Column("col",
                children = listOf("hdr", "range_pick", "div", "metrics_row", "refresh_btn")),
            "hdr" to A2UIComponent.Row("hdr", children = listOf("hdr_ico", "hdr_txt"), align = "center"),
            "hdr_ico" to A2UIComponent.IconComp("hdr_ico", "activity", 19),
            "hdr_txt" to A2UIComponent.TextComp("hdr_txt", A2UIValue.Static("payment-service 상태"), "h2"),
            "range_pick" to A2UIComponent.ChoicePickerComp("range_pick",
                options = listOf(
                    A2UIOption("15분", "15m"),
                    A2UIOption("1시간", "1h"),
                    A2UIOption("24시간", "24h"),
                ),
                valuePath = "/status/range"),
            "div" to A2UIComponent.Divider("div"),
            "metrics_row" to A2UIComponent.Row("metrics_row",
                children = listOf("m1", "m2", "m3"), justify = "spaceBetween"),
            "m1" to A2UIComponent.Column("m1", children = listOf("m1_v", "m1_l")),
            "m1_v" to A2UIComponent.TextComp("m1_v", A2UIValue.FormatStr("\${/status/p99}ms"), "h1"),
            "m1_l" to A2UIComponent.TextComp("m1_l", A2UIValue.Static("p99 지연"), "caption"),
            "m2" to A2UIComponent.Column("m2", children = listOf("m2_v", "m2_l")),
            "m2_v" to A2UIComponent.TextComp("m2_v", A2UIValue.FormatStr("\${/status/errorRate}%"), "h1"),
            "m2_l" to A2UIComponent.TextComp("m2_l", A2UIValue.Static("에러율"), "caption"),
            "m3" to A2UIComponent.Column("m3", children = listOf("m3_v", "m3_l")),
            "m3_v" to A2UIComponent.TextComp("m3_v", A2UIValue.PathBound("/status/rps"), "h1"),
            "m3_l" to A2UIComponent.TextComp("m3_l", A2UIValue.Static("RPS"), "caption"),
            "refresh_btn" to A2UIComponent.ButtonComp("refresh_btn",
                label = A2UIValue.Static("새로고침"), variant = "secondary",
                action = A2UIAction(
                    eventName = "refresh_status",
                    contextRefs = listOf(A2UIContextRef("range", path = "/status/range")),
                )),
        ),
        dataModel = mutableMapOf(
            "status" to mutableMapOf(
                "range" to "1h",
                "p99" to 842,
                "errorRate" to 1.3,
                "rps" to "2.4k",
            )
        ),
    )

    // ─────────────────────────────────────────────────────────
    // 4. Risk Assessment Input Form
    // ─────────────────────────────────────────────────────────
    fun riskSurface(): A2UISurface = A2UISurface(
        surfaceId = "risk_form_${System.currentTimeMillis()}",
        primaryColor = Color(0xFF0E7C66),
        agentDisplayName = "안전 AI",
        components = mapOf(
            "root" to A2UIComponent.Card("root", "col"),
            "col" to A2UIComponent.Column("col",
                children = listOf("hdr", "trades_in", "loc_sel", "equip_in", "count_sel", "submit_btn")),
            "hdr" to A2UIComponent.TextComp("hdr", A2UIValue.Static("위험성평가 자동 생성"), "h2"),
            "trades_in" to A2UIComponent.TagInputComp("trades_in",
                label = "공종 선택", valuePath = "/risk/trades",
                suggestions = listOf("배관", "용접", "비계", "전기", "굴착", "도장"),
                placeholder = "공종명 직접 입력 후 추가"),
            "loc_sel" to A2UIComponent.SelectComp("loc_sel",
                label = "작업 장소", valuePath = "/risk/location",
                options = listOf(
                    A2UIOption("일반", "일반"),
                    A2UIOption("지상", "지상"),
                    A2UIOption("고소 (2m 이상)", "고소"),
                    A2UIOption("지하", "지하"),
                    A2UIOption("밀폐 공간", "밀폐공간"),
                )),
            "equip_in" to A2UIComponent.TextFieldComp("equip_in",
                label = "사용 장비", valuePath = "/risk/equip",
                placeholder = "예: 크레인, 용접기, 굴착기"),
            "count_sel" to A2UIComponent.SelectComp("count_sel",
                label = "공종당 항목 수", valuePath = "/risk/count",
                options = listOf(
                    A2UIOption("3개", "3"),
                    A2UIOption("5개", "5"),
                    A2UIOption("8개", "8"),
                    A2UIOption("10개", "10"),
                )),
            "submit_btn" to A2UIComponent.ButtonComp("submit_btn",
                label = A2UIValue.Static("AI 위험성평가 생성"),
                variant = "primary",
                action = A2UIAction(
                    eventName = "generate_risk",
                    contextRefs = listOf(
                        A2UIContextRef("trades", path = "/risk/trades"),
                        A2UIContextRef("count", path = "/risk/count"),
                        A2UIContextRef("location", path = "/risk/location"),
                        A2UIContextRef("equip", path = "/risk/equip"),
                    ),
                ),
                checks = listOf(A2UICheck(
                    A2UICondition.Required("/risk/trades"),
                    "공종을 1개 이상 선택해야 합니다.",
                ))),
        ),
        dataModel = mutableMapOf(
            "risk" to mutableMapOf(
                "trades" to mutableListOf<String>(),
                "location" to "일반",
                "equip" to "",
                "count" to "5",
            )
        ),
    )

    // ─────────────────────────────────────────────────────────
    // Risk result surface (generated after form submit)
    // ─────────────────────────────────────────────────────────
    fun buildRiskResultSurface(trades: List<String>, count: Int): A2UISurface {
        val rows = buildRiskRows(trades, count)
        val highCount = rows.count { (it["risk"] as? String) == "상" }
        return A2UISurface(
            surfaceId = "risk_result_${System.currentTimeMillis()}",
            primaryColor = Color(0xFF0E7C66),
            agentDisplayName = "안전 AI",
            components = mapOf(
                "root" to A2UIComponent.Card("root", "col"),
                "col" to A2UIComponent.Column("col", children = listOf("hdr", "div", "table")),
                "hdr" to A2UIComponent.TextComp("hdr",
                    A2UIValue.Static("${trades.size}개 공종 · 총 ${rows.size}건 · 고위험 ${highCount}건"), "caption"),
                "div" to A2UIComponent.Divider("div"),
                "table" to A2UIComponent.RiskCardList("table", "/result/rows"),
            ),
            dataModel = mutableMapOf(
                "result" to mutableMapOf("rows" to rows)
            ),
            sendDataModel = false,
        )
    }

    // ─────────────────────────────────────────────────────────
    // Sample risk row generator
    // ─────────────────────────────────────────────────────────
    private val HAZARD_DB: Map<String, List<Map<String, String>>> = mapOf(
        "배관" to listOf(
            mapOf("task" to "배관 자재 인양·운반", "cause" to "중량물 수동 취급으로 인한 근골격계 손상", "current" to "2인 1조 운반 기준", "improve" to "운반 보조기구(대차) 사용, 기계화 검토", "poss" to "중", "sev" to "하", "risk" to "하"),
            mapOf("task" to "배관 용접 연결 작업", "cause" to "용접 화재·폭발 및 유해가스 흡입", "current" to "소화기 배치", "improve" to "화기작업 허가서 발행 및 환기 강화", "poss" to "중", "sev" to "중", "risk" to "중"),
            mapOf("task" to "고압 배관 압력 시험", "cause" to "과압으로 인한 배관 파열·비산", "current" to "압력계 설치", "improve" to "안전밸브 설치 및 작업 반경 내 출입 통제", "poss" to "하", "sev" to "상", "risk" to "중"),
            mapOf("task" to "배관 지지대 설치", "cause" to "고소 작업 중 추락", "current" to "안전대 착용 기준", "improve" to "작업발판 설치 및 안전대 체결점 확보", "poss" to "중", "sev" to "상", "risk" to "상"),
            mapOf("task" to "배관 보온재 시공", "cause" to "보온재 분진 흡입 및 피부 자극", "current" to "마스크 착용", "improve" to "방진마스크(1급) 및 보호의 지급", "poss" to "중", "sev" to "하", "risk" to "하"),
        ),
        "용접" to listOf(
            mapOf("task" to "아크 용접 작업", "cause" to "아크광에 의한 눈 손상 및 용접 흄 흡입", "current" to "보호면 착용 기준", "improve" to "환기장치 설치 및 방독마스크 지급", "poss" to "중", "sev" to "중", "risk" to "중"),
            mapOf("task" to "가스 절단 작업", "cause" to "가스 누출 및 역화 폭발 위험", "current" to "밸브 잠금 절차 운영", "improve" to "가스 검지기 상시 운영 및 역화 방지기 부착", "poss" to "하", "sev" to "상", "risk" to "중"),
            mapOf("task" to "밀폐 공간 용접", "cause" to "산소 결핍 및 유해가스 축적", "current" to "환기팬 운영", "improve" to "밀폐공간 작업 허가 및 산소 농도 연속 감시", "poss" to "중", "sev" to "상", "risk" to "상"),
            mapOf("task" to "용접 후처리 그라인딩", "cause" to "연삭 파편 비산으로 인한 눈·피부 손상", "current" to "보안경 착용", "improve" to "방진 마스크 및 안면 보호구 착용 의무화", "poss" to "상", "sev" to "중", "risk" to "상"),
            mapOf("task" to "전기 저항 용접", "cause" to "감전 및 전기 화재", "current" to "절연 장갑 착용", "improve" to "접지 확인 및 누전 차단기 설치", "poss" to "하", "sev" to "상", "risk" to "중"),
        ),
        "비계" to listOf(
            mapOf("task" to "비계 설치·해체 작업", "cause" to "고소 작업 중 추락·낙하", "current" to "안전대 착용 기준", "improve" to "안전대 체결지점 확보 및 추락 방호망 설치", "poss" to "중", "sev" to "상", "risk" to "상"),
            mapOf("task" to "비계 자재 인양", "cause" to "자재 낙하로 인한 하부 작업자 충격", "current" to "낙하물 방지망 설치", "improve" to "하부 출입 통제 구역 설정 및 안전모 착용", "poss" to "중", "sev" to "중", "risk" to "중"),
            mapOf("task" to "비계 이동·조립", "cause" to "비계 전도 위험", "current" to "아웃트리거 전개 기준", "improve" to "지반 강도 확인 및 고정 볼트 체결 점검", "poss" to "하", "sev" to "상", "risk" to "중"),
            mapOf("task" to "작업 발판 설치", "cause" to "발판 이탈로 인한 추락", "current" to "발판 고정 기준", "improve" to "발판 걸림 장치 점검 및 안전난간 설치", "poss" to "중", "sev" to "상", "risk" to "상"),
            mapOf("task" to "비계 위 자재 보관", "cause" to "과하중으로 인한 비계 붕괴", "current" to "최대 하중 표시", "improve" to "하중 계산서 작성 및 허용 하중 초과 금지 교육", "poss" to "하", "sev" to "상", "risk" to "중"),
        ),
        "전기" to listOf(
            mapOf("task" to "전기 배선 연결 작업", "cause" to "활선 접촉으로 인한 감전", "current" to "차단기 OFF 후 작업", "improve" to "LOTO(잠금·표지) 절차 적용", "poss" to "하", "sev" to "상", "risk" to "중"),
            mapOf("task" to "전기 기기 설치·교체", "cause" to "절연 불량으로 인한 감전·화재", "current" to "절연 장갑 착용", "improve" to "절연 저항 측정 및 누전 차단기 설치", "poss" to "중", "sev" to "상", "risk" to "상"),
            mapOf("task" to "케이블 트레이 배선", "cause" to "고소 작업 중 추락", "current" to "안전대 착용 기준", "improve" to "작업 발판 설치 및 안전대 체결", "poss" to "중", "sev" to "상", "risk" to "상"),
            mapOf("task" to "전동 공구 사용", "cause" to "스파크로 인한 화재 또는 감전", "current" to "접지된 공구 사용", "improve" to "방폭형 공구 사용 및 정기 점검", "poss" to "하", "sev" to "중", "risk" to "하"),
            mapOf("task" to "외함 접지 작업", "cause" to "접지 불량으로 인한 감전", "current" to "접지 저항 측정", "improve" to "제3종 접지 기준 준수 및 기록 관리", "poss" to "하", "sev" to "상", "risk" to "중"),
        ),
        "굴착" to listOf(
            mapOf("task" to "터파기 굴착 작업", "cause" to "토사 붕괴로 인한 매몰", "current" to "경사면 유지 기준 적용", "improve" to "흙막이 설치 및 지하수 수위 모니터링", "poss" to "중", "sev" to "상", "risk" to "상"),
            mapOf("task" to "굴착기 운용", "cause" to "굴착기 전도·충돌 위험", "current" to "작업 반경 내 출입 금지", "improve" to "유도원 배치 및 후진 경보 장치 부착", "poss" to "중", "sev" to "중", "risk" to "중"),
            mapOf("task" to "지하 매설물 굴착", "cause" to "가스·전기 매설물 손상으로 인한 폭발·감전", "current" to "매설물 도면 확인", "improve" to "매설물 탐지기 사용 및 관계기관 입회", "poss" to "하", "sev" to "상", "risk" to "중"),
            mapOf("task" to "굴착면 수직 작업", "cause" to "수직 굴착면 붕괴", "current" to "경사면 유지", "improve" to "강재 흙막이 설치 및 계측 관리", "poss" to "중", "sev" to "상", "risk" to "상"),
            mapOf("task" to "되메우기 다짐 작업", "cause" to "다짐 장비 하중에 의한 지반 침하", "current" to "다짐 기준 준수", "improve" to "다짐도 확인 시험 실시 및 기록 관리", "poss" to "하", "sev" to "중", "risk" to "하"),
        ),
        "도장" to listOf(
            mapOf("task" to "도료 스프레이 도포", "cause" to "유기 용제 흡입으로 인한 중독", "current" to "방진마스크 착용", "improve" to "방독마스크(유기 증기용) 교체 및 환기 강화", "poss" to "중", "sev" to "중", "risk" to "중"),
            mapOf("task" to "고소 외벽 도장 작업", "cause" to "작업 중 추락", "current" to "안전대 착용 기준", "improve" to "작업 발판 및 안전대 체결점 확보", "poss" to "중", "sev" to "상", "risk" to "상"),
            mapOf("task" to "도료 혼합·희석 작업", "cause" to "인화성 증기 폭발·화재", "current" to "화기 금지 표시", "improve" to "방폭 구역 지정 및 정전기 방지 접지", "poss" to "하", "sev" to "상", "risk" to "중"),
            mapOf("task" to "도장 건조실 작업", "cause" to "밀폐 공간 유해가스 축적", "current" to "환기팬 운영", "improve" to "산소 농도 측정 및 밀폐공간 출입 허가 운영", "poss" to "중", "sev" to "상", "risk" to "상"),
            mapOf("task" to "도장 폐기물 처리", "cause" to "유해 폐기물 피부·호흡기 노출", "current" to "보호의 착용", "improve" to "지정 폐기물 처리 절차 준수 및 위탁 처리", "poss" to "하", "sev" to "중", "risk" to "하"),
        ),
    )

    private fun buildRiskRows(trades: List<String>, count: Int): List<Map<String, Any?>> =
        trades.flatMap { trade ->
            val hazards = HAZARD_DB[trade] ?: listOf(
                mapOf(
                    "task" to "$trade 일반 작업",
                    "cause" to "작업 중 부주의로 인한 안전사고",
                    "current" to "안전 수칙 준수",
                    "improve" to "작업 전 TBM(Tool Box Meeting) 실시",
                    "poss" to "중", "sev" to "중", "risk" to "중",
                )
            )
            hazards.take(count).map { h ->
                (h + mapOf("trade" to trade)).toMutableMap<String, Any?>()
            }
        }
}
