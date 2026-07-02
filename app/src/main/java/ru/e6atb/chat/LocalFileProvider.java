package ru.e6atb.chat;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URLConnection;

public final class LocalFileProvider extends ContentProvider {
	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public String getType(Uri uri) {
		String type = URLConnection.guessContentTypeFromName(fileName(uri));
		return type == null ? "application/octet-stream" : type;
	}

	@Override
	public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
		if (mode != null && mode.indexOf('w') >= 0) {
			throw new FileNotFoundException("read only");
		}
		File file = resolve(uri);
		if (file == null || !file.isFile()) {
			throw new FileNotFoundException("not found");
		}
		return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		File file = resolve(uri);
		String[] cols = projection == null ? new String[] {
			OpenableColumns.DISPLAY_NAME,
			OpenableColumns.SIZE
		} : projection;
		MatrixCursor cursor = new MatrixCursor(cols, 1);
		Object[] values = new Object[cols.length];
		for (int i = 0; i < cols.length; i++) {
			if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
				values[i] = file == null ? fileName(uri) : file.getName();
			} else if (OpenableColumns.SIZE.equals(cols[i])) {
				values[i] = file == null ? 0 : file.length();
			}
		}
		cursor.addRow(values);
		return cursor;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		return null;
	}

	@Override
	public int delete(Uri uri, String selection, String[] selectionArgs) {
		return 0;
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		return 0;
	}

	private File resolve(Uri uri) {
		if (getContext() == null) return null;
		File base = getContext().getExternalFilesDir(null);
		if (base == null) base = getContext().getFilesDir();
		if (base == null) return null;
		try {
			File root = base.getCanonicalFile();
			File target = new File(root, fileName(uri)).getCanonicalFile();
			String rootPath = root.getPath();
			String targetPath = target.getPath();
			if (!targetPath.equals(rootPath) && !targetPath.startsWith(rootPath + File.separator)) {
				return null;
			}
			return target;
		} catch (IOException e) {
			return null;
		}
	}

	private String fileName(Uri uri) {
		String segment = uri == null ? null : uri.getLastPathSegment();
		if (segment == null || segment.length() == 0) return "file";
		return segment.replace('/', '_').replace('\\', '_');
	}
}

