#import <Foundation/Foundation.h>
#import <CoreMIDI/CoreMIDI.h>

NS_ASSUME_NONNULL_BEGIN

// Work around a bug in the declaration of MIDIPacketListAdd(). The return value should be _Nullable,
// so Swift code can compare it to nil.
// This function just calls MIDIPacketListAdd() and does nothing extra.
extern MIDIPacket * _Nullable SMWorkaroundMIDIPacketListAdd(MIDIPacketList *pktlist, ByteCount listSize, MIDIPacket *curPacket, MIDITimeStamp time, ByteCount nData, const Byte *data);

NS_ASSUME_NONNULL_END
