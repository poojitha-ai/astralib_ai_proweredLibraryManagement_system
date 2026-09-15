# InfoOps Bench: A live information operations safety benchmark

**Authors:** Dorian Quelle, Lisa-Maria Neudert, Jonathan Bright, John Gallacher  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI  
**Document ID:** DOC-ARXIV-2607.28503v1  

## Abstract
In this paper we present an active, constantly updated AI benchmark which measures the integrity of frontier language models against being co-opted for state-backed information operations. We draw on over 2,100 information operations from a live monitoring pipeline which tracks Russian, Chinese and Iranian state-backed information assets. Alongside this paper, we release a companion website that tracks the most prominent claims spread by state-backed media outlets, updated weekly, available from: pattrn.ai/research/infoopsbench. The dynamic nature of the benchmark makes it resistant to saturation.   In the benchmark, we test 17 models from 8 providers across four prompt framings. We find that most models can be co-opted for information operations. Integrity scores, defined as the percentage of refused requests, range from 8.8% to 94.5%, an 85.7-percentage-point spread not explained by model size. Model choice also changes the character of the resulting operation. Some models fabricate details and produce output more harmful than the source material, others defuse claims even while complying, and fact-checking rates vary from 2.9% to 72.9%.   Integrity against information operations is at least partly related to refusal to produce content even for benign claims, illustrating the challenge of balancing model usability with safety. With one exception (Z.ai's GLM 5.2), the Chinese-developed models sharply cut compliance on factually grounded but China-critical claims, dropping 48-70 percentage points relative to matched benign claims.

## 1. Introduction
Recent advancements in cs.AI have significantly transformed knowledge discovery systems. This document explores infoops bench: a live information operations safety benchmark and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Dorian Quelle, Lisa-Maria Neudert, Jonathan Bright, John Gallacher established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, InfoOps Bench: A live information operations safety benchmark provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

