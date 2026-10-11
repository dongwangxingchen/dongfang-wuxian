package cc.nkbr.lanzouplus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import org.junit.Test;

/**
 * [DFW-132] 音乐转换引擎的 JVM 可测部分：WAV 头字节布局 + 输出文件名清洗。
 * MediaCodec/MediaExtractor 链路是真机验证项（L2），JVM 测不了也不伪造。
 */
public class MusicConvertJvmTest {

    /** 反射取 WavHeader.header(...)（private static 内嵌类，纯字节逻辑无 Android 依赖）。 */
    private static byte[] header(int sampleRate, int channels, int bytesPerSample, long dataBytes)
            throws Exception {
        Class<?> wav = Class.forName("cc.nkbr.lanzouplus.MusicConvertEngine$WavHeader");
        Method method = wav.getDeclaredMethod("header", int.class, int.class, int.class, long.class);
        method.setAccessible(true);
        return (byte[]) method.invoke(null, sampleRate, channels, bytesPerSample, dataBytes);
    }

    private static int u16(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    private static int u32(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    @Test
    public void wavHeaderIsRiffPcm44Bytes() throws Exception {
        byte[] head = header(44100, 2, 2, 88200);
        assertEquals(44, head.length);
        assertEquals("RIFF", new String(head, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(head, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("fmt ", new String(head, 12, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("data", new String(head, 36, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(16, u32(head, 16));
        assertEquals(1, u16(head, 20));
        assertEquals(2, u16(head, 22));
        assertEquals(44100, u32(head, 24));
        assertEquals(44100 * 2 * 2, u32(head, 28));
        assertEquals(4, u16(head, 32));
        assertEquals(16, u16(head, 34));
        assertEquals(88200, u32(head, 40));
        assertEquals(36 + 88200, u32(head, 4));
    }

    @Test
    public void wavHeaderMonauralSumsCorrectly() throws Exception {
        byte[] head = header(22050, 1, 2, 4410);
        assertEquals(1, u16(head, 22));
        assertEquals(22050, u32(head, 24));
        assertEquals(22050 * 2, u32(head, 28));
        assertEquals(2, u16(head, 32));
    }

    @Test
    public void wavHeaderClampsOversizeData() throws Exception {
        // 超 2GB 的数据块长度必须夹住（WAV 头是 32 位长度字段）
        byte[] head = header(44100, 2, 2, 10L * 1024 * 1024 * 1024);
        int dataLen = u32(head, 40);
        assertTrue(dataLen > 0);
        assertTrue(dataLen <= Integer.MAX_VALUE - 44);
    }

    @Test
    public void fillOverwritesRealFileHeader() throws Exception {
        // 模拟真实链路：先写占位头 → 写假 PCM → 回填真参数，断言头部被覆盖且数据完好
        File temp = File.createTempFile("mcwav", ".wav");
        temp.deleteOnExit();
        Class<?> wav = Class.forName("cc.nkbr.lanzouplus.MusicConvertEngine$WavHeader");
        byte[] placeholder = (byte[]) wav.getDeclaredMethod("placeholder").invoke(null);
        try (FileOutputStream out = new FileOutputStream(temp)) {
            out.write(placeholder);
            out.write(new byte[1000]);
            Method fill = wav.getDeclaredMethod("fill", FileOutputStream.class, int.class,
                    int.class, int.class, long.class);
            fill.setAccessible(true);
            fill.invoke(null, out, 48000, 2, 2, 1000L);
        }
        byte[] all = Files.readAllBytes(temp.toPath());
        assertEquals(1044, all.length);
        assertEquals(48000, u32(all, 24));
        assertEquals(1000, u32(all, 40));
        assertEquals(36 + 1000, u32(all, 4));
    }

    @Test
    public void humanErrorMapsCancelAndFallback() {
        assertEquals("已取消", MusicConvertEngine.humanError(new IOException("已取消")));
        assertTrue(MusicConvertEngine.humanError(new IOException("设备不支持"))
                .startsWith("转换失败："));
        assertTrue(MusicConvertEngine.humanError(new IOException((String) null))
                .startsWith("转换失败："));
    }

    @Test
    public void extensionMappingIsStable() {
        assertEquals("wav", MusicConvertEngine.extension(MusicConvertEngine.Target.WAV));
        assertEquals("m4a", MusicConvertEngine.extension(MusicConvertEngine.Target.M4A));
        assertEquals("flac", MusicConvertEngine.extension(MusicConvertEngine.Target.FLAC));
    }
}
