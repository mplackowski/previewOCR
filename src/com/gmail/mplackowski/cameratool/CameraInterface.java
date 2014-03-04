package com.gmail.mplackowski.cameratool;

import android.graphics.Bitmap;
import android.os.Environment;

public interface CameraInterface {
	
	public static final String OCR_TEXT = "OcrText";
	public static final String OCR_ACCURACY = "OcrAccuracy";
	public static final String DATA_PATH = Environment
			.getExternalStorageDirectory().toString() + "/PreviewOCR/";
	public static final String LANG = "eng";
	public static final int OCR_QUEUE_SIZE = 3;
	
	public static final int DEGREE_0 = 0;
	public static final int DEGREE_90 = 90;
	public static final int DEGREE_180 = 180;
	public static final int DEGREE_270 = 270;

	interface OCRListener {
		public void onTextRecognized(String text, float accuracy);
	}
	
	interface CropListener {
		public void onCropUpdate(Bitmap image);
	}
}

