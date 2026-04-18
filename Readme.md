🔐 Secure File Locker

A Java-based desktop application for securely encrypting and decrypting files using modern cryptographic standards. Built with a user-friendly GUI and strong security practices.

🚀 Features

- 🔒 AES-256 encryption using GCM mode (authenticated encryption)
- 🔑 Password-based key derivation (PBKDF2 with salt)
- 📂 Drag-and-drop file support
- 📊 Real-time progress tracking
- 🖥️ Clean and interactive Swing-based UI
- 🛡️ Protection against tampering and incorrect passwords
- ⚡ Fast and efficient file processing

🛠️ Technologies Used

- Java (JDK 8+)
- Swing (GUI)
- Cryptography:
  - AES/GCM/NoPadding
  - PBKDF2 (HmacSHA256)

📦 How It Works
Encryption

1. Select a file
2. Enter and confirm password
3. File is encrypted using AES-GCM
4. Output file: `filename.ext.enc`

Decryption

1. Select `.enc` file
2. Enter password
3. File is securely decrypted
4. Output file: `decrypted_filename.ext`

▶️ How to Run

1. Compile the program:
   javac SecureFileLocker.java

2. Run the application:
   java SecureFileLocker

📁 Project Structure
SecureFileLocker.java
README.md

🔐 Security Details

- Uses **AES-256-GCM** for encryption (ensures confidentiality + integrity)
- Uses **PBKDF2 (200,000 iterations)** for strong password-based key generation
- Random **salt** and **IV** generated for every encryption
- Detects:
  - Wrong password
  - File tampering (via authentication tag)

⚠️ Limitations

- Desktop application only (no web/mobile support)
- No file recovery if password is lost
- Large files depend on system performance
