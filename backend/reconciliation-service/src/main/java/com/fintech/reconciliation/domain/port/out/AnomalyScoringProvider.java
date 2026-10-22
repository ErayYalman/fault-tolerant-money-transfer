package com.fintech.reconciliation.domain.port.out;

import com.fintech.reconciliation.domain.model.AnomalyFeatures;
import com.fintech.reconciliation.domain.model.AnomalyPrediction;

public interface AnomalyScoringProvider {

    AnomalyPrediction score(AnomalyFeatures features);
}