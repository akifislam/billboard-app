# BillBoard iOS 1.0

`BillBoard-iOS-Shareable-Unsigned.ipa` is an arm64 device build for iOS 17 or later.
The BillBoard app icon is embedded in the package.

## Install on an iPhone

Apple does not permit an unsigned IPA to be installed by opening it from Files.
Before installation, sign the IPA for the destination iPhone using one of these methods:

1. Open this Xcode project, select the iPhone as the run destination, select a Personal Team under Signing & Capabilities, and press Run.
2. Import the unsigned IPA into Sideloadly or AltStore and sign it with an Apple Account.
3. Use an Apple Developer distribution certificate and provisioning profile, then distribute through TestFlight or an approved ad hoc installation.

A free Personal Team installation normally needs periodic re-signing. TestFlight or paid Apple Developer distribution is preferable for a durable installation.
