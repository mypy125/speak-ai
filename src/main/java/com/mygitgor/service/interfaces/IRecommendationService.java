package com.mygitgor.service.interfaces;


import com.mygitgor.analysis.RecommendationEngine;
import com.mygitgor.model.EnhancedSpeechAnalysis;

import java.util.List;

public interface IRecommendationService {
    List<RecommendationEngine.PersonalizedRecommendation> generateRecommendations(EnhancedSpeechAnalysis analysis);
    RecommendationEngine.WeeklyLearningPlan generateWeeklyPlan(EnhancedSpeechAnalysis analysis);
}
