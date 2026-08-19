package com.stockbook.app.photos

import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import com.stockbook.core.model.PhotoPolicy
import java.io.File

/**
 * Getting a photograph of a bill, without asking the phone for anything.
 *
 * Both routes are deliberate:
 *
 * - **The gallery** goes through `PickVisualMedia`, which hands back one picture
 *   and nothing else. It needs no permission on any version of Android — the
 *   picker runs outside this app and only what the owner taps comes back.
 * - **The camera** goes through `ACTION_IMAGE_CAPTURE`, which opens the phone's
 *   own camera app. This needs no permission *because the manifest does not ask
 *   for one*: Android requires `CAMERA` for this intent only from apps that
 *   declare it. Declaring it would make the app ask for something it does not
 *   need, and would fail the check that reads the built APK.
 *
 * The captured file lands in the cache, is shrunk into [PhotoStore], and the
 * original is thrown away. Cache rather than the photo directory so that a
 * capture abandoned halfway leaves nothing for the sweep to reason about, and so
 * "Clear cache" can never reach a photograph the owner kept.
 */
class PhotoCapture(
    val takePhoto: () -> Unit,
    val chooseFromPhotos: () -> Unit
)

/**
 * Wires both routes up and hands back a saved id.
 *
 * [onSaved] is called with the new id once the picture has been shrunk and
 * written. [onFailed] is called when nothing usable came back — a picker can
 * return something that resolves to no image, and a phone can have no camera app
 * at all. Neither is a crash, and neither is silence.
 */
@Composable
fun rememberPhotoCapture(
    onSaved: (String) -> Unit,
    onFailed: (String) -> Unit
): PhotoCapture {
    val context = LocalContext.current
    val photos = remember(context) { PhotoStore(context) }

    // Where the camera app is told to write. Held across the launch because the
    // result only says whether it succeeded, not where it put anything.
    var pending by remember { mutableStateOf<File?>(null) }

    val fromGallery = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val id = photos.save(uri)
        if (id != null) onSaved(id) else onFailed(PhotoFailure.UNREADABLE)
    }

    val fromCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { taken: Boolean ->
        val file = pending
        pending = null
        if (!taken || file == null) {
            file?.delete()
            return@rememberLauncherForActivityResult
        }
        val id = photos.save(Uri.fromFile(file))
        // The full-size original has served its purpose the moment a shrunk copy
        // exists. Leaving it in the cache would double what the app costs in
        // storage for no benefit at all.
        file.delete()
        if (id != null) onSaved(id) else onFailed(PhotoFailure.UNREADABLE)
    }

    return remember(fromGallery, fromCamera) {
        PhotoCapture(
            takePhoto = {
                val file = File(
                    File(context.cacheDir, "capture").also { it.mkdirs() },
                    PhotoPolicy.fileName(PhotoPolicy.newId())
                )
                pending = file
                try {
                    fromCamera.launch(
                        FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                    )
                } catch (_: ActivityNotFoundException) {
                    // A phone with no camera app. Rare, and not a reason to fall
                    // over — the gallery is still there.
                    pending = null
                    file.delete()
                    onFailed(PhotoFailure.NO_CAMERA)
                }
            },
            chooseFromPhotos = {
                fromGallery.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
        )
    }
}

/** Which of the two things that can go wrong did. The words live in `Strings`. */
object PhotoFailure {
    const val UNREADABLE = "unreadable"
    const val NO_CAMERA = "no-camera"
}
