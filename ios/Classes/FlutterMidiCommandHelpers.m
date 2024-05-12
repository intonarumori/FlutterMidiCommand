
#import "FlutterMidiCommandHelpers.h"

MIDIPacket * _Nullable SMWorkaroundMIDIPacketListAdd(MIDIPacketList *pktlist, ByteCount listSize, MIDIPacket *curPacket, MIDITimeStamp time, ByteCount nData, const Byte *data)
{
	// MIDIPacketListAdd isn't declared as returning a _Nullable pointer, but it should be
	return MIDIPacketListAdd(pktlist, listSize, curPacket, time, nData, data);
}
