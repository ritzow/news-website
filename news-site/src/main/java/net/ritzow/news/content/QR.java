package net.ritzow.news.content;

import ar.com.hjg.pngj.*;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.QRCode;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.Deflater;

public class QR {
	
	public static String toString(QRCode code) {
		var result = code.getMatrix();
		int rowSize = result.getWidth() * 2;
		char[] codePoints = new char[rowSize * result.getHeight() + result.getHeight() - 1];
		/* Index across QRCode matrix */
		for(int i = 0; i < result.getHeight(); i++) {
			for(int j = 0; j < result.getWidth(); j++) {
				char value = result.get(j, i) == 1 ? '█' : ' ';
				int index = i * (rowSize + 1) + j * 2;
				codePoints[index] = value;
				codePoints[index + 1] = value;
			}
		}
		
		for(int i = rowSize; i < codePoints.length; i += rowSize + 1) {
			codePoints[i] = '\n';
		}
		return new String(codePoints);
	}
	
	public static byte[] toQrPng(String url) throws WriterException {
		return toImage(com.google.zxing.qrcode.encoder.Encoder.encode(
			url, ErrorCorrectionLevel.L, Map.of(
				EncodeHintType.QR_COMPACT, Boolean.TRUE
			)));
	}
	
	public static byte[] toImage(QRCode code) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteMatrix result = code.getMatrix();
		var info = new ImageInfo(result.getWidth(), result.getHeight(), 1, false, true, false);
		var writer = new PngWriter(out, info);
		writer.getPixelsWriter().setDeflaterCompLevel(0);
		writer.getPixelsWriter().setFilterType(FilterType.FILTER_NONE);
		writer.getPixelsWriter().setDeflaterStrategy(Deflater.NO_COMPRESSION);
		var line = ImageLineByte.getFactory(info).createImageLine(info);
		for(int i = 0; i < result.getHeight(); i++) {
			System.arraycopy(result.getArray()[i], 0, line.getScanline(), 0, result.getWidth());
			writer.writeRow(line);
		}
		writer.end();
		return out.toByteArray();
	}
}
