package yoavbz.dupimg.database;

import android.arch.persistence.room.*;
import android.net.Uri;
import android.support.v4.provider.DocumentFile;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;
import java.util.List;

@Dao
public abstract class ImageDao {

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	public abstract void insert(List<Image> images);

	@Delete
	public abstract void delete(Image image);

	@Query("DELETE FROM images WHERE uri = :uri")
	public abstract void delete(Uri uri);

	@Update
	public abstract void update(Image image);

	@Query("SELECT * FROM images ORDER BY dateTaken DESC")
	public abstract List<Image> getAll();

	@Transaction
	public boolean deleteNotInList(List<DocumentFile> localImages) {
		boolean deleted = false;
		List<Uri> localUris = new ArrayList<>();
		for (DocumentFile file : localImages){
			localUris.add(file.getUri());
		}
		for (Uri uri : getAllUris()) {
			if (!localUris.contains(uri)) {
				delete(uri);
				deleted = true;
			}
		}
		return deleted;
	}

	@Query("SELECT uri FROM images ORDER BY dateTaken DESC")
	public abstract List<Uri> getAllUris();
}
