package yoavbz.galleryml.background;

import android.app.Service;
import android.content.Intent;
import android.os.*;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import java.nio.file.Paths;

public class ImageListener extends Service {

	private static final String TAG = "ImageListener";
	NewImageObserver observer;
	private static boolean isRunning = false;

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "OnStartCommand");
		isRunning = true;
		String dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
		String path = Paths.get(dcim, "Camera").toString();
		int mask = FileObserver.CREATE | FileObserver.DELETE | FileObserver.MOVED_TO | FileObserver.MOVED_FROM;
		observer = new NewImageObserver(path, mask);
		observer.startWatching();
		return Service.START_NOT_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "OnDestroy");
		isRunning = false;
		super.onDestroy();
	}

	public static boolean isRunning() {
		Log.d(TAG, "isRunning " + isRunning);
		return isRunning;
	}

	/**
	 * This class describes a file observer handling the app actions when files change in the device camera folder.
	 */
	private class NewImageObserver extends FileObserver {

		private static final String TAG = "ImageObserver";

		NewImageObserver(String path, int mask) {
			super(path, mask);
			Log.d(TAG, "Initiating NewImageObserver..");
		}

		@Override
		public void onEvent(int event, @Nullable String path) {
			Handler handler = new Handler(Looper.getMainLooper());
			switch (event) {
				case FileObserver.CREATE:
				case FileObserver.MOVED_TO:
					// TODO: Create new Image and save in the DB
					handler.post(() -> Toast.makeText(getApplicationContext(), "New Image!", Toast.LENGTH_SHORT)
							.show());
					Log.d(TAG, "New image found!");
					break;
				case FileObserver.DELETE:
				case FileObserver.MOVED_FROM:
					// TODO: Create new Image and save in the DB
					handler.post(() -> Toast.makeText(getApplicationContext(), "Image was removed!!", Toast.LENGTH_SHORT)
							.show());
					Log.d(TAG, "An image was removed!");
					break;
			}
		}

		@Override
		public void startWatching() {
			super.startWatching();
			Toast.makeText(ImageListener.this, "Monitor started", Toast.LENGTH_LONG).show();
			Log.d(TAG, "NewImageObserver startWatching");
		}

		@Override
		public void stopWatching() {
			super.stopWatching();
			Toast.makeText(ImageListener.this, "Monitor stopped", Toast.LENGTH_LONG).show();
			Log.d(TAG, "NewImageObserver stopWatching");
		}
	}
}
