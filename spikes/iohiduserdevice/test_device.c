#include <CoreFoundation/CoreFoundation.h>
#include <IOKit/IOKitLib.h>
#include <IOKit/hidsystem/IOHIDUserDevice.h>
#include <mach/mach_time.h>
#include <stdio.h>
#include <unistd.h>

// Standard USB HID Gamepad Descriptor
// 4 axes (X, Y, Z, Rz) 8-bit unsigned (0..255, center 128)
// 16 buttons 1-bit each (2 bytes)
// Total report size = 6 bytes
static const uint8_t gamepad_hid_descriptor[] = {
    0x05, 0x01,        // Usage Page (Generic Desktop Ctrls)
    0x09, 0x05,        // Usage (Game Pad)
    0xA1, 0x01,        // Collection (Application)
    0xA1, 0x00,        //   Collection (Physical)
    0x05, 0x01,        //     Usage Page (Generic Desktop Ctrls)
    0x09, 0x30,        //     Usage (X)
    0x09, 0x31,        //     Usage (Y)
    0x09, 0x32,        //     Usage (Z)
    0x09, 0x35,        //     Usage (Rz)
    0x15, 0x00,        //     Logical Minimum (0)
    0x26, 0xFF, 0x00,  //     Logical Maximum (255)
    0x75, 0x08,        //     Report Size (8)
    0x95, 0x04,        //     Report Count (4)
    0x81, 0x02,        //     Input (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
    0x05, 0x09,        //     Usage Page (Button)
    0x19, 0x01,        //     Usage Minimum (0x01)
    0x29, 0x10,        //     Usage Maximum (0x10)
    0x15, 0x00,        //     Logical Minimum (0)
    0x25, 0x01,        //     Logical Maximum (1)
    0x75, 0x01,        //     Report Size (1)
    0x95, 0x10,        //     Report Count (16)
    0x81, 0x02,        //     Input (Data,Var,Abs,No Wrap,Linear,Preferred State,No Null Position)
    0xC0,              //   End Collection
    0xC0               // End Collection
};

int main(int argc, char *argv[]) {
    printf("Testing IOHIDUserDeviceCreateWithProperties on macOS...\n");

    CFMutableDictionaryRef matchingDict = CFDictionaryCreateMutable(
        kCFAllocatorDefault, 0,
        &kCFTypeDictionaryKeyCallBacks,
        &kCFTypeDictionaryValueCallBacks);

    CFDataRef descriptorData = CFDataCreate(kCFAllocatorDefault, gamepad_hid_descriptor, sizeof(gamepad_hid_descriptor));
    CFDictionarySetValue(matchingDict, CFSTR(kIOHIDReportDescriptorKey), descriptorData);

    int vendorID = 0x054C;  // Sony
    int productID = 0x0268; // DualShock 3
    CFNumberRef vendorNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &vendorID);
    CFNumberRef productNum = CFNumberCreate(kCFAllocatorDefault, kCFNumberIntType, &productID);
    CFDictionarySetValue(matchingDict, CFSTR(kIOHIDVendorIDKey), vendorNum);
    CFDictionarySetValue(matchingDict, CFSTR(kIOHIDProductIDKey), productNum);
    CFDictionarySetValue(matchingDict, CFSTR(kIOHIDProductKey), CFSTR("PS3 Virtual Controller"));
    CFDictionarySetValue(matchingDict, CFSTR(kIOHIDManufacturerKey), CFSTR("Antigravity"));

    IOHIDUserDeviceRef device = IOHIDUserDeviceCreateWithProperties(kCFAllocatorDefault, matchingDict, 0);

    CFRelease(descriptorData);
    CFRelease(vendorNum);
    CFRelease(productNum);
    CFRelease(matchingDict);

    if (!device) {
        fprintf(stderr, "ERROR: IOHIDUserDeviceCreateWithProperties returned NULL!\n");
        return 1;
    }

    printf("SUCCESS: IOHIDUserDeviceCreateWithProperties created device: %p\n", (void*)device);

    dispatch_queue_t queue = dispatch_queue_create("com.antigravity.hiddevice", DISPATCH_QUEUE_SERIAL);
    IOHIDUserDeviceSetDispatchQueue(device, queue);
    IOHIDUserDeviceActivate(device);

    printf("Device activated on dispatch queue. Running for 10 seconds...\n");

    // Report buffer: X, Y, Z, Rz (all 128 = centered), buttons (all 0)
    uint8_t report[6] = { 128, 128, 128, 128, 0, 0 };

    for (int i = 0; i < 20; i++) {
        if (i % 2 == 1) {
            report[4] = 0x01; // button 1 pressed
            printf("Tick %d: Button 1 PRESSED\n", i);
        } else {
            report[4] = 0x00; // button 1 released
            printf("Tick %d: Button 1 RELEASED\n", i);
        }
        IOReturn ret = IOHIDUserDeviceHandleReportWithTimeStamp(device, mach_absolute_time(), report, sizeof(report));
        if (ret != kIOReturnSuccess) {
            printf("IOHIDUserDeviceHandleReportWithTimeStamp returned 0x%x\n", ret);
        }
        usleep(500000); // 500ms
    }

    IOHIDUserDeviceCancel(device);
    CFRelease(device);
    printf("Cleaned up device. Test finished.\n");
    return 0;
}
