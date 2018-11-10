package yoavbz.galleryml;

import android.Manifest;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

public class IntroActivity extends AppIntro {

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getSupportActionBar().hide();

		SliderPage welcomeSlide = new SliderPage();
		welcomeSlide.setTitle("Welcome!");
		welcomeSlide.setDescription("This App will help you clean your duplicated photos using advanced machine learning algorithms!");
		welcomeSlide.setImageDrawable(R.drawable.ic_menu_gallery);
		welcomeSlide.setBgColor(Color.TRANSPARENT);
		addSlide(AppIntroFragment.newInstance(welcomeSlide));

		SliderPage permissionSlide = new SliderPage();
		permissionSlide.setTitle("App Permissions");
		permissionSlide.setDescription("In order to scan and modify files, please click the 'Next' button and approve the asked permissions");
		permissionSlide.setImageDrawable(R.drawable.ic_menu_gallery);
		permissionSlide.setBgColor(Color.TRANSPARENT);
		addSlide(AppIntroFragment.newInstance(permissionSlide));

		addSlide(new BackgroundSlide());
//		SliderPage backgroundService = new SliderPage();
//		backgroundService.setTitle("Background Monitor");
//		backgroundService.setDescription("The app can monitor your taken photos and automatically suggest you to clean the found duplicates");
//		backgroundService.setImageDrawable(R.drawable.ic_menu_gallery);
//		backgroundService.setBgColor(Color.TRANSPARENT);
//		addSlide(AppIntroFragment.newInstance(backgroundService));

		SliderPage doneSlide = new SliderPage();
		doneSlide.setTitle("Done!");
		doneSlide.setDescription("We are good to go :)");
		doneSlide.setImageDrawable(R.drawable.ic_menu_gallery);
		doneSlide.setBgColor(Color.TRANSPARENT);
		addSlide(AppIntroFragment.newInstance(doneSlide));

		showSkipButton(false);
		askForPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2);
		showSeparator(true);
		setFadeAnimation();
	}

	@Override
	public void onDonePressed(Fragment currentFragment) {
		// Returning to the main activity
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
		pref.edit().putBoolean("showIntro", false).apply();
		setResult(RESULT_OK);
		finish();
	}

	public static class BackgroundSlide extends Fragment {

		@Nullable
		@Override
		public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
			View view = inflater.inflate(R.layout.background_slide, container, false);
			Switch backgroundEnabled = view.findViewById(R.id.background_enabled);
			backgroundEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> {
				// Save switch status to SharedPreferences
				SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(getContext());
				pref.edit().putBoolean("backgroundMonitor", isChecked).apply();
			});
			return view;
		}
	}
}
