package yoavbz.dupimg.background;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class ClassificationTask extends AsyncTask<Uri, Void, List<Cluster<Image>>> {

	private static final int NOTIFICATION_ID = 1;
	private final WeakReference<MainActivity> weakReference;
	private Notification.Builder mBuilder;

	public ClassificationTask(MainActivity mainActivity) {
		weakReference = new WeakReference<>(mainActivity);
	}

	@Override
	protected void onPreExecute() {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			Log.d(MainActivity.TAG, "ClassificationTask: Starting image classification asynchronously..");

			activity.galleryView.setVisibility(View.GONE);
			activity.progressBar.setVisibility(View.VISIBLE);
			activity.textView.setVisibility(View.VISIBLE);
			activity.invalidateOptionsMenu();
			setNotificationChannel(activity);
			mBuilder = new Notification.Builder(activity, "dupImg")
					.setSmallIcon(R.drawable.ic_gallery)
					.setContentTitle("Scanning images..")
					.setOngoing(true);
			activity.notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
		}
	}

	private void setNotificationChannel(MainActivity activity) {
		NotificationChannel channel = new NotificationChannel("dupImg", "Scanning Progress",
		                                                      NotificationManager.IMPORTANCE_MIN);
		channel.enableLights(false);
		channel.enableVibration(false);
		activity.notificationManager.createNotificationChannel(channel);
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
			try (ImageClassifier classifier = new ImageClassifier(activity, "mobilenet_v2_1.0_224_quant.tflite",
			                                                      "labels.txt", 224)) {
				ImageDatabase db = ImageDatabase.getAppDatabase(activity);

				Uri dirUri = uris[0];
				if (dirUri == null) {
					activity.isCustomScan = false;
					// In case no custom uri was delivered - use default value
					String uriString = PreferenceManager.getDefaultSharedPreferences(activity)
					                                    .getString("dirUri", null);
					dirUri = Uri.parse(uriString);
				} else {
					activity.isCustomScan = true;
				}
				DocumentFile[] docs = DocumentFile.fromTreeUri(activity, dirUri).listFiles();

				activity.runOnUiThread(() -> {
					activity.textView.setText("Scanning " + docs.length + " files..");
					activity.progressBar.setIndeterminate(true);
					mBuilder.setProgress(docs.length, 0, true);
				});
				Log.d(MainActivity.TAG, "ClassificationTask: Found " + docs.length + " images on " + dirUri.getPath());

				// Fetching all the images from the given directory
				List<DocumentFile> localImages = new ArrayList<>();
				for (DocumentFile doc : docs) {
					String type = doc.getType();
					if (!isCancelled() && type != null && type.equals("image/jpeg")) {
						localImages.add(doc);
					}
				}

				activity.runOnUiThread(() -> {
					activity.progressBar.setIndeterminate(false);
					activity.progressBar.setProgress(0);
					activity.progressBar.setMax(docs.length);
					mBuilder.setProgress(activity.progressBar.getMax(),
					                     activity.progressBar.getProgress(), false);
				});
				DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.65, 2);
				List<Image> toClusters;

				if (!activity.isCustomScan) {
					// Regular Scan
					// Removing from the database images that were deleted from local directory
					db.imageDao().deleteNotInList(localImages);
					List<Image> newImages = getNewImages(activity, db.imageDao(), classifier, localImages);
					if (!newImages.isEmpty()) {
						Log.d(MainActivity.TAG,
						      "ClassificationTask: Inserting " + newImages.size() + " images to DB");
						db.imageDao().insert(newImages);
					} else {
						Log.d(MainActivity.TAG, "ClassificationTask: No new images..");
					}
					toClusters = db.imageDao().getAll();
				} else {
					// Custom scan
					toClusters = new ArrayList<>();
					for (DocumentFile file : localImages) {
						if (!isCancelled()) {
							toClusters.add(new Image(file, activity, classifier));
						}
						publishProgress();
					}
				}
				return clusterer.cluster(toClusters);
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "ClassificationTask: Got an exception", e);
				cancel(true);
			}
		}
		return new ArrayList<>();
	}

	private List<Image> getNewImages(Context context, ImageDao dao, ImageClassifier classifier, List<DocumentFile> localImages) {
		ArrayList<Image> newImages = new ArrayList<>();
		// Filtering images that are already in the database
		localImages.removeIf(file -> dao.getAllUris().contains(file.getUri()));
		for (DocumentFile newImage : localImages) {
			try {
				Image image = new Image(newImage, context, classifier);
				newImages.add(image);
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "getNewImages: ", e);
			}
			publishProgress();
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
		Log.d(MainActivity.TAG, "ClassificationTask: Clustered " + clusters.size() + " clusters");
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.notificationManager.cancel(NOTIFICATION_ID);
			activity.progressBar.setVisibility(View.GONE);
			activity.galleryView.setVisibility(View.VISIBLE);
			activity.galleryView.setImageClusters(clusters);
			activity.invalidateOptionsMenu();
		}
	}

	@Override
	protected void onCancelled() {
		Log.d(MainActivity.TAG, "ClassificationTask - onCancelled: Cancelling task");
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.notificationManager.cancel(NOTIFICATION_ID);
			activity.textView.setText("Got an error :(");
			activity.textView.setVisibility(View.VISIBLE);
			activity.progressBar.setVisibility(View.GONE);
			activity.invalidateOptionsMenu();
		}
	}
}
