package org.jellyfin.androidtv.ui.playback.xray

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

class XRayItemTests : FunSpec({
	// Shape returned by the X-Ray server plugin (GUIDs without dashes, PascalCase, extra fields).
	val response = """
		{"ItemId":"ed343c8fd888bf6d3c0b2a5af844c4c8","HasTimeline":true,"Source":"face",
		"People":[
			{"Id":"61b0d738788a93e5906342b0022382d9","Name":"Cary Grant","Role":"Peter Joshua","HasImage":true},
			{"Id":"6bd6346cf19e79aa2bcb96f33f637860","Name":"Audrey Hepburn","Role":"Regina Lampert","HasImage":true},
			{"Id":"e78ac9f21fb19a2f7476be0c64fb925d","Name":"Jacques Marin","Role":null,"HasImage":false}],
		"Appearances":[
			{"PersonId":"6bd6346cf19e79aa2bcb96f33f637860","StartTicks":30000000,"EndTicks":1850000000},
			{"PersonId":"61b0d738788a93e5906342b0022382d9","StartTicks":190000000,"EndTicks":1190000000},
			{"PersonId":"e78ac9f21fb19a2f7476be0c64fb925d","StartTicks":1500000000,"EndTicks":1600000000}],
		"Analysis":null}
	""".trimIndent()

	val item = Json { ignoreUnknownKeys = true }.decodeFromString<XRayItem>(response)

	test("parses plugin response") {
		item.hasTimeline shouldBe true
		item.people.size shouldBe 3
		item.people[0].name shouldBe "Cary Grant"
		item.people[2].role shouldBe null
	}

	test("returns people on screen in billing order") {
		item.peopleAt(600_000_000).map { it.name } shouldBe listOf("Cary Grant", "Audrey Hepburn")
		item.peopleAt(1_550_000_000).map { it.name } shouldBe listOf("Audrey Hepburn", "Jacques Marin")
	}

	test("end of an appearance is exclusive") {
		item.peopleAt(1_190_000_000).map { it.name } shouldBe listOf("Audrey Hepburn")
		item.peopleAt(1_850_000_000).shouldBeEmpty()
	}
})
