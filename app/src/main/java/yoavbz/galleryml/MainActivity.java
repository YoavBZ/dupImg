package yoavbz.galleryml;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
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
import org.apache.commons.math3.ml.clustering.DoublePoint;
import yoavbz.galleryml.background.ImageListener;
import yoavbz.galleryml.database.ImageDao;
import yoavbz.galleryml.database.ImageDatabase;
import yoavbz.galleryml.gallery.Constants;
import yoavbz.galleryml.gallery.Image;
import yoavbz.galleryml.gallery.cluster.ImageCluster;
import yoavbz.galleryml.gallery.cluster.ImageClusterActivity;
import yoavbz.galleryml.gallery.views.MediaGalleryView;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity
		implements NavigationView.OnNavigationItemSelectedListener, MediaGalleryView.OnImageClicked {

	private static final String TAG = "MainActivity";
	private List<Image> list = new ArrayList<>();
	private ArrayList<ImageCluster> clusters = new ArrayList<>();
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
		if (asyncTask != null) {
			asyncTask.cancel(true);
		}
		super.onDestroy();
	}

	/**
	 * Handling the app initiation, after displaying the into on first use:
	 * * Regular UI processing.
	 * * Clarifai client initiation.
	 * * Async Clarifai inputs resetting.
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

		SwitchCompat monitorSwitch = (SwitchCompat) navigationView.getMenu().findItem(
				R.id.drawer_switch).getActionView();
		monitorSwitch.setChecked(ImageListener.isRunning(this));
		monitorSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			Log.d(TAG, "Background service is " + (ImageListener.isRunning(this) ? "" : "not ") + "running");
			if (isChecked) {
				// Turn on background listener service
				startService(new Intent(this, ImageListener.class));
			} else {
				// Turn off background listener service
				stopService(new Intent(this, ImageListener.class));
			}
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
		Bundle bundle = new Bundle();
		bundle.putParcelableArrayList(Constants.IMAGES, clusters.get(pos).getImages());
		bundle.putString(Constants.TITLE, "Similar Images");
		intent.putExtras(bundle);
		startActivityForResult(intent, 0);
	}

	/**
	 * Handling return from the 'startActivityForResult' activities
	 *
	 * @param requestCode Activity identifier: ImageClusterActivity = 0 , IntroActivity = 1
	 * @param resultCode  Should be RESULT_OK always
	 * @param data        Contains the result data
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == 0 && resultCode == RESULT_OK) {
			String selectedImage = data.getStringExtra("filename");
			Toast.makeText(this, selectedImage, Toast.LENGTH_LONG).show();
		} else if (requestCode == 1 && resultCode == RESULT_OK) {
			init();
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
				Toast.makeText(this, "Rescanning..", Toast.LENGTH_SHORT).show();
				rescanImages();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		// Handle navigation view item clicks here.
		int id = item.getItemId();
		switch (id) {
			case R.id.nav_photos:
				// All photos
				break;
			case R.id.nav_about:
				new AlertDialog.Builder(this)
						.setTitle("About")
						.setMessage("Credits :)")
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

		@Override
		protected void onPreExecute() {
			list.clear();
			clusters.clear();
			Log.d(TAG, "fetchAndCluster: Fetching images from DB");
			db = ImageDatabase.getAppDatabase(MainActivity.this).imageDao();
			Log.d(TAG, "Starting image classification asynchronously..");
			try {
				classifier = new ImageClassifier(MainActivity.this, "mobilenet_v2_1.0_224_quant.tflite",
				                                 "labels.txt", 224);
			} catch (IOException e) {
				Log.e(TAG, "Couldn't build image classifier: " + e);
				cancel(true);
			}
			progressBar = findViewById(R.id.classification_progress);
			progressBar.setVisibility(View.VISIBLE);
		}

		@Override
		protected Void doInBackground(Void... voids) {
			list = db.getAll();
			Log.d(TAG, "fetchAndCluster: Got " + list.size() + " images");
			List<DoublePoint> points = list.stream()
			                               .map(Image::getPoint)
			                               .collect(Collectors.toList());
			DBSCANClusterer<DoublePoint> clusterer = new DBSCANClusterer<>(2.05, 2);
			List<Cluster<DoublePoint>> dbscanClusters = clusterer.cluster(points);
			for (Cluster<DoublePoint> cluster : dbscanClusters) {
				ImageCluster imageCluster = new ImageCluster();
				for (DoublePoint point : cluster.getPoints()) {
					int index = points.indexOf(point);
					imageCluster.addImage(list.get(index));
					publishProgress(100 / points.size());
				}
				clusters.add(imageCluster);
			}
			Log.d(TAG, "fetchAndCluster: Clustered " + clusters.size() + " clusters");
			galleryView.notifyDataSetChanged();
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			progressBar.incrementProgressBy(values[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			progressBar.setVisibility(View.GONE);
			galleryView.setImageClusters(clusters);
			galleryView.notifyDataSetChanged();
			classifier.close();
		}
	}

	private class ClassifyAndCluster extends AsyncTask<Void, Integer, Void> {

		private ImageClassifier classifier;
		private ProgressBar progressBar;
		private TextView textView;
		private Path cameraDir;

		@Override
		protected void onPreExecute() {
			list.clear();
			clusters.clear();
			String dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
			cameraDir = Paths.get(dcim, "Camera");
			Log.d(TAG, "Starting image classification asynchronously..");
			try {
				classifier = new ImageClassifier(MainActivity.this, "mobilenet_v2_1.0_224_quant.tflite",
				                                 "labels.txt", 224);
			} catch (IOException e) {
				Log.e(TAG, "Couldn't build image classifier: " + e);
				cancel(true);
			}
			progressBar = findViewById(R.id.classification_progress);
			progressBar.setVisibility(View.VISIBLE);
			textView = findViewById(R.id.content_text);
			textView.setText("Scanning images..");
			textView.setVisibility(View.VISIBLE);
		}

		@Override
		protected Void doInBackground(Void... voids) {
			try {
				long imagesNum = Files.list(cameraDir).count();
				ArrayList<DoublePoint> points = new ArrayList<>();

				Files.walk(cameraDir)
				     .filter((Path path) -> path.toString().endsWith(".jpg"))
				     .forEach((Path path) -> {
					     try {
						     Image image = new Image(path);
						     if (image.getDateTaken() != null) {
							     list.add(image);
							     image.calculateFeatureVector(classifier);
							     points.add(image.getPoint());
						     }
						     publishProgress((int) (100 / imagesNum));
					     } catch (Exception e) {
						     Log.e(TAG, "Skipping image: " + path.getFileName());
					     }
				     });

				DBSCANClusterer<DoublePoint> clusterer = new DBSCANClusterer<>(2.05, 2);
				List<Cluster<DoublePoint>> dbscanClusters = clusterer.cluster(points);
				for (Cluster<DoublePoint> cluster : dbscanClusters) {
					ImageCluster imageCluster = new ImageCluster();
					for (DoublePoint point : cluster.getPoints()) {
						int index = points.indexOf(point);
						imageCluster.addImage(list.get(index));
					}
					clusters.add(imageCluster);
				}
			} catch (IOException e) {
				Log.e(TAG, "Got an exception while initiating input list: " + e);
				textView.setText("Got an error :(");
				textView.setVisibility(View.VISIBLE);
				galleryView.setVisibility(View.GONE);
			}
			return null;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			progressBar.incrementProgressBy(values[0]);
		}

		@Override
		protected void onPostExecute(Void result) {
			progressBar.setVisibility(View.GONE);
			textView.setVisibility(View.GONE);
			galleryView.setImageClusters(clusters);
			galleryView.notifyDataSetChanged();
			classifier.close();
		}
	}
}
