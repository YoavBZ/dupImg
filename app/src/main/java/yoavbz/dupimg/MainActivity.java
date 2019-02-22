package yoavbz.dupimg;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.*;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
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
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import org.apache.commons.math3.ml.clustering.Cluster;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import yoavbz.dupimg.background.ClassificationTask;
import yoavbz.dupimg.background.NotificationJobService;
import yoavbz.dupimg.gallery.GalleryView;
import yoavbz.dupimg.gallery.ImageClusterActivity;
import yoavbz.dupimg.intro.IntroActivity;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener,
		GalleryView.OnClusterClickListener {

	public static final String TAG = "dupImg";
	private static final int SCANNING_NOTIFICATION_ID = 1;
	public static final int JOB_ID = 1;
	private static final int IMAGE_CLUSTER_ACTIVITY_CODE = 0;
	private static final int INTRO_ACTIVITY_CODE = 1;
	private static final int CHANGE_DEFAULT_DIR_ACTIVITY_CODE = 2;
	private static final int SCAN_CUSTOM_DIR_ACTIVITY_CODE = 3;
	public static final String ACTION_UPDATE_UI = "yoavbz.dupimg.ACTION_UPDATE_UI";

	private ClassificationTask asyncTask;
	public AtomicBoolean isAsyncTaskRunning = new AtomicBoolean(false);
	public NotificationManager notificationManager;

	public GalleryView galleryView;
	public TextView textView;
	public ProgressBar progressBar;

	public AtomicBoolean isCustomScan = new AtomicBoolean(false);

	private SharedPreferences pref;
	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (!isCustomScan.get() && ACTION_UPDATE_UI.equals(intent.getAction())) {
				rescanImages(null);
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
		pref = PreferenceManager.getDefaultSharedPreferences(this);
		if (pref.getBoolean("showIntro", true)) {
			Intent introIntent = new Intent(this, IntroActivity.class);
			startActivityForResult(introIntent, INTRO_ACTIVITY_CODE);
		} else {
			init();
		}
	}

	@Override
	protected void onStart() {
		super.onStart();
		// Registering BroadcastReceiver
		IntentFilter intentFilter = new IntentFilter(ACTION_UPDATE_UI);
		LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, intentFilter);
	}

	@Override
	protected void onStop() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		// Remove notification if asyncTask isn't done yet
		if (asyncTask != null) {
			notificationManager.cancel(SCANNING_NOTIFICATION_ID);
			asyncTask.cancel(true);
		}
		new Thread(() -> {
			Glide.get(this).clearDiskCache();
			Log.d(TAG, "onDestroy: Cleared Glide cache");
		}).start();
		super.onDestroy();
	}

	/**
	 * Handling the app initiation, after displaying the intro on first use:
	 * * Regular UI initiation.
	 * * Async images loading.
	 */
	@SuppressWarnings("ConstantConditions")
	private void init() {
		// Layout
		setContentView(R.layout.activity_main);
		Toolbar toolbar = findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		// DrawerLayout
		DrawerLayout drawerLayout = findViewById(R.id.drawer_layout);
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar,
		                                                         R.string.navigation_drawer_open,
		                                                         R.string.navigation_drawer_close);
		drawerLayout.addDrawerListener(toggle);
		toggle.syncState();

		// NavigationView (drawer content)
		NavigationView navigationView = findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		// Background monitor switch
		SwitchCompat monitorSwitch = (SwitchCompat) navigationView.getMenu()
		                                                          .findItem(R.id.drawer_switch)
		                                                          .getActionView();
		boolean isJobScheduled = pref.getBoolean("isJobSchedule", true);
		JobScheduler scheduler = getSystemService(JobScheduler.class);
		Log.d(TAG, "MainActivity: Background service is " + (isJobScheduled ? "" : "not ") + "running");
		monitorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			if (isChecked) {
				// Turn on background jobService
				JobInfo job = new JobInfo.Builder(JOB_ID, new ComponentName(getPackageName(),
				                                                            NotificationJobService.class.getName()))
						.setPeriodic(TimeUnit.MINUTES.toMillis(15))
						.setPersisted(true)
						.build();
				scheduler.schedule(job);
			} else {
				// Turn off background jobService
				scheduler.cancel(JOB_ID);
			}
			pref.edit()
			    .putBoolean("isJobSchedule", isChecked)
			    .apply();
		});
		monitorSwitch.setChecked(isJobScheduled);

		// Gallery view
		galleryView = findViewById(R.id.gallery);
		galleryView.setOnImageClickListener(this);

		// Saving objects for future use
		notificationManager = getSystemService(NotificationManager.class);
		textView = findViewById(R.id.content_text);
		progressBar = findViewById(R.id.classification_progress);

		rescanImages(null);
	}

	/**
	 * Scanning all images in the Camera directory, classifying and clustering them, and displaying them
	 *
	 * @param dir The {@link Uri} of the directory to scan, use null for default value
	 */
	private void rescanImages(Uri dir) {
		asyncTask = new ClassificationTask(this);
		asyncTask.execute(dir);
	}

	@Override
	public void onClusterClick(List<Image> cluster, ImageView thumbnail) {
		// Starting ImageClusterActivity with correct parameters
		Intent intent = new Intent(this, ImageClusterActivity.class);
		intent.putParcelableArrayListExtra("IMAGES", (ArrayList<Image>) cluster);
		// Handling transition animation
		intent.putExtra("transition", String.valueOf(thumbnail.getId()));
		thumbnail.setTransitionName(String.valueOf(thumbnail.getId()));
		ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(this, thumbnail,
		                                                                       String.valueOf(thumbnail.getId()));
		startActivityForResult(intent, IMAGE_CLUSTER_ACTIVITY_CODE, options.toBundle());
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
				if (resultCode == RESULT_OK) {
					if (!isCustomScan.get()) {
						rescanImages(null);
					} else {
						DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.65, 2);
						List<Image> scannedImages = galleryView.getAllImages();
						ArrayList<Uri> deleted = data.getParcelableArrayListExtra("deleted");
						// Remove deleted images from galleryView
						scannedImages.removeIf(img -> deleted.contains(img.getUri()));
						List<Cluster<Image>> clusters = clusterer.cluster(scannedImages);
						galleryView.setImageClusters(clusters);
					}
				}
				break;
			case INTRO_ACTIVITY_CODE:
				if (resultCode == RESULT_OK) {
					init();
				} else {
					finish();
				}
				break;
			case CHANGE_DEFAULT_DIR_ACTIVITY_CODE:
				if (resultCode == RESULT_OK && data != null && data.getData() != null) {
					Uri dirUri = data.getData();
					SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
					pref.edit()
					    .putString("dirUri", dirUri.toString())
					    .apply();
					if (!isCustomScan.get()) {
						rescanImages(null);
					}
				}
				break;
			case SCAN_CUSTOM_DIR_ACTIVITY_CODE:
				if (resultCode == RESULT_OK && data != null && data.getData() != null) {
					rescanImages(data.getData());
				}
		}
	}

	/**
	 * When Back-key is being pressed, closing the drawer if opened, moving it to back if AsyncTask is running,
	 * closing it otherwise.
	 */
	@Override
	public void onBackPressed() {
		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		if (drawer.isDrawerOpen(GravityCompat.START)) {
			drawer.closeDrawer(GravityCompat.START);
		} else if (isAsyncTaskRunning.get()) {
			moveTaskToBack(true);
		} else {
			super.onBackPressed();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.gallery_toolbar_menu, menu);
		menu.findItem(R.id.action_back).setVisible(isCustomScan.get());
		menu.findItem(R.id.action_cancel).setVisible(isAsyncTaskRunning.get());
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_back:
				isCustomScan.compareAndSet(true, false);
				rescanImages(null);
				return true;
			case R.id.action_cancel:
				asyncTask.cancel(true);
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
				startActivityForResult(intent, CHANGE_DEFAULT_DIR_ACTIVITY_CODE);
				break;
			case R.id.custom_dir:
				intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
				intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
				startActivityForResult(intent, SCAN_CUSTOM_DIR_ACTIVITY_CODE);
				break;
			case R.id.nav_about:
				final SpannableString message = new SpannableString(
						"The app repository is available at:\nhttps://github.com/YoavBZ/dupImg\n\nHave fun!");
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