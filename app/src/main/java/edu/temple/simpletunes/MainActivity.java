package edu.temple.simpletunes;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;

import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    private final String REPEAT_STATE_KEY = "repeatState";
    private final String SHUFFLE_STATE_KEY = "shuffleState";
    private final String PLAY_STATE_KEY = "playState";
    private ActivityResultLauncher<Intent> mActivityResultLauncher;
    private ActivityResultLauncher<Intent> folderLauncher;
    public static final String TRACK_FILE_NAME = "trackFileName";
    private static final int STORAGE_PERMISSION_CODE = 101;
    private Intent mServiceIntent;
    private int repeatState = 0;
    private boolean shuffleState = false;
    private boolean playState = false;
    private RecyclerView recyclerView;
    private PlaylistAdapter playlistAdapter;
    private RecyclerView.LayoutManager layoutManager;

    // Variables and initialization of MediaPlayerService service connection.
    // TODO: use functions available through mAudioControlsBinder to control media.
    // mMediaControlsBinder.play, pause, resume, stop, isPlaying.
    // resume and pause does not check if track is playing or already paused.
    private boolean isConnected = false;
    private MediaPlayerService.ControlsBinder mAudioControlsBinder;
    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            isConnected = true;
            mAudioControlsBinder = (MediaPlayerService.ControlsBinder) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isConnected = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Bind the MediaPlayerService to the MainActivity.
        mServiceIntent = new Intent(this, MediaPlayerService.class);
        bindService(mServiceIntent, mServiceConnection, Context.BIND_AUTO_CREATE);

        mActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode() == RESULT_OK && result.getData() == null){
                Log.d(TAG, "onActivityResult: data was null");
            }else{
                if (result.getData() != null) {
                    Uri audioFile = result.getData().getData();
                    Log.d(TAG, "onActivityResult: got URI " + audioFile.toString());
                    mediaPlayerPlay(audioFile);
                    ImageButton playPauseButton = findViewById(R.id.playPauseButton);
                    playPauseButton.setImageResource(R.drawable.ic_baseline_pause_circle_outline_72);
                }
            }
        });
        folderLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if(result.getResultCode() == RESULT_OK && result.getData() == null){
                Log.d(TAG, "onActivityResult: data was null");
            }else{
                if (result.getData() != null) {
                    Uri uri = result.getData().getData();
                    Log.d(TAG, "onActivityResult: got URI " + uri.toString());
                    DocumentFile directory = DocumentFile.fromTreeUri(MainActivity.this, uri);
                    if(directory == null){
                        Log.d(TAG, "onActivityResult: got empty directory");
                    }else{
                        DocumentFile[] contents = directory.listFiles();
                        Log.d(TAG, "onCreate: Folder passed to MediaPlayerService. Items in folder: " + contents.length);
                        mediaPlayerPlayFolder(contents);
                        ImageButton playPauseButton = findViewById(R.id.playPauseButton);
                        playPauseButton.setImageResource(R.drawable.ic_baseline_pause_circle_outline_72);
                    }
                }
            }
        });
    }

    public boolean checkPermission(String permission, int requestCode) {
        if (ContextCompat.checkSelfPermission(MainActivity.this, permission) == PackageManager.PERMISSION_DENIED) {
            // Requesting the permission
            ActivityCompat.requestPermissions(MainActivity.this, new String[] { permission }, requestCode);
            return false;
        }else{
            Log.d(TAG, "checkPermission: permission granted");
            return true;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Intent i = new Intent();
                i.setAction(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("audio/mpeg");
                mActivityResultLauncher.launch(i);
            }
        }
    }

    @Override
    protected void onResume() {
        // TODO **************** Remove test code *****************
        recyclerView = findViewById(R.id.recyclerView);
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        String[] trackNames = new String[20];
        for (int i = 0; i < trackNames.length; i++) {
            trackNames[i] = "Track Name " + (i+1) ;
        }

        PlaylistAdapter playlistAdapter = new PlaylistAdapter(trackNames);

        recyclerView.setAdapter(playlistAdapter);

        ImageButton browserButton = findViewById(R.id.browserButton);
        browserButton.setOnClickListener(view -> {
            if(checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE, STORAGE_PERMISSION_CODE)){
                Intent i = new Intent();
                i.setAction(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("audio/mpeg");
                mActivityResultLauncher.launch(i);
            }
        });
        ImageButton folderButton = findViewById(R.id.libraryButton);
        folderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // https://www.programcreek.com/java-api-examples/?class=android.content.Intent&method=ACTION_OPEN_DOCUMENT_TREE
                Intent i = new Intent();
                i.setAction(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                        | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                        | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                folderLauncher.launch(i);
            }
        });

        ImageButton playPauseButton = findViewById(R.id.playPauseButton);
        playPauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean status = mediaPlayerPauseOrStart();
                updatePlayButton(status);
            }
        });
        ImageButton skipNextButton = findViewById(R.id.skipNextButton);
        skipNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayerNext();
            }
        });

        ImageButton skipPrevButton = findViewById(R.id.skipPrevButton);
        skipPrevButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mediaPlayerPrev();
            }
        });
        ImageButton repeatButton = findViewById(R.id.repeatButton);
        repeatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                int status = mediaPlayerRepeat();
                updateRepeatButton(status);
            }
        });
        ImageButton shuffleButton = findViewById(R.id.shuffleButton);
        shuffleButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean status = mediaPlayerShuffle();
                updateShuffleButton(status);
            }
        });
        super.onResume();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        repeatState = savedInstanceState.getInt(REPEAT_STATE_KEY, 0);
        updateRepeatButton(repeatState);
        shuffleState = savedInstanceState.getBoolean(SHUFFLE_STATE_KEY, false);
        updateShuffleButton(shuffleState);
        playState = savedInstanceState.getBoolean(PLAY_STATE_KEY, false);
        updatePlayButton(playState);
        super.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(REPEAT_STATE_KEY, repeatState);
        outState.putBoolean(SHUFFLE_STATE_KEY, shuffleState);
        outState.putBoolean(PLAY_STATE_KEY, playState);
        super.onSaveInstanceState(outState);
    }
    private void updateRepeatButton(int status){
        ImageButton repeatButton = findViewById(R.id.repeatButton);
        switch (status){
            case 0:
                repeatButton.setImageResource(R.drawable.ic_baseline_repeat_48);
                break;
            case 1:
                repeatButton.setImageResource(R.drawable.ic_baseline_repeat_on_48);
                break;
            case 2:
                repeatButton.setImageResource(R.drawable.ic_baseline_repeat_one_48);
                break;
            default:
                break;
        }
    }
    private void updateShuffleButton(boolean status){
        ImageButton shuffleButton = findViewById(R.id.shuffleButton);
        if(status){
            shuffleButton.setImageResource(R.drawable.ic_baseline_shuffle_on_48);
        }else{
            shuffleButton.setImageResource(R.drawable.ic_baseline_shuffle_48);
        }
    }
    private void updatePlayButton(boolean isPlaying){
        ImageButton playPauseButton = findViewById(R.id.playPauseButton);
        if(isPlaying){
            playPauseButton.setImageResource(R.drawable.ic_baseline_pause_circle_outline_72);
        }else{
            playPauseButton.setImageResource(R.drawable.ic_baseline_play_circle_outline_72);
        }
    }
    /**
     * The mediaPlayerPrev method is used to skip to the previously played track in the file.
     */
    private void mediaPlayerPrev() {
        if (isConnected) {
            mAudioControlsBinder.playPrev();
        }
    }
    /**
     * The mediaPlayerNext method is used to skip to the next track in the file.
     */
    private void mediaPlayerNext() {
        if (isConnected) {
            mAudioControlsBinder.playNext();
        }
    }

    /**
     * The mediaPlayerPauseOrStart method is used to pause the current track or start from the
     * paused position. Checks if service is bound first.
     */
    private boolean mediaPlayerPauseOrStart() {
        if (isConnected) {
            playState = mAudioControlsBinder.isPlaying();
            if(playState){
                mAudioControlsBinder.pause();
            }else{
                mAudioControlsBinder.resume();
            }
        }else{
            playState = false;
        }
        playState = mAudioControlsBinder.isPlaying();
        return playState;
    }

    /**
     * The mediaPlayerPlay method is used to start the MediaPlayerService and
     * also play the associated Uri.
     * @param myUri The Uri to start playing.
     */
    private void mediaPlayerPlay(Uri myUri) {
        String path = myUri.getPath();
        if (isConnected) // Start service if first time playing a track.
            // Send file name through intent to service for first notification.
            mServiceIntent.putExtra(TRACK_FILE_NAME, path.substring(path.lastIndexOf("/")+1));
            startForegroundService(mServiceIntent);
        mAudioControlsBinder.play(myUri);
        playState = true;
    }

    /**
     * The mediaPlayerPlayFolder plays the entire folder found in a DocumentFile array. Stops after
     * last file is completed playing.
     * @param folder The DocumentFile array to play all audio files from.
     */
    private void mediaPlayerPlayFolder(DocumentFile[] folder) {
        Arrays.sort(folder, new DocumentFileComparator());
        String name = folder[0].getName();
        if (isConnected)
            // Send file name through intent to service for first notification.
            mServiceIntent.putExtra(TRACK_FILE_NAME, name);
            startForegroundService(mServiceIntent);
        mAudioControlsBinder.playFolder(folder);
        playState = true;
    }
    private int mediaPlayerRepeat(){
        if(isConnected){
            repeatState = mAudioControlsBinder.repeat();
        }else{
            repeatState = 0;
        }
        return repeatState;
    }
    private boolean mediaPlayerShuffle(){
        if(isConnected){
            shuffleState = mAudioControlsBinder.shuffle();
        }else{
            shuffleState = false;
        }
        return shuffleState;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        if (!isChangingConfigurations())
            stopService(new Intent(this, MediaPlayerService.class));
    }
}