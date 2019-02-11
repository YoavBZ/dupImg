package yoavbz.dupimg;

import android.app.Application;
import android.content.Context;
import org.acra.ACRA;
import org.acra.annotation.AcraMailSender;
import org.acra.annotation.AcraNotification;

@AcraMailSender(mailTo = "yoav.bz4@gmail.com")
@AcraNotification(resTitle = R.string.crash_title,
		resText = R.string.crash_text,
		resChannelName = R.string.app_name)
public class MainApplication extends Application {
	@Override
	protected void attachBaseContext(Context base) {
		super.attachBaseContext(base);
		ACRA.init(this);
	}
}
