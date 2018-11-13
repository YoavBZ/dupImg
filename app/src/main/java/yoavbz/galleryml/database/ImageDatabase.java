package yoavbz.galleryml.database;

import android.arch.persistence.room.*;
import android.content.Context;
import org.apache.commons.math3.ml.clustering.DoublePoint;
import yoavbz.galleryml.gallery.Image;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

@Database(entities = Image.class, version = 1, exportSchema = false)
@TypeConverters(ImageDatabase.Converters.class)
public abstract class ImageDatabase extends RoomDatabase {

	private static ImageDatabase INSTANCE;

	public static ImageDatabase getAppDatabase(Context context) {
		if (INSTANCE == null) {
			INSTANCE = Room.databaseBuilder(context.getApplicationContext(), ImageDatabase.class,
			                                "image-database.db").build();
		}
		return INSTANCE;
	}

	public static void destroyInstance() {
		INSTANCE = null;
	}

	public abstract ImageDao imageDao();

	public static class Converters {
		@TypeConverter
		public static Date fromTimestamp(Long value) {
			return value == null ? null : new Date(value);
		}

		@TypeConverter
		public static Long dateToTimestamp(Date date) {
			return date == null ? null : date.getTime();
		}

		@TypeConverter
		public static String pathToString(Path path) {
			return path.toString();
		}

		@TypeConverter
		public static Path pathToString(String path) {
			return Paths.get(path);
		}

		@TypeConverter
		public static String pointToString(DoublePoint point) {
			double[] arr = point.getPoint();
			StringBuilder str = new StringBuilder().append(arr[0]);
			for (int i = 0; i < arr.length - 1; i++) {
				str.append(",").append(arr[i]);
			}
			return str.toString();
		}

		@TypeConverter
		public static DoublePoint stringToPoint(String str) {
			String[] strArray = str.split(",");
			double[] doubleArray = new double[str.length()];
			for (int i = 0; i < strArray.length; i++) {
				doubleArray[i] = Double.parseDouble(strArray[i]);
			}
			return new DoublePoint(doubleArray);
		}

	}
}
