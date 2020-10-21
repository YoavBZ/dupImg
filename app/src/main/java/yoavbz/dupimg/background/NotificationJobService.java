package yoavbz.dupimg.background;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import yoavbz.dupimg.Image;
import yoavbz.dupimg.MainActivity;
import yoavbz.dupimg.R;
import yoavbz.dupimg.database.ImageDao;
import yoavbz.dupimg.database.ImageDatabase;
import yoavbz.dupimg.gallery.ImageClusterActivity;

public class NotificationJobService extends JobService {

	private NotificationManager notificationManager;
	private ImageDao db;
	private Thread thread;

	private static void checkInterrupt(@NonNull Thread thread) throws InterruptedException {
		if (thread.isInterrupted()) {
			throw new InterruptedException();
		}
	}

	@Override
	public boolean onStartJob(JobParameters params) {
		Log.d(MainActivity.TAG, "NotificationJobService: onStartJob");
		notificationManager = getSystemService(NotificationManager.class);
		thread = new Thread(() -> {
			createNotificationChannel();
			boolean updateUi = false;
			try (ImageClassifier classifier = new ImageClassifier(NotificationJobService.this,
			                                                      "mobilenet_v2_1.0_224_quant.tflite",
			                                                      "labels.txt", 224)) {
				checkInterrupt(thread);
				db = ImageDatabase.getAppDatabase(NotificationJobService.this).imageDao();
				checkInterrupt(thread);
				// Fetching all the images from the camera directory
				List<String> localImages = getLocalImages();
				checkInterrupt(thread);
				// Removing from the database images that were deleted from local directory
				boolean deletedImages = db.deleteNotInList(localImages);
				if (deletedImages) {
					updateUi = true;
				}
				checkInterrupt(thread);
				// Filtering new local images, which aren't in the database
				List<Image> newImages = getNewImages(localImages, classifier);
				if (newImages.isEmpty()) {
					Log.d(MainActivity.TAG, "NotificationJobService: No new images, finishing job..");
					return;
				}
				db.insert(newImages);

				NotificationCompat.Builder builder = new NotificationCompat.Builder(NotificationJobService.this,
				                                                                    "dupImg")
						.setContentTitle("dupImg")
						.setSmallIcon(R.drawable.ic_gallery)
						.setShowWhen(true)
						.setContentText("Click to select which images to keep")
						.setAutoCancel(true)
						.setGroup("dupImg");

				DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.7, 2);
				// Constructing image clusters and send notifications if necessary
				checkInterrupt(thread);
				List<Cluster<Image>> clusters = clusterer.cluster(newImages);
				processClusters(clusters, builder);
				// Updating UI in case of new clusters
				if (!clusters.isEmpty()) {
					updateUi = true;
				}
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "NotificationJobService: Got an exception", e);
			} finally {
				if (updateUi && !thread.isInterrupted()) {
					Intent intent = new Intent(MainActivity.ACTION_UPDATE_UI);
					LocalBroadcastManager.getInstance(NotificationJobService.this).sendBroadcast(intent);
				}
				jobFinished(params, false);
			}
		});
		thread.start();
		return true;
	}

	private List<Image> getNewImages(@NonNull List<String> localImages, ImageClassifier classifier) {
		ArrayList<Image> newImages = new ArrayList<>();
		// Filtering images that are already in the database
		localImages.removeAll(db.getAllPaths());
		for (String path : localImages) {
			try {
				Image image = new Image(path, this, classifier);
				newImages.add(image);
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "getNewImages: ", e);
			}
		}
		return newImages;
	}

	private List<String> getLocalImages() {
		Set<String> dirs = PreferenceManager.getDefaultSharedPreferences(this)
		                                    .getStringSet("dirs", Collections.emptySet());
		return ClassificationTask.fetchLocalImages((String[]) dirs.toArray(new String[0]));
	}

	private void processClusters(@NonNull List<Cluster<Image>> clusters, NotificationCompat.Builder builder) {
		for (Cluster<Image> cluster : clusters) {
			List<String> paths = cluster.getPoints().stream().map(Image::getPath).collect(Collectors.toList());
			Intent intent = new Intent(this, ImageClusterActivity.class);
			intent.putStringArrayListExtra("IMAGES", (ArrayList<String>) paths);
			int id = (int) SystemClock.uptimeMillis();
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_ONE_SHOT);
			Image previewImg = cluster.getPoints().get(0);
			Bitmap orientedBitmap = previewImg.getOrientedBitmap();
			builder.setContentIntent(pendingIntent)
			       .setContentTitle("Found " + paths.size() + " new duplicates!")
			       .setLargeIcon(orientedBitmap)
			       .setStyle(new NotificationCompat.BigPictureStyle()
					                 .bigPicture(orientedBitmap)
					                 .bigLargeIcon(null));
			notificationManager.notify(id, builder.build());
		}
	}

	private void createNotificationChannel() {
		// Create the NotificationChannel with all the parameters.
		NotificationChannel notificationChannel = new NotificationChannel("dupImg", "Background Notification",
		                                                                  NotificationManager.IMPORTANCE_HIGH);
		notificationChannel.setDescription("Notifications from the background scanning service.");

		notificationManager.createNotificationChannel(notificationChannel);
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		Log.d(MainActivity.TAG, "NotificationJobService: onStopJob");
		if (thread != null) {
			thread.interrupt();
		}
		return false;
	}
}
