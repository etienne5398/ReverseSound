package com.reversesong;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import android.support.v4.app.ActivityCompat;
import android.content.pm.PackageManager;
import android.support.v4.content.ContextCompat;

import com.reversesong.R;

/**
 * Created by Etienne on 26/12/2016.
 */

public class RecordActivity extends AppCompatActivity {

    private static final String TAG = RecordActivity.class.getCanonicalName() ;
    public static final int SAMPLING_RATE = 44100; //44100
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    public static final int CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_DEFAULT;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_IN_CONFIG, AUDIO_FORMAT);
    public static final String AUDIO_RECORDING_FILE_NAME = "recording.raw";
    public static final String AUDIO_REVERSED_FILE_NAME = "reversedFile.raw";
    private static final String ROOTPATH = Environment.getExternalStorageDirectory().getAbsolutePath();
    private int threshold = 1500; //1500

    private DataInputStream dis;
    private AudioRecord recorder;
    private boolean recording ;
    private BufferedOutputStream os;
    private AudioTrack at;
    private Thread thread;
    private long silenceStart;
    private boolean soundOccured;
    private boolean silenceStarted = false;
    private boolean autoRecord = false;

    private int delay = 1 ;
    Button buttonStartRecord, buttonStopRecord, buttonStartPlaying,
    buttonStopPlaying;
    Switch switch_button;
    SeekBar delayControl, thresholdControl;
    public static final int RequestPermissionCode = 1;
    private TextView delayLabel;
    private TextView thresholdLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.record_activity_main);

        buttonStartRecord = (Button) findViewById(R.id.button);
        buttonStopRecord = (Button) findViewById(R.id.button2);
        buttonStartPlaying = (Button) findViewById(R.id.button3);
        buttonStopPlaying = (Button)findViewById(R.id.button4);
        switch_button = (Switch) findViewById(R.id.switch_button);
        delayControl = (SeekBar) findViewById(R.id.seekbar);
        delayLabel = (TextView) findViewById(R.id.delay_text);
        thresholdControl = (SeekBar) findViewById(R.id.seekbar_threshold);
        thresholdLabel = (TextView) findViewById(R.id.threshold_text);

        buttonStopRecord.setEnabled(false);
        buttonStartPlaying.setEnabled(false);
        buttonStopPlaying.setEnabled(false);

        delayControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                delay = progress + 1;
                delayLabel.setText(String.valueOf(delay) + (delay>1?" seconds": " second"));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        thresholdControl.setProgress(1500);
        thresholdControl.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                threshold = progress + 50;
                thresholdLabel.setText(String.valueOf(threshold));
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO Auto-generated method stub
            }

            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });


        switch_button.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {

            @Override
            public void onCheckedChanged(CompoundButton buttonView,
                                         boolean isChecked) {
                autoRecord = isChecked ;
            }
        });

        buttonStartRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(checkPermission()) {
                    buttonStartRecord.setEnabled(false);
                    buttonStopRecord.setEnabled(true);
                    buttonStartPlaying.setEnabled(false);
                    startRecording();
                    Toast.makeText(RecordActivity.this, "Recording started",
                            Toast.LENGTH_SHORT).show();
                } else {
                    requestPermission();
                }

            }
        });

        buttonStopRecord.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopRecord();
            }
        });

        buttonStartPlaying.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) throws IllegalArgumentException,
                    SecurityException, IllegalStateException {
                try {
                    playShortAudioFileViaAudioTrack(ROOTPATH +"/"+ AUDIO_REVERSED_FILE_NAME);
                } catch (IOException e) {
                    Log.e(TAG, "play() failed");
                }
                Toast.makeText(RecordActivity.this, "Recording Playing",
                        Toast.LENGTH_SHORT).show();
            }
        });

        buttonStopPlaying.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                buttonStopRecord.setEnabled(false);
                buttonStopPlaying.setEnabled(false);
                buttonStartRecord.setEnabled(true);
                buttonStartPlaying.setEnabled(true);

                if (at != null) {
                    at.stop();
                    at.release();
                }
            }
        });
    }

    private void stopRecord() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                buttonStopRecord.setEnabled(false);
                buttonStopPlaying.setEnabled(false);
            }
        });
        boolean soundReverted= false;
        if(recorder != null) {
            recording = false;
            recorder.stop();
            recorder.release();
            thread = null;
            Log.v(TAG, "Recording done…");
            soundReverted = reverse();
        }
        final boolean finalSoundReverted = soundReverted;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                buttonStartPlaying.setEnabled(finalSoundReverted);
                buttonStartRecord.setEnabled(true);
            }
        });
    }

    private void requestPermission() {
        ActivityCompat.requestPermissions(RecordActivity.this, new
                String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}, RequestPermissionCode);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RequestPermissionCode:
                if (grantResults.length> 0) {
                    boolean StoragePermission = grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED;
                    boolean RecordPermission = grantResults[1] ==
                            PackageManager.PERMISSION_GRANTED;

                    if (StoragePermission && RecordPermission) {
                        Toast.makeText(RecordActivity.this, "Permission Granted",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(RecordActivity.this,"Permission Denied",Toast.LENGTH_SHORT).show();
                    }
                }
                break;
        }
    }

    public boolean checkPermission() {
        if(Build.VERSION.SDK_INT >= 23 ) {
            int result = ContextCompat.checkSelfPermission(getApplicationContext(),
                    WRITE_EXTERNAL_STORAGE);
            int result1 = ContextCompat.checkSelfPermission(getApplicationContext(),
                    RECORD_AUDIO);
            return result == PackageManager.PERMISSION_GRANTED &&
                    result1 == PackageManager.PERMISSION_GRANTED;
        }else{
            return true;
        }
    }

    private void playShortAudioFileViaAudioTrack(final String filePath) throws IOException
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                buttonStopRecord.setEnabled(false);
                buttonStartRecord.setEnabled(false);
                buttonStartPlaying.setEnabled(false);
                buttonStopPlaying.setEnabled(true);
            }
        });
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                // We keep temporarily filePath globally as we have only two sample sounds now..
                if (filePath==null)
                    return;

                //Reading the file..
                byte[] byteData = null;
                File file = null;
                file = new File(filePath); // for ex. path= "/sdcard/samplesound.pcm" or "/sdcard/samplesound.wav"
                byteData = new byte[(int) file.length()];
                FileInputStream in = null;
                try {
                    in = new FileInputStream( file );
                    try {
                        in.read( byteData );
                        in.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                // Set and push to audio track..
                at = new AudioTrack(
                        AudioManager.STREAM_MUSIC,
                        SAMPLING_RATE,
                        CHANNEL_IN_CONFIG,
                        AUDIO_FORMAT,
                        BUFFER_SIZE,
                        AudioTrack.MODE_STREAM);
                at.play();
                // Write the byte array to the track
                at.write(byteData, 0, byteData.length);
                at.setNotificationMarkerPosition(byteData.length/2); // div par 2 car 16bit
                at.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                    @Override
                    public void onMarkerReached(AudioTrack audioTrack) {
                        buttonStopRecord.setEnabled(false);
                        buttonStopPlaying.setEnabled(false);
                        buttonStartPlaying.setEnabled(true);
                        buttonStartRecord.setEnabled(true);

                        if(autoRecord){
                            buttonStartRecord.setEnabled(false);
                            buttonStopRecord.setEnabled(true);
                            buttonStartPlaying.setEnabled(false);
                            startRecording();
                        }
                    }
                    @Override
                    public void onPeriodicNotification(AudioTrack audioTrack) {
                        //rien
                    }
                });
            }
        });
        thread.start();

    }

    public boolean reverse(){
        File inputFile = new File(ROOTPATH+"/"+AUDIO_RECORDING_FILE_NAME);
        File revFile = new File(ROOTPATH, AUDIO_REVERSED_FILE_NAME);
        if (revFile.exists()) {
            revFile.delete();
        }
        if(inputFile.length() > 0) {
            try {
                InputStream is = new FileInputStream(ROOTPATH + "/" + AUDIO_RECORDING_FILE_NAME);
                BufferedInputStream bis = new BufferedInputStream(is);
                dis = new DataInputStream(bis);
            } catch (Exception e) {
                e.printStackTrace();
            }
            int fileLength = (int) inputFile.length();
            byte[] buffer = new byte[fileLength];

            byte[] byteArray = new byte[fileLength + 1];
            Log.v("bytearray size = ", "" + byteArray.length);

            try {
                while (dis.read(buffer) != -1) {
                    dis.read(buffer);
                    Log.v("about to read buffer", "buffer");
                    byteArray = buffer;
                }
                Log.v(" buffer size = ", "" + buffer.length);
            } catch (IOException e) {
                e.printStackTrace();
            }
            byte[] tempArray = reverse16bit(byteArray);

            System.out.println("create Reverse :" + revFile.getAbsolutePath());
            Log.v("revfile path ", "" + revFile.getAbsolutePath());
            if (revFile.exists()) {
                revFile.delete();
            }
            try {
                OutputStream os = new FileOutputStream(revFile);
                BufferedOutputStream bos = new BufferedOutputStream(os);
                DataOutputStream dos = new DataOutputStream(bos);
                Log.v("temparray size = ", "" + tempArray.length);
                dos.write(tempArray);
                dos.flush();
                dos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }else{
            Toast.makeText(RecordActivity.this,"Aucun son capturé", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    public byte[] reverse16bit(byte[] array) {
        byte[] reversedArray = new byte[array.length];
        if (array == null) {
            return null;
        }
        int i = 0 ;
        int j = array.length - 1;
        while(i < array.length){
            reversedArray[j-1] = array[i];
            reversedArray[j] = array[i+1];
            j-=2;
            i+=2;
        }
        return reversedArray;
    }

    private void startRecording() {
        recording = true ;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                byte[] audioData = new byte[BUFFER_SIZE];
                recorder = new AudioRecord(
                        AUDIO_SOURCE,
                        SAMPLING_RATE,
                        CHANNEL_IN_CONFIG,
                        AUDIO_FORMAT,
                        BUFFER_SIZE);

                String filePath = ROOTPATH
                        + "/" + AUDIO_RECORDING_FILE_NAME;
                os = null;
                try {
                    os = new BufferedOutputStream(new FileOutputStream(filePath));
                } catch (FileNotFoundException e) {
                    Log.e(TAG, "File not found for recording ", e);
                }
                recorder.startRecording();
                silenceStarted = false ;
                soundOccured = false;
                while (recording) {
                    int status = recorder.read(audioData, 0, audioData.length);
                    if (status == AudioRecord.ERROR_INVALID_OPERATION ||
                            status == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "Error reading audio data!");
                        return;
                    }

                    if(searchThreshold(byteToShort(audioData), threshold) == -1 ){
                        if(!silenceStarted) {
                            silenceStarted = true;
                            silenceStart = System.currentTimeMillis();
                        }
                        long timeElapsed = System.currentTimeMillis() - silenceStart;
                        System.out.println((timeElapsed)/1000);
                        if( timeElapsed > delay * 1000 && soundOccured){
                            silenceStarted = false ;
                            stopRecord();
                            try {
                                playShortAudioFileViaAudioTrack(ROOTPATH +"/"+ AUDIO_REVERSED_FILE_NAME);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(RecordActivity.this, "Recording playing",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });

                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                            return;
                        }
                    }else{
                        silenceStarted = false ;
                        soundOccured = true;
                    }
                    try {
                        if(soundOccured) {
                            os.write(audioData, 0, audioData.length);
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Error saving recording ", e);
                        return;
                    }
                }
                soundOccured = false;
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();

    }

    short [] byteToShort(byte [] input) {
        int short_index = 0, byte_index = 0;
        short [] shortArray = new short[input.length / 2 ];
        for(; byte_index < input.length; ){
            ByteBuffer bb = ByteBuffer.allocate(2);
            bb.order(ByteOrder.LITTLE_ENDIAN);
            bb.put(input[byte_index]);
            bb.put(input[byte_index + 1]);
            shortArray[short_index] = bb.getShort(0);
            byte_index +=2;
            ++short_index;
        }
        return shortArray;
    }

    byte [] shortToByte(short [] input, int elements) {
        int short_index, byte_index;
        int iterations = elements; //input.length;
        byte [] buffer = new byte[iterations * 2];
        short_index = 0; byte_index = 0;
        for(/*NOP*/; short_index != iterations; /*NOP*/)
        {
            buffer[byte_index]     = (byte) (input[short_index] & 0x00FF);
            buffer[byte_index + 1] = (byte) ((input[short_index] & 0xFF00) >> 8);
            ++short_index;
            byte_index += 2;
        }

        return buffer;
    }

    int searchThreshold(short[]arr, int thr){
        int arrLen=arr.length;
        System.out.print("\n");
        for (int peakIndex=0; peakIndex<arrLen; peakIndex++){
            System.out.print(arr[peakIndex]+" ");
            if ((arr[peakIndex] >= thr) || (arr[peakIndex] <= -thr)){

                return 1; //bruit
            }
        }
        System.out.println("\nSEUIL: "+thr+ "\n\nSILENCE\n");
        return -1; //silence
    }

}

