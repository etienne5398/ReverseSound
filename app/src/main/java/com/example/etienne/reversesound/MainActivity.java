/*
* The application needs to have the permission to write to external storage
* if the output file is written to the external storage, and also the
* permission to record audio. These permissions must be set in the
* application's AndroidManifest.xml file, with something like:
*
* <uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
* <uses-permission android:name="android.permission.RECORD_AUDIO" />
*
*/
package com.example.etienne.reversesound;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.support.v7.app.AppCompatActivity;
import android.widget.LinearLayout;
import android.os.Bundle;
import android.os.Environment;
import android.view.ViewGroup;
import android.widget.Button;
import android.view.View;
import android.content.Context;
import android.util.Log;
import android.media.MediaRecorder;
import android.media.MediaPlayer;

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
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    public static final int SAMPLING_RATE = 44100; //44100
    public static final int AUDIO_SOURCE = MediaRecorder.AudioSource.MIC;
    public static final int CHANNEL_IN_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(SAMPLING_RATE, CHANNEL_IN_CONFIG, AUDIO_FORMAT);
    public static final String AUDIO_RECORDING_FILE_NAME = "recording.raw";
    public static final String AUDIO_REVERSED_FILE_NAME = "reversedFile.raw";
    public static final int HEADER_SIZE = 0;
    private static final String ROOTPATH = Environment.getExternalStorageDirectory().getAbsolutePath();
    private static final String LOG_TAG = "AudioRecordTest";
    private static final String TAG = MainActivity.class.getCanonicalName() ;

    private RecordButton mRecordButton = null;
    private MediaRecorder mRecorder = null;
    private PlayButton   mPlayButton = null;
    private MediaPlayer   mPlayer = null;
    private DataInputStream dis;
    private AudioRecord recorder;
    private boolean recording ;
    private BufferedOutputStream os;
    private AudioTrack at;
    private Thread thread;

    private void onRecord(boolean start) {
        if (start) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void onPlay(boolean start) {
        if (start) {
            startPlaying();
        } else {
            stopPlaying();
        }
    }

    private void startPlaying() {
        try {
            playShortAudioFileViaAudioTrack(ROOTPATH +"/"+ AUDIO_REVERSED_FILE_NAME);
        } catch (IOException e) {
            Log.e(LOG_TAG, "play() failed");
        }
    }

    private void stopPlaying() {
        if (at != null) {
            at.stop();
            at.release();
        }
    }

    private void startRecording() {
        recording = true ;
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                byte audioData[] = new byte[BUFFER_SIZE];
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

                while (recording) {
                    int status = recorder.read(audioData, 0, audioData.length);
                    if (status == AudioRecord.ERROR_INVALID_OPERATION ||
                            status == AudioRecord.ERROR_BAD_VALUE) {
                        Log.e(TAG, "Error reading audio data!");
                        return;
                    }
                    try {
                        os.write(audioData, 0, audioData.length);
                    } catch (IOException e) {
                        Log.e(TAG, "Error saving recording ", e);
                        return;
                    }
                }
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();

    }

    private void stopRecording() {
        if(recorder != null) {
            recording = false;
            recorder.stop();
            recorder.release();
            thread = null;
            Log.v(TAG, "Recording done…");
            reverse();
        }

    }

    public void reverse(){
        File inputFile = new File(ROOTPATH+"/"+AUDIO_RECORDING_FILE_NAME);
        try {
            InputStream is = new FileInputStream(ROOTPATH+"/"+AUDIO_RECORDING_FILE_NAME);
            BufferedInputStream bis = new BufferedInputStream(is);
            dis = new DataInputStream(bis);
        } catch (Exception e) {
            e.printStackTrace();
        }

        int fileLength = (int)inputFile.length();
        byte[] buffer = new byte[fileLength];

        byte[] byteArray = new byte[fileLength + 1];
        Log.v("bytearray size = ", ""+byteArray.length);

        try {
            while(dis.read(buffer) != -1 ) {
                dis.read(buffer);
                Log.v("about to read buffer", "buffer");
                byteArray = buffer;
            }
            Log.v(" buffer size = ", ""+ buffer.length);
        } catch (IOException e) {
            e.printStackTrace();
        }
        byte[] tempArray = reverse16bit(byteArray);
        File revFile = new File(ROOTPATH, AUDIO_REVERSED_FILE_NAME);
        System.out.println("create Reverse :"+revFile.getAbsolutePath());
        Log.v("revfile path ", ""+revFile.getAbsolutePath());
        if(revFile.exists()){
            revFile.delete();
        }
        try {
            OutputStream os = new FileOutputStream(revFile);
            BufferedOutputStream bos = new BufferedOutputStream(os);
            DataOutputStream dos = new DataOutputStream(bos);
            Log.v("temparray size = ", ""+ tempArray.length);
            dos.write(tempArray);
            dos.flush();
            dos.close();
        } catch (Exception e) {
            e.printStackTrace();
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

    private void playShortAudioFileViaAudioTrack(final String filePath) throws IOException
    {
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
                        AudioFormat.CHANNEL_IN_DEFAULT,
                        AUDIO_FORMAT,
                        BUFFER_SIZE,
                        AudioTrack.MODE_STREAM);
                if (at!=null) {
                    at.play();
                    // Write the byte array to the track
                    at.write(byteData, 0, byteData.length);
                }
                else
                    Log.d("TCAudio", "audio track is not initialised ");
            }
        });
        thread.start();

    }

    class RecordButton extends Button {
        boolean mStartRecording = true;

        OnClickListener clicker = new OnClickListener() {
            public void onClick(View v) {
                if (mStartRecording) {
                    setText("Stop recording");
                } else {
                    setText("Start recording");
                }
                onRecord(mStartRecording);
                mStartRecording = !mStartRecording;
            }
        };

        public RecordButton(Context ctx) {
            super(ctx);
            setText("Start recording");
            setOnClickListener(clicker);
        }
    }

    class PlayButton extends Button {
        boolean mStartPlaying = true;

        OnClickListener clicker = new OnClickListener() {
            public void onClick(View v) {
                onPlay(mStartPlaying);
                if (mStartPlaying) {
                    setText("Stop playing");
                } else {
                    setText("Start playing");
                }
                mStartPlaying = !mStartPlaying;
            }
        };

        public PlayButton(Context ctx) {
            super(ctx);
            setText("Start playing");
            setOnClickListener(clicker);
        }
    }

    public MainActivity() {
        //mFileName = ROOTPATH + "/audiorecordtest.3gp";
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        LinearLayout ll = new LinearLayout(this);
        mRecordButton = new RecordButton(this);
        ll.addView(mRecordButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        mPlayButton = new PlayButton(this);
        ll.addView(mPlayButton,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        0));
        setContentView(ll);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }

        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
        }
    }
}
