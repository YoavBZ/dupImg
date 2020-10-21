package yoavbz.dupimg.treeview;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.List;

import yoavbz.dupimg.R;

import static yoavbz.dupimg.MainActivity.SELECT_SD_ROOT_CODE;

public class FileUtils {

	public static boolean deleteFile(Context context, File file) {
		if (file.delete()) {
			return true;
		}
		DocumentFile document = getDocumentFile(context, file);
		return document != null && document.delete();
	}

	/**
	 * Get a DocumentFile corresponding to the given file (for writing on ExtSdCard on Android 5). If the file is not
	 * existing, it is created.
	 *
	 * @param file The file.
	 * @return The DocumentFile
	 */
	public static DocumentFile getDocumentFile(Context context, File file) {
		final List<UriPermission> persistedUriPermissions = context.getContentResolver().getPersistedUriPermissions();
		for (UriPermission uriPermission : persistedUriPermissions) {
			final Uri treeUri = uriPermission.getUri();
			// Starting with the storage root
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
		DocumentFile documentFile = getDocumentFile(activity, file);
		if ((documentFile != null && !documentFile.canWrite()) || !file.canWrite()) {
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
