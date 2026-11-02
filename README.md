# BillBoard for iOS

Native SwiftUI edition of BillBoard, the private multi-meter dashboard for NESCO, DESCO, and PGCL.

## Features

- NESCO prepaid and postpaid electricity
- DESCO prepaid electricity
- PGCL postpaid gas
- Live balance/due status and payment information
- Recharge, usage, and provider-specific details
- Balance-duration estimates and low-balance notifications
- Local saved-meter and snapshot storage
- Dark, low-glare interface

This public project contains **no saved customer or meter data**. A fresh installation starts empty.

## Run from Xcode

1. Open `BillBoard.xcodeproj` in Xcode.
2. Select the BillBoard target, then **Signing & Capabilities**.
3. Choose your own Apple development team and use a unique bundle identifier if requested.
4. Select an iOS 17+ simulator or connected iPhone.
5. Press **Run**.

## IPA

[`releases/BillBoard-iOS-Shareable-Unsigned.ipa`](releases/BillBoard-iOS-Shareable-Unsigned.ipa) is an unsigned arm64 build for iOS 17 or later. Apple does not allow an unsigned IPA to install by opening it from Files; each recipient must sign it with their own Apple ID through Xcode, AltStore, or Sideloadly.

For TestFlight or Ad Hoc distribution, use an Apple Developer Program membership and the appropriate distribution profile.

## Privacy

Connections and cached snapshots are stored in the app's local container. Signing certificates, provisioning profiles, Xcode user data, and personal meter lists are excluded from this branch.

## Contact

For a customized version, contact [iamakifislam@gmail.com](mailto:iamakifislam@gmail.com).

BillBoard is independent and is not affiliated with or endorsed by NESCO, DESCO, or PGCL.
