package yoavbz.dupimg.database;

import android.arch.persistence.room.*;
import android.net.Uri;
import yoavbz.dupimg.models.Image;

import java.util.List;

@Dao
public abstract class ImageDao {

	@Query("SELECT * FROM images ORDER BY dateTaken DESC")
	public abstract List<Image> getAll();

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	public abstract void insert(List<Image> images);

	@Delete
	public abstract void delete(Image image);

	@Query("DELETE FROM images WHERE uri = :uri")
	public abstract void delete(Uri uri);

	@Transaction
	public boolean deleteNotInList(List<Uri> localImages) {
		boolean deleted = false;
		for (Uri uri : getAllUris()) {
			if (!localImages.contains(uri)) {
				delete(uri);
				deleted = true;
			}
		}
		return deleted;
	}

	@Query("SELECT uri FROM images ORDER BY dateTaken DESC")
	public abstract List<Uri> getAllUris();
}
