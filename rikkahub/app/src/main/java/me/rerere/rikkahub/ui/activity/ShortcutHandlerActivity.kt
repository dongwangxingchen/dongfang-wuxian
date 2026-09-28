package me.rerere.rikkahub.ui.activity

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
// [DFWX PATCH P7] 移除 me.rerere.rikkahub.BuildConfig 引用：APPLICATION_ID 是 application
// 模块专属字段，本模块转 library 后不存在；改用 Context.packageName（宿主合并后即宿主 applicationId）。
import me.rerere.rikkahub.RouteActivity
import java.io.File

class ShortcutHandlerActivity : ComponentActivity() {
    private var photoURI: Uri? = null

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            finish()
        }
    }

    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            photoURI?.let {
                val intent = Intent(this, RouteActivity::class.java).apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_STREAM, it.toString())
                }
                startActivity(intent)
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun launchCamera() {
        val imageFile = File(cacheDir, "shortcut_camera_image.jpg")
        // [DFWX PATCH P7] 上游为 "${BuildConfig.APPLICATION_ID}.fileprovider"
        photoURI = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)
        photoURI?.let {
            takePictureLauncher.launch(it)
        } ?: finish()
    }
}
