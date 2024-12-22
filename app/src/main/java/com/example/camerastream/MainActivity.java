package com.example.camerastream;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements SurfaceHolder.Callback {
    private static final int DEFAULT_PORT = 7373;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private MjpegServer server;
    private Camera camera;
    private TextView urlTextView;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Button changePortButton;
    private Button copyUrlButton;
    private int port = DEFAULT_PORT;

    static String url;

    private String[] REQUIRED_PERMISSIONS = new String[]{
            Manifest.permission.CAMERA,
            Manifest.permission.INTERNET,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlTextView = findViewById(R.id.urlTextView);
        surfaceView = findViewById(R.id.surfaceView);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        changePortButton = findViewById(R.id.changePortButton);
        copyUrlButton = findViewById(R.id.copyUrlButton);

        changePortButton.setOnClickListener(v -> changePort());
        copyUrlButton.setOnClickListener(v -> copyUrlToClipboard());

        surfaceView.setOnTouchListener((v, event) -> {
            if (camera != null) {
                camera.autoFocus(null);
            }
            return false;
        });

        if (allPermissionsGranted()) {
            startStreaming();
        } else {
            requestPermissions();
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startStreaming();
            } else {
                Toast.makeText(this, "Permissions are required for the app to work", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void startStreaming() {
        camera = Camera.open();
        server = new MjpegServer(port, camera);
        server.start();

        String ipAddress = getLocalIpAddress();
        if (ipAddress != null) {
            url = "http://" + ipAddress + ":" + port;
            urlTextView.setText("🔗 En tu navegador, introduce:\n" + url);
        }
    }

    private String getLocalIpAddress() {
        try {
            List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
            for (NetworkInterface intf : interfaces) {
                List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
                for (InetAddress addr : addrs) {
                    if (!addr.isLoopbackAddress() && addr.getHostAddress().indexOf(':') == -1) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    private void changePort() {
        // Incrementar el puerto
        port++;
        
        // Detener el servidor si está en ejecución
        if (server != null) {
            server.stop();
            server = null;
        }
        
        // Reiniciar la cámara
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
        
        // Reiniciar el streaming
        startStreaming();
    }

    private void copyUrlToClipboard() {

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        // Crear un objeto ClipData con el texto a copiar
        ClipData clip = ClipData.newPlainText("URL", url); // Siempre tendrá el valor ya que esto es pos inicialización
        // Copiar el texto al portapapeles
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "URL copiada al portapapeles", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            if (camera != null) {
                camera.setPreviewDisplay(holder);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
            Camera.Size optimalSize = getOptimalPreviewSize(sizes, width, height);
            parameters.setPreviewSize(optimalSize.width, optimalSize.height);
            camera.setParameters(parameters);
            camera.startPreview();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        if (camera != null) {
            camera.stopPreview();
        }
    }

    private Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.1;
        double targetRatio = (double) w / h;

        if (sizes == null) return null;

        Camera.Size optimalSize = null;
        double minDiff = Double.MAX_VALUE;

        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) > ASPECT_TOLERANCE) continue;
            if (Math.abs(size.height - h) < minDiff) {
                optimalSize = size;
                minDiff = Math.abs(size.height - h);
            }
        }

        if (optimalSize == null) {
            minDiff = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                if (Math.abs(size.height - h) < minDiff) {
                    optimalSize = size;
                    minDiff = Math.abs(size.height - h);
                }
            }
        }
        return optimalSize;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (server != null) {
            server.stop();
        }
        if (camera != null) {
            camera.release();
        }
    }
}