package yoavbz.dupimg;

import android.Manifest;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

public class IntroActivity extends AppIntro {

	private boolean resultOk = false;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSupportActionBar().hide();

		SliderPage welcomeSlide = new SliderPage();
		welcomeSlide.setTitle("Welcome!");
		welcomeSlide.setDescription(
				"This App will help you clean your duplicated photos using advanced machine learning algorithms!");
		welcomeSlide.setImageDrawable(R.drawable.clean_duplicates);
		welcomeSlide.setBgColor(Color.TRANSPARENT);
		addSlide(AppIntroFragment.newInstance(welcomeSlide));

		SliderPage permissionSlide = new SliderPage();
		permissionSlide.setTitle("App Permissions");
		permissionSlide.setDescription(
				"In order to scan and modify files, please click the 'Next' button and approve the asked permissions");
		permissionSlide.setImageDrawable(R.drawable.ic_menu_gallery);
		permissionSlide.setBgColor(Color.TRANSPARENT);
		addSlide(AppIntroFragment.newInstance(permissionSlide));

		SliderPage doneSlide = new SliderPage();
		doneSlide.setTitle("Done!");
		doneSlide.setDescription("We are good to go :)");
		doneSlide.setImageDrawable(R.drawable.ic_menu_gallery);
		doneSlide.setBgColor(Color.TRANSPARENT);
		addSlide(AppIntroFragment.newInstance(doneSlide));

		showSkipButton(false);
		askForPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
		                               Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
		showSeparator(true);
		setFadeAnimation();
	}

	@Override
	public void onDonePressed(Fragment currentFragment) {
		// Returning to the main activity
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		pref.edit().putBoolean("showIntro", false).apply();
		resultOk = true;
		setResult(RESULT_OK);
		finish();
	}

	@Override
	protected void onDestroy() {
		if (!resultOk){
			setResult(RESULT_CANCELED);
		}
		super.onDestroy();
	}
}
