# HARGO: Heterogeneity-Aware Reward-Guided Optimization for RL Post-Training of LLMs on HPC Tasks

**Authors:** Tiangang Li, Xiangbo Tian  
**Publication Date:** 2026-07-30  
**Categories:** cs.LG  
**Document ID:** DOC-ARXIV-2607.28301v1  

## Abstract
Supervised fine-tuning (SFT) can equip large language models (LLMs) with domain knowledge for high-performance computing (HPC) tasks such as data race detection and benchmark question answering. However, knowledge alone does not guarantee task-appropriate behavior: the same SFT model that correctly classifies 88.65\% of C/C++ data race samples produces verbose, imprecise answers to factual queries, with 65.9\% of MLPerf responses exceeding 40 characters. Reinforcement learning (RL) post-training addresses this gap by optimizing for task-specific rewards rather than token-level imitation. Yet HPC tasks exhibit extreme heterogeneity, with binary classification, factual QA, and semantic generation differing by 58x in answer length, spanning three distinct reward distributions, and showing widely varying SFT accuracy. This makes uniform-weight RL methods such as GRPO suboptimal. We propose HARGO, Heterogeneity-Aware Reward-Guided Optimization, which introduces per-response importance weighting via confidence-modulated advantage: computing a discrimination signal from group-level reward contrast and a confidence signal from reference model log-probabilities, then modulating the advantage before computing per-response weights, without requiring task-type labels. Across four HPC tasks and nine methods, HARGO achieves the best performance on all three primary metrics: WinRate 54.62\%, Data Race F1 91.30\%, and PLP Similarity 0.8558. Ablation confirms complementary contributions from both signals. HARGO establishes the best overall alignment quality among compared methods for heterogeneous HPC tasks.

## 1. Introduction
Recent advancements in cs.LG have significantly transformed knowledge discovery systems. This document explores hargo: heterogeneity-aware reward-guided optimization for rl post-training of llms on hpc tasks and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Tiangang Li, Xiangbo Tian established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, HARGO: Heterogeneity-Aware Reward-Guided Optimization for RL Post-Training of LLMs on HPC Tasks provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

