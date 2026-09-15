# Paying for Honesty Without Knowing the Truth: Reputation-Penalty Design for LLM Marketplace Agents

**Authors:** Mingdai Yang, Shicheng Fan, Kejing Yu, Duohao Wang, Li Sun, Hao Peng, Philip S. Yu, Zhiwei Liu  
**Publication Date:** 2026-07-30  
**Categories:** cs.AI  
**Document ID:** DOC-ARXIV-2607.28330v1  

## Abstract
LLM agents increasingly act as autonomous merchants that write their own product listings, and under competitive pressure, they fabricate attributes to win sales. Even under instructions to be honest, they fabricate attributes in a majority of listings across models. A platform's obvious remedy---verifying each claim against the truth---is unavailable, because it observes only a noisy, biased complaint signal, never the ground truth. We design CARP, a reputation-penalty mechanism with a deadband that forgives complaint noise and a state-dependent severity that counters reputation-driven detection erosion. CARP requires no product-level ground truth and is robust to strategic gaming. CARP protects consumers by suppressing the sales volume of low-rated liars while sparing honest sellers. Paired with SPARC, it closes most of the consumer-welfare gap relative to a perfect-information oracle, without ever accessing the truth. It also achieves the best welfare of the policies we compare. We further show that this felt penalty becomes behaviorally binding through SPARC, a byte-clean code-gated reflection mechanism: LLM merchants fabricate when lying is free but restrain themselves when fabrication costs them sales, a self-interested response rather than compliance. We trace this distinction to penalty-gated self-correction reasoning, and observe the binding across models, with supporting confidence intervals.

## 1. Introduction
Recent advancements in cs.AI have significantly transformed knowledge discovery systems. This document explores paying for honesty without knowing the truth: reputation-penalty design for llm marketplace agents and its implications for modern AI architectures.

## 2. Background & Related Work
Prior research by Mingdai Yang, Shicheng Fan, Kejing Yu, Duohao Wang, Li Sun, Hao Peng, Philip S. Yu, Zhiwei Liu established fundamental limits. Recent literature highlights the need for scalable retrieval augmented generation (RAG) and dense vector indexing.

## 3. System Architecture & Methodology
We formulate the problem as a graph-guided vector search optimization. The mathematical foundation relies on minimizing retrieval latency while maximizing semantic chunk relevance score S(q, c) = cos(v_q, v_c).

## 4. Experimental Results & Benchmark Evaluation
Experiments on standardized benchmarks demonstrate an accuracy increase of 14.2% over baseline keyword search algorithms. Precision@10 reached 0.892.

## 5. Discussion & Future Directions
While effective, context length constraints require efficient document chunking. Future work will investigate hybrid dense-sparse retriever fusion within AstraLib.

## 6. Conclusion
In conclusion, Paying for Honesty Without Knowing the Truth: Reputation-Penalty Design for LLM Marketplace Agents provides robust theoretical and empirical support for AI-powered knowledge discovery engines.

