package yoavbz.dupimg.background;

import android.animation.ObjectAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.provider.DocumentsContract;
import android.support.annotation.NonNull;
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
import java.util.Comparator;
import java.util.Date;
import java.util.List;

public class ClassificationTask extends AsyncTask<Uri, Void, List<Cluster<Image>>> {

	private static final int NOTIFICATION_ID = 1;
	private final WeakReference<MainActivity> weakReference;
	private Notification.Builder mBuilder;
	private JobInfo job;
	private JobScheduler scheduler;

	public ClassificationTask(MainActivity mainActivity) {
		weakReference = new WeakReference<>(mainActivity);
	}

	@Override
	protected void onPreExecute() {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			Log.d(MainActivity.TAG, "ClassificationTask: Starting image classification asynchronously..");

			scheduler = (JobScheduler) activity.getSystemService(Context.JOB_SCHEDULER_SERVICE);
			// Stopping JobScheduler while classifying
			job = scheduler.getPendingJob(MainActivity.JOB_ID);
			if (job != null) {
				scheduler.cancel(MainActivity.JOB_ID);
			}
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
				ArrayList<Uri> localImages = fetchLocalUris(activity, dirUri);
				int size = localImages.size();
				Log.d(MainActivity.TAG,
				      "ClassificationTask: Found " + size + " images on " + dirUri.getPath());

				activity.runOnUiThread(() -> {
					activity.textView.setText("Scanning " + size + " files..");
					activity.progressBar.setIndeterminate(false);
					activity.progressBar.setProgress(0);
					activity.progressBar.setMax(localImages.size() * 2);
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
					// Animating ProgressBars
					activity.runOnUiThread(() -> {
						ObjectAnimator.ofInt(activity.progressBar, "progress",
						                     activity.progressBar.getMax())
						              .setDuration(400)
						              .start();
						mBuilder.setProgress(0, 0, true);
						activity.notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
					});
					toClusters = db.imageDao().getAll();
				} else {
					// Custom scan
					toClusters = new ArrayList<>();
					for (Uri uri : localImages) {
						if (!isCancelled()) {
							toClusters.add(new Image(uri, activity, classifier));
						}
						publishProgress();
					}
				}
				List<Cluster<Image>> clusters = clusterer.cluster(toClusters);
				// Sorting images in each cluster
				Comparator<Image> imageComparator = (image1, image2) ->
						image1.getDateTaken(activity).compareTo(image2.getDateTaken(activity));
				for (Cluster<Image> cluster : clusters) {
					cluster.getPoints().sort(imageComparator);
				}
				return clusters;
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "ClassificationTask: Got an exception", e);
				cancel(true);
			}
		}
		return new ArrayList<>();
	}

	static ArrayList<Uri> fetchLocalUris(@NonNull Context context, Uri dir) {
		ContentResolver contentResolver = context.getContentResolver();
		ArrayList<Uri> uris = new ArrayList<>();
		Uri child = DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getTreeDocumentId(dir));

		try (Cursor c = contentResolver.query(child, new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
		                                                          DocumentsContract.Document.COLUMN_MIME_TYPE},
		                                      null, null, null)) {
			while (c.moveToNext()) {
				final String id = c.getString(0);
				final String mime = c.getString(1);
				if ("image/jpeg".equals(mime)) {
					uris.add(DocumentsContract.buildDocumentUriUsingTree(dir, id));
				}
			}
		}
		return uris;
	}

	private List<Image> getNewImages(Context context, @NonNull ImageDao dao, ImageClassifier classifier,
	                                 @NonNull List<Uri> localImages) {
		ArrayList<Image> newImages = new ArrayList<>();
		// Filtering images that are already in the database
		localImages.removeAll(dao.getAllUris());
		for (Uri uri : localImages) {
			try {
				Image image = new Image(uri, context, classifier);
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
			// Sorting clusters by date
			clusters.sort((Cluster<Image> cluster1, Cluster<Image> cluster2) -> {
				Date date1 = cluster1.getPoints().get(0).getDateTaken();
				Date date2 = cluster2.getPoints().get(0).getDateTaken();
				return date2.compareTo(date1);
			});
			activity.galleryView.setImageClusters(clusters);
			activity.progressBar.setVisibility(View.GONE);
			activity.galleryView.setVisibility(View.VISIBLE);
			activity.invalidateOptionsMenu();
			if (job != null) {
				scheduler.schedule(job);
			}
		}
	}

	@Override
	protected void onCancelled() {
		Log.d(MainActivity.TAG, "ClassificationTask - onCancelled: Cancelling task");
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.notificationManager.cancel(NOTIFICATION_ID);
			activity.textView.setText(activity.getString(R.string.got_an_error));
			activity.textView.setVisibility(View.VISIBLE);
			activity.progressBar.setVisibility(View.GONE);
			activity.invalidateOptionsMenu();
		}
	}
}
