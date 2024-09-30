package com.sovworks.eds.android.filemanager.fragments;
import static android.view.View.SYSTEM_UI_FLAG_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_IMMERSIVE;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
import static android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageButton;

import com.sovworks.eds.android.InputStreamDataSource;
import com.sovworks.eds.android.Logger;
import com.sovworks.eds.android.R;
import com.sovworks.eds.android.filemanager.FileManagerFragment;
import com.sovworks.eds.exceptions.ApplicationException;
import com.sovworks.eds.fs.File;
import com.sovworks.eds.fs.Path;
import com.sovworks.eds.fs.RandomAccessIO;
import com.sovworks.eds.locations.Location;
import com.trello.rxlifecycle2.components.RxFragment;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;

import java.io.IOException;

public class VideoPreviewFragment extends RxFragment implements FileManagerFragment
{
	public static final String TAG = "VideoPreviewFragment";

	public static final String STATE_CURRENT_PATH = "com.sovworks.eds.android.CURRENT_VIDEO_PATH";

	public static VideoPreviewFragment newInstance(Path currentVideoPath)
	{
		Bundle b = new Bundle();
		if(currentVideoPath!=null)
			b.putString(STATE_CURRENT_PATH,currentVideoPath.getPathString());
		VideoPreviewFragment pf = new VideoPreviewFragment();
		pf.setVideoPath(currentVideoPath);
		pf.setArguments(b);
		return pf;	
	}

	public void setVideoPath(Path currentVideoPath){
		this.currentVideoPath = currentVideoPath;
	}

	@Override
	public void onActivityCreated (Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
	}

	@Override
	public void onStart()
	{
		super.onStart();
	}

	@Override
	public void onStop()
	{
		super.onStop();
		Logger.debug(TAG + " fragment stopped");

	}

	@Override
	public void onResume()
	{
		super.onResume();
		Logger.debug(TAG + " fragment resumed");
	}

	@Override
	public void onPause()
	{
		super.onPause();
		Logger.debug(TAG + " fragment paused");
	}

	@Override
	public boolean onBackPressed()
	{
		Activity ctx = getActivity();
		ctx.setRequestedOrientation(StartOrientation);
		return false;
	}

	private PreviewFragment.Host getPreviewFragmentHost()
	{
		return (PreviewFragment.Host)getActivity();
	}

	private void initParams(Bundle args)
	{
		StartOrientation = getResources().getConfiguration().orientation;

		if(currentVideoPath==null){
			PreviewFragment.Host act = getPreviewFragmentHost();
			if(act == null)
				return;
			Location loc = act.getLocation();
			try
			{
				currentVideoPath = args.containsKey(STATE_CURRENT_PATH) ? loc.getFS().getPath(args.getString(STATE_CURRENT_PATH)) : null;
			}
			catch(IOException e)
			{
				Logger.showAndLog(getActivity(), e);
			}
		}
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		initParams(savedInstanceState);

		view = inflater.inflate(R.layout.video_preview_fragment, container, false);
		Context ctx = getActivity().getApplicationContext();
		playerView = view.findViewById(R.id.videoView);
		playerView.setUseController(true); // 显示控制器
		playerView.setBackgroundColor(getResources().getColor(android.R.color.black)); // 设置背景为黑

		ImageButton rotateButton = playerView.findViewById(R.id.rotate_screen);
		rotateButton.setOnClickListener(v -> toggleScreenOrientation());

		player = new SimpleExoPlayer.Builder(ctx).build();
		playerView.setPlayer(player);
		Logger.debug(TAG + ": currentVideoPath : "+ currentVideoPath);
		if(currentVideoPath==null){
			return view;
		}
        try {
			videoFile = currentVideoPath.getFile().getRandomAccessIO(File.AccessMode.Read);
        } catch (IOException e) {
			Logger.debug(TAG + " : " +e.getMessage());
			return view;
        }

        DataSource.Factory factory = () -> new InputStreamDataSource(videoFile);
		// Create a MediaSource using ExtractorMediaSource
		MediaSource mediaSource = new ProgressiveMediaSource.Factory(factory)
				.createMediaSource(MediaItem.fromUri(Uri.EMPTY));
		// Prepare the player with the source
		player.setMediaSource(mediaSource);
		player.prepare();
		player.play(); // 开始播放

		setNavVisibility(view,false); // 进入全屏模式
		return view;
	}

	@SuppressLint("InlinedApi")
	private void setNavVisibility(View view, boolean visible)
	{
		int newVis = SYSTEM_UI_FLAG_LAYOUT_STABLE
				| SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
		if (!visible)
			newVis |= SYSTEM_UI_FLAG_FULLSCREEN | SYSTEM_UI_FLAG_IMMERSIVE ;

		// Set the new desired visibility.
		view.setSystemUiVisibility(newVis);
	}

	// 切换屏幕方向的方法
	private void toggleScreenOrientation() {
		Activity ctx = getActivity();
		int currentOrientation = getResources().getConfiguration().orientation;
		if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
			// 切换到横屏
			ctx.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		} else {
			// 切换到竖屏
			ctx.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		}
	}

	@Override
	public void onDestroyView()
	{
		super.onDestroyView();
		if (player != null) {
			player.release();
			player = null;
		}
		if (videoFile != null) {
            try {
                videoFile.close();
            } catch (IOException ignored) {
            }
			videoFile = null;
		}
	}

	@Override
	public void onSaveInstanceState (Bundle outState)
	{
		super.onSaveInstanceState(outState);
		if(currentVideoPath!=null)
			outState.putString(STATE_CURRENT_PATH, currentVideoPath.getPathString());
	}
	
	@Override
    public void onCreateOptionsMenu(Menu menu,MenuInflater menuInflater)
	{
		super.onCreateOptionsMenu(menu, menuInflater);
	}

	@Override
	public void onPrepareOptionsMenu (Menu menu)
	{
		super.onPrepareOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem)
	{
		return super.onOptionsItemSelected(menuItem);
	}

	private View view;
	private ExoPlayer player;
	private PlayerView playerView;
	private Path currentVideoPath;
	private RandomAccessIO videoFile;
	int StartOrientation;

}
