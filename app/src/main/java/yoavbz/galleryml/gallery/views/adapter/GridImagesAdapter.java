package yoavbz.galleryml.gallery.views.adapter;

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
import yoavbz.galleryml.R;
import yoavbz.galleryml.gallery.cluster.ImageCluster;
import yoavbz.galleryml.gallery.views.MediaGalleryView;

import java.util.ArrayList;

public class GridImagesAdapter extends RecyclerView.Adapter<GridImagesAdapter.ViewHolder> {
	private ArrayList<ImageCluster> mDataset;
	private Context mContext;
	private Drawable imgPlaceHolderResId;
	private MediaGalleryView.OnImageClicked mClickListener;
	private int mHeight;
	private int mWidth;

	public GridImagesAdapter(Context activity, ArrayList<ImageCluster> imageClusters, Drawable imgPlaceHolderResId) {
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
		ImageCluster cluster = mDataset.get(holder.getAdapterPosition());
		String path = cluster.getFirstImage().getPath().toString();
		Glide.with(mContext)
		     .load(path)
		     .apply(new RequestOptions().placeholder(imgPlaceHolderResId))
		     .into(holder.clusterThumbnail);
		holder.clusterSize.setText(String.valueOf(cluster.size()));
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
