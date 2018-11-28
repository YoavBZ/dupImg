package yoavbz.dupimg;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import yoavbz.dupimg.background.NotificationJobService;
import yoavbz.dupimg.database.ImageDao;
import yoavbz.dupimg.database.ImageDatabase;
import yoavbz.dupimg.gallery.ImageClusterActivity;
import yoavbz.dupimg.gallery.MediaGalleryView;
import yoavbz.dupimg.models.Image;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener, MediaGalleryView.OnImageClicked {

	public static final String TAG = "dupImg";
	private List<Image> list = new ArrayList<>();
	private List<Cluster<Image>> clusters = new ArrayList<>();
	private MediaGalleryView galleryView;
	private AsyncTask asyncTask;

	/**
	 * Launching IntoActivity in case of first use, otherwise continuing normally.
	 *
	 * @param savedInstanceState Regular savedInstanceState parameter
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		if (pref.getBoolean("showIntro", true)) {
			Intent introIntent = new Intent(this, IntroActivity.class);
			startActivityForResult(introIntent, 1);
		} else {
			init();
		}
	}

	@Override
	protected void onDestroy() {
		// Cancel asyncTask if running
		if (asyncTask != null) {
			asyncTask.cancel(true);
		}
		super.onDestroy();
	}

	/**
	 * Handling the app initiation, after displaying the intro on first use:
	 * * Regular UI initiation.
	 * * Async images loading.
	 */
	private void init() {
		setContentView(R.layout.activity_main);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open,
		                                                         R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();

		NavigationView navigationView = findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		SwitchCompat monitorSwitch = (SwitchCompat) navigationView.getMenu().findItem(R.id.drawer_switch)
		                                                          .getActionView();

		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		boolean monitorScheduled = pref.getBoolean("isScheduled", false);
		Log.d(TAG, "MainActivity: Background service is " + (monitorScheduled ? "" : "not ") + "running");

		monitorSwitch.setChecked(monitorScheduled);
		monitorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
			if (isChecked) {
				// Turn on background jobService
				JobInfo job = new JobInfo.Builder(1, new ComponentName(getPackageName(),
				                                                       NotificationJobService.class.getName()))
						.setPeriodic(TimeUnit.MINUTES.toMillis(15))
						.build();
				scheduler.schedule(job);
			} else {
				// Turn off background jobService
				scheduler.cancel(1);
			}
			pref.edit().putBoolean("isScheduled", isChecked).apply();
		});

		galleryView = findViewById(R.id.gallery);
		galleryView.setImageClusters(clusters);
		galleryView.setOnImageClickListener(this);
		galleryView.setPlaceHolder(R.drawable.media_gallery_placeholder);

		new Thread(() -> {
			ImageDao db = ImageDatabase.getAppDatabase(MainActivity.this).imageDao();
			if (db.getImageCount() == 0) {
				rescanImages();
			} else {
				fetchAndCluster();
			}
		}).start();
	}

	/**
	 * Loading all images from the database, clustering them and displaying them
	 */
	private void fetchAndCluster() {
		asyncTask = new FetchAndCluster().execute();
	}

	/**
	 * Scanning all images in the Camera directory, classifying and clustering them, and displaying them
	 */
	private void rescanImages() {
		asyncTask = new ClassifyAndCluster().execute();
	}

	/**
	 * Starting the batch gallery activity
	 *
	 * @param pos The position of the image clicked
	 */
	@Override
	public void onImageClicked(int pos) {
		// Starting ImageClusterActivity with correct parameters
		Intent intent = new Intent(this, ImageClusterActivity.class);
		intent.putParcelableArrayListExtra("IMAGES", (ArrayList<Image>) clusters.get(pos).getPoints());
		startActivityForResult(intent, 0);
	}

	/**
	 * Handling return from the 'startActivityForResult' activities
	 *
	 * @param requestCode Activity identifier: ImageClusterActivity = 0 , IntroActivity = 1
	 * @param resultCode  RESULT_OK on success, otherwise RESULT_CANCELED
	 * @param data        Contains the result data
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 0 && resultCode == RESULT_OK) {
			// Returned from ImageClusterActivity with images to delete
			new Thread(() -> {
				ArrayList<Image> toDelete = data.getParcelableArrayListExtra("toDelete");
				ImageDao dao = ImageDatabase.getAppDatabase(this).imageDao();
				for (Image image : toDelete) {
					image.delete(MainActivity.this, dao);
				}
				fetchAndCluster();
			}).start();
		} else if (requestCode == 1) {
			if (resultCode == RESULT_OK) {
				// Intro finished successfully
				init();
			} else {
				// Intro finished unsuccessfully
				finish();
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		if (grantResults.length == 0) {
			return;
		}
		for (int result : grantResults) {
			if (result == PackageManager.PERMISSION_DENIED) {
				Toast.makeText(this, "Cannot launch app without permissions", Toast.LENGTH_LONG).show();
				finish();
				return;
			}
		}
	}

	/**
	 * When Back-key is being pressed, closing the drawer if opened, otherwise closing app.
	 */
	@Override
	public void onBackPressed() {
		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		if (drawer.isDrawerOpen(GravityCompat.START)) {
			drawer.closeDrawer(GravityCompat.START);
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.gallery_toolbar_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_rescan:
				rescanImages();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		// Handle navigation view item clicks here.
		switch (item.getItemId()) {
			case R.id.nav_photos:
				// All photos
				break;
			case R.id.nav_about:
				new AlertDialog.Builder(this)
						.setTitle("About")
						.setMessage("App repository is available at:\nhttps://github.com/YoavBZ\n\nHave fun!")
						.setNeutralButton("Ok", null)
						.show();
		}
		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);
		return true;
	}

	private class FetchAndCluster extends AsyncTask<Void, Integer, Void> {

		private ImageClassifier classifier;
		private ProgressBar progressBar;
		private ImageDao db;
		private TextView textView;

		@Override
		protected void onPreExecute() {
			list.clear();
			clusters.clear();
			Log.d(TAG, "MainActivity: fetchAndCluster: Fetching images from DB");
			db = ImageDatabase.getAppDatabase(MainActivity.this).imageDao();
			Log.d(TAG, "MainActivity: Starting image classification asynchronously..");
			try {
				classifier = new ImageClassifier(MainActivity.this, "mobilenet_v2_1.0_224_quant.tflite",
				                                 "labels.txt", 224);
			} catch (IOException e) {
				Log.e(TAG, "MainActivity: Couldn't build image classifier: " + e);
				cancel(true);
			}
			progressBar = findViewById(R.id.classification_progress);
			textView = findViewById(R.id.content_text);
			runOnUiThread(() -> {
				textView.setText("Loading images..");
				galleryView.notifyDataSetChanged();
				progressBar.setIndeterminate(true);
				progressBar.setVisibility(View.VISIBLE);
				textView.setVisibility(View.VISIBLE);
			});
		}

		@Override
		protected Void doInBackground(Void... voids) {
			try {
				list = db.getAll();
				Log.d(TAG, "MainActivity - fetchAndCluster: Got " + list.size() + " images");
				DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.85, 2);
				clusters = clusterer.cluster(list);
				Log.d(TAG, "MainActivity - fetchAndCluster: Clustered " + clusters.size() + " clusters");
			} catch (Exception e) {
				Log.e(TAG, "MainActivity - doInBackground: Got an exception!", e);
				cancel(true);
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			progressBar.setVisibility(View.GONE);
			textView.setVisibility(View.GONE);
			galleryView.setImageClusters(clusters);
			galleryView.notifyDataSetChanged();
			classifier.close();
		}

		@Override
		protected void onCancelled() {
			textView.setText("Got an error :(");
			textView.setVisibility(View.VISIBLE);
			progressBar.setVisibility(View.GONE);
		}
	}

	private class ClassifyAndCluster extends AsyncTask<Void, Integer, Void> {

		private static final int NOTIFICATION_ID = 1;
		private ImageClassifier classifier;
		private ProgressBar progressBar;
		private TextView textView;
		private Path cameraDir;
		private NotificationCompat.Builder mBuilder;
		private NotificationManagerCompat notificationManager;

		@Override
		protected void onPreExecute() {
			list.clear();
			clusters.clear();
			String dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
			cameraDir = Paths.get(dcim, "Camera");
			Log.d(TAG, "ClassifyAndCluster: Starting image classification asynchronously..");
			try {
				classifier = new ImageClassifier(MainActivity.this,
				                                 "mobilenet_v2_1.0_224_quant.tflite",
				                                 "labels.txt", 224);
			} catch (IOException e) {
				Log.e(TAG, "ClassifyAndCluster: Couldn't build image classifier: " + e);
				cancel(true);
			}
			progressBar = findViewById(R.id.classification_progress);
			progressBar.setProgress(0);
			textView = findViewById(R.id.content_text);
			runOnUiThread(() -> {
				textView.setText("Scanning images..");
				galleryView.setVisibility(View.GONE);
				progressBar.setIndeterminate(false);
				progressBar.setVisibility(View.VISIBLE);
				textView.setVisibility(View.VISIBLE);
			});
			notificationManager = NotificationManagerCompat.from(MainActivity.this);
			setNotificationChannel();
			mBuilder = new NotificationCompat.Builder(MainActivity.this, "default")
					.setSmallIcon(R.drawable.ic_menu_gallery)
					.setContentTitle("Scanning images..")
					.setPriority(NotificationCompat.PRIORITY_LOW)
					.setOngoing(true)
					.setProgress(100, 0, false);
			notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
		}

		private void setNotificationChannel() {
			NotificationChannel notificationChannel = new NotificationChannel("default", "default",
			                                                                  NotificationManager.IMPORTANCE_LOW);
			notificationChannel.enableLights(false);
			notificationChannel.enableVibration(false);
			NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			notificationManager.createNotificationChannel(notificationChannel);
		}

		@Override
		protected Void doInBackground(Void... voids) {
			try {
				long imagesNum = Files.list(cameraDir).count();
				Log.d(TAG, "ClassifyAndCluster: Found " + imagesNum + " images on DCIM directory");

				Files.walk(cameraDir)
				     .filter((Path path) -> path.toString().endsWith(".jpg"))
				     .forEach((Path path) -> {
					     Image image = new Image(path);
					     if (image.getDateTaken() != null) {
						     list.add(image);
						     image.calculateFeatureVector(classifier);
					     } else {
						     Log.w(TAG, "ClassifyAndCluster: Skipping image: " + path.getFileName());
					     }
					     publishProgress(Math.max(1, (int) (100 / imagesNum)));
				     });
				ImageDatabase db = ImageDatabase.getAppDatabase(MainActivity.this);
				db.clearAllTables();
				db.imageDao().insert(list);
				DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.85, 2);
				clusters = clusterer.cluster(list);
				Log.d(TAG, "ClassifyAndCluster: Clustered " + clusters.size() + " clusters");
			} catch (IOException e) {
				Log.e(TAG, "ClassifyAndCluster: Got an exception while initiating input list: " + e);
				cancel(true);
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			progressBar.incrementProgressBy(values[0]);
			mBuilder.setProgress(100, progressBar.getProgress(), false);
			notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
		}

		@Override
		protected void onPostExecute(Void result) {
			notificationManager.cancel(NOTIFICATION_ID);
			progressBar.setVisibility(View.GONE);
			textView.setVisibility(View.GONE);
			galleryView.setVisibility(View.VISIBLE);
			galleryView.setImageClusters(clusters);
			galleryView.notifyDataSetChanged();
			classifier.close();
		}

		@Override
		protected void onCancelled() {
			notificationManager.cancel(NOTIFICATION_ID);
			textView.setText("Got an error :(");
			textView.setVisibility(View.VISIBLE);
			progressBar.setVisibility(View.GONE);
		}
	}
}
