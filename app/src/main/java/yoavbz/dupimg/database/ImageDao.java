package yoavbz.dupimg.database;

import android.arch.persistence.room.*;
import yoavbz.dupimg.models.Image;

import java.util.List;

@Dao
public interface ImageDao {

	@Query("SELECT * FROM images")
	List<Image> getAll();

	@Query("SELECT COUNT(*) FROM images")
	int getImageCount();

	@Insert(onConflict = OnConflictStrategy.IGNORE)
	void insert(List<Image> images);

	@Delete
	void delete(Image images);

	@Query("DELETE FROM images WHERE path LIKE :path")
	void delete(String path);

	@Query("DELETE FROM images WHERE path NOT IN (:imagesToKeep)")
	void deleteNotInList(List<String> imagesToKeep);
}
