import Foundation
import React

@objc(RNUsageModule)
class RNUsageModule: NSObject, RCTBridgeModule {

  static func moduleName() -> String! {
    return "RNUsageModule"
  }

  static func requiresMainQueueSetup() -> Bool {
    return false
  }

  @objc
  func getUsageOverview(
    _ startTime: NSNumber,
    endTime: NSNumber,
    resolver: @escaping RCTPromiseResolveBlock,
    rejecter: @escaping RCTPromiseRejectBlock
  ) {
    // Stub implementation: no native usage sessions yet.
    // The JS bridge treats an empty array as "no native data" and falls back to API.
    resolver([])
  }
}
