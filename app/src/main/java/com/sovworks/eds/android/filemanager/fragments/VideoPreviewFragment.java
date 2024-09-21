package com.sovworks.eds.android.filemanager.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import com.sovworks.eds.android.Logger;
import com.sovworks.eds.android.R;
import com.sovworks.eds.android.filemanager.FileManagerFragment;
import com.sovworks.eds.fs.Path;
import com.trello.rxlifecycle2.components.RxFragment;

public class VideoPreviewFragment extends RxFragment implements FileManagerFragment
{
	public static final String TAG = "VideoPreviewFragment";

    public static final String STATE_CURRENT_VIDEO_PATH = "com.sovworks.eds.android.CURRENT_VIDEO_PATH";
	
	public static VideoPreviewFragment newInstance(Path currentVideoPath)
	{
		Bundle b = new Bundle();
        if(currentVideoPath!=null)
		    b.putString(STATE_CURRENT_VIDEO_PATH,currentVideoPath.getPathString());
		VideoPreviewFragment pf = new VideoPreviewFragment();
		pf.setArguments(b);
		return pf;	
	}

	public static VideoPreviewFragment newInstance(String currentVideoPathString)
	{
		Bundle b = new Bundle();
		b.putString(STATE_CURRENT_VIDEO_PATH,currentVideoPathString);
		VideoPreviewFragment pf = new VideoPreviewFragment();
		pf.setArguments(b);
		return pf;
	}

	@Override
	public void onActivityCreated (Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public void onStart()
	{
		super.onStart();
		Logger.debug(TAG + " fragment started");
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
        Logger.debug(TAG + ": onBackPressed");
		return false;
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) 
	{
		View view = inflater.inflate(R.layout.video_preview_fragment, container, false);
		Context ctx = getActivity().getApplicationContext();
//		playerView = view.findViewById(R.id.videoView);
//		player = new SimpleExoPlayer.Builder(ctx).build();
//		playerView.setPlayer(player);
//
//		// 创建MediaItem
//		String videoUrl = "https://your-video-url.com/path/to/video.mp4"; // 替换为你的视频URL
//		MediaItem mediaItem = MediaItem.fromUri(videoUrl);
//
//		// 准备播放
//		player.setMediaItem(mediaItem);
//		player.prepare();
//		player.play(); // 开始播放
		return view;
	}

	@Override
	public void onDestroyView()
	{
		super.onDestroyView();
//		player.release();
	}	
	
	@Override
	public void onSaveInstanceState (Bundle outState)
	{	
		super.onSaveInstanceState(outState);
		if(_currentVideoPath !=null)
			outState.putString(STATE_CURRENT_VIDEO_PATH, _currentVideoPath.getPathString());
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

//	private SimpleExoPlayer player;
//	private PlayerView playerView;
	private Path _currentVideoPath;

}
