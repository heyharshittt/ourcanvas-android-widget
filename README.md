# OurCanvas Android Widget

A small Android companion for the existing OurCanvas website. The widget displays the shared Firebase Realtime Database drawing; tapping the canvas opens the existing website so drawing remains unchanged.

## Setup
1. Install the APK on both Android phones.
2. Open **OurCanvas Widget** and paste the invite link from the website.
3. Tap **Connect canvas**.
4. Add the **OurCanvas** widget from the phone's home-screen widget picker.

Both phones must use the same invite link / room.

## Refresh behaviour
Android home-screen widgets are not continuously live. This widget refreshes periodically (about every 15 minutes) and when its refresh control is tapped. Android may defer background refreshes because of battery and system restrictions.

## Firebase
This companion reads the existing path:
`rooms/{roomId}/strokes.json`

It uses the existing Realtime Database URL and does not create a second database or modify the website. The database must permit unauthenticated reads for the room data, matching the access model currently used by the website. If reads are denied, review the Realtime Database rules before using the widget. Do not make the entire database publicly writable.

## Build
The GitHub Actions workflow builds a debug APK. Open **Actions**, select the latest successful **Build Android APK** run, and download the `ourcanvas-debug-apk` artifact.
