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
			if (mClickListener == null) {
				return;
			}
			mClickListener.onImageClicked(holder.getAdapterPosition());
		});
		ViewGroup.LayoutParams params = holder.imageView.getLayoutParams();
		if (mHeight != -1 && mHeight != MediaGalleryView.DEFAULT)
			params.height = mHeight;
		if (mWidth != -1 && mWidth != MediaGalleryView.DEFAULT)
			params.width = mWidth;
		holder.imageView.setLayoutParams(params);
		ImageCluster cluster = mDataset.get(holder.getAdapterPosition());
		String path = cluster.getFirstImage().getPath();
		Glide.with(mContext)
				.load(path)
				.apply(new RequestOptions().placeholder(imgPlaceHolderResId))
				.into(holder.imageView);
		holder.imagesNum.setText(String.valueOf(cluster.size()));
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
		public ImageView imageView;
		public TextView imagesNum;

		ViewHolder(View itemView) {
			super(itemView);
			imageView = itemView.findViewById(R.id.image_view);
			imagesNum = itemView.findViewById(R.id.images_num);
		}
	}
}
