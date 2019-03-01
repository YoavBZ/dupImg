package yoavbz.dupimg.intro;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.widget.Toast;
import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.model.SliderPage;
import yoavbz.dupimg.R;
import yoavbz.dupimg.intro.CustomFragments.AnimatedFragment;
import yoavbz.dupimg.intro.CustomFragments.BackgroundMonitorFragment;
import yoavbz.dupimg.intro.CustomFragments.PermissionFragment;
import yoavbz.dupimg.intro.CustomFragments.SelectDirFragment;

@SuppressWarnings("ConstantConditions")
public class IntroActivity extends AppIntro {

	public static final int PERMISSION_FRAGMENT_INDEX = 2;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSupportActionBar().hide();
		setResult(RESULT_CANCELED);

		// TODO: Increase description size
		AnimatedSliderPage welcomeSlide = new AnimatedSliderPage();
		welcomeSlide.setTitle("Welcome!");
		welcomeSlide.setDescription(
				"This App will help you clean your duplicated images");
		welcomeSlide.setBgColor(Color.TRANSPARENT);
		welcomeSlide.setDescSize(20f);
		welcomeSlide.setAnimationId(R.raw.smiley);
		addSlide(AnimatedFragment.newInstance(welcomeSlide));

		SliderPage permissionSlide = new SliderPage();
		permissionSlide.setTitle("App Permissions");
		permissionSlide.setDescription(
				"In order to scan and modify files, please click the 'Next' button and approve the asked permissions");
		permissionSlide.setDescColor(Color.WHITE);
		permissionSlide.setImageDrawable(R.drawable.permissions);
		permissionSlide.setBgColor(Color.TRANSPARENT);
		addSlide(PermissionFragment.newInstance(permissionSlide));

		SliderPage defaultDirSlide = new SliderPage();
		defaultDirSlide.setTitle("Camera Directory");
		defaultDirSlide.setDescription("Please select your default camera directory:");
		defaultDirSlide.setImageDrawable(R.drawable.ic_camera);
		defaultDirSlide.setBgColor(Color.TRANSPARENT);
		addSlide(SelectDirFragment.newInstance(defaultDirSlide));

		SliderPage backgroundMonitorSlide = new SliderPage();
		backgroundMonitorSlide.setTitle("Background Monitor");
		backgroundMonitorSlide.setDescription("Periodically scan your default directory for duplications");
		backgroundMonitorSlide.setImageDrawable(R.drawable.monitor_notification);
		backgroundMonitorSlide.setBgColor(Color.TRANSPARENT);
		addSlide(BackgroundMonitorFragment.newInstance(backgroundMonitorSlide));

		AnimatedSliderPage doneSlide = new AnimatedSliderPage();
		doneSlide.setTitle("Done!");
		doneSlide.setDescription("We are good to go :)");
		doneSlide.setBgColor(Color.TRANSPARENT);
		doneSlide.setDescSize(20f);
		doneSlide.setAnimationId(R.raw.done);
		addSlide(AnimatedFragment.newInstance(doneSlide));

		// Adding listener for hiding next button when changing to defaultDirSlide until selecting directory
		pager.addOnPageChangeListener(new PagerOnPageChangeListener() {
			@Override
			public void onPageSelected(int position) {
				super.onPageSelected(position);
				if (position == PERMISSION_FRAGMENT_INDEX) {
					setButtonState(nextButton, false);

				}
			}
		});

		showSkipButton(false);
		askForPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
		                               Manifest.permission.WRITE_EXTERNAL_STORAGE},
		                  PERMISSION_FRAGMENT_INDEX);
		showSeparator(true);
		setGoBackLock(true);
		setFadeAnimation();
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
}