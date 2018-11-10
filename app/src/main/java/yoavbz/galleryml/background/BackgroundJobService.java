package yoavbz.galleryml.background;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.util.Log;

public class BackgroundJobService extends JobService {

	private static final String TAG = "BackgroundJobService";

	@Override
	public boolean onStartJob(JobParameters params) {
		Log.d(TAG, "onStartJob");
		if (!ImageListener.isRunning()) {
			startService(new Intent(getApplicationContext(), ImageListener.class));
		}
		jobFinished(params, false);
		return true;
	}

	@Override
	public boolean onStopJob(JobParameters params) {
		Log.d(TAG, "onStopJob");
		return true;
	}

}
