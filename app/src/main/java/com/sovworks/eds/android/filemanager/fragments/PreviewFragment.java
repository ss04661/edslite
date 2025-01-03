package com.sovworks.eds.android.filemanager.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ViewSwitcher;

import com.sovworks.eds.android.Logger;
import com.sovworks.eds.android.R;
import com.sovworks.eds.android.filemanager.FileManagerFragment;
import com.sovworks.eds.android.filemanager.tasks.LoadPathInfoObservable;
import com.sovworks.eds.android.filemanager.tasks.LoadedImage;
import com.sovworks.eds.android.helpers.CachedPathInfo;
import com.sovworks.eds.android.service.FileOpsService;
import com.sovworks.eds.android.settings.UserSettings;
import com.sovworks.eds.android.views.GestureImageView.NavigListener;
import com.sovworks.eds.android.views.GestureImageViewWithFullScreenMode;
import com.sovworks.eds.exceptions.ApplicationException;
import com.sovworks.eds.fs.Path;
import com.sovworks.eds.fs.util.StringPathUtil;
import com.sovworks.eds.locations.Location;
import com.sovworks.eds.settings.GlobalConfig;
import com.trello.rxlifecycle2.components.RxFragment;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.CancellationException;

import io.reactivex.Single;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.BehaviorSubject;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;

import static com.sovworks.eds.android.settings.UserSettingsCommon.IMAGE_VIEWER_AUTO_ZOOM_ENABLED;
import static com.sovworks.eds.android.settings.UserSettingsCommon.IMAGE_VIEWER_FULL_SCREEN_ENABLED;

public class PreviewFragment extends RxFragment implements FileManagerFragment
{
	public interface Host
	{
		NavigableSet<? extends CachedPathInfo> getCurrentFiles();
		Location getLocation();
		Object getFilesListSync();
		void onToggleFullScreen();
		void closePreviewFragment();
		void selectFileByName(String name);
		void unSelectFileByName(String name);
		void scrollToFile(String name);
		void confirmDeleteFiles(ArrayList<Path> paths);
	}
	
	public static final String TAG = "PreviewFragment";

    public static final String STATE_CURRENT_PATH = "com.sovworks.eds.android.CURRENT_PATH";
	
	public static PreviewFragment newInstance(Path currentImagePath)
	{
		Bundle b = new Bundle();
        if(currentImagePath!=null)
		    b.putString(STATE_CURRENT_PATH,currentImagePath.getPathString());
		PreviewFragment pf = new PreviewFragment();
		pf.setArguments(b);
		return pf;	
	}

	public static PreviewFragment newInstance(String currentImagePathString)
	{
		Bundle b = new Bundle();
		b.putString(STATE_CURRENT_PATH,currentImagePathString);
		PreviewFragment pf = new PreviewFragment();
		pf.setArguments(b);
		return pf;
	}
	
	public static Bitmap loadDownsampledImage(Path path,int sampleSize) throws IOException
	{
		BitmapFactory.Options options = new BitmapFactory.Options();			
	    options.inSampleSize = sampleSize;
	    InputStream data = path.getFile().getInputStream();
		try
		{
			return BitmapFactory.decodeStream(data, null, options);
		}
		finally
		{
			data.close();
		}
	}
	
	public static BitmapFactory.Options loadImageParams(Path path) throws IOException
	{
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		InputStream data = path.getFile().getInputStream();
		try
		{
			BitmapFactory.decodeStream(data, null, options);
		}
		finally
		{
			data.close();
		}
		return options;
	}
	
	public static int calcSampleSize(Rect viewRect,Rect regionRect)
	{
	    int inSampleSize = 1;

	    if (regionRect.height() > viewRect.height() || regionRect.width() > viewRect.width()) 
	    { 
	        inSampleSize = Math.max(
		        				Math.round((float)regionRect.height() / (float)viewRect.height()),
		        				Math.round((float)regionRect.width() / (float)viewRect.width())
	        				);
	        if(inSampleSize>1)
	        {
		        for(int i=1;i<10;i++)
		        {
		        	if(Math.pow(2, i)>inSampleSize)
		        	{
		        		inSampleSize = (int) Math.pow(2, i-1);
		        		break;
		        	}
		        }
	        }
	    }
	    return inSampleSize;
		
	}

	@Override
	public void onActivityCreated (Bundle savedInstanceState)
	{
		super.onActivityCreated(savedInstanceState);
		try
		{
			initParams(savedInstanceState != null && savedInstanceState.containsKey(STATE_CURRENT_PATH) ?
                       savedInstanceState
                                                                                                        :
                       getArguments()
            );
			loadImagePaths();
		}
		catch (IOException | ApplicationException e)
		{
			Logger.showAndLog(getActivity(), e);
		}
		setHasOptionsMenu(true);
	}

	@Override
	public void onStart()
	{
		super.onStart();
		Logger.debug(TAG + " fragment started");
		loadImageWhenReady();
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

	public int getPathLabel(String path){
		SharedPreferences sharedPreferences = UserSettings.getSettings(getActivity()).getSharedPreferences();
		Set<String> likePaths = sharedPreferences.getStringSet("喜欢列表", new HashSet<>());
		Set<String> disLikePaths = sharedPreferences.getStringSet("不喜欢列表", new HashSet<>());
        if(likePaths.contains(path)){
			return 1;
		}
		if(disLikePaths.contains(path)){
			return 2;
		}
		return 0;
	}

	public void setPathLabel(String path, int label){
		SharedPreferences sharedPreferences = UserSettings.getSettings(getActivity()).getSharedPreferences();
		Set<String> likePaths = sharedPreferences.getStringSet("喜欢列表", new HashSet<>());
		Set<String> disLikePaths = sharedPreferences.getStringSet("不喜欢列表", new HashSet<>());
		if(label==1){
			likePaths.add(path);
			disLikePaths.remove(path);
		}else if(label==2){
			likePaths.remove(path);
			disLikePaths.add(path);
		}else{
			likePaths.remove(path);
			disLikePaths.remove(path);
		}
		sharedPreferences.edit().putStringSet("喜欢列表", likePaths).commit();
		sharedPreferences.edit().putStringSet("不喜欢列表", disLikePaths).commit();
	}

	@Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,Bundle savedInstanceState) 
	{
		Logger.debug(TAG + ": currentImagePath : "+ _currentImagePath);

		View view = inflater.inflate(R.layout.preview_fragment, container, false);
		Button likeButton = view.findViewById(R.id.like_button);
		_likeButton = likeButton;
		likeButton.setOnClickListener((v)-> {
			if(_currentImagePath==null){
				return;
			}
			String path = _currentImagePath.getPathString();
			int label = getPathLabel(path);
			if(label==1){ //喜欢
				setPathLabel(path, 2);
				likeButton.setText("讨厌");
			}else if(label==2){ //讨厌
				setPathLabel(path, 0);
				likeButton.setText("一般");
			}else{ //一般
				setPathLabel(path, 1);
				likeButton.setText("喜欢");
			}
		});
		view.findViewById(R.id.previous_button).setOnClickListener((v)-> {
            try {
                moveLeft();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (ApplicationException e) {
                throw new RuntimeException(e);
            }
        });
		view.findViewById(R.id.next_button).setOnClickListener((v)-> {
			try {
				moveRight();
			} catch (IOException e) {
				throw new RuntimeException(e);
			} catch (ApplicationException e) {
				throw new RuntimeException(e);
			}
		});
		view.findViewById(R.id.close_button).setOnClickListener((v)-> {
			Logger.debug("close_button");
			closePreviewFragment();
		});
		view.findViewById(R.id.delete_button).setOnClickListener((v)-> {
			Logger.debug("delete_button");
			deleteImage();
        });

		_mainImageView = view.findViewById(R.id.imageView);
		_mainImageView.setAutoZoom(UserSettings.getSettings(getActivity()).isImageViewerAutoZoomEnabled());

		_mainImageView.setNavigListener(new NavigListener()
		{			
			@Override
			public void onPrev()
			{
				try
				{
					moveLeft();
				}
				catch (IOException | ApplicationException e)
				{
					Logger.showAndLog(getActivity(), e);
				}
			}
			
			@Override
			public void onNext()
			{
				try
				{
					moveRight();
				}
				catch (IOException | ApplicationException e)
				{
					Logger.showAndLog(getActivity(), e);
				}
			}

			@Override
			public void onClose()
			{
				Logger.debug("onClose");
				closePreviewFragment();
			}
		});

		_mainImageView.setOnLoadOptimImageListener(srcImageRect ->
		{
            if(_isOptimSupported)
                loadImage(srcImageRect);
        });
        _mainImageView.setOnSizeChangedListener(() ->
		{
            _mainImageView.getViewRect().round(_viewRect);
            _imageViewPrepared.onNext(_viewRect.width() > 0 && _viewRect.height() > 0);
        });



		if(UserSettings.getSettings(getActivity()).isImageViewerFullScreenModeEnabled())
			_mainImageView.setFullscreenMode(true);
		return view;
	}


	@Override
	public void onDestroyView()
	{
		_mainImageView.clearImage();
		_mainImageView = null;
		super.onDestroyView();		
	}	
	
	@Override
	public void onSaveInstanceState (Bundle outState)
	{	
		super.onSaveInstanceState(outState);
		if(_currentImagePath!=null)					
			outState.putString(STATE_CURRENT_PATH, _currentImagePath.getPathString());		
	}
	
	@Override
    public void onCreateOptionsMenu(Menu menu,MenuInflater menuInflater)
	{		
		menuInflater.inflate(R.menu.image_viewer_menu, menu);
		super.onCreateOptionsMenu(menu, menuInflater);
	}

	@Override
	public void onPrepareOptionsMenu (Menu menu)
	{
		super.onPrepareOptionsMenu(menu);
		try
		{
			MenuItem mi = menu.findItem(R.id.prevMenuItem);
			mi.setEnabled(getPrevImagePath() != null);
			mi = menu.findItem(R.id.nextMenuItem);
			mi.setEnabled(getNextImagePath() != null);
			mi = menu.findItem(R.id.fullScreenModeMenuItem);
			mi.setChecked(_isFullScreen);
		}
		catch (IOException | ApplicationException e)
		{
			Logger.showAndLog(getActivity(), e);
		}
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem menuItem)
	{
		try
		{
			switch (menuItem.getItemId())
			{
			case R.id.prevMenuItem:
				moveLeft();
				return true;
			case R.id.nextMenuItem:
				moveRight();
				return true;		
			case R.id.zoomInMenuItem:
				_mainImageView.zoomIn();
				return true;
			case R.id.zoomOutMenuItem:
				_mainImageView.zoomOut();
				return true;
			case R.id.rotateLeftMenuItem:
				_mainImageView.rotateLeft();
				return true;
			case R.id.rotateRightMenuItem:
				_mainImageView.rotateRight();
				return true;
			case R.id.toggleAutoZoom:
				toggleAutoZoom();
				return true;
			case R.id.fullScreenModeMenuItem:
				toggleFullScreen();
				return true;
			}
		}
		catch (Exception e)
		{
			Logger.showAndLog(getActivity(), e);
		}
		return super.onOptionsItemSelected(menuItem);
	}

	public void updateImageViewFullScreen()
	{
		if(_mainImageView!=null && _isFullScreen)
			_mainImageView.setFullscreenMode(true);
	}

	private GestureImageViewWithFullScreenMode _mainImageView;
	private Button _likeButton;
	private Path _currentImagePath, _prevImagePath, _nextImagePath;
	private final Rect _viewRect = new Rect();
	private boolean _isFullScreen, _isOptimSupported;
	private final Subject<Boolean> _imageViewPrepared = BehaviorSubject.create();

	private void loadImagePaths()
	{
		_prevImagePath = _nextImagePath = null;
		getActivity().invalidateOptionsMenu();
		if(_currentImagePath != null)
		{
			Location loc = getPreviewFragmentHost().getLocation();
			if(loc!=null)
			{
				loc = loc.copy();
				loc.setCurrentPath(_currentImagePath);
				LoadPathInfoObservable.create(loc).
						subscribeOn(Schedulers.io()).
						observeOn(AndroidSchedulers.mainThread()).
						compose(bindToLifecycle()).
						subscribe(
								rec -> {
									setNeibImagePaths(rec);
									getActivity().invalidateOptionsMenu();
								},
								err -> { if(!(err instanceof CancellationException))
									Logger.showAndLog(getActivity(), err);
								}
						);
			}
		}
	}


	@SuppressLint("ApplySharedPref")
	private void toggleFullScreen()
	{
		_isFullScreen = !_isFullScreen;
		UserSettings.getSettings(getActivity()).getSharedPreferences().edit().putBoolean(IMAGE_VIEWER_FULL_SCREEN_ENABLED, _isFullScreen).commit();

		if(android.os.Build.VERSION.SDK_INT>=Build.VERSION_CODES.KITKAT)
		{
			if(_mainImageView!=null)
				_mainImageView.setFullscreenMode(_isFullScreen);
		}
		getPreviewFragmentHost().onToggleFullScreen();
		getActivity().invalidateOptionsMenu();
	}
	private void initParams(Bundle args) throws IOException, ApplicationException
	{
		Host act = getPreviewFragmentHost();
		if(act == null)
			return;
		Location loc = act.getLocation();
		try
		{
			_currentImagePath = args.containsKey(STATE_CURRENT_PATH) ? loc.getFS().getPath(args.getString(STATE_CURRENT_PATH)) : null;
		}
		catch(IOException e)
		{
			Logger.showAndLog(getActivity(), e);
		}
		if(_currentImagePath == null)
			_currentImagePath = getFirstImagePath();

		if (_currentImagePath!=null) {
			String path = _currentImagePath.getPathString();
			int defaultLabel = getPathLabel(path);
			if(defaultLabel==1){
				_likeButton.setText("喜欢");
			}else if(defaultLabel==2){
				_likeButton.setText("不喜");
			}else{
				_likeButton.setText("一般");
			}
		}

	}

	private Path getFirstImagePath()
	{
		synchronized (getPreviewFragmentHost().getFilesListSync())
		{
			Host h = getPreviewFragmentHost();
			//noinspection unchecked
			NavigableSet<CachedPathInfo> files = (NavigableSet<CachedPathInfo>) h.getCurrentFiles();
			return files.isEmpty() ? null : files.first().getPath();
		}
	}

	private Host getPreviewFragmentHost()
	{
		return (Host)getActivity();
	}

	
	private void showImage()
	{
		getActivity().invalidateOptionsMenu();
	}

	@SuppressLint("ApplySharedPref")
	private void toggleAutoZoom()
	{
		UserSettings settings = UserSettings.getSettings(getActivity());
		boolean val = !settings.isImageViewerAutoZoomEnabled();
		settings.getSharedPreferences().edit().putBoolean(IMAGE_VIEWER_AUTO_ZOOM_ENABLED, val).commit();
		_mainImageView.setAutoZoom(val);
	}

	private void closePreviewFragment(){
		String filePath = _currentImagePath.getPathDesc();
		String fileName  = new java.io.File(filePath).getName();
		Host h = getPreviewFragmentHost();
		h.closePreviewFragment();
		h.scrollToFile(fileName);
	}

	private void unSelectImage()
	{
		String filePath = _currentImagePath.getPathDesc();
		String fileName  = new java.io.File(filePath).getName();

		Logger.debug(_currentImagePath.getPathString());
		Logger.debug(_currentImagePath.getPathDesc());
		getPreviewFragmentHost().unSelectFileByName(fileName);
	}

	private void selectImage()
	{
		String filePath = _currentImagePath.getPathDesc();
		String fileName  = new java.io.File(filePath).getName();
		
		Logger.debug(_currentImagePath.getPathString());
		Logger.debug(_currentImagePath.getPathDesc());
		getPreviewFragmentHost().selectFileByName(fileName);
	}

	private void deleteImage()
	{
		ArrayList<Path> paths = new ArrayList<>();
		paths.add(_currentImagePath);
		getPreviewFragmentHost().confirmDeleteFiles(paths);
	}

	private void moveLeft() throws IOException, ApplicationException
	{
		if(_prevImagePath != null)
		{
			_currentImagePath = _prevImagePath;
			loadImageWhenReady();
		}
		loadImagePaths();
	}
	
	private void moveRight() throws IOException, ApplicationException
	{
		if(_nextImagePath!=null)
		{
			_currentImagePath = _nextImagePath;
			loadImageWhenReady();
		}
		loadImagePaths();
	}
	
	private Path getNextImagePath() throws IOException, ApplicationException
	{
		return _nextImagePath;
	}
	
	private Path getPrevImagePath()
	{
		return _prevImagePath;
	}
	
	private void setNeibImagePaths(CachedPathInfo curImageFileInfo) throws IOException, ApplicationException
	{
		synchronized (getPreviewFragmentHost().getFilesListSync())
		{
			Host h = getPreviewFragmentHost();
			//noinspection unchecked
			NavigableSet<CachedPathInfo> files = (NavigableSet<CachedPathInfo>) h.getCurrentFiles();
			if(files.isEmpty())
				return;
			Context ctx = getActivity().getApplicationContext();
			CachedPathInfo cur = curImageFileInfo;
			while(cur!=null)
			{
				cur = files.higher(cur);
				if(cur!=null && cur.isFile())
				{
					String mime = FileOpsService.getMimeTypeFromExtension(ctx, new StringPathUtil(cur.getName()).getFileExtension());
					if (mime.startsWith("image/"))
					{
						_nextImagePath = cur.getPath();
						break;
					}
				}
			}
			cur = curImageFileInfo;
			while(cur!=null)
			{
				cur = files.lower(cur);
				if(cur!=null && cur.isFile())
				{
					String mime = FileOpsService.getMimeTypeFromExtension(ctx, new StringPathUtil(cur.getName()).getFileExtension());
					if (mime.startsWith("image/"))
					{
						_prevImagePath = cur.getPath();
						break;
					}
				}
			}
		}
	}

	private void loadImageWhenReady()
	{
		_imageViewPrepared.
				filter(res -> res).
				firstElement().
				compose(bindToLifecycle()).
				subscribe(res -> loadImage(null), err ->
				{
					if(!(err instanceof CancellationException))
						Logger.log(err);
				});
	}

	private void loadImage(Rect regionRect)
	{		
		if(_currentImagePath == null)
			return;
		Logger.debug(TAG + ": loading image");
		// if(regionRect == null)
		// 	showLoading();
		Single<LoadedImage> loadImageTaskObservable = LoadedImage.createObservable(getActivity().getApplicationContext(), _currentImagePath, _viewRect, regionRect).
				subscribeOn(Schedulers.io()).
				observeOn(AndroidSchedulers.mainThread()).
				compose(bindToLifecycle());
		if(GlobalConfig.isTest())
		{
			loadImageTaskObservable = loadImageTaskObservable.
					doOnSubscribe(sub -> TEST_LOAD_IMAGE_TASK_OBSERVABLE.onNext(true)).
					doFinally(() -> TEST_LOAD_IMAGE_TASK_OBSERVABLE.onNext(false));
		}

		loadImageTaskObservable.subscribe(res -> {
					if(regionRect == null)
					{
						_mainImageView.setImage(
								res.getImageData(),
								res.getSampleSize(),
								res.getRotation(),
								res.getFlipX(),
								res.getFlipY());
						_isOptimSupported = res.isOptimSupported();
						showImage();
					}
					else
						_mainImageView.setOptimImage(res.getImageData(), res.getSampleSize());

				}, err -> {
					if(!(err instanceof CancellationException))
						Logger.showAndLog(getActivity(), err);
				});
	}

	static
	{
		if(GlobalConfig.isTest())
			TEST_LOAD_IMAGE_TASK_OBSERVABLE = PublishSubject.create();

	}

	public static Subject<Boolean> TEST_LOAD_IMAGE_TASK_OBSERVABLE;
}
