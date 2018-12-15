package yoavbz.dupimg;

import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.github.isabsent.filepicker.SimpleFilePickerDialog;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import yoavbz.dupimg.background.NotificationJobService;
import yoavbz.dupimg.database.ImageDatabase;
import yoavbz.dupimg.gallery.ImageClusterActivity;
import yoavbz.dupimg.gallery.MediaGalleryView;
import yoavbz.dupimg.models.Image;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener, MediaGalleryView.OnImageClicked, SimpleFilePickerDialog.InteractionListenerString {

	public static final String TAG = "dupImg";
	private static final int JOB_ID = 1;
	private static final int IMAGE_CLUSTER_ACTIVITY_CODE = 0;
	private static final int INTRO_ACTIVITY_CODE = 1;
	private List<Image> list = new ArrayList<>();
	private List<Cluster<Image>> clusters = new ArrayList<>();
	private MediaGalleryView galleryView;
	private AsyncTask asyncTask;
	private NotificationManagerCompat notificationManager;
	private TextView textView;
	private ProgressBar progressBar;

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
	protected void onStop() {
		new Thread(() -> {
			Glide.get(this).clearDiskCache();
			Log.d(TAG, "onDestroy: Cleared Glide cache");
		}).start();
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		// Remove notification if asyncTask
		if (asyncTask != null && asyncTask.getStatus() != AsyncTask.Status.FINISHED) {
			NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			notificationManager.cancel(1);
			asyncTask = null;
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
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
		                                                         R.string.navigation_drawer_open,
		                                                         R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();

		NavigationView navigationView = findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		SwitchCompat monitorSwitch = (SwitchCompat) navigationView.getMenu()
		                                                          .findItem(R.id.drawer_switch)
		                                                          .getActionView();

		JobScheduler scheduler = (JobScheduler) getSystemService(JOB_SCHEDULER_SERVICE);
		boolean isJobScheduled = scheduler.getPendingJob(JOB_ID) != null;
		Log.d(TAG, "MainActivity: Background service is " + (isJobScheduled ? "" : "not ") + "running");

		monitorSwitch.setChecked(isJobScheduled);
		monitorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			if (isChecked) {
				// Turn on background jobService
				JobInfo job = new JobInfo.Builder(1, new ComponentName(getPackageName(),
				                                                       NotificationJobService.class.getName()))
						.setPeriodic(TimeUnit.MINUTES.toMillis(15))
						.setPersisted(true)
						.build();
				scheduler.schedule(job);
			} else {
				// Turn off background jobService
				scheduler.cancel(1);
				// Resetting firstRun parameter
				PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
				                 .edit()
				                 .putBoolean("firstRun", true)
				                 .apply();
			}
		});

		galleryView = findViewById(R.id.gallery);
		galleryView.setImageClusters(clusters);
		galleryView.setOnImageClickListener(this);
		galleryView.setPlaceHolder(R.drawable.media_gallery_placeholder);

		notificationManager = NotificationManagerCompat.from(this);
		textView = findViewById(R.id.content_text);
		progressBar = findViewById(R.id.classification_progress);

		boolean shouldRescan = PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
		                                        .getBoolean("shouldRescan", true);
		if (shouldRescan) {
			// Clear database and rescan all images
			String dcim = Environment.getExternalStoragePublicDirectory(
					Environment.DIRECTORY_DCIM).getAbsolutePath();
			Path cameraDir = Paths.get(dcim, "Camera");
			rescanImages(cameraDir, true);
		} else {
			// Get all images from the database
			fetchAndCluster();
		}
	}

	/**
	 * Scanning all images in the Camera directory, classifying and clustering them, and displaying them
	 */
	private void rescanImages(Path dir, boolean clearDb) {
		asyncTask = new ClassifyAndCluster(this).execute(dir, clearDb);
	}

	/**
	 * Loading all images from the database, clustering them and displaying them
	 */
	private void fetchAndCluster() {
		asyncTask = new FetchAndCluster(this).execute();
	}

	/**
	 * Starting the batch gallery weakReference
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
	 * @param requestCode Activity identifier: IMAGE_CLUSTER_ACTIVITY_CODE = 0 , INTRO_ACTIVITY_CODE = 1
	 * @param resultCode  RESULT_OK on success, otherwise RESULT_CANCELED
	 * @param data        Contains the result data
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == IMAGE_CLUSTER_ACTIVITY_CODE && resultCode == RESULT_OK) {
			fetchAndCluster();
		} else if (requestCode == INTRO_ACTIVITY_CODE) {
			if (resultCode == RESULT_OK) {
				// Intro finished successfully
				init();
			} else {
				// Intro finished unsuccessfully
				finish();
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
		} else if (!asyncTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
			moveTaskToBack(true);
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
				String dcim = Environment.getExternalStoragePublicDirectory(
						Environment.DIRECTORY_DCIM).getAbsolutePath();
				Path cameraDir = Paths.get(dcim, "Camera");
				rescanImages(cameraDir, true);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		// Handle navigation view item clicks here.
		switch (item.getItemId()) {
			case R.id.custom_dir:
				// All photos
				showListItemDialog("Select directory to scan:", null,
				                   SimpleFilePickerDialog.CompositeMode.FOLDER_ONLY_DIRECT_CHOICE_IMMEDIATE, null);
				break;
			case R.id.nav_about:
				final SpannableString message =
						new SpannableString("The app repository is available at:\n" +
								                    "https://github.com/YoavBZ/dupImg\n\nHave fun!");
				Linkify.addLinks(message, Linkify.WEB_URLS);
				AlertDialog dialog = new AlertDialog.Builder(this)
						.setTitle("About")
						.setMessage(message)
						.setNeutralButton("Ok", null)
						.show();
				((TextView) dialog.findViewById(android.R.id.message))
						.setMovementMethod(LinkMovementMethod.getInstance());
		}
		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);
		return true;
	}

	@Override
	public void showListItemDialog(String title, String folderPath, SimpleFilePickerDialog.CompositeMode mode, String dialogTag) {
		SimpleFilePickerDialog.build(folderPath, mode)
		                      .title(title)
		                      .show(this, "dir_dialog");
	}

	@Override
	public boolean onResult(@NonNull String dialogTag, int which, @NonNull Bundle extras) {
		String customPath = extras.getString(SimpleFilePickerDialog.SELECTED_SINGLE_PATH);
		Log.d(TAG, "onResult: Selected customPath = " + customPath);
		if (customPath != null) {
			rescanImages(Paths.get(customPath), false);
			PreferenceManager.getDefaultSharedPreferences(MainActivity.this).edit()
			                 .putBoolean("shouldRescan", true)
			                 .apply();
			return true;
		}
		return false;
	}

	private static class FetchAndCluster extends AsyncTask<Void, Integer, Void> {

		private final WeakReference<MainActivity> weakReference;

		FetchAndCluster(MainActivity activity) {
			this.weakReference = new WeakReference<>(activity);
		}

		@Override
		protected void onPreExecute() {
			MainActivity activity = weakReference.get();
			if (activity != null) {
				activity.list.clear();
				activity.clusters.clear();
				Log.d(TAG, "MainActivity: fetchAndCluster: Fetching images from DB");

				ProgressBar progressBar = activity.findViewById(R.id.classification_progress);
				TextView textView = activity.findViewById(R.id.content_text);

				textView.setText("Loading images..");
				activity.galleryView.setImageClusters(activity.clusters);
				activity.galleryView.notifyDataSetChanged();
				progressBar.setIndeterminate(true);
				progressBar.setVisibility(View.VISIBLE);
				textView.setVisibility(View.VISIBLE);
			}
		}

		@Override
		protected Void doInBackground(Void... voids) {
			MainActivity activity = weakReference.get();
			if (activity != null) {
				try {
					activity.list = ImageDatabase.getAppDatabase(activity).imageDao().getAll();
					Log.d(TAG, "MainActivity - fetchAndCluster: Got " + activity.list.size() + " images");
					DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.85, 2);
					activity.clusters = clusterer.cluster(activity.list);
					Log.d(TAG, "MainActivity - fetchAndCluster: Clustered " + activity.clusters.size() + " clusters");
				} catch (Exception e) {
					Log.e(TAG, "MainActivity - doInBackground: Got an exception!", e);
					cancel(true);
				}
			}
			return null;
		}

		@Override
		protected void onPostExecute(Void result) {
			MainActivity activity = weakReference.get();
			if (activity != null) {
				ProgressBar progressBar = activity.findViewById(R.id.classification_progress);
				TextView textView = activity.findViewById(R.id.content_text);

				progressBar.setVisibility(View.GONE);
				textView.setVisibility(View.GONE);
				activity.galleryView.setImageClusters(activity.clusters);
				activity.galleryView.notifyDataSetChanged();
			}
		}

		@Override
		protected void onCancelled() {
			Log.d(TAG, "FetchAndCluster - onCancelled: Cancelling task");

			MainActivity activity = weakReference.get();
			if (activity != null) {
				ProgressBar progressBar = activity.findViewById(R.id.classification_progress);
				TextView textView = activity.findViewById(R.id.content_text);

				textView.setText("Got an error :(");
				textView.setVisibility(View.VISIBLE);
				progressBar.setVisibility(View.GONE);
			}
		}
	}

	private static class ClassifyAndCluster extends AsyncTask<Object, Integer, Void> {

		private static final int NOTIFICATION_ID = 1;
		private final WeakReference<MainActivity> weakReference;
		private ImageClassifier classifier;
		private NotificationCompat.Builder mBuilder;

		ClassifyAndCluster(MainActivity mainActivity) {
			weakReference = new WeakReference<>(mainActivity);
		}

		@Override
		protected void onPreExecute() {
			MainActivity activity = weakReference.get();
			if (activity != null) {
				activity.list.clear();
				activity.clusters.clear();
				Log.d(TAG, "ClassifyAndCluster: Starting image classification asynchronously..");

				activity.progressBar.setProgress(0);
				activity.textView.setText("Scanning images..");
				activity.galleryView.setVisibility(View.GONE);
				activity.progressBar.setIndeterminate(false);
				activity.progressBar.setVisibility(View.VISIBLE);
				activity.textView.setVisibility(View.VISIBLE);

				NotificationManagerCompat notificationManager = NotificationManagerCompat.from(activity);
				setNotificationChannel(activity);
				mBuilder = new NotificationCompat.Builder(activity, "default")
						.setSmallIcon(R.drawable.ic_menu_gallery)
						.setContentTitle("Scanning images..")
						.setPriority(NotificationCompat.PRIORITY_LOW)
						.setOngoing(true)
						.setProgress(100, 0, false);
				notificationManager.notify(NOTIFICATION_ID, mBuilder.build());
			}
		}

		private void setNotificationChannel(MainActivity activity) {
			NotificationChannel notificationChannel = new NotificationChannel("default", "default",
			                                                                  NotificationManager.IMPORTANCE_LOW);
			notificationChannel.enableLights(false);
			notificationChannel.enableVibration(false);
			NotificationManager notificationManager = (NotificationManager) activity.getSystemService(
					NOTIFICATION_SERVICE);
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
					Path dir = (Path) objects[0];
					long imagesNum = Files.list(dir).count();
					Log.d(TAG, "ClassifyAndCluster: Found " + imagesNum + " images on DCIM directory");

					classifier = new ImageClassifier(activity, "mobilenet_v2_1.0_224_quant.tflite",
					                                 "labels.txt", 224);
					Files.list(dir)
					     .filter((Path path) -> path.toString().endsWith(".jpg"))
					     .forEach((Path path) -> {
						     if (!isCancelled()) {
							     Image image = new Image(path, classifier);
							     if (image.getDateTaken() != null) {
								     activity.list.add(image);
							     } else {
								     Log.w(TAG, "ClassifyAndCluster: Skipping image: " + path.getFileName());
							     }
							     publishProgress(Math.max(1, (int) (100 / imagesNum)));
						     }
					     });
					Collections.reverse(activity.list);
					classifier.close();
					boolean resetDb = (boolean) objects[1];
					if (resetDb) {
						ImageDatabase db = ImageDatabase.getAppDatabase(activity);
						db.clearAllTables();
						db.imageDao().insert(activity.list);
					}
					if (!isCancelled()) {
						DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.85, 2);
						activity.clusters = clusterer.cluster(activity.list);
						Log.d(TAG, "ClassifyAndCluster: Clustered " + activity.clusters.size() + " clusters");
					}
				} catch (IOException e) {
					Log.e(TAG, "ClassifyAndCluster: Got an exception", e);
					cancel(true);
				}
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			MainActivity activity = weakReference.get();
			if (activity != null) {
				activity.progressBar.incrementProgressBy(values[0]);
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
				activity.textView.setVisibility(View.GONE);
				activity.galleryView.setVisibility(View.VISIBLE);
				activity.galleryView.setImageClusters(activity.clusters);
				activity.galleryView.notifyDataSetChanged();
				PreferenceManager.getDefaultSharedPreferences(activity).edit()
				                 .putBoolean("shouldRescan", false)
				                 .apply();
			}
		}

		@Override
		protected void onCancelled() {
			Log.d(TAG, "ClassifyAndCluster - onCancelled: Cancelling task");
			MainActivity activity = weakReference.get();
			if (activity != null) {
				activity.notificationManager.cancel(NOTIFICATION_ID);
				activity.textView.setText("Got an error :(");
				activity.textView.setVisibility(View.VISIBLE);
				activity.progressBar.setVisibility(View.GONE);
				PreferenceManager.getDefaultSharedPreferences(activity).edit()
				                 .putBoolean("shouldRescan", true)
				                 .apply();
			}
		}
	}
}