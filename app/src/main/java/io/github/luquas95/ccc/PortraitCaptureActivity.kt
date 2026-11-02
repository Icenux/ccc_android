package io.github.luquas95.ccc

import com.journeyapps.barcodescanner.CaptureActivity
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * The QR scanner of the pairing screen. The library's own CaptureActivity is declared
 * `sensorLandscape`, which turns the phone's screen; this subclass only exists so the manifest
 * can pin it to portrait (see AndroidManifest.xml).
 *
 * It also turns on continuous focus: the library's default only refocuses now and then, so on
 * some phones the code stayed blurred until the user tapped the screen. With continuous focus the
 * camera is sharp by itself and the code is read as soon as it is in view.
 */
class PortraitCaptureActivity : CaptureActivity() {
    override fun initializeContent(): DecoratedBarcodeView {
        val view = super.initializeContent()
        val camera = view.barcodeView
        camera.cameraSettings = camera.cameraSettings.apply {
            isAutoFocusEnabled = true
            isContinuousFocusEnabled = true
        }
        return view
    }
}
