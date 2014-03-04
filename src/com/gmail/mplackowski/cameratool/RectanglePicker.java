package com.gmail.mplackowski.cameratool;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class RectanglePicker extends View {

	private static final String TAG = "RectanglePicker";
	
	public static final int DEFAULT_DIVIER = 8;
	public static final int DEFAULT_PADDING = 0;
	public static final int DEFAULT_PICKER_COLOR = Color.parseColor("#EB974E");
	public static final int DEFAULT_ACTIVE_COLOR = Color.parseColor("#1BA39C");
	
	interface OnResizeListener {
		public void onPickerResized(Rect rect);
	};
	
	private OnResizeListener mResizeListener;

	public enum PickerFit {FILL, SQUARE /*OPPOSITE*/};
	private PickerFit mFitting;

	public enum PickerArea {NONE, LEFT_TOP, INSIDE, RIGHT_BOTTOM};
	private PickerArea mPickerArea;
	
	public enum Border {LEFT, RIGHT, TOP, BOTTOM};

	private Paint mPaint;
	
	private int mPickerColor;
	private int mActiveColor;

	private int mBoundWidth;
	private int mBoundHeight;

	private int mPadding;
	private int mWidth;
	private int mHeight;
	private int mX;
	private int mY;

	private int mActiveSize;
	private int mDivider;
	private int mTouchStartX;
	private int mTouchStartY;

	private boolean mInit;
	
	public RectanglePicker(Context context) {
		super(context);
		defaults();
	}
	
	public RectanglePicker(Context context,PickerFit fitting, int divider, int padding) {
		super(context);
		defaults();
		mFitting = fitting;
		mDivider = divider;
		mPadding = padding;
	}
	
	private void defaults(){
		mPaint = new Paint();
		mInit = true;
		mFitting = PickerFit.SQUARE;
		mDivider = DEFAULT_DIVIER;
		mPadding = DEFAULT_PADDING;
		mPickerColor = DEFAULT_PICKER_COLOR;
		mActiveColor = DEFAULT_ACTIVE_COLOR;
	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		Log.d(TAG, "onLayout w:" + getWidth() + " h: " + getHeight());
		mBoundHeight = getHeight();
		mBoundWidth = getWidth();
		mActiveSize = Math.min(mBoundHeight, mBoundWidth)/mDivider;
		if (mInit) {
			mInit = false;
			computeInitLayout();
		}
	}

	private void computeInitLayout() {

		switch (mFitting) {
		case FILL:
			mHeight = mBoundHeight - 2 * mPadding;
			mWidth = mBoundWidth - 2 * mPadding;
			break;
		case SQUARE: {
			int min = Math.min(mBoundHeight, mBoundWidth);
			mHeight = min - 2 * mPadding;
			mWidth = mHeight;
			break;
		}
//		case OPPOSITE: {
//			int min = Math.min(mBoundHeight, mBoundWidth);
//			int max = Math.max(mBoundHeight, mBoundWidth);
//			int ratio = min / max;
//
//			mHeight = min * ratio - 2 * mPadding;
//			mWidth = min - 2 * mPadding;
//			Log.d(TAG,"computeInitLayout h "+mHeight+ " w: "+mWidth+" ratio "+ratio);
//			break;
//		}
		}
	}


	@Override
	public void onDraw(Canvas canvas) {

		// 1. Draw main rectangle
		mPaint.setColor(mPickerColor);
		mPaint.setStyle(Paint.Style.STROKE);
		mPaint.setStrokeWidth(3);
		canvas.drawRect(left(), top(), right(), bottom(), mPaint);
		// 2. draw left top active area
		mPaint.setColor(mActiveColor);
		canvas.drawRect(left(), 
						 top(), 
						 left() + mActiveSize, 
						 top()+ mActiveSize, mPaint);
		
		// 3. draw right bottom active area
		mPaint.setColor(mActiveColor);
		canvas.drawRect(right() - mActiveSize, 
						bottom() - mActiveSize, 
						right(),
						bottom(), mPaint);
	}
	
	
	public Rect rect(){
		return new Rect(left(), top(), right(), bottom());
	}
	
	public Rect rect(Camera.Size size, int degrees){
		
		float hR = 0;
		float wR = 0;
		
		// portrait 
		if(degrees == 90 || degrees == 270)
		{
			hR = (float)size.width/mBoundHeight;
			wR = (float)size.height/mBoundWidth;
		// landscape
		}else
		{
			hR = (float)size.height/mBoundHeight;
			wR = (float)size.width/mBoundWidth;
		}
		
		int left =   (int)(wR*mX);
		int right =  (int)(wR*(mX+mWidth));
		int top =    (int)(hR*mY);
		int bottom = (int)(hR*(mY+mHeight));
		Log.d(TAG,"pickerRect "+(new Rect(left,top,right,bottom)));
		return new Rect(left,top,right,bottom);
	}
		

	@Override
	public boolean onTouchEvent(MotionEvent event) {

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN: 
			startTouch(event);
			break;
		case MotionEvent.ACTION_MOVE: 
			handleMoveEvent(event);
			break;
		}
		return true;
	}
	
	private void resetTouch(MotionEvent event){
		mTouchStartX = (int) event.getX();
		mTouchStartY = (int) event.getY();
	}

	private void startTouch(MotionEvent event) {
		mPickerArea = getArea((int) event.getX(), (int) event.getY());
		resetTouch(event);
	}

	private void handleMoveEvent(MotionEvent event) {
		switch (mPickerArea) {
		case INSIDE:
			movePicker(event);
			break;
		case LEFT_TOP:
			resizePicker(event, PickerArea.LEFT_TOP);
			break;
		case RIGHT_BOTTOM:
			resizePicker(event, PickerArea.RIGHT_BOTTOM);
			break;
		case NONE:
			break;

		default:
			break;

		}

	}

	private void resizePicker(MotionEvent event, PickerArea area) {
		// 1. Calculate move
		int x = (int) event.getX() - mTouchStartX;
		int y = (int) event.getY() - mTouchStartY;

		if (x == 0 && y == 0) return;

		Log.d(TAG, "resizePicker x:" + x + " y:" + y);

		// 2. Validate and assign new values
		if (area == PickerArea.LEFT_TOP) {
			if (isValidLeft(mX + x)) {
				mWidth -= x;
				mX += x;
			}
			if (isValidTop(mY + y)) {
				mHeight -= y;
				mY += y;
			}
		} else if (area == PickerArea.RIGHT_BOTTOM) {
			if (isValidRight(mX + x))
				mWidth += x;
			if (isValidBottom(mY + y))
				mHeight += y;
		}else 
			return;
		// 3. update view and prepare for next touch
		resetTouch(event);
		updateView();

	}

	private void movePicker(MotionEvent event) {

		// 1. Calculate move
		int x = (int) event.getX() - mTouchStartX;
		int y = (int) event.getY() - mTouchStartY;
		
		if (x == 0 && y == 0) return;
		
		Log.d(TAG, "movePicker x:" + x + " y:" + y);

		// 2. Validate and assign new values
		if (isValidX(mX + x))
			mX += x;

		if (isValidY(mY + y))
			mY += y;

		// 3. update view and prepare for next touch
		resetTouch(event);
		updateView();
	}
	
	private void updateView() {
		if(mResizeListener!=null)
			mResizeListener.onPickerResized(rect());
		invalidate();
	}

	private boolean isValidLeft(int x) {
		if (left(x) >= border(Border.LEFT) && left(x) < right())
			return true;
		return false;
	}

	private boolean isValidRight(int x) {
		if (right(x) <= border(Border.RIGHT) && right(x)> left())
			return true;
		return false;
	}

	private boolean isValidTop(int y) {
		if (top(y) >= border(Border.TOP) && top(y) < bottom())
			return true;
		return false;
	}

	private boolean isValidBottom(int y) {
		if (bottom(y) <= border(Border.BOTTOM) && bottom(y) > top())
			return true;
		return false;
	}

	private boolean isValidX(int x) {
		if (isValidLeft(x) && isValidRight(x))
			return true;
		return false;
	}

	private boolean isValidY(int y) {
		if (isValidTop(y) && isValidBottom(y))
			return true;
		return false;
	}

	private PickerArea getArea(int x, int y) {

		// 1. left top area
		if (x >= left() && 
			x <= left() + mActiveSize && 
			y >= top() && 
			y <= top() + mActiveSize)
			return PickerArea.LEFT_TOP;

		// 2. right bottom area
		else if (x >= right() - mActiveSize && 
				 x <= right() &&
				 y >= bottom() - mActiveSize && 
				 y <= bottom())
			return PickerArea.RIGHT_BOTTOM;

		// 3. inside
		else if (hasPoint(x, y))
			return PickerArea.INSIDE;

		return PickerArea.NONE;
	}

	public boolean hasPoint(int x, int y) {
		if (x >= left() && 
			x <= right()&& 
			y >= top()  && 
			y <= bottom())
			return true;
		return false;
	}

	private int border(Border border) {
		switch (border) {
		case LEFT:  return mPadding;
		case RIGHT: return mBoundWidth - mPadding;
		case TOP:   return mPadding;
		case BOTTOM:return mBoundHeight - mPadding;
		}

		return 0;
	}

	private int left(int x)  {return x + mPadding;}

	private int right(int x) {return x + mWidth;}

	private int top(int y)   {return y + mPadding;}

	private int bottom(int y){return y + mHeight;}

	public int left()        {return left(mX);}
	
	public int right()       {return right(mX);}
	
	public int top()         {return top(mY);}
	
	public int bottom()      {return bottom(mY);}

}
