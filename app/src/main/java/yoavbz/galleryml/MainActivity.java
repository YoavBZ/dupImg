package yoavbz.galleryml;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener, MediaGalleryView.OnImageClicked {

	private static final String TAG = "MainActivity";
	private ArrayList<Image> list = new ArrayList<>();
	private ArrayList<ImageCluster> clusters = new ArrayList<>();
	private MediaGalleryView galleryView;

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
		ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
		drawer.addDrawerListener(toggle);
		toggle.syncState();

		NavigationView navigationView = findViewById(R.id.nav_view);
		navigationView.setNavigationItemSelectedListener(this);

		initList();
		galleryView = findViewById(R.id.gallery);
//		galleryView.setImageClusters(clusters);
		galleryView.setOnImageClickListener(this);
		galleryView.setPlaceHolder(R.drawable.media_gallery_placeholder);
//		galleryView.notifyDataSetChanged();
	}

	/**
	 * Adding .jpg images from the Camera directory to the input list, classify them and cluster them using DBScan
	 */
	private void initList() {
		new ClassificationTask().execute();
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
		// TODO: Change the batch list instead of the whole list
		bundle.putParcelableArrayList(Constants.IMAGES, clusters.get(pos).getImages());
		bundle.putString(Constants.TITLE, "Similar Images");
		// TODO: Once changed, start from 0 ?
//		bundle.putInt(Constants.SELECTED_IMAGE_POSITION, pos);
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
			case R.id.action_refresh:
				Toast.makeText(this, "Refreshing..", Toast.LENGTH_SHORT).show();
				initList();
				return true;
			case R.id.action_start_service:
//				calculateDistances(list.get(15).getFeatureVector());
				boolean serviceRunning = ImageListener.isRunning();
				if (!serviceRunning) {
					startService(new Intent(this, ImageListener.class));
				}
				return true;
			case R.id.action_stop_service:
				serviceRunning = ImageListener.isRunning();
				if (serviceRunning) {
					stopService(new Intent(this, ImageListener.class));
				}
		}
		return super.onOptionsItemSelected(item);
	}

	private double[] calculateDistances(double[] mainVector) {
		double[] distances = new double[list.size()];
		for (int i = 0; i < list.size(); i++) {
			double[] tempVector = list.get(i).getFeatureVector();
			double sum = 0.0;
			for (int j = 0; j < mainVector.length; j++) {
				sum = sum + Math.pow((mainVector[j] - tempVector[j]), 2);
			}
			distances[i] = Math.sqrt(sum);
			Log.d(TAG, String.valueOf(distances[i]));
		}
		return distances;
	}

	@Override
	public boolean onNavigationItemSelected(@NonNull MenuItem item) {
		// Handle navigation view item clicks here.
		int id = item.getItemId();
		switch (id) {
			case R.id.nav_photos:
				// All photos
			case R.id.nav_settings:
				// Setting fragment if necessary

			case R.id.nav_about:
				new AlertDialog.Builder(this).setTitle("About")
						.setMessage("Credits :)")
						.setNeutralButton("Ok", null)
						.show();
		}

		DrawerLayout drawer = findViewById(R.id.drawer_layout);
		drawer.closeDrawer(GravityCompat.START);
		return true;
	}

	private class ClassificationTask extends AsyncTask<Void, Integer, Void> {

		private ImageClassifier classifier;
		private ProgressBar progressBar;
		private Path cameraDir;

		@Override
		protected void onPreExecute() {
			list.clear();
			clusters.clear();
			Log.d(TAG, "Starting image classification asynchronously..");
			try {
				classifier = new ImageClassifier(MainActivity.this, "mobilenet_v2_1.0_224_quant.tflite", "labels.txt", 224);
			} catch (IOException e) {
				e.printStackTrace();
			}
			progressBar = findViewById(R.id.classification_progress);
			String dcim = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath();
			cameraDir = Paths.get(dcim, "Camera");
		}

		@Override
		protected Void doInBackground(Void... voids) {
			try {
				long imagesNum = Files.list(cameraDir).count();
				ArrayList<DoublePoint> points = new ArrayList<>();

				Files.walk(cameraDir).filter((Path path) -> path.toString().endsWith(".jpg")).forEach((Path path) -> {
					Image image = new Image(path);
					// Only processing images with valid dates
					if (image.getDateTaken() != null) {
						list.add(image);
					} else {
						Log.e(TAG, "Skipping image: " + image.getFileName());
					}
					Bitmap bitmap = BitmapFactory.decodeFile(image.getPath());
					bitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, false);
					image.setFeatureVector(classifier.recognizeImage(bitmap));
					points.add(new DoublePoint(image.getFeatureVector()));
					publishProgress((int) (1 / imagesNum));
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
				TextView noImages = findViewById(R.id.text_no_images);
				noImages.setVisibility(View.VISIBLE);
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
			galleryView.setImageClusters(clusters);
			galleryView.notifyDataSetChanged();
			classifier.close();
		}
	}
}
