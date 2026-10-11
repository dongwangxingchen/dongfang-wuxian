package cc.nkbr.lanzouplus;

import android.media.MediaCodec;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

/**
 * [DFW-132] 音乐转换引擎（工具箱「音乐转换」）。
 *
 * ## 选型（研究结论，卡 DFW-132）
 * - ffmpeg-kit 2025-01-06 官方退役、二进制下架，不引入；
 * - 全部用 Android 系统自带 MediaCodec/MediaExtractor/MediaMuxer（CDD 保证 AAC 编码器存在，
 *   minSdk 26 保证 FLAC/Opus 解码可用），零第三方依赖（铁律 3）；
 * - MP3 编码器系统不保证有，1.0.2 不做 MP3 输出（java-lame 引入前需用户明确批准）。
 *
 * ## 链路
 *   MediaExtractor(任意容器) → 解码器 → PCM → [WAV 封装 | AAC 编码+Muxer | FLAC 编码+Muxer]
 *
 * ## 线程与回调
 * 全部方法阻塞式，调用方必须在后台线程（MainActivity 的 io 执行器）跑；
 * progress 回调在转换线程上被调，参数 0..100；isCancelled 轮询点在每块 buffer 之间。
 */
final class MusicConvertEngine {

    /** 支持的输出格式。MP3 故意不在列表里（见类注释）。 */
    enum Target { WAV, M4A, FLAC }

    /** 转换期间周期回调；在转换线程调用，UI 更新需自行 post。 */
    interface Progress {
        void onProgress(int percent);
    }

    private MusicConvertEngine() {}

    /** FLAC/Opus 编码器不是每台设备都有，运行时探测（CDD 只强制 AAC）。 */
    static boolean encoderAvailable(String mime) {
        for (int i = 0; i < MediaCodecList.getCodecCount(); i++) {
            android.media.MediaCodecInfo info = MediaCodecList.getCodecInfoAt(i);
            if (info.isEncoder()) {
                for (String type : info.getSupportedTypes()) {
                    if (type.equalsIgnoreCase(mime)) return true;
                }
            }
        }
        return false;
    }

    /** 输出扩展名。 */
    static String extension(Target target) {
        switch (target) {
            case WAV: return "wav";
            case M4A: return "m4a";
            case FLAC: return "flac";
        }
        return "bin";
    }

    /**
     * 主入口：把 inputUri 指向的音频转成 target 格式写到 outFile。
     *
     * @throws IOException 文件损坏/DRM/不支持的格式/输出格式设备不支持
     */
    static void convert(android.content.Context context, android.net.Uri inputUri,
                        Target target, File outFile, Progress progress,
                        java.util.function.BooleanSupplier isCancelled) throws IOException {
        if (target == Target.FLAC && !encoderAvailable(MediaFormat.MIMETYPE_AUDIO_FLAC)) {
            throw new IOException("这台设备不支持 FLAC 编码");
        }
        MediaExtractor extractor = new MediaExtractor();
        try {
            android.content.res.AssetFileDescriptor afd = context.getContentResolver()
                    .openAssetFileDescriptor(inputUri, "r");
            if (afd == null) throw new IOException("打不开这个文件");
            try {
                extractor.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            } finally {
                try { afd.close(); } catch (IOException ignored) {}
            }
            int trackIndex = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat f = extractor.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    trackIndex = i;
                    format = f;
                    break;
                }
            }
            if (trackIndex < 0 || format == null) throw new IOException("里面没有音频轨");
            extractor.selectTrack(trackIndex);
            long durationUs = format.containsKey(MediaFormat.KEY_DURATION)
                    ? format.getLong(MediaFormat.KEY_DURATION) : 0L;

            // ── 第一步：解到 PCM（MemoryPipe 里转），边解边喂第二步 ──
            switch (target) {
                case WAV:
                    decodeToWav(extractor, format, outFile, durationUs, progress, isCancelled);
                    break;
                case M4A:
                    transcode(extractor, format, outFile, MediaFormat.MIMETYPE_AUDIO_AAC,
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4,
                            durationUs, progress, isCancelled);
                    break;
                case FLAC:
                    transcode(extractor, format, outFile, MediaFormat.MIMETYPE_AUDIO_FLAC,
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_OGG,
                            durationUs, progress, isCancelled);
                    break;
            }
        } finally {
            try { extractor.release(); } catch (Exception ignored) {}
        }
    }

    // ────────────────────────── 解码 → WAV ──────────────────────────

    private static void decodeToWav(MediaExtractor extractor, MediaFormat inputFormat,
                                    File outFile, long durationUs, Progress progress,
                                    java.util.function.BooleanSupplier isCancelled) throws IOException {
        String decodeMime = inputFormat.getString(MediaFormat.KEY_MIME);
        MediaCodec decoder = MediaCodec.createDecoderByType(decodeMime);
        // PCM 参数在 configure 阶段就要定（输出格式声明）
        int sampleRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        int channels = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;

        FileOutputStream out = new FileOutputStream(outFile);
        try {
            // WAV 头先占位，编完回填大小
            out.write(WavHeader.placeholder());
            decoder.configure(inputFormat, null, null, 0);
            decoder.start();
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            long writtenFrames = 0;
            while (!outputDone) {
                if (isCancelled.getAsBoolean()) {
                    throw new IOException("已取消");
                }
                if (!inputDone) {
                    int inIndex = decoder.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(inIndex);
                        int sampleSize = buffer == null ? 0 : extractor.readSampleData(buffer, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            decoder.queueInputBuffer(inIndex, 0, sampleSize, pts, 0);
                            extractor.advance();
                        }
                    }
                }
                int outIndex = decoder.dequeueOutputBuffer(info, 10_000);
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat om = decoder.getOutputFormat();
                    sampleRate = om.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                            ? om.getInteger(MediaFormat.KEY_SAMPLE_RATE) : sampleRate;
                    channels = om.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                            ? om.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : channels;
                } else if (outIndex >= 0) {
                    ByteBuffer pcm = decoder.getOutputBuffer(outIndex);
                    if (pcm != null && info.size > 0) {
                        pcm.position(info.offset);
                        pcm.limit(info.offset + info.size);
                        byte[] chunk = new byte[info.size];
                        pcm.get(chunk);
                        out.write(chunk);
                        writtenFrames += info.size / (channels * 2);
                    }
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    decoder.releaseOutputBuffer(outIndex, false);
                    if (eos) outputDone = true;
                    if (durationUs > 0 && progress != null) {
                        long played = info.presentationTimeUs;
                        progress.onProgress((int) Math.min(99, played * 100 / durationUs));
                    }
                }
            }
            // 回填 WAV 头（PCM16 = 2 字节每样本）
            WavHeader.fill(out, sampleRate, channels, 2, writtenFrames * (channels * 2));
            if (progress != null) progress.onProgress(100);
        } finally {
            try { decoder.stop(); } catch (Exception ignored) {}
            try { decoder.release(); } catch (Exception ignored) {}
            try { out.close(); } catch (IOException ignored) {}
        }
    }

    // ────────────────────────── 解码 → 编码 → Muxer ──────────────────────────

    private static void transcode(MediaExtractor extractor, MediaFormat inputFormat, File outFile,
                                  String encoderMime, int muxerKind, long durationUs,
                                  Progress progress,
                                  java.util.function.BooleanSupplier isCancelled) throws IOException {
        String decodeMime = inputFormat.getString(MediaFormat.KEY_MIME);
        int sampleRate = inputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)
                ? inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE) : 44100;
        int channels = inputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                ? inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) : 2;

        MediaCodec decoder = MediaCodec.createDecoderByType(decodeMime);
        MediaCodec encoder = MediaCodec.createEncoderByType(encoderMime);
        MediaMuxer muxer = null;
        File tempFile = new File(outFile.getParentFile(), outFile.getName() + ".part");
        try {
            MediaFormat encodeFormat = MediaFormat.createAudioFormat(encoderMime, sampleRate, channels);
            if (encoderMime.equals(MediaFormat.MIMETYPE_AUDIO_AAC)) {
                encodeFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                        android.media.MediaCodecInfo.CodecProfileLevel.AACObjectLC);
                encodeFormat.setInteger(MediaFormat.KEY_BIT_RATE, aacBitRate(sampleRate, channels));
            }
            // MAX_INPUT_SIZE 给足余量，避免 FLAC 高码率输入被截
            encodeFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 262144);
            encoder.configure(encodeFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            decoder.configure(inputFormat, null, null, 0);
            decoder.start();

            muxer = new MediaMuxer(tempFile.getAbsolutePath(), muxerKind);
            int muxerTrack = -1;
            boolean muxerStarted = false;
            MediaCodec.BufferInfo decInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo encInfo = new MediaCodec.BufferInfo();
            boolean decInputDone = false, decOutputDone = false, encOutputDone = false;
            // 解码输出直接当编码输入（PCM 在两个 codec 的 buffer 间拷贝）
            while (!encOutputDone) {
                if (isCancelled.getAsBoolean()) throw new IOException("已取消");
                // 1) 喂解码器
                if (!decInputDone) {
                    int inIndex = decoder.dequeueInputBuffer(10_000);
                    if (inIndex >= 0) {
                        ByteBuffer buffer = decoder.getInputBuffer(inIndex);
                        int size = buffer == null ? 0 : extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            decInputDone = true;
                        } else {
                            decoder.queueInputBuffer(inIndex, 0, size, extractor.getSampleTime(), 0);
                            extractor.advance();
                        }
                    }
                }
                // 2) 解码输出 → 编码输入
                if (!decOutputDone) {
                    int outIndex = decoder.dequeueOutputBuffer(decInfo, 10_000);
                    if (outIndex >= 0) {
                        ByteBuffer pcm = decoder.getOutputBuffer(outIndex);
                        boolean eos = (decInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                        if (pcm != null && decInfo.size > 0) {
                            pcm.position(decInfo.offset);
                            pcm.limit(decInfo.offset + decInfo.size);
                            int encIn = encoder.dequeueInputBuffer(10_000);
                            if (encIn >= 0) {
                                ByteBuffer encBuf = encoder.getInputBuffer(encIn);
                                encBuf.clear();
                                int limit = Math.min(decInfo.size, encBuf.capacity());
                                byte[] chunk = new byte[limit];
                                pcm.get(chunk);
                                encBuf.put(chunk);
                                encoder.queueInputBuffer(encIn, 0, limit, decInfo.presentationTimeUs,
                                        eos ? MediaCodec.BUFFER_FLAG_END_OF_STREAM : 0);
                            } else if (eos) {
                                // 拿不到输入 buffer 但解码器已到 EOS：必须把 EOS 传给编码器，
                                // 否则 encOutputDone 永远等不到。index 0 在编码器启动后总是可用的。
                                encoder.queueInputBuffer(0, 0, 0, decInfo.presentationTimeUs,
                                        MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            }
                        } else if (eos) {
                            encoder.queueInputBuffer(0, 0, 0, decInfo.presentationTimeUs,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        }
                        decoder.releaseOutputBuffer(outIndex, false);
                        if (eos) decOutputDone = true;
                        if (durationUs > 0 && progress != null) {
                            progress.onProgress((int) Math.min(99, decInfo.presentationTimeUs * 100 / durationUs));
                        }
                    }
                }
                // 3) 编码输出 → muxer
                int encOut = encoder.dequeueOutputBuffer(encInfo, 10_000);
                if (encOut == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    muxerTrack = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                    muxerStarted = true;
                } else if (encOut >= 0) {
                    ByteBuffer encoded = encoder.getOutputBuffer(encOut);
                    boolean eos = (encInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (encoded != null && encInfo.size > 0 && muxerStarted && muxerTrack >= 0) {
                        encoded.position(encInfo.offset);
                        encoded.limit(encInfo.offset + encInfo.size);
                        muxer.writeSampleData(muxerTrack, encoded, encInfo);
                    }
                    encoder.releaseOutputBuffer(encOut, false);
                    if (eos) encOutputDone = true;
                }
            }
            if (progress != null) progress.onProgress(100);
        } finally {
            try { decoder.stop(); } catch (Exception ignored) {}
            try { decoder.release(); } catch (Exception ignored) {}
            try { encoder.stop(); } catch (Exception ignored) {}
            try { encoder.release(); } catch (Exception ignored) {}
            if (muxer != null) {
                try { muxer.stop(); } catch (Exception ignored) {}
                try { muxer.release(); } catch (Exception ignored) {}
            }
        }
        // 全部成功才转正（半途失败/取消不留下半个坏文件）
        if (tempFile.length() == 0) throw new IOException("转换结果为空");
        if (!tempFile.renameTo(outFile)) {
            // 同名已存在时 rename 会失败，先删再试
            if (outFile.exists() && outFile.delete() && tempFile.renameTo(outFile)) {
                // ok
            } else {
                tempFile.delete();
                throw new IOException("无法写出输出文件（同名占用？）");
            }
        }
    }

    /** AAC 码率：采样率与声道数定档（与常见转码工具口径一致）。 */
    private static int aacBitRate(int sampleRate, int channels) {
        int perChannel;
        if (sampleRate >= 44100) perChannel = 96_000;
        else if (sampleRate >= 32000) perChannel = 64_000;
        else perChannel = 48_000;
        return perChannel * Math.max(1, Math.min(channels, 2));
    }

    /** 输入文件大小上限（100MB）：超限直接拒，防 OOM/长转卡死（卡 DFW-132 验收项）。 */
    static final long MAX_INPUT_BYTES = 100L * 1024 * 1024;

    static String humanError(IOException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("已取消")) return "已取消";
        return "转换失败：" + message;
    }

    /** RIFF/WAVE 头（PCM16 little-endian）。 */
    private static final class WavHeader {
        static byte[] placeholder() {
            return header(44100, 2, 2, 0);
        }

        /** 编码完成后把真实采样率/声道/数据长度回填进已写出的文件头。 */
        static void fill(FileOutputStream out, int sampleRate, int channels,
                         int bytesPerSample, long dataBytes) throws IOException {
            byte[] head = header(sampleRate, channels, bytesPerSample, dataBytes);
            // 覆盖文件开头的 44 字节：先强制落盘，再覆写头部，最后指针留在末尾等 close
            out.flush();
            java.nio.channels.FileChannel channel = out.getChannel();
            channel.position(0);
            channel.write(ByteBuffer.wrap(head));
            channel.force(true);
        }

        private static byte[] header(int sampleRate, int channels, int bytesPerSample, long dataBytes) {
            int blockAlign = channels * bytesPerSample;
            int byteRate = sampleRate * blockAlign;
            long safeData = Math.min(dataBytes, Integer.MAX_VALUE - 44L);
            ByteBuffer buffer = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
            buffer.put((byte) 'R').put((byte) 'I').put((byte) 'F').put((byte) 'F');
            buffer.putInt((int) (36 + safeData));
            buffer.put((byte) 'W').put((byte) 'A').put((byte) 'V').put((byte) 'E');
            buffer.put((byte) 'f').put((byte) 'm').put((byte) 't').put((byte) ' ');
            buffer.putInt(16);
            buffer.putShort((short) 1);
            buffer.putShort((short) channels);
            buffer.putInt(sampleRate);
            buffer.putInt(byteRate);
            buffer.putShort((short) blockAlign);
            buffer.putShort((short) (bytesPerSample * 8));
            buffer.put((byte) 'd').put((byte) 'a').put((byte) 't').put((byte) 'a');
            buffer.putInt((int) safeData);
            return buffer.array();
        }
    }

    /** 供测试/展示用的格式化。 */
    static String describe(String mime, int sampleRate, int channels) {
        return String.format(Locale.CHINA, "%s · %d Hz · %s",
                mime, sampleRate, channels == 1 ? "单声道" : channels == 2 ? "立体声" : channels + " 声道");
    }
}
