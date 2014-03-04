package com.gmail.mplackowski.cameratool;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.googlecode.tesseract.android.TessBaseAPI;

import android.app.Activity;
import android.content.ContentProvider.PipeDataWriter;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

public class CameraTool implements PreviewCallback, PictureCallback, RectanglePicker.OnResizeListener, CameraInterface {

	private static final String TAG = "CameraTool";
	
	public static enum CameraSource {FRONT, BACK};
	private CameraSource mSource;
	
	private Activity mActivity;
	private int mDegrees;
	
	private Camera mCamera;
	private Camera.Size mSize;
	private int mCameraCount;
	private int mCameraID;

	private Bitmap mPreviewBitmap;
	
	private Handler mHandler;
	private Thread mOCRWorker;
	private boolean mOCRShouldWork;
	private CameraInterface.OCRListener mOCRListener;
	private CameraInterface.CropListener mCropListener;
	private BlockingQueue<byte[]> mOCRQueue;
	
	private RectanglePicker mPicker;
	private Preview mPreview;
	private ViewGroup mParent;
	
	private boolean mShouldSavePreview;	
	private int mPictureCounter;
	private int mMaxPictureToSave;
	
	TessBaseAPI mTesseract;
	
	public CameraTool(Activity activity) {
		mActivity = activity;
		createFileLocation();
		if (hasHardwareCamera(mActivity)) {
			mCameraCount = Camera.getNumberOfCameras();
			defaults();
		}
	}
	
	private void defaults(){
		source(CameraSource.BACK);
		mShouldSavePreview = false;
		mPictureCounter = 0;
		mMaxPictureToSave = 0;
	}
	
	public boolean start()
	{
		if(startPreview() && startOCR())
			return true;
		return false;
	}
	
	public boolean startPreview() {

		nullCheck();

		if (hasHardwareCamera(mActivity)) {
			mPreview = new Preview(mActivity);
			mParent.addView(mPreview);

			return safeCameraOpen();
		}
		return false;
	}
	
	public boolean startOCR() {
		
		if(mOCRShouldWork) return false;

		nullCheck();

		mOCRShouldWork = true;
		
		mPicker = new RectanglePicker(mActivity);
		mParent.addView(mPicker);

		mOCRQueue = new LinkedBlockingQueue<byte[]>();

		mTesseract = new TessBaseAPI();
		mTesseract.setDebug(true);
		mTesseract.init(CameraInterface.DATA_PATH, CameraInterface.LANG);
		
		
		mHandler = new Handler() {
			@Override
			public void handleMessage(Message msg) {
				super.handleMessage(msg);
				String text = msg.getData().getString(CameraInterface.OCR_TEXT);
				float accuracy = msg.getData().getFloat(
						CameraInterface.OCR_ACCURACY);
				if (mOCRListener != null) {
					mOCRListener.onTextRecognized(text, accuracy);
				}
			}
		};

		mOCRWorker = new Thread(new Runnable() {

			@Override
			public void run() {
				while (mOCRShouldWork) {
					try {
						byte[] imageData = mOCRQueue.take();
						
						mPreviewBitmap = getBitmapImageFromYUV(imageData, mSize.width,
								mSize.height, mDegrees, mPicker.rect(mSize, mDegrees));
						
						if(mOCRQueue.size()>CameraInterface.OCR_QUEUE_SIZE)
							mOCRQueue.clear();
						
						forwardPreviewBitmap(mPreviewBitmap);
						
						mTesseract.clear();
						mTesseract.setImage(mPreviewBitmap);
						
						String recognizedText = mTesseract.getUTF8Text();
						float accuracy = mTesseract.meanConfidence();
						recognizedText = recognizedText.replaceAll("[^a-zA-Z0-9]+", " ");
						
						updateOCR(recognizedText,accuracy);

					} catch (InterruptedException e) {
						Log.d(TAG, "mOCRThread  InterruptedException "+e);
					}
				}
				mTesseract.end();
			}
		});
		
		mOCRWorker.start();
		
		return true;
	}
	
	protected void forwardPreviewBitmap(Bitmap mPreviewBitmap2) {
		if(mCropListener!=null)
			mCropListener.onCropUpdate(mPreviewBitmap);
		
		if(mShouldSavePreview)
			saveBitmap(mPreviewBitmap);
	}

	private void updateOCR(String text, float accuracy){
		Message m = new Message();
		Bundle b = new Bundle();
		b.putString(CameraInterface.OCR_TEXT, text);
		b.putFloat(CameraInterface.OCR_ACCURACY, accuracy);
		m.setData(b);
		mHandler.sendMessage(m);
	}

	public void stopOCR() {
		mOCRShouldWork = false;
	}
	public void stop() {
		stopOCR();
		releaseCameraAndPreview();
	}
	
	private void nullCheck(){
		if(mActivity == null)
			throw new RuntimeException("Activity is NULL!");
		if(mParent == null)
			throw new RuntimeException("Container View is NULL!");
	}

	public CameraTool setPreviewCallback() {
		mCamera.setPreviewCallback(this);
		return this;
	}

	private boolean safeCameraOpen() {
		boolean qOpened = false;

		try {
			releaseCameraAndPreview();
			mCamera = Camera.open(mCameraID);

			setPreviewCallback();
			qOpened = (mCamera != null);
		} catch (Exception e) {
			Log.d(TAG, "failed to open Camera");
			mCamera = null;
			e.printStackTrace();
		}

		return qOpened;
	}

	private void releaseCameraAndPreview() {
		if (mCamera != null) {
			mCamera.stopPreview();
			mCamera.setPreviewCallback(null);
		    mPreview.getHolder().removeCallback(mPreview);
			mCamera.release();
			mCamera = null;
		}
	}

	private int getCameraID(CameraSource type) {
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		for (int cameraID = 0; cameraID < mCameraCount; cameraID++) {
			Camera.getCameraInfo(cameraID, cameraInfo);
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
					&& type == CameraSource.FRONT)
				return cameraID;
			if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK
					&& type == CameraSource.BACK)
				return cameraID;
		}
		return -1;
	}

	public View getPreview() {
		return mPreview;
	}

	private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w,
			int h) {
		final double ASPECT_TOLERANCE = 0.1;
		double targetRatio = (double) h / w;

		if (sizes == null)
			return null;

		Camera.Size optimalSize = null;
		double minDiffH = Double.MAX_VALUE;
		double minDiffW = Double.MAX_VALUE;

		int targetHeight = h;
		int targetWidth = w;

		for (Camera.Size size : sizes) {
			Log.d(TAG,"getOptimalPreviewSize h:"+size.height+" w:"+size.width);
			double ratio = (double) size.width / size.height;
			if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE)
				continue;
			if (Math.abs(size.height - targetHeight) < minDiffH) {
				optimalSize = size;
				minDiffH = Math.abs(size.height - targetHeight);
			}
			if(Math.abs(size.width - targetWidth) < minDiffW)
				minDiffW = Math.abs(size.width - targetWidth);
		}

		if (optimalSize == null) {
			minDiffH = Double.MAX_VALUE;
			minDiffW = Double.MAX_VALUE;
			
			for (Camera.Size size : sizes) {
				int diffH  = Math.abs(size.height - targetHeight);
				int diffW = Math.abs(size.width - targetWidth);
				
				if(diffH<diffW){
					if (diffH < minDiffH)
					{
						optimalSize = size;
						minDiffH = Math.abs(size.height - targetHeight);
					}
					
				}else {
					if (diffW < minDiffW)
					{
						optimalSize = size;
						minDiffW = Math.abs(size.width - targetWidth);
					}
				}
			}
		}
		
		//Log.d(TAG,"getOptimalPreviewSize RETURN  h:"+optimalSize.height+" w:"+optimalSize.width);
		return optimalSize;
	}
	
	public int getOrientationDegrees(Activity activity, Camera camera) {
		Camera.CameraInfo info = new Camera.CameraInfo();
		Camera.getCameraInfo(mCameraID, info);

		int rotation = activity.getWindowManager().getDefaultDisplay()
				.getRotation();
		int degrees = 0;
		switch (rotation) {
		case Surface.ROTATION_0:  degrees = 0;  break; 
		case Surface.ROTATION_90: degrees = 90; break;
		case Surface.ROTATION_180:degrees = 180;break;
		case Surface.ROTATION_270:degrees = 270;break;
		}

		int result;
		if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
			result = (info.orientation + degrees) % 360;
			result = (360 - result) % 360; // compensate the mirror
		} else { // back-facing
			result = (info.orientation - degrees + 360) % 360;
		}
		Log.d(TAG,"getOrientationDegrees "+result);
		return result;
	}

	public void setCameraDisplayOrientation(Activity activity, Camera camera) {

		mDegrees = getOrientationDegrees(activity, camera);
		camera.setDisplayOrientation(mDegrees);
	}

	class Preview extends SurfaceView implements SurfaceHolder.Callback {

		private SurfaceHolder mHolder;

		public Preview(Context context) {
			super(context);
			mHolder = getHolder();
			mHolder.addCallback(this);
		}

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			
			Log.d(TAG,"surfaceChanged");
//			if (mHolder.getSurface() == null)
//				return;

//			try {
//				mCamera.stopPreview();
//			} catch (Exception e) {
//			}
//			
//			safeStartPreview();

		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			Log.d(TAG,"surfaceCreated");
			safeStartPreview();

		}
		
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			stop();
		}
		
		private void safeStartPreview(){
			
			try {
				mCamera.setPreviewDisplay(mHolder);
				fitPreview();
				setCameraDisplayOrientation(mActivity, mCamera);
				mCamera.startPreview();

			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}

	@Override
	public void onPreviewFrame(byte[] data, Camera camera) {
		// TODO Auto-generated method stub
		Log.d(TAG, "onPreviewFrame ");
		if(mOCRQueue!=null && data != null){
			boolean addedToQueue = mOCRQueue.offer(data);
			Log.d(TAG,"onPreviewFrame addedToQueue "+addedToQueue+" size "+mOCRQueue.size());
			
		}

	}

	@Override
	public void onPictureTaken(byte[] data, Camera camera) {
		//Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
		// TODO implement listener

	}
	
	/** Check if this device has a camera */
	public boolean hasHardwareCamera(Context context) {
		if (context.getPackageManager().hasSystemFeature(
				PackageManager.FEATURE_CAMERA)) {
			return true;
		} else {
			return false;
		}
	}

	public static Bitmap getBitmapImageFromYUV(byte[] data, int width,
			int height, int degree, Rect rect) {
		Bitmap bitmap = getBitmapImageFromYUV(data, width, height, rect);
		return rotateBitmap(bitmap, degree,rect);

	}

	public static Bitmap rotateBitmap(Bitmap source, float angle, Rect rect) {
		Matrix matrix = new Matrix();
		matrix.postRotate(angle);

		source = Bitmap.createBitmap(source, 0, 0, source.getWidth(),
				source.getHeight(), matrix, true);
		source = Bitmap.createBitmap(source, rect.left, rect.top, rect.width(), rect.height());
		
		return source;

	}

	public static Bitmap getBitmapImageFromYUV(byte[] data, int width,
			int height, Rect rect) {
		YuvImage yuvimage = new YuvImage(data, ImageFormat.NV21, width, height,
				null);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		yuvimage.compressToJpeg(new Rect(0, 0, width, height), 90, baos);

		byte[] jdata = baos.toByteArray();
		BitmapFactory.Options bitmapFatoryOptions = new BitmapFactory.Options();
		bitmapFatoryOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
		Bitmap bmp = BitmapFactory.decodeByteArray(jdata, 0, jdata.length,
				bitmapFatoryOptions);
		
		Log.d(TAG,"getBitmapImageFromYUV w:"+bmp.getWidth()+" h:"+bmp.getHeight());
	
		
		return bmp;
	}

	public void fitPreview() 
	{
			mSize = getOptimalPreviewSize(mCamera.getParameters()
					.getSupportedPreviewSizes(), mPreview.getWidth(),
					mPreview.getHeight());

			Camera.Parameters p = mCamera.getParameters();
			p.setPreviewSize(mSize.width, mSize.height);

			mCamera.setParameters(p);
	}
	

	@Override
	public void onPickerResized(Rect rect) {
		Log.d(TAG, "onPickerResized  "+rect);
		
	}
	
	private void saveBitmap(Bitmap b){
		if(mPictureCounter>mMaxPictureToSave)
			mPictureCounter =0;
		
		String fname = "PreviewOCR-"+mPictureCounter+".jpg";
		File file = new File (DATA_PATH, fname);
		if (file.exists ()) file.delete (); 
		try {
		       FileOutputStream out = new FileOutputStream(file);
		       b.compress(Bitmap.CompressFormat.JPEG, 90, out);
		       out.flush();
		       out.close();
		       
		       mPictureCounter++;

		} catch (Exception e) {
		       e.printStackTrace();
		}
	}
	
	private void createFileLocation(){
		
		String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/" };
		
		for (String path : paths) 
		{
			File dir = new File(path);
			if (!dir.exists()) dir.mkdirs();
		}
		
		if (!(new File(DATA_PATH + "tessdata/" + LANG + ".traineddata")).exists()) {
			try {

				AssetManager assetManager = mActivity.getAssets();
				InputStream in = assetManager.open("tessdata/" + LANG + ".traineddata");
				OutputStream out = new FileOutputStream(DATA_PATH
						+ "tessdata/" + LANG + ".traineddata");

				byte[] buf = new byte[1024];
				int len;
				while ((len = in.read(buf)) > 0) 
					out.write(buf, 0, len);
				
				in.close();
				out.close();
				
			} catch (IOException e) {
				Log.e(TAG, "createFileLocation Was unable to copy " + LANG + " traineddata " + e.toString());
			}
		}
	}

	public CameraTool cropListener(CameraInterface.CropListener listener) {
		mCropListener = listener;
		return this;
		
	}

	public CameraTool container(ViewGroup parent) {
		mParent = parent;
		return this;
		
	}

	public CameraInterface.OCRListener getOCRListener() {
		return mOCRListener;
	}

	public CameraTool OCRListener(CameraInterface.OCRListener mOCRListener) {
		this.mOCRListener = mOCRListener;
		return this;
	}

	public CameraSource getSource() {
		return mSource;
	}

	public CameraTool source(CameraSource mSource) {
		this.mSource = mSource;
		mCameraID = getCameraID(mSource);
		return this;
	}

	public boolean isSaving() {
		return mShouldSavePreview;
	}

	public CameraTool save(boolean shouldSave) {
		this.mShouldSavePreview = shouldSave;
		return this;
	}
	public CameraTool save(int numberOfSaves) {
		this.mShouldSavePreview = true;
		mMaxPictureToSave = numberOfSaves;
		return this;
	}

}
