package com.invisiblewrench.fluttermidicommand

import android.content.pm.ServiceInfo
import android.media.midi.*
import android.os.Handler
import android.util.Log
import io.flutter.plugin.common.MethodChannel.Result

class ConnectedDevice : Device {
    var inputPort: MidiInputPort? = null
    var outputPort: MidiOutputPort? = null

    override var isRawMidiDataReceivingEnabled: Boolean = false
        set(value) {
            if (this.receiver != null) {
                (this.receiver as RXReceiver)?.isRawMidiDataReceivingEnabled = value
            }
            field = value
        }

    private var isOwnVirtualDevice = false;

    constructor(device:MidiDevice, setupStreamHandler: FMCStreamHandler) : super(deviceIdForInfo(device.info), device.info.type.toString()) {
        this.midiDevice = device
        this.setupStreamHandler = setupStreamHandler
    }

    override fun connectWithStreamHandler(streamHandler: FMCStreamHandler, connectResult:Result?) {
        Log.d("FlutterMIDICommand","connectWithHandler")

        this.midiDevice.info?.let {

            Log.d("FlutterMIDICommand","inputPorts ${it.inputPortCount} outputPorts ${it.outputPortCount}")

            this.receiver = RXReceiver(streamHandler, this.midiDevice)
            (this.receiver as RXReceiver).isRawMidiDataReceivingEnabled = isRawMidiDataReceivingEnabled
//
//        it.ports.forEach {
//          Log.d("FlutterMIDICommand", "${it.name} ${it.type} ${it.portNumber}")
//        }

            var serviceInfo = it.properties.getParcelable<ServiceInfo>("service_info")
            if (serviceInfo?.name == "com.invisiblewrench.fluttermidicommand.VirtualDeviceService") {
                Log.d("FlutterMIDICommand", "Own virtual")
                isOwnVirtualDevice = true
            } else {
                if (it.inputPortCount > 0) {
                    Log.d("FlutterMIDICommand", "Open input port: 0")
                    this.inputPort = this.midiDevice.openInputPort(0)
                }
            }
            if (it.outputPortCount > 0) {
                Log.d("FlutterMIDICommand", "Open output port: 0")
                this.outputPort = this.midiDevice.openOutputPort(0)
                this.outputPort?.connect(this.receiver)
            }
        }

        Handler().postDelayed({
            connectResult?.success(null)
            setupStreamHandler?.send("deviceConnected")
        }, 2500)
    }

    override fun selectInputPort(portNumber: Int) {
        if (this.inputPort != null) {
            this.inputPort?.close();
            this.inputPort = null;
        }
        this.midiDevice.info?.let {
            if (it.inputPortCount > 0 && portNumber < it.inputPortCount) {
                Log.d("FlutterMIDICommand", "Open input port: $portNumber")
                this.inputPort = this.midiDevice.openInputPort(portNumber)
            }
        }
    }

    override fun selectOutputPort(portNumber: Int) {
        if (this.outputPort != null) {
            this.outputPort?.disconnect(receiver)
            this.outputPort?.close();
            this.outputPort = null;
        }
        this.midiDevice.info?.let {
            if (it.outputPortCount > 0 && portNumber < it.outputPortCount) {
                Log.d("FlutterMIDICommand", "Open output port: $portNumber")
                this.outputPort = this.midiDevice.openOutputPort(portNumber)
                this.outputPort?.connect(this.receiver)
            }
        }
    }

//    fun openPorts(ports: List<Port>) {
//      this.midiDevice.info?.let { deviceInfo ->
//        Log.d("FlutterMIDICommand","inputPorts ${deviceInfo.inputPortCount} outputPorts ${deviceInfo.outputPortCount}")
//
//        ports.forEach { port ->
//          Log.d("FlutterMIDICommand", "Open port ${port.type} ${port.id}")
//          when (port.type) {
//            "MidiPortType.IN" -> {
//              if (deviceInfo.inputPortCount > port.id) {
//                Log.d("FlutterMIDICommand", "Open input port ${port.id}")
//                this.inputPort = this.midiDevice.openInputPort(port.id)
//              }
//            }
//            "MidiPortType.OUT" -> {
//              if (deviceInfo.outputPortCount > port.id) {
//                Log.d("FlutterMIDICommand", "Open output port ${port.id}")
//                this.outputPort = this.midiDevice.openOutputPort(port.id)
//                this.outputPort?.connect(receiver)
//              }
//            }
//            else -> {
//              Log.d("FlutterMIDICommand", "Unknown MIDI port type ${port.type}. Not opening.")
//            }
//          }
//        }
//      }
//    }

    override fun send(data: ByteArray, timestamp: Long?) {

        if(isOwnVirtualDevice) {
            Log.d("FlutterMIDICommand", "Send to recevier")
            if (timestamp == null)
                this.receiver?.send(data, 0, data.size)
            else
                this.receiver?.send(data, 0, data.size, timestamp)

        } else {
//        Log.d("FlutterMIDICommand", "Send to input port ${this.inputPort}")
            this.inputPort?.send(data, 0, data.count(), if (timestamp is Long) timestamp else 0)
        }
    }

    override fun close() {
        Log.d("FlutterMIDICommand", "Flush input port ${this.inputPort}")
        this.inputPort?.flush()
        Log.d("FlutterMIDICommand", "Close input port ${this.inputPort}")
        this.inputPort?.close()
        Log.d("FlutterMIDICommand", "Close output port ${this.outputPort}")
        this.outputPort?.close()
        Log.d("FlutterMIDICommand", "Disconnect receiver ${this.receiver}")
        this.outputPort?.disconnect(this.receiver)
        this.receiver = null
        Log.d("FlutterMIDICommand", "Close device ${this.midiDevice}")
        this.midiDevice.close()

        setupStreamHandler?.send("deviceDisconnected")
    }

    class RXReceiver(stream: FMCStreamHandler, device: MidiDevice) : MidiReceiver() {
        val stream = stream
        var isBluetoothDevice = device.info.type == MidiDeviceInfo.TYPE_BLUETOOTH
        val deviceInfo = mapOf("id" to if(isBluetoothDevice) device.info.properties.get(MidiDeviceInfo.PROPERTY_BLUETOOTH_DEVICE).toString() else device.info.id.toString(), "name" to device.info.properties.getString(MidiDeviceInfo.PROPERTY_NAME), "type" to if(isBluetoothDevice) "BLE" else "native")
        var isRawMidiDataReceivingEnabled: Boolean = false

        // MIDI parsing
        enum class PARSER_STATE
        {
            HEADER,
            PARAMS,
            SYSEX,
        }

        var parserState = PARSER_STATE.HEADER

        var sysExBuffer = mutableListOf<Byte>()
        var midiBuffer = mutableListOf<Byte>()
        var midiPacketLength:Int = 0
        var statusByte:Byte = 0

        override fun onSend(msg: ByteArray?, offset: Int, count: Int, timestamp: Long) {
            msg?.also {
                var data = it.slice(IntRange(offset, offset + count - 1))
//        Log.d("FlutterMIDICommand", "data sliced $data offset $offset count $count")

                if (data.size > 0) {
                    if (isRawMidiDataReceivingEnabled) {
                        midiBuffer.clear()
                        for (i in 0 until data.size) {
                            midiBuffer.add(data[i])
                        }
                        stream.send( mapOf("data" to midiBuffer.toList(), "timestamp" to timestamp, "device" to deviceInfo))
                        return
                    }

                    for (i in 0 until data.size) {
                        var midiByte: Byte = data[i]
                        var midiInt = midiByte.toInt() and 0xFF

//          Log.d("FlutterMIDICommand", "parserState $parserState byte $midiByte")

                        when (parserState) {
                            PARSER_STATE.HEADER -> {
                                if (midiInt == 0xF0) {
                                    parserState = PARSER_STATE.SYSEX
                                    sysExBuffer.clear()
                                    sysExBuffer.add(midiByte)
                                } else if (midiInt and 0x80 == 0x80) {
                                    // some kind of midi msg
                                    statusByte = midiByte
                                    midiPacketLength = lengthOfMessageType(midiInt)
//                Log.d("FlutterMIDICommand", "expected length $midiPacketLength")
                                    midiBuffer.clear()
                                    midiBuffer.add(midiByte)
                                    parserState = PARSER_STATE.PARAMS
                                    finalizeMessageIfComplete(timestamp)
                                } else {
                                    // in header state but no status byte, do running status
                                    midiBuffer.clear()
                                    midiBuffer.add(statusByte)
                                    midiBuffer.add(midiByte)
                                    parserState = PARSER_STATE.PARAMS
                                    finalizeMessageIfComplete(timestamp)
                                }
                            }

                            PARSER_STATE.SYSEX -> {
                                if (midiInt == 0xF0) {
                                    // Android can skip SysEx end bytes, when more sysex messages are coming in succession.
                                    // in an attempt to save the situation, add an end byte to the current buffer and start a new one.
                                    sysExBuffer.add(0xF7.toByte())
//                Log.d("FlutterMIDICommand", "sysex force finalized $sysExBuffer")
                                    stream.send(
                                        mapOf(
                                            "data" to sysExBuffer.toList(),
                                            "timestamp" to timestamp,
                                            "device" to deviceInfo
                                        )
                                    )
                                    sysExBuffer.clear();
                                }
                                sysExBuffer.add(midiByte)
                                if (midiInt == 0xF7) {
                                    // Sysex complete
//                Log.d("FlutterMIDICommand", "sysex complete $sysExBuffer")
                                    stream.send(
                                        mapOf(
                                            "data" to sysExBuffer.toList(),
                                            "timestamp" to timestamp,
                                            "device" to deviceInfo
                                        )
                                    )
                                    parserState = PARSER_STATE.HEADER
                                }
                            }

                            PARSER_STATE.PARAMS -> {
                                midiBuffer.add(midiByte)
                                finalizeMessageIfComplete(timestamp)
                            }
                        }
                    }
                }
            }
        }

        fun finalizeMessageIfComplete(timestamp: Long) {
            if (midiBuffer.size == midiPacketLength) {
//        Log.d("FlutterMIDICommand", "status complete $midiBuffer")
                stream.send( mapOf("data" to midiBuffer.toList(), "timestamp" to timestamp, "device" to deviceInfo))
                parserState = PARSER_STATE.HEADER
            }
        }

        fun lengthOfMessageType(type:Int): Int {
            var midiType:Int = type and 0xF0

            when (type) {
                0xF6, 0xF8, 0xFA, 0xFB, 0xFC, 0xFF, 0xFE -> return 1
                0xF1, 0xF3 -> return 2
                0xF2 -> return 3
            }

            when (midiType) {
                0xC0, 0xD0 -> return 2
                0x80, 0x90, 0xA0, 0xB0, 0xE0 -> return 3
            }
            return 0
        }
    }

}
