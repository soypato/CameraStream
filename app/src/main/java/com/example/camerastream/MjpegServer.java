package com.example.camerastream;

import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MjpegServer {
    private final int port;
    private final Camera camera;
    private boolean isRunning;
    private ServerSocket serverSocket;
    private final ExecutorService executorService;
    private byte[] lastJpeg;

    public MjpegServer(int port, Camera camera) {
        this.port = port;
        this.camera = camera;
        this.executorService = Executors.newCachedThreadPool();
        setupCamera();
    }

    private void setupCamera() {
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewFormat(ImageFormat.NV21);
        camera.setParameters(parameters);

        camera.setPreviewCallback(new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                Camera.Parameters parameters = camera.getParameters();
                int width = parameters.getPreviewSize().width;
                int height = parameters.getPreviewSize().height;

                YuvImage yuvImage = new YuvImage(data, ImageFormat.NV21, width, height, null);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                yuvImage.compressToJpeg(new Rect(0, 0, width, height), 80, out);
                lastJpeg = out.toByteArray();
            }
        });
    }

    public void start() {
        isRunning = true;
        camera.startPreview();

        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    while (isRunning) {
                        Socket socket = serverSocket.accept();
                        handleClient(socket);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void handleClient(final Socket socket) {
        executorService.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    OutputStream output = socket.getOutputStream();

                    // Send HTTP header
                    String header = "HTTP/1.0 200 OK\r\n" +
                            "Connection: close\r\n" +
                            "Cache-Control: no-cache\r\n" +
                            "Content-Type: multipart/x-mixed-replace; boundary=FRAME\r\n\r\n";
                    output.write(header.getBytes());

                    // Send frames
                    while (isRunning && !socket.isClosed()) {
                        if (lastJpeg != null) {
                            String frameHeader = "--FRAME\r\n" +
                                    "Content-Type: image/jpeg\r\n" +
                                    "Content-Length: " + lastJpeg.length + "\r\n\r\n";
                            output.write(frameHeader.getBytes());
                            output.write(lastJpeg);
                            output.write("\r\n".getBytes());
                            output.flush();
                        }
                        Thread.sleep(50); // 20 fps
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    public void stop() {
        isRunning = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        executorService.shutdown();
    }
}
