package yoavbz.dupimg;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import org.tensorflow.lite.Interpreter;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

public class ImageClassifier implements Closeable {

	private Interpreter interpreter;
	private int inputSize;
	private List<String> labelList;

	public ImageClassifier(@NonNull Context context, String modelPath, String labelPath, int inputSize)
			throws IOException {
		AssetManager assetManager = context.getAssets();
		interpreter = new Interpreter(loadModelFile(assetManager, modelPath));
		labelList = loadLabelList(assetManager, labelPath);
		this.inputSize = inputSize;
	}

	public double[] recognizeImage(Bitmap bitmap) {
		ByteBuffer byteBuffer = convertBitmapToByteBuffer(bitmap);
		byte[][] result = new byte[1][labelList.size()];
		interpreter.run(byteBuffer, result);
		double[] vector = new double[result[0].length];
		for (int i = 0; i < labelList.size(); ++i) {
			// Calculating feature confidence
			vector[i] = (result[0][i] & 0xff) / 255.0f;
		}
		return vector;
	}

	@Override
	public void close() {
		interpreter.close();
		interpreter = null;
	}

	private MappedByteBuffer loadModelFile(AssetManager assetManager, String modelPath) throws IOException {
		AssetFileDescriptor fileDescriptor = assetManager.openFd(modelPath);
		FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
		FileChannel fileChannel = inputStream.getChannel();
		long startOffset = fileDescriptor.getStartOffset();
		long declaredLength = fileDescriptor.getDeclaredLength();
		return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
	}

	private List<String> loadLabelList(AssetManager assetManager, String labelPath) throws IOException {
		List<String> labelList = new ArrayList<>();
		BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open(labelPath)));
		String line;
		while ((line = reader.readLine()) != null) {
			labelList.add(line);
		}
		reader.close();
		return labelList;
	}

	private ByteBuffer convertBitmapToByteBuffer(@NonNull Bitmap bitmap) {
		final int BATCH_SIZE = 1;
		final int PIXEL_SIZE = 3;
		ByteBuffer byteBuffer = ByteBuffer.allocateDirect(BATCH_SIZE * inputSize * inputSize * PIXEL_SIZE);
		byteBuffer.order(ByteOrder.nativeOrder());
		int[] intValues = new int[inputSize * inputSize];
		bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
		int pixel = 0;
		for (int i = 0; i < inputSize; ++i) {
			for (int j = 0; j < inputSize; ++j) {
				final int val = intValues[pixel++];
				byteBuffer.put((byte) ((val >> 16) & 0xFF));
				byteBuffer.put((byte) ((val >> 8) & 0xFF));
				byteBuffer.put((byte) (val & 0xFF));
			}
		}
		return byteBuffer;
	}
}