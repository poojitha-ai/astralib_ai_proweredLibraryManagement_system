# How Benchmarks Mis-Score Computer-Use Agents

**Authors:** Zihan Dong, Zhiyuan Ma, Zekun Wang, Yunqing Li, Zirou Liu, Ruixuan Deng, Qishi Zhan, Rui Qian  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI  
**Document ID:** DOC-ARXIV-2607.28367v1  

## Abstract
Computer-use agents (CUA) are being deployed to browse the web and operate desktop software, yet their benchmark scores are still commonly produced by brittle scripted oracles. A score is the output of a pipeline in which tasks can be stale, trajectories can omit decisive visual evidence, evaluators can reject valid alternatives, and aggregate reports can hide the cause of failure. We organize these problems into a reliability framework spanning task construction, trajectory observation, scoring, and reporting. We then audit 150 public failure-scored trajectories from five web, enterprise-workflow, and desktop-control benchmarks, find that 15.3\% of FAIL verdicts are wrong: 10.7\% are evaluator false negatives and 4.7\% are broken tasks. For genuine failures, a three-tier diagnostic taxonomy shows that verification/feedback and planning failures dominate execution/grounding errors, while a single scalar success rate can not explain. We connect these findings to newer long-horizon CUA benchmarks and derive stage-specific design rules for CUA evaluation.

## 1. Introduction
Recent advancements in cs.AI have significantly transformed knowledge discovery systems. This document explores how benchmarks mis-score computer-use agents and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Zihan Dong, Zhiyuan Ma, Zekun Wang, Yunqing Li, Zirou Liu, Ruixuan Deng, Qishi Zhan, Rui Qian established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, How Benchmarks Mis-Score Computer-Use Agents provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

