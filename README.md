previewOCR
==========

Implementation of OCR tool for Android

To use it you need tess-two library, all information can be found here:
https://github.com/rmtheis/tess-two

Main features:
- simplified camera initalisation 
- picker view for selecting text area from the preview 
- live recognition
- desired number of last analyzed images can be saved

How it works: 

    mCameraTool = new CameraTool(this);
    mCameraTool.container(mCameraRL)        // ViewGroup that will contain the Preview and Picker views
               .OCRListener(this)           // Listener for OCR results
               .cropListener(this)          // Listener for Picker changes 
               .save(10)                    // Stores last 10 previews on SD-Card
               .source(CameraSource.BACK)   // Defines source camera
               .start();                    // Starts preview and OCR
               
More here: 

- WIKI https://github.com/mplackowski/previewOCR/wiki
- Sample Activity https://github.com/mplackowski/previewOCR/blob/master/src/com/gmail/mplackowski/previewocr/MainActivity.java


Sample Screenshot

![alt tag](http://i.imgur.com/dS9zpo1.png)

TODO:
- Expose Camera object and its options for easy changes
- Test on different devices
- Improve landscape mode (for now preview always fits parent view) - it can be stretched now

Credits: (all helpful sources that was used to build that project)
- https://github.com/GautamGupta/Simple-Android-OCR
- http://developer.android.com/guide/topics/media/camera.html
- http://developer.android.com/samples/MediaRecorder/index.html
- https://github.com/rmtheis/tess-two 


