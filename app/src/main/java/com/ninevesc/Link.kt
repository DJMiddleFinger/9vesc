package com.ninevesc

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** BLE connection to the VESC plus the settings the 9VESC script last reported. */
class Link(app: Application) : AndroidViewModel(app) {
    val transport = VescBleTransport(app)
    val state = MutableStateFlow<G30State?>(null)
    val message = MutableStateFlow<String?>(null)

    private val decoder = PacketDecoder()
    private var replyWatch: Job? = null

    init {
        viewModelScope.launch {
            transport.incoming.collect { chunk ->
                decoder.feed(chunk).forEach { p -> G30.parse(p)?.let(::onReply) }
            }
        }
        viewModelScope.launch {
            transport.state.collect { s ->
                if (s is VescBleTransport.State.Connected) {
                    decoder.reset()
                    send(G30.read())
                } else {
                    state.value = null
                    if (s is VescBleTransport.State.Failed) message.value = s.reason
                }
            }
        }
    }

    private fun onReply(s: G30State) {
        replyWatch?.cancel()
        state.value = s
        message.value = when {
            s.status == 1 -> "Stop the scooter to change settings."
            s.status == 2 -> "The controller didn't accept that. Is the script up to date?"
            s.vars.size < 30 || s.confs.size < 20 -> "The script on the controller is older than this app. Upload lisp/g30_dash_9vesc.lisp again."
            else -> null
        }
    }

    fun connect(address: String) = viewModelScope.launch {
        message.value = null
        transport.connect(address)
    }

    fun disconnect() = transport.disconnect()

    fun set(f: Field, raw: Double) = send(G30.set(f, raw))
    fun storeConf() = send(G30.storeConf())

    private fun send(payload: ByteArray) {
        replyWatch?.cancel()
        replyWatch = viewModelScope.launch {
            transport.send(VescPacket.encode(payload))
            delay(3000)
            message.value = "No reply from the 9VESC script. Is it uploaded to the controller and running?"
        }
    }

    override fun onCleared() = transport.disconnect()
}
