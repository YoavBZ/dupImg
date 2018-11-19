package yoavbz.galleryml.background;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;
import yoavbz.galleryml.ImageClassifier;
import yoavbz.galleryml.database.ImageDao;
import yoavbz.galleryml.database.ImageDatabase;
import yoavbz.galleryml.models.Image;

import java.io.IOException;
import java.nio.file.Paths;

public class ImageListener extends Service {

	private static final String TAG = "ImageListener";
	NewImageObserver observer;

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(TAG, "OnStartCommand");
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		pref.edit().putBoolean("isRunning", true).apply();

		String dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
		String path = Paths.get(dcim, "Camera").toString();
		int mask = FileObserver.CREATE | FileObserver.DELETE | FileObserver.MOVED_TO | FileObserver.MOVED_FROM;
		observer = new NewImageObserver(path, mask);
		observer.startWatching();

		return Service.START_STICKY;
	}

	@Override
	public void onDestroy() {
		Log.d(TAG, "OnDestroy");
		observer.stopWatching();
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		pref.edit().putBoolean("isRunning", false).apply();
		super.onDestroy();
	}

	/**
	 * This class describes a file observer handling the app actions when files change in the device camera folder.
	 */
	private class NewImageObserver extends FileObserver {

		private static final String TAG = "ImageObserver";
		private String directory;
		private ImageClassifier classifier = null;
		private ImageDao db;

		NewImageObserver(String path, int mask) {
			super(path, mask);
			directory = path;
			db = ImageDatabase.getAppDatabase(getApplicationContext()).imageDao();
			try {
				classifier = new ImageClassifier(ImageListener.this, "mobilenet_v2_1.0_224_quant.tflite", "labels.txt",
				                                 224);
			} catch (IOException e) {
				Log.e(TAG, "Couldn't build image classifier: " + e);
			}
			Log.d(TAG, "Initiating NewImageObserver..");
		}

		@Override
		public void onEvent(int event, @Nullable String path) {
			Handler handler = new Handler(Looper.getMainLooper());
			path = directory + '/' + path;
			switch (event) {
				case FileObserver.CREATE:
				case FileObserver.MOVED_TO:
					Log.d(TAG, "New image found!");
					Image image = new Image(path);
					if (image.getDateTaken() != null) {
						image.calculateFeatureVector(classifier);
						db.insert(image);
						handler.post(() -> Toast.makeText(ImageListener.this, "New Image!", Toast.LENGTH_SHORT).show());
					}
					break;
				case FileObserver.DELETE:
				case FileObserver.MOVED_FROM:
					db.delete(path);
					handler.post(
							() -> Toast.makeText(ImageListener.this, "Image was removed!!",
							                     Toast.LENGTH_SHORT).show());
					Log.d(TAG, "An image was removed!");
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
			Toast.makeText(ImageListener.this, "Monitor stopped", Toast.LENGTH_LONG).show();
			Log.d(TAG, "NewImageObserver stopWatching");
			super.stopWatching();
		}
	}
}
