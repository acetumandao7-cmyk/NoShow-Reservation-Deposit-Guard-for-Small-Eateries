# NoShow: Reservation Deposit Guard for Small Eateries

NoShow is a mobile reservation management application designed for small eateries. It helps manage reservations, reservation deposits, confirmations, cancellations, and no-show records in one organized system.

## Project Overview

NoShow is developed as a native Android mobile application. The MVP uses a local Room database backed by SQLite, allowing the application to work without a cloud backend.

## Technology Stack

- Kotlin
- Jetpack Compose
- Android SDK
- Room Database
- SQLite
- Gradle
- Git / GitHub

## Core Features

- User registration and login
- Customer, Staff, and Admin roles
- Customer and staff profile management
- Reservation creation and management
- Reservation details viewing
- Reservation confirmation
- Reservation deposit recording and tracking
- Reservation cancellation and no-show recording
- Reservation and deposit status monitoring
- Reservation record management
- User management
- Role and permission management
- Deposit rule configuration
- Reservation parameter configuration
- Reporting dashboard and reports

## User Roles

### Customer

Customers can register, log in, manage their profile, create reservations, view reservation details, and monitor reservation and deposit status.

### Staff

Staff can manage reservation records, review reservation details, and manage reservation deposits according to the configured permissions and deposit rules.

### Admin

Administrators can manage users, roles and permissions, deposit settings, reservation parameters, and other system settings.

## Local Database

The current MVP uses a local Room/SQLite database. Data is stored on the device where the application is installed. Cloud synchronization and a remote backend are not required for the current MVP.

## Demo Accounts

The application includes predefined local accounts for testing:

| Role | Email | Password |
|---|---|---|
| Staff | staff@noshow.com | staff123 |
| Admin | admin@noshow.com | admin123 |

Customer accounts can be created through the registration screen.

## Requirements

- Android Studio
- Android SDK
- Java Development Kit supported by the project configuration
- Android device or emulator
- Git

## Running the Project

1. Clone this repository.
2. Open the project folder in Android Studio.
3. Allow Gradle to sync and download the required dependencies.
4. Connect an Android device or start an emulator.
5. Build and run the application.

## Project Structure

```text
NoShow/
├── app/
│   └── src/main/
│       ├── java/com/example/noshow/
│       │   ├── data/
│       │   └── MainActivity.kt
│       └── res/
├── gradle/
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── settings.gradle.kts
└── README.md
```

## Security Note

Release signing files and local configuration files are intentionally excluded from the repository. The release keystore and `keystore.properties` must be kept private and should not be uploaded to GitHub.

## Project Scope

The application focuses on reservation and deposit management for small eateries. Full online food ordering and delivery, advanced payment processing or banking, real-time chat, AI-based customer behavior prediction, dynamic pricing, hardware procurement/POS terminals, and advanced loyalty programs are outside the current project scope.

## Version

Current client release: **NoShow v1.3**
