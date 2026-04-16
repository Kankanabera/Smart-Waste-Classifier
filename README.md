# ♻️ EcoSort AI — Smart Waste Classification Android App
 
> An on-device Android application that uses a TensorFlow Lite CNN model to classify waste into 5 categories in real-time — no internet connection required. Provides recyclability labels and actionable disposal guidance instantly.
 
![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Language-Java-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![ML](https://img.shields.io/badge/ML-TensorFlow_Lite-FF6F00?style=flat-square&logo=tensorflow&logoColor=white)
![IDE](https://img.shields.io/badge/IDE-Android_Studio-3DDC84?style=flat-square&logo=android-studio&logoColor=white)
![Status](https://img.shields.io/badge/Status-Complete-brightgreen?style=flat-square)
 
---
 
## 📌 Problem Statement
 
Despite growing environmental awareness, many people struggle with identifying correct disposal methods for different types of waste — leading to contamination of recycling streams and poor waste management outcomes. EcoSort AI simplifies this by providing instant, AI-powered waste classification directly on a mobile device, with no internet dependency.
 
---
 
## 🧠 How It Works
 
```
User captures / selects image
        ↓
Bitmap preprocessed → resized to 224×224, normalized to [0,1]
        ↓
TFLite Interpreter runs CNN inference (4 threads)
        ↓
Confidence scores output for 5 classes
        ↓
Top label + confidence + recyclability + disposal guidance displayed
```
 
---
 
## 🗂️ Waste Categories
 
| Category | Recyclable / Compostable | Disposal Guidance |
|----------|--------------------------|-------------------|
| 🧴 Plastic | ✅ Recyclable | Clean the item and place in the recycling bin |
| 📄 Paper | ✅ Recyclable | Recycle if clean and dry |
| 🔩 Metal | ✅ Recyclable | Dispose at a scrap or recycling centre |
| 🫙 Glass | ✅ Recyclable | Place in the glass recycling container |
| 🍂 Organic | 🌱 Compostable | Put in a compost bin |
 
---
 
## 🚀 Features
 
- **Real-time on-device inference** — TFLite model runs entirely on device, no internet needed
- **Confidence Score Display** — shows prediction likelihood as a percentage
- **Recyclability & Compostability Indicator** — clear category label per waste type
- **Disposal Guidance** — actionable recommendations based on classification result
- **Items Scanned Counter** — tracks total waste items classified per session
- **Scan History Tracker** — RecyclerView showing last 10 scans with timestamp
- **Result Color Highlighting** — color-coded waste type labels for quick visual cues
- **Loading Indicator** — animated ProgressBar during model inference
- **Non-blocking UI** — inference runs on a background `ExecutorService` thread
---
 
## 🛠️ Tech Stack
 
| Layer | Technology |
|-------|-----------|
| Language | Java |
| ML Framework | TensorFlow Lite (CNN) |
| UI | XML Layouts, RecyclerView, CardView, MaterialComponents |
| IDE | Android Studio |
| Image Handling | `ImageDecoder` (API 28+), `MediaStore` (API 24–27) |
| Threading | `ExecutorService` (single background thread) |
| Camera | `FileProvider` + `ACTION_IMAGE_CAPTURE` |
 
---
 
## 📁 Project Structure
 
```
Smart-Waste-Classifier/
├── app/
│   └── src/main/
│       ├── java/com/hello/ecosortai/
│       │   ├── MainActivity.java          # Entry point, UI logic, inference pipeline
│       │   ├── WasteClassifier.java       # TFLite model loader & inference engine
│       │   ├── ScanHistoryAdapter.java    # RecyclerView adapter for scan history
│       │   └── ScanRecord.java            # Data model for a single scan
│       ├── assets/
│       │   └── waste_classifier.tflite    # CNN model (place here before building)
│       └── res/
│           └── layout/
│               └── activity_main.xml      # Main UI layout
└── README.md
```
 
---
 
## ⚙️ Setup & Run
 
### Prerequisites
- Android Studio (latest stable)
- Android device or emulator running API 21+
- `waste_classifier.tflite` model file
### Steps
 
```bash
# 1. Clone the repository
git clone https://github.com/Kankanabera/Smart-Waste-Classifier.git
 
# 2. Open in Android Studio
# File → Open → Select the cloned folder
 
# 3. Add the TFLite model
# Place waste_classifier.tflite in:
# app/src/main/assets/waste_classifier.tflite
 
# 4. Build & Run
# Click ▶ Run or use Shift+F10
```
 
> ⚠️ The `.tflite` model file is not included in this repo due to size. You can train your own using the training script or use any compatible 5-class waste classification TFLite model with input size 224×224.
 
---

## 🧪 Model Details
 
- **Input size:** 224 × 224 × 3 (RGB)
- **Output:** 5-class softmax probabilities
- **Classes (in training order):** Glass, Metal, Organic, Paper, Plastic
- **Preprocessing:** Pixel values normalized to [0.0, 1.0]
- **Buffer format:** Direct `ByteBuffer` in native byte order, RGB channel layout
- **Inference threads:** 4 (configured via `Interpreter.Options`)
---
 
## 🌍 Applications
 
- Individual users seeking proper waste disposal guidance
- Educational institutions promoting environmental awareness
- Municipalities supporting public waste segregation initiatives
---
 
## 👩‍💻 Author
 
**Kankana Bera**
[![LinkedIn](https://img.shields.io/badge/LinkedIn-0A66C2?style=flat-square&logo=linkedin&logoColor=white)](https://linkedin.com/in/kankana-bera)
[![GitHub](https://img.shields.io/badge/GitHub-181717?style=flat-square&logo=github&logoColor=white)](https://github.com/Kankanabera)
