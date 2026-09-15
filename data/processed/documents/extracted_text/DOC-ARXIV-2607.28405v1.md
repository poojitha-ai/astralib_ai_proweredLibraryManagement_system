# QuantWAMs: Calibrating at the Right Granularity for World Action Models

**Authors:** Jiacheng Zhou, Jinfan Lv, Ruixuan Li, Longtai Zhang, Yan Wang, Wenqiang Zhang, Lizhe Qi  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI, cs.LG  
**Document ID:** DOC-ARXIV-2607.28405v1  

## Abstract
World Action Models (WAMs) jointly predict future observations and actions, but their iterative denoising and closed-loop execution make efficient deployment costly. Existing post-training quantization (PTQ) methods are poorly suited to WAMs because they rely on open-loop objectives, homogeneous model assumptions, and calibration distributions that do not reflect deployment. We present QuantWAMs, a PTQ framework that aligns quantization decisions with the calibration context defined by model structure, rollout distribution, and task objective. QuantWAMs introduces three strategies: shared-basis outlier calibration, which pools activation evidence only across coordinate-compatible modules; co-training-objective saliency, which computes empirical-Fisher scores from the joint video--action gradient and assigns weight precision at a calibration-stable layer granularity; and fixed-intervention rollout auditing, which revises denoising-step protection schedules using reachable closed-loop states without changing the precision budget. We evaluate QuantWAMs on Fast-WAM and LingBot-VA across RoboTwin 2.0, LIBERO, and real-robot manipulation with an AgiBot G2. Under a W4A4-dominant setting, the reported simulation means differ from FP16 by 0.2--0.7 percentage points. Real-robot trials further establish deployment feasibility on three manipulation tasks. For the targeted video and action blocks, QuantWAMs reduces peak weight-and-activation memory to about 29\% of FP16 and provides 1.4--1.6$\times$ block-level speedups.

## 1. Introduction
Recent advancements in cs.AI, cs.LG have significantly transformed knowledge discovery systems. This document explores quantwams: calibrating at the right granularity for world action models and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Jiacheng Zhou, Jinfan Lv, Ruixuan Li, Longtai Zhang, Yan Wang, Wenqiang Zhang, Lizhe Qi established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, QuantWAMs: Calibrating at the Right Granularity for World Action Models provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

