package yoavbz.dupimg.background;

import android.animation.ObjectAnimator;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
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

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static yoavbz.dupimg.MainActivity.TAG;

public class ClassificationTask extends AsyncTask<String, Void, List<Cluster<Image>>> {

	private final int NOTIFICATION_ID = 1;
	private final WeakReference<MainActivity> weakReference;
	private Notification.Builder mBuilder;
	private AtomicBoolean isPreviewing = new AtomicBoolean(false);
	private AdditiveAnimator animation;
	private int scanned = 0;
	private int total = 0;

	public ClassificationTask(MainActivity mainActivity) {
		weakReference = new WeakReference<>(mainActivity);
	}

	static List<String> fetchLocalImages(@NonNull String... paths) {
		List<String> localImages = new ArrayList<>();
		for (String path : paths) {
			File[] files = new File(path).listFiles((dir, name) -> name.endsWith("jpg"));
			Log.d(TAG, "ClassificationTask: Found " + files.length + " images on " + path);
			for (File file : files) {
				localImages.add(file.getAbsolutePath());
			}
		}
		return localImages;
	}

	@Override
	protected void onPreExecute() {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			Log.d(TAG, "ClassificationTask: Starting image classification asynchronously..");
			// Handling menu items
			activity.isAsyncTaskRunning.compareAndSet(false, true);
			activity.invalidateOptionsMenu();

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
		channel.setDescription("Notification the appears only while the classification task is running.\n" +
				                       "Indicates the task progress.");
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
	 * @param paths Contains directory paths to scan in case of a custom scan, null otherwise
	 * @return null
	 */
	@Override
	protected List<Cluster<Image>> doInBackground(String... paths) {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			try (ImageClassifier classifier = new ImageClassifier(activity, "mobilenet_v2_1.0_224_quant.tflite",
			                                                      "labels.txt", 224)) {
				ImageDatabase db = ImageDatabase.getAppDatabase(activity);

				// Checking scanning mode (regular/custom) and getting the dir paths
				if (paths.length == 0) {
					activity.isCustomScan.compareAndSet(true, false);
					// In case no custom path was delivered - use default value
					Set<String> dirs = PreferenceManager.getDefaultSharedPreferences(activity)
					                                    .getStringSet("dirs", Collections.emptySet());
					paths = dirs.toArray(new String[0]);
				} else {
					activity.isCustomScan.compareAndSet(false, true);
				}

				// Fetching local images on each dir selected
				checkCancellation();
				List<String> localImages = fetchLocalImages(paths);
				total = localImages.size();

				// Updating ProgressBars
				checkCancellation();
				activity.runOnUiThread(() -> {
					String str = String.format(Locale.ENGLISH, "Scanning %d images..", total);
					activity.textView.setText(str);
					Log.d(TAG, "doInBackground: " + str);
					activity.progressBar.setIndeterminate(false);
					activity.progressBar.setProgress(0);
					activity.progressBar.setMax(total);
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
						Log.d(TAG, "ClassificationTask: Inserting " + newImages.size() + " images to DB");
						db.imageDao().insert(newImages);
					} else {
						Log.d(TAG, "ClassificationTask: No new images..");
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
					for (String path : localImages) {
						checkCancellation();
						Image image = new Image(path, activity, classifier);
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
				Log.e(TAG, "ClassificationTask: Got an exception", e);
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
	private void animatePreview(@NonNull AppCompatActivity activity, Image image) {
		if (!isPreviewing.get()) {
			isPreviewing.compareAndSet(false, true);
			ImageView preview = activity.findViewById(R.id.preview);
			ConstraintLayout layout = (ConstraintLayout) preview.getParent();
			activity.runOnUiThread(() -> {
				preview.setImageBitmap(image.getOrientedBitmap());
				preview.setVisibility(View.VISIBLE);
				float originalX = (layout.getWidth() - preview.getWidth()) * 0.75f;
				animation = AdditiveAnimator.animate(preview)
				                            // Alpha
				                            .alpha(1f)
				                            .setDuration(1000L)
				                            .thenBeforeEnd(1000L)
				                            // X transitioning
				                            .xBy(-(layout.getWidth() - preview.getWidth()) * 0.5f)
				                            .setDuration(2000L)
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

	private List<Image> getNewImages(AppCompatActivity activity, @NonNull ImageDao dao, ImageClassifier classifier,
	                                 @NonNull List<String> localImages) {
		ArrayList<Image> newImages = new ArrayList<>();
		// Filtering images that are already in the database
		checkCancellation();
		localImages.removeAll(dao.getAllPaths());
		// Deciding whether to show preview image or not
		boolean shouldAnimatePreview = localImages.size() > 5;
		for (String path : localImages) {
			checkCancellation();
			try {
				Image image = new Image(path, activity, classifier);
				newImages.add(image);
				if (shouldAnimatePreview) {
					animatePreview(activity, image);
				}
			} catch (Exception e) {
				Log.e(TAG, "getNewImages: ", e);
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
			activity.textView.setText(String.format(Locale.ENGLISH, "Scanned %d/%d images..", ++scanned, total));
			mBuilder.setProgress(activity.progressBar.getMax(), activity.progressBar.getProgress(), false);
			activity.notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
		}
	}

	@Override
	protected void onPostExecute(List<Cluster<Image>> clusters) {
		Log.d(TAG, "ClassificationTask: Clustered " + clusters.size() + " clusters");
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
		}
	}

	private void checkCancellation() {
		if (isCancelled()) {
			throw new CancellationException();
		}
	}

	@Override
	protected void onCancelled() {
		Log.d(TAG, "ClassificationTask - onCancelled: Cancelling task");
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
