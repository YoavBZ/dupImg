package yoavbz.dupimg.gallery.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import org.apache.commons.math3.ml.clustering.Cluster;
import yoavbz.dupimg.R;
import yoavbz.dupimg.gallery.MediaGalleryView;
import yoavbz.dupimg.models.Image;

import java.util.ArrayList;
import java.util.List;

public class GridImagesAdapter extends RecyclerView.Adapter<GridImagesAdapter.ViewHolder> {
	private ArrayList<Cluster<Image>> mDataset;
	private Context mContext;
	private Drawable imgPlaceHolderResId;
	private MediaGalleryView.OnImageClicked mClickListener;
	private int mHeight;
	private int mWidth;

	public GridImagesAdapter(Context activity, ArrayList<Cluster<Image>> imageClusters, Drawable imgPlaceHolderResId) {
		super();
		this.mDataset = imageClusters;
		this.mContext = activity;
		this.imgPlaceHolderResId = imgPlaceHolderResId;
	}

	@Override
	@NonNull
	@SuppressLint("InflateParams")
	public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
		return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_image, null));
	}

	@Override
	public void onBindViewHolder(@NonNull final ViewHolder holder, final int position) {
		holder.itemView.setOnClickListener(view -> {
			if (mClickListener != null) {
				mClickListener.onImageClicked(holder.getAdapterPosition());
			}
		});
		ViewGroup.LayoutParams params = holder.clusterThumbnail.getLayoutParams();
		if (mHeight != -1 && mHeight != MediaGalleryView.DEFAULT_SIZE) {
			params.height = mHeight;
		}
		if (mWidth != -1 && mWidth != MediaGalleryView.DEFAULT_SIZE) {
			params.width = mWidth;
		}
		holder.clusterThumbnail.setLayoutParams(params);
		List<Image> images = mDataset.get(holder.getAdapterPosition()).getPoints();
		String path = images.get(0).getPath().toString();
		Glide.with(mContext)
		     .load(path)
		     .apply(new RequestOptions().placeholder(imgPlaceHolderResId))
		     .into(holder.clusterThumbnail);
		holder.clusterSize.setText(String.valueOf(images.size()));
	}

	public void setImgPlaceHolder(Drawable imgPlaceHolderResId) {
		this.imgPlaceHolderResId = imgPlaceHolderResId;
	}

	@Override
	public int getItemCount() {
		return mDataset.size();
	}

	public void setOnImageClickListener(MediaGalleryView.OnImageClicked onImageClickListener) {
		this.mClickListener = onImageClickListener;
	}

	public void setImageSize(int width, int height) {
		this.mWidth = width;
		this.mHeight = height;
	}

	class ViewHolder extends RecyclerView.ViewHolder {
		ImageView clusterThumbnail;
		TextView clusterSize;

		ViewHolder(View itemView) {
			super(itemView);
			clusterThumbnail = itemView.findViewById(R.id.cluster_thumbnail);
			clusterSize = itemView.findViewById(R.id.cluster_size);
		}
	}
}
