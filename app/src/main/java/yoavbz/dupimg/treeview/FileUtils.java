package yoavbz.dupimg.treeview;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;
import yoavbz.dupimg.R;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import static yoavbz.dupimg.MainActivity.SELECT_SD_ROOT_CODE;
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

	public static boolean deleteFile(Context context, File file) {
		if (file.delete()) {
			return true;
		}
		DocumentFile document = getDocumentFile(context, file);
		return document.delete();
	}

	/**
	 * Get a DocumentFile corresponding to the given file (for writing on ExtSdCard on Android 5). If the file is not
	 * existing, it is created.
	 *
	 * @param file The file.
	 * @return The DocumentFile
	 */
	public static DocumentFile getDocumentFile(Context context, File file) {
		Set<String> uris = PreferenceManager.getDefaultSharedPreferences(context)
		                                    .getStringSet("STORAGE_URIS", Collections.emptySet());
		for (String s : uris) {
			Uri treeUri = Uri.parse(s);
			// Starting with root of storage
			DocumentFile document = DocumentFile.fromTreeUri(context, treeUri);
			// Finding the correct Uri from the set

			String[] parts = file.getAbsolutePath().split("/");
			// Walking up the tree
			for (int i = 2; i < parts.length; i++) {
				DocumentFile nextFile = document.findFile(parts[i]);
				if (nextFile != null) {
					document = nextFile;
				}
			}
			if (file.getName().equals(document.getName())) {
				return document;
			}
		}
		return null;
	}

	public static void showSdcardDialogIfNeeded(@NonNull Activity activity, String path) {
		File file = new File(path);
		if (!file.canWrite()) {
//		if (!file.canWrite() && !FileUtils.getDocumentFile(activity, file).canWrite()) {
			FileUtils.sdcardGuideDialog(activity, file);
		}
	}

	public static void sdcardGuideDialog(@NonNull Activity activity, File file) {
		View sdcardDialog = activity.getLayoutInflater().inflate(R.layout.sdcard_dialog, null);
		TextView description = sdcardDialog.findViewById(R.id.description);
		description.setText("Please choose the root directory of:\n" + file.getPath() +
				                    "\nto grant write access to operate");
		new AlertDialog.Builder(activity)
				.setTitle("Permission is needed")
				.setView(sdcardDialog)
				.setPositiveButton("Open", (dialog, which) -> {
					Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
					activity.startActivityForResult(intent, SELECT_SD_ROOT_CODE);
				})
				.setNegativeButton("Cancel", (dialog, which) ->
						Toast.makeText(activity, "Error", Toast.LENGTH_SHORT).show())
				.show();
	}
}
