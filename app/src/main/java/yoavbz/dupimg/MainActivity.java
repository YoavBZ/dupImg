package yoavbz.dupimg;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.*;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.LocalBroadcastManager;
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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import org.apache.commons.math3.ml.clustering.Cluster;
import yoavbz.dupimg.background.ClassificationTask;
import yoavbz.dupimg.background.FetchingTask;
import yoavbz.dupimg.background.NotificationJobService;
import yoavbz.dupimg.gallery.ImageClusterActivity;
import yoavbz.dupimg.gallery.MediaGalleryView;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener,
		MediaGalleryView.OnImageClicked {

	public static final String TAG = "dupImg";
	private static final int JOB_ID = 1;
	private static final int IMAGE_CLUSTER_ACTIVITY_CODE = 0;
	private static final int INTRO_ACTIVITY_CODE = 1;
	private static final int CHANGE_DEFAULT_DIR_ACTIVITY_CODE = 2;
	private static final int SCAN_CUSTOM_DIR_ACTIVITY_CODE = 3;
	public static final String ACTION_UPDATE_UI = "yoavbz.dupimg.ACTION_UPDATE_UI";

	public List<Image> list = new ArrayList<>();
	public List<Cluster<Image>> clusters = new ArrayList<>();

	public MediaGalleryView galleryView;

	private AsyncTask asyncTask;
	public NotificationManagerCompat notificationManager;
	public TextView textView;
	public ProgressBar progressBar;

	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (ACTION_UPDATE_UI.equals(intent.getAction())) {
				fetchAndCluster();
			}
		}
	};

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
			startActivityForResult(introIntent, INTRO_ACTIVITY_CODE);
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
		// Remove notification if asyncTask isn't done yet
		if (asyncTask != null) {
			asyncTask.cancel(true);
			NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			notificationManager.cancel(1);
			asyncTask = null;
		}
		LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
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

		// Handling BroadcastReceiver
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(ACTION_UPDATE_UI);
		LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);

		boolean shouldRescan = PreferenceManager.getDefaultSharedPreferences(MainActivity.this)
		                                        .getBoolean("shouldRescan", true);
		if (shouldRescan) {
			// Clear database and rescan all images
			String uriString = PreferenceManager.getDefaultSharedPreferences(this)
			                                    .getString("dirUri", null);
			rescanImages(Uri.parse(uriString), true);
		} else {
			// Get all images from the database
			fetchAndCluster();
		}
	}

	/**
	 * Scanning all images in the Camera directory, classifying and clustering them, and displaying them
	 */
	private void rescanImages(Uri dir, boolean clearDb) {
		asyncTask = new ClassificationTask(this).execute(dir, clearDb);
	}

	/**
	 * Loading all images from the database, clustering them and displaying them
	 */
	private void fetchAndCluster() {
		asyncTask = new FetchingTask(this).execute();
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
		startActivityForResult(intent, IMAGE_CLUSTER_ACTIVITY_CODE);
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
		switch (requestCode) {
			case IMAGE_CLUSTER_ACTIVITY_CODE:
				if(resultCode == RESULT_OK) {
					fetchAndCluster();
				}
				break;
			case INTRO_ACTIVITY_CODE:
				if(resultCode == RESULT_OK) {
					init();
				} else {
					finish();
				}
				break;
			case CHANGE_DEFAULT_DIR_ACTIVITY_CODE:
				if (resultCode == RESULT_OK && data != null) {
					Uri dirUri = data.getData();
					SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
					pref.edit()
					    .putString("dirUri", dirUri.toString())
					    .apply();
					rescanImages(dirUri, true);
				}
				break;
			case SCAN_CUSTOM_DIR_ACTIVITY_CODE:
				if (resultCode == RESULT_OK && data != null) {
					Toast.makeText(this, "Refresh to return to default directory", Toast.LENGTH_LONG).show();
					rescanImages(data.getData(), false);
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
	public boolean onPrepareOptionsMenu(Menu menu) {
		return asyncTask == null || asyncTask.getStatus().equals(AsyncTask.Status.FINISHED);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_refresh:
				fetchAndCluster();
				break;
			case R.id.action_rescan:
				String uriString = PreferenceManager.getDefaultSharedPreferences(this)
				                                    .getString("dirUri", null);
				Uri uri = Uri.parse(uriString);
				rescanImages(uri, true);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		// Handle navigation view item clicks here.
		switch (item.getItemId()) {
			case R.id.change_default_dir:
				Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
				intent.putExtra("scanType", "normal");
				startActivityForResult(intent, CHANGE_DEFAULT_DIR_ACTIVITY_CODE);
				break;
			case R.id.custom_dir:
				intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
				intent.putExtra("scanType", "custom");
				startActivityForResult(intent, SCAN_CUSTOM_DIR_ACTIVITY_CODE);
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
}