package org.jellyfin.androidtv.ui.playback.xray

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.serializer.UUIDSerializer
import timber.log.Timber
import java.util.UUID

/**
 * Data served by the X-Ray server plugin (https://github.com/de-Bat/jellyfin-plugin-xray).
 */
@Serializable
data class XRayItem(
	@SerialName("ItemId") @Serializable(with = UUIDSerializer::class) val itemId: UUID,
	@SerialName("HasTimeline") val hasTimeline: Boolean,
	@SerialName("People") val people: List<XRayPerson> = emptyList(),
	@SerialName("Appearances") val appearances: List<XRayAppearance> = emptyList(),
) {
	/** People on screen at [positionTicks], in billing order. */
	fun peopleAt(positionTicks: Long): List<XRayPerson> {
		val present = appearances
			.filter { positionTicks >= it.startTicks && positionTicks < it.endTicks }
			.mapTo(HashSet()) { it.personId }
		return people.filter { it.id in present }
	}
}

@Serializable
data class XRayPerson(
	@SerialName("Id") @Serializable(with = UUIDSerializer::class) val id: UUID,
	@SerialName("Name") val name: String,
	@SerialName("Role") val role: String? = null,
	@SerialName("HasImage") val hasImage: Boolean = false,
)

@Serializable
data class XRayAppearance(
	@SerialName("PersonId") @Serializable(with = UUIDSerializer::class) val personId: UUID,
	@SerialName("StartTicks") val startTicks: Long,
	@SerialName("EndTicks") val endTicks: Long,
)

interface XRayRepository {
	/** Returns X-Ray data for an item, or null if the server has no X-Ray plugin or no data. */
	suspend fun getItem(itemId: UUID): XRayItem?

	fun getPersonImageUrl(person: XRayPerson): String?
}

class XRayRepositoryImpl(
	private val api: ApiClient,
) : XRayRepository {
	private val json = Json { ignoreUnknownKeys = true }
	private val mutex = Mutex()
	private val cache = mutableMapOf<UUID, XRayItem?>()

	override suspend fun getItem(itemId: UUID): XRayItem? = mutex.withLock {
		if (cache.containsKey(itemId)) return cache[itemId]

		val result = withContext(Dispatchers.IO) {
			try {
				val response = api.request(pathTemplate = "/XRay/Items/{itemId}", pathParameters = mapOf("itemId" to itemId))
				json.decodeFromString<XRayItem>(response.body.decodeToString()).takeIf { it.hasTimeline }
			} catch (err: InvalidStatusException) {
				// 404: plugin not installed (or item unknown) - remember that and stay quiet.
				Timber.d("X-Ray unavailable for %s: %s", itemId, err.status)
				null
			}
		}
		cache[itemId] = result
		result
	}

	override fun getPersonImageUrl(person: XRayPerson): String? = if (person.hasImage) {
		api.imageApi.getItemImageUrl(itemId = person.id, imageType = ImageType.PRIMARY, fillHeight = 168, fillWidth = 112)
	} else {
		null
	}
}
