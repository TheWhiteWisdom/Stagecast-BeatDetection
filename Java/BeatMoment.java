package testing.hyyrynen.fredrik.beatrecognitionmoment;

import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.graphics.Canvas;
import android.hardware.camera2.CameraManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Vibrator;
import android.support.annotation.RequiresApi;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.TextView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Random;
import java.util.Scanner;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
public class BeatMoment extends AppCompatActivity implements TimeAnimator.TimeListener {
    private class Flags {
        public volatile boolean beatFlag = false;
        public volatile boolean threadRunning = true;
        public volatile boolean flashLightEnabled = false;
        public volatile String lyricText = "";
    }

    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    final Flags flags = new Flags();
    Random rand = new Random();
    private BeatRecognizer beatRecognizer;
    private TimeAnimator timeAnimator = new TimeAnimator();

    private Context context;
    private CameraManager cameraManager;
    private String cameraID;

    private Vibrator vibrator;
    private TextView lyricTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = this.getBaseContext();

        setContentView(R.layout.activity_beat_moment);

        timeAnimator.setTimeListener(this);

        vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        surfaceView = (SurfaceView) findViewById(R.id.background_surfaceView);
        lyricTextView = (TextView) findViewById(R.id.lyric_text);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                timeAnimator.start();
                startWebSocketConnection();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        BeatRecognizer beatRecognizer = null;
        try {
            beatRecognizer = new BeatRecognizer();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        new Thread(beatRecognizer).start();
    }

    @Override
    protected void onResume(){
        super.onResume();
        camera = android.hardware.Camera.open();
    }

    @RequiresApi(api = Build.VERSION_CODES.ECLAIR)
    @Override
    protected void onStop(){
        super.onStop();
        flags.threadRunning=false;
        android.hardware.Camera.Parameters p = camera.getParameters();
        p.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(p);
        camera.stopPreview();
        flashOn = false;
    }

    @Override
    public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
        if(flags.beatFlag){
            flags.beatFlag = false;
            /*if(this.context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)){
                captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_SINGLE);
            }*/
            flashScreen(100);

        }
        updateGraphics();
    }

    float frames = 100;
    float currentFrame;
    float cooldown;

    private void flashScreen(int frames){
        this.frames = frames;
        currentFrame = frames;
        cooldown = 10;
        vibrator.vibrate(200);
    }

    private android.hardware.Camera camera;
    boolean flashOn = false;
    private void updateGraphics(){
        lyricTextView.setText(flags.lyricText);
        Canvas canvas = surfaceHolder.lockCanvas();
        if(canvas == null)
            return;
        int color = webColor;
        int r = (int)(((color & 0xFF0000)>>16) * (currentFrame/frames));
        int g = (int)(((color & 0x00FF00)>>8) * (currentFrame/frames));
        int b = (int)(((color & 0x0000FF)) * (currentFrame/frames));
        canvas.drawRGB(r,g,b);
        Paint paint = new Paint();
        paint.setColor(0xFFFFFF - color);
        surfaceHolder.unlockCanvasAndPost(canvas);

        if(flags.flashLightEnabled && !flashOn && currentFrame/frames >= 0.2f){
            flashOn();
        }
        else if(flashOn && currentFrame/frames < 0.2f){
            flashOff();
        }

        cooldown--;
        if(cooldown < 0)
            cooldown = 0;
        if(cooldown == 0) {
            if (currentFrame > 0)
                currentFrame -= 8;
        }
        if(currentFrame < 0)
            currentFrame = 0;
    }

    private void flashOn() {
        android.hardware.Camera.Parameters p = camera.getParameters();
        p.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_TORCH);
        camera.setParameters(p);
        camera.startPreview();
        flashOn = true;
    }
    private void flashOff() {
        android.hardware.Camera.Parameters p = camera.getParameters();
        p.setFlashMode(android.hardware.Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(p);
        camera.stopPreview();
        flashOn = false;
    }

    private URI uri;
    private WebSocketClient wsc;
    private HttpURLConnection urlConnection;
    private int webColor = 0xFFFFFF;

    private void startWebSocketConnection(){
        try {
            uri = new URI("ws://stagecast.se/api/events/team5test/ws?x-user-listener=1");
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        wsc = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                System.err.println("Status: " + serverHandshake.getHttpStatus());
            }

            @Override
            public void onMessage(String string) {
                try {
                    System.err.println("MESSAGE RECIEVED!");
                    System.err.println(string);
                    JSONObject jsonObject = new JSONObject(string);
                    JSONObject jsonObject1 = jsonObject.getJSONObject("msg");
                    String sColor = jsonObject1.getString("color");
                    webColor = Color.rgb(
                            Integer.valueOf( sColor.substring( 1, 3 ), 16 ),
                            Integer.valueOf( sColor.substring( 3, 5 ), 16 ),
                            Integer.valueOf( sColor.substring( 5, 7 ), 16 ) );
                    flags.flashLightEnabled = jsonObject1.getBoolean("flash");
                    flags.lyricText = jsonObject1.getString("text");

                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(int i, String s, boolean b) {}

            @Override
            public void onError(Exception e) {}
        };
        wsc.connect();
    }

    private int getColorFromSocket(){
        return webColor;
    }

    private class BeatRecognizer implements Runnable{
        private TimeAnimator timeAnimator = new TimeAnimator();
        private InputStream inputStream;

        private short[] buffer;
        private double[] amps;
        private AudioRecord recorder;

        private BeatRecognizer() throws MalformedURLException {
        }

        @Override
        public void run() {
            amps = new double[ampSize];
            int sampleRate = 8000;
            int channelConfig = AudioFormat.CHANNEL_CONFIGURATION_MONO;
            int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
            int minBufSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

            recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,sampleRate,channelConfig,audioFormat,minBufSize);
            recorder.startRecording();

            while(flags.threadRunning) {
                buffer = new short[minBufSize];
                double sum = 0;
                int readSize = recorder.read(buffer, 0, minBufSize);
                for (int i = 0; i < readSize; i++) {
                    sum += buffer [i] * buffer [i];
                }
                if (readSize > 0) {
                    final double amplitude = sum / readSize;
                    saveAmp(amplitude);
                    double avg = getAvgAmp();
                        //System.err.println("Amplitude comp avg: " + amplitude/avg);
                    if(amplitude/avg > 1.3)
                        flags.beatFlag = true;
                }
                /*
                if(rand.nextInt(700000) == 10)
                    flags.beatFlag = true;
                */
            }
            recorder.release();
        }

        private int ampTracker = 0;
        private int ampSize = 20;
        private void saveAmp(double amp){
            amps[ampTracker] = amp;
            ampTracker++;
            if(ampTracker>=ampSize){
                allFilled = true;
                ampTracker = 0;
            }
        }

        private boolean allFilled=false;
        private double getAvgAmp(){
            double avg = 0;
            if(!allFilled)
                return -1;
            for(double d : amps){
                avg += d/ampSize;
            }
            return avg;
        }
    }
}
