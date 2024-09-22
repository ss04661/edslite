package com.sovworks.eds.android;

import static com.google.android.exoplayer2.util.Util.castNonNull;
import static java.lang.Math.min;

import android.net.Uri;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.text.TextUtils;

import androidx.annotation.DoNotInline;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.upstream.BaseDataSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSourceException;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.TransferListener;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.Map;

import com.sovworks.eds.android.Logger;
import com.sovworks.eds.fs.RandomAccessIO;


/** A {@link DataSource} for reading local files. */
public final class InputStreamDataSource extends BaseDataSource {

    @Nullable private RandomAccessIO file;
    @Nullable private Uri uri;
    private long bytesRemaining;
    private boolean opened;

    public InputStreamDataSource(RandomAccessIO input) {
        super(/* isNetwork= */ false);
        file = input;
    }

    @Override
    public long open(DataSpec dataSpec) throws com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException {
        Uri uri = dataSpec.uri;
        this.uri = uri;
        transferInitializing(dataSpec);
        try {
            file.seek(dataSpec.position);
            bytesRemaining =
                    dataSpec.length == C.LENGTH_UNSET ? file.length() - dataSpec.position : dataSpec.length;
        } catch (IOException e) {
            throw new com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
        }
        if (bytesRemaining < 0) {
            throw new com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException(
                    /* message= */ null,
                    /* cause= */ null,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE);
        }

        opened = true;
        transferStarted(dataSpec);

        return bytesRemaining;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException {
        if (length == 0) {
            return 0;
        } else if (bytesRemaining == 0) {
            return C.RESULT_END_OF_INPUT;
        } else {
            int bytesRead;
            try {
                bytesRead = castNonNull(file).read(buffer, offset, (int) min(bytesRemaining, length));
            } catch (IOException e) {
                throw new com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException(e, PlaybackException.ERROR_CODE_IO_UNSPECIFIED);
            }

            if (bytesRead > 0) {
                bytesRemaining -= bytesRead;
                bytesTransferred(bytesRead);
            }

            return bytesRead;
        }
    }

    @Override
    @Nullable
    public Uri getUri() {
        return uri;
    }

    @Override
    public void close() throws com.google.android.exoplayer2.upstream.FileDataSource.FileDataSourceException {
    }

}

