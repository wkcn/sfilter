package android42.sfilter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Environment;
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
import android.widget.ImageButton;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import android.util.Log;


public class MainActivity extends AppCompatActivity{
    private static final String TAG = "MainActivity";
    private static final int REQUEST_CAMERA_PERMISSION = 101;
    private SRender renderer;
    private TextureView textureView;
	private GestureDetector gestureDetector;
    private int filterId = 0;
	private static final int filterNum = 7;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //toolbar.setVisibility(View.INVISIBLE);



        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
				renderer.switchCamera();
                Snackbar.make(view, "切换摄像头", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
            }
        });

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

        gestureDetector = new GestureDetector(textureView.getContext(), myGestureListener);
    }

	// 手势识别
	private GestureDetector.OnGestureListener myGestureListener = new GestureDetector.SimpleOnGestureListener(){
		@Override
		public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY){
			Log.i(TAG, "onFling");
			float x = e2.getX() - e1.getX();
			float y = e2.getY() - e1.getY();
			if (x > 0){
				// Right
				filterId = (filterId + 1) % filterNum;
			}else if (x < 0){
				// Left
				filterId = (filterId - 1 + filterNum) % filterNum;
			}
			switchFilter();
			return true;
		}
	};

	public boolean onTouchEvent(MotionEvent event){
		return gestureDetector.onTouchEvent(event);
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

    private boolean caputre() {
        String mPath = genSaveFileName(getTitle().toString() + "_", ".png");
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

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private String genSaveFileName(String prefix, String suffix) {
        Date date = new Date();
        SimpleDateFormat dateformat1 = new SimpleDateFormat("yyyyMMdd_hhmmss");
        String timeString = dateformat1.format(date);
        String externalPath = Environment.getExternalStorageDirectory().toString();
        return externalPath + "/" + prefix + timeString + suffix;
    }



}
