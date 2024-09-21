package com.sovworks.eds.android.filemanager.records;

import android.content.Context;
import android.net.Uri;

import com.sovworks.eds.android.Logger;
import com.sovworks.eds.android.R;
import com.sovworks.eds.android.errors.UserException;
import com.sovworks.eds.android.helpers.TempFilesMonitor;
import com.sovworks.eds.android.service.FileOpsService;
import com.sovworks.eds.android.settings.UserSettings;
import com.sovworks.eds.exceptions.ApplicationException;
import com.sovworks.eds.fs.Path;
import com.sovworks.eds.fs.util.StringPathUtil;
import com.sovworks.eds.locations.Location;
import com.sovworks.eds.locations.Openable;
import com.sovworks.eds.settings.Settings;
import com.sovworks.eds.settings.SettingsCommon;

import java.io.IOException;

public abstract class ExecutableFileRecordBase extends FileRecord
{
	public ExecutableFileRecordBase(Context context)
	{
		super(context);
		_settings = UserSettings.getSettings(context);
	}

	@Override
	public void init(Location location, Path path) throws IOException
	{
		super.init(location, path);
		_loc = location;
	}

	@Override
	public boolean open() throws Exception
	{
		return open(false);
	}

    @Override
	public boolean openInplace() throws Exception
	{
		return open(true);
	}

	public boolean open(boolean inplace) throws Exception
	{
		if(!isFile())
			return false;
		String mime = FileOpsService.getMimeTypeFromExtension(_host, new StringPathUtil(getName()).getFileExtension());
		Logger.debug("ExecutableFileRecordBase : mime : "+mime);
		if(mime.startsWith("image/"))
			openImageFile(_loc,this, inplace);
		else if(mime.startsWith("video/"))
			openVideoFile(_loc,this);
		else{
			if(inplace){
				_host.showProperties(this, true);
			}
			startDefaultFileViewer(_loc,this);
		}
		return true;
	}
	
	protected Location _loc;
	protected final Settings _settings;
	
	protected void extractFileAndStartViewer(Location location, BrowserRecord rec) throws UserException, IOException
	{		
		if (rec.getSize() > 1024 * 1024 * _settings.getMaxTempFileSize())
			throw new UserException(_host, R.string.err_temp_file_is_too_big);
		Location loc = location.copy();
		loc.setCurrentPath(rec.getPath());
		TempFilesMonitor.getMonitor(_context).startFile(loc);
	}		
	
	protected void startDefaultFileViewer(Location location,BrowserRecord rec) throws IOException, UserException, ApplicationException
	{
		Uri devUri = location.getDeviceAccessibleUri(rec.getPath());
		if(devUri != null)
			FileOpsService.startFileViewer(_host, devUri, FileOpsService.getMimeTypeFromExtension(_context,new StringPathUtil(rec.getName()).getFileExtension()));
        else
            extractFileAndStartViewer(location, rec);
	}
	
	protected void openImageFile(Location location, BrowserRecord rec, boolean inplace) throws IOException, UserException, ApplicationException
	{			
		int ivMode = _settings.getInternalImageViewerMode();
		if(ivMode == SettingsCommon.USE_INTERNAL_IMAGE_VIEWER_ALWAYS ||
				(ivMode == SettingsCommon.USE_INTERNAL_IMAGE_VIEWER_VIRT_FS &&
				location instanceof Openable)
		)	
			_host.showPhoto(rec,inplace);
		else
		{
			if(inplace)
				_host.showPhoto(rec, true);
			startDefaultFileViewer(location, rec);
		}
	}

	protected void openVideoFile(Location location, BrowserRecord rec) throws IOException, UserException, ApplicationException
	{
		int ivMode = _settings.getInternalImageViewerMode();
		if(ivMode == SettingsCommon.USE_INTERNAL_IMAGE_VIEWER_ALWAYS ||
				(ivMode == SettingsCommon.USE_INTERNAL_IMAGE_VIEWER_VIRT_FS &&
						location instanceof Openable)
		)
			_host.showVideo(rec);
		else
		{
			startDefaultFileViewer(location, rec);
		}
	}
}