package net.ritzow.news.content;

import ar.com.hjg.pngj.*;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.google.zxing.qrcode.encoder.ByteMatrix;
import com.google.zxing.qrcode.encoder.QRCode;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class QR {
	public static void qrCode() throws WriterException, IOException {
		QRCode code = com.google.zxing.qrcode.encoder.Encoder.encode(
			"https://stackoverflow.com/questions/4839128/optimizing-long-bitcount", ErrorCorrectionLevel.H, Map.of(
			EncodeHintType.QR_COMPACT, Boolean.TRUE
		));
		var result = code.getMatrix();
		try(var out = new OutputStreamWriter(new FileOutputStream(FileDescriptor.out), StandardCharsets.UTF_8)) {
			out.write(toString(code));
			/*for(int i = 0; i < result.getHeight(); i++) {
				for(int j = 0; j < result.getWidth(); j++) {
					out.write(result.get(j, i) == 1 ? "██" : "  ");
				}
				out.write("\n");
			}	*/
		}
	}
	
	public static QRCode example() throws WriterException {
		return com.google.zxing.qrcode.encoder.Encoder.encode(
			"https://stackoverflow.com/questions/4839128/optimizing-long-bitcount", ErrorCorrectionLevel.H, Map.of(
				EncodeHintType.QR_COMPACT, Boolean.TRUE
			));
	}
	
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
	
	public static void toImage(QRCode code) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ByteMatrix result = code.getMatrix();
		var info = new ImageInfo(result.getWidth(), result.getHeight(), 1, false, true, false);
		var writer = new PngWriter(out, info);
		writer.setCompLevel(9);
		var line = ImageLineInt.getFactory(info).createImageLine(info);
		for(int i = 0; i < result.getHeight(); i++) {
			for(int j = 0; j < result.getWidth(); j++) {
				line.getScanline()[j] = result.get(j, i);
			}
			writer.writeRow(line);
		}
		writer.end();
		Files.write(Path.of("testimage.png"), out.toByteArray());
	}
}
