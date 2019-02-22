package yoavbz.dupimg.models;

import android.annotation.SuppressLint;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.media.ExifInterface;
import android.support.v4.provider.DocumentFile;
import android.util.Log;
import org.apache.commons.math3.ml.clustering.Clusterable;
import yoavbz.dupimg.ImageClassifier;
import yoavbz.dupimg.MainActivity;
import yoavbz.dupimg.database.ImageDao;
import yoavbz.dupimg.database.ImageDatabase;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

@SuppressWarnings("ConstantConditions")
@Entity(tableName = "images")
public class Image implements Parcelable, Clusterable {

	@PrimaryKey
	@NonNull
	private Uri uri;
	private long dateTaken;
	private double[] point;

	@Ignore
	@SuppressLint("SimpleDateFormat")
	private static DateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");

	public Image() {
	}

	/**
	 * Constructor for Image object: Extracts EXIF DATETIME attributes, fallbacks to the creationTime if unavailable
	 * In addition, generates feature vector using the context's {@link android.content.ContentResolver}
	 * for opening InputStream of the {@link DocumentFile} corresponding Uri
	 *
	 * @param uri        The image {@link Uri}
	 * @param context    A context for getting {@link android.content.ContentResolver}
	 * @param classifier A TensorFlow Lite classifier, for generating feature vector (vector field)
	 */
	public Image(@NonNull Uri uri, @NonNull Context context, @NonNull ImageClassifier classifier) throws IOException {
		this.uri = uri;
		point = classifier.recognizeImage(getScaledBitmap(context));
		getDateTaken(context);
	}

	@Nullable
	public Bitmap getOrientedBitmap(Context context) {
		try {
			int rotation;
			// Extracting rotation state
			ContentResolver contentResolver = context.getContentResolver();
			try (InputStream in = contentResolver.openInputStream(uri)) {
				ExifInterface exif = new ExifInterface(in);
				switch (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_UNDEFINED)) {
					case ExifInterface.ORIENTATION_ROTATE_90:
						rotation = 90;
						break;
					case ExifInterface.ORIENTATION_ROTATE_180:
						rotation = 180;
						break;
					case ExifInterface.ORIENTATION_ROTATE_270:
						rotation = 270;
						break;
					default:
						rotation = ExifInterface.ORIENTATION_UNDEFINED;
				}
			}
			// Constructing a Bitmap
			Bitmap bitmap = getScaledBitmap(context);
			// Rotating
			Matrix rotationMatrix = new Matrix();
			rotationMatrix.postRotate(rotation);
			return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
			                           rotationMatrix, false);
		} catch (Exception e) {
			Log.e(MainActivity.TAG, "Got an exception while constructing oriented Bitmap", e);
		}
		return null;
	}

	@NonNull
	public Uri getUri() {
		return uri;
	}

	public void setUri(@NonNull Uri uri) {
		this.uri = uri;
	}

	public long getDateTaken() {
		return dateTaken;
	}

	public long getDateTaken(Context context) {
		if (dateTaken == 0) {
			try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
				// Extracting DATETIME_ORIGINAL
				ExifInterface exif = new ExifInterface(inputStream);
				String date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
				dateTaken = dateFormat.parse(date).getTime();
			} catch (Exception e) {
				DocumentFile file = DocumentFile.fromSingleUri(context, uri);
				Log.e(MainActivity.TAG, "Image - Got an exception while extracting date from " + file.getName(), e);
				// Fallback - Extracting creationTime attribute
				dateTaken = file.lastModified();
			}
			// Update image in DB
			ImageDatabase.getAppDatabase(context).imageDao().update(this);
		}
		return dateTaken;
	}

	public void setDateTaken(long dateTaken) {
		this.dateTaken = dateTaken;
	}

	public void setPoint(double[] point) {
		this.point = point;
	}

	@NonNull
	@Override
	public String toString() {
		return uri.getLastPathSegment();
	}

	/**
	 * @param context A context to access the image's InputStream (from its {@link Uri}) using {@link ContentResolver}
	 * @return A scaled Bitmap representation of the image (224x224 pixels)
	 */
	private Bitmap getScaledBitmap(Context context) throws IOException {
		try (InputStream inputStream = context.getContentResolver().openInputStream(uri)) {
			Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
			return Bitmap.createScaledBitmap(bitmap, 224, 224, false);
		}
	}

	public void delete(Context context) {
		ImageDao dao = ImageDatabase.getAppDatabase(context).imageDao();
		if (DocumentFile.fromSingleUri(context, uri).delete()) {
			dao.delete(this);
			Log.d(MainActivity.TAG, "Image: Deleted " + toString());
		} else {
			Log.e(MainActivity.TAG, "Image: Couldn't delete file " + toString());
		}
	}

	// --- Clusterable interface functions ---

	@Override
	public double[] getPoint() {
		return point;
	}

	// --- Parcelable interface functions ---

	private Image(Parcel in) {
		uri = Uri.parse(in.readString());
		dateTaken = in.readLong();
		point = in.createDoubleArray();
	}

	public static final Creator<Image> CREATOR = new Creator<Image>() {
		public Image createFromParcel(Parcel in) {
			return new Image(in);
		}

		public Image[] newArray(int size) {
			return new Image[size];
		}
	};

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(uri.toString());
		dest.writeLong(dateTaken);
		dest.writeDoubleArray(point);
	}

	@Override
	public int describeContents() {
		return 0;
	}
}
