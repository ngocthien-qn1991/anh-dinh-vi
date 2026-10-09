package vn.anhdinhvi.app

import android.hardware.camera2.CameraCharacteristics
import android.util.Size
import android.view.Surface
import android.view.View
import androidx.activity.ComponentActivity
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * Máy ảnh gốc (CameraX) hiển thị bên dưới WebView, đúng vị trí khung ngắm trên trang.
 * Hỗ trợ zoom quang học của máy và chuyển sang ống kính góc rộng (.6x) nếu máy có.
 */
@androidx.annotation.OptIn(ExperimentalCamera2Interop::class)
class NativeCamera(
    private val activity: ComponentActivity,
    private val previewView: PreviewView,
    private val emit: (String) -> Unit
) {
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var facingBack = true
    private var onWide = false
    private var wideId: String? = null
    private var wideLabel = 0.6f
    private var pendingZoom = 1f
    private var mainMax = 1f
    private var zoomLive: LiveData<ZoomState>? = null
    private val zoomObserver = Observer<ZoomState> { report() }
    private val captureDir = File(activity.cacheDir, "capture").apply { mkdirs() }
    private val main = ContextCompat.getMainExecutor(activity)

    val captureFolder: File get() = captureDir

    fun start(back: Boolean) {
        facingBack = back
        onWide = false
        pendingZoom = 1f
        val future = ProcessCameraProvider.getInstance(activity)
        future.addListener({
            try {
                provider = future.get()
                findWide()
                bind()
            } catch (e: Exception) {
                fail(e.message ?: "lỗi máy ảnh")
            }
        }, main)
    }

    private fun idOf(i: CameraInfo): String? = try {
        Camera2CameraInfo.from(i).cameraId
    } catch (e: Exception) { null }

    private fun facingOf(i: CameraInfo): Int? = try {
        Camera2CameraInfo.from(i).getCameraCharacteristic(CameraCharacteristics.LENS_FACING)
    } catch (e: Exception) { null }

    /** Độ rộng góc nhìn tương đối: kích thước cảm biến / tiêu cự. */
    private fun fovScore(i: CameraInfo): Float {
        return try {
            val c = Camera2CameraInfo.from(i)
            val focal = c.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?.minOrNull() ?: return 0f
            val size = c.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return 0f
            if (focal <= 0f) 0f else size.width / focal
        } catch (e: Exception) { 0f }
    }

    private fun findWide() {
        wideId = null
        val p = provider ?: return
        val backs = p.availableCameraInfos.filter { facingOf(it) == CameraCharacteristics.LENS_FACING_BACK }
        if (backs.size < 2) return
        val mainInfo = try {
            CameraSelector.DEFAULT_BACK_CAMERA.filter(backs).firstOrNull()
        } catch (e: Exception) { null } ?: return
        val mainScore = fovScore(mainInfo)
        if (mainScore <= 0f) return
        val mainId = idOf(mainInfo)
        val best = backs.filter { idOf(it) != mainId }.maxByOrNull { fovScore(it) } ?: return
        val s = fovScore(best)
        if (s > mainScore * 1.25f) {
            wideId = idOf(best)
            wideLabel = ((mainScore / s) * 10f).roundToInt() / 10f
        }
    }

    private fun bind() {
        val p = provider ?: return
        val wid = wideId
        val selector = when {
            !facingBack -> CameraSelector.DEFAULT_FRONT_CAMERA
            onWide && wid != null -> CameraSelector.Builder()
                .addCameraFilter { list -> list.filter { idOf(it) == wid } }
                .build()
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        val previewSel = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
        val captureSel = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(Size(4000, 3000), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            )
            .build()
        val preview = Preview.Builder().setResolutionSelector(previewSel).build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val cap = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(captureSel)
            .setTargetRotation(previewView.display?.rotation ?: Surface.ROTATION_0)
            .build()
        try {
            p.unbindAll()
            val cam = p.bindToLifecycle(activity, selector, preview, cap)
            camera = cam
            imageCapture = cap
            zoomLive?.removeObserver(zoomObserver)
            zoomLive = cam.cameraInfo.zoomState
            zoomLive?.observe(activity, zoomObserver)
            if (!onWide) cam.cameraControl.setZoomRatio(clampZoom(pendingZoom))
            report()
        } catch (e: Exception) {
            if (onWide) { onWide = false; wideId = null; bind() }
            else fail(e.message ?: "không mở được máy ảnh")
        }
    }

    private fun clampZoom(z: Float): Float {
        val st = camera?.cameraInfo?.zoomState?.value ?: return z
        return z.coerceIn(st.minZoomRatio, st.maxZoomRatio)
    }

    private fun report() {
        val st = camera?.cameraInfo?.zoomState?.value
        val min = st?.minZoomRatio ?: 1f
        val max = st?.maxZoomRatio ?: 1f
        val cur = st?.zoomRatio ?: 1f
        if (!onWide) mainMax = max
        val lens = facingBack && min >= 0.95f && wideId != null
        val effMin = when {
            min < 0.95f -> min
            lens -> wideLabel
            else -> 1f
        }
        val effZoom = if (onWide) wideLabel else cur
        val effMax = if (onWide) mainMax else max
        val json = JSONObject()
            .put("ok", true)
            .put("min", effMin.toDouble())
            .put("max", effMax.toDouble())
            .put("zoom", effZoom.toDouble())
            .put("lens", lens)
            .put("back", facingBack)
        emit("window.onNativeCam && window.onNativeCam($json)")
    }

    private fun fail(msg: String) {
        val json = JSONObject().put("ok", false).put("msg", "Không mở được máy ảnh ($msg)")
        emit("window.onNativeCam && window.onNativeCam($json)")
    }

    fun setZoom(z: Float) {
        val cam = camera ?: return
        val st = cam.cameraInfo.zoomState.value
        val min = st?.minZoomRatio ?: 1f
        if (facingBack && !onWide && z < 0.95f && min >= 0.95f && wideId != null) {
            onWide = true; bind(); return
        }
        if (onWide && z >= 0.95f) {
            onWide = false; pendingZoom = z; bind(); return
        }
        if (onWide) return
        cam.cameraControl.setZoomRatio(clampZoom(z))
    }

    fun focus(fx: Float, fy: Float) {
        val cam = camera ?: return
        if (previewView.width == 0) return
        val point = previewView.meteringPointFactory.createPoint(fx * previewView.width, fy * previewView.height)
        try {
            cam.cameraControl.startFocusAndMetering(FocusMeteringAction.Builder(point).build())
        } catch (e: Exception) { }
    }

    fun shoot() {
        val cap = imageCapture
        if (cap == null) {
            emit("window.onNativeShot && window.onNativeShot(null, 'Máy ảnh chưa sẵn sàng')")
            return
        }
        captureDir.listFiles()?.forEach { it.delete() }
        val name = "shot_${System.currentTimeMillis()}.jpg"
        val file = File(captureDir, name)
        cap.targetRotation = previewView.display?.rotation ?: Surface.ROTATION_0
        val opts = ImageCapture.OutputFileOptions.Builder(file).build()
        cap.takePicture(opts, main, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                emit("window.onNativeShot && window.onNativeShot('https://appassets.androidplatform.net/capture/$name', null)")
            }

            override fun onError(e: ImageCaptureException) {
                emit("window.onNativeShot && window.onNativeShot(null, ${JSONObject.quote(e.message ?: "lỗi chụp")})")
            }
        })
    }

    /** Đặt khung camera đúng vị trí khung ngắm trên trang (đơn vị CSS px = dp). */
    fun place(x: Float, y: Float, w: Float, h: Float) {
        val d = activity.resources.displayMetrics.density
        val pw = (w * d).roundToInt().coerceAtLeast(1)
        val ph = (h * d).roundToInt().coerceAtLeast(1)
        val lp = previewView.layoutParams
        if (lp.width != pw || lp.height != ph) {
            lp.width = pw; lp.height = ph
            previewView.layoutParams = lp
        }
        previewView.translationX = x * d
        previewView.translationY = y * d
        if (previewView.visibility != View.VISIBLE) previewView.visibility = View.VISIBLE
    }
}
