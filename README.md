**📱 Lost & Found App**
**📌 Description**
Lost & Found App is a mobile application that helps users report lost items and connect with others who have found them. Built with Android and Firebase, the app allows users to create listings for lost or found items, chat in real-time, and track delivery statuses. With a clean and responsive UI, the app makes it easy to return lost belongings.

✨ **Features**
🔐 Email/password-based user registration and login

📝 Create and view listings for lost or found items

💬 Real-time chat system between users

📊 Track delivered and found item counts in user profile

📱 Responsive UI with ConstraintLayout and Material Design components

🛠️ **Technologies Used**
Language: Java

Framework: Android SDK

Backend: Firebase (Authentication, Firestore)

Local Database: SQLite

Dependencies: Google Play Services, Firebase SDKs

🚀 **Installation**
✅ **Prerequisites**
Android Studio (latest version recommended)

Firebase account with a project set up

Android device or emulator (API level 31+)

📥 **Steps**
1. Clone the Repository
bash
git clone https://github.com/Andunie/lost-found-app.git
cd lost-found-app
2. Set Up Firebase
Create a Firebase project in the Firebase Console

**Enable Email/Password authentication under Authentication**

**Enable Firestore Database**

**Create the following Firestore collections and fields:**

lostItems Collection
Field	Type
creatorId	String
itemTitle	String
description	String
address	String
postedBy	String
ownerId	String
finderId	String
isDelivered	Boolean

conversations Collection
Field	Type
participants	Array
itemId	String
creatorId	String
lastMessage	String
lastMessageTime	Timestamp
isActive	Boolean

messages Collection
Field	Type
conversationId	String
senderEmail	String
messageContent	String
timestamp	Timestamp

users Collection
Field	Type
username	String
email	String
emailVerified	Boolean
foundItemsCount	Integer
deliveredItemsCount	Integer

Download google-services.json from Firebase and place it in the app/ directory

Add your app’s SHA-1 fingerprint in Firebase Console (run ./gradlew signingReport to get it)

3. Open in Android Studio
Open the project in Android Studio

Sync the project with Gradle files

4. Build and Run
Connect an Android device or start an emulator

Click Run in Android Studio

📱 **Usage**
Register/Login: Open the app and log in or register with your email and password.

Create Listing: Go to the Lost Items tab and add a new item with its title, description, and location.

Chat: Open a listing and start a real-time conversation with another user.

Track Items: Check your profile to view how many items you have found or delivered.

📁 **Project Structure**
app/
├── src/main/java/com/example/myapplication/
│   ├── LoginActivity.java
│   ├── RegisterActivity.java
│   ├── ChatActivity.java
│   ├── ConversationsActivity.java
│   └── LostItemDetailActivity.java
├── src/main/res/
│   └── Layouts and UI resources (e.g., activity_login.xml)
├── google-services.json
🤝 Contributing
Fork this repository

**Create a feature branch**

**bash**
git checkout -b feature-branch
Make your changes and commit

**bash**
git commit -m "Add new feature"
Push to the branch

**bash**
git push origin feature-branch
Open a Pull Request

📄 **License**
This project is licensed under the MIT License. See the LICENSE file for details.

📬 **Contact**
For support or questions, please open an issue or contact the maintainer at **ilhan_yigitoglu_@hotmail.com**

🙏 **Acknowledgements**
Special thanks to the Firebase team for their powerful backend solutions.

Inspired by open-source Android projects and Material Design best practices.
