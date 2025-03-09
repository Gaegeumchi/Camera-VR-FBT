package com.gaegeumchi.fbt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.wifi.WifiManager
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var autoDetectButton: Button
    private lateinit var inputIpButton: Button
    private lateinit var ipAddressEditText: EditText
    private lateinit var connectButton: Button
    private lateinit var connectionStatusTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var scanningTextView: TextView
    private lateinit var detectFailedTextView: TextView
    private lateinit var textureView: TextureView
    private lateinit var cameraSwitchButton: Button
    private lateinit var imageReader: ImageReader

    private var cameraId: String = "0"
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var serverSocket: Socket? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        autoDetectButton = findViewById(R.id.autoDetectButton)
        inputIpButton = findViewById(R.id.inputIpButton)
        ipAddressEditText = findViewById(R.id.ipAddressEditText)
        connectButton = findViewById(R.id.connectButton)
        connectionStatusTextView = findViewById(R.id.connectionStatusTextView)
        progressBar = findViewById(R.id.progressBar)
        scanningTextView = findViewById(R.id.scanningTextView)
        detectFailedTextView = findViewById(R.id.detectFailedTextView)
        textureView = findViewById(R.id.textureView)
        cameraSwitchButton = findViewById(R.id.cameraSwitchButton)

        autoDetectButton.setOnClickListener { autoDetectIP() }
        inputIpButton.setOnClickListener { showManualInput() }
        connectButton.setOnClickListener { connectToServer(ipAddressEditText.text.toString()) }
        cameraSwitchButton.setOnClickListener { switchCamera() }
    }

    private fun autoDetectIP() {
        progressBar.visibility = View.VISIBLE
        scanningTextView.visibility = View.VISIBLE
        detectFailedTextView.visibility = View.GONE

        executor.execute {
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val deviceIP = formatIPAddress(wifiManager.dhcpInfo.ipAddress)
            val subnet = deviceIP.substringBeforeLast(".")

            for (i in 1..254) {
                val targetIP = "$subnet.$i"
                if (targetIP == deviceIP) continue
                if (isPortOpen(targetIP, 1818)) {
                    if (tryConnect(targetIP, 1818)) {
                        runOnUiThread {
                            connectionStatusTextView.text = "Connected to $targetIP"
                            progressBar.visibility = View.GONE
                            scanningTextView.visibility = View.GONE
                            detectFailedTextView.visibility = View.GONE
                            startCameraStreaming()
                        }
                        return@execute
                    }
                }
            }
            runOnUiThread {
                progressBar.visibility = View.GONE
                scanningTextView.visibility = View.GONE
                detectFailedTextView.visibility = View.VISIBLE
            }
        }
    }

    private fun isPortOpen(ip: String, port: Int, timeout: Int = 200): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeout)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun tryConnect(host: String, port: Int): Boolean {
        return try {
            val socket = Socket(host, port)
            val output = DataOutputStream(socket.getOutputStream())
            output.write("connect_signal\n".toByteArray())
            output.flush()

            val response = socket.getInputStream().bufferedReader().readLine()
            if (response == "fbt from gaegeumchi") {
                serverSocket = socket
                true
            } else false
        } catch (e: Exception) {
            false
        }
    }



    private fun formatIPAddress(ip: Int): String {
        return "${ip and 0xFF}.${(ip shr 8) and 0xFF}.${(ip shr 16) and 0xFF}.${(ip shr 24) and 0xFF}"
    }


    private fun showManualInput() {
        ipAddressEditText.visibility = View.VISIBLE
        connectButton.visibility = View.VISIBLE
    }

    private fun connectToServer(ip: String) {
        progressBar.visibility = View.VISIBLE
        executor.execute {
            if (tryConnect(ip, 1818)) {
                runOnUiThread {
                    connectionStatusTextView.text = "Connected to $ip"
                    progressBar.visibility = View.GONE
                    scanningTextView.visibility = View.GONE
                    detectFailedTextView.visibility = View.GONE
                    startCameraStreaming()
                }
            } else {
                runOnUiThread {
                    connectionStatusTextView.text = "Connection Failed"
                    progressBar.visibility = View.GONE
                }
            }
        }
    }

    private fun openCamera() {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
                return
            }

            setupImageReader() // 🔹 ImageReader 초기화 추가

            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCameraPreview()
                }
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }


    private fun setupImageReader() {
        imageReader = ImageReader.newInstance(640, 480, android.graphics.ImageFormat.JPEG, 2)
        imageReader.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            image?.let {
                val buffer: ByteBuffer = it.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                it.close()
                sendByteArrayToServer(bytes)  // 🔹 여기서 서버로 자동 전송
            }
        }, null)
    }


    private fun sendByteArrayToServer(byteArray: ByteArray) {
        try {
            val outputStream = serverSocket!!.getOutputStream()
            val dataSize = byteArray.size
            val dataSizeBytes = ByteArray(4)
            dataSizeBytes[0] = (dataSize shr 24 and 0xFF).toByte()
            dataSizeBytes[1] = (dataSize shr 16 and 0xFF).toByte()
            dataSizeBytes[2] = (dataSize shr 8 and 0xFF).toByte()
            dataSizeBytes[3] = (dataSize and 0xFF).toByte()
            outputStream.write(dataSizeBytes)
            outputStream.write(byteArray)
            outputStream.flush()

            Log.d("CameraStreaming", "이미지 전송 완료: ${byteArray.size} bytes")
        } catch (e: Exception) {
            Log.e("CameraStreaming", "Error sending frame data: ${e.message}")
        }
    }


    private fun createCameraPreview() {
        try {
            val texture = textureView.surfaceTexture
            texture?.setDefaultBufferSize(640, 480)
            val surface = Surface(texture)

            val captureRequestBuilder = cameraDevice!!.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            cameraDevice!!.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    if (cameraDevice == null) return
                    cameraCaptureSession = session
                    try {
                        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                        cameraCaptureSession!!.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    } catch (e: CameraAccessException) {
                        e.printStackTrace()
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "카메라 설정 실패", Toast.LENGTH_SHORT).show()
                    }
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }





    private fun switchCamera() {
        cameraId = if (cameraId == "0") "1" else "0"
        cameraDevice?.close()
        openCamera()
    }

    private fun startCameraStreaming() {
        runOnUiThread {
            textureView.visibility = View.VISIBLE
            cameraSwitchButton.visibility = View.VISIBLE
        }
        if (textureView.isAvailable) {
            openCamera()
        } else {
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                    openCamera()
                }

                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = false
                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
            }
        }
    }
}


