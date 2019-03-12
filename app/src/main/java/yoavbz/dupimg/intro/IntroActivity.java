package yoavbz.dupimg.intro;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.model.SliderPage;
import yoavbz.dupimg.R;
import yoavbz.dupimg.intro.CustomFragments.AnimatedFragment;
import yoavbz.dupimg.intro.CustomFragments.BackgroundMonitorFragment;
import yoavbz.dupimg.intro.CustomFragments.PermissionFragment;
import yoavbz.dupimg.intro.CustomFragments.SelectDirFragment;

@SuppressWarnings("ConstantConditions")
public class IntroActivity extends AppIntro {

	private static final int DIRS_FRAGMENT_INDEX = 2;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSupportActionBar().hide();
		setResult(RESULT_CANCELED);

		AnimatedSliderPage welcomeSlide = new AnimatedSliderPage();
		welcomeSlide.setTitle("Welcome!");
		welcomeSlide.setDescription("This App will help you clean your duplicated images!");
		welcomeSlide.setDescSize(19f);
		welcomeSlide.setAnimationId(R.raw.smiley);
		welcomeSlide.setBgColor(Color.TRANSPARENT);
		addSlide(AnimatedFragment.newInstance(welcomeSlide));

		SliderPage permissionSlide = new SliderPage();
		permissionSlide.setTitle("App Permissions");
		permissionSlide.setDescription(
				"In order to scan and modify files, please click the 'Next' button and approve the asked permissions");
		permissionSlide.setImageDrawable(R.drawable.permissions);
		permissionSlide.setBgColor(Color.TRANSPARENT);
		addSlide(PermissionFragment.newInstance(permissionSlide));

		SliderPage defaultDirSlide = new SliderPage();
		defaultDirSlide.setTitle("Scanning Directories");
		defaultDirSlide.setDescription("Please select the directories\nyou would like to scan:");
		defaultDirSlide.setImageDrawable(R.drawable.images);
		defaultDirSlide.setBgColor(Color.TRANSPARENT);
		addSlide(SelectDirFragment.newInstance(defaultDirSlide));

		SliderPage backgroundMonitorSlide = new SliderPage();
		backgroundMonitorSlide.setTitle("Background Monitor");
		backgroundMonitorSlide.setDescription("Periodically scan your selected directories for duplications.\n" +
				                                      "In case duplications have been found, " +
				                                      "a notification will be sent.");
		backgroundMonitorSlide.setImageDrawable(R.drawable.monitor_notification);
		backgroundMonitorSlide.setBgColor(Color.TRANSPARENT);
		addSlide(BackgroundMonitorFragment.newInstance(backgroundMonitorSlide));

		AnimatedSliderPage doneSlide = new AnimatedSliderPage();
		doneSlide.setTitle("Done!");
		doneSlide.setDescription("We are good to go :)");
		doneSlide.setDescSize(19f);
		doneSlide.setAnimationId(R.raw.done);
		doneSlide.setBgColor(Color.TRANSPARENT);
		addSlide(AnimatedFragment.newInstance(doneSlide));

		// Adding listener for hiding next button when changing to defaultDirSlide until selecting directory
		pager.addOnPageChangeListener(new PagerOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				if (position == DIRS_FRAGMENT_INDEX) {
					setButtonState(nextButton, false);
				}
				setButtonState(backButton, position != 0);
			}
		});
		askForPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
		                               Manifest.permission.WRITE_EXTERNAL_STORAGE},
		                  DIRS_FRAGMENT_INDEX);
		showSkipButton(false);
		showSeparator(true);
	}

	@Override
	public void onDonePressed(Fragment currentFragment) {
		// Returning to the main activity
		PreferenceManager.getDefaultSharedPreferences(getBaseContext()).edit()
		                 .putBoolean("showIntro", false)
		                 .apply();
		setResult(RESULT_OK);
		finish();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		for (int result : grantResults) {
			if (result == PackageManager.PERMISSION_DENIED) {
				Toast.makeText(this, "Cannot launch app without permissions", Toast.LENGTH_LONG).show();
				finish();
			}
		}
	}
}