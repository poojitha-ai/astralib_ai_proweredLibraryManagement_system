# APO: Unsupervised Atomic Policy Optimization for 3D Structure Prediction of Atomic Systems

**Authors:** Shentong Mo, Yatao Bian  
**Publication Date:** 2026-07-30  
**Categories:** cs.LG, cs.AI, cs.MA  
**Document ID:** DOC-ARXIV-2607.28553v1  

## Abstract
Predicting the 3D structures of atomic systems is fundamental to advancing material science and drug discovery. While flow-matching models (, FlowDPO) have recently shown promise in this domain, their performance relies heavily on alignment with ground-truth coordinates via supervised preference learning. However, obtaining experimental labels for novel crystal phases or de novo proteins is prohibitively expensive, creating a bottleneck for structural modeling in data-scarce regimes. In this work, we propose (Atomic Policy Optimization), a fully unsupervised alignment framework that eliminates the need for ground-truth reference structures. APO adapts group-relative policy optimization to 3D atomic environments, utilizing a novel dual-reward mechanism: (i) a that reinforces the policy's dominant latent structural modes through eigen-decomposition of sample similarities, and (ii) a that enforces thermodynamic stability. Our framework enables the model to ``self-correct'' by identifying physically plausible configurations within sampled groups. Extensive benchmarks on crystal and antibody structure prediction demonstrate that APO consistently outperforms fully supervised baselines, achieving a new state-of-the-art in match rates and structural fidelity. Furthermore, we show that APO effectively straightens probability paths, significantly improving inference efficiency. Our results suggest that intrinsic physical consistency can serve as a superior guide for alignment compared to noisy, supervised coordinate matching.

## 1. Introduction
Recent advancements in cs.LG, cs.AI, cs.MA have significantly transformed knowledge discovery systems. This document explores apo: unsupervised atomic policy optimization for 3d structure prediction of atomic systems and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Shentong Mo, Yatao Bian established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, APO: Unsupervised Atomic Policy Optimization for 3D Structure Prediction of Atomic Systems provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

