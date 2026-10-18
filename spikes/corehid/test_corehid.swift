import Foundation
import CoreHID

// Standard Gamepad HID Descriptor (6 bytes: 4 axes 8-bit, 16 buttons 1-bit)
let descriptorBytes: [UInt8] = [
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
    0x81, 0x02,        //     Input (Data,Var,Abs)
    0x05, 0x09,        //     Usage Page (Button)
    0x19, 0x01,        //     Usage Minimum (0x01)
    0x29, 0x10,        //     Usage Maximum (0x10)
    0x15, 0x00,        //     Logical Minimum (0)
    0x25, 0x01,        //     Logical Maximum (1)
    0x75, 0x01,        //     Report Size (1)
    0x95, 0x10,        //     Report Count (16)
    0x81, 0x02,        //     Input (Data,Var,Abs)
    0xC0,              //   End Collection
    0xC0               // End Collection
]

final class DummyDelegate: HIDVirtualDeviceDelegate, @unchecked Sendable {
    func hidVirtualDevice(_ device: HIDVirtualDevice, receivedSetReportRequestOfType type: HIDReportType, id: HIDReportID?, data: Data) async throws {
        print("Delegate receivedSetReportRequestOfType")
    }
    func hidVirtualDevice(_ device: HIDVirtualDevice, receivedGetReportRequestOfType type: HIDReportType, id: HIDReportID?, maxSize: Int) async throws -> Data {
        print("Delegate receivedGetReportRequestOfType")
        return Data([128, 128, 128, 128, 0, 0])
    }
}

@main
struct TestCoreHID {
    static func main() async {
        print("Testing CoreHID.HIDVirtualDevice on macOS 27...")
        let descData = Data(descriptorBytes)
        let props = HIDVirtualDevice.Properties(
            descriptor: descData,
            vendorID: 0x054C,
            productID: 0x0268,
            product: "PS3 Virtual Controller",
            manufacturer: "Antigravity"
        )
        guard let device = HIDVirtualDevice(properties: props) else {
            print("ERROR: HIDVirtualDevice(properties:) returned nil!")
            return
        }
        print("SUCCESS: Created HIDVirtualDevice: \(device)")
        let delegate = DummyDelegate()
        await device.activate(delegate: delegate)
        print("Device activated! Holding for 10 seconds...")
        for i in 0..<20 {
            try? await Task.sleep(nanoseconds: 500_000_000)
            var report = [UInt8](repeating: 128, count: 4)
            report.append(i % 2 == 1 ? 0x01 : 0x00)
            report.append(0x00)
            do {
                try await device.dispatchInputReport(data: Data(report), timestamp: .now)
                print("Tick \(i): Dispatched report (Button 1 = \(i % 2 == 1))")
            } catch {
                print("dispatchInputReport error: \(error)")
            }
        }
        print("Test completed.")
    }
}
