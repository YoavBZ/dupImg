package yoavbz.dupimg;

import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.*;
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
import yoavbz.dupimg.utils.Directory;
import yoavbz.dupimg.utils.DirectoryTreeView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener,
		GalleryView.OnClusterClickListener {

	// Constants
	public static final String TAG = "dupImg";
	public static final int JOB_ID = 1;
	public static final String ACTION_UPDATE_UI = "yoavbz.dupimg.ACTION_UPDATE_UI";
	private static final int SCANNING_NOTIFICATION_ID = 1;
	private static final int IMAGE_CLUSTER_ACTIVITY_CODE = 0;
	private static final int INTRO_ACTIVITY_CODE = 1;
	// AtomicBoolean for AsyncTask usage
	public AtomicBoolean isAsyncTaskRunning = new AtomicBoolean(false);
	public AtomicBoolean isCustomScan = new AtomicBoolean(false);
	// Views
	public GalleryView galleryView;
	public TextView textView;
	public ProgressBar progressBar;
	// Utility References
	public NotificationManager notificationManager;
	private ClassificationTask asyncTask;
	private SharedPreferences pref;
	private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			if (!isCustomScan.get() && ACTION_UPDATE_UI.equals(intent.getAction())) {
				rescanImages();
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
		LocalBroadcastManager.getInstance(this).registerReceiver(broadcastReceiver, new IntentFilter(ACTION_UPDATE_UI));
	}

	@Override
	protected void onStop() {
		LocalBroadcastManager.getInstance(this).unregisterReceiver(broadcastReceiver);
		super.onStop();
	}

	/**
	 * In addition to the app destruction, cancels asyncTask if running and clears Glide disk cache
	 */
	@Override
	protected void onDestroy() {
		if (asyncTask != null) {
			// Remove notification if asyncTask isn't done yet
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
	 * - Regular UI initiation.
	 * - Async images classifying and clustering.
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

		rescanImages();

	}

	/**
	 * Scanning all images in the Camera directory, classifying and clustering them, and displaying them
	 *
	 * @param dirs The paths of the directories to scan
	 */
	private void rescanImages(@NonNull String... dirs) {
		asyncTask = new ClassificationTask(this);
		asyncTask.execute(dirs);
	}

	/**
	 * @param cluster          The image list of the clicked cluster
	 * @param clusterThumbnail The thumbnail of the clicked cluster
	 */
	@Override
	public void onClusterClick(List<Image> cluster, ImageView clusterThumbnail) {
		// Starting ImageClusterActivity with correct parameters
		Intent intent = new Intent(this, ImageClusterActivity.class);
		intent.putParcelableArrayListExtra("IMAGES", (ArrayList<Image>) cluster);
		// Handling transition animation
		intent.putExtra("transition", String.valueOf(clusterThumbnail.getId()));
		clusterThumbnail.setTransitionName(String.valueOf(clusterThumbnail.getId()));
		ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(this, clusterThumbnail,
		                                                                       String.valueOf(clusterThumbnail.getId()));
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
						rescanImages();
					} else {
						DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.65, 2);
						List<Image> scannedImages = galleryView.getAllImages();
						List<String> deleted = data.getStringArrayListExtra("deleted");
						// Remove deleted images from galleryView
						scannedImages.removeIf(img -> {
							for (String deletedPath : deleted) {
								if (deletedPath.equals(img.getPath())) {
									return true;
								}
							}
							return false;
						});
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
		}
	}

	/**
	 * When Back-key is being pressed, closes the drawer if opened, move app to back if AsyncTask is running,
	 * close it otherwise.
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

	/**
	 * This method sets the visibility for each {@link MenuItem} according to the app state
	 *
	 * @param menu The main menu
	 * @return true to display this menu
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.gallery_toolbar_menu, menu);
		menu.findItem(R.id.action_back).setVisible(isCustomScan.get());
		menu.findItem(R.id.action_cancel).setVisible(isAsyncTaskRunning.get());
		return true;
	}

	/**
	 * Listener that handles  item selection.
	 * Menu items are:
	 * change_default_dirs = Changing the default directories to scan, using DirectoryTreeView
	 * custom_dir = Performing custom scan, using DirectoryTreeView to select dirs
	 * nav_about = Displaying about dialog
	 *
	 * @param item The selected {@link MenuItem}
	 * @return false to allow normal menu processing to proceed, true to consume it here.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.action_back:
				asyncTask.cancel(true);
				isCustomScan.compareAndSet(true, false);
				rescanImages();
				return true;
			case R.id.action_cancel:
				asyncTask.cancel(true);
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Listener that handles {@link NavigationView} item selection.
	 * Menu items are:
	 * change_default_dirs = Changing the default directories to scan, using DirectoryTreeView
	 * custom_dir = Performing custom scan, using DirectoryTreeView to select dirs
	 * nav_about = Displaying about dialog
	 *
	 * @param item The selected {@link MenuItem}
	 * @return true to display the item as selected
	 */
	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		// Handle navigation view item clicks here.
		switch (item.getItemId()) {
			case R.id.change_default_dirs:
				// Changing the default directories to scan, using DirectoryTreeView
				DirectoryTreeView dirTreeView = new DirectoryTreeView(this);
				Set<String> selectedDirs = new HashSet<>();
				dirTreeView.setOnDirStateChangeListener((dir, state) -> {
					if (state == Directory.DirState.FULL) {
						selectedDirs.add(dir.getFile().getPath());
					} else if (state == Directory.DirState.NONE) {
						selectedDirs.remove(dir.getFile().getPath());
					}
				});
				new AlertDialog.Builder(this)
						.setTitle("Select directories to scan:")
						.setView(dirTreeView)
						.setPositiveButton("OK", (dialog, which) ->
								PreferenceManager.getDefaultSharedPreferences(this).edit()
								                 .putStringSet("dirs", selectedDirs)
								                 .apply())
						.setNegativeButton("Cancel", null)
						.show();
				break;
			case R.id.custom_dir:
				// Performing custom scan, using DirectoryTreeView to select dirs
				dirTreeView = new DirectoryTreeView(this);
				selectedDirs = new HashSet<>();
				dirTreeView.setOnDirStateChangeListener((dir, state) -> {
					if (state == Directory.DirState.FULL) {
						selectedDirs.add(dir.getFile().getAbsolutePath());
					} else if (state == Directory.DirState.NONE) {
						selectedDirs.remove(dir.getFile().getAbsolutePath());
					}
				});
				new AlertDialog.Builder(this)
						.setTitle("Select directories to scan:")
						.setView(dirTreeView)
						.setPositiveButton("OK", (dialog, which) -> rescanImages(selectedDirs.toArray(new String[0])))
						.setNegativeButton("Cancel", null)
						.show();
				break;
			case R.id.nav_about:
				// Displaying about dialog
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