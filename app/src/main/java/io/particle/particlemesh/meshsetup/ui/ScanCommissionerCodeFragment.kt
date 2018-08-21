package io.particle.particlemesh.meshsetup.ui

import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.bluetooth.le.ScanResult
import android.content.Context
import androidx.navigation.fragment.findNavController
import io.particle.particlemesh.common.QATool
import io.particle.particlemesh.common.Result
import io.particle.particlemesh.common.truthy
import io.particle.particlemesh.meshsetup.connection.RequestSender
import io.particle.particlemesh.meshsetup.ui.utils.buildMatchingDeviceScanner
import io.particle.particlemesh.meshsetup.utils.safeToast
import io.particle.sdk.app.R
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.launch
import mu.KotlinLogging


class ScanCommissionerCodeFragment :  ScanIntroBaseFragment() {

    override val layoutId: Int = R.layout.fragment_scan_commissioner_code

    private lateinit var scannerLD: LiveData<List<ScanResult>?>

    private val log = KotlinLogging.logger {}

    override fun onBarcodeUpdated(barcodeData: BarcodeData?) {
        log.info { "onBarcodeUpdated(COMMISH): $barcodeData" }

        setupController.setCommissionerBarcode(barcodeData!!)

        scannerLD = buildMatchingDeviceScanner(this, barcodeData.serialNumber)
        scannerLD.observe(this, Observer { onMatchingDeviceFound(it) })
    }

    private fun onMatchingDeviceFound(results: List<ScanResult>?) {
        if (!results.truthy()) {
            QATool.illegalState("Results list was empty (but this should never happen!)")
            return
        }

        scannerLD.removeObservers(this)

        val device = results!![0].device
        val commishAddress = device.address
        val ctx = requireActivity().applicationContext

        val mobileSecret = setupController.otherParams.value!!.commissionerBarcode!!.mobileSecret

        launch(UI) {
            val commissioner = setupController.connectToCommissioner(commishAddress, mobileSecret)
            if (commissioner == null) {
                ctx.safeToast("Unable to connect to device ${device.name}")
            } else {
                ctx.safeToast("Connected to ${device.name}")
                launch { onCommissionerConnected(commissioner, ctx) }
            }
        }
    }

    private suspend fun onCommissionerConnected(commissioner: RequestSender, ctx: Context) {
        val networkInfoReply = commissioner.sendGetNetworkInfo()
        val targetNetwork = setupController.otherParams.value!!.networkInfo!!
        val commissionerNetwork = when (networkInfoReply) {
            is Result.Error,
            is Result.Absent -> {
                ctx.safeToast("Could not get network info from commissioner")
                return
            }
            is Result.Present -> networkInfoReply.value.network
        }
        if (targetNetwork.extPanId != commissionerNetwork.extPanId) {
            log.warn { "Selected device is not on mesh network ${targetNetwork.name}" }
            ctx.safeToast("Selected device is not on mesh network ${targetNetwork.name}")
            return
        }

        findNavController().navigate(
                R.id.action_scanCommissionerCodeFragment_to_enterNetworkPasswordFragment
        )
    }

}