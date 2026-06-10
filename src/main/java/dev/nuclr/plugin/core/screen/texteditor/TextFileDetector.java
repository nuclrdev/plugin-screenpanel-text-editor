package dev.nuclr.plugin.core.screen.texteditor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import dev.nuclr.platform.plugin.NuclrResource;

/**
 * Utility for detecting whether a file contains text or binary content.
 * Uses a multi-strategy approach: MIME type probing, byte-level heuristics,
 * and UTF-8 validity checking.
 */
public final class TextFileDetector {

    private static final int SAMPLE_SIZE = 8192;
    private static final double NULL_BYTE_THRESHOLD = 0.0;
    private static final double CONTROL_CHAR_THRESHOLD = 0.02;

    private TextFileDetector() {
    }

    /**
     * Returns true if the file is likely a text file.
     * Combines MIME probing, byte analysis, and UTF-8 decoding.
     */
    public static boolean isTextFile(NuclrResource resource) throws IOException {
    	
        if (resource.isFolder()) {
            return false;
        }
        if (resource.getLength() == 0L) {
            return true; // empty files are trivially text
        }
        
    		Path tempFile = null;
    	
        try (var is = resource.openInputStream()) {
        	
        		if (false == Files.isRegularFile(resource.getPath())) {
        			
        			/** Create a temp file to probe the MIME type, since NuclrResource may not be a Path */
        			tempFile = Files.createTempFile("mimeprobe." + UUID.randomUUID().toString(), null);
        			Files.copy(is, tempFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        			
        			resource.getMetadata().put("tempPath", tempFile);
        			
        		} else {
        			tempFile = resource.getPath();
        		}

	        // Strategy 1: MIME type hint from the OS / file extension
	        Boolean mimeResult = checkMimeType(tempFile);
	        if (mimeResult != null) {
	            return mimeResult;
	        }
	
	        // Strategy 2: byte-level heuristic on a sample
	        byte[] sample = readSample(tempFile);
	        
	        return isSampleText(sample);
	        
        } catch (Exception ignored) {
        	// fall through to heuristic
        } finally {
        	
        }
        
        return false;
    }

    /**
     * Probes the MIME type via the platform's file type detector.
     * Returns true/false if conclusive, null if unknown.
     */
    private static Boolean checkMimeType(Path path) {
    	
        try {
        	
            String mime = Files.probeContentType(path);
            if (mime != null) {
                if (mime.startsWith("text/")) return true;
                if (mime.startsWith("image/") || mime.startsWith("audio/")
                        || mime.startsWith("video/") || mime.equals("application/octet-stream")) {
                    return false;
                }
                // Some text-like MIME types don't start with "text/"
                if (mime.contains("json") || mime.contains("xml")
                        || mime.contains("javascript") || mime.contains("yaml")
                        || mime.contains("svg") || mime.contains("csv")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            // fall through to heuristic
		}
        return null;
    }

    /**
     * Reads up to SAMPLE_SIZE bytes from the file.
     */
    private static byte[] readSample(Path path) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            return is.readNBytes(SAMPLE_SIZE);
        }
    }

    /**
     * Analyses a byte sample to determine if it looks like text.
     * Checks for null bytes, control character density, and valid UTF-8.
     */
    private static boolean isSampleText(byte[] sample) {
        if (sample.length == 0) return true;

        // Check for UTF BOM markers (strong text signal)
        if (hasBom(sample)) return true;

        int nullCount = 0;
        int controlCount = 0;

        for (byte b : sample) {
            int unsigned = b & 0xFF;
            if (unsigned == 0x00) {
                nullCount++;
            } else if (isControlChar(unsigned)) {
                controlCount++;
            }
        }

        // Any null bytes → almost certainly binary
        double nullRatio = (double) nullCount / sample.length;
        if (nullRatio > NULL_BYTE_THRESHOLD) {
            return false;
        }

        // High density of control characters → binary
        double controlRatio = (double) controlCount / sample.length;
        if (controlRatio > CONTROL_CHAR_THRESHOLD) {
            return false;
        }

        // Final check: does it decode as valid UTF-8?
        return isValidUtf8(sample);
    }

    /**
     * Returns true if the byte is a non-printable control character,
     * excluding common whitespace (tab, newline, carriage return).
     */
    private static boolean isControlChar(int b) {
        // C0 control block, excluding HT (0x09), LF (0x0A), CR (0x0D)
        if (b < 0x20 && b != 0x09 && b != 0x0A && b != 0x0D) {
            return true;
        }
        // DEL and C1 control block
        return b == 0x7F || (b >= 0x80 && b <= 0x9F);
    }

    /**
     * Checks for common Unicode BOM markers.
     */
    private static boolean hasBom(byte[] data) {
        if (data.length >= 3
                && (data[0] & 0xFF) == 0xEF
                && (data[1] & 0xFF) == 0xBB
                && (data[2] & 0xFF) == 0xBF) {
            return true; // UTF-8 BOM
        }
        if (data.length >= 2) {
            int b0 = data[0] & 0xFF;
            int b1 = data[1] & 0xFF;
            // UTF-16 LE or BE
            if ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to decode the sample as UTF-8.
     */
    private static boolean isValidUtf8(byte[] data) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}