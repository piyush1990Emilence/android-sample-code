
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.Intent;
import android.databinding.DataBindingUtil;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.StatFs;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class CustomCameraActivity extends AppCompatActivity implements SurfaceHolder.Callback, View.OnClickListener {

    public boolean isGoingNextActivity = false;
    int flag = 0;
    float mDist = 0;
    int flashType = 1;
    OrientationEventListener myOrientationEventListener;
    int iOrientation = 0;
    int mOrientation = 90;
    private ActivityCustomCameraBinding binding;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private Handler customHandler = new Handler();
    private File tempFile = null;
    private Camera.PictureCallback jpegCallback;
    private MediaRecorder mediaRecorder;
    private RunTimePermission runTimePermission;
    private Animation scale_up_animation, fade_out_animation;
    private boolean isVideoCapture = false;
    private int oldY, touchSlop;
    private List<PojoGallery> pojoGalleries1 = new ArrayList<>();
    private File folder = null;
    private SavePicTask savePicTask;
    private int mPhotoAngle = 90;
    private long timeInMilliseconds = 0L, startTime = SystemClock.uptimeMillis(), updatedTime = 0L, timeSwapBuff = 0L;
    private Runnable updateTimerThread = new Runnable() {

        public void run() {

            timeInMilliseconds = SystemClock.uptimeMillis() - startTime;
            updatedTime = timeSwapBuff + timeInMilliseconds;

            int secs = (int) (updatedTime / 1000);
            int mins = secs / 60;
            int hrs = mins / 60;

            secs = secs % 60;
            binding.textCounter.setText(String.format("%02d", mins) + ":" + String.format("%02d", secs));
            customHandler.postDelayed(this, 0);

        }

    };
    private SaveVideoTask saveVideoTask = null;
    private String mediaFileName = null;

    public static Bitmap rotate(Bitmap bitmap, int degree) {
        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        Matrix mtx = new Matrix();
        mtx.setRotate(degree);

        return Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = DataBindingUtil.setContentView(this, R.layout.activity_custom_camera);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageMultipleImages event) {
//        DashboardActivity.isGoingToNextScreen = false;
        finish();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MediaPreviewItemDeletedEvent event) {
        try {
            if (null != event.position) {

                int index = event.position;
                pojoGalleries1.remove(index);
            }
        } catch (Exception e1) {
            e1.printStackTrace();
        }
    }

    private void init() {
        binding.executePendingBindings();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        runTimePermission = new RunTimePermission(this);
        runTimePermission.requestPermission(new String[]{Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        }, new RunTimePermission.RunTimePermissionListener() {

            @Override
            public void permissionGranted() {
                // First we need to check availability of play services
                initControls();

                identifyOrientationEvents();

                RealmDatabaseHandler realmDatabaseHandler= RealmDatabaseHandler.getRealmDatabaseHandler();
                String path = Environment.getExternalStorageDirectory() +"/" + "BaBBleU_"+realmDatabaseHandler.getUserInfo().getUserId() + File.separator;
                //create a folder to get image
                folder = new File(path);
                if (!folder.exists()) {
                    folder.mkdirs();
                }
                //capture image on callback
                captureImageCallback();
                //
                if (camera != null) {
                    Camera.CameraInfo info = new Camera.CameraInfo();
                    if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                        binding.imgFlashOnOff.setVisibility(View.GONE);
                    }
                }
            }

            @Override
            public void permissionDenied() {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        RealmDatabaseHandler realmDatabaseHandler = RealmDatabaseHandler.getRealmDatabaseHandler();
        PojoAppAuthentication authentication = realmDatabaseHandler.getAppAuthentication();
        if (MyApplication.isAppBackground && authentication != null && (authentication.getIsWholeAppLocked() == 1 && (authentication.getIsPasscodeEnabled() == 1 || authentication.getIsBiometricsEnabled() == 1))) {
            GlobalFunction.wholeAppTimeCheck(authentication, this, getSupportFragmentManager(), R.id.root_view_custom_camera);
        }
        MyApplication.isAppBackground = false;

        try {
            if (myOrientationEventListener != null)
                myOrientationEventListener.enable();
        } catch (Exception e1) {
            e1.printStackTrace();
        }

        init();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (runTimePermission != null) {
            runTimePermission.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

    }

    private void cancelSavePicTaskIfNeed() {
        if (savePicTask != null && savePicTask.getStatus() == AsyncTask.Status.RUNNING) {
            savePicTask.cancel(true);
        }
    }

    private void cancelSaveVideoTaskIfNeed() {
        if (saveVideoTask != null && saveVideoTask.getStatus() == AsyncTask.Status.RUNNING) {
            saveVideoTask.cancel(true);
        }
    }

    public String saveToSDCard(byte[] data, int rotation) throws IOException {
        String imagePath = "";
        try {

            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            options.inScaled = false;
            BitmapFactory.decodeByteArray(data, 0, data.length, options);

            DisplayMetrics metrics = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(metrics);

            int reqHeight = metrics.heightPixels;
            int reqWidth = metrics.widthPixels;

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

            options.inJustDecodeBounds = false;
            options.inScaled = false;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (rotation != 0) {
                Matrix mat = new Matrix();
                mat.postRotate(rotation);
                Bitmap bitmap1 = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), mat, true);
                if (bitmap != bitmap1) {
                    bitmap.recycle();
                }
                imagePath = getSavePhotoLocal(bitmap1);
                if (bitmap1 != null) {
                    bitmap1.recycle();
                }
            } else {
                imagePath = getSavePhotoLocal(bitmap);
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return imagePath;
    }

    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            if (width > height) {
                inSampleSize = Math.round((float) height / (float) reqHeight);
            } else {
                inSampleSize = Math.round((float) width / (float) reqWidth);
            }
        }
        return inSampleSize;
    }

    private String getSavePhotoLocal(Bitmap bitmap) {
        String path = "";
        try {
            OutputStream output;
            File file = new File(folder + File.separator + Constant.JPEG_FILE_PREFIX + System.currentTimeMillis() + Constant.JPEG_FILE_SUFFIX);
            try {
                output = new FileOutputStream(file);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
                output.flush();
                output.close();
                path = file.getAbsolutePath();
            } catch (Exception e) {
                e.printStackTrace();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return path;
    }

    private void captureImageCallback() {

        surfaceHolder = binding.imgSurface.getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_NORMAL);
        jpegCallback = (data, camera) -> {

            refreshCamera();
            cancelSavePicTaskIfNeed();
            savePicTask = new SavePicTask(data, getPhotoRotation());
            savePicTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);

        };
    }

    private void identifyOrientationEvents() {

        myOrientationEventListener = new OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            @Override
            public void onOrientationChanged(int iAngle) {

                final int iLookup[] = {0, 0, 0, 90, 90, 90, 90, 90, 90, 180, 180, 180, 180, 180, 180, 270, 270, 270, 270, 270, 270, 0, 0, 0}; // 15-degree increments
                if (iAngle != ORIENTATION_UNKNOWN) {

                    int iNewOrientation = iLookup[iAngle / 15];
                    if (iOrientation != iNewOrientation) {
                        iOrientation = iNewOrientation;
                        if (iOrientation == 0) {
                            mOrientation = 90;
                        } else if (iOrientation == 270) {
                            mOrientation = 0;
                        } else if (iOrientation == 90) {
                            mOrientation = 180;
                        }

                    }
                    mPhotoAngle = normalize(iAngle);
                }
            }
        };

        if (myOrientationEventListener.canDetectOrientation()) {
            myOrientationEventListener.enable();
        }

    }

    private void initControls() {
        mediaRecorder = new MediaRecorder();
        binding.textCounter.setVisibility(View.GONE);
        binding.imgSwipeCamera.setOnClickListener(this);
        activeCameraCapture();
        binding.imgFlashOnOff.setOnClickListener(this);
    }

    @Override
    public void onBackPressed() {
        cancelSavePicTaskIfNeed();
        MediaPreviewFragment mediaPreviewFragment = (MediaPreviewFragment) getSupportFragmentManager().findFragmentByTag(Constant.FRAG_MEDIA_PREVIEW);
        if (mediaPreviewFragment != null && mediaPreviewFragment.isVisible()) {
            getSupportFragmentManager().popBackStack();
        } else {
            PassCodeFragment passCodeFragment1 = (PassCodeFragment) getSupportFragmentManager().findFragmentByTag(getString(R.string.pass_code));
            if (passCodeFragment1 == null) {
                DashboardActivity.isGoingToNextScreen = false;
                isGoingNextActivity = true;
                finish();
            }
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.imgFlashOnOff:
                flashToggle();
                break;
            case R.id.imgSwipeCamera:
                camera.stopPreview();
                camera.release();
                if (flag == 0) {
                    binding.imgFlashOnOff.setVisibility(View.GONE);
                    flag = 1;
                } else {
                    binding.imgFlashOnOff.setVisibility(View.VISIBLE);
                    flag = 0;
                }
                surfaceCreated(surfaceHolder);
                break;
            default:
                break;
        }
    }

    //------------------SURFACE CREATED FIRST TIME--------------------//

    private void flashToggle() {

        if (flashType == 1) {

            flashType = 2;
        } else if (flashType == 2) {

            flashType = 3;
        } else if (flashType == 3) {

            flashType = 1;
        }
        refreshCamera();
    }

    private void captureImage() {
        camera.takePicture(null, null, jpegCallback);
        inActiveCameraCapture();
    }

    private void releaseMediaRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.reset();   // clear recorder configuration
            mediaRecorder.release(); // release the recorder object
            mediaRecorder = new MediaRecorder();
        }
    }

    public void refreshCamera() {
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        try {
            camera.stopPreview();
            Camera.Parameters param = camera.getParameters();
            List<Camera.Size> allSizes = param.getSupportedPictureSizes();
            Camera.Size size = allSizes.get(0); // get top size
//            int i = 0; i < allSizes.size(); i++
            for (Camera.Size sizeObj : allSizes) {
                if (sizeObj.width > size.width)
                    size = sizeObj;
            }
            //set max Picture Size
            param.setPictureSize(size.width, size.height);

            if (flag == 0) {
                if (flashType == 1) {
                    param.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                    binding.imgFlashOnOff.setImageResource(R.drawable.ic_flash_auto);
                } else if (flashType == 2) {
                    param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                    Camera.Parameters params = null;
                    if (camera != null) {
                        params = camera.getParameters();

                        if (params != null) {
                            List<String> supportedFlashModes = params.getSupportedFlashModes();

                            if (supportedFlashModes != null) {
                                if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                                    param.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                                } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                                    param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                                }
                            }
                        }
                    }
                    binding.imgFlashOnOff.setImageResource(R.drawable.ic_flash_on);
                } else if (flashType == 3) {
                    param.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    binding.imgFlashOnOff.setImageResource(R.drawable.ic_flash_off);
                }
            }


            refreshCameraPreview(param);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //------------------SURFACE OVERRIDE METHIDS END--------------------//

    private void refreshCameraPreview(Camera.Parameters param) {
        try {

            if (0 == flag) {

                param.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            param.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
            param.setZoom(0);
            camera.setParameters(param);
            setCameraDisplayOrientation(0);

            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setCameraDisplayOrientation(int cameraId) {

        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int rotation = getWindowManager().getDefaultDisplay().getRotation();

        if (Build.MODEL.equalsIgnoreCase("Nexus 6") && flag == 1) {
            rotation = Surface.ROTATION_180;
        }
        int degrees = 0;
        switch (rotation) {

            case Surface.ROTATION_0:

                degrees = 0;
                break;

            case Surface.ROTATION_90:

                degrees = 90;
                break;

            case Surface.ROTATION_180:

                degrees = 180;
                break;

            case Surface.ROTATION_270:

                degrees = 270;
                break;

        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {

            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror

        } else {
            result = (info.orientation - degrees + 360) % 360;

        }

        camera.setDisplayOrientation(result);
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0) {
        try {
            if (flag == 0) {
                camera = Camera.open(0);
            } else {
                camera = Camera.open(1);
            }
        } catch (RuntimeException e) {
            e.printStackTrace();
        }

        try {
            Camera.Parameters param;
            param = camera.getParameters();

            List<Camera.Size> allSizes = param.getSupportedPictureSizes();
            Camera.Size size = allSizes.get(0); // get top size
//            int i = 0; i < allSizes.size(); i++
            for (Camera.Size sizeObj : allSizes) {
                if (sizeObj.width > size.width)
                    size = sizeObj;
            }
            //set max Picture Size
            param.setPictureSize(size.width, size.height);

            if (0 == flag) {

                param.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            param.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);

            camera.setParameters(param);
            setCameraDisplayOrientation(0);
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();

            if (flashType == 1) {
                param.setFlashMode(Camera.Parameters.FLASH_MODE_AUTO);
                binding.imgFlashOnOff.setImageResource(R.drawable.ic_flash_auto);

            } else if (flashType == 2) {
                param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                Camera.Parameters params = null;
                if (camera != null) {
                    params = camera.getParameters();

                    if (params != null) {
                        List<String> supportedFlashModes = params.getSupportedFlashModes();

                        if (supportedFlashModes != null) {
                            if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                                param.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                            } else if (supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_ON)) {
                                param.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                            }
                        }
                    }
                }
                binding.imgFlashOnOff.setImageResource(R.drawable.ic_flash_on);

            } else if (flashType == 3) {
                param.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                binding.imgFlashOnOff.setImageResource(R.drawable.ic_flash_off);
            }


        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0) {
        try {
            camera.stopPreview();
            camera.release();
            camera = null;
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {
        refreshCamera();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (!isGoingNextActivity) {
            MyApplication.isAppBackground = true;
        }
        try {

            if (customHandler != null)
                customHandler.removeCallbacksAndMessages(null);

            releaseMediaRecorder();       // if you are using MediaRecorder, release it first

            if (myOrientationEventListener != null)
                myOrientationEventListener.enable();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void activeCameraCapture() {
        if (binding.imgCapture != null) {
            isVideoCapture = false;
            binding.imgCapture.setAlpha(1.0f);
            binding.ivVideoCapturePins.clearAnimation();
            binding.imgCapture.setOnLongClickListener((View v) -> {
                isVideoCapture = true;
                binding.hintTextView.setVisibility(View.INVISIBLE);
                binding.imgCapture.setVisibility(View.GONE);
                binding.ivVideoCapture.setVisibility(View.VISIBLE);
                binding.ivVideoCapturePins.setVisibility(View.VISIBLE);

                try {
                    if (prepareMediaRecorder()) {
                        myOrientationEventListener.disable();
                        mediaRecorder.start();
                        startTime = SystemClock.uptimeMillis();
                        customHandler.postDelayed(updateTimerThread, 0);
                    } else {
                        return false;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                binding.textCounter.setVisibility(View.VISIBLE);
                binding.imgSwipeCamera.setVisibility(View.GONE);

                scale_up_animation = AnimationUtils.loadAnimation(CustomCameraActivity.this, R.anim.scale_up_animation);
                fade_out_animation = AnimationUtils.loadAnimation(CustomCameraActivity.this, R.anim.fade_out);

                scale_up_animation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        binding.ivVideoCapturePins.startAnimation(fade_out_animation);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });

                fade_out_animation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {

                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {

                        binding.ivVideoCapturePins.startAnimation(scale_up_animation);
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {

                    }
                });

                binding.ivVideoCapturePins.startAnimation(scale_up_animation);

                binding.imgCapture.setOnTouchListener((v1, event) -> {

                    Camera.Parameters params = camera.getParameters();
                    if (event.getAction() == MotionEvent.ACTION_UP) {

                        binding.ivVideoCapturePins.clearAnimation();
                        fade_out_animation.setAnimationListener(null);
                        scale_up_animation.setAnimationListener(null);

                        binding.hintTextView.setVisibility(View.VISIBLE);
                        binding.imgCapture.setVisibility(View.VISIBLE);
                        binding.ivVideoCapture.setVisibility(View.GONE);
                        binding.ivVideoCapturePins.setVisibility(View.GONE);

                        cancelSaveVideoTaskIfNeed();

                        if (!binding.textCounter.getText().toString().equals("00:00")) {
                            saveVideoTask = new SaveVideoTask();
                            saveVideoTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR);
                        } else {
                            binding.imgCapture.setOnTouchListener(null);
                            binding.textCounter.setVisibility(View.GONE);
                            binding.imgSwipeCamera.setVisibility(View.VISIBLE);
                            try {
                                myOrientationEventListener.enable();
                                customHandler.removeCallbacksAndMessages(null);
                                mediaRecorder.stop();
                                releaseMediaRecorder();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }

                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        ViewConfiguration vc = ViewConfiguration.get(this);
                        touchSlop = vc.getScaledTouchSlop();
                        oldY = (int) event.getRawY();
                        return true;
                    } else if (event.getAction() == MotionEvent.ACTION_MOVE && params.isZoomSupported()) {
                        camera.cancelAutoFocus();
                        if (event.getY() < 1)
                            handleZoomVideo(event, params);
                    }
                    return true;

                });
                return true;
            });
            binding.imgCapture.setOnClickListener(v -> {

                if (isSpaceAvailable()) {
                    captureImage();
                } else {
                    Toast.makeText(CustomCameraActivity.this, getString(R.string.memory_is_not_available), Toast.LENGTH_SHORT).show();
                }
            });
        }

    }

    //--------------------------CHECK FOR MEMORY -----------------------------//

    public void onVideoSendDialog(final String videopath) {

        runOnUiThread(() -> {
            if (videopath != null) {
                File fileVideo = new File(videopath);
                long fileSizeInBytes = fileVideo.length();
                long fileSizeInKB = fileSizeInBytes / 1024;
                long fileSizeInMB = fileSizeInKB / 1024;

                Camera.Parameters params = camera.getParameters();
                params.setZoom(0);
                camera.setParameters(params);

                intentToMediaPreview(videopath.toString(), 2);
            }
        });
    }

    private void inActiveCameraCapture() {
        if (binding.imgCapture != null) {
            binding.imgCapture.setAlpha(0.5f);
            binding.imgCapture.setOnClickListener(null);
        }
    }

    public int getFreeSpacePercantage() {
        int percantage = (int) (freeMemory() * 100 / totalMemory());
        int modValue = percantage % 5;
        return percantage - modValue;
    }

    public double totalMemory() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        double sdAvailSize = (double) stat.getBlockCount() * (double) stat.getBlockSize();
        return sdAvailSize / 1073741824;
    }
    //-------------------END METHODS OF CHECK MEMORY--------------------------//

    public double freeMemory() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        double sdAvailSize = (double) stat.getAvailableBlocks() * (double) stat.getBlockSize();
        return sdAvailSize / 1073741824;
    }

    public boolean isSpaceAvailable() {
        if (getFreeSpacePercantage() >= 1) {
            return true;
        } else {
            return false;
        }
    }

    @SuppressLint("SimpleDateFormat")
    protected boolean prepareMediaRecorder() throws IOException {

        mediaRecorder = new MediaRecorder(); // Works well
        camera.stopPreview();
        camera.unlock();
        mediaRecorder.setCamera(camera);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);

        if (flag == 1) {
            mediaRecorder.setProfile(CamcorderProfile.get(1, CamcorderProfile.QUALITY_720P));
        } else {
            mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_720P));
        }
        mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());

        mediaRecorder.setOrientationHint(mOrientation);

        if (Build.MODEL.equalsIgnoreCase("Nexus 6") && flag == 1) {

            if (mOrientation == 90) {
                mediaRecorder.setOrientationHint(mOrientation);
            } else if (mOrientation == 180) {
                mediaRecorder.setOrientationHint(0);
            } else {
                mediaRecorder.setOrientationHint(180);
            }

        } else if (mOrientation == 90 && flag == 1) {
            mediaRecorder.setOrientationHint(270);
        } else if (flag == 1) {
            mediaRecorder.setOrientationHint(mOrientation);
        }
        mediaFileName = Constant.MP4_FILE_PREFIX + System.currentTimeMillis();
        mediaRecorder.setOutputFile(folder + File.separator + mediaFileName + Constant.MP4_FILE_SUFFIX); // Environment.getExternalStorageDirectory()

        try {
            mediaRecorder.prepare();
        } catch (Exception e) {
            releaseMediaRecorder();
            e.printStackTrace();
            return false;
        }
        return true;

    }

    private int normalize(int degrees) {
        if (degrees > 315 || degrees <= 45) {
            return 0;
        }

        if (degrees > 45 && degrees <= 135) {
            return 90;
        }

        if (degrees > 135 && degrees <= 225) {
            return 180;
        }

        if (degrees > 225 && degrees <= 315) {
            return 270;
        }

        throw new RuntimeException("Error....");
    }

    private int getPhotoRotation() {
        int rotation;
        int orientation = mPhotoAngle;

        Camera.CameraInfo info = new Camera.CameraInfo();
        if (flag == 0) {
            Camera.getCameraInfo(0, info);
        } else {
            Camera.getCameraInfo(1, info);
        }

        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - orientation + 360) % 360;
        } else {
            rotation = (info.orientation + orientation) % 360;
        }
        return rotation;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Get the pointer ID
        Camera.Parameters params = camera.getParameters();
        int action = event.getAction();


        if (event.getPointerCount() > 1) {
            // handle multi-touch events
            if (action == MotionEvent.ACTION_POINTER_DOWN) {
                mDist = getFingerSpacing(event);
            } else if (action == MotionEvent.ACTION_MOVE && params.isZoomSupported()) {
                camera.cancelAutoFocus();
                handleZoom(event, params);
            }
        } else {
            // handle single touch events
            if (isVideoCapture) {

            } else {
                if (action == MotionEvent.ACTION_UP) {
                    handleFocus(event, params);
                }
            }
        }
        return true;
    }

    private void handleZoom(MotionEvent event, Camera.Parameters params) {
        int maxZoom = params.getMaxZoom();
        int zoom = params.getZoom();
        float newDist = getFingerSpacing(event);
        if (newDist > mDist) {
            //zoom in
            if (zoom < maxZoom)
                zoom += 2;
        } else if (newDist < mDist) {
            //zoom out
            if (zoom > 0)
                zoom -= 2;
        }
        mDist = newDist;
        params.setZoom(zoom);
        camera.setParameters(params);
    }

    public void handleFocus(MotionEvent event, Camera.Parameters params) {
        int pointerId = event.getPointerId(0);
        List<String> supportedFocusModes = params.getSupportedFocusModes();
        if (supportedFocusModes != null && supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            camera.autoFocus((b, camera) -> {
                // currently set to auto-focus on single touch
            });
        }
    }

    /**
     * Determine the space between the first two fingers
     */
    private float getFingerSpacing(MotionEvent event) {
        // ...
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void handleZoomVideo(MotionEvent event, Camera.Parameters params) {
        int maxZoom = params.getMaxZoom();
        int zoom = params.getZoom();
        int newY = (int) event.getRawY();
        if (newY - oldY < -touchSlop) {
            //zoom in
            if (zoom < maxZoom)
                zoom += 1;
            oldY = newY + (touchSlop / 2);
        } else if (newY - oldY > touchSlop) {
            //zoom out
            if (zoom > 0)
                zoom -= 1;
            oldY = newY - (touchSlop / 2);
        }
        params.setZoom(zoom);
        camera.setParameters(params);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        if (event.str.equals(Constant.EVENT_FINISH_CUSTOM_CAMERA_ACTIVITY)) {
//            DashboardActivity.isGoingToNextScreen = false;
            finish();
            overridePendingTransition(0, 0);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!EventBus.getDefault().isRegistered(this)) {
            EventBus.getDefault().register(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    private void intentToMediaPreview(String filePath, Integer type) {

        PojoGallery pojoGallery = new PojoGallery();
        pojoGallery.setImage(filePath);
        pojoGallery.setType(type);
        pojoGalleries1.add(pojoGallery);
        String bundle = new Gson().toJson(pojoGalleries1);

        isGoingNextActivity = true;
        Intent intent = new Intent(CustomCameraActivity.this, MediaPreviewActivity.class);
        intent.putExtra(Constant.KEY_CHECK, "");
        intent.putExtra("data", bundle);
        intent.putExtra(Constant.KEY_CONVERSATION, getIntent().getStringExtra(Constant.KEY_CONVERSATION));
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        startActivity(intent);
    }

    //Save image
    @SuppressLint("StaticFieldLeak")
    private class SavePicTask extends AsyncTask<Void, Void, String> {
        private byte[] data;
        private int rotation = 0;

        public SavePicTask(byte[] data, int rotation) {
            this.data = data;
            this.rotation = rotation;
        }

        protected void onPreExecute() {
            activeCameraCapture();
        }

        @Override
        protected String doInBackground(Void... params) {

            try {
                return saveToSDCard(data, rotation);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {

            tempFile = new File(result);
            EventBus.getDefault().post(new MessageEventCaptureImageFile(tempFile.toString()));
            intentToMediaPreview(tempFile.toString(), 1);

        }
    }

    //save video
    private class SaveVideoTask extends AsyncTask<Void, Void, Void> {
        ProgressDialog progressDialog = null;

        @Override
        protected void onPreExecute() {
            progressDialog = new ProgressDialog(CustomCameraActivity.this);
            progressDialog.setMessage("Processing a video...");
            progressDialog.show();
            super.onPreExecute();
            binding.imgCapture.setOnTouchListener(null);
            binding.textCounter.setVisibility(View.GONE);
            binding.imgSwipeCamera.setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Void... params) {
            try {
                try {
                    myOrientationEventListener.enable();
                    customHandler.removeCallbacksAndMessages(null);
                    mediaRecorder.stop();
                    releaseMediaRecorder();
                    tempFile = new File(folder + File.separator + mediaFileName + Constant.MP4_FILE_SUFFIX);

                } catch (Exception e) {
                    e.printStackTrace();
                }

            } catch (Exception e) {
                e.printStackTrace();

            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            if (progressDialog != null) {
                if (progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }
            if (tempFile != null)
                onVideoSendDialog(tempFile.getAbsolutePath());
        }
    }
}
