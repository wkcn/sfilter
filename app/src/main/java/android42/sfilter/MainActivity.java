package android42.sfilter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.content.Intent;
import android.net.Uri;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.GestureDetector;
import android.view.WindowManager;
import android.view.View.OnLongClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;
import android.content.Context;
import android.util.AttributeSet;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;

import android.util.Log;

public class MainActivity extends AppCompatActivity implements OnLongClickListener{
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private static final String[] FILTER_NAMES = {"原图","Ascii码滤镜","边缘滤镜","电磁滤镜","乐高滤镜","贴片滤镜","水流滤镜"};
    private SRender renderer;
    private TextureView textureView;
    private int filterId = 0;
    private int filterIdOld = 0;
	private static final int filterNum = 7;
    private static boolean capture_btn = true;
    FloatingActionButton fab;
    private ImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        imageView = (ImageView)findViewById(R.id.imageView);
        //toolbar.setVisibility(View.INVISIBLE);
        imageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent albumIntent = new Intent(Intent.ACTION_PICK, null);
                String path = getSDPath() + "/android42/";
                File fi = new File(path);
                albumIntent.setDataAndType(Uri.fromFile(fi), "image/*");
                startActivity(albumIntent);
            }
        });

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (capture_btn)
                    caputre();
                capture_btn = true;
            }
        });
        fab.setOnLongClickListener(this);

        //ImageButton captureBtn = (ImageButton)findViewById(R.id.captureBtn);

        Log.i(TAG, "camera permission");
		// 申请相机权限
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA)) {
                Toast.makeText(this, "Camera access is required.", Toast.LENGTH_SHORT).show();

            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA},
                        REQUEST_CAMERA_PERMISSION);
            }

        } else {
            setupCameraPreviewView();
        }

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,  WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);

        textureView.setOnTouchListener(new View.OnTouchListener() {
                                           private float startX, startY, oldX, oldY, offsetX, offsetY;
                                           private float actionX, actionY;

                                           @Override
                                           public boolean onTouch(View v, MotionEvent event) {
                                               switch (event.getAction()) {
                                                   case MotionEvent.ACTION_DOWN:
                                                       startX = event.getX();
                                                       startY = event.getY();
                                                       oldX = startX;
                                                       oldY = startY;
                                                       offsetX = 0;
                                                       offsetY = 0;
                                                       break;
                                                   case MotionEvent.ACTION_MOVE:
                                                       offsetX = event.getX() - oldX;
                                                       offsetY = event.getY() - oldY;
                                                       if (Math.abs(offsetX) > Math.abs(offsetY)){
                                                           actionX = offsetX;
                                                           actionY = 0;
                                                       }else{
                                                           actionX = 0;
                                                           actionY = offsetY;
                                                       }
                                                       if (actionY < 0){
                                                           renderer.handleZoom(false);
                                                       }else if (actionY > 0){
                                                           renderer.handleZoom(true);
                                                       }
                                                       oldX = event.getX();
                                                       oldY = event.getY();
                                                       break;
                                                   case MotionEvent.ACTION_UP:
                                                       if (actionX > 0){
                                                           // Right
                                                           filterId = (filterId + 1) % filterNum;
                                                       }else if (actionX < 0){
                                                           // Left
                                                           filterId = (filterId - 1 + filterNum) % filterNum;
                                                       }
                                                       switchFilter();
                                                       break;


                                               }
                                               return true;
                                           }

                                       }
            );
    }

    public boolean onLongClick(View view) {//实现接口中的方法
        if(view == fab){//当按下的是按钮时
            /*
            Toast.makeText(
                    this,
                    "长时间按下了按钮",
                    Toast.LENGTH_SHORT
            ).show();//显示提示
            */
            renderer.switchCamera();
            Snackbar.make(view, "切换摄像头", Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            capture_btn = false;
        }
        return false;
    }

    // 申请权限
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CAMERA_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    setupCameraPreviewView();
                }
            }
        }
    }

	// 设置相机视图
    void setupCameraPreviewView() {
        textureView = (TextureView) findViewById(R.id.textureView);
        renderer = new SRender(this, textureView);
        assert textureView != null;
        textureView.setSurfaceTextureListener(renderer);
        // Show original frame when touch the view
        textureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        renderer.setFilter(0);
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        renderer.setFilter(filterId);
                        break;
                }
                return true;
            }
        });

        textureView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                renderer.onSurfaceTextureSizeChanged(null, v.getWidth(), v.getHeight());
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

	public void switchFilter(){
        renderer.setFilter(filterId);
        if (filterId != filterIdOld){
            Snackbar.make(textureView, "切换为：" + FILTER_NAMES[filterId], Snackbar.LENGTH_LONG)
                    .setAction("Action", null).show();
            filterIdOld = filterId;
        }
	}

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        //renderer.handleZoom(true);
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.select_filter0){
			filterId = 0;
        }else if (id == R.id.select_filter1){
			filterId = 1;
        }else if (id == R.id.select_filter2){
			filterId = 2;
        }else if (id == R.id.select_filter3){
			filterId = 3;
        }else if (id == R.id.select_filter4){
			filterId = 4;
        }else if (id == R.id.select_filter5){
			filterId = 5;
        }else if (id == R.id.select_filter6){
			filterId = 6;
        }
		switchFilter();

        return super.onOptionsItemSelected(item);
    }

    public void onCaptureBtn(View view){
        caputre();
    }

    private Bitmap scaleBitmap(Bitmap origin, int newWidth, int newHeight) {
        if (origin == null) {
            return null;
        }
        int height = origin.getHeight();
        int width = origin.getWidth();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        Matrix matrix = new Matrix();
        matrix.postScale(scaleWidth, scaleHeight);// 使用后乘
        Bitmap newBM = Bitmap.createBitmap(origin, 0, 0, width, height, matrix, false);
        if (!origin.isRecycled()) {
            origin.recycle();
        }
        return newBM;
    }

    private boolean caputre() {
        String mPath = genSaveFileName("android42_", ".png");
        Toast.makeText(this, "图片已保存到：" + mPath, Toast.LENGTH_SHORT).show();
        File imageFile = new File(mPath);
        if (imageFile.exists()) {
            imageFile.delete();
        }

        // create bitmap screen capture
        Bitmap bitmap = textureView.getBitmap();

        OutputStream outputStream = null;

        try {
            outputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream);
            outputStream.flush();
            outputStream.close();
            //Log.i(TAG, "SAVED OK");

            Bitmap ibm = scaleBitmap(bitmap,imageView.getWidth(),imageView.getHeight());
            //Log.i(TAG, "OOO");
            imageView.setImageBitmap(ibm);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    public String getSDPath(){
        File sdDir = null;
        boolean sdCardExist = Environment.getExternalStorageState()
                .equals(android.os.Environment.MEDIA_MOUNTED); // 判断sd卡是否存在
        if (sdCardExist)
        {
            sdDir = Environment.getExternalStorageDirectory();// 获取跟目录
            return sdDir.toString();
        }

        return null;
    }

    private String genSaveFileName(String prefix, String suffix) {
        Date date = new Date();
        SimpleDateFormat dateformat1 = new SimpleDateFormat("yyyyMMdd_hhmmss");
        String timeString = dateformat1.format(date);
        //String externalPath = Environment.getExternalStorageDirectory().toString();
        String path = getSDPath();
        File dir = new File(path + "/android42");
        if (!dir.exists()) {
            dir.mkdir();
        }
        return dir + "/" + prefix + timeString + suffix;
    }



}
