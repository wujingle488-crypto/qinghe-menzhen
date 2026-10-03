package com.commerce.cs.server.media;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

/** 用本机简体中文听写引擎把 WAV 转成文字。识别不到就说明原因，不编一句症状。 */
@Service
public class SpeechTranscriber {
    public String transcribe(Path wav) {
        Path script = resolveScript();
        ProcessBuilder builder = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass",
                "-File", script.toString(), "-Path", wav.toString());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            process.getInputStream().transferTo(captured);
            boolean finished = process.waitFor(25, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalArgumentException("语音识别超时，请说短一些再试");
            }
            String output = captured.toString(StandardCharsets.UTF_8).replace("\uFEFF", "").trim();
            if (process.exitValue() != 0) {
                throw new IllegalArgumentException(shortReason(output));
            }
            if (output.isBlank()) {
                throw new IllegalArgumentException("没有听清，请靠近麦克风再说一次");
            }
            return output.lines().reduce((first, second) -> second).orElse(output).trim();
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("语音识别暂时不可用");
        }
    }

    private static Path resolveScript() {
        Path[] candidates = {
                Path.of("scripts", "transcribe-zh.ps1"),
                Path.of("..", "scripts", "transcribe-zh.ps1")
        };
        for (Path candidate : candidates) {
            Path absolute = candidate.toAbsolutePath().normalize();
            if (absolute.toFile().isFile()) {
                return absolute;
            }
        }
        throw new IllegalArgumentException("语音识别脚本缺失");
    }

    private static String shortReason(String output) {
        String line = output == null ? "" : output.replace('\n', ' ').trim();
        if (line.isBlank()) {
            return "语音识别失败";
        }
        return line.length() <= 80 ? line : "语音识别失败";
    }
}
