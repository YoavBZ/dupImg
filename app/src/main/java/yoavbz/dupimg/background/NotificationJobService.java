package yoavbz.dupimg.background;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.provider.DocumentFile;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class NotificationJobService extends JobService {

	private NotificationManager mNotifyManager;
	private ImageClassifier classifier;
	private ImageDao db;
	private Thread thread;

	@Override
	public boolean onStartJob(JobParameters params) {
		Log.d(MainActivity.TAG, "NotificationJobService: onStartJob");
		mNotifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		thread = new Thread(() -> {
			createNotificationChannel();
			boolean updateUi = false;
			try {
				classifier = new ImageClassifier(NotificationJobService.this,
				                                 "mobilenet_v2_1.0_224_quant.tflite", "labels.txt",
				                                 224);

				db = ImageDatabase.getAppDatabase(this).imageDao();
				// Fetching all the images from the camera directory
				List<Uri> localImages = getLocalImages();
				// Removing from the database images that were deleted from local directory
				boolean deletedImages = db.deleteNotInList(localImages);
				if (deletedImages) {
					updateUi = true;
				}
				// Filtering new local images, which aren't in the database
				List<Image> newImages = getNewImages(localImages);
				if (newImages.isEmpty()) {
					Log.d(MainActivity.TAG, "NotificationJobService: No new images, finishing job..");
					return;
				}
				db.insert(newImages);

				NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "dupImg")
						.setContentTitle("dupImg")
						.setSmallIcon(R.drawable.ic_menu_gallery)
						.setShowWhen(true)
						.setContentText("Click to select which images to keep")
						.setAutoCancel(true)
						.setGroup("dupImg")
						.setPriority(NotificationCompat.PRIORITY_HIGH);

				DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.7, 2);
				// Constructing image clusters and send notifications if necessary
				List<Cluster<Image>> clusters = clusterer.cluster(newImages);
				processClusters(clusters, builder);
				// Updating UI in case of new clusters
				if (!clusters.isEmpty()) {
					updateUi = true;
				}
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "NotificationJobService: Got an exception", e);
			} finally {
				classifier.close();
				if (updateUi) {
					Intent intent = new Intent(MainActivity.ACTION_UPDATE_UI);
					LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
				}
				jobFinished(params, false);
			}
		});
		thread.start();
		return true;
	}

	private List<Image> getNewImages(List<Uri> localImages) {
		ArrayList<Image> newImages = new ArrayList<>();
		// Filtering images that are already in the database
		localImages.removeAll(db.getAllUris());
		for (Uri newImage : localImages) {
			Image image;
			try {
				image = new Image(DocumentFile.fromSingleUri(this, newImage), this, classifier);
				newImages.add(image);
			} catch (IOException e) {
				Log.e(MainActivity.TAG, "getNewImages: ", e);
			}
		}
		return newImages;
	}

	private List<Uri> getLocalImages() {
		List<Uri> images = new ArrayList<>();
		String uriString = PreferenceManager.getDefaultSharedPreferences(this)
		                                    .getString("dirUri", null);
		Uri uri = Uri.parse(uriString);
		DocumentFile[] docs = DocumentFile.fromTreeUri(NotificationJobService.this, uri).listFiles();
		for (DocumentFile doc : docs) {
			String type = doc.getType();
			if (type != null && type.equals("image/jpeg")) {
				images.add(doc.getUri());
			}
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
			try (InputStream inputStream = getContentResolver().openInputStream(images.get(0).getUri())) {
				Bitmap previewImg = BitmapFactory.decodeStream(inputStream);
//			Bitmap previewImg = images.get(0).getBitmap(inputStream);
				builder.setContentIntent(pendingIntent)
				       .setContentTitle("Found " + images.size() + " new duplicates!")
				       .setLargeIcon(previewImg)
				       .setStyle(new NotificationCompat.BigPictureStyle()
						                 .bigPicture(previewImg)
						                 .bigLargeIcon(null));
				mNotifyManager.notify(i, builder.build());
			} catch (IOException e) {
				Log.e(MainActivity.TAG, "processClusters: ", e);
			}
		}
	}

	private void createNotificationChannel() {
		// Create the NotificationChannel with all the parameters.
		NotificationChannel notificationChannel = new NotificationChannel(
				"dupImg", "Background Notification", NotificationManager.IMPORTANCE_HIGH);
		notificationChannel.enableLights(false);
		notificationChannel.setDescription("Notifications from Job Service");

		mNotifyManager.createNotificationChannel(notificationChannel);
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
