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
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import yoavbz.dupimg.ImageClassifier;
import yoavbz.dupimg.MainActivity;
import yoavbz.dupimg.R;
import yoavbz.dupimg.database.ImageDao;
import yoavbz.dupimg.database.ImageDatabase;
import yoavbz.dupimg.models.Image;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class ClassificationTask extends AsyncTask<Uri, Void, List<Cluster<Image>>> {

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
					.setOngoing(true);
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
	 * @param uris Contains directory uri to scan in case of a custom scan, null otherwise
	 * @return null
	 */
	@Override
	protected List<Cluster<Image>> doInBackground(Uri... uris) {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			ImageDatabase db = null;
			try (ImageClassifier classifier = new ImageClassifier(activity, "mobilenet_v2_1.0_224_quant.tflite",
			                                                      "labels.txt", 224)) {
				db = ImageDatabase.getAppDatabase(activity);

				Uri dirUri = uris[0];
				if (dirUri == null) {
					// In case no custom uri was delivered - use default value
					String uriString = PreferenceManager.getDefaultSharedPreferences(activity)
					                                    .getString("dirUri", null);
					dirUri = Uri.parse(uriString);
				}
				DocumentFile[] docs = DocumentFile.fromTreeUri(activity, dirUri).listFiles();

				activity.runOnUiThread(() -> activity.progressBar.setMax(docs.length));
				mBuilder.setProgress(docs.length, 0, false);
				Log.d(MainActivity.TAG, "ClassifyAndCluster: Found " + docs.length + " images on " + dirUri.getPath());

				// Fetching all the images from the given directory
				List<Uri> localImages = new ArrayList<>();
				for (DocumentFile doc : docs) {
					String type = doc.getType();
					if (!isCancelled() && type != null && type.equals("image/jpeg")) {
						localImages.add(doc.getUri());
					}
					publishProgress();
				}

				activity.runOnUiThread(() -> {
					activity.progressBar.setIndeterminate(true);
					mBuilder.setProgress(activity.progressBar.getMax(),
					                     activity.progressBar.getProgress(), true);
				});
				DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.7, 2);
				List<Image> toClusters;

				boolean regularScan = (uris[0] == null);
				if (regularScan) {
					// Regular Scan
					// Removing from the database images that were deleted from local directory
					db.imageDao().deleteNotInList(localImages);
					List<Image> newImages = getNewImages(activity, db.imageDao(), classifier, localImages);
					if (!newImages.isEmpty()) {
						db.imageDao().insert(newImages);
					} else {
						Log.d(MainActivity.TAG, "NotificationJobService: No new images..");
					}
					toClusters = db.imageDao().getAll();
				} else {
					// Custom scan
					toClusters = new ArrayList<>();
					for (Uri uri : localImages) {
						if (!isCancelled()) {
							DocumentFile doc = DocumentFile.fromSingleUri(activity, uri);
							Image image = new Image(doc, activity, classifier);
							if (image.isValid()) {
								toClusters.add(image);
							} else {
								Log.d(MainActivity.TAG, "ClassifyAndCluster: Skipping file: " + doc.getName());
							}
						}
					}
				}
				return clusterer.cluster(toClusters);
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "ClassifyAndCluster: Got an exception", e);
				cancel(true);
			} finally {
				if (db != null) {
//					db.close();
				}
			}
		}
		return new ArrayList<>();
	}

	private List<Image> getNewImages(Context context, ImageDao dao, ImageClassifier classifier, List<Uri> localImages) {
		ArrayList<Image> newImages = new ArrayList<>();
		// Filtering images that are already in the database
		localImages.removeAll(dao.getAllUris());
		for (Uri newImage : localImages) {
			try {
				Image image = new Image(DocumentFile.fromSingleUri(context, newImage), context, classifier);
				newImages.add(image);
			} catch (IOException e) {
				Log.e(MainActivity.TAG, "getNewImages: ", e);
			}
		}
		return newImages;
	}

	@Override
	protected void onProgressUpdate(Void... voids) {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.progressBar.incrementProgressBy(1);
			mBuilder.setProgress(activity.progressBar.getMax(), activity.progressBar.getProgress(), false);
			activity.notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
		}
	}

	@Override
	protected void onPostExecute(List<Cluster<Image>> clusters) {
		Log.d(MainActivity.TAG, "ClassificationTask - fetchAndCluster: Clustered " + clusters.size() + " clusters");
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.notificationManager.cancel(NOTIFICATION_ID);
			activity.progressBar.setVisibility(View.GONE);
			activity.galleryView.setVisibility(View.VISIBLE);
			activity.galleryView.setImageClusters(clusters);
			activity.galleryView.notifyDataSetChanged();
			activity.invalidateOptionsMenu();
			if (clusters.isEmpty()) {
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
