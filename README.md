# SpendTrack Native

Native Android rewrite of SpendTrack using Kotlin, Jetpack Compose and Material 3 / Expressive-ready APIs.

## Baseline
- Kotlin 2.4.10
- Android Gradle Plugin 9.4.0
- Compose BOM 2026.08.00
- Material 3 1.4.0 (stable)
- Navigation Compose 2.10.0
- Room 2.8.4
- WorkManager 2.11.0
- compileSdk / targetSdk 37
- minSdk 26

## Architecture
- Single native Android app
- Jetpack Compose UI
- Room local database
- ViewModel/repository layer to be added as screens become functional
- WorkManager for reminder scheduling
- Android local notifications for card/loan reminders

## Current milestone
The app shell is established with:
- Material 3 Expressive floating navigation with four top-level destinations
- Home dashboard calculated from local Room data
- Offline transaction entry, including income and shared-expense personal share
- Atomic account/card balance effects when transactions are added or deleted
- Room entities and DAOs for accounts, categories, transactions, budgets, loans and owed records

## Still to implement

- Account/card, category, budget, loan and owed-entry management screens
- Transaction search, filters and editing
- Credit-card bill-payment transfers, backup/restore and native reminder scheduling

## Build
Open this directory in Android Studio and let Gradle sync. A local Android SDK is required.
