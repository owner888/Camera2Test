package com.agc.camera2test;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AndroidCamera2Api extends AppCompatActivity {
    private static final String TAG = "AndroidCameraApi";
    private Button takePictureButton;
    private TextureView textureView;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    private String cameraId;
    private List<String> cameraIdList = new ArrayList<>();
    protected CameraDevice cameraDevice;
    protected CameraCaptureSession cameraCaptureSessions;
    protected CaptureRequest captureRequest;
    protected CaptureRequest.Builder captureRequestBuilder;
    private Size mPreviewSize;
    private ImageReader imageReader;
    private File file;
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;
    private String mState = "PREVIEW";
    private boolean areWeFocused = false;
    int sensorOrientation = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_android_camera2_api);
        textureView = (TextureView) findViewById(R.id.texture);
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        takePictureButton = (Button) findViewById(R.id.btn_takepicture);
        assert takePictureButton != null;
        takePictureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            //open your camera here
            openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
            // Transform you image captured size according to the surface width and height
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    final CameraCaptureSession.CaptureCallback captureCallbackListener = new CameraCaptureSession.CaptureCallback() {
        private void process(CaptureResult result) {
            Log.d("captureCallback", "***************** Entering PROCESS");
            switch (mState) {
                case "PREVIEW": {
                    int afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (CaptureResult.CONTROL_AF_TRIGGER_START == afState) {
                        if (areWeFocused) {
                            // Log.d("statePreview", "***************** areWeFocused: " + areWeFocused);
                        }
                    }
                    areWeFocused = CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState;
                    // Log.d("statePreview", "***************** areWeFocused: " + areWeFocused);
                    mState = "";
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                        CaptureResult partialResult) {
            Log.d("captureProgressed", "*********** GOT HERE");
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                                       TotalCaptureResult result) {
            Log.d("captureCompleted", "*********** GOT HERE");
            super.onCaptureCompleted(session, request, result);
            Toast.makeText(AndroidCamera2Api.this, "Saved:" + file, Toast.LENGTH_SHORT)
                    .show();

            process(result);
            createCameraPreview();
        }
    };

    protected void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    protected void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    protected void takePicture() {
        if(null == cameraDevice) {
            Log.e(TAG, "cameraDevice is null");
            return;
        }
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraDevice.getId());

            Size[] jpegSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP).getOutputSizes(ImageFormat.JPEG);
            int width = 640;
            int height = 480;
            if (jpegSizes != null && 0 < jpegSizes.length) {
                width = jpegSizes[0].getWidth();
                height = jpegSizes[0].getHeight();
            }

            ImageReader reader = ImageReader.newInstance(width, height, ImageFormat.JPEG, 1);
            List<Surface> outputSurfaces = new ArrayList<Surface>(2);
            outputSurfaces.add(reader.getSurface());
            outputSurfaces.add(new Surface(textureView.getSurfaceTexture()));
            final CaptureRequest.Builder captureBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(reader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            // Orientation
            int rotation = getWindowManager().getDefaultDisplay().getRotation();
            Log.e(TAG, "rotation: " + rotation);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION,(sensorOrientation + ORIENTATIONS.get(rotation) + 270) % 360);

            final File file = new File(getExternalCacheDir().getAbsolutePath()+"/"+System.currentTimeMillis()+".jpg");
            ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireLatestImage();
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.capacity()];
                        buffer.get(bytes);
                        save(bytes);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }
                }
                private void save(byte[] bytes) throws IOException {
                    OutputStream output = null;
                    try {
                        output = new FileOutputStream(file);
                        output.write(bytes);
                    } finally {
                        if (null != output) {
                            output.close();
                        }
                    }
                }
            };
            reader.setOnImageAvailableListener(readerListener, mBackgroundHandler);
            final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                    insertMediaPic(AndroidCamera2Api.this, file.getAbsolutePath());
                    Toast.makeText(AndroidCamera2Api.this, "Saved Success", Toast.LENGTH_SHORT).show();
                    Log.d("captureProgressed", "*********** CAPTURE COMPLETED");
                    createCameraPreview();
                }
            };
            cameraDevice.createCaptureSession(outputSurfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    try {
                        session.capture(captureBuilder.build(), captureListener, mBackgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                }
            }, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void createCameraPreview() {
        try {
            Log.d("createPreview", "***************** Setting STATE to PREVIEW");
            mState = "PREVIEW";
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert texture != null;
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            Surface surface = new Surface(texture);
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(surface);
            cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback(){
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    //The camera is already closed
                    if (null == cameraDevice) {
                        return;
                    }
                    // When the session is ready, we start displaying the preview.
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(AndroidCamera2Api.this, "Configuration change", Toast.LENGTH_SHORT).show();
                }
            }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void initSpinner() {
        // 声明一个下拉列表的数组适配器
        ArrayAdapter<String> starAdapter = new ArrayAdapter<String>(this,
                R.layout.layout_camera_id, cameraIdList){
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView imageView = (TextView) view.findViewById(R.id.tv_camera_id);
                imageView.setText(cameraIdList.get(position));
                return view;
            }

            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView imageView = (TextView) view.findViewById(R.id.tv_camera_id);
                imageView.setText(cameraIdList.get(position));
                return view;
            }
        };
        // 从布局文件中获取名叫sp_dialog的下拉框
        Spinner sp_dialog = findViewById(R.id.camear_spinner);
        // 指定下拉列表的样式
        // 设置下拉框的标题。对话框模式才显示标题，下拉模式不显示标题
        sp_dialog.setPrompt("请选择镜头");
        sp_dialog.setAdapter(starAdapter); // 设置下拉框的数组适配器
        sp_dialog.setSelection(0); // 设置下拉框默认显示第一项
        // // 给下拉框设置选择监听器，一旦用户选中某一项，就触发监听器的onItemSelected方法
        sp_dialog.setOnItemSelectedListener(new MySelectedListener());
    }

    // 定义下拉列表需要显示的文本数组

    // 定义一个选择监听器，它实现了接口OnItemSelectedListener
    class MySelectedListener implements AdapterView.OnItemSelectedListener {
        // 选择事件的处理方法，其中arg2代表选择项的序号
        public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
            initCamera(cameraIdList.get(arg2));
        }

        // 未选择时的处理方法，通常无需关注
        public void onNothingSelected(AdapterView<?> arg0) {}
    }

    private void openCamera() {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "is camera open");
        cameraIdList.clear();
        scanAllCameras(manager);
        initCamera(cameraIdList.get(0));
        initSpinner();
        Log.e(TAG, "openCamera X");
    }

    private void scanAllCameras(CameraManager manager){
        // 如果ids为空, 重新从 0-128 查找
        if(!cameraIdList.isEmpty()){
            return;
        }
        String[] ids = new String[128];
        for (int i = 0; i < 128; i++) {
            ids[i] = String.valueOf(i);
        }
        for (String id: ids) {
            try {
                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(String.valueOf(id));
                cameraIdList.add(id);
            } catch (IllegalArgumentException ae) {

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void initCamera(String cameraId){
        if(cameraId.equals(this.cameraId)){
            return;
        }
        closeCamera();
        this.cameraId = cameraId;
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);

        try {
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            Integer integer = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if(integer != null){
                sensorOrientation = integer;
            }
            Log.d(TAG, "***************** SENSOR_ORIENTATION: " + integer);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            mPreviewSize = map.getOutputSizes(SurfaceTexture.class)[cameraIdList.indexOf(cameraId)];
            // Add permission for camera and let user grant the permission
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(AndroidCamera2Api.this, new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CAMERA_PERMISSION);
                return;
            }

            manager.openCamera(cameraId, stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    protected void updatePreview() {
        final CameraCaptureSession.CaptureCallback captureListener = new CameraCaptureSession.CaptureCallback() {
            private void process(CaptureResult result) {
                // Log.d("captureCallback", "***************** ENTERING Capture Callback PROCESS()");
                switch (mState) {
                    case "PREVIEW": {
                        int afState = result.get(CaptureResult.CONTROL_AF_STATE);
                        if (CaptureResult.CONTROL_AF_TRIGGER_START == afState) {
                            if (areWeFocused) {
                                // Log.d("statePreview", "***************** areWeFocused: " + areWeFocused);
                            }
                        }
                        areWeFocused = CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED == afState;
                        // Log.d("statePreview", "***************** areWeFocused: " + areWeFocused);
                        break;
                    }
                }
            }

            @Override
            public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request,
                                            CaptureResult partialResult) {
                super.onCaptureProgressed(session, request, partialResult);
                // Log.d("captureProgressed", "*********** CAPTURE PROGRESSED");
                process(partialResult);
            }
        };

        if(null == cameraDevice) {
            Log.e(TAG, "updatePreview error, return");
        }
        captureRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        try {
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(), captureListener, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (null != cameraDevice) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (null != imageReader) {
            imageReader.close();
            imageReader = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_DENIED) {
                // close the app
                Toast.makeText(AndroidCamera2Api.this, "Sorry!!!, you can't use this app without granting permission", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.e(TAG, "onResume");
        startBackgroundThread();
        if (textureView.isAvailable()) {
            openCamera();
        } else {
            textureView.setSurfaceTextureListener(textureListener);
        }
    }

    @Override
    protected void onPause() {
        Log.e(TAG, "onPause");
        //closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    /**
     * 插入相册 部分机型适配(区分手机系统版本 Android Q)
     *
     * @param context
     * @param filePath
     * @return
     */
    public static boolean insertMediaPic(Context context, String filePath) {
        if (TextUtils.isEmpty(filePath)) return false;
        File file = new File(filePath);
        // 判断android Q  (10 ) 版本
        if (Build.VERSION.SDK_INT >= 29) {
            if (file == null || !file.exists()) {
                return false;
            } else {
                try {
                    MediaStore.Images.Media.insertImage(context.getContentResolver(), file.getAbsolutePath(), file.getName(), null);
                    return true;
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
        } else {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DATA, file.getAbsolutePath());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.ImageColumns.DATE_TAKEN, System.currentTimeMillis() + "");
            context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.parse("file://" + file.getAbsolutePath())));
            return true;
        }

    }
}