package yoavbz.dupimg.background;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import android.view.View;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import yoavbz.dupimg.ImageClassifier;
import yoavbz.dupimg.MainActivity;
import yoavbz.dupimg.R;
import yoavbz.dupimg.database.ImageDatabase;
import yoavbz.dupimg.models.Image;

import java.lang.ref.WeakReference;
import java.util.Collections;

public class ClassificationTask extends AsyncTask<Object, Void, Void> {

	private static final int NOTIFICATION_ID = 1;
	private final WeakReference<MainActivity> weakReference;
	private NotificationCompat.Builder mBuilder;

	public ClassificationTask(MainActivity mainActivity) {
		weakReference = new WeakReference<>(mainActivity);
	}

	@Override
	protected void onPreExecute() {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.list.clear();
			activity.clusters.clear();
			Log.d(MainActivity.TAG, "ClassifyAndCluster: Starting image classification asynchronously..");

			activity.progressBar.setProgress(0);
			activity.textView.setText("Scanning images..");
			activity.galleryView.setVisibility(View.GONE);
			activity.progressBar.setIndeterminate(false);
			activity.progressBar.setVisibility(View.VISIBLE);
			activity.textView.setVisibility(View.VISIBLE);
			activity.invalidateOptionsMenu();

			NotificationManagerCompat notificationManager = NotificationManagerCompat.from(activity);
			setNotificationChannel(activity);
			mBuilder = new NotificationCompat.Builder(activity, "dupImg")
					.setSmallIcon(R.drawable.ic_menu_gallery)
					.setContentTitle("Scanning images..")
					.setPriority(NotificationCompat.PRIORITY_LOW)
					.setOngoing(true)
					.setProgress(100, 0, false);
			notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
		}
	}

	private void setNotificationChannel(MainActivity activity) {
		NotificationChannel notificationChannel = new NotificationChannel("dupImg", "Scanning Progress",
		                                                                  NotificationManager.IMPORTANCE_LOW);
		notificationChannel.enableLights(false);
		notificationChannel.enableVibration(false);
		NotificationManager notificationManager = (NotificationManager) activity.getSystemService(
				Context.NOTIFICATION_SERVICE);
		notificationManager.createNotificationChannel(notificationChannel);
	}

	/**
	 * Iterating the given directory files, classifying them and clustering them into similarity clusters
	 *
	 * @param objects contains two objects: [0] - directory path to scan
	 *                [1] - boolean value which determine whether to reset the database
	 * @return null
	 */
	@Override
	protected Void doInBackground(Object... objects) {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			try {
				Uri uri = (Uri) objects[0];
				DocumentFile[] docs = DocumentFile.fromTreeUri(activity, uri).listFiles();
				int imagesNum = docs.length;
				activity.progressBar.setMax(imagesNum);
				Log.d(MainActivity.TAG, "ClassifyAndCluster: Found " + imagesNum + " images on DCIM directory");

				ImageClassifier classifier = new ImageClassifier(activity, "mobilenet_v2_1.0_224_quant.tflite",
				                                                 "labels.txt", 224);
				for (DocumentFile doc : docs) {
					String type = doc.getType();
					if (!isCancelled() && type != null && type.equals("image/jpeg")) {
						Image image = new Image(doc, activity, classifier);
						if (image.getDateTaken() != null) {
							activity.list.add(image);
						} else {
							Log.w(MainActivity.TAG, "ClassifyAndCluster: Skipping image: " + doc.getName());
						}
					} else {
						Log.d(MainActivity.TAG, "ClassifyAndCluster: Skipping doc: " + doc.getName());
					}
					publishProgress();
				}
				Collections.reverse(activity.list);
				classifier.close();
				boolean resetDb = (boolean) objects[1];
				if (resetDb) {
					ImageDatabase db = ImageDatabase.getAppDatabase(activity);
					db.clearAllTables();
					if (!activity.list.isEmpty()) {
						db.imageDao().insert(activity.list);
					}
				}
				if (!isCancelled()) {
					DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.7, 2);
					activity.clusters = clusterer.cluster(activity.list);
					Log.d(MainActivity.TAG, "ClassifyAndCluster: Clustered " + activity.clusters.size() + " clusters");
				}
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "ClassifyAndCluster: Got an exception", e);
				cancel(true);
			}
		}
		return null;
	}

	@Override
	protected void onProgressUpdate(Void... voids) {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.progressBar.incrementProgressBy(1);
			mBuilder.setProgress(100, activity.progressBar.getProgress(), false);

			activity.notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
		}
	}

	@Override
	protected void onPostExecute(Void result) {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.notificationManager.cancel(NOTIFICATION_ID);
			activity.progressBar.setVisibility(View.GONE);
			activity.galleryView.setVisibility(View.VISIBLE);
			activity.galleryView.setImageClusters(activity.clusters);
			activity.galleryView.notifyDataSetChanged();
			activity.invalidateOptionsMenu();
			if (activity.clusters.isEmpty()) {
				activity.textView.setText("No duplicates were found :)");
			} else {
				activity.textView.setVisibility(View.GONE);
			}
			PreferenceManager.getDefaultSharedPreferences(activity).edit()
			                 .putBoolean("shouldRescan", false)
			                 .apply();
		}
	}

	@Override
	protected void onCancelled() {
		Log.d(MainActivity.TAG, "ClassifyAndCluster - onCancelled: Cancelling task");
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.notificationManager.cancel(NOTIFICATION_ID);
			activity.textView.setText("Got an error :(");
			activity.textView.setVisibility(View.VISIBLE);
			activity.progressBar.setVisibility(View.GONE);
			activity.invalidateOptionsMenu();
			PreferenceManager.getDefaultSharedPreferences(activity).edit()
			                 .putBoolean("shouldRescan", true)
			                 .apply();
		}
	}
}
