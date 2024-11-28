package com.sovworks.eds.android.filemanager.activities;

import com.sovworks.eds.android.navigdrawer.DrawerController;
import com.sovworks.eds.fs.Path;
import com.sovworks.eds.locations.Location;

import java.util.ArrayList;

public class FileManagerActivity extends FileManagerActivityBase
{
    public static void openFileManager(FileManagerActivity fm, Location location, int scrollPosition)
    {
        fm.goTo(location, scrollPosition);
    }

    @Override
    protected DrawerController createDrawerController()
    {
        return new DrawerController(this);
    }

    @Override
    protected void showPromoDialogIfNeeded()
    {
        if(_settings.getLastViewedPromoVersion() < 211)
            super.showPromoDialogIfNeeded();
    }

}
