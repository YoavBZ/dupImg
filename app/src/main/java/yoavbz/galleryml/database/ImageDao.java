package yoavbz.galleryml.database;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import yoavbz.galleryml.gallery.Image;

import java.util.Date;
import java.util.List;

@Dao
public interface ImageDao {

	@Query("SELECT * FROM images")
	List<Image> getAll();

	@Query("SELECT * FROM images WHERE dateTaken BETWEEN :start AND :end")
	List<Image> getByRange(Date start, Date end);

	@Query("SELECT COUNT(*) FROM images")
	int getImageCount();

	@Insert
	void insert(Image image);

	@Insert
	void Insert(Image... images);

	@Query("DELETE FROM images WHERE path LIKE :path")
	void delete(String path);

	@Delete
	void delete(Image... images);
}
