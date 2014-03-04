package com.gmail.mplackowski.previewocr;


import com.gmail.mplackowski.cameratool.CameraInterface;
import com.gmail.mplackowski.cameratool.CameraTool;
import com.gmail.mplackowski.cameratool.CameraTool.CameraSource;

import android.os.Bundle;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.util.Log;
import android.view.Menu;
import android.view.OrientationEventListener;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class MainActivity extends Activity implements CameraInterface.OCRListener, CameraInterface.CropListener {
	
	private static final String TAG = "MainActivity";
	private CameraTool mCameraTool;
	private RelativeLayout mCameraRL;
	private TextView mOcrTV;
	private ImageView mCropIV;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mCameraRL = (RelativeLayout) findViewById(R.id.cameraRL);
		mOcrTV = (TextView) findViewById(R.id.ocrTV);
		mCropIV = (ImageView) findViewById(R.id.cropIV);

		mCameraTool = new CameraTool(this);
		mCameraTool.container(mCameraRL)		// ViewGroup that will contain the Preview and Picker views
				   .OCRListener(this)			// Listener for OCR results
				   .cropListener(this)			// Listener for Picker changes 
				   .save(10)					// Stores last 10 previews on SD-Card
				   .source(CameraSource.BACK)	// Defines source camera
				   .start();					// Starts preview and OCR
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onTextRecognized(String text, float accuracy) {
		mOcrTV.setText("Accuracy:["+accuracy+"] Text: "+text);
	}

	@Override
	public void onCropUpdate(final Bitmap image) {
		
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mCropIV.setImageBitmap(image);
			}
		});

		
	}

}
