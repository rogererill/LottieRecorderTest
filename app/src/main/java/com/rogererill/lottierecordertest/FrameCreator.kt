package com.rogererill.lottierecordertest

import android.graphics.drawable.Drawable
import com.airbnb.lottie.LottieDrawable

class FrameCreator(private val lottieDrawable: LottieDrawable) {

  init {
    lottieDrawable.scale = VIDEO_WIDTH_PX / lottieDrawable.intrinsicWidth
  }

  private val durationInFrames: Int = lottieDrawable.composition.durationFrames.toInt()
  private var currentFrame: Int = 0

  fun generateFrame(): Drawable {
    lottieDrawable.frame = currentFrame
    ++currentFrame
    return lottieDrawable
  }

  fun hasEnded() = currentFrame > durationInFrames
}

private const val VIDEO_WIDTH_PX = 720f