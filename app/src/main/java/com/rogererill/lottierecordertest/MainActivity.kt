package com.rogererill.lottierecordertest

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider.getUriForFile
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable
import java.io.File

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val textView: TextView = findViewById(R.id.tv_info)
    val startButton: Button = findViewById(R.id.button_start)

    val lottieComposition = LottieCompositionFactory.fromRawResSync(this, R.raw.android_wave)
    val lottieDrawable = LottieDrawable()
    lottieDrawable.composition = lottieComposition.value

    val path = getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: File(cacheDir, Environment.DIRECTORY_PICTURES).apply { mkdirs() }
    val videoFile = File(path, "lottie_in_video.mp4")
    val recordingOperation = RecordingOperation(Recorder(videoOutput = videoFile), FrameCreator(lottieDrawable))
    {
      textView.text = getString(R.string.recording_finished)
      openCreatedVideo(videoFile)
    }

    startButton.setOnClickListener {
      textView.text = getString(R.string.recording)
      recordingOperation.start()
    }
  }

  private fun openCreatedVideo(videoFile: File) {
    val intent = Intent()
    intent.action = Intent.ACTION_VIEW
    val uriForFile = getUriForFile(this, "com.rogererill.provider", videoFile)
    intent.setDataAndType(uriForFile, "video/*")
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    startActivity(intent)
  }
}
