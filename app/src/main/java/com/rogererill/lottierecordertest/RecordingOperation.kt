package com.rogererill.lottierecordertest

class RecordingOperation(
    private val recorder: Recorder,
    private val frameCreator: FrameCreator
) {

  fun start() {
    while (isRecording()) {
      recorder.nextFrame(frameCreator.generateFrame())
    }
    recorder.end()
  }

  fun isRecording() = !frameCreator.hasEnded()
}