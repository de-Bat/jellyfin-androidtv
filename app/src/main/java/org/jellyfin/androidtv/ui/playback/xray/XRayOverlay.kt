package org.jellyfin.androidtv.ui.playback.xray

import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.MutableStateFlow
import org.jellyfin.androidtv.R
import org.jellyfin.androidtv.ui.base.Text
import org.jellyfin.androidtv.ui.composable.AsyncImage
import org.koin.compose.koinInject
import java.util.UUID

private const val MaxPeopleShown = 6
private val Accent = Color(0xFF00A4DC)

/**
 * Prime Video style X-Ray panel listing the cast members on screen at [positionTicks].
 * Renders nothing when the server has no X-Ray data or nobody is recognized.
 */
@Composable
fun XRayPanel(
	itemId: UUID?,
	positionTicks: Long,
	visible: Boolean,
	modifier: Modifier = Modifier,
	repository: XRayRepository = koinInject(),
) {
	var data by remember { mutableStateOf<XRayItem?>(null) }
	LaunchedEffect(itemId) {
		data = null
		if (itemId != null) data = runCatching { repository.getItem(itemId) }.getOrNull()
	}

	val people = data?.peopleAt(positionTicks).orEmpty()

	Box(modifier = modifier.padding(start = 48.dp, top = 140.dp, bottom = 200.dp), contentAlignment = Alignment.TopStart) {
		AnimatedVisibility(
			visible = visible && people.isNotEmpty(),
			enter = fadeIn() + slideInHorizontally { -it / 8 },
			exit = fadeOut() + slideOutHorizontally { -it / 8 },
		) {
			Column(
				modifier = Modifier
					.widthIn(min = 240.dp, max = 420.dp)
					.clip(RoundedCornerShape(8.dp))
					.background(Color.Black.copy(alpha = 0.72f))
					.padding(horizontal = 18.dp, vertical = 14.dp),
				verticalArrangement = Arrangement.spacedBy(10.dp),
			) {
				Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
					Text(text = stringResource(R.string.xray_title), color = Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
					Text(text = stringResource(R.string.xray_in_scene), color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
				}

				people.take(MaxPeopleShown).forEach { person ->
					XRayPersonRow(person, repository.getPersonImageUrl(person))
				}

				if (people.size > MaxPeopleShown) {
					Text(
						text = stringResource(R.string.xray_more, people.size - MaxPeopleShown),
						color = Color.White.copy(alpha = 0.6f),
						fontSize = 13.sp,
					)
				}
			}
		}
	}
}

@Composable
private fun XRayPersonRow(person: XRayPerson, imageUrl: String?) {
	Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
		Box(
			modifier = Modifier
				.size(width = 44.dp, height = 66.dp)
				.clip(RoundedCornerShape(4.dp))
				.background(Color.White.copy(alpha = 0.12f)),
		) {
			if (imageUrl != null) {
				AsyncImage(
					url = imageUrl,
					aspectRatio = 2f / 3f,
					scaleType = ImageView.ScaleType.CENTER_CROP,
					modifier = Modifier.size(width = 44.dp, height = 66.dp),
				)
			}
		}
		Column {
			Text(
				text = person.name,
				color = Color.White,
				fontSize = 17.sp,
				fontWeight = FontWeight.SemiBold,
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
			)
			if (!person.role.isNullOrBlank()) {
				Text(
					text = person.role,
					color = Color.White.copy(alpha = 0.65f),
					fontSize = 14.sp,
					maxLines = 1,
					overflow = TextOverflow.Ellipsis,
				)
			}
		}
	}
}

/**
 * View wrapper for the legacy (Leanback) player.
 */
class XRayOverlayView @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyle: Int = 0,
) : AbstractComposeView(context, attrs, defStyle) {
	private val _itemId = MutableStateFlow<UUID?>(null)
	private val _positionMs = MutableStateFlow(0L)
	private val _overlayVisible = MutableStateFlow(false)

	var itemId: UUID?
		get() = _itemId.value
		set(value) {
			_itemId.value = value
		}

	var currentPositionMs: Long
		get() = _positionMs.value
		set(value) {
			_positionMs.value = value
		}

	var overlayVisible: Boolean
		get() = _overlayVisible.value
		set(value) {
			_overlayVisible.value = value
		}

	@Composable
	override fun Content() {
		val itemId by _itemId.collectAsState()
		val positionMs by _positionMs.collectAsState()
		val visible by _overlayVisible.collectAsState()

		XRayPanel(itemId = itemId, positionTicks = positionMs * 10_000, visible = visible)
	}
}
