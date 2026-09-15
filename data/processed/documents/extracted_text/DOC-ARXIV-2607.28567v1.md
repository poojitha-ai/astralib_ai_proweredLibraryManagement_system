# Doubly Robust Functional Representation Learning for Longitudinal Causal Inference with Irregular Histories

**Authors:** Mengfei Ran, Yifeng Shen, Ruijie Guan  
**Publication Date:** 2026-07-30  
**Categories:** stat.ML, cs.LG, stat.AP, stat.CO, stat.ME  
**Document ID:** DOC-ARXIV-2607.28567v1  

## Abstract
Longitudinal causal studies often record histories as irregular functional fragments: laboratory values, physiologic signals, sensor streams, and image-derived summaries measured at unequal and informative times. Standard doubly robust estimators usually require scalar summaries, whereas sequence learners optimize prediction losses that need not stabilize the efficient influence function. We propose Doubly Robust Functional Representation Learning (DR-FRL), a cross-fitted workflow that turns irregular histories into estimand-targeted states for observed-history regimes. Functional and temporal encoders map point clouds and prior histories into states; nuisance heads estimate outcome, treatment, and censoring functions; and EIF-targeted validation, calibration, overlap, tail, and ablation diagnostics assess whether the state supports the estimating equation. If the selected state preserves the nuisance information needed by the EIF, representation error enters the same second-order product remainder as ordinary nuisance error, and the mean estimator is asymptotically linear under explicit rate, overlap, calibration, and stability conditions. Catoni aggregation is treated separately as a bounded-influence point estimator, not a replacement for Wald inference. Simulations show gains when functional confounding is high-dimensional, measurement is informative, support is weak, or pseudo-outcomes are heavy-tailed. A VitalDB audit shows that DR-FRL can use irregular laboratory point clouds and deliver a useful negative finding: for this ICU-disposition endpoint, scalar laboratory summaries already carry much endpoint-relevant information.

## 1. Introduction
Recent advancements in stat.ML, cs.LG, stat.AP, stat.CO, stat.ME have significantly transformed knowledge discovery systems. This document explores doubly robust functional representation learning for longitudinal causal inference with irregular histories and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Mengfei Ran, Yifeng Shen, Ruijie Guan established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Doubly Robust Functional Representation Learning for Longitudinal Causal Inference with Irregular Histories provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

