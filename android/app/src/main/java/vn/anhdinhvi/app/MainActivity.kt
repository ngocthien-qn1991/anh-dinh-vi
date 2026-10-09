package vn.anhdinhvi.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null
    private var pendingGeo: Pair<String, GeolocationPermissions.Callback>? = null
    private var pendingCamRequest: PermissionRequest? = null
    private var afterCamPermission: (() -> Unit)? = null

    private val askCamera =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            pendingCamRequest?.let { req ->
                if (granted) req.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) else req.deny()
            }
            pendingCamRequest = null
            val next = afterCamPermission
            afterCamPermission = null
            if (granted) next?.invoke() else {
                fileCallback?.onReceiveValue(null); fileCallback = null
                if (next != null) toastJs("Cần cho phép máy ảnh để chụp")
            }
        }

    private fun hasCamera(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            val cb = fileCallback
            fileCallback = null
            val uri = cameraUri
            cb?.onReceiveValue(if (ok && uri != null) arrayOf(uri) else null)
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val cb = fileCallback
            fileCallback = null
            cb?.onReceiveValue(uri?.let { arrayOf(it) })
        }

    private val askPermissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                hasLocation()
            pendingGeo?.let { (origin, cb) -> cb.invoke(origin, granted, false) }
            pendingGeo = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#14161A")
        window.navigationBarColor = Color.parseColor("#14161A")

        File(cacheDir, "camera").deleteRecursively()
        File(cacheDir, "share").deleteRecursively()

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        setContentView(web)

        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            @Suppress("DEPRECATION")
            setGeolocationEnabled(true)
            allowFileAccess = false
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
        }
        web.addJavascriptInterface(Bridge(), "AndroidBridge")

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.host == "appassets.androidplatform.net") return false
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url)); true
                } catch (e: Exception) { true }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (!request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                        request.deny(); return@runOnUiThread
                    }
                    if (hasCamera()) request.grant(arrayOf(PermissionRequest.RESOURCE_VIDEO_CAPTURE))
                    else { pendingCamRequest = request; askCamera.launch(Manifest.permission.CAMERA) }
                }
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String, callback: GeolocationPermissions.Callback
            ) {
                if (hasLocation()) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeo = origin to callback
                    askPermissions.launch(neededPermissions())
                }
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    if (params.isCaptureEnabled) {
                        val dir = File(cacheDir, "camera").apply { mkdirs() }
                        val file = File(dir, "cap_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(
                            this@MainActivity, "$packageName.files", file
                        )
                        cameraUri = uri
                        if (hasCamera()) takePicture.launch(uri)
                        else {
                            afterCamPermission = { takePicture.launch(uri) }
                            askCamera.launch(Manifest.permission.CAMERA)
                        }
                    } else {
                        pickImage.launch("image/*")
                    }
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    callback.onReceiveValue(null)
                    toastJs("Không mở được máy ảnh: ${e.message}")
                    true
                }
            }
        }

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web.canGoBack()) web.goBack() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun neededPermissions(): Array<String> {
        val list = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            list.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        return list.toTypedArray()
    }

    private fun toastJs(msg: String) {
        runOnUiThread {
            web.evaluateJavascript(
                "window.onNativeToast && window.onNativeToast(${JSONObject.quote(msg)})", null
            )
        }
    }

    private fun saveToGallery(bytes: ByteArray, name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AnhDinhVi")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("không tạo được tệp ảnh")
            resolver.openOutputStream(uri)?.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                runOnUiThread { askPermissions.launch(neededPermissions()) }
                throw IllegalStateException("cần cấp quyền bộ nhớ, bấm Lưu ảnh lần nữa")
            }
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "AnhDinhVi")
            dir.mkdirs()
            val file = File(dir, name)
            file.writeBytes(bytes)
            MediaScannerConnection.scanFile(this, arrayOf(file.absolutePath), arrayOf("image/jpeg"), null)
        }
    }

    private val net = Executors.newFixedThreadPool(3)

    /** Gọi mạng phía Android, tự đi theo chuyển hướng (Apps Script trả 302 sang googleusercontent). */
    private fun httpCall(method: String, startUrl: String, body: String): Pair<Int, String> {
        var url = startUrl
        var m = method
        var payload: String? = if (method == "POST") body else null
        repeat(6) {
            val c = URL(url).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20000
            c.readTimeout = 60000
            c.requestMethod = m
            c.setRequestProperty("User-Agent", "AnhDinhVi/1.0 (Android)")
            c.setRequestProperty("Accept-Language", "vi")
            if (payload != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "text/plain; charset=utf-8")
                c.outputStream.use { it.write(payload!!.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code in 300..399) {
                val loc = c.getHeaderField("Location") ?: return code to ""
                url = URL(URL(url), loc).toString()
                m = "GET"; payload = null
                c.disconnect()
                return@repeat
            }
            val stream = if (code >= 400) c.errorStream else c.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            c.disconnect()
            return code to text
        }
        return 0 to "chuyển hướng quá nhiều lần"
    }

    inner class Bridge {
        @JavascriptInterface
        fun http(id: String, method: String, url: String, body: String) {
            net.execute {
                val (code, text) = try {
                    httpCall(method, url, body)
                } catch (e: Exception) {
                    0 to (e.message ?: e.javaClass.simpleName)
                }
                runOnUiThread {
                    web.evaluateJavascript(
                        "window.__nativeHttpDone(${JSONObject.quote(id)}, $code, ${JSONObject.quote(text)})", null
                    )
                }
            }
        }

        @JavascriptInterface
        fun saveImage(base64: String, name: String) {
            try {
                saveToGallery(Base64.decode(base64, Base64.DEFAULT), name)
                toastJs("Đã lưu vào Thư viện ảnh (album AnhDinhVi)")
            } catch (e: Exception) {
                toastJs("Chưa lưu được ảnh: ${e.message}")
            }
        }

        @JavascriptInterface
        fun shareImage(base64: String, name: String) {
            try {
                val dir = File(cacheDir, "share").apply { mkdirs() }
                val file = File(dir, name)
                file.writeBytes(Base64.decode(base64, Base64.DEFAULT))
                val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.files", file)
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runOnUiThread { startActivity(Intent.createChooser(send, "Chia sẻ ảnh")) }
            } catch (e: Exception) {
                toastJs("Chưa chia sẻ được: ${e.message}")
            }
        }
    }
}
