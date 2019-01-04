package yoavbz.dupimg.background;

import android.os.AsyncTask;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import org.apache.commons.math3.ml.clustering.DBSCANClusterer;
import yoavbz.dupimg.MainActivity;
import yoavbz.dupimg.R;
import yoavbz.dupimg.database.ImageDatabase;
import yoavbz.dupimg.models.Image;

import java.lang.ref.WeakReference;

public class FetchingTask extends AsyncTask<Void, Integer, Void> {

	private final WeakReference<MainActivity> weakReference;

	public FetchingTask(MainActivity activity) {
		this.weakReference = new WeakReference<>(activity);
	}

	@Override
	protected void onPreExecute() {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.list.clear();
			activity.clusters.clear();
			Log.d(MainActivity.TAG, "MainActivity: fetchAndCluster: Fetching images from DB");

			ProgressBar progressBar = activity.findViewById(R.id.classification_progress);
			TextView textView = activity.findViewById(R.id.content_text);

			textView.setText("Loading images..");
			activity.galleryView.setImageClusters(activity.clusters);
			activity.galleryView.notifyDataSetChanged();
			progressBar.setIndeterminate(true);
			progressBar.setVisibility(View.VISIBLE);
			textView.setVisibility(View.VISIBLE);
			activity.invalidateOptionsMenu();
		}
	}

	@Override
	protected Void doInBackground(Void... voids) {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			try {
				activity.list = ImageDatabase.getAppDatabase(activity).imageDao().getAll();
				Log.d(MainActivity.TAG, "MainActivity - fetchAndCluster: Got " + activity.list.size() + " images");
				DBSCANClusterer<Image> clusterer = new DBSCANClusterer<>(1.7, 2);
				activity.clusters = clusterer.cluster(activity.list);
				Log.d(MainActivity.TAG, "MainActivity - fetchAndCluster: Clustered " + activity.clusters.size() + " clusters");
			} catch (Exception e) {
				Log.e(MainActivity.TAG, "MainActivity - doInBackground: Got an exception!", e);
				cancel(true);
			}
		}
		return null;
	}

	@Override
	protected void onPostExecute(Void result) {
		MainActivity activity = weakReference.get();
		if (activity != null) {
			activity.progressBar.setVisibility(View.GONE);
			if (activity.clusters.isEmpty()) {
				activity.textView.setText("No duplicates were found :)");
			} else {
				activity.textView.setVisibility(View.GONE);
			}
			activity.galleryView.setImageClusters(activity.clusters);
			activity.galleryView.notifyDataSetChanged();
			activity.invalidateOptionsMenu();
		}
	}

	@Override
	protected void onCancelled() {
		Log.d(MainActivity.TAG, "FetchAndCluster - onCancelled: Cancelling task");
		MainActivity activity = weakReference.get();
		if (activity != null) {
			ProgressBar progressBar = activity.findViewById(R.id.classification_progress);
			TextView textView = activity.findViewById(R.id.content_text);
			textView.setText("Got an error :(");
			textView.setVisibility(View.VISIBLE);
			progressBar.setVisibility(View.GONE);
			activity.invalidateOptionsMenu();
		}
	}
}
