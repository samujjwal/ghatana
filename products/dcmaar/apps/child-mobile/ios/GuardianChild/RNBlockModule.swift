import Foundation
import React

@objc(RNBlockModule)
class RNBlockModule: NSObject, RCTBridgeModule {

  static func moduleName() -> String! {
    return "RNBlockModule"
  }

  static func requiresMainQueueSetup() -> Bool {
    return false
  }

  @objc
  func getBlockEvents(
    _ resolver: @escaping RCTPromiseResolveBlock,
    rejecter: @escaping RCTPromiseRejectBlock
  ) {
    // Stub implementation: no native block events yet.
    // The JS bridge treats an empty array as "no native data" and falls back to API.
    resolver([])
  }
}
