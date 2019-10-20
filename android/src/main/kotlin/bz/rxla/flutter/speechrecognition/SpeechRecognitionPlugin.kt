package bz.rxla.flutter.speechrecognition

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry.Registrar
import java.util.*

class SpeechRecognitionPlugin private constructor(private val context: Context, private val speechChannel: MethodChannel) : MethodCallHandler, RecognitionListener {

    private val speech: SpeechRecognizer
    private var transcription = ""
    private val recognizerIntent: Intent

    init {
        this.speechChannel.setMethodCallHandler(this)

        speech = SpeechRecognizer.createSpeechRecognizer(context)
        speech.setRecognitionListener(this)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "speech.activate" -> {
                // FIXME => Dummy activation verification : we assume that speech recognition permission
                // is declared in the manifest and accepted during installation ( AndroidSDK 21- )
                val locale = context.resources.configuration.locale
                Log.d(LOG_TAG, "Current Locale : $locale")
                speechChannel.invokeMethod("speech.onCurrentLocale", locale.toString())
                result.success(true)
            }
            "speech.listen" -> {
                recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, getLocale(call.arguments.toString()))
                speech.startListening(recognizerIntent)
                result.success(true)
            }
            "speech.cancel" -> {
                speech.cancel()
                result.success(false)
            }
            "speech.stop" -> {
                speech.stopListening()
                result.success(true)
            }
            "speech.destroy" -> {
                speech.cancel()
                speech.destroy()
                result.success(true)
            }
            else -> result.notImplemented()
        }
    }

    private fun getLocale(code: String): Locale {
        val localeParts = code.split("_".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        return Locale(localeParts[0], localeParts[1])
    }

    override fun onReadyForSpeech(params: Bundle) {
        Log.d(LOG_TAG, "onReadyForSpeech")
        speechChannel.invokeMethod("speech.onSpeechAvailability", true)
    }

    override fun onBeginningOfSpeech() {
        Log.d(LOG_TAG, "onRecognitionStarted")
        transcription = ""
        speechChannel.invokeMethod("speech.onRecognitionStarted", null)
    }

    override fun onRmsChanged(rmsdB: Float) {
        Log.d(LOG_TAG, "onRmsChanged : $rmsdB")
    }

    override fun onBufferReceived(buffer: ByteArray) {
        Log.d(LOG_TAG, "onBufferReceived")
    }

    override fun onEndOfSpeech() {
        Log.d(LOG_TAG, "onEndOfSpeech")
        speechChannel.invokeMethod("speech.onRecognitionComplete", transcription)
    }

    override fun onError(error: Int) {
        Log.d(LOG_TAG, "onError : $error")
        speechChannel.invokeMethod("speech.onSpeechAvailability", false)
        speechChannel.invokeMethod("speech.onError", error)
    }

    override fun onPartialResults(partialResults: Bundle) {
        Log.d(LOG_TAG, "onPartialResults...")
        val matches = partialResults
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches != null) {
            transcription = matches[0]
        }
        sendTranscription(false)
    }

    override fun onEvent(eventType: Int, params: Bundle) {
        Log.d(LOG_TAG, "onEvent : $eventType")
    }

    override fun onResults(results: Bundle) {
        Log.d(LOG_TAG, "onResults...")
        val matches = results
                .getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (matches != null) {
            transcription = matches[0]
            Log.d(LOG_TAG, "onResults -> $transcription")
            sendTranscription(true)
        }
        sendTranscription(false)
    }

    private fun sendTranscription(isFinal: Boolean) {
        speechChannel.invokeMethod(if (isFinal) "speech.onRecognitionComplete" else "speech.onSpeech", transcription)
    }

    companion object {

        private const val LOG_TAG = "SpeechRecognitionPlugin"

        /**
         * Plugin registration.
         */
        @JvmStatic
        fun registerWith(registrar: Registrar) {
            val channel = MethodChannel(registrar.messenger(), "speech_recognition")
            channel.setMethodCallHandler(SpeechRecognitionPlugin(registrar.activeContext(), channel))
        }
    }
}
