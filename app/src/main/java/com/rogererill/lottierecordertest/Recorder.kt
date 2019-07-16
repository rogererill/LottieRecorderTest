package com.rogererill.lottierecordertest

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.view.Surface
import java.io.Closeable
import java.io.File
import java.nio.ByteBuffer

class Recorder(
    mimeType: String = "video/avc",
    bitRate: Int = DEFAULT_BITRATE,
    iFrameInterval: Int = DEFAULT_IFRAME_INTERVAL,
    private val framesPerSecond: Int = DEFAULT_FPS,
    width: Int = DEFAULT_WIDTH,
    height: Int = DEFAULT_HEIGHT,
    videoOutput: File
) : Closeable {

  private val videoBufferInfo: MediaCodec.BufferInfo = MediaCodec.BufferInfo()
  private lateinit var videoEncoder: MediaCodec
  private lateinit var muxer: MediaMuxer
  private lateinit var inputSurface: Surface
  private var trackIndex: Int = 0
  private var muxerStarted: Boolean = false
  private var fakePts: Long = 0
  private var videoLengthInMs: Long = 0

  companion object {
    private const val VERBOSE = false
    private const val DEFAULT_IFRAME_INTERVAL = 5
    private const val DEFAULT_BITRATE = 4 * 1000 * 1000
    private const val DEFAULT_WIDTH = 720
    private const val DEFAULT_HEIGHT = 720
    private const val DEFAULT_FPS = 30
    private const val TIMEOUT_USEC = 10000
  }

  init {
    if (width < 0) {
      throw IllegalArgumentException("You must set a positive width")
    }
    if (height < 0) {
      throw IllegalArgumentException("You must set a positive height")
    }
    if (framesPerSecond < 0) {
      throw IllegalArgumentException("You must set a positive number of frames per second")
    }

    val videoFormat = createMediaFormat(mimeType, width, height, bitRate, iFrameInterval)

    // Create a MediaCodec videoEncoder, and configure it with our format.  Get a Surface
    // we can use for input and wrap it with a class that handles the EGL work.
    startVideoEncoder(mimeType, videoFormat)

    // Create a MediaMuxer.  We can't add the video track and start() the muxer here,
    // because our MediaFormat doesn't have the Magic Goodies.  These can only be
    // obtained from the videoEncoder after it has started processing data.
    //
    // We're not actually interested in multiplexing audio.  We just want to convert
    // the raw H.264 elementary stream we get from MediaCodec into a .mp4 file.
    createMediaMuxer(videoOutput)
  }

  fun nextFrame(currentFrame: Drawable) {
    drainEncoder(false)
    val canvas = inputSurface.lockCanvas(null)
    try {
      canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)  // Here you need to set some kind of background. Could be any color
      currentFrame.draw(canvas)
    } finally {
      inputSurface.unlockCanvasAndPost(canvas)
    }
  }

  fun end() {
    drainEncoder(true)
    close()
  }

  private fun createMediaMuxer(output: File) {
    if (VERBOSE) Log.d("TAG", "inputSurface will go to $output")

    muxer = MediaMuxer(
        output.toString(),
        MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
    )

    trackIndex = -1
    muxerStarted = false
  }

  private fun startVideoEncoder(mimeType: String, videoFormat: MediaFormat) {
    videoEncoder = MediaCodec.createEncoderByType(mimeType)
    videoEncoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
    inputSurface = videoEncoder.createInputSurface()
    videoEncoder.start()
  }

  private fun createMediaFormat(
      mimeType: String,
      width: Int,
      height: Int,
      bitRate: Int,
      iFrameInterval: Int
  ): MediaFormat {
    val videoFormat = MediaFormat.createVideoFormat(mimeType, width, height)
    videoFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
    videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
    videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framesPerSecond)
    videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameInterval)
    if (VERBOSE) Log.d("TAG", "format: $videoFormat")
    return videoFormat
  }

  /**
   * Extracts all pending data from the videoEncoder.
   *
   *
   * If endOfStream is not set, this returns when there is no more data to drain.  If it
   * is set, we send EOS to the videoEncoder, and then iterate until we see EOS on the inputSurface.
   * Calling this with endOfStream set should be done once, right before stopping the muxer.
   */
  @SuppressLint("SwitchIntDef")
  private fun drainEncoder(endOfStream: Boolean) {
    if (VERBOSE) Log.d("TAG", "drainEncoder($endOfStream)")

    if (endOfStream) {
      Log.d("TAG", "sending end of stream to videoEncoder")
      videoEncoder.signalEndOfInputStream()
    }

    drainEncoderPostLollipop(endOfStream)
  }

  private fun drainEncoderPostLollipop(endOfStream: Boolean) {
    encodeLoop@ while (true) {
      val outputBufferIndex = videoEncoder.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_USEC.toLong())
      when {
        outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
          // no inputSurface available yet
          if (!endOfStream) {
            break@encodeLoop // out of while
          } else {
            if (VERBOSE) Log.d("TAG", "no inputSurface available, spinning to await EOS")
          }
        }
        outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
          startMuxer()
        }
        outputBufferIndex > 0 -> {
          if (encodeVideoData(
                  videoEncoder.getOutputBuffer(outputBufferIndex)!!,
                  outputBufferIndex,
                  endOfStream
              )
          ) break@encodeLoop
        }
        else -> Log.w("TAG", "unexpected result from videoEncoder.dequeueOutputBuffer: $outputBufferIndex")
      }
    }
  }

  private fun startMuxer() {
    // should happen before receiving buffers, and should only happen once
    if (muxerStarted) {
      throw RuntimeException("format changed twice")
    }
    val newFormat = videoEncoder.outputFormat
    Log.d("TAG", "videoEncoder inputSurface format changed: $newFormat")

    trackIndex = muxer.addTrack(newFormat)
    muxer.start()
    muxerStarted = true
  }

  private fun encodeVideoData(encodedData: ByteBuffer, outputBufferIndex: Int, endOfStream: Boolean): Boolean {
    if (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
      // The codec config data was pulled out and fed to the muxer when we got
      // the INFO_OUTPUT_FORMAT_CHANGED status. Ignore it.
      if (VERBOSE) Log.d("TAG", "ignoring BUFFER_FLAG_CODEC_CONFIG")
      videoBufferInfo.size = 0
    }

    if (videoBufferInfo.size != 0) {
      if (!muxerStarted) {
        throw RuntimeException("muxer hasn't started")
      }

      // adjust the ByteBuffer values to match BufferInfo
      encodedData.position(videoBufferInfo.offset)
      encodedData.limit(videoBufferInfo.offset + videoBufferInfo.size)
      videoBufferInfo.presentationTimeUs = fakePts
      // we save ms length of the video before buffer is disposed:
      if (endOfStream) videoLengthInMs = videoBufferInfo.presentationTimeUs
      fakePts += 1000000L / framesPerSecond

      muxer.writeSampleData(trackIndex, encodedData, videoBufferInfo)
      if (VERBOSE) Log.d("TAG", "sent ${videoBufferInfo.size} bytes to muxer")
    }

    videoEncoder.releaseOutputBuffer(outputBufferIndex, false)

    if (videoBufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
      if (!endOfStream) {
        Log.w("TAG", "reached endRecording of stream unexpectedly")
      } else {
        if (VERBOSE) Log.d("TAG", "endRecording of stream reached")
      }
      return true // out of while
    }
    return false
  }

  /**
   * Releases videoEncoder resources. May be called after partial / failed initialization.
   */
  override fun close() {
    if (VERBOSE) Log.d("TAG", "releasing videoEncoder objects")
    videoEncoder.stop()
    videoEncoder.release()
    inputSurface.release()
    muxer.stop()
    muxer.release()
  }
}