package com.rogererill.lottierecordertest

import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import java.io.File

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val lottieComposition = LottieCompositionFactory.fromRawResSync(this, R.raw.android_wave)
    val lottieDrawable = LottieDrawable()
    lottieDrawable.composition = lottieComposition.value

    val path = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(cacheDir, Environment.DIRECTORY_PICTURES).apply { mkdirs() }
    val videoFile = File(path, "lottie_in_video.mp4")
    val recordingOperation = RecordingOperation(
        Recorder(videoOutput = videoFile),
        FrameCreator(lottieDrawable)
    )
    recordingOperation.start()
  }
}
