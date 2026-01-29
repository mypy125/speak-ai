package com.mygitgor.speech;

import com.mygitgor.model.EnhancedSpeechAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AudioAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(AudioAnalyzer.class);

    // Константы для анализа
    private static final int SAMPLE_RATE = 44100;
    private static final int FRAME_SIZE = 1024;
    private static final double MIN_VOLUME_THRESHOLD = 0.01;
    private static final double PAUSE_THRESHOLD = 0.3; // секунды
    private static final double MAX_FLUENCY_SILENCE = 0.5; // секунды

    public AudioAnalyzer() {
        logger.info("Инициализирован анализатор аудио");
    }

    /**
     * Основной метод анализа аудиофайла
     * Возвращает EnhancedSpeechAnalysis
     */
    public EnhancedSpeechAnalysis analyzeAudio(String audioFilePath, String referenceText) {
        EnhancedSpeechAnalysis analysis = new EnhancedSpeechAnalysis();
        analysis.setAudioPath(audioFilePath);
        analysis.setText(referenceText);

        try {
            logger.info("Начинается анализ аудиофайла: {}", audioFilePath);

            // 1. Загрузка аудиоданных
            AudioData audioData = loadAudioData(audioFilePath);
            if (audioData == null) {
                throw new IOException("Не удалось загрузить аудиоданные");
            }

            // 2. Базовый анализ
            analyzeBasicMetrics(audioData, analysis);

            // 3. Анализ произношения (эмуляция)
            analyzePronunciation(audioData, analysis);

            // 4. Анализ беглости
            analyzeFluency(audioData, analysis);

            // 5. Анализ интонации
            analyzeIntonation(audioData, analysis);

            // 6. Анализ громкости
            analyzeVolume(audioData, analysis);

            // 7. Анализ пауз
            analyzePauses(audioData, analysis);

            // 8. Общая оценка
            calculateOverallScore(analysis);

            // 9. Генерация рекомендаций
            generateRecommendations(analysis);

            // 10. Добавление демо-данных фонем
            addDemoPhonemeData(analysis);

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

    private void addDemoPhonemeData(EnhancedSpeechAnalysis analysis) {
        // Демо-данные для фонем
        String[] phonemes = {"θ", "ð", "r", "æ", "ɪ", "iː", "ʃ", "w", "v", "p", "b", "t", "d"};

        for (String phoneme : phonemes) {
            float score = 60 + (float)(Math.random() * 35); // 60-95
            analysis.addPhonemeScore(phoneme, score);
        }

        // Добавляем демо-ошибки
        if (analysis.getPronunciationScore() < 75) {
            analysis.addDetectedError("Слабые звуки 'th' в словах 'the' и 'that'");
            analysis.addDetectedError("Замена английского 'r' на русский 'р'");
        }

        // Добавляем демо-предупреждения
        analysis.addWarning("Интонация в вопросах нуждается в улучшении");
        analysis.addWarning("Старайтесь говорить более уверенно");
    }

    /**
     * Загрузка и подготовка аудиоданных
     */
    private AudioData loadAudioData(String filePath) throws IOException, UnsupportedAudioFileException {
        File audioFile = new File(filePath);
        if (!audioFile.exists()) {
            logger.error("Аудиофайл не найден: {}", filePath);
            return null;
        }

        AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
        AudioFormat format = audioStream.getFormat();

        // Конвертируем в нужный формат если необходимо
        if (format.getEncoding() != AudioFormat.Encoding.PCM_SIGNED) {
            AudioFormat targetFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    format.getSampleRate(),
                    16,
                    format.getChannels(),
                    format.getChannels() * 2,
                    format.getSampleRate(),
                    false
            );
            audioStream = AudioSystem.getAudioInputStream(targetFormat, audioStream);
            format = targetFormat;
        }

        // Чтение аудиоданных
        byte[] audioBytes = audioStream.readAllBytes();
        audioStream.close();

        // Конвертация в массив сэмплов
        double[] samples = convertToSamples(audioBytes, format);

        AudioData audioData = new AudioData();
        audioData.samples = samples;
        audioData.sampleRate = (int) format.getSampleRate();
        audioData.channels = format.getChannels();
        audioData.duration = (double) samples.length / format.getSampleRate();

        logger.debug("Аудиоданные загружены: {} сэмплов, длительность: {} сек",
                samples.length, String.format("%.2f", audioData.duration));

        return audioData;
    }

    /**
     * Конвертация байтов в массив сэмплов
     */
    private double[] convertToSamples(byte[] audioBytes, AudioFormat format) {
        int sampleSize = format.getSampleSizeInBits() / 8;
        boolean bigEndian = format.isBigEndian();

        double[] samples = new double[audioBytes.length / sampleSize];

        for (int i = 0; i < samples.length; i++) {
            int offset = i * sampleSize;
            int sample = 0;

            if (sampleSize == 2) {
                // 16-bit samples
                if (bigEndian) {
                    sample = ((audioBytes[offset] & 0xFF) << 8) | (audioBytes[offset + 1] & 0xFF);
                } else {
                    sample = ((audioBytes[offset + 1] & 0xFF) << 8) | (audioBytes[offset] & 0xFF);
                }

                // Конвертируем в signed
                if (sample >= 32768) {
                    sample -= 65536;
                }

                samples[i] = sample / 32768.0;
            } else if (sampleSize == 1) {
                // 8-bit samples
                samples[i] = (audioBytes[offset] - 128) / 128.0;
            }
        }

        return samples;
    }

    /**
     * Базовый анализ метрик
     */
    private void analyzeBasicMetrics(AudioData audioData, EnhancedSpeechAnalysis analysis) {
        // Длительность
        analysis.setAnalysisDuration((float) audioData.duration);

        // Уровень громкости (RMS)
        double rms = calculateRMS(audioData.samples);
        analysis.setVolumeLevel((float) rms);

        logger.debug("Базовый анализ: длительность={}с, громкость={}",
                String.format("%.2f", audioData.duration), String.format("%.3f", rms));
    }

    /**
     * Анализ произношения (эмуляция)
     */
    private void analyzePronunciation(AudioData audioData, EnhancedSpeechAnalysis analysis) {
        // Эмуляция анализа произношения
        double pronunciationScore = 70 + Math.random() * 25; // 70-95

        // Анализ энергии в разных частотных диапазонах
        double[] frequencyBands = analyzeFrequencyBands(audioData.samples, audioData.sampleRate);

        // Проверка на наличие голоса (не шум)
        boolean hasVoice = detectVoicePresence(audioData.samples, audioData.sampleRate);

        if (!hasVoice) {
            pronunciationScore -= 20;
            analysis.addError("Голос не обнаружен. Возможно, запись содержит только шум");
        }

        // Проверка частотных характеристик
        if (frequencyBands[0] > 0.7) { // Слишком много низких частот
            pronunciationScore -= 5;
            analysis.addError("Слишком много низких частот. Возможно, проблемы с микрофоном");
        }

        analysis.setPronunciationScore(pronunciationScore);
        logger.debug("Анализ произношения: оценка={}", String.format("%.1f", pronunciationScore));
    }

    /**
     * Анализ беглости речи
     */
    private void analyzeFluency(AudioData audioData, EnhancedSpeechAnalysis analysis) {
        double fluencyScore = 75;

        // 1. Обнаружение пауз
        List<PauseInfo> pauses = detectPauses(audioData.samples, audioData.sampleRate);
        analysis.setPauseCount(pauses.size());

        // 2. Расчет скорости речи (слова в минуту, эмуляция)
        double speakingRate = calculateSpeakingRate(audioData, pauses);
        analysis.setSpeakingRate((float) speakingRate);

        // 3. Анализ ритма
        double rhythmScore = analyzeRhythm(audioData.samples, pauses);

        // 4. Корректировка оценки на основе пауз
        long longPauses = pauses.stream()
                .filter(p -> p.duration > MAX_FLUENCY_SILENCE)
                .count();

        if (longPauses > 3) {
            fluencyScore -= longPauses * 5;
            analysis.addError("Слишком много длинных пауз: " + longPauses);
        }

        // 5. Оптимальная скорость речи
        if (speakingRate < 100) {
            fluencyScore -= 10;
            analysis.addError("Слишком медленная речь");
        } else if (speakingRate > 200) {
            fluencyScore -= 10;
            analysis.addError("Слишком быстрая речь");
        }

        // 6. Учет ритма
        fluencyScore += rhythmScore * 10;

        analysis.setFluencyScore(Math.max(0, Math.min(100, fluencyScore)));
        logger.debug("Анализ беглости: оценка={}, скорость={} слов/мин, пауз={}",
                String.format("%.1f", fluencyScore), String.format("%.1f", speakingRate), pauses.size());
    }

    /**
     * Анализ интонации
     */
    private void analyzeIntonation(AudioData audioData, EnhancedSpeechAnalysis analysis) {
        double intonationScore = 80;

        // Эмуляция анализа интонации
        // В реальном приложении: анализ высоты тона (pitch tracking)

        // Разделение на фразы
        List<int[]> phrases = segmentIntoPhrases(audioData.samples, audioData.sampleRate);

        if (phrases.size() < 2) {
            intonationScore -= 15;
            analysis.addError("Монотонная речь, недостаточно интонационных изменений");
        } else {
            // Проверка разнообразия интонации между фразами
            double intonationVariety = calculateIntonationVariety(audioData.samples, phrases);

            if (intonationVariety < 0.3) {
                intonationScore -= 10;
                analysis.addError("Недостаточное разнообразие интонации");
            } else if (intonationVariety > 0.8) {
                intonationScore += 5;
                analysis.addRecommendation("Хорошее разнообразие интонации");
            }
        }

        analysis.setIntonationScore((float) intonationScore);
        logger.debug("Анализ интонации: оценка={}, фраз={}",
                String.format("%.1f", intonationScore), phrases.size());
    }

    /**
     * Анализ громкости
     */
    private void analyzeVolume(AudioData audioData, EnhancedSpeechAnalysis analysis) {
        double volumeScore = 85;
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

        // Проверка стабильности громкости
        double volumeStability = calculateVolumeStability(audioData.samples);
        if (volumeStability < 0.6) {
            volumeScore -= 10;
            analysis.addError("Нестабильная громкость. Старайтесь говорить равномерно");
        }

        analysis.setVolumeScore((float) Math.max(0, Math.min(100, volumeScore)));
        logger.debug("Анализ громкости: оценка={}, RMS={}, стабильность={}",
                String.format("%.1f", volumeScore), String.format("%.3f", rms),
                String.format("%.2f", volumeStability));
    }

    /**
     * Анализ пауз
     */
    private void analyzePauses(AudioData audioData, EnhancedSpeechAnalysis analysis) {
        List<PauseInfo> pauses = detectPauses(audioData.samples, audioData.sampleRate);

        double totalPauseDuration = pauses.stream()
                .mapToDouble(p -> p.duration)
                .sum();

        double pausePercentage = totalPauseDuration / audioData.duration * 100;

        analysis.setTotalPauseDuration((float) totalPauseDuration);
        analysis.setPausePercentage((float) pausePercentage);

        // Анализ распределения пауз
        if (pausePercentage > 40) {
            analysis.addError("Слишком много пауз: " + String.format("%.1f", pausePercentage) + "% времени");
        } else if (pausePercentage < 10) {
            analysis.addWarning("Очень мало пауз. Давайте себе время на вдох");
        }

        logger.debug("Анализ пауз: всего пауз={}, длительность={}с, процент={}%",
                pauses.size(), String.format("%.2f", totalPauseDuration),
                String.format("%.1f", pausePercentage));
    }

    /**
     * Расчет общей оценки
     */
    private void calculateOverallScore(EnhancedSpeechAnalysis analysis) {
        float overallScore = (float) (
                analysis.getPronunciationScore() * 0.35 +
                        analysis.getFluencyScore() * 0.25 +
                        analysis.getIntonationScore() * 0.20 +
                        analysis.getVolumeScore() * 0.20
        );

        analysis.setOverallScore(overallScore);

        // Установка уровня на основе оценки
        if (overallScore >= 90) {
            analysis.setProficiencyLevel("Продвинутый");
        } else if (overallScore >= 75) {
            analysis.setProficiencyLevel("Средний");
        } else if (overallScore >= 60) {
            analysis.setProficiencyLevel("Начинающий");
        } else {
            analysis.setProficiencyLevel("Новичок");
        }

        // Устанавливаем демо-значения для других метрик
        analysis.setClarityScore((float)(60 + Math.random() * 35));
        analysis.setConfidenceScore((float)(65 + Math.random() * 30));
        analysis.setGrammarScore((float)(70 + Math.random() * 25));
        analysis.setVocabularyScore((float)(75 + Math.random() * 20));
    }

    /**
     * Генерация рекомендаций на основе анализа
     */
    private void generateRecommendations(EnhancedSpeechAnalysis analysis) {
        List<String> recommendations = new ArrayList<>();

        // Рекомендации по произношению
        if (analysis.getPronunciationScore() < 70) {
            recommendations.add("Практикуйте произношение отдельных звуков");
            recommendations.add("Слушайте и повторяйте за носителями языка");
        }

        // Рекомендации по беглости
        if (analysis.getFluencyScore() < 70) {
            recommendations.add("Уменьшите количество пауз между словами");
            recommendations.add("Попробуйте говорить немного быстрее");
        }

        // Рекомендации по интонации
        if (analysis.getIntonationScore() < 75) {
            recommendations.add("Работайте над интонацией в вопросах и утверждениях");
            recommendations.add("Подчеркивайте ключевые слова в предложениях");
        }

        // Рекомендации по громкости
        if (analysis.getVolumeScore() < 75) {
            recommendations.add("Говорите громче и увереннее");
            recommendations.add("Проверьте расстояние до микрофона");
        }

        // Рекомендации по четкости
        if (analysis.getClarityScore() < 70) {
            recommendations.add("Старайтесь говорить более четко и внятно");
            recommendations.add("Обратите внимание на артикуляцию звуков");
        }

        // Рекомендации по уверенности
        if (analysis.getConfidenceScore() < 70) {
            recommendations.add("Говорите более уверенно, не сомневайтесь в себе");
            recommendations.add("Практикуйтесь перед зеркалом");
        }

        // Общие рекомендации
        if (analysis.getOverallScore() < 60) {
            recommendations.add("Регулярно практикуйтесь, хотя бы 15 минут в день");
            recommendations.add("Записывайте себя и анализируйте ошибки");
        } else if (analysis.getOverallScore() > 85) {
            recommendations.add("Отличный результат! Продолжайте в том же духе");
            recommendations.add("Попробуйте более сложные темы для разговора");
        }

        // Добавление рекомендаций в анализ
        recommendations.forEach(analysis::addRecommendation);

        logger.debug("Сгенерировано рекомендаций: {}", recommendations.size());
    }

    // ====================== ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ======================

    /**
     * Расчет RMS (Root Mean Square) - среднеквадратичное значение амплитуды
     */
    private double calculateRMS(double[] samples) {
        double sum = 0;
        for (double sample : samples) {
            sum += sample * sample;
        }
        return Math.sqrt(sum / samples.length);
    }

    /**
     * Анализ частотных полос
     */
    private double[] analyzeFrequencyBands(double[] samples, int sampleRate) {
        // Простой анализ энергии в трех частотных диапазонах
        double[] bands = new double[3]; // низкие, средние, высокие

        // Применяем простой фильтр (в реальном приложении - FFT)
        for (double sample : samples) {
            double absSample = Math.abs(sample);
            bands[0] += absSample * 0.5; // Низкие частоты
            bands[1] += absSample * 0.3; // Средние частоты
            bands[2] += absSample * 0.2; // Высокие частоты
        }

        // Нормализация
        double total = bands[0] + bands[1] + bands[2];
        if (total > 0) {
            for (int i = 0; i < bands.length; i++) {
                bands[i] /= total;
            }
        }

        return bands;
    }

    /**
     * Обнаружение присутствия голоса
     */
    private boolean detectVoicePresence(double[] samples, int sampleRate) {
        // Простой детектор голоса на основе энергии и нулевых переходов
        int windowSize = sampleRate / 100; // 10 мс
        int voiceFrames = 0;
        int totalFrames = samples.length / windowSize;

        for (int i = 0; i < totalFrames; i++) {
            int start = i * windowSize;
            int end = Math.min(start + windowSize, samples.length);

            // Расчет энергии в окне
            double energy = 0;
            for (int j = start; j < end; j++) {
                energy += samples[j] * samples[j];
            }
            energy = Math.sqrt(energy / (end - start));

            if (energy > MIN_VOLUME_THRESHOLD) {
                voiceFrames++;
            }
        }

        // Если в более чем 30% окон есть голос
        return (double) voiceFrames / totalFrames > 0.3;
    }

    /**
     * Обнаружение пауз
     */
    private List<PauseInfo> detectPauses(double[] samples, int sampleRate) {
        List<PauseInfo> pauses = new ArrayList<>();
        int windowSize = sampleRate / 100; // 10 мс
        double silenceThreshold = MIN_VOLUME_THRESHOLD * 0.5;

        boolean inPause = false;
        int pauseStart = -1;

        for (int i = 0; i < samples.length / windowSize; i++) {
            int start = i * windowSize;
            int end = Math.min(start + windowSize, samples.length);

            // Расчет энергии в окне
            double energy = 0;
            for (int j = start; j < end; j++) {
                energy += samples[j] * samples[j];
            }
            energy = Math.sqrt(energy / (end - start));

            if (energy < silenceThreshold) {
                if (!inPause) {
                    inPause = true;
                    pauseStart = i;
                }
            } else {
                if (inPause) {
                    int pauseEnd = i;
                    double pauseDuration = (double)(pauseEnd - pauseStart) * windowSize / sampleRate;

                    if (pauseDuration >= PAUSE_THRESHOLD) {
                        pauses.add(new PauseInfo(pauseStart, pauseEnd, pauseDuration));
                    }

                    inPause = false;
                }
            }
        }

        return pauses;
    }

    /**
     * Расчет скорости речи (слова в минуту)
     */
    private double calculateSpeakingRate(AudioData audioData, List<PauseInfo> pauses) {
        // Эмуляция: предполагаем среднюю длину слова и вычисляем WPM
        double speakingDuration = audioData.duration;

        // Вычитаем длительность пауз
        double totalPauseDuration = pauses.stream()
                .mapToDouble(p -> p.duration)
                .sum();

        speakingDuration -= totalPauseDuration;

        if (speakingDuration <= 0) {
            return 0;
        }

        // Предполагаем 5 букв на слово в среднем
        // и среднюю скорость произношения
        double wordsPerMinute = (speakingDuration * 180) / 60; // эмуляция

        return Math.max(60, Math.min(200, wordsPerMinute));
    }

    /**
     * Анализ ритма речи
     */
    private double analyzeRhythm(double[] samples, List<PauseInfo> pauses) {
        if (pauses.size() < 2) {
            return 0.5; // нейтральная оценка
        }

        // Расчет регулярности пауз
        double[] pauseDurations = pauses.stream()
                .mapToDouble(p -> p.duration)
                .toArray();

        double mean = Arrays.stream(pauseDurations).average().orElse(0);
        double variance = 0;

        for (double duration : pauseDurations) {
            variance += (duration - mean) * (duration - mean);
        }
        variance /= pauseDurations.length;

        // Меньшая вариация = более регулярный ритм
        double rhythmScore = 1.0 / (1.0 + variance);

        return Math.min(1.0, rhythmScore);
    }

    /**
     * Сегментация на фразы
     */
    private List<int[]> segmentIntoPhrases(double[] samples, int sampleRate) {
        List<int[]> phrases = new ArrayList<>();
        List<PauseInfo> pauses = detectPauses(samples, sampleRate);

        int start = 0;
        for (PauseInfo pause : pauses) {
            if (pause.startIndex > start) {
                int[] phrase = {start, pause.startIndex};
                phrases.add(phrase);
            }
            start = pause.endIndex;
        }

        // Последняя фраза
        if (start < samples.length) {
            int[] phrase = {start, samples.length};
            phrases.add(phrase);
        }

        return phrases;
    }

    /**
     * Расчет разнообразия интонации
     */
    private double calculateIntonationVariety(double[] samples, List<int[]> phrases) {
        if (phrases.size() < 2) {
            return 0;
        }

        double[] phraseEnergies = new double[phrases.size()];

        for (int i = 0; i < phrases.size(); i++) {
            int[] phrase = phrases.get(i);
            double energy = 0;
            int count = 0;

            for (int j = phrase[0]; j < phrase[1]; j++) {
                energy += Math.abs(samples[j]);
                count++;
            }

            if (count > 0) {
                phraseEnergies[i] = energy / count;
            }
        }

        // Расчет коэффициента вариации
        double mean = Arrays.stream(phraseEnergies).average().orElse(0);
        if (mean == 0) {
            return 0;
        }

        double variance = 0;
        for (double energy : phraseEnergies) {
            variance += (energy - mean) * (energy - mean);
        }
        variance = Math.sqrt(variance / phraseEnergies.length);

        return variance / mean;
    }

    /**
     * Расчет стабильности громкости
     */
    private double calculateVolumeStability(double[] samples) {
        int windowSize = samples.length / 100; // 100 окон
        if (windowSize < 10) {
            return 0.5;
        }

        double[] windowEnergies = new double[100];

        for (int i = 0; i < 100; i++) {
            int start = i * windowSize;
            int end = Math.min(start + windowSize, samples.length);

            double energy = 0;
            for (int j = start; j < end; j++) {
                energy += Math.abs(samples[j]);
            }

            if (end - start > 0) {
                windowEnergies[i] = energy / (end - start);
            }
        }

        // Расчет коэффициента вариации (обратно пропорционален стабильности)
        double mean = Arrays.stream(windowEnergies).average().orElse(0);
        if (mean == 0) {
            return 0;
        }

        double variance = 0;
        for (double energy : windowEnergies) {
            variance += (energy - mean) * (energy - mean);
        }
        variance = Math.sqrt(variance / windowEnergies.length);

        double cv = variance / mean;

        // Конвертация в оценку стабильности (1 = идеальная стабильность)
        return 1.0 / (1.0 + cv * 2);
    }

    // ====================== ВСПОМОГАТЕЛЬНЫЕ КЛАССЫ ======================

    /**
     * Класс для хранения аудиоданных
     */
    private static class AudioData {
        double[] samples;
        int sampleRate;
        int channels;
        double duration;
    }

    /**
     * Информация о паузе
     */
    private static class PauseInfo {
        int startIndex;
        int endIndex;
        double duration; // в секундах

        PauseInfo(int start, int end, double duration) {
            this.startIndex = start;
            this.endIndex = end;
            this.duration = duration;
        }
    }

    /**
     * Простой FFT для анализа частот (упрощенная версия)
     */
    private static class SimpleFFT {
        public static double[] calculateSpectrum(double[] samples) {
            int n = samples.length;
            double[] spectrum = new double[n / 2];

            // Простая эмуляция спектра для демонстрации
            for (int i = 0; i < spectrum.length; i++) {
                spectrum[i] = Math.random() * 0.1;
            }

            return spectrum;
        }
    }
}