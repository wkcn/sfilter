package android42.sfilter;

/**
 * Created by wkcn on 5/17/17.
 */

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.TextureView;
import android.view.Surface;

import java.io.IOException;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import android42.sfilter.filters.CameraFilter;
import android42.sfilter.filters.OriginalFilter;
import android42.sfilter.filters.AsciiArtFilter;
import android42.sfilter.filters.BasicDeformFilter;
import android42.sfilter.filters.EdgeDetectionFilter;
import android42.sfilter.filters.EMInterferenceFilter;
import android42.sfilter.filters.LegofiedFilter;
import android42.sfilter.filters.TileMosaicFilter;

import android.util.Log;
import android.view.View;

// 渲染器
public class SRender implements Runnable, TextureView.SurfaceTextureListener {
    private static final String TAG = "SRender";
    private static final int EGL_OPENGL_ES2_BIT = 4;
    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int DRAW_INTERVAL = 1000 / 30;

    private Thread renderThread;
    private Context context;
    private SurfaceTexture surfaceTexture;
    private int gwidth, gheight;
    private TextureView textureView;

	// EGL
    private EGLDisplay eglDisplay;
    private EGLSurface eglSurface;
    private EGLContext eglContext;
    private EGL10 egl10;

    private Camera camera = null;
	private int cameraID = 0;

    private SurfaceTexture cameraSurfaceTexture;
    private int cameraTextureId;
    private CameraFilter filter;
    private int filterID = 0;
    private SparseArray<CameraFilter> cameraFilterMap = new SparseArray<>();

	public SRender(Context context, TextureView textureView){
        this.context = context;
		this.textureView = textureView;
	}
	@Override
	public void run(){
		// 初始化OpenGL
		initGL(surfaceTexture);
		// 加载滤镜
		cameraFilterMap.append(0, new OriginalFilter(context));
		cameraFilterMap.append(1, new AsciiArtFilter(context));
		cameraFilterMap.append(2, new EdgeDetectionFilter(context));
		cameraFilterMap.append(3, new EMInterferenceFilter(context));
		cameraFilterMap.append(4, new LegofiedFilter(context));
		cameraFilterMap.append(5, new TileMosaicFilter(context));
		cameraFilterMap.append(6, new BasicDeformFilter(context));
		setFilter(filterID);
		cameraTextureId = SGLU.genTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
		cameraSurfaceTexture = new SurfaceTexture(cameraTextureId);
		// 相机视图
		initCamera();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (gwidth < 0 && gheight < 0)
					// 重新设置画布大小
                    GLES20.glViewport(0, 0, gwidth = -gwidth, gheight = -gheight);

                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

				// 更新camera Surface
                synchronized (this) {
                    cameraSurfaceTexture.updateTexImage();
                }

                // Draw camera preview
                filter.draw(cameraTextureId, gwidth, gheight);

                // Flush
                GLES20.glFlush();
                egl10.eglSwapBuffers(eglDisplay, eglSurface);

                Thread.sleep(DRAW_INTERVAL);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        cameraSurfaceTexture.release();
		// 退出GL
        GLES20.glDeleteTextures(1, new int[]{cameraTextureId}, 0);

	}

	// Surface Override
    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }
	@Override
	public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int _width, int _height){
		gwidth = -_width;
		gheight = -_height;
	}
    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
		if (camera != null){
			// 释放摄像机
			releaseCamera();
		}
		// 渲染线程
		if (renderThread != null & renderThread.isAlive()){
			renderThread.interrupt();
		}
		// 释放滤镜
		filter.release();
		return true;
	}
	@Override
	public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
		if (renderThread != null && renderThread.isAlive()) {
			renderThread.interrupt();
		}
		renderThread = new Thread(this);

		surfaceTexture = surface;
		gwidth = -width;
		gheight = -height;

		openCamera(cameraID);

		renderThread.start();
	} 

	public boolean openCamera(int id){
		if (camera == null){
			try{
				camera = Camera.open(id);
				cameraID = id;
				initCamera();
				return true;
			}catch (RuntimeException e){
				return false;
			}
		}
		return false;
	}

    private void initCamera(){
        Parameters parameters = camera.getParameters();
        if (parameters.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        // parameters.setRotation(90);
        camera.setParameters(parameters);

		try {
			camera.setPreviewTexture(cameraSurfaceTexture);
			camera.startPreview();
		} catch (IOException ioe) {
		}
	}

    public void handleZoom(boolean isZoomIn) {
        //Log.i(TAG, "handleZoom");
        if (camera == null) return;
        Camera.Parameters params = camera.getParameters();
        if (params == null) return;
        if (params.isZoomSupported()) {
            int maxZoom = params.getMaxZoom();
            int zoom = params.getZoom();
            if (isZoomIn && zoom < maxZoom) {
                zoom++;
            } else if (zoom > 0) {
                zoom--;
            }
            params.setZoom(zoom);
            camera.setParameters(params);
        } else {
            //LogUtils.i("zoom not supported");
        }
    }

	public void switchCamera(){
		Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
		final int numberOfCameras = Camera.getNumberOfCameras();
		cameraID = (cameraID + 1) % numberOfCameras;
		Camera.getCameraInfo(cameraID, cameraInfo);
		releaseCamera();
        //textureView.setVisibility(View.GONE);
		if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT){
			textureView.setRotation(180);
		}else{
			textureView.setRotation(0);
		}
        openCamera(cameraID);
		//textureView.setVisibility(View.VISIBLE);
	}

    public void releaseCamera(){
        if(camera != null){
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

	public void setFilter(int id){
		filterID = id; 
		filter = cameraFilterMap.get(id);
		if (filter != null){
			filter.onAttach();
		}
	}

    private void initGL(SurfaceTexture texture) {
        egl10 = (EGL10) EGLContext.getEGL();

        eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }

        int[] version = new int[2];
        if (!egl10.eglInitialize(eglDisplay, version)) {
            throw new RuntimeException("eglInitialize failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }

        int[] configsCount = new int[1];
        EGLConfig[] configs = new EGLConfig[1];
        int[] configSpec = {
                EGL10.EGL_RENDERABLE_TYPE,
                EGL_OPENGL_ES2_BIT,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE
        };

        EGLConfig eglConfig = null;
        if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
            throw new IllegalArgumentException("eglChooseConfig failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        } else if (configsCount[0] > 0) {
            eglConfig = configs[0];
        }
        if (eglConfig == null) {
            throw new RuntimeException("eglConfig not initialized");
        }

        int[] attrib_list = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE};
        eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
        eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, texture, null);

        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            int error = egl10.eglGetError();
            if (error == EGL10.EGL_BAD_NATIVE_WINDOW) {
                Log.e(TAG, "eglCreateWindowSurface returned EGL10.EGL_BAD_NATIVE_WINDOW");
                return;
            }
            throw new RuntimeException("eglCreateWindowSurface failed " +
                    android.opengl.GLUtils.getEGLErrorString(error));
        }

        if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed " +
                    android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError()));
        }
    }

}
