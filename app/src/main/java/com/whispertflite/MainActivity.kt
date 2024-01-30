package com.whispertflite

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.whispertflite.asr.IRecorderListener
import com.whispertflite.asr.IWhisperListener
import com.whispertflite.asr.Recorder
import com.whispertflite.asr.Whisper
import com.whispertflite.utils.WaveUtil
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import com.whispertflite.R


class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private var tvStatus: TextView? = null
    private var tvResult: TextView? = null
    private var fabCopy: FloatingActionButton? = null
    private var mWhisper: Whisper? = null
    private var mRecorder: Recorder? = null
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val waveFileName = "MicInput.wav"
        val handler = Handler(Looper.getMainLooper())
        tvStatus = findViewById(R.id.tvStatus)
        tvResult = findViewById(R.id.tvResult)

        // Implementation of record button functionality
        val btnMicRec = findViewById<Button>(R.id.btnRecord)
        btnMicRec.setOnClickListener { v: View? ->
            if (mRecorder != null && mRecorder!!.isInProgress) {
                Log.d(TAG, "Recording is in progress... stopping...")
                stopRecording()
            } else {
                Log.d(TAG, "Start recording...")
                startRecording()
            }
        }

        // Implementation of transcribe button functionality
        val btnTranscb = findViewById<Button>(R.id.btnTranscb)
        btnTranscb.setOnClickListener { v: View? ->
            if (mRecorder != null && mRecorder!!.isInProgress) {
                Log.d(TAG, "Recording is in progress... stopping...")
                stopRecording()
            }
            if (mWhisper != null && mWhisper!!.isInProgress) {
                Log.d(TAG, "Whisper is already in progress...!")
                stopTranscription()
            } else {
                Log.d(TAG, "Start transcription...")
                val waveFilePath = getFilePath(waveFileName)
                startTranscription(waveFilePath)
            }
        }

        // Call the method to copy specific file types from assets to data folder
        val extensionsToCopy = arrayOf("pcm", "bin", "wav", "tflite")
        copyAssetsWithExtensionsToDataFolder(this, extensionsToCopy)
        val modelPath: String
        val vocabPath: String
        val useMultilingual = false // TODO: change multilingual flag as per model used
        if (useMultilingual) {
            // Multilingual model and vocab
            modelPath = getFilePath("whisper-tiny.tflite")
            vocabPath = getFilePath("filters_vocab_multilingual.bin")
        } else {
            // English-only model and vocab
            modelPath = getFilePath("whisper-tiny-en.tflite")
            vocabPath = getFilePath("filters_vocab_en.bin")
        }
        mWhisper = Whisper(this)
        mWhisper!!.loadModel(modelPath, vocabPath, useMultilingual)
        mWhisper!!.setListener(object : IWhisperListener {
            override fun onUpdateReceived(message: String) {
                Log.d(TAG, "Update is received, Message: $message")
                handler.post { tvStatus?.setText(message) }
                if (message == Whisper.MSG_PROCESSING) {
                    handler.post { tvResult?.setText("") }
                } else if (message == Whisper.MSG_FILE_NOT_FOUND) {
                    // write code as per need to handled this error
                    Log.d(TAG, "File not found error...!")
                }
            }

            override fun onResultReceived(result: String) {
                Log.d(TAG, "Result: $result")
                handler.post { tvResult?.append(result) }
            }
        })
        mRecorder = Recorder(this)
        mRecorder!!.setListener(object : IRecorderListener {
            override fun onUpdateReceived(message: String) {
                Log.d(TAG, "Update is received, Message: $message")
                handler.post { tvStatus?.setText(message) }
                if (message == Recorder.MSG_RECORDING) {
                    handler.post { tvResult?.setText("") }
                    handler.post { btnMicRec.text = Recorder.ACTION_STOP }
                } else if (message == Recorder.MSG_RECORDING_DONE) {
                    handler.post { btnMicRec.text = Recorder.ACTION_RECORD }
                }
            }

            override fun onDataReceived(samples: FloatArray) {
                //mWhisper.writeBuffer(samples);
            }
        })

        // Assume this Activity is the current activity, check record permission
        checkRecordPermission()

    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun checkRecordPermission() {
        val permission = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
        if (permission == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted")
        } else {
            Log.d(TAG, "Requesting record permission")
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 0)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) Log.d(
            TAG,
            "Record permission is granted"
        ) else Log.d(TAG, "Record permission is not granted")
    }

    // Recording calls
    @RequiresApi(Build.VERSION_CODES.M)
    private fun startRecording() {
        checkRecordPermission()
        val waveFilePath = getFilePath(WaveUtil.RECORDING_FILE)
        mRecorder!!.setFilePath(waveFilePath)
        mRecorder!!.start()
    }

    private fun stopRecording() {
        mRecorder!!.stop()
    }

    // Transcription calls
    private fun startTranscription(waveFilePath: String) {
        mWhisper!!.setFilePath(waveFilePath)
        mWhisper!!.setAction(Whisper.ACTION_TRANSCRIBE)
        mWhisper!!.start()
    }

    private fun stopTranscription() {
        mWhisper!!.stop()
    }

    // Returns file path from data folder
    private fun getFilePath(assetName: String?): String {
        val outfile = File(filesDir, assetName)
        if (!outfile.exists()) {
            Log.d(TAG, "File not found - " + outfile.absolutePath)
        }
        Log.d(TAG, "Returned asset path: " + outfile.absolutePath)
        return outfile.absolutePath
    }

    companion object {
        // Copy assets to data folder
        private fun copyAssetsWithExtensionsToDataFolder(
            context: Context,
            extensions: Array<String>
        ) {
            val assetManager = context.assets
            try {
                // Specify the destination directory in the app's data folder
                val destFolder = context.filesDir.absolutePath
                for (extension in extensions) {
                    // List all files in the assets folder with the specified extension
                    val assetFiles = assetManager.list("")
                    for (assetFileName in assetFiles!!) {
                        if (assetFileName.endsWith(".$extension")) {
                            val outFile = File(destFolder, assetFileName)
                            if (outFile.exists()) continue
                            val inputStream = assetManager.open(assetFileName)
                            val outputStream: OutputStream = FileOutputStream(outFile)

                            // Copy the file from assets to the data folder
                            val buffer = ByteArray(1024)
                            var read: Int
                            while (inputStream.read(buffer).also { read = it } != -1) {
                                outputStream.write(buffer, 0, read)
                            }
                            inputStream.close()
                            outputStream.flush()
                            outputStream.close()
                        }
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}