## CameraFragment Enhancement Tasks

- [ ] Disable Git auto-revert on CameraFragment.kt
- [ ] Correct ObjectDetectorHelper constructor invocation using named parameters
- [ ] Guard MediaStore output-stream call with nullable `?.use`
- [ ] Implement shutter button touch feedback on press
- [ ] Wire `capturePhoto()` logic to shutter button click listener
- [ ] Trigger ML model inference in Capture mode
- [ ] Overlay bounding boxes on captured image
- [ ] Save composited image to MediaStore
- [ ] Implement UI mode toggling between Capture and Real-time
- [ ] Diagnose and fix crash on capture invocation
- [ ] Test capture flow: button dims, inference runs, overlay draws, image saves
- [ ] Rebuild and verify functionality on a physical device
- [ ] Commit final changes and remove experimental debugging code
