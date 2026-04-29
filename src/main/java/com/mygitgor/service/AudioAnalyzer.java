package com.mygitgor.service;

import com.mygitgor.model.EnhancedSpeechAnalysis;
import com.mygitgor.service.interfaces.IAudioAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AudioAnalyzer implements IAudioAnalysisService, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(AudioAnalyzer.class);

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAME_SIZE = 1024;
    private static final double MIN_VOLUME_THRESHOLD = 0.01;
    private static final double PAUSE_THRESHOLD = 0.3;
    private static final double MAX_FLUENCY_SILENCE = 0.5;

    private volatile boolean closed = false;
    private final List<AudioInputStream> openStreams = Collections.synchronizedList(new ArrayList<>());

    public AudioAnalyzer() {
        logger.info("Инициализирован анализатор аудио");
    }

    @Override
    public EnhancedSpeechAnalysis analyzeAudio(String audioFilePath, String referenceText) {
        EnhancedSpeechAnalysis analysis = new EnhancedSpeechAnalysis();
        analysis.setAudioPath(audioFilePath);
        analysis.setText(referenceText);

        try {
            logger.info("Начинается анализ аудиофайла: {}", audioFilePath);
            AudioData audioData = loadAudioData(audioFilePath);
            if (audioData == null) {
                throw new IOException("Не удалось загрузить аудиоданные");
            }

            analyzeBasicMetrics(audioData, analysis);

            List<PauseInfo> pauses = detectPauses(audioData.samples, audioData.sampleRate);

            analyzePronunciation(audioData, analysis, pauses);
            analyzeFluency(audioData, analysis, pauses);
            analyzeIntonation(audioData, analysis);
            analyzeVolume(audioData, analysis);
            analyzePauses(audioData, analysis, pauses);

            calculateOverallScore(analysis);
            generateRecommendations(analysis);

            logger.info("Анализ аудио завершен. Общая оценка: {}/100",
                    String.format("%.1f", analysis.getOverallScore()));

        } catch (Exception e) {
            logger.error("Ошибка при анализе аудио", e);
            analysis.addError("Ошибка анализа: " + e.getMessage());
            analysis.setPronunciationScore(0);
            analysis.setFluencyScore(0);
        }

        return analysis;
    }

    private AudioData loadAudioData(String filePath) throws IOException, UnsupportedAudioFileException {
        if (closed) throw new IllegalStateException("AudioAnalyzer закрыт");

        File audioFile = new File(filePath);
        if (!audioFile.exists()) {
            logger.error("Аудиофайл не найден: {}", filePath);
            return null;
        }

        AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
        openStreams.add(audioStream);
        AudioFormat format = audioStream.getFormat();

        if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(), 16,
                    format.getChannels(), format.getChannels() * 2,
                    format.getSampleRate(), false
            );
            audioStream = AudioSystem.getAudioInputStream(targetFormat, audioStream);
            openStreams.add(audioStream);
            format = targetFormat;
        }

        byte[]   audioBytes = audioStream.readAllBytes();
        double[] samples    = convertToSamples(audioBytes, format);

        AudioData audioData = new AudioData();
        audioData.samples    = samples;
        audioData.sampleRate = (int) format.getSampleRate();
        audioData.channels   = format.getChannels();
        audioData.duration   = (double) samples.length / format.getSampleRate();

        logger.debug("Аудиоданные загружены: {} сэмплов, длительность: {} сек",
                samples.length, String.format("%.2f", audioData.duration));
        return audioData;
    }

    private double[] convertToSamples(byte[] audioBytes, AudioFormat format) {
        int sampleSize = format.getSampleSizeInBits() / 8;
        boolean bigEndian = format.isBigEndian();
        double[] samples = new double[audioBytes.length / sampleSize];

        for (int i = 0; i < samples.length; i++) {
            int offset = i * sampleSize;
            int sample = 0;

            if (sampleSize == 2) {
                if (bigEndian) {
                    sample = ((audioBytes[offset] & 0xFF) << 8) | (audioBytes[offset + 1] & 0xFF);
                } else {
                    sample = ((audioBytes[offset + 1] & 0xFF) << 8) | (audioBytes[offset] & 0xFF);
                }
                if (sample >= 32768) sample -= 65536;
                samples[i] = sample / 32768.0;
            } else if (sampleSize == 1) {
                samples[i] = (audioBytes[offset] - 128) / 128.0;
            }
        }
        return samples;
    }

    private void analyzeBasicMetrics(AudioData audioData, EnhancedSpeechAnalysis analysis) {
        analysis.setAnalysisDuration((float) audioData.duration);
        double rms = calculateRMS(audioData.samples);
        analysis.setVolumeLevel((float) rms);
        logger.debug("Базовый анализ: длительность={}с, RMS={}",
                String.format("%.2f", audioData.duration), String.format("%.3f", rms));
    }

    private void analyzePronunciation(AudioData audioData, EnhancedSpeechAnalysis analysis,
                                      List<PauseInfo> pauses) {
        double pronunciationScore = 70.0;

        double[] spectrum = RealFFT.calculateSpectrum(audioData.samples, FRAME_SIZE);
        double[] frequencyBands = computeFrequencyBands(spectrum, audioData.sampleRate);

        boolean hasVoice = detectVoicePresence(audioData.samples, audioData.sampleRate);
        if (!hasVoice) {
            pronunciationScore -= 20;
            analysis.addError("Голос не обнаружен. Возможно, запись содержит только шум");
        }

        if (frequencyBands.length > 0 && frequencyBands[0] > 0.7) {
            pronunciationScore -= 5;
            analysis.addError("Слишком много низких частот. Возможно, проблемы с микрофоном");
        }

        if (frequencyBands.length > 2 && frequencyBands[2] > 0.15) {
            pronunciationScore += 5;
        }

        double speechDuration = audioData.duration
                - pauses.stream().mapToDouble(p -> p.duration).sum();
        if (speechDuration < 0.5) {
            pronunciationScore -= 15;
            analysis.addError("Слишком короткая запись — недостаточно для анализа");
        }

        analysis.setPronunciationScore(Math.max(0, Math.min(100, pronunciationScore)));

        addPhonemeDataFromSpectrum(analysis, spectrum, audioData.sampleRate);

        logger.debug("Анализ произношения: оценка={}", String.format("%.1f", pronunciationScore));
    }

    private void addPhonemeDataFromSpectrum(EnhancedSpeechAnalysis analysis,
                                            double[] spectrum, int sampleRate) {
        int specLen = spectrum.length;

        double totalEnergy = Arrays.stream(spectrum).sum();
        if (totalEnergy == 0) return;

        double vowelEnergy = bandEnergy(spectrum, sampleRate, 80,  300);
        double fricativeEnergy = bandEnergy(spectrum, sampleRate, 3000, 8000);
        double nasalEnergy = bandEnergy(spectrum, sampleRate, 250, 2000);
        double stopEnergy = bandEnergy(spectrum, sampleRate, 1000, 4000);

        double normVowel = Math.min(1.0, vowelEnergy / totalEnergy * 4);
        double normFricative = Math.min(1.0, fricativeEnergy / totalEnergy * 8);
        double normNasal = Math.min(1.0, nasalEnergy / totalEnergy * 5);
        double normStop = Math.min(1.0, stopEnergy / totalEnergy * 6);

        float vowelScore = (float) (50 + normVowel     * 50);
        float fricativeScore = (float) (50 + normFricative * 50);
        float nasalScore = (float) (50 + normNasal     * 50);
        float stopScore = (float) (50 + normStop      * 50);

        analysis.addPhonemeScore("iː",  vowelScore);
        analysis.addPhonemeScore("ɪ",   vowelScore);
        analysis.addPhonemeScore("æ",   (float)(vowelScore * 0.95));
        analysis.addPhonemeScore("ɑː",  vowelScore);
        analysis.addPhonemeScore("ʌ",   vowelScore);
        analysis.addPhonemeScore("ə",   vowelScore);

        analysis.addPhonemeScore("θ",   fricativeScore);
        analysis.addPhonemeScore("ð",   fricativeScore);
        analysis.addPhonemeScore("ʃ",   fricativeScore);
        analysis.addPhonemeScore("ʒ",   (float)(fricativeScore * 0.9));
        analysis.addPhonemeScore("v",   fricativeScore);
        analysis.addPhonemeScore("r",   (float)(fricativeScore * 0.85));

        analysis.addPhonemeScore("ŋ",   nasalScore);
        analysis.addPhonemeScore("l",   nasalScore);
        analysis.addPhonemeScore("w",   nasalScore);

        analysis.addPhonemeScore("tʃ",  stopScore);
        analysis.addPhonemeScore("dʒ",  stopScore);

        if (fricativeScore < 70) {
            analysis.addDetectedError("Слабые фрикативные звуки (th, sh, v) — требуется больше воздуха");
        }
        if (vowelScore < 65) {
            analysis.addDetectedError("Нечёткие гласные — обратите внимание на положение языка");
        }
    }

    private void analyzeFluency(AudioData audioData, EnhancedSpeechAnalysis analysis,
                                List<PauseInfo> pauses) {
        double fluencyScore = 75.0;

        analysis.setPauseCount(pauses.size());

        double speakingRate = calculateSpeakingRate(audioData, pauses);
        analysis.setSpeakingRate((float) speakingRate);

        double rhythmScore = analyzeRhythm(audioData.samples, pauses);

        long longPauses = pauses.stream()
                .filter(p -> p.duration > MAX_FLUENCY_SILENCE)
                .count();

        if (longPauses > 3) {
            fluencyScore -= longPauses * 5;
            analysis.addError("Слишком много длинных пауз: " + longPauses);
        }

        if (speakingRate < 80) {
            fluencyScore -= 10;
            analysis.addError("Слишком медленная речь (" + String.format("%.0f", speakingRate) + " слов/мин)");
        } else if (speakingRate > 220) {
            fluencyScore -= 10;
            analysis.addError("Слишком быстрая речь (" + String.format("%.0f", speakingRate) + " слов/мин)");
        }

        fluencyScore += rhythmScore * 10;

        analysis.setFluencyScore((float) Math.max(0, Math.min(100, fluencyScore)));
        logger.debug("Анализ беглости: оценка={}, скорость={} слов/мин, пауз={}",
                String.format("%.1f", fluencyScore), String.format("%.1f", speakingRate), pauses.size());
    }

    private void analyzeIntonation(AudioData audioData, EnhancedSpeechAnalysis analysis) {
        double intonationScore = 80.0;
        List<int[]> phrases = segmentIntoPhrases(audioData.samples, audioData.sampleRate);

        if (phrases.size() < 2) {
            intonationScore -= 15;
            analysis.addError("Монотонная речь, недостаточно интонационных изменений");
        } else {
            double variety = calculateIntonationVariety(audioData.samples, phrases);
            if (variety < 0.3) {
                intonationScore -= 10;
                analysis.addError("Недостаточное разнообразие интонации");
            } else if (variety > 0.8) {
                intonationScore += 5;
                analysis.addRecommendation("Хорошее разнообразие интонации");
            }
        }

        analysis.setIntonationScore((float) Math.max(0, Math.min(100, intonationScore)));
        logger.debug("Анализ интонации: оценка={}, фраз={}",
                String.format("%.1f", intonationScore), phrases.size());
    }

    private void analyzeVolume(AudioData audioData, EnhancedSpeechAnalysis analysis) {
        double volumeScore = 85.0;
        double rms = analysis.getVolumeLevel();

        if (rms < 0.05) {
            volumeScore -= 30;
            analysis.addError("Очень тихая запись. Увеличьте громкость");
        } else if (rms < 0.1) {
            volumeScore -= 15;
            analysis.addError("Тихая запись. Поднесите микрофон ближе");
        } else if (rms > 0.7) {
            volumeScore -= 20;
            analysis.addError("Слишком громкая запись. Возможны искажения");
        } else if (rms > 0.4) {
            volumeScore -= 5;
            analysis.addWarning("Немного слишком громко");
        }

        double stability = calculateVolumeStability(audioData.samples);
        if (stability < 0.6) {
            volumeScore -= 10;
            analysis.addError("Нестабильная громкость. Старайтесь говорить равномерно");
        }

        analysis.setVolumeScore((float) Math.max(0, Math.min(100, volumeScore)));
        logger.debug("Анализ громкости: оценка={}, RMS={}, стабильность={}",
                String.format("%.1f", volumeScore), String.format("%.3f", rms),
                String.format("%.2f", stability));
    }

    private void analyzePauses(AudioData audioData, EnhancedSpeechAnalysis analysis,
                               List<PauseInfo> pauses) {
        double totalPauseDuration = pauses.stream().mapToDouble(p -> p.duration).sum();
        double pausePercentage = totalPauseDuration / audioData.duration * 100;

        analysis.setTotalPauseDuration((float) totalPauseDuration);
        analysis.setPausePercentage((float) pausePercentage);

        if (pausePercentage > 40) {
            analysis.addError("Слишком много пауз: " + String.format("%.1f", pausePercentage) + "% времени");
        } else if (pausePercentage < 10) {
            analysis.addWarning("Очень мало пауз. Давайте себе время на вдох");
        }

        logger.debug("Анализ пауз: всего={}, длительность={}с, процент={}%",
                pauses.size(), String.format("%.2f", totalPauseDuration),
                String.format("%.1f", pausePercentage));
    }

    private void calculateOverallScore(EnhancedSpeechAnalysis analysis) {
        float overallScore = (float) (
                analysis.getPronunciationScore() * 0.35 +
                        analysis.getFluencyScore() * 0.25 +
                        analysis.getIntonationScore() * 0.20 +
                        analysis.getVolumeScore() * 0.20
        );
        analysis.setOverallScore(overallScore);

        if (overallScore >= 90) analysis.setProficiencyLevel("Продвинутый");
        else if (overallScore >= 75) analysis.setProficiencyLevel("Средний");
        else if (overallScore >= 60) analysis.setProficiencyLevel("Начинающий");
        else analysis.setProficiencyLevel("Новичок");

        analysis.setClarityScore((float)(
                analysis.getPronunciationScore() * 0.6 + analysis.getFluencyScore() * 0.4));

        analysis.setConfidenceScore((float)(
                analysis.getVolumeScore() * 0.5 + analysis.getIntonationScore() * 0.5));

        analysis.setGrammarScore(0);
        analysis.setVocabularyScore(0);
        analysis.addWarning("Grammar и vocabulary score требуют текстовой транскрипции — не вычислены");
    }

    private void generateRecommendations(EnhancedSpeechAnalysis analysis) {
        List<String> recommendations = new ArrayList<>();

        if (analysis.getPronunciationScore() < 70) {
            recommendations.add("Практикуйте произношение отдельных звуков");
            recommendations.add("Слушайте и повторяйте за носителями языка");
        }
        if (analysis.getFluencyScore() < 70) {
            recommendations.add("Уменьшите количество пауз между словами");
            recommendations.add("Попробуйте говорить немного быстрее");
        }
        if (analysis.getIntonationScore() < 75) {
            recommendations.add("Работайте над интонацией в вопросах и утверждениях");
            recommendations.add("Подчеркивайте ключевые слова в предложениях");
        }
        if (analysis.getVolumeScore() < 75) {
            recommendations.add("Говорите громче и увереннее");
            recommendations.add("Проверьте расстояние до микрофона");
        }
        if (analysis.getClarityScore() < 70) {
            recommendations.add("Старайтесь говорить более четко и внятно");
            recommendations.add("Обратите внимание на артикуляцию звуков");
        }
        if (analysis.getConfidenceScore() < 70) {
            recommendations.add("Говорите более уверенно, не сомневайтесь в себе");
            recommendations.add("Практикуйтесь перед зеркалом");
        }
        if (analysis.getOverallScore() < 60) {
            recommendations.add("Регулярно практикуйтесь, хотя бы 15 минут в день");
            recommendations.add("Записывайте себя и анализируйте ошибки");
        } else if (analysis.getOverallScore() > 85) {
            recommendations.add("Отличный результат! Продолжайте в том же духе");
            recommendations.add("Попробуйте более сложные темы для разговора");
        }

        recommendations.forEach(analysis::addRecommendation);
        logger.debug("Сгенерировано рекомендаций: {}", recommendations.size());
    }

    private double calculateRMS(double[] samples) {
        double sum = 0;
        for (double s : samples) sum += s * s;
        return Math.sqrt(sum / samples.length);
    }

    private double[] computeFrequencyBands(double[] spectrum, int sampleRate) {
        double low = bandEnergy(spectrum, sampleRate,    0,  300);
        double mid = bandEnergy(spectrum, sampleRate,  300, 3000);
        double high = bandEnergy(spectrum, sampleRate, 3000, sampleRate / 2);

        double total = low + mid + high;
        if (total == 0) return new double[]{0.33, 0.33, 0.33};
        return new double[]{low / total, mid / total, high / total};
    }

    private double bandEnergy(double[] spectrum, int sampleRate, int freqLow, int freqHigh) {
        int binLow  = freqLow  * FRAME_SIZE / sampleRate;
        int binHigh = Math.min(spectrum.length - 1, freqHigh * FRAME_SIZE / sampleRate);
        double sum  = 0;
        for (int i = Math.max(0, binLow); i <= binHigh; i++) sum += spectrum[i];
        return sum;
    }

    private boolean detectVoicePresence(double[] samples, int sampleRate) {
        int windowSize  = sampleRate / 100;
        int voiceFrames = 0;
        int totalFrames = samples.length / windowSize;

        for (int i = 0; i < totalFrames; i++) {
            int start = i * windowSize;
            int end   = Math.min(start + windowSize, samples.length);
            double energy = 0;
            for (int j = start; j < end; j++) energy += samples[j] * samples[j];
            if (Math.sqrt(energy / (end - start)) > MIN_VOLUME_THRESHOLD) voiceFrames++;
        }
        return totalFrames > 0 && (double) voiceFrames / totalFrames > 0.3;
    }

    private List<PauseInfo> detectPauses(double[] samples, int sampleRate) {
        List<PauseInfo> pauses = new ArrayList<>();
        int    windowSize = sampleRate / 100;
        double silenceThreshold = MIN_VOLUME_THRESHOLD * 0.5;
        boolean inPause = false;
        int pauseStart = -1;

        for (int i = 0; i < samples.length / windowSize; i++) {
            int start = i * windowSize;
            int end = Math.min(start + windowSize, samples.length);
            double energy = 0;
            for (int j = start; j < end; j++) energy += samples[j] * samples[j];
            energy = Math.sqrt(energy / (end - start));

            if (energy < silenceThreshold) {
                if (!inPause) { inPause = true; pauseStart = i; }
            } else {
                if (inPause) {
                    double duration = (double)(i - pauseStart) * windowSize / sampleRate;
                    if (duration >= PAUSE_THRESHOLD) pauses.add(new PauseInfo(pauseStart, i, duration));
                    inPause = false;
                }
            }
        }
        return pauses;
    }

    private double calculateSpeakingRate(AudioData audioData, List<PauseInfo> pauses) {
        double totalPauseDuration = pauses.stream().mapToDouble(p -> p.duration).sum();
        double speakingDuration = audioData.duration - totalPauseDuration;
        if (speakingDuration <= 0) return 0;

        int syllableCount = estimateSyllableCount(audioData.samples, audioData.sampleRate);
        double estimatedWords = syllableCount / 1.5;
        double wpm = (estimatedWords / speakingDuration) * 60;

        return Math.max(40, Math.min(300, wpm));
    }

    private int estimateSyllableCount(double[] samples, int sampleRate) {
        int windowMs = 20;
        int windowSize = sampleRate * windowMs / 1000;
        if (windowSize < 1) return 0;

        int windows = samples.length / windowSize;
        double[] envelope = new double[windows];
        for (int i = 0; i < windows; i++) {
            int start = i * windowSize;
            int end = Math.min(start + windowSize, samples.length);
            double e = 0;
            for (int j = start; j < end; j++) e += samples[j] * samples[j];
            envelope[i] = Math.sqrt(e / (end - start));
        }

        double maxE = Arrays.stream(envelope).max().orElse(1);
        if (maxE > 0) for (int i = 0; i < envelope.length; i++) envelope[i] /= maxE;

        double peakThreshold = 0.3;
        int syllables = 0;
        int minGap = sampleRate / (windowSize * 4);

        for (int i = 1; i < envelope.length - 1; i++) {
            if (envelope[i] > peakThreshold
                    && envelope[i] > envelope[i - 1]
                    && envelope[i] > envelope[i + 1]) {
                syllables++;
                i += minGap;
            }
        }
        return Math.max(1, syllables);
    }

    private double analyzeRhythm(double[] samples, List<PauseInfo> pauses) {
        if (pauses.size() < 2) return 0.5;

        double[] durations = pauses.stream().mapToDouble(p -> p.duration).toArray();
        double mean = Arrays.stream(durations).average().orElse(0);
        double variance = 0;
        for (double d : durations) variance += (d - mean) * (d - mean);
        variance /= durations.length;

        return Math.min(1.0, 1.0 / (1.0 + variance));
    }

    private List<int[]> segmentIntoPhrases(double[] samples, int sampleRate) {
        List<int[]> phrases = new ArrayList<>();
        List<PauseInfo> pauses = detectPauses(samples, sampleRate);
        int start = 0;
        for (PauseInfo pause : pauses) {
            if (pause.startIndex > start) phrases.add(new int[]{start, pause.startIndex});
            start = pause.endIndex;
        }
        if (start < samples.length) phrases.add(new int[]{start, samples.length});
        return phrases;
    }

    private double calculateIntonationVariety(double[] samples, List<int[]> phrases) {
        if (phrases.size() < 2) return 0;

        double[] energies = new double[phrases.size()];
        for (int i = 0; i < phrases.size(); i++) {
            int[] p = phrases.get(i);
            double e = 0;
            for (int j = p[0]; j < p[1]; j++) e += Math.abs(samples[j]);
            energies[i] = (p[1] > p[0]) ? e / (p[1] - p[0]) : 0;
        }

        double mean = Arrays.stream(energies).average().orElse(0);
        if (mean == 0) return 0;

        double variance = 0;
        for (double e : energies) variance += (e - mean) * (e - mean);
        return Math.sqrt(variance / energies.length) / mean;
    }

    private double calculateVolumeStability(double[] samples) {
        int windowCount = 100;
        int windowSize  = samples.length / windowCount;
        if (windowSize < 10) return 0.5;

        double[] energies = new double[windowCount];
        for (int i = 0; i < windowCount; i++) {
            int start = i * windowSize;
            int end = Math.min(start + windowSize, samples.length);
            double e = 0;
            for (int j = start; j < end; j++) e += Math.abs(samples[j]);
            energies[i] = (end > start) ? e / (end - start) : 0;
        }

        double mean = Arrays.stream(energies).average().orElse(0);
        if (mean == 0) return 0;

        double variance = 0;
        for (double e : energies) variance += (e - mean) * (e - mean);
        double cv = Math.sqrt(variance / energies.length) / mean;
        return 1.0 / (1.0 + cv * 2);
    }

    private static class RealFFT {

        public static double[] calculateSpectrum(double[] samples, int frameSize) {
            int n = Integer.highestOneBit(frameSize);
            if (n > samples.length) n = Integer.highestOneBit(samples.length);
            if (n < 2) return new double[]{0};

            double[] windowed = new double[n];
            for (int i = 0; i < n; i++) {
                double hann = 0.5 * (1 - Math.cos(2 * Math.PI * i / (n - 1)));
                windowed[i] = samples[i] * hann;
            }

            double[] re = windowed.clone();
            double[] im = new double[n];

            for (int i = 1, j = 0; i < n; i++) {
                int bit = n >> 1;
                for (; (j & bit) != 0; bit >>= 1) j ^= bit;
                j ^= bit;
                if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; }
            }

            for (int len = 2; len <= n; len <<= 1) {
                double ang = -2 * Math.PI / len;
                double wRe = Math.cos(ang);
                double wIm = Math.sin(ang);
                for (int i = 0; i < n; i += len) {
                    double curRe = 1, curIm = 0;
                    for (int j = 0; j < len / 2; j++) {
                        double uRe = re[i + j];
                        double uIm = im[i + j];
                        double vRe = re[i + j + len / 2] * curRe - im[i + j + len / 2] * curIm;
                        double vIm = re[i + j + len / 2] * curIm + im[i + j + len / 2] * curRe;
                        re[i + j]           = uRe + vRe;
                        im[i + j]           = uIm + vIm;
                        re[i + j + len / 2] = uRe - vRe;
                        im[i + j + len / 2] = uIm - vIm;
                        double newCurRe = curRe * wRe - curIm * wIm;
                        curIm = curRe * wIm + curIm * wRe;
                        curRe = newCurRe;
                    }
                }
            }

            double[] spectrum = new double[n / 2];
            for (int i = 0; i < n / 2; i++) {
                spectrum[i] = Math.sqrt(re[i] * re[i] + im[i] * im[i]) / n;
            }
            return spectrum;
        }
    }

    public double analyzePronunciation(String userInput, String phoneme) {
        File f = new File(userInput);
        if (f.exists()) {
            EnhancedSpeechAnalysis analysis = analyzeAudio(userInput, null);
            Float phonemeScore = analysis.getPhonemeScore(phoneme);
            return phonemeScore != null ? phonemeScore : analysis.getPronunciationScore();
        }
        return -1;
    }

    @Override
    public void close() {
        if (closed) return;
        logger.info("Закрытие AudioAnalyzer...");
        closed = true;

        synchronized (openStreams) {
            for (AudioInputStream stream : openStreams) {
                try {
                    if (stream != null) stream.close();
                } catch (IOException e) {
                    logger.warn("Ошибка при закрытии аудиопотока", e);
                }
            }
            openStreams.clear();
        }
        logger.info("AudioAnalyzer закрыт");
    }

    public boolean isClosed() { return closed; }

    private static class AudioData {
        double[] samples;
        int sampleRate;
        int channels;
        double duration;
    }

    private static class PauseInfo {
        int startIndex;
        int endIndex;
        double duration;

        PauseInfo(int start, int end, double duration) {
            this.startIndex = start;
            this.endIndex = end;
            this.duration = duration;
        }
    }
}