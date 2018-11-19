package yoavbz.galleryml.models;

import android.app.Activity;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.media.ExifInterface;
import android.util.Log;
import android.widget.Toast;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import yoavbz.galleryml.ImageClassifier;
import yoavbz.galleryml.database.ImageDao;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

@Entity(tableName = "images")
public class Image implements Parcelable {

	public static final Creator<Image> CREATOR = new Creator<Image>() {
		public Image createFromParcel(Parcel in) {
			return new Image(in);
		}

		public Image[] newArray(int size) {
			return new Image[size];
		}
	};

	@PrimaryKey
	@NonNull
	private Path path;
	private Date dateTaken;
	private DoublePoint point;

	public Image(@NonNull Path path) {
		this.path = path;
		try {
			ExifInterface exif = new ExifInterface(this.path.toString());
			String date = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL);
			if (date == null) {
				date = exif.getAttribute(ExifInterface.TAG_DATETIME);
			}
			DateFormat dateFormat = new SimpleDateFormat("yyyy:MM:dd HH:mm:ss");
			dateTaken = dateFormat.parse(date);
		} catch (IOException | ParseException e) {
			Log.e("Image", "Got an exception while constructing image: " + e);
		}
	}

	public Image(String path) {
		this(Paths.get(path));
	}

	private Image(Parcel in) {
		path = Paths.get(in.readString());
		dateTaken = new Date(in.readLong());
		point = new DoublePoint(in.createDoubleArray());
	}

	@NonNull
	public Path getPath() {
		return path;
	}

	public void setPath(@NonNull Path path) {
		this.path = path;
	}

	public Date getDateTaken() {
		return dateTaken;
	}

	public void setDateTaken(Date dateTaken) {
		this.dateTaken = dateTaken;
	}

	public DoublePoint getPoint() {
		return point;
	}

	public void setPoint(DoublePoint point) {
		this.point = point;
	}

	// --- Parcelable interface functions ---

	/**
	 * Calculating the feature vector using a given classifier
	 *
	 * @param classifier The classifier to run the TensorFlow interpreter for calculating feature vector
	 */
	public void calculateFeatureVector(ImageClassifier classifier) {
		Bitmap bitmap = BitmapFactory.decodeFile(path.toString());
		bitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, false);
		point = new DoublePoint(classifier.recognizeImage(bitmap));
	}

	public void delete(Activity activity, ImageDao dao) {
		if (path.toFile().delete()) {
			dao.delete(this);
		} else {
			activity.runOnUiThread(() -> {
				Toast.makeText(activity, "Couldn't delete file " + toString(), Toast.LENGTH_SHORT).show();
			});
		}
	}

	@Override
	public int describeContents() {
		return 0;
	}

	@Override
	public void writeToParcel(Parcel dest, int flags) {
		dest.writeString(path.toString());
		dest.writeLong(dateTaken.getTime());
		dest.writeDoubleArray(point.getPoint());
	}

	@Override
	public String toString() {
		return path.getFileName().toString();
	}
}
