package yoavbz.dupimg.background;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
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
import android.support.constraint.ConstraintLayout;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import at.wirecube.additiveanimations.additive_animator.AdditiveAnimator;
import at.wirecube.additiveanimations.additive_animator.AnimationEndListener;
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
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressLint("DefaultLocale")
public class ClassificationTask extends AsyncTask<Uri, Void, List<Cluster<Image>>> {

	private final int NOTIFICATION_ID = 1;
	private final WeakReference<MainActivity> weakReference;
	private Notification.Builder mBuilder;
	private JobInfo job;
	private JobScheduler scheduler;
	private AtomicBoolean isPreviewing = new AtomicBoolean(false);
	private AdditiveAnimator animation;
	private int scanned = 0;
	private int total;

	public ClassificationTask(MainActivity mainActivity) {
		weakReference = new WeakReference<>(mainActivity);
	}

	@Override
	protected void onPreExecute() {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			Log.d(MainActivity.TAG, "ClassificationTask: Starting image classification asynchronously..");
			// Handling menu items
			activity.isAsyncTaskRunning.compareAndSet(false, true);
			activity.invalidateOptionsMenu();

			// Stopping JobScheduler while classifying
			scheduler = activity.getSystemService(JobScheduler.class);
			job = scheduler.getPendingJob(MainActivity.JOB_ID);
			if (job != null) {
				scheduler.cancel(MainActivity.JOB_ID);
			}
			// Modifying views visibility
			activity.galleryView.setVisibility(View.GONE);
			activity.progressBar.setVisibility(View.VISIBLE);
			activity.textView.setVisibility(View.VISIBLE);

			initNotification(activity);
		}
	}

	private void initNotification(@NonNull MainActivity activity) {
		NotificationChannel channel = new NotificationChannel("dupImg", "Scanning Progress",
		                                                      NotificationManager.IMPORTANCE_MIN);
		channel.enableLights(false);
		channel.enableVibration(false);
		activity.notificationManager.createNotificationChannel(channel);
		mBuilder = new Notification.Builder(activity, "dupImg")
				.setSmallIcon(R.drawable.ic_gallery)
				.setContentTitle("Scanning images..")
				.setOngoing(true);
		activity.notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
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

				// TODO: Handle multiple directories (Uris)
				// Checking scanning mode (regular/custom) and getting the dirUri
				Uri dirUri = uris[0];
				if (dirUri == null) {
					activity.isCustomScan.compareAndSet(true, false);
					// In case no custom uri was delivered - use default value
					String uriString = PreferenceManager.getDefaultSharedPreferences(activity)
					                                    .getString("dirUri", null);
					dirUri = Uri.parse(uriString);
				} else {
					activity.isCustomScan.compareAndSet(false, true);
				}

				// Fetching local images on dirUri
				checkCancellation();
				ArrayList<Uri> localImages = fetchLocalUris(activity, dirUri);
				total = localImages.size();
				Log.d(MainActivity.TAG, "ClassificationTask: Found " + total + " images on " + dirUri.getPath());

				// Updating ProgressBars
				checkCancellation();
				activity.runOnUiThread(() -> {
					activity.textView.setText(String.format("Scanning %d images..", total));
					activity.progressBar.setIndeterminate(false);
					activity.progressBar.setProgress(0);
					activity.progressBar.setMax(localImages.size());
					mBuilder.setProgress(activity.progressBar.getMax(),
					                     activity.progressBar.getProgress(), false);
				});

				// Initiating clusterer and list of images to scan
				DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.65, 2);
				List<Image> imagesToClusters;
				checkCancellation();

				// Processing images according to scanning mode
				if (!activity.isCustomScan.get()) {
					// Regular Scan

					// Removing (from DB) images that were deleted from local directory
					db.imageDao().deleteNotInList(localImages);

					// Inserting new images to the DB
					List<Image> newImages = getNewImages(activity, db.imageDao(), classifier, localImages);
					if (!newImages.isEmpty()) {
						Log.d(MainActivity.TAG, "ClassificationTask: Inserting " + newImages.size() + " images to DB");
						db.imageDao().insert(newImages);
					} else {
						Log.d(MainActivity.TAG, "ClassificationTask: No new images..");
					}

					// Finishing scan, animating ProgressBars
					checkCancellation();
					activity.runOnUiThread(() -> {
						ObjectAnimator.ofInt(activity.progressBar, "progress",
						                     activity.progressBar.getMax())
						              .setDuration(400)
						              .start();
						mBuilder.setProgress(0, 0, true);
						activity.notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
					});
					imagesToClusters = db.imageDao().getAll();
				} else {
					// Custom scan

					// Iterating local images
					imagesToClusters = new ArrayList<>();
					for (Uri uri : localImages) {
						checkCancellation();
						Image image = new Image(uri, activity, classifier);
						imagesToClusters.add(image);
						animatePreview(activity, image);
						publishProgress();
					}
				}
				checkCancellation();

				// Clustering!
				List<Cluster<Image>> clusters = clusterer.cluster(imagesToClusters);

				// Sorting images in each cluster
				Comparator<Image> imageComparator = (image1, image2) ->
						// Comparing using getDateTaken(activity), to extract date from files if needed
						Long.compare(image1.getDateTaken(activity), (image2.getDateTaken(activity)));
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

	/**
	 * Iteratively animating a preview image while scanning
	 *
	 * @param activity The activity {@link MainActivity}
	 * @param image    The {@link Image} to preview
	 */
	private void animatePreview(@NonNull Activity activity, Image image) {
		if (!isPreviewing.get()) {
			isPreviewing.compareAndSet(false, true);
			ImageView preview = activity.findViewById(R.id.preview);
			ConstraintLayout layout = (ConstraintLayout) preview.getParent();
			activity.runOnUiThread(() -> {
				preview.setImageBitmap(image.getOrientedBitmap(activity));
				preview.setVisibility(View.VISIBLE);
				float originalX = preview.getX();
				animation = AdditiveAnimator.animate(preview)
				                            // Alpha
				                            .alpha(1f)
				                            .setDuration(1000L)
				                            .thenBeforeEnd(1000L)
				                            // X transitioning
				                            .xBy(-(layout.getWidth() - preview.getWidth()) * 0.5f)
				                            .setDuration(1700L)
				                            .thenBeforeEnd(600L)
				                            // Alpha
				                            .alpha(0f)
				                            .setDuration(600L)
				                            .addEndAction(new AnimationEndListener() {
					                            @Override
					                            public void onAnimationEnd(boolean wasCancelled) {
						                            preview.setX(originalX);
						                            isPreviewing.compareAndSet(true, false);
					                            }
				                            });
				animation.start();
			});
		}
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

	private List<Image> getNewImages(Activity activity, @NonNull ImageDao dao, ImageClassifier classifier,
	                                 @NonNull List<Uri> localImages) {
		ArrayList<Image> newImages = new ArrayList<>();
		// Filtering images that are already in the database
		checkCancellation();
		localImages.removeAll(dao.getAllUris());
		// Deciding whether to show preview image or not
		boolean shouldAnimatePreview = localImages.size() > 5;
		for (Uri uri : localImages) {
			checkCancellation();
			try {
				Image image = new Image(uri, activity, classifier);
				newImages.add(image);
				if (shouldAnimatePreview) {
					animatePreview(activity, image);
				}
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
			activity.textView.setText(String.format("Scanned %d/%d images..", ++scanned, total));
			mBuilder.setProgress(activity.progressBar.getMax(), activity.progressBar.getProgress(), false);
			activity.notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
		}
	}

	@Override
	protected void onPostExecute(List<Cluster<Image>> clusters) {
		Log.d(MainActivity.TAG, "ClassificationTask: Clustered " + clusters.size() + " clusters");
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.isAsyncTaskRunning.compareAndSet(true, false);
			// Hide preview
			if (animation != null) {
				activity.findViewById(R.id.preview).setVisibility(View.GONE);
				animation.cancelAllAnimations();
			}
			activity.notificationManager.cancel(NOTIFICATION_ID);
			// Sorting clusters by date
			clusters.sort((Cluster<Image> cluster1, Cluster<Image> cluster2) -> {
				long date1 = cluster1.getPoints().get(0).getDateTaken();
				long date2 = cluster2.getPoints().get(0).getDateTaken();
				return Long.compare(date2, date1);
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

	private void checkCancellation() {
		if (isCancelled()) {
			throw new CancellationException();
		}
	}

	@Override
	protected void onCancelled() {
		Log.d(MainActivity.TAG, "ClassificationTask - onCancelled: Cancelling task");
		MainActivity activity = weakReference.get();
		if (activity != null) {
			// Handling menu items
			activity.isAsyncTaskRunning.compareAndSet(true, false);
			activity.invalidateOptionsMenu();
			// Hiding preview
			if (animation != null) {
				activity.findViewById(R.id.preview).setVisibility(View.GONE);
				animation.cancelAllAnimations();
			}
			activity.notificationManager.cancel(NOTIFICATION_ID);
			activity.textView.setText(activity.getString(R.string.got_an_error));
			activity.textView.setVisibility(View.VISIBLE);
			activity.progressBar.setVisibility(View.GONE);
		}
	}
}
