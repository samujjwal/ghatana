/**
 * Platform Plugins Settings
 *
 * Defines all submodules in the platform-plugins composite build.
 */
rootProject.name = "platform-plugins"

includeBuild("../platform-kernel")

include("plugin-billing-ledger")
include("plugin-fraud-detection")
include("plugin-compliance")
include("plugin-consent")
include("plugin-risk-management")
include("plugin-audit-trail")
