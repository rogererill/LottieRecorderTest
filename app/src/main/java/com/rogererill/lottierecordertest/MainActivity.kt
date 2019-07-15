package com.rogererill.lottierecordertest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.LottieDrawable

class MainActivity : AppCompatActivity() {

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    val lottieComposition = LottieCompositionFactory.fromRawResSync(this, R.raw.lottielogo)
    val lottieDrawable = LottieDrawable()
    lottieDrawable.composition = lottieComposition.value
  }
}
