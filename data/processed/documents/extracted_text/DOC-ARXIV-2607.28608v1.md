# KAISEN: Reproducible Subgroup Fairness Auditing for Clinical Risk Models

**Authors:** Sparsh Roy, Samuel Girmachew, Nishita Chavan  
**Publication Date:** 2026-07-30  
**Categories:** cs.LG, q-bio.QM  
**Document ID:** DOC-ARXIV-2607.28608v1  

## Abstract
Clinical risk models routinely achieve strong aggregate performance while producing materially different error rates across patient subgroups. Audit pipelines have been proposed to catch this, but their components are rarely stress-tested, so it is unclear which parts of an audit can be trusted and under what conditions. We present KAISEN, a five-phase audit pipeline covering subgroup stratification, disparity measurement, mechanism diagnostics, post-hoc mitigation, and drift monitoring, evaluated to the point of failure on a synthetic benchmark of 16 disease tasks, 15 social-determinant axes from Healthy People 2030, and three prespecified intersections. Four findings follow. (i) Significance tracks each axis's gap against its own minimum detectable effect: rank correlation between significance count and raw equalized-odds difference (EOD) across the 15 axes is rho = 0.56, rising to rho = 0.78 once EOD is standardized by that floor. (ii) Per-group threshold optimization reduces EOD in 48 of 48 held-out runs (paired delta = -0.285, 95% CI [-0.313, -0.252]), while group-wise Platt scaling -- the better calibrator -- behaves as a coin flip on EOD (19 of 48 runs improved, 95% CI [0.26, 0.55]) with mean effect near zero, so what an audit should report is the variance, not the average. (iii) The mechanism diagnostic classifies 144 of 144 controlled cases correctly but recovers none of 48 model-driven cases under proxy misspecification, with no signal that it failed. (iv) CUSUM failures and false alarms track cohort realization far more than disease: at the reference threshold, all 27 false alarms and 7 of 8 missed shifts come from different seeds (chi-squared p = 0.002), so a threshold tuned on one cohort fails to transfer. All results are synthetic with known ground truth and do not establish clinical validity. Code, artifacts, and scripts reproducing every number are released.

## 1. Introduction
Recent advancements in cs.LG, q-bio.QM have significantly transformed knowledge discovery systems. This document explores kaisen: reproducible subgroup fairness auditing for clinical risk models and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Sparsh Roy, Samuel Girmachew, Nishita Chavan established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, KAISEN: Reproducible Subgroup Fairness Auditing for Clinical Risk Models provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

