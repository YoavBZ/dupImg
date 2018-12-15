package yoavbz.dupimg.background;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import yoavbz.dupimg.ImageClassifier;
import yoavbz.dupimg.MainActivity;
import yoavbz.dupimg.R;
import yoavbz.dupimg.database.ImageDao;
import yoavbz.dupimg.database.ImageDatabase;
import yoavbz.dupimg.gallery.ImageClusterActivity;
import yoavbz.dupimg.models.Image;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class NotificationJobService extends JobService {

	private static final String PRIMARY_CHANNEL_ID = "dupImg";
	private NotificationManager mNotifyManager;
	private ImageClassifier classifier;

	@Override
	public boolean onStartJob(JobParameters params) {
		Log.d(MainActivity.TAG, "NotificationJobService: onStartJob");
		mNotifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		new Thread(() -> {
			createNotificationChannel();
			try {
				classifier = new ImageClassifier(NotificationJobService.this,
				                                 "mobilenet_v2_1.0_224_quant.tflite", "labels.txt",
				                                 224);

				SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
				boolean isFirstRun = pref.getBoolean("firstRun", true);

				pref.edit().putBoolean("firstRun", false).apply();

				// Do nothing in case of first run
				if (isFirstRun) {
					return;
				}

				ImageDao db = ImageDatabase.getAppDatabase(this).imageDao();
				// Fetching all the images from the camera directory
				List<String> localImages = getLocalImages();
				// Removing from the database images that were deleted from local directory
				db.deleteNotInList(localImages);
				// Fetching all the images from the database
				List<Image> dbImages = db.getAll();
				// Filtering new local images, which aren't in the database
				List<Image> newImages = getNewImages(localImages, dbImages);
				if (newImages.isEmpty()) {
					Log.d(MainActivity.TAG, "NotificationJobService: No new images, finishing job..");
					return;
				}
				db.insert(newImages);

				NotificationCompat.Builder builder = new NotificationCompat.Builder(this, PRIMARY_CHANNEL_ID)
						.setContentTitle("dupImg")
						.setSmallIcon(R.drawable.ic_menu_gallery)
						.setShowWhen(true)
						.setContentText("Click to select which images to keep")
						.setAutoCancel(true)
						.setGroup("dupImg")
						.setPriority(NotificationCompat.PRIORITY_HIGH);

				DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.85, 2);
				// Construct image clusters and send notifications if necessary
				processClusters(clusterer.cluster(newImages), builder);
			} catch (IOException e) {
				Log.e(MainActivity.TAG, "NotificationJobService: Got an exception while constructing classifier", e);
			} finally {
				jobFinished(params, false);
			}
		}).start();

		return true;
	}

	private List<Image> getNewImages(List<String> localImages, List<Image> dbImages) {
		ArrayList<Image> newImages = new ArrayList<>();
		// Remove images that are already in the database from the local images list, to filter new ones
		localImages.removeIf((String fileName) -> dbImages.stream().anyMatch((Image image) -> fileName.equals(
				image.getPath().toString())));
		for (String newImage : localImages) {
			Image image = new Image(newImage, classifier);
			newImages.add(image);
		}
		return newImages;
	}

	private List<String> getLocalImages() {
		List<String> images = new ArrayList<>();
		String dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
		Path cameraDir = Paths.get(dcim, "Camera");
		File[] files = cameraDir.toFile().listFiles((dir, name) -> name.endsWith(".jpg"));
		for (File file : files) {
			images.add(file.getAbsolutePath());
		}
		return images;
	}

	private void processClusters(List<Cluster<Image>> clusters, NotificationCompat.Builder builder) {
		for (int i = 0; i < clusters.size(); i++) {
			ArrayList<Image> images = (ArrayList<Image>) clusters.get(i).getPoints();
			Intent intent = new Intent(this, ImageClusterActivity.class);
			intent.putParcelableArrayListExtra("IMAGES", images);
			PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
			                                                        PendingIntent.FLAG_UPDATE_CURRENT);
			Bitmap previewImg = images.get(0).getBitmap();
			builder.setContentIntent(pendingIntent)
			       .setContentTitle("Found " + images.size() + " new duplicates!")
			       .setLargeIcon(previewImg)
			       .setStyle(new NotificationCompat.BigPictureStyle()
					                 .bigPicture(previewImg)
					                 .bigLargeIcon(null));
			mNotifyManager.notify(i, builder.build());
		}
	}

	private void createNotificationChannel() {
		// Create the NotificationChannel with all the parameters.
		NotificationChannel notificationChannel = new NotificationChannel(
				PRIMARY_CHANNEL_ID, "Background Notification", NotificationManager.IMPORTANCE_HIGH);
		notificationChannel.enableLights(false);
		notificationChannel.setDescription("Notifications from Job Service");

		mNotifyManager.createNotificationChannel(notificationChannel);
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		Log.d(MainActivity.TAG, "NotificationJobService: onStopJob");
		return false;
	}
}
