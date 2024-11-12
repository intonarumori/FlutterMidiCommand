package com.invisiblewrench.fluttermidicommand

import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiReceiver
import io.flutter.plugin.common.MethodChannel.Result

abstract class Device {
    var id:String
    var type:String
    lateinit var midiDevice: MidiDevice
    protected var receiver:MidiReceiver? = null
    protected var setupStreamHandler: FMCStreamHandler? = null
    public open var isRawMidiDataReceivingEnabled: Boolean

    constructor(id: String, type: String) {
        this.id = id
        this.type = type
        this.isRawMidiDataReceivingEnabled = false
    }

    abstract fun connectWithStreamHandler(streamHandler: FMCStreamHandler, connectResult:Result?)

    abstract fun send(data: ByteArray, timestamp: Long?)

    abstract fun close()

    companion object {
        fun deviceIdForInfo(info: MidiDeviceInfo): String {
            var isBluetoothDevice = info.type == MidiDeviceInfo.TYPE_BLUETOOTH
            var deviceId: String = if (isBluetoothDevice) info.properties.get(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE).toString() else info.id.toString()
            return deviceId
        }
    }
}
