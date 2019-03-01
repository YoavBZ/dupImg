package yoavbz.dupimg.utils;

import android.content.Context;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;

import static yoavbz.dupimg.MainActivity.TAG;

public class FileUtils {

	/**
	 * Returns all available SD-Cards in the system (include emulated)
	 *
	 * @return paths to all available SD-Cards in the system (include emulated)
	 * @see <a href="https://github.com/TeamAmaze/AmazeFileManager">AmazeFileManager repo</a>
	 */
	public static ArrayList<File> getStorageDirectories(Context context) {
		final ArrayList<File> paths = new ArrayList<>();
		for (File externalFileDir : context.getExternalFilesDirs("external")) {
			if (externalFileDir != null) {
				int index = externalFileDir.getAbsolutePath().lastIndexOf("/Android/data");
				if (index < 0) {
					Log.w(TAG, "Unexpected external file dir: " + externalFileDir.getAbsolutePath());
				} else {
					File file = new File(externalFileDir.getAbsolutePath().substring(0, index));
					if (!paths.contains(file) && file.canRead() && file.isDirectory()) {
						paths.add(file);
					}
				}
			}
		}
		if (paths.isEmpty()) {
			paths.add(new File("/storage/sdcard1"));
		}
		File usb = getUsbDrive();
		if (usb != null && !paths.contains(usb)) {
			paths.add(usb);
		}
		return paths;
	}

	@Nullable
	private static File getUsbDrive() {
		File parent = new File("/storage");

		try {
			for (File f : parent.listFiles()) {
				if (f.exists() && f.getName().toLowerCase().contains("usb") && f.canExecute()) {
					return f;
				}
			}
		} catch (Exception ignored) {
		}
		String sdcard = Environment.getExternalStorageDirectory().getPath();
		parent = new File(sdcard + "/usbStorage");
		if (parent.exists() && parent.canExecute()) {
			return parent;
		}
		parent = new File(sdcard + "/usb_storage");
		if (parent.exists() && parent.canExecute()) {
			return parent;
		}

		return null;
	}
}
