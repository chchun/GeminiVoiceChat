package com.aromit.geminivoicechat.a2ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class A2UIEnvelopeApplierTest {

    private val createEnvelope = """
        {"version":"v0.9","createSurface":{"surfaceId":"activity-score-summary",
         "catalogId":"https://a2ui.org/specification/v0_9/catalogs/basic/catalog.json",
         "theme":{"primaryColor":"#D30707"},"sendDataModel":true}}
    """.trimIndent()

    private val componentsEnvelope = """
        {"version":"v0.9","updateComponents":{"surfaceId":"activity-score-summary",
         "components":[{"id":"root","component":"ActvScoreSummaryCard","value":{"path":"/activityScore"}}]}}
    """.trimIndent()

    private val dataEnvelope = """
        {"version":"v0.9","updateDataModel":{"surfaceId":"activity-score-summary","path":"/",
         "value":{"activityScore":{"title":"종합 안전활동 점수","score":16,"gradeName":"나쁨",
         "gradeCounts":[{"label":"나쁨","count":3,"tone":"bad"}]},"raw":{"status":200}}}}
    """.trimIndent()

    @Test
    fun `surfaceIdOf는 엔벨로프 3종 모두에서 surfaceId를 찾는다`() {
        assertEquals("activity-score-summary", A2UIEnvelopeApplier.surfaceIdOf(createEnvelope))
        assertEquals("activity-score-summary", A2UIEnvelopeApplier.surfaceIdOf(componentsEnvelope))
        assertEquals("activity-score-summary", A2UIEnvelopeApplier.surfaceIdOf(dataEnvelope))
        assertNull(A2UIEnvelopeApplier.surfaceIdOf("not json"))
    }

    @Test
    fun `엔벨로프 3종 순차 적용 시 서피스가 완성된다`() {
        var surface = A2UIEnvelopeApplier.apply(null, createEnvelope)
        assertNotNull(surface)
        assertEquals("activity-score-summary", surface!!.surfaceId)
        assertTrue(surface.sendDataModel)
        assertTrue(surface.components.isEmpty())

        surface = A2UIEnvelopeApplier.apply(surface, componentsEnvelope)
        val root = surface!!.components["root"] as A2UIComponent.ServerCard
        assertEquals("ActvScoreSummaryCard", root.kind)
        assertEquals("/activityScore", root.valuePath)

        surface = A2UIEnvelopeApplier.apply(surface, dataEnvelope)
        val score = getAtPath(surface!!.dataModel, "/activityScore/score")
        assertEquals(16, (score as Number).toInt())
        assertEquals("나쁨", getAtPath(surface.dataModel, "/activityScore/gradeName"))
    }

    @Test
    fun `updateDataModel의 루트 경로는 기존 모델에 병합된다`() {
        var surface = A2UIEnvelopeApplier.apply(null, createEnvelope)!!
        surface = A2UIEnvelopeApplier.apply(surface, dataEnvelope)!!
        val second = """
            {"version":"v0.9","updateDataModel":{"surfaceId":"activity-score-summary",
             "path":"/extra","value":"hello"}}
        """.trimIndent()
        surface = A2UIEnvelopeApplier.apply(surface, second)!!
        assertEquals("hello", getAtPath(surface.dataModel, "/extra"))
        assertEquals("나쁨", getAtPath(surface.dataModel, "/activityScore/gradeName"))
    }

    @Test
    fun `createSurface 이전의 update나 파싱 실패는 현재 상태를 그대로 반환한다`() {
        assertNull(A2UIEnvelopeApplier.apply(null, componentsEnvelope))
        assertNull(A2UIEnvelopeApplier.apply(null, "broken json"))

        val surface = A2UIEnvelopeApplier.apply(null, createEnvelope)
        val after = A2UIEnvelopeApplier.apply(surface, "broken json")
        assertEquals(surface, after)
    }

    @Test
    fun `표준 컴포넌트 폼이 기존 모델 타입으로 매핑된다 - T110`() {
        val createForm = """
            {"version":"v0.9","createSurface":{"surfaceId":"safety-report-form",
             "theme":{"primaryColor":"#2563EB","agentDisplayName":"Safety Report Bot"},"sendDataModel":true}}
        """.trimIndent()
        val formComponents = """
            {"version":"v0.9","updateComponents":{"surfaceId":"safety-report-form","components":[
              {"id":"root","component":"Card","child":"form"},
              {"id":"form","component":"Column","children":["title","kind","dt_row","submit"]},
              {"id":"title","component":"Text","text":"아차사고 등록","variant":"h2"},
              {"id":"kind","component":"ChoicePicker","variant":"mutuallyExclusive",
               "value":{"path":"/report/kind"},
               "options":[{"label":"안전제안","value":"safetySuggestion"},{"label":"아차사고","value":"nearMiss"}]},
              {"id":"dt_row","component":"Row","children":["receipt_dt","registrant"],"gap":12},
              {"id":"receipt_dt","component":"TextField","label":"청취일시","variant":"datetime",
               "value":{"path":"/report/receiptDateTime"},
               "checks":[{"call":"required","args":{"value":{"path":"/report/receiptDateTime"}},"message":"청취일시를 입력해 주세요."}]},
              {"id":"registrant","component":"TextField","label":"등록자","variant":"shortText",
               "value":{"path":"/report/registrant"},"readonly":true},
              {"id":"photos","component":"FileUpload","label":"사진","value":{"path":"/report/photos"}},
              {"id":"submit","component":"Button","text":"아차사고 등록","variant":"primary",
               "checks":[{"call":"required","args":{"value":{"path":"/report/content"}},"message":"내용을 입력해 주세요."}],
               "action":{"event":{"name":"register_safety_report",
                 "context":{"kind":{"path":"/report/kind"},"workerName":{"path":"/report/workerName"}}}}}
            ]}}
        """.trimIndent()

        var surface = A2UIEnvelopeApplier.apply(null, createForm)!!
        assertEquals("Safety Report Bot", surface.agentDisplayName)

        surface = A2UIEnvelopeApplier.apply(surface, formComponents)!!
        assertEquals("form", (surface.components["root"] as A2UIComponent.Card).child)
        assertEquals(4, (surface.components["form"] as A2UIComponent.Column).children.size)

        val title = surface.components["title"] as A2UIComponent.TextComp
        assertEquals("h2", title.variant)
        assertEquals("아차사고 등록", (title.text as A2UIValue.Static).text)

        val picker = surface.components["kind"] as A2UIComponent.ChoicePickerComp
        assertEquals("/report/kind", picker.valuePath)
        assertEquals(2, picker.options.size)

        val dt = surface.components["receipt_dt"] as A2UIComponent.TextFieldComp
        assertEquals("/report/receiptDateTime", dt.valuePath)
        assertEquals(1, dt.checks.size)
        assertEquals(
            "/report/receiptDateTime",
            (dt.checks[0].condition as A2UICondition.Required).valuePath,
        )

        assertTrue((surface.components["registrant"] as A2UIComponent.TextFieldComp).readonly)

        // FileUpload — spec 002 T105 에서 정식 컴포넌트로 매핑
        val photos = surface.components["photos"] as A2UIComponent.FileUploadComp
        assertEquals("/report/photos", photos.valuePath)
        assertEquals(5, photos.maxFiles)

        val submit = surface.components["submit"] as A2UIComponent.ButtonComp
        assertEquals("register_safety_report", submit.action!!.eventName)
        assertEquals("/report/kind", submit.action!!.contextRefs.first { it.key == "kind" }.path)
        assertEquals(1, submit.checks.size)
    }

    @Test
    fun `알 수 없는 컴포넌트 kind도 ServerCard로 담긴다 - 렌더러 폴백 몫`() {
        var surface = A2UIEnvelopeApplier.apply(null, createEnvelope)!!
        val unknown = """
            {"version":"v0.9","updateComponents":{"surfaceId":"activity-score-summary",
             "components":[{"id":"root","component":"FutureCard","value":{"path":"/x"}}]}}
        """.trimIndent()
        surface = A2UIEnvelopeApplier.apply(surface, unknown)!!
        assertEquals("FutureCard", (surface.components["root"] as A2UIComponent.ServerCard).kind)
    }
}
