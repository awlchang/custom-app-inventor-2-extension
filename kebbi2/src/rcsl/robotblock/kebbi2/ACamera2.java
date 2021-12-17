package rcsl.robotblock.kebbi2;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.hardware.camera2.params.Face;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import com.google.appinventor.components.runtime.AndroidViewComponent;
import com.google.appinventor.components.runtime.ComponentContainer;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import android.os.Handler;
import android.os.HandlerThread;
import android.graphics.Point;
import java.util.Comparator;
import java.util.Arrays;
import android.graphics.ImageFormat;
import android.graphics.Color;

public class ACamera2 {

    private final Activity activity;
    private CameraDevice cameraDevice;
    private int cameraType = 0;
    private int noiseReductionMode = 0;
    private Size imageDimension;
    private Size mPreviewSize;
    private AutoFitTextureView cameraLayout;
    private String cameraID;
    private FrameLayout cameraView;
    private View view;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private boolean visible = true;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private OverlayView mOverlayView;
    private ImageReader mImageReader;
    CameraCharacteristics characteristics;

    public ACamera2(ComponentContainer container) {
        // super(container.$form());
        activity = container.$context();
    }

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        public void onSurfaceTextureAvailable(final SurfaceTexture surfaceTexture, final int width, final int height) {
            openCamera(width, height);
        }

        public void onSurfaceTextureSizeChanged(final SurfaceTexture surfaceTexture, final int width, final int height) {
            configureTransform(width, height);
        }

        public boolean onSurfaceTextureDestroyed(final SurfaceTexture surfaceTexture) {
            return false;
        }

        public void onSurfaceTextureUpdated(final SurfaceTexture surfaceTexture) {
        }
    };

    private void configureTransform(final int viewWidth, final int viewHeight) {
        if (null == cameraLayout || null == imageDimension) {
            return;
        }

        final int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        final Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        final float centerX = viewRect.centerX();
        final float centerY = viewRect.centerY();

        if (1 == rotation || 3 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            final float scale = Math.max(viewHeight / mPreviewSize.getHeight(), viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate((float) (90 * (rotation - 2)), centerX, centerY);
        }
        else if (2 == rotation) {
            matrix.postRotate(180.0f, centerX, centerY);
        }
        cameraLayout.setTransform(matrix);
    }

    private void openCamera(final int viewWidth, final int viewHeight) {

        setUpCameraOutputs(viewWidth, viewHeight);
        configureTransform(viewWidth, viewHeight);

        final CameraManager cameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            // cameraID = cameraManager.getCameraIdList()[cameraType];
            // characteristics = cameraManager.getCameraCharacteristics(this.cameraID);
            // final StreamConfigurationMap configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // assert cameraManager != null;
            // imageDimension = configurationMap.getOutputSizes(SurfaceTexture.class)[0];
            cameraManager.openCamera(cameraID, stateCallback, mBackgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraManager mCameraManager;
    // private boolean mSwappedDimensions;
    private static final int MAX_PREVIEW_WIDTH = 1024;
    private static final int MAX_PREVIEW_HEIGHT = 600;
    private int mSensorOrientation;

    private void setUpCameraOutputs(int width, int height) {
        // Activity activity = getActivity();
        mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraID = mCameraManager.getCameraIdList()[cameraType];
            characteristics = mCameraManager.getCameraCharacteristics(cameraID);
            final StreamConfigurationMap configurationMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            // imageDimension = configurationMap.getOutputSizes(SurfaceTexture.class)[0];
    
            // For still image captures, we use the largest available size.
            Size largest = Collections.max(Arrays.asList(configurationMap.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(null, mBackgroundHandler);

            // Find out if we need to swap dimension to get the preview size relative to sensor
            // coordinate.
            int displayRotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            //noinspection ConstantConditions
            mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            
            Point displaySize = new Point();
            activity.getWindowManager().getDefaultDisplay().getSize(displaySize);

            int rotatedPreviewWidth = width;
            int rotatedPreviewHeight = height;
            int maxPreviewWidth = displaySize.x;
            int maxPreviewHeight = displaySize.y;

            mPreviewSize = chooseOptimalSize(configurationMap.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

            // cameraLayout.setAspectRatio(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // mCameraId = cameraId;

            int orientationOffset = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            Rect activeArraySizeRect = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);

            // Face Detection Matrix
            mFaceDetectionMatrix = new Matrix();
            // TODO - I guess that is not enough if we have a landscape layout too... 
            mFaceDetectionMatrix.setRotate(orientationOffset);

            // Log.i("Test", "activeArraySizeRect1: (" + activeArraySizeRect + ") -> " + activeArraySizeRect.width() + ", " + activeArraySizeRect.height());

            float s1 = mPreviewSize.getWidth() / (float)activeArraySizeRect.width();
            float s2 = mPreviewSize.getHeight() / (float)activeArraySizeRect.height();
//                    float s1 = mOverlayView.getWidth();
//                    float s2 = mOverlayView.getHeight();
            boolean mirror = true; // we always use front face camera
            boolean weAreinPortrait = true;
            // mFaceDetectionMatrix.postScale(mirror ? -s1 : s1, s2);
            int offsetDxDy = 100;
            mFaceDetectionMatrix.setRotate(0);
            mFaceDetectionMatrix.postScale(mirror ? -s1 : s1, s2);
            mFaceDetectionMatrix.postTranslate(mPreviewSize.getWidth()+offsetDxDy, -offsetDxDy);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {

        }
    }

    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            // Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }

    // @SimpleFunction(description = "Initialize camera in an arrangement")
    public void Initialize(final AndroidViewComponent component, final int cameraType) {
        this.cameraType = cameraType;
        cameraView = (FrameLayout) (view = component.getView());
        removeView(cameraView);
        cameraView.addView(cameraLayout = new AutoFitTextureView(activity));
        cameraView.addView(mOverlayView = new OverlayView(activity));
        cameraLayout.setSurfaceTextureListener(surfaceTextureListener);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);

        cameraLayout.setLayoutParams(params);
        // cameraView.setAlpha(0);
        // cameraLayout.setOnTouchListener(this);
        Visible(visible);
    }

    private void removeView(final FrameLayout cameraView) {
        if (cameraLayout != null && cameraLayout.getVisibility() == 0) {
            cameraView.removeView(cameraLayout);
        }
    }

    public void InitializeFailed() {
        // EventDispatcher.dispatchEvent(this, "InitializeFailed");
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        public void onOpened(final CameraDevice camera) {
            cameraDevice = camera;
            createCameraPreview();
        }

        public void onDisconnected(final CameraDevice camera) {
            cameraDevice.close();
        }

        public void onError(final CameraDevice camera, final int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void createCameraPreview() {
        try {
            final SurfaceTexture surfaceTexture = cameraLayout.getSurfaceTexture();
            assert surfaceTexture != null;
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            final Surface previewSurface = new Surface(surfaceTexture);
            (captureRequestBuilder = cameraDevice.createCaptureRequest(1)).addTarget(previewSurface);
            // if (enhance) {
            //     captureRequestBuilder.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_HIGH_QUALITY);
            //     captureRequestBuilder.set(CaptureRequest.SHADING_MODE, CaptureRequest.SHADING_MODE_HIGH_QUALITY);
            //     captureRequestBuilder.set(CaptureRequest.TONEMAP_MODE, CaptureRequest.TONEMAP_MODE_HIGH_QUALITY);
            //     captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY);
            //     captureRequestBuilder.set(CaptureRequest.COLOR_CORRECTION_MODE, CaptureRequest.COLOR_CORRECTION_MODE_HIGH_QUALITY);
            //     captureRequestBuilder.set(CaptureRequest.HOT_PIXEL_MODE, CaptureRequest.HOT_PIXEL_MODE_HIGH_QUALITY);
            //     captureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
            // }
            captureRequestBuilder.set(CaptureRequest.JPEG_QUALITY, (byte) 100);
            // captureRequestBuilder.set(CaptureRequest.FLASH_MODE, (flashMode ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF));
            // captureRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, cameraStyle);
            captureRequestBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE, CaptureRequest.NOISE_REDUCTION_MODE_HIGH_QUALITY);
            captureRequestBuilder.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, 2);

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface), new CameraCaptureSession.StateCallback() {
                public void onConfigured(@NonNull final CameraCaptureSession cameraCaptureSession) {
                    if (null == ACamera2.this.cameraDevice) {
                        return;
                    }
                    cameraCaptureSessions = cameraCaptureSession;
                    // Log.e("Camera Zoom", String.valueOf(MaxZoom()));
                    // Initialized();
                    updatePreview();
                }

                public void onConfigureFailed(@NonNull final CameraCaptureSession cameraCaptureSession) {
                    InitializeFailed();
                }
            }, null);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, 1);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        }
        catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Matrix mFaceDetectionMatrix;
    public static FaceTrackListener faceTrackCallBack = null;

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            Integer mode = result.get(CaptureResult.STATISTICS_FACE_DETECT_MODE);
            Face[] faces = result.get(CaptureResult.STATISTICS_FACES);
            if (faces != null && mode != null) {
                if (faces.length > 0) {
                    for(int i = 0; i < faces.length; i++) {
                        if (faces[i].getScore() > 50) {
                            Log.i("Test", "faces : " + faces.length + " , mode : " + mode);
                            int left = faces[i].getBounds().left;
                            int top = faces[i].getBounds().top;
                            int right = faces[i].getBounds().right;
                            int bottom = faces[i].getBounds().bottom;
                            //float points[] = {(float)left, (float)top, (float)right, (float)bottom};

                            Rect uRect = new Rect(left, top, right, bottom);
                            RectF rectF = new RectF(uRect);
                            mFaceDetectionMatrix.mapRect(rectF);
                            //mFaceDetectionMatrix.mapPoints(points);
                            rectF.round(uRect);
                            //uRect.set((int) rectF.left, (int) rectF.top, (int) rectF.right, (int) rectF.bottom);
                            Log.i("Test", "Activity rect" + i + " bounds: " + uRect);
                            
                            int centerX = uRect.left+(uRect.right-uRect.left)/2;
                            int centerY = uRect.top+(uRect.bottom-uRect.top)/2;
                        
                            faceTrackCallBack.OnEvent(centerX, centerY);

                            final Rect rect = uRect;
                            activity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mOverlayView.setRect(rect);
                                    mOverlayView.requestLayout();
                                }
                            });
                            break;
                        }else{
                            mOverlayView.clear();
                        }
                    }
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    public void Initialized() {
        // EventDispatcher.dispatchEvent(this, "Initialized");
    }

    public void Visible(boolean visible) {
        if(visible){
            mOverlayView.BACKGROUND = Color.TRANSPARENT;
            // mOverlayView.setdrawColor(Color.YELLOW);
        }else{
            mOverlayView.BACKGROUND = Color.BLACK;
            // mOverlayView.setdrawColor(Color.BLACK);
        }
        // if (cameraView != null) {
        //     cameraView.setVisibility(visible ? 0 : 4);
        // }
        this.visible = visible;
    }

     /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }
    }
}